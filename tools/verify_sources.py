"""Read-only comparison against the diagnostic source manifest, outside the game instance's live saves."""
from pathlib import Path
import hashlib
import json

project = Path(__file__).resolve().parents[1]
root = project / 'run-smoke' / 'fixtures'
expected = json.loads((root / 'fixture-hashes.json').read_text(encoding='utf-8'))
actual = {
    path.relative_to(root).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
    for path in sorted(root.rglob('*'))
    if path.is_file() and path.name != 'fixture-hashes.json'
}
result = {
    'baselineFiles': len(expected),
    'observedFiles': len(actual),
    'changed': [name for name in expected if name in actual and expected[name] != actual[name]],
    'missing': sorted(expected.keys() - actual.keys()),
    'added': sorted(actual.keys() - expected.keys()),
}
result['passed'] = not any(result[key] for key in ('changed', 'missing', 'added'))
(project / 'run-smoke' / 'source-integrity.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
print(json.dumps(result))
raise SystemExit(0 if result['passed'] else 1)
