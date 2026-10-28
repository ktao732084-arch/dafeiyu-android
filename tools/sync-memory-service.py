"""Vendor the reviewed Python engine, adapter and unchanged eval cases.
Usage: python tools/sync-memory-service.py --source ../memory-review
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import re

parser = argparse.ArgumentParser()
parser.add_argument('--source', required=True)
args = parser.parse_args()
source = Path(args.source).resolve()
root = Path(__file__).resolve().parents[1]
target = root / 'memory-service' / 'upstream'
files = [source / 'LICENSE', source / 'eval/run_eval.py', source / 'eval/run_companion_eval.py', source / 'eval/README.md']
files += list((source / 'src').glob('*.py'))
files += list((source / 'eval/cases').glob('*.json'))
files += [source / 'tests' / name for name in ('test_companion_api.py', 'test_agent_features.py', 'test_dedup_regression.py', 'test_embeddings.py', 'test_llm_judge.py')]
manifest = []
for path in sorted(files):
    relative = path.relative_to(source)
    dest = target / relative
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(path.read_bytes().replace(b'\r\n', b'\n'))
    manifest.append(dict(path=relative.as_posix(), sha256=hashlib.sha256(dest.read_bytes()).hexdigest()))
revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=source, text=True).strip()
engine_revision = re.search(r"ENGINE_REVISION = '([a-f0-9]{40})'", (source / 'src/companion_api.py').read_text(encoding='utf-8')).group(1)
(target.parent / 'UPSTREAM.json').write_text(json.dumps(dict(repository='https://github.com/ktao732084-arch/agent-anthropomorphic-memory',
    revision=revision, reviewed_engine_revision=engine_revision,
    note='Adapter and shared replay harness are integration additions; source snapshot hashes include them.', files=manifest), ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'Copied {len(files)} source files; no credentials or user state included.')
