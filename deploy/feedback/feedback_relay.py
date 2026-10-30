#!/usr/bin/env python3
"""Relay for in-app problem reports: the app POSTs JSON here, this files a GitHub issue.

The GitHub token lives only in this server's environment (see README.md); the APK never carries it. Runs behind the
existing Nginx on 127.0.0.1, standard library only (Python 3.8+). Logs counts and outcomes, never report text.

Environment:
  FEEDBACK_GITHUB_TOKEN  fine-grained token with Issues: read and write on FEEDBACK_REPO only (required)
  FEEDBACK_REPO          owner/name of the repository receiving issues; keep it private (required)
  FEEDBACK_PORT          local port, default 8790
"""
import collections, http.server, json, os, re, sys, threading, time, urllib.request

MAX_BODY = 16 * 1024
MAX_TEXT, MAX_CONTACT = 2000, 100
PER_HOUR, PER_DAY, GLOBAL_PER_DAY = 5, 20, 300
ID = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
SHORT = {"app_version": 32, "android": 40, "device": 80}


class Rejected(Exception):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status


def clean(value, limit, field):
    if not isinstance(value, str):
        raise Rejected(400, field + " must be text")
    value = "".join(ch for ch in value if ch in "\n\t" or ch.isprintable()).strip()
    if len(value) > limit:
        raise Rejected(400, field + " too long")
    return value


def validate(report):
    """The fields the app sends, trimmed and bounded; anything else is ignored."""
    if not isinstance(report, dict):
        raise Rejected(400, "body must be a JSON object")
    rid = report.get("id")
    if not isinstance(rid, str) or not ID.match(rid):
        raise Rejected(400, "bad id")
    text = clean(report.get("text"), MAX_TEXT, "text")
    if not text:
        raise Rejected(400, "text is empty")
    out = {"id": rid, "text": text, "contact": clean(report.get("contact", ""), MAX_CONTACT, "contact")}
    for field, limit in SHORT.items():
        out[field] = clean(report.get(field, ""), limit, field)
    code = report.get("version_code", 0)
    out["version_code"] = code if isinstance(code, int) and not isinstance(code, bool) and 0 <= code < 10**9 else 0
    return out


def fence(text):
    """A code fence longer than any backtick run inside, so the text cannot render links, images or @mentions."""
    longest = max((len(run) for run in re.findall(r"`+", text)), default=0)
    mark = "`" * max(3, longest + 1)
    return mark + "text\n" + text + "\n" + mark


def issue(report):
    first = report["text"].splitlines()[0].strip()
    title = "[反馈] " + (first[:40] + "…" if len(first) > 40 else first)
    lines = ["**内容**", "", fence(report["text"]), ""]
    if report["contact"]:
        lines += ["**联系方式**", "", fence(report["contact"]), ""]
    version = report["app_version"] + (" (code %d)" % report["version_code"] if report["version_code"] else "")
    lines += ["| 项目 | 值 |", "|---|---|",
              "| App | " + (version or "未知").replace("|", "/") + " |",
              "| Android | " + (report["android"] or "未知").replace("|", "/") + " |",
              "| 机型 | " + (report["device"] or "未知").replace("|", "/") + " |", "",
              "<sub>来自 App 内「问题反馈」 · " + report["id"] + "</sub>"]
    return {"title": title, "body": "\n".join(lines)}


def github(repo, token):
    def post(payload):
        request = urllib.request.Request(
            "https://api.github.com/repos/" + repo + "/issues", json.dumps(payload).encode("utf-8"),
            {"Authorization": "Bearer " + token, "Accept": "application/vnd.github+json",
             "X-GitHub-Api-Version": "2022-11-28", "Content-Type": "application/json",
             "User-Agent": "dafeiyu-feedback-relay"}, method="POST")
        with urllib.request.urlopen(request, timeout=15) as response:
            return json.loads(response.read().decode("utf-8"))["number"]
    return post


class Relay:
    """Rate limits and duplicate ids, in memory: a restart forgets them, which only loosens limits briefly."""

    def __init__(self, create, clock=time.time):
        self.create, self.clock, self.lock = create, clock, threading.Lock()
        self.by_client = collections.defaultdict(collections.deque)
        self.all_day = collections.deque()
        self.receipts = collections.OrderedDict()

    def submit(self, client, report):
        report = validate(report)
        with self.lock:
            if report["id"] in self.receipts:
                return self.receipts[report["id"]]
            now = self.clock()
            mine = self.by_client[client]
            while mine and now - mine[0] >= 86400:
                mine.popleft()
            while self.all_day and now - self.all_day[0] >= 86400:
                self.all_day.popleft()
            if sum(1 for t in mine if now - t < 3600) >= PER_HOUR or len(mine) >= PER_DAY or len(self.all_day) >= GLOBAL_PER_DAY:
                raise Rejected(429, "too many reports")
            mine.append(now)
            self.all_day.append(now)
            if len(self.by_client) > 10000:
                for key in [k for k, v in self.by_client.items() if not v]:
                    del self.by_client[key]
        try:
            receipt = "#%d" % self.create(issue(report))
        except Exception as error:
            raise Rejected(502, "could not file the report: " + type(error).__name__)
        with self.lock:
            self.receipts[report["id"]] = receipt
            while len(self.receipts) > 2000:
                self.receipts.popitem(last=False)
        return receipt


def handler_for(relay):
    class Handler(http.server.BaseHTTPRequestHandler):
        server_version = "dafeiyu-feedback"
        sys_version = ""

        def answer(self, status, payload):
            data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self):
            self.answer(405, {"ok": False, "error": "POST only"})

        def do_POST(self):
            try:
                if self.path != "/submit":
                    raise Rejected(404, "not found")
                if not self.headers.get("Content-Type", "").lower().startswith("application/json"):
                    raise Rejected(400, "JSON only")
                try:
                    length = int(self.headers.get("Content-Length", ""))
                except ValueError:
                    raise Rejected(411, "length required")
                if length < 0 or length > MAX_BODY:
                    raise Rejected(413, "too large")
                try:
                    report = json.loads(self.rfile.read(length).decode("utf-8"))
                except (UnicodeDecodeError, ValueError):
                    raise Rejected(400, "bad JSON")
                # Nginx sets X-Real-IP; the relay only listens on loopback, so the header cannot come from outside.
                client = self.headers.get("X-Real-IP") or self.client_address[0]
                receipt = relay.submit(client, report)
                self.log_message("filed %s", receipt)
                self.answer(200, {"ok": True, "receipt": receipt})
            except Rejected as rejected:
                self.log_message("rejected %d %s", rejected.status, rejected)
                self.answer(rejected.status, {"ok": False, "error": str(rejected)})

        def log_message(self, fmt, *args):
            sys.stderr.write("feedback: " + (fmt % args) + "\n")

    return Handler


def main():
    token, repo = os.environ.get("FEEDBACK_GITHUB_TOKEN", ""), os.environ.get("FEEDBACK_REPO", "")
    if not token or not re.match(r"^[\w.-]+/[\w.-]+$", repo):
        sys.exit("Set FEEDBACK_GITHUB_TOKEN and FEEDBACK_REPO (owner/name).")
    port = int(os.environ.get("FEEDBACK_PORT", "8790"))
    server = http.server.ThreadingHTTPServer(("127.0.0.1", port), handler_for(Relay(github(repo, token))))
    sys.stderr.write("feedback: listening on 127.0.0.1:%d for %s\n" % (port, repo))
    server.serve_forever()


if __name__ == "__main__":
    main()
