"""Record the Python service's state after every write call of the 84 shared scenarios.

tests/com/xiaoxiao/pet/LocalEngineReplay.java replays the same calls through the phone's
LocalEngine and compares. Queries are skipped: retrieval is not part of the Java port.
Usage: python -X utf8 tests/local-engine/replay.py OUT.json [utc_offset_minutes]
"""
from datetime import datetime
import json
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[2] / 'memory-service/upstream'
sys.path.insert(0, str(root / 'src'))
sys.path.insert(0, str(root / 'eval'))
import companion_api as api  # noqa: E402
from run_eval import load_cases  # noqa: E402

offset = int(sys.argv[2]) if len(sys.argv) > 2 else 0
cases = []
for case in load_cases():
    state, sequence, calls = dict(memories=[], archive=[], audit=[]), 0, []
    def call(now, incoming=()):
        global state
        payload = dict(protocol=1, now=now, utc_offset_minutes=offset, state=state, incoming=list(incoming),
                       query='', semantic=False, judge=False)
        response = api.run_isolated(payload)
        state = response['state']
        calls.append(dict(now=now, incoming=list(incoming), state=state, rejected=response['rejected']))
    for session in case['sessions']:
        now = int(datetime.fromisoformat(session['date'] + 'T10:00:00+00:00').timestamp() * 1000)
        for message in session.get('say', []):
            sequence += 1
            call(now, [dict(id='r' + str(sequence), text=message, source='turn' + str(sequence), at=now, pinned=sequence % 5 == 0)])
        call(now + int(13.5 * 3600000))
    cases.append(dict(case=case['id'], offset=offset, calls=calls))
Path(sys.argv[1]).write_text(json.dumps(cases, ensure_ascii=False), encoding='utf-8')
print(len(cases), 'cases,', sum(len(c['calls']) for c in cases), 'calls')
