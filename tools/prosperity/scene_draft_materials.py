"""Restore only approved draft material factories and their exact texture dependencies."""
import hashlib
import json
from pathlib import Path
import re
import sys
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
REPORT = (ROOT / sys.argv[1]).resolve() if len(sys.argv)>1 else ROOT / 'temp/scene-refine-v70'
JAVA = ROOT / 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/remaster/RemasterBlocks.java'


def main():
    scenes = json.loads((REPORT / 'geometry-report.json').read_text(encoding='utf8'))
    wanted = {key.split('#')[0] for scene in scenes for key in scene['materials'] if ':draft' in key}
    current = JAVA.read_text(encoding='utf8')
    original = subprocess.check_output(['git', 'show', 'v1.20.67:' + JAVA.relative_to(ROOT).as_posix()], cwd=ROOT).decode('utf8')
    pattern = re.compile(r'    private static RemasterBlock block(\d+)\(\) \{.*?(?=\n    private static RemasterBlock block|\n\}\s*$)', re.S)
    existing = set(re.findall(r'new RemasterBlock\(\s*"(gtsr:[a-z0-9_]+)"', current))
    restored = []
    textures = ROOT / 'src/main/resources/assets/gtsr/textures/blocks'
    archived = ROOT / 'temp/compact-rebuild/retired-production/textures'
    texture_records = []
    for match in pattern.finditer(original):
        block = re.search(r'"(gtsr:[a-z0-9_]+)"', match.group()).group(1)
        if block not in wanted or block in existing:
            continue
        anchor = '        if (!REGISTRY.isEmpty()) return;'
        if current.count(anchor) != 1:
            raise ValueError('Missing or ambiguous registration insertion point')
        current = current.replace(anchor, anchor + '\n        register(block' + match.group(1) + '());', 1)
        current = current.rstrip()[:-1] + '\n' + match.group() + '\n\n}\n'
        restored.append(block)
        for texture in sorted(set(re.findall(r'remaster_[0-9a-f]{16}', match.group()))):
            target = textures / (texture + '.png')
            if not target.exists():
                source = archived / target.name
                if source.exists():
                    shutil.copy2(source, target)
                else:
                    target.write_bytes(subprocess.check_output(['git', 'show', 'v1.20.67:' + target.relative_to(ROOT).as_posix()], cwd=ROOT))
                texture_records.append({'file': target.relative_to(ROOT).as_posix(), 'sha256': hashlib.sha256(target.read_bytes()).hexdigest()})
    actual = set(re.findall(r'new RemasterBlock\(\s*"(gtsr:[a-z0-9_]+)"', current))
    assert wanted <= actual, sorted(wanted - actual)
    code = re.sub(r'/\*.*?\*/|//[^\n]*', '', current, flags=re.S)
    methods = {block: index for index, block in re.findall(
        r'private static RemasterBlock block(\d+)\(\)\s*\{\s*return new RemasterBlock\(\s*"(gtsr:[a-z0-9_]+)"', code)}
    registration = re.search(r'public static void registerBlocks\(\)\s*\{(.*?)\n    \}', code, re.S)
    if registration is None:
        raise ValueError('Missing registerBlocks method')
    registered = set(re.findall(r'register\(block(\d+)\(\)\);', registration.group(1)))
    assert all(methods[block] in registered for block in wanted), 'Required factory is not registered'
    JAVA.write_text(current, encoding='utf8')
    record = {'requiredDraftMaterials': sorted(wanted), 'restoredFactories': restored, 'restoredTextures': texture_records}
    (REPORT / 'material-report.json').write_text(json.dumps(record, indent=2), encoding='utf8')
    print(json.dumps({'requiredDraftMaterials': len(wanted), 'restoredFactories': len(restored), 'restoredTextures': len(texture_records)}))


if __name__ == '__main__':
    main()
