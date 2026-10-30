#!/usr/bin/env python3
"""Daily private SQLite snapshot; retain the latest seven completed snapshots."""
from contextlib import closing
from datetime import datetime, timezone
import os
from pathlib import Path
import sqlite3

db = Path(os.environ.get('FEEDBACK_DB', '/var/lib/dafeiyu-feedback/feedback.sqlite3')).resolve()
if not db.is_file():
    raise SystemExit('Feedback database does not exist; refusing to create an empty backup')
out = db.parent / 'backups'
out.mkdir(mode=0o700, exist_ok=True)
name = 'feedback-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '.sqlite3'
temp = out / (name + '.part')
with closing(sqlite3.connect(str(db))) as source, closing(sqlite3.connect(str(temp))) as target:
    source.backup(target)
os.chmod(temp, 0o600)
temp.replace(out / name)
for old in sorted(out.glob('feedback-*.sqlite3'), reverse=True)[7:]:
    if old.is_file() and not old.is_symlink() and old.resolve().parent == out.resolve():
        old.unlink()
print('Feedback backup complete')
