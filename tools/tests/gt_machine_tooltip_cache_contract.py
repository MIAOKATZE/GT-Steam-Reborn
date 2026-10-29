"""Check registered MTE ownership and exercise GT205's unchanged tooltip methods.

Run with --gt-source <GT5-Unofficial source checkout>. The JVM fixture compiles
the real ItemMachines description methods and SkipGenerateDescription annotation
against bounded language/registry seams; it does not launch Minecraft.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess


ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--gt-source', type=Path, required=True)
args = parser.parse_args()
OUT = ROOT / 'build/gt-machine-tooltip-cache-contract'
OUT.mkdir(parents=True, exist_ok=True)
source_root = ROOT / 'src/main/java/com/miaokatze/gtsr'
loader = (source_root / 'loader/MachineLoader.java').read_text(encoding='utf-8')
classes = sorted(set(re.findall(r'new (MTE\w+)\s*\(', loader)))
sources = {p.stem: p for p in source_root.rglob('*.java')}
assert classes, 'No registered machines discovered'
for name in classes:
    text = sources[name].read_text(encoding='utf-8')
    assert '@IMetaTileEntity.SkipGenerateDescription' in text, name
    assert 'import gregtech.api.interfaces.metatileentity.IMetaTileEntity;' in text, name
    assert not re.search(r'\baddFormattedString\s*\(', text), name

gt = args.gt_source / 'src/main/java/gregtech'
item_path = gt / 'common/blocks/ItemMachines.java'
annotation_path = gt / 'api/interfaces/metatileentity/IMetaTileEntity.java'
item = item_path.read_text(encoding='utf-8')
annotation = annotation_path.read_text(encoding='utf-8')


def method(signature):
    start = item.index(signature)
    body = item.index('{', start)
    depth = 1
    end = body + 1
    while depth:
        depth += (item[end] == '{') - (item[end] == '}')
        end += 1
    return item[start:end]


methods = '\n'.join(method(s) for s in (
    'private void addDescription(',
    'private void registerDescription(@Nullable String[]',
    'private static boolean isSkipGenerateDescription(',
))
real_annotation = re.search(
    r'@Target\(ElementType.TYPE\)\s+@Retention\(RetentionPolicy.RUNTIME\)\s+'
    r'@interface SkipGenerateDescription \{\}', annotation).group()
java = r'''
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;
import java.lang.annotation.*;

public class TooltipCacheContract {
    static int checks;
    @interface Nullable {}
    interface IMetaTileEntity {
        String[] getDescription();
        String getMetaName();
        ANNOTATION
    }
    interface ISecondaryDescribable extends IMetaTileEntity {
        boolean isDisplaySecondaryDescription();
    }
    static final class GTUtility {
        static boolean isStringValid(String value) { return value != null && !value.isEmpty(); }
    }
    static final class GTSplit {
        static final String LB = "\\n";
        static String[] split(String value) { return value.split("\\\\n"); }
        static String[] splitFormatted(String value, Object[] params) {
            return split(String.format(value, params));
        }
    }
    static final class StatCollector {
        static final Map<String, String> values = new HashMap<>();
        static boolean canTranslate(String key) { return values.containsKey(key); }
        static String translateToLocal(String key) { return values.getOrDefault(key, key); }
    }
    static final class GTLanguageManager {
        static final Map<String, String> registered = new HashMap<>();
        static final Map<String, String> disk = new HashMap<>();
        static boolean hasGTLocalizationKey(String key) { return registered.containsKey(key); }
        static void addStringLocalization(String key, String value) {
            String translated = disk.getOrDefault(key, value);
            registered.putIfAbsent(key, translated);
            StatCollector.values.putIfAbsent(key, translated);
        }
    }
    static class Unmarked implements ISecondaryDescribable {
        boolean shift;
        String current = "CURRENT_ZH";
        public String getMetaName() { return "gtsr.fixture"; }
        public boolean isDisplaySecondaryDescription() { return shift; }
        public String[] getDescription() {
            return new String[] {current + (shift ? "_STRUCTURE" : ""), "", null, "100%"};
        }
    }
    @IMetaTileEntity.SkipGenerateDescription
    static final class Owned extends Unmarked {}
    static final class AnnotatedParentOnly extends OwnedParent {}
    @IMetaTileEntity.SkipGenerateDescription
    static class OwnedParent extends Unmarked {}
    static final class ItemMachines {
        METHODS
    }
    static void check(boolean valid, String message) {
        checks++;
        if (!valid) throw new AssertionError(message);
    }
    static String key(Unmarked machine) {
        return "gt.blockmachines." + machine.getMetaName() + ".tooltip" + (machine.shift ? "_secondary" : "");
    }
    static List<String> render(ItemMachines item, Unmarked machine) {
        List<String> result = new ArrayList<>();
        item.addDescription(result, machine, 123);
        return result;
    }
    public static void main(String[] args) {
        ItemMachines item = new ItemMachines();
        Owned owned = new Owned();
        Unmarked external = new Unmarked();
        check(!ItemMachines.isSkipGenerateDescription(new AnnotatedParentOnly()),
            "GT annotation must remain non-inherited; every registered class needs the annotation");
        for (boolean shift : new boolean[] {false, true}) {
            owned.shift = external.shift = shift;
            String key = key(owned);
            GTLanguageManager.disk.put(key, "STALE_DISK");
            StatCollector.values.put(key, "STALE_BASE");
            StatCollector.values.put(key + ".123", "STALE_METADATA");
            for (String language : new String[] {"CURRENT_ZH", "CURRENT_EN", "CURRENT_ZH_RELOADED"}) {
                owned.current = language;
                List<String> rendered = render(item, owned);
                check(rendered.equals(Arrays.asList(owned.getDescription())), "Owned tooltip must display exact current description");
                check(!rendered.contains("STALE_METADATA"), "Old metadata translation must be bypassed");
                check(render(item, external).equals(Collections.singletonList("STALE_METADATA")),
                    "External GT custom metadata translation must remain effective");
            }
            StatCollector.values.remove(key + ".123");
            check(render(item, external).equals(Collections.singletonList("STALE_BASE")),
                "External base translation must remain effective");
            check(GTLanguageManager.disk.get(key).equals("STALE_DISK"), "User disk translation must stay intact");
        }
        item.registerDescription(new String[] {"A", "", null, "B"}, "external", 123);
        check(GTLanguageManager.registered.get("external").equals("A\\nB"), "Original external registration preserved");
        item.registerDescription(new String[] {"C"}, "external", 124);
        check(GTLanguageManager.registered.get("external.124").equals("C"), "Original collision registration preserved");
        check(ItemMachines.isSkipGenerateDescription(owned), "Owned registration skip uses actual GT predicate");
        System.out.println("PASS " + checks + " actual GT method checks");
    }
}
'''.replace('ANNOTATION', real_annotation).replace('METHODS', methods)
java_file = OUT / 'TooltipCacheContract.java'
java_file.write_text(java, encoding='utf-8')
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(OUT), str(java_file)], check=True)
result = subprocess.run(['java', '-cp', str(OUT), 'TooltipCacheContract'],
                        capture_output=True, text=True, check=True)
evidence = {
    'registered_classes': classes,
    'registered_class_count': len(classes),
    'gt_sources': {str(p): hashlib.sha256(p.read_bytes()).hexdigest()
                   for p in (item_path, annotation_path)},
    'result': result.stdout.strip(),
    'boundary': 'GT205 source methods compiled with bounded registry seams; Minecraft rendering is not exercised',
}
(OUT / 'result.json').write_text(json.dumps(evidence, indent=2), encoding='utf-8')
print(f'PASS {len(classes)} registered GTSR concrete machine classes annotated')
print(result.stdout, end='')
