#!/usr/bin/env python3
"""Private per-install feedback board. Local SQLite, no GitHub/Tencent credentials.

PR 27's WebView and system image picker remain the client; this replaces its external board.
Run behind the supplied loopback-only Nginx proxy. Admin operations use SSH + this CLI.
"""
import argparse
from contextlib import contextmanager, closing
import base64
import hashlib
import hmac
import http.cookies
import http.server
import io
import json
import os
from pathlib import Path
import re
import secrets
import sqlite3
import sys
import threading
import time
import urllib.parse
import warnings

PREFIX = '/dafeiyu-feedback/'
COOKIE = 'dafeiyu_feedback'
MAX_BODY = 4 * 1024 * 1024 + 16384
MAX_IMAGE = 3 * 1024 * 1024
ID = re.compile(r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
RECEIPT = re.compile(r'^[A-Za-z0-9_-]{24}$')
HERE = Path(__file__).resolve().parent
IMAGE_WORKERS = threading.BoundedSemaphore(2)


class Rejected(Exception):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status


def clean(value, limit, field):
    if not isinstance(value, str):
        raise Rejected(400, field + '格式不正确')
    value = ''.join(c for c in value if c in '\n\t' or c.isprintable()).strip()
    if len(value) > limit:
        raise Rejected(400, field + '太长了')
    return value


def request_id(value):
    if not isinstance(value, str) or not ID.fullmatch(value):
        raise Rejected(400, '提交编号无效，请重新打开页面')
    return value


def screenshot(value):
    with IMAGE_WORKERS:
        return decode_screenshot(value)


def decode_screenshot(value):
    if not value:
        return None
    if not isinstance(value, str) or len(value) > MAX_IMAGE * 4 // 3 + 100:
        raise Rejected(413, '截图请小于 3 MB')
    try:
        raw = base64.b64decode(value, validate=True)
        if len(raw) > MAX_IMAGE:
            raise Rejected(413, '截图请小于 3 MB')
        from PIL import Image, ImageOps
        with warnings.catch_warnings():
            warnings.simplefilter('error', Image.DecompressionBombWarning)
            with Image.open(io.BytesIO(raw)) as img:
                if img.format not in ('PNG', 'JPEG', 'WEBP') or img.width * img.height > 12_000_000:
                    raise Rejected(400, '请选不超过 1200 万像素的 PNG、JPEG 或 WebP 截图')
                img.load()
                image = ImageOps.exif_transpose(img).convert('RGB')
                image.thumbnail((2400, 2400))
                out = io.BytesIO()
                image.save(out, format='JPEG', quality=88)  # Re-encode, stripping EXIF and other metadata.
                data = out.getvalue()
                if len(data) > MAX_IMAGE:
                    raise Rejected(413, '截图太大，请缩小后再发')
                return data
    except Rejected:
        raise
    except Exception:
        raise Rejected(400, '这张图片无法读取，请换一张截图')


class Store:
    def __init__(self, path, secret, origin, clock=time.time):
        if len(secret) < 32:
            raise ValueError('FEEDBACK_SECRET must contain at least 32 characters')
        u = urllib.parse.urlsplit(origin)
        if u.scheme != 'https' or not u.netloc or u.username or u.path or u.query or u.fragment:
            raise ValueError('FEEDBACK_ORIGIN must be an HTTPS origin without path')
        self.path, self.secret, self.origin, self.clock = str(path), secret.encode(), origin, clock
        Path(self.path).parent.mkdir(parents=True, exist_ok=True)
        with self.db() as db:
            db.executescript('''
              PRAGMA journal_mode=WAL;
              CREATE TABLE IF NOT EXISTS users(owner TEXT PRIMARY KEY, created INTEGER NOT NULL);
              CREATE TABLE IF NOT EXISTS reports(
                id TEXT PRIMARY KEY, owner TEXT NOT NULL, request_id TEXT NOT NULL, digest TEXT NOT NULL,
                text TEXT NOT NULL, contact TEXT NOT NULL, meta TEXT NOT NULL, created INTEGER NOT NULL,
                client TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'open', image BLOB,
                UNIQUE(owner,request_id));
              CREATE INDEX IF NOT EXISTS reports_owner ON reports(owner,created);
              CREATE INDEX IF NOT EXISTS reports_client ON reports(client,created);
              CREATE TABLE IF NOT EXISTS messages(
                id INTEGER PRIMARY KEY AUTOINCREMENT, report TEXT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
                author TEXT NOT NULL, request_id TEXT NOT NULL, text TEXT NOT NULL, created INTEGER NOT NULL,
                UNIQUE(report,author,request_id));
            ''')

    @contextmanager
    def db(self):
        db = sqlite3.connect(self.path, timeout=10)
        db.row_factory = sqlite3.Row
        db.execute('PRAGMA foreign_keys=ON')
        try:
            with db:
                yield db
        finally:
            db.close()

    def digest(self, value):
        return hmac.new(self.secret, value.encode(), hashlib.sha256).hexdigest()

    def bootstrap(self, install):
        request_id(install)
        owner = hashlib.sha256(install.encode()).hexdigest()
        now = int(self.clock())
        with self.db() as db:
            db.execute('BEGIN IMMEDIATE')
            if not db.execute('SELECT 1 FROM users WHERE owner=?', (owner,)).fetchone():
                if db.execute('SELECT count(*) FROM users').fetchone()[0] >= 50000:
                    raise Rejected(503, '服务暂时繁忙，请稍后重试')
                db.execute('INSERT INTO users VALUES(?,?)', (owner, now))
        value = owner + ':' + str(now + 365 * 86400)
        return value + ':' + self.digest(value)

    def owner(self, cookie):
        try:
            jar = http.cookies.SimpleCookie(cookie or '')
            value = jar[COOKIE].value
            owner, expires, signature = value.split(':')
            if not re.fullmatch('[0-9a-f]{64}', owner) or int(expires) < self.clock():
                raise ValueError()
            if not hmac.compare_digest(signature, self.digest(owner + ':' + expires)):
                raise ValueError()
            with self.db() as db:
                if not db.execute('SELECT 1 FROM users WHERE owner=?', (owner,)).fetchone():
                    raise ValueError()
            return owner
        except (ValueError, KeyError, http.cookies.CookieError):
            raise Rejected(401, '请从大肥鱼 App 重新打开问题反馈')

    def submit(self, owner, client, body, meta):
        if not isinstance(body, dict):
            raise Rejected(400, '内容格式不正确')
        rid = request_id(body.get('id'))
        text = clean(body.get('text'), 2000, '反馈内容')
        contact = clean(body.get('contact', ''), 100, '联系方式')
        if not text:
            raise Rejected(400, '先写下遇到的问题')
        # Digest original request for reliable duplicate detection, even after image sanitization.
        digest = hashlib.sha256(json.dumps([text, contact, body.get('image', '')], ensure_ascii=False).encode()).hexdigest()
        image = screenshot(body.get('image', ''))
        now = int(self.clock() * 1000)
        client = self.digest(client)
        with self.db() as db:
            db.execute('BEGIN IMMEDIATE')
            old = db.execute('SELECT id,digest FROM reports WHERE owner=? AND request_id=?', (owner, rid)).fetchone()
            if old:
                if old['digest'] != digest:
                    raise Rejected(409, '这次提交的内容已改变，请重新提交')
                return old['id']
            hour = db.execute('SELECT count(*) FROM reports WHERE (owner=? OR client=?) AND created>?', (owner, client, now - 3600000)).fetchone()[0]
            day = db.execute('SELECT count(*) FROM reports WHERE (owner=? OR client=?) AND created>?', (owner, client, now - 86400000)).fetchone()[0]
            total_day = db.execute('SELECT count(*) FROM reports WHERE created>?', (now - 86400000,)).fetchone()[0]
            if hour >= 5 or day >= 20 or total_day >= 300:
                raise Rejected(429, '发得有点频繁，稍后再试；草稿仍在')
            count, used = db.execute('SELECT count(*),coalesce(sum(length(image)),0) FROM reports').fetchone()
            if count >= 10000 or used + len(image or b'') > 128 * 1024 * 1024:
                raise Rejected(503, '反馈空间暂满，请稍后再试')
            receipt = secrets.token_urlsafe(18)
            db.execute('INSERT INTO reports(id,owner,request_id,digest,text,contact,meta,created,client,image) VALUES(?,?,?,?,?,?,?,?,?,?)',
                       (receipt, owner, rid, digest, text, contact, json.dumps(meta, ensure_ascii=False), now, client, image))
            return receipt

    def reports(self, owner=None):
        with self.db() as db:
            rows = db.execute('SELECT id,text,contact,meta,created,status,image IS NOT NULL AS has_image FROM reports' +
                              (' WHERE owner=?' if owner else '') + ' ORDER BY created DESC LIMIT 100', (owner,) if owner else ()).fetchall()
            result = []
            for row in rows:
                value = dict(row)
                value['meta'] = json.loads(value['meta'])
                value['messages'] = [dict(r) for r in db.execute('SELECT author,text,created FROM messages WHERE report=? ORDER BY id', (row['id'],))]
                result.append(value)
            return result

    def image(self, owner, receipt):
        with self.db() as db:
            row = db.execute('SELECT image FROM reports WHERE owner=? AND id=?', (owner, receipt)).fetchone()
            if not row or row[0] is None:
                raise Rejected(404, '没有这张截图')
            return row[0]

    def message(self, receipt, text, rid, owner=None):
        request_id(rid)
        text = clean(text, 2000, '回复内容')
        if not text:
            raise Rejected(400, '先写下回复')
        author = 'user' if owner else 'developer'
        with self.db() as db:
            db.execute('BEGIN IMMEDIATE')
            row = db.execute('SELECT owner FROM reports WHERE id=?', (receipt,)).fetchone()
            if not row or (owner and row['owner'] != owner):
                raise Rejected(404, '没有这条反馈')
            old = db.execute('SELECT text FROM messages WHERE report=? AND author=? AND request_id=?', (receipt, author, rid)).fetchone()
            if old:
                if old[0] != text:
                    raise Rejected(409, '提交编号已使用')
                return
            if db.execute('SELECT count(*) FROM messages WHERE report=?', (receipt,)).fetchone()[0] >= 60:
                raise Rejected(429, '这条反馈回复较多，请新建反馈继续')
            now = int(self.clock() * 1000)
            if owner and db.execute('SELECT count(*) FROM messages JOIN reports ON reports.id=messages.report WHERE reports.owner=? AND author=? AND messages.created>?', (owner, 'user', now - 3600000)).fetchone()[0] >= 20:
                raise Rejected(429, '回复较频繁，请稍后再试')
            db.execute('INSERT INTO messages(report,author,request_id,text,created) VALUES(?,?,?,?,?)', (receipt, author, rid, text, now))


def handler_for(store):
    class Handler(http.server.BaseHTTPRequestHandler):
        server_version = 'DafeiyuFeedback'
        sys_version = ''

        def setup(self):
            super().setup()
            self.connection.settimeout(15)

        def answer(self, status, body=b'', mime='application/json; charset=utf-8', cookie=None, location=None):
            if isinstance(body, dict):
                body = json.dumps(body, ensure_ascii=False).encode()
            self.send_response(status)
            self.send_header('Content-Type', mime)
            self.send_header('Content-Length', str(len(body)))
            self.send_header('Cache-Control', 'no-store')
            self.send_header('X-Content-Type-Options', 'nosniff')
            self.send_header('Referrer-Policy', 'no-referrer')
            self.send_header('X-Frame-Options', 'DENY')
            self.send_header('Content-Security-Policy', "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' blob: data:; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'")
            if cookie:
                for name, value in cookie.items():
                    self.send_header('Set-Cookie', f'{name}={value}; Path={PREFIX}; HttpOnly; Secure; SameSite=Strict; Max-Age=31536000')
            if location:
                self.send_header('Location', location)
            self.end_headers()
            self.wfile.write(body)

        def body(self, mime):
            if self.headers.get('Content-Type', '').split(';')[0].strip().lower() != mime:
                raise Rejected(415, '内容格式不正确')
            try:
                length = int(self.headers.get('Content-Length', ''))
            except ValueError:
                raise Rejected(411, '请求缺少长度')
            if not 0 < length <= MAX_BODY:
                raise Rejected(413, '内容或截图过大')
            data = self.rfile.read(length)
            if len(data) != length:
                raise Rejected(400, '内容未传完整')
            return data

        def route(self):
            path = urllib.parse.urlsplit(self.path).path
            if self.command == 'GET':
                if path == PREFIX + 'health':
                    with store.db() as db:
                        db.execute('SELECT 1 FROM reports LIMIT 1')
                    return self.answer(200, {'ok': True})
                files = {PREFIX: ('index.html', 'text/html; charset=utf-8'), PREFIX + 'app.js': ('app.js', 'text/javascript; charset=utf-8'), PREFIX + 'style.css': ('style.css', 'text/css; charset=utf-8')}
                if path in files:
                    name, mime = files[path]
                    cookies = None
                    if path == PREFIX and self.headers.get('Authorization'):
                        # Native WebView initial navigation only. No token in URLs, HTML, JS or logs.
                        auth = self.headers['Authorization']
                        if not auth.startswith('Bearer '):
                            raise Rejected(401, '请从大肥鱼 App 打开')
                        encoded = self.headers.get('X-Dafeiyu-Device', '')
                        if len(encoded) > 4096:
                            raise Rejected(400, '设备信息过长')
                        fields = json.loads(base64.b64decode(encoded, validate=True))
                        if not isinstance(fields, dict):
                            raise Rejected(400, '设备信息格式不正确')
                        meta = {k: clean(fields.get(k, ''), limit, k) for k, limit in [('app_version', 48), ('android', 64), ('device', 100)]}
                        session = store.bootstrap(auth[7:])
                        owner = store.owner(f'{COOKIE}={session}')
                        value = base64.urlsafe_b64encode(json.dumps(meta, ensure_ascii=False).encode()).decode()
                        cookies = {COOKIE: session, COOKIE + '_meta': value + '.' + store.digest(owner + ':' + value)}
                    return self.answer(200, (HERE / 'web' / name).read_bytes(), mime, cookies)
                owner = store.owner(self.headers.get('Cookie'))
                if path == PREFIX + 'api/reports':
                    return self.answer(200, {'ok': True, 'reports': store.reports(owner)})
                match = re.fullmatch(re.escape(PREFIX) + r'api/images/([A-Za-z0-9_-]{24})', path)
                if match:
                    return self.answer(200, store.image(owner, match[1]), 'image/jpeg')
                raise Rejected(404, '页面不存在')
            if self.command != 'POST':
                raise Rejected(405, '请求方式不支持')
            origin = self.headers.get('Origin')
            if origin and origin != store.origin:
                raise Rejected(403, '请在反馈页面内操作')
            if origin != store.origin:
                raise Rejected(403, '请在反馈页面内操作')
            owner = store.owner(self.headers.get('Cookie'))
            body = json.loads(self.body('application/json'))
            if not isinstance(body, dict):
                raise Rejected(400, '内容格式不正确')
            if path == PREFIX + 'api/reports':
                meta = {}
                try:
                    jar = http.cookies.SimpleCookie(self.headers.get('Cookie', ''))
                    value, signature = jar[COOKIE + '_meta'].value.rsplit('.', 1)
                    if hmac.compare_digest(signature, store.digest(owner + ':' + value)):
                        meta = json.loads(base64.urlsafe_b64decode(value))
                except (ValueError, KeyError, http.cookies.CookieError):
                    pass
                receipt = store.submit(owner, self.headers.get('X-Real-IP') or self.client_address[0], body, meta)
                return self.answer(200, {'ok': True, 'receipt': receipt})
            match = re.fullmatch(re.escape(PREFIX) + r'api/reports/([A-Za-z0-9_-]{24})/messages', path)
            if match:
                store.message(match[1], body.get('text'), body.get('id'), owner)
                return self.answer(200, {'ok': True})
            raise Rejected(404, '页面不存在')

        def handle_request(self):
            try:
                self.route()
            except Rejected as e:
                self.answer(e.status, {'ok': False, 'error': str(e)})
            except (ValueError, UnicodeError):
                self.answer(400, {'ok': False, 'error': '内容格式不正确'})
            except (BrokenPipeError, ConnectionResetError, TimeoutError):
                pass
            except Exception as e:
                print('feedback failure: ' + type(e).__name__, file=sys.stderr)
                self.answer(503, {'ok': False, 'error': '服务暂时不可用，草稿已保留'})

        do_GET = do_POST = do_PUT = do_DELETE = handle_request

        def log_message(self, fmt, *args):
            pass  # No body, cookie, query, contact or IP in application logs.

    return Handler


class Server(http.server.ThreadingHTTPServer):
    daemon_threads = True
    def __init__(self, address, handler):
        self.workers = threading.BoundedSemaphore(8)
        super().__init__(address, handler)

    def process_request(self, request, address):
        if not self.workers.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, address)
        except Exception:
            self.workers.release()
            raise

    def process_request_thread(self, request, address):
        try:
            super().process_request_thread(request, address)
        finally:
            self.workers.release()


def main():
    p = argparse.ArgumentParser()
    p.add_argument('command', choices=['serve', 'list', 'reply', 'close', 'backup'])
    p.add_argument('--id')
    p.add_argument('--file')
    p.add_argument('--request-id')
    args = p.parse_args()
    store = Store(os.environ.get('FEEDBACK_DB', '/var/lib/dafeiyu-feedback/feedback.sqlite3'), os.environ.get('FEEDBACK_SECRET', ''), os.environ.get('FEEDBACK_ORIGIN', 'https://81.70.79.133'))
    if args.command == 'serve':
        Server(('127.0.0.1', int(os.environ.get('FEEDBACK_PORT', '8790'))), handler_for(store)).serve_forever()
    elif args.command == 'list':
        rows = store.reports()
        if args.id:
            rows = [r for r in rows if r['id'] == args.id]
        print(json.dumps(rows, ensure_ascii=False, indent=2))
    elif args.command == 'reply':
        if not args.file or not args.request_id:
            p.error('reply requires --file and --request-id (UUID for retry deduplication)')
        text = sys.stdin.buffer.read().decode('utf-8') if args.file == '-' else Path(args.file).read_text(encoding='utf-8')
        store.message(args.id, text, args.request_id)
        print('Reply saved')
    elif args.command == 'close':
        with store.db() as db:
            changed = db.execute('UPDATE reports SET status=? WHERE id=?', ('closed', args.id)).rowcount
        if not changed:
            p.error('Report not found')
        print('Closed')
    else:
        if not args.file:
            p.error('backup requires --file')
        with store.db() as source, closing(sqlite3.connect(args.file)) as target:
            source.backup(target)
        os.chmod(args.file, 0o600)
        print('Backup saved')


if __name__ == '__main__':
    main()
