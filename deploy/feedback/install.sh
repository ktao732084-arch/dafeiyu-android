#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
test "$(id -u)" = 0
python3 -c 'import sys; assert sys.version_info >= (3,11), "Python 3.11+ required"'
test -f /etc/nginx/conf.d/dash-bj-443.conf
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup="/root/dafeiyu-feedback-backup-$stamp"
mkdir -m 700 "$backup"
for file in /etc/nginx/conf.d/dash-bj-443.conf /etc/nginx/snippets/dafeiyu-feedback.conf /etc/nginx/conf.d/dafeiyu-feedback-rate.conf /etc/systemd/system/dafeiyu-feedback.service; do
    if test -f "$file"; then cp -p "$file" "$backup/$(basename "$file")"; fi
done
if test -d /opt/dafeiyu-feedback/web; then cp -a /opt/dafeiyu-feedback/web "$backup/web"; fi
if test -f /opt/dafeiyu-feedback/feedback_server.py; then cp -p /opt/dafeiyu-feedback/feedback_server.py "$backup/"; fi
if ! id dafeiyu-feedback >/dev/null 2>&1; then useradd --system --home-dir /var/lib/dafeiyu-feedback --shell /sbin/nologin dafeiyu-feedback; fi
install -d -m 755 /opt/dafeiyu-feedback /opt/dafeiyu-feedback/web
install -d -m 700 -o dafeiyu-feedback -g dafeiyu-feedback /var/lib/dafeiyu-feedback
if ! test -x /opt/dafeiyu-feedback/venv/bin/python; then python3 -m venv /opt/dafeiyu-feedback/venv; fi
/opt/dafeiyu-feedback/venv/bin/python -m pip install --disable-pip-version-check --index-url https://pypi.org/simple --only-binary=:all: -r requirements.txt
install -m 644 feedback_server.py backup.py requirements.txt /opt/dafeiyu-feedback/
install -m 644 web/index.html web/app.js web/style.css /opt/dafeiyu-feedback/web/
if ! test -f /etc/dafeiyu-feedback.env; then
    python3 - <<'PY'
import os,secrets
path='/etc/dafeiyu-feedback.env'
fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w') as f:
    f.write('FEEDBACK_ORIGIN=https://81.70.79.133\nFEEDBACK_PORT=8790\nFEEDBACK_DB=/var/lib/dafeiyu-feedback/feedback.sqlite3\nFEEDBACK_SECRET='+secrets.token_hex(32)+'\n')
PY
fi
install -m 644 dafeiyu-feedback.service dafeiyu-feedback-backup.service dafeiyu-feedback-backup.timer /etc/systemd/system/
install -m 644 feedback-http.conf /etc/nginx/conf.d/dafeiyu-feedback-rate.conf
install -m 644 feedback-location.conf /etc/nginx/snippets/dafeiyu-feedback.conf
python3 - <<'PY'
from pathlib import Path
p=Path('/etc/nginx/conf.d/dash-bj-443.conf')
s=p.read_text();line='    include /etc/nginx/snippets/dafeiyu-feedback.conf;'
anchor='    include /etc/nginx/snippets/dafeiyu-updates.conf;'
if line not in s:
    if s.count(anchor)!=1: raise SystemExit('Expected one existing update include; inspect nginx config manually')
    p.write_text(s.replace(anchor,anchor+'\n'+line))
PY
if ! nginx -t; then
    for file in /etc/nginx/conf.d/dash-bj-443.conf /etc/nginx/snippets/dafeiyu-feedback.conf /etc/nginx/conf.d/dafeiyu-feedback-rate.conf; do
        if test -f "$backup/$(basename "$file")"; then cp -p "$backup/$(basename "$file")" "$file"; else rm -f -- "$file"; fi
    done
    echo "Nginx validation failed; active configuration was not reloaded. Backup: $backup" >&2
    exit 1
fi
systemctl daemon-reload
systemctl enable --now dafeiyu-feedback
systemctl restart dafeiyu-feedback
for i in $(seq 1 20); do
    if curl --fail --silent http://127.0.0.1:8790/dafeiyu-feedback/health >/dev/null; then break; fi
    sleep 1
done
curl --fail --silent http://127.0.0.1:8790/dafeiyu-feedback/health
systemctl reload nginx
systemctl enable --now dafeiyu-feedback-backup.timer
systemctl start dafeiyu-feedback-backup.service
printf '\nDeployed. Backup: %s\n' "$backup"
