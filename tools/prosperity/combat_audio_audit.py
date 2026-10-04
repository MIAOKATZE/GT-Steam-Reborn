"""Read-only registration/file/production-key closure plus independent decoding."""
from pathlib import Path
import json, re
import numpy as np
import soundfile as sf
from combat_audio import keys, sha, canonical

root = Path(__file__).resolve().parents[2]
assets = root / 'src/main/resources/assets/gtsr'
registry = json.loads((assets / 'sounds.json').read_text())
required = set(keys())
assert required <= set(registry)
files = set()
for key, value in registry.items():
    for entry in value['sounds']:
        name = entry if isinstance(entry, str) else entry['name']
        assert ':' not in key
        path = assets / 'sounds' / (name.removeprefix('gtsr:') + '.ogg')
        assert path.exists(), str(path)
        if key in required:
            assert value['category'] == 'hostile' and not entry['stream']
            assert canonical(path.read_bytes()) == path.read_bytes()
            decoded, rate = sf.read(path)
            assert rate == 32000 and decoded.ndim == 1 and np.isfinite(decoded).all()
            assert .5 < len(decoded)/rate < 5
            assert 0.05 < np.max(np.abs(decoded)) < 10**(-1/20)
            files.add(path)
assert files == set((assets/'sounds/combat').glob('*.ogg')), 'Orphan combat OGG'
java = root/'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity'
literal_keys = set()
for path in java.rglob('*.java'):
    for key in re.findall(r'"gtsr:([a-z][a-z0-9_.-]+)"', path.read_text(encoding='utf-8')):
        if not key.endswith('.') and key.split('.')[0] in ('king','hive','colossus','spawner','entity','oathguard','creature','echo'):
            literal_keys.add(key)
assert literal_keys <= set(registry), 'Unregistered Java sound keys: '+str(literal_keys-set(registry))
king = (java/'encounter/EntitySilentKing.java').read_text(encoding='utf-8')
for event in re.findall(r'sound\("([a-z]+)"\)', king):
    assert 'king.'+event in registry
fingerprints = json.loads((root/'temp/combat-v71/audio/fingerprints.json').read_text())
for key, row in fingerprints.items():
    assert sha((assets/'sounds/combat'/(key.replace('.','_')+'.ogg')).read_bytes()) == row['ogg']
result = {'result':'PASS', 'productionAssets':len(files), 'registeredKeys':len(registry),
    'literalJavaKeys':sorted(literal_keys),'dynamicProfilesCovered':len([key for key in required if key.startswith('entity.')])/4,
    'canonicalOggIndependentDecode':'PASS','orphanFiles':0,
    'subjective_audio_audition':'NOT_PERFORMED','forge_openal':'NOT_PERFORMED'}
(root/'temp/combat-v71/audio/closure.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result))
