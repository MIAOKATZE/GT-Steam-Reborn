"""Verify that native GT machine descriptions have no custom replacement layer."""
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/main/java/com/miaokatze/gtsr/client/tooltip'
OUT = ROOT / 'build/v2013-tooltips'
OUT.mkdir(parents=True, exist_ok=True)
for name in ('GTSRMachineTooltips', 'GTSRMachineTooltipProfiles', 'GTSRMachineTooltipNotes'):
    assert not (SOURCE / (name + '.java')).exists(), name
handler = (SOURCE / 'GTSRTooltipHandler.java').read_text(encoding='utf-8')
assert 'rewrite(' not in handler and 'GuiScreen' not in handler
languages = []
for locale in ('zh_CN', 'en_US'):
    rows = (ROOT / f'src/main/resources/assets/gtsr/lang/{locale}.lang').read_text(encoding='utf-8').splitlines()
    assert not any(row.startswith('gtsr.tooltip.machine.') for row in rows)
    native = dict(row.split('=', 1) for row in rows if row.startswith('gtsr.critical.tooltip.native.'))
    assert native and all(value.strip() and '?' not in value for value in native.values())
    languages.append(native)
assert languages[0].keys() == languages[1].keys()
source = (ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical/CriticalTooltips.java').read_text(encoding='utf-8')
for machine in ('loom', 'solar', 'turbine', 'sun', 'battery'):
    assert f'private static void {machine}(' in source
assert 'beginStructureBlock' in source and 'toolTipFinisher' in source
assert all(f'EnumChatFormatting.{color}' in source for color in ('AQUA', 'GOLD', 'GREEN', 'RED', 'LIGHT_PURPLE'))
result = {'status': 'PASS', 'nativeKeysPerLanguage': len(languages[0]),
          'scope': 'Source routing and bilingual native tooltip keys; no game rendering or physical input'}
(OUT / 'native-tooltip-contract.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
print(json.dumps(result))
