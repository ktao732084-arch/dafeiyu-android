"""Refresh the publication inventory while preserving inherited attribution."""
import hashlib
import json
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
path = root / 'PROVENANCE.json'
previous = json.loads(path.read_text(encoding='utf-8'))
by_path = {r['path']: r for r in previous['files']}
upstream = json.loads((root / 'memory-service/UPSTREAM.json').read_text(encoding='utf-8'))
paths = subprocess.check_output(['git', 'ls-files', '-co', '--exclude-standard', '-z'], cwd=root).decode().split('\0')
binary = {'.mp4', '.png', '.jpg', '.webp', '.ttf', '.otf', '.ico'}
rows = []
for name in sorted(set(paths) - {'', 'PROVENANCE.json'}):
    file = root / name
    if not file.is_file():
        continue
    row = by_path.get(name, dict(path=name, relationship='project-added', direct_source='This project, AI-assisted integration and implementation'))
    if name.startswith('memory-service/upstream/'):
        relative = name[len('memory-service/upstream/'):]
        row = dict(path=name, relationship='vendored', source_commit=upstream['revision'],
                   direct_source=upstream['repository'] + '/blob/' + upstream['revision'] + '/' + relative,
                   license_scope='MIT; preserved in memory-service/upstream/LICENSE')
    data = file.read_bytes()
    if file.suffix not in binary:
        data = data.replace(b'\r\n', b'\n')
    row['sha256'] = hashlib.sha256(data).hexdigest()
    rows.append(row)
previous.update(version='0.3.0', memory_engine_commit=upstream['revision'],
                scope='Publication inventory, excluding this self-referential manifest and ignored build/user files. Text SHA-256 uses Git LF-normalized contents.', files=rows)
path.write_text(json.dumps(previous, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'Updated {len(rows)} attributed file checksums.')
