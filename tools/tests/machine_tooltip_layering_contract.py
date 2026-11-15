"""The two authored controllers opt in; other machines retain GT's native description path."""
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
assert re.search(r'if \(machine\.getPresetKind\(\) == CriticalMachineKind\.SOLAR\)\s*\{\s*GTSRSolarBoilerTooltip\.apply\(event\.itemStack, event\.toolTip\);\s*return;', handler)
assert re.search(r'else if \(meta instanceof MTEMegaSteamTurbineArray\)\s*\{\s*GTSRMegaTurbineTooltip\.apply\(event\.itemStack, event\.toolTip\);\s*return;', handler)
assert handler.index('GTSRMegaTurbineTooltip.apply') < handler.index('MachineTooltipPages.apply')
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
# The Builder and both translated sets jointly supply the colours, including the native fallback.
for native in languages:
    for name, code in [('AQUA', 'b'), ('GOLD', '6'), ('GREEN', 'a'), ('RED', 'c'), ('LIGHT_PURPLE', 'd')]:
        assert f'EnumChatFormatting.{name}' in source or any('§' + code in line for line in native.values()), name
result = {'status': 'PASS', 'nativeKeysPerLanguage': len(languages[0]),
          'scope': 'Source routing and bilingual native tooltip keys; no game rendering or physical input'}
(OUT / 'native-tooltip-contract.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
print(json.dumps(result))
