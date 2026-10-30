import base64
from concurrent.futures import ThreadPoolExecutor
from contextlib import closing
import http.client
import http.cookies
import io
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import threading
import unittest
import uuid
from PIL import Image
from feedback_server import Store, Server, Rejected, handler_for, COOKIE, PREFIX, screenshot, MAX_BODY


class FeedbackTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.now = 1800000000
        self.store = Store(Path(self.temp.name) / 'feedback.sqlite3', 'a' * 64, 'https://feedback.example', lambda: self.now)
        self.install = str(uuid.uuid4())
        self.cookie = COOKIE + '=' + self.store.bootstrap(self.install)
        self.owner = self.store.owner(self.cookie)
        self.other = self.store.owner(COOKIE + '=' + self.store.bootstrap(str(uuid.uuid4())))
        self.server = Server(('127.0.0.1', 0), handler_for(self.store))
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        self.temp.cleanup()

    def body(self, **kw):
        return dict(id=str(uuid.uuid4()), text='功能验证：中文、emoji 🐟', contact='', **kw)

    def submit(self, body=None, owner=None, ip='test-ip'):
        return self.store.submit(owner or self.owner, ip, body or self.body(), {'app_version': 'test (19)'})

    def rejected(self, code, fn):
        with self.assertRaises(Rejected) as e:
            fn()
        self.assertEqual(code, e.exception.status)

    def request(self, method, path, body=None, headers=None):
        h = {'Cookie': self.cookie, 'Origin': self.store.origin}
        h.update(headers or {})
        data = body
        if isinstance(body, dict):
            data = json.dumps(body).encode()
            h['Content-Type'] = 'application/json'
        c = http.client.HTTPConnection(*self.server.server_address, timeout=5)
        try:
            c.request(method, path, body=data, headers=h)
            r = c.getresponse()
            return r.status, r.read(), r.getheaders()
        finally:
            c.close()

    def test_private_lists_and_read_access(self):
        rid = self.submit()
        self.assertEqual(rid, self.store.reports(self.owner)[0]['id'])
        self.assertEqual([], self.store.reports(self.other))
        self.rejected(404, lambda: self.store.image(self.other, rid))
        self.rejected(404, lambda: self.store.message(rid, 'not mine', str(uuid.uuid4()), self.other))

    def test_cookie_tampering_and_expiry(self):
        self.rejected(401, lambda: self.store.owner(None))
        self.rejected(401, lambda: self.store.owner(self.cookie[:-2] + 'xx'))
        self.now += 366 * 86400
        self.rejected(401, lambda: self.store.owner(self.cookie))
        self.assertEqual(self.owner, self.store.owner(COOKIE + '=' + self.store.bootstrap(self.install)))

    def test_persistence_and_duplicate_after_restart(self):
        body = self.body()
        rid = self.submit(body)
        reloaded = Store(self.store.path, 'a' * 64, self.store.origin, lambda: self.now)
        self.assertEqual(rid, reloaded.submit(self.owner, 'test-ip', body, {}))
        self.assertEqual(1, len(reloaded.reports(self.owner)))

    def test_concurrent_duplicate_is_one_record(self):
        body = self.body()
        with ThreadPoolExecutor(max_workers=8) as pool:
            ids = list(pool.map(lambda _: self.submit(body), range(8)))
        self.assertEqual(1, len(set(ids)))
        self.assertEqual(1, len(self.store.reports(self.owner)))

    def test_id_collision_changed_body_rejected(self):
        body = self.body()
        self.submit(body)
        body['text'] = 'changed'
        self.rejected(409, lambda: self.submit(body))

    def test_rate_limit_survives_restart_and_duplicate_is_free(self):
        bodies = [self.body() for _ in range(5)]
        for body in bodies:
            self.submit(body)
        reloaded = Store(self.store.path, 'a' * 64, self.store.origin, lambda: self.now)
        self.rejected(429, lambda: reloaded.submit(self.owner, 'test-ip', self.body(), {}))
        self.assertTrue(self.submit(bodies[0]))
        self.now += 3601
        self.assertTrue(self.submit())

    def test_ip_limit_cannot_be_reset_by_new_install(self):
        for _ in range(5):
            self.submit()
        self.rejected(429, lambda: self.submit(owner=self.other))

    def test_text_and_metadata_are_not_interpreted(self):
        body = self.body()
        body.update(text='<script>alert(1)</script>\nDROP TABLE reports;', model_key='not retained')
        self.submit(body)
        row = self.store.reports(self.owner)[0]
        self.assertEqual(body['text'], row['text'])
        self.assertNotIn('model_key', json.dumps(row))
        for text in ['', 'x' * 2001, 123]:
            body['id'], body['text'] = str(uuid.uuid4()), text
            self.rejected(400, lambda: self.submit(body))

    def test_image_reencoded_and_owner_protected(self):
        out = io.BytesIO()
        Image.new('RGB', (32, 24), (50, 100, 150)).save(out, format='PNG')
        body = self.body(image=base64.b64encode(out.getvalue()).decode())
        rid = self.submit(body)
        data = self.store.image(self.owner, rid)
        self.assertTrue(data.startswith(b'\xff\xd8'))
        self.assertTrue(self.store.reports(self.owner)[0]['has_image'])
        with Image.open(io.BytesIO(data)) as img:
            self.assertEqual((32, 24), img.size)
            self.assertFalse(img.getexif())
        self.rejected(404, lambda: self.store.image(self.other, rid))

    def test_invalid_images_rejected(self):
        for data in ['not base64', base64.b64encode(b'<svg onload="alert(1)">').decode()]:
            self.rejected(400, lambda: screenshot(data))
        self.rejected(413, lambda: screenshot('a' * (5 * 1024 * 1024)))

    def test_reply_delivery_and_duplicate(self):
        rid = self.submit()
        mid = str(uuid.uuid4())
        self.store.message(rid, '开发者回复', mid)
        self.store.message(rid, '开发者回复', mid)
        self.store.message(rid, '用户补充', str(uuid.uuid4()), self.owner)
        self.assertEqual(['developer', 'user'], [m['author'] for m in self.store.reports(self.owner)[0]['messages']])
        self.rejected(409, lambda: self.store.message(rid, 'changed', mid))

    def test_reply_cli_reads_stdin(self):
        rid = self.submit()
        env = dict(os.environ, FEEDBACK_DB=self.store.path, FEEDBACK_SECRET='a' * 64, FEEDBACK_ORIGIN=self.store.origin)
        subprocess.run([sys.executable, str(Path(__file__).with_name('feedback_server.py')), 'reply', '--id', rid, '--file', '-', '--request-id', str(uuid.uuid4())], input='管理命令中文回复 🐟', text=True, encoding='utf-8', env=env, check=True, capture_output=True)
        self.assertEqual('管理命令中文回复 🐟', self.store.reports(self.owner)[0]['messages'][0]['text'])

    def test_bootstrap_headers_and_cookie_policy(self):
        meta = base64.b64encode(json.dumps({'device': '模拟器', 'app_version': 'test', 'android': '15', 'key': 'ignored'}).encode()).decode()
        code, data, headers = self.request('GET', PREFIX, headers={'Authorization': 'Bearer ' + self.install, 'X-Dafeiyu-Device': meta})
        self.assertEqual(200, code)
        cookies = [v for k, v in headers if k.lower() == 'set-cookie']
        self.assertEqual(2, len(cookies))
        self.assertTrue(all('HttpOnly' in c and 'Secure' in c and 'SameSite=Strict' in c for c in cookies))
        self.assertNotIn(self.install.encode(), data)
        jar = http.cookies.SimpleCookie()
        for cookie in cookies:
            jar.load(cookie)
        self.cookie = '; '.join(k + '=' + v.value for k, v in jar.items())
        self.assertEqual(200, self.request('POST', PREFIX + 'api/reports', self.body())[0])
        row = self.store.reports(self.owner)[0]
        self.assertEqual({'device': '模拟器', 'app_version': 'test', 'android': '15'}, row['meta'])

    def test_http_auth_origin_and_path_restrictions(self):
        self.assertEqual(401, self.request('GET', PREFIX + 'api/reports', headers={'Cookie': ''})[0])
        self.assertEqual(403, self.request('POST', PREFIX + 'api/reports', self.body(), {'Origin': 'https://evil.example'})[0])
        self.assertEqual(403, self.request('POST', PREFIX + 'api/reports', self.body(), {'Origin': ''})[0])
        self.assertEqual(404, self.request('GET', PREFIX + '../feedback.sqlite3')[0])
        self.assertEqual(405, self.request('DELETE', PREFIX + 'api/reports')[0])
        self.assertEqual(404, self.request('POST', PREFIX, self.body())[0])

    def test_http_invalid_payloads(self):
        self.assertEqual(400, self.request('POST', PREFIX + 'api/reports', b'{bad', {'Content-Type': 'application/json'})[0])
        self.assertEqual(415, self.request('POST', PREFIX + 'api/reports', b'text', {'Content-Type': 'text/plain'})[0])
        self.assertEqual(413, self.request('POST', PREFIX + 'api/reports', b'x', {'Content-Type': 'application/json', 'Content-Length': str(MAX_BODY + 1)})[0])

    def test_http_duplicate_and_owner_image(self):
        body = self.body()
        first = json.loads(self.request('POST', PREFIX + 'api/reports', body)[1])
        second = json.loads(self.request('POST', PREFIX + 'api/reports', body)[1])
        self.assertEqual(first['receipt'], second['receipt'])
        self.assertEqual(404, self.request('GET', PREFIX + 'api/images/' + first['receipt'])[0])

    def test_static_security_and_health(self):
        for path in ['', 'app.js', 'style.css', 'health']:
            code, _, headers = self.request('GET', PREFIX + path)
            self.assertEqual(200, code)
            self.assertEqual('no-store', dict(headers)['Cache-Control'])
            self.assertIn("frame-ancestors 'none'", dict(headers)['Content-Security-Policy'])

    def test_backup_preserves_feedback_and_replies(self):
        rid = self.submit()
        self.store.message(rid, 'backup evidence', str(uuid.uuid4()))
        target = Path(self.temp.name) / 'backup.sqlite3'
        with self.store.db() as source, closing(sqlite3.connect(target)) as backup:
            source.backup(backup)
        restored = Store(target, 'a' * 64, self.store.origin, lambda: self.now)
        self.assertEqual('backup evidence', restored.reports(self.owner)[0]['messages'][0]['text'])

    def test_daily_backup_keeps_seven_and_restores(self):
        self.submit()
        folder = Path(self.temp.name) / 'backups'
        folder.mkdir()
        for i in range(1, 10):
            (folder / f'feedback-2025010{i}T000000Z.sqlite3').write_bytes(b'old fixture')
        unrelated = folder / 'keep-this.txt'
        unrelated.write_text('not a backup')
        env = dict(os.environ, FEEDBACK_DB=self.store.path)
        subprocess.run([sys.executable, str(Path(__file__).with_name('backup.py'))], env=env, check=True, capture_output=True)
        files = sorted(folder.glob('feedback-*.sqlite3'))
        self.assertEqual(7, len(files))
        self.assertTrue(unrelated.exists())
        with closing(sqlite3.connect(files[-1])) as db:
            self.assertEqual(1, db.execute('SELECT count(*) FROM reports').fetchone()[0])


if __name__ == '__main__':
    unittest.main(verbosity=2)
