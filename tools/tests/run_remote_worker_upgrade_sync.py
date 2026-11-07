#!/usr/bin/env python3
"""Exercise the exact panel upgrade-item initializer against real MUI2/MC classes, without starting Minecraft."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
PANEL = ROOT / 'src/main/java/com/miaokatze/gtsr/common/gui/mui2/worker/RemoteWorkerNodePanel.java'
FIXTURE = ROOT / 'tools/tests/RemoteWorkerUpgradeSyncRegression.java'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath-file', type=Path, required=True)
    parser.add_argument('--minecraft-jar', type=Path, required=True)
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    source = PANEL.read_text(encoding='utf-8')
    # Use the production initializer verbatim, including the client/server guard.
    match = re.search(r'com\.cleanroommc\.modularui\.value\.sync\.GenericSyncValue<ItemStack, \?> upgradeItem\s*=.*?;',
                      source, re.DOTALL)
    if match is None or 'manager.syncValue("worker_upgrade_item", upgradeItem);' not in source:
        raise AssertionError('Production upgrade-item callsite or sync identity changed; review this bounded fixture')
    generated = out / 'UpgradeItemSyncCallsite.java'
    generated.write_text('import net.minecraft.item.ItemStack;\n'
                         'import com.cleanroommc.modularui.value.sync.GenericSyncValue;\n'
                         'final class UpgradeItemSyncCallsite {\n'
                         ' static GenericSyncValue<ItemStack, ?> create(RemoteWorkerUpgradeSyncRegression.Machine machine, '
                         'RemoteWorkerUpgradeSyncRegression.Manager manager) {\n'
                         + match.group(0) + '\nreturn upgradeItem;\n}\n}\n', encoding='utf-8')
    # Jabel's compiler service is unnecessary for this Java fixture and needs its own agent dependencies.
    entries = [entry for entry in args.classpath_file.read_text(encoding='utf-8-sig').strip().split(os.pathsep)
               if 'jabel-javac-plugin' not in entry]
    entries.append(str(args.minecraft_jar.resolve()))
    classpath = os.pathsep.join(entries)
    java = str(args.java_home / 'bin/java') if args.java_home else 'java'
    javac = str(args.java_home / 'bin/javac') if args.java_home else 'javac'
    classes = out / 'classes'
    classes.mkdir(exist_ok=True)
    receipt = {
        'boundary': 'Verbatim production sync initializer; fixture manager/machine; real MUI2 GenericSyncValue, '
                    'ByteBufAdapters.ITEM_STACK, MC ItemStack/NBT/PacketBuffer. No panel lifecycle, GL, TCP or game startup.',
        'panelSHA256': hashlib.sha256(PANEL.read_bytes()).hexdigest(),
        'fixtureSHA256': hashlib.sha256(FIXTURE.read_bytes()).hexdigest(),
        'initializer': match.group(0),
        'dependencies': [entry for entry in entries if 'ModularUI2' in entry or entry.endswith('recompiled_minecraft-1.7.10.jar')],
    }
    commands = [
        ('compile', [javac, '-encoding', 'UTF-8', '-proc:none', '-cp', classpath, '-d', str(classes),
                     str(generated), str(FIXTURE)]),
        ('test', [java, '-cp', str(classes) + os.pathsep + classpath, 'RemoteWorkerUpgradeSyncRegression']),
    ]
    for name, command in commands:
        result = subprocess.run(command, capture_output=True, text=True, errors='replace')
        output = result.stdout + result.stderr
        (out / (name + '.log')).write_text(output, encoding='utf-8')
        print(output, end='')
        receipt[name + 'ExitCode'] = result.returncode
        (out / 'receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
        if result.returncode:
            return result.returncode
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
