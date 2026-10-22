"""Compile/run only the immutable material domain, with a file-reader geometry seam. No client or GL."""
from pathlib import Path
import ast
import json
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/critical-material-catalog/contract'
SOURCE = ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical'
OUT.mkdir(parents=True, exist_ok=True)
stub = OUT / 'src/com/miaokatze/gtsr/common/critical/CriticalGeometry.java'
stub.parent.mkdir(parents=True, exist_ok=True)
stub.write_text('''package com.miaokatze.gtsr.common.critical;
import java.io.*;import java.nio.charset.StandardCharsets;import com.google.gson.*;
public final class CriticalGeometry {
 public static JsonObject readJson(String relative) {
  try (InputStreamReader reader = new InputStreamReader(CriticalGeometry.class.getResourceAsStream(
   "/assets/gtsr/critical/" + relative), StandardCharsets.UTF_8)) {
   return new JsonParser().parse(reader).getAsJsonObject();
  } catch (IOException ex) { throw new IllegalStateException(ex); }
 }
}''', encoding='utf-8')
gson = next((Path.home() / '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.2.4').glob('*/*.jar'))
classes = OUT / 'classes'
classes.mkdir(exist_ok=True)
sources = [SOURCE / (name + '.java') for name in (
    'CriticalMaterialDefinitions', 'CriticalMaterialCatalog', 'CriticalMachineKind')]
subprocess.run(['javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-cp', str(gson), '-d', str(classes),
                str(stub), *map(str, sources), str(Path(__file__).with_name('CriticalMaterialCatalogContract.java'))],
               cwd=ROOT, check=True)
result = subprocess.run(['java', '-cp', ';'.join(map(str, [classes, gson, ROOT / 'src/main/resources'])),
                         'com.miaokatze.gtsr.common.critical.CriticalMaterialCatalogContract'],
                        cwd=ROOT, check=True, text=True, capture_output=True)
sys.path.insert(0, str(ROOT / 'tools/critical'))
from material_palette import expand_palette, normalize_palette
document = json.loads((ROOT / 'src/main/resources/assets/gtsr/critical/materials.json').read_text(encoding='utf-8'))
assert normalize_palette(expand_palette(document)) == document
assert len(expand_palette(document)) == 371
for relative in ['material_palette.py', 'deduplicate_sprites.py', 'import_r5_v31.py',
                 'verify_rail_render_v28.py', 'materials46/audit.py', 'gates/gravity_caps_v29.py']:
    ast.parse((ROOT / 'tools/critical' / relative).read_text(encoding='utf-8'))
(OUT / 'result.txt').write_text(result.stdout + 'PASS Python normalized/flat round-trip and 6 consumers syntax\n', encoding='utf-8')
print((OUT / 'result.txt').read_text(encoding='utf-8'), end='')
