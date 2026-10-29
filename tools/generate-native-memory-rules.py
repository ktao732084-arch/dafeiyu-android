"""Generate Java constants from the reviewed upstream AST; never execute Python engine code."""
import ast
import hashlib
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
sources = root / 'memory-service/upstream/src'
def constants(path, class_name=None):
    nodes = ast.parse(path.read_text(encoding='utf-8')).body
    if class_name:
        nodes = next(n for n in nodes if isinstance(n, ast.ClassDef) and n.name == class_name).body
    result = {}
    for node in nodes:
        if isinstance(node, ast.Assign) and isinstance(node.targets[0], ast.Name):
            try:
                result[node.targets[0].id] = ast.literal_eval(node.value)
            except (ValueError, TypeError):
                pass
    return result
memory = constants(sources / 'memory.py')
noise = constants(sources / 'noise_filter.py', 'NoiseFilter')
rules = {k: memory[k] for k in ['IMPORTANCE_RULES', 'EXPLICIT_SIGNALS', 'OVERRIDE_SIGNALS_TIER1', 'OVERRIDE_SIGNALS_TIER2', 'DEDUP_CONFIG']}
rules['FALLBACK_NOISE'] = memory['NOISE_PATTERNS']
for k in ['NOISE_PATTERNS', 'NOISE_KEYWORDS', 'CONVERSATION_NOISE', 'FACT_MARKERS', 'QUESTION_PATTERN', 'DISTRACTION_PATTERNS']:
    rules[k] = noise[k]
rules['SECRET_PATTERNS'] = constants(sources / 'noise_filter.py')['SECRET_PATTERNS']
def java_regex(value):
    if isinstance(value, str):
        return re.sub(r'\\U([0-9A-Fa-f]{8})', lambda m: '\\x{' + m.group(1).lstrip('0') + '}', value)
    if isinstance(value, list):
        return [java_regex(v) for v in value]
    if isinstance(value, dict):
        return {k: java_regex(v) for k,v in value.items()}
    return value
literal = json.dumps(json.dumps(java_regex(rules), ensure_ascii=False, separators=(',', ':')), ensure_ascii=False)
java = '''package com.xiaoxiao.pet;
import org.json.JSONObject;
/** Generated from agent-anthropomorphic-memory (MIT). See docs/native-rules-source.json. */
final class NativeMemoryRules {
    static final JSONObject DATA=load();
    private static JSONObject load(){try{return new JSONObject(%s);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
}
''' % literal
(root / 'src/com/xiaoxiao/pet/NativeMemoryRules.java').write_text(java, encoding='utf-8')
(root / 'docs/native-rules-source.json').write_text(json.dumps(dict(repository='https://github.com/ktao732084-arch/agent-anthropomorphic-memory',
    snapshot=json.loads((root / 'memory-service/UPSTREAM.json').read_text(encoding='utf-8'))['revision'],
    files={n: hashlib.sha256((sources / n).read_bytes()).hexdigest() for n in ['memory.py', 'noise_filter.py']}), indent=2) + '\n', encoding='utf-8')
print('Generated native rule constants from reviewed source.')
