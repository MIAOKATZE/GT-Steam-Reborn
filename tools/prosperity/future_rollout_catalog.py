"""Validate current authored rollout facts without inventing entity identities."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
path=ROOT/'plan/prosperity/future-design/catalog.js'
catalog=json.loads(path.read_text(encoding='utf8').split('=',1)[1].strip().rstrip(';'))
assert sum(e.get('implemented') is True for e in catalog['structures'])==61
assert next(e for e in catalog['structures'] if e['id']=='abandoned_city')['designOnly'] is True
for e in catalog['structures']:
 b=e.get('encounter',{}).get('boss',{})
 if b and e.get('implemented'):
  assert b['code'].startswith('di-') and int(b['code'][3:])<=15, e['id']
print('PASS 61 authored structures; existing DI01-DI15 only; city remains design-only')
