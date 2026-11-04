"""Export the offline report by compiling selected production Java methods, without Gradle.

Run: python tools/turbine-report/export_report.py [--check]
The generated HTML includes its data; file:// needs neither fetch nor a web server.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "plan/临时计划/20261103-动力阵列与巨型轮机重构预览/performance.html"
MACHINE = ROOT / "src/main/java/com/miaokatze/gtsr/common/machine"
GT_VERSION = "5.09.54.265"


def block(text, pattern):
    """Read one named Java declaration; production snippets contain balanced braces."""
    start = re.search(pattern, text).start()
    opening = text.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (text[end] == "{") - (text[end] == "}")
        end += 1
    return text[start:end]


def clean(text):
    return re.sub(r"//[^\n]*|/\*.*?\*/", "", text, flags=re.S)


def method(text, name):
    return block(clean(text), rf"(?:public|private|protected)\s+(?:static\s+)?[\w<>\[\]]+\s+{name}\s*\(")


def constant(text, name):
    return re.search(rf"(?:public|private) static final [\w\[\]]+ {name}\s*=.*?;", clean(text), re.S)[0]


def expression(text, pattern):
    return re.search(pattern, clean(text), re.S)[1].strip()


def voltages():
    cache = Path.home() / ".gradle/caches/modules-2/files-2.1/com.github.GTNewHorizons/GT5-Unofficial" / GT_VERSION
    jar = next(cache.glob("*/*-dev.jar"))
    bytecode = subprocess.check_output(
        ["javap", "-classpath", str(jar), "-c", "-p", "gregtech.api.enums.GTValues"], text=True, encoding="utf-8"
    ).split("static {};", 1)[1]
    v_code = bytecode.split("newarray       long", 1)[1].split("// Field V:[J", 1)[0]
    values = [int(n) for n in re.findall(r"// long (\d+)l", v_code)]
    assert len(values) == 16, "GTValues.V initializer changed; review the dedicated extractor"
    vn_code = bytecode.split("// Field VN:[Ljava/lang/String;", 1)[0]
    names = re.findall(r"// String (\S+)", vn_code)[-16:]
    assert len(names) == 16 and names[0] == "ULV" and names[12] == "UMV"
    return values, names, jar


def generate():
    mega = (MACHINE / "MTEMegaSteamTurbineArray.java").read_text(encoding="utf-8")
    kpa = (MACHINE / "MTEKineticProcessingArray.java").read_text(encoding="utf-8")
    steam = (MACHINE / "turbine/SteamTurbineSteamTypes.java").read_text(encoding="utf-8")
    heavy = (MACHINE / "turbine/HeavySteamTurbineCycle.java").read_text(encoding="utf-8")
    values, names, jar = voltages()
    snippets = {}

    def take(source, label, name):
        value = method(source, name)
        snippets[f"{label}.{name}"] = value
        return value

    constants = "\n".join(constant(mega, n) for n in [
        "MAX_EXTRA_STACKS", "SINGULARITY_EFFICIENCY_BONUS", "SINGULARITY_SAVINGS_BONUS",
        "SINGULARITY_POWER_MULTIPLIER", "CRITICAL_POWER_MULTIPLIER", "CRITICAL_EFFICIENCY_BONUS", "CRITICAL_SAVINGS_BONUS",
    ])
    power_parameters = constant(mega, "POWER_PARAMETERS")
    steam_enum = block(clean(steam), r"public enum SteamType\b")
    steam_methods = "\n".join(take(steam, "SteamTypes", n) for n in ["effectiveSteamEffFactor", "effectiveSteamEuPerL"])
    mega_methods = "\n".join(take(mega, "Mega", n) for n in [
        "getSingularityPowerMult", "getSingularityEfficiencyBonus", "getSingularitySavingsBonus",
        "setMaterialTier", "getGroupCount", "getMaxEfficiencyLimit", "getSteamSavings", "getVoltage",
        "isCycleOverlimitActive", "getEffectiveSteamEffFactor", "getEffectiveSteamEuPerL", "calcSteamConsumption",
    ])
    base = expression(mega, r"long base = (.*?);")
    output = expression(method(mega, "onRunningTick"), r"addEnergyOutput\((.*?)\);")
    high_tier = expression(mega, r"boolean canUseHighTier = (.*?);")
    snippets["Mega.baseEUt"] = base
    snippets["Mega.outputEUt"] = output
    snippets["Mega.canUseHighTier"] = high_tier
    kpa_methods = "\n".join(take(kpa, "KPA", n) for n in [
        "setMaterialTier", "getEUtDiscount", "getDurationModifier", "getMaxRecipeVoltage", "getSteamConsumptionRate",
    ])
    parallel = expression(method(kpa, "checkInternalMachine"), r"maxParallel = (.*?);")
    per_amp = expression(method(kpa, "checkProcessing"), r"mSteamPerAmp = (.*?);")
    snippets["KPA.maxParallel"] = parallel
    snippets["KPA.steamPerAmp"] = per_amp
    snippets["Mega.constants"] = constants + power_parameters
    snippets["SteamTypes.SteamType"] = steam_enum
    rates = constant(kpa, "STEAM_CONSUMPTION_RATES")
    snippets["KPA.STEAM_CONSUMPTION_RATES"] = rates
    heavy_class = block(heavy, r"public final class HeavySteamTurbineCycle\b").replace(
        "public final class HeavySteamTurbineCycle", "static final class HeavySteamTurbineCycle", 1)
    snippets["HeavySteamTurbineCycle"] = heavy_class
    java = f"""
public class ReportProbe {{
 static class GTValues {{ static final long[] V = {{{','.join(str(v)+'L' for v in values)}}}; }}
 static class SteamTurbineSteamTypes {{ {steam_enum} {steam_methods} }}
 static class Mega {{
  int mStackCount, mCasingTier, mMaterialTier, mPipeTier, mGearTier, mSingularityMode, mPowerParameter;
  boolean chip;
  {constants} {power_parameters}
  boolean hasCycleOverlimitChip() {{ return chip; }}
  {mega_methods}
  void row(SteamTurbineSteamTypes.SteamType type) {{
   long voltage=getVoltage(); int groupCount=getGroupCount(), powerMult=getSingularityPowerMult();
   long mFullBaseEUt={base}; int mEfficiency=getMaxEfficiencyLimit(type);
   long output={output};
   System.out.println("M,"+mCasingTier+","+mMaterialTier+","+mStackCount+","+(type.ordinal()-1)+","+mPowerParameter+","+mSingularityMode+","+(chip?1:0)+","+mEfficiency+","+getSteamSavings()+","+mFullBaseEUt+","+output+","+calcSteamConsumption(type)+","+isCycleOverlimitActive()+","+getEffectiveSteamEffFactor(type)+","+(!type.requiresHighTier() || ({high_tier})));
  }}
 }}
 static class KPA {{
  int mCasingTier, mMachineTier, mGlassTier=99, mMaterialTier, mPipeTier, mGearTier;
  Object internalMachineStack=new Object(); boolean boost;
  boolean isBoostModeActive() {{return boost;}}
  {rates} {kpa_methods}
  int parallel(int mStackSize) {{ return {parallel}; }}
  void row() {{long effectiveV=getMaxRecipeVoltage(); double rate=getSteamConsumptionRate();
   System.out.println("K,"+mCasingTier+","+mMachineTier+","+(boost?1:0)+","+effectiveV+","+rate+","+({per_amp})+","+parallel(1)+","+parallel(64));
  }}
 }}
 {heavy_class}
 public static void main(String[] args) {{
  Mega m=new Mega();
  for(int c=1;c<=12;c++) for(int mat=1;mat<=3;mat++) for(int k=0;k<=4;k++)
   for(var type:SteamTurbineSteamTypes.SteamType.values()) {{if(type.ordinal()==0)continue;
    for(int power:Mega.POWER_PARAMETERS) for(int mode=0;mode<=2;mode++) for(int chip=0;chip<=1;chip++) {{
     m.mCasingTier=c;m.setMaterialTier(mat);m.mStackCount=k;m.mPowerParameter=power;m.mSingularityMode=mode;m.chip=chip==1;m.row(type);
    }}
   }}
  KPA a=new KPA();
  for(int c=1;c<=12;c++) for(int t=0;t<=12;t++) for(int b=0;b<=1;b++) {{a.mCasingTier=c;a.mMachineTier=t;a.boost=b==1;a.row();}}
  for(int mat=1;mat<=3;mat++) {{a.setMaterialTier(mat);System.out.println("D,"+mat+","+a.getEUtDiscount()+","+a.getDurationModifier());}}
  for(int profile=0;profile<3;profile++) {{
   int tier=profile==0?1:2; boolean heated=profile==2;
   int limit=HeavySteamTurbineCycle.efficiencyLimit(tier,heated), rated=HeavySteamTurbineCycle.ratedSteam(tier,heated);
   int seconds=0, efficiency=0;
   while(efficiency<limit) {{ efficiency=HeavySteamTurbineCycle.warmUp(efficiency,limit,true); seconds++; if(seconds>10000)throw new AssertionError("Warm-up never reaches limit"); }}
   for(int flow:new int[]{{25,50,100}}) {{
    var result=HeavySteamTurbineCycle.settle(tier,heated,limit,0,rated*flow/100,1_000_000L,rated);
    System.out.println("H,"+profile+","+HeavySteamTurbineCycle.output(tier)+","+rated+","+limit+","+seconds+","+flow+","+result.steam()+","+result.output()+","+result.efficiency());
   }}
  }}
 }}
}}
"""
    # Qualify this nested enum so the copied machine methods compile in the standalone probe.
    java = re.sub(r"(?<![\w.])SteamType\b", "SteamTurbineSteamTypes.SteamType", java)
    java = java.replace("enum SteamTurbineSteamTypes.SteamType", "enum SteamType").replace(
        "SteamTurbineSteamTypes.SteamType(float", "SteamType(float")
    with tempfile.TemporaryDirectory(prefix="gtsr-turbine-report-") as directory:
        source = Path(directory) / "ReportProbe.java"
        source.write_text(java, encoding="utf-8")
        subprocess.run(["javac", "-encoding", "UTF-8", str(source)], check=True)
        raw = subprocess.check_output(["java", "-cp", directory, "ReportProbe"], text=True, encoding="utf-8")
    rows, kpa_rows, modifiers, heavy_rows = [], [], [], []
    for line in raw.splitlines():
        kind, *fields = line.split(",")
        if kind == "M":
            # Long values stay decimal strings: the browser must not round through JS Number.
            rows.append([*map(int, fields[:8]), float(fields[8]), *fields[9:12], fields[12] == "true", float(fields[13]), fields[14] == "true"])
        elif kind == "K":
            kpa_rows.append([*map(int, fields[:4]), float(fields[4]), *map(int, fields[5:])])
        elif kind == "D":
            modifiers.append([int(fields[0]), *map(float, fields[1:])])
        elif kind == "H":
            heavy_rows.append(list(map(int, fields)))
    assert len(rows) == 32400 and len(kpa_rows) == 312 and len(modifiers) == 3
    assert all(int(row[11]) >= 0 and int(row[10]) > 0 for row in rows)
    for row in rows:
        assert row[12] == (row[2] == 4 and row[6] == 1)
        assert row[14] == (row[3] in (0, 2) or row[0] >= 6)
        assert int(row[10]) == int(row[9]) * row[7] // 10000
    assert len(heavy_rows) == 9
    source_paths = [MACHINE / "MTEMegaSteamTurbineArray.java", MACHINE / "MTEKineticProcessingArray.java", MACHINE / "turbine/SteamTurbineSteamTypes.java", MACHINE / "turbine/HeavySteamTurbineCycle.java"]
    data = {
        "schema": 1, "gtVersion": GT_VERSION, "voltages": list(map(str, values)), "voltageNames": names,
        "megaColumns": ["tier", "material", "extra", "steam", "power", "mode", "chip", "efficiency", "savings", "baseEUt", "outputEUt", "steamLt", "chipActive", "steamFactor", "allowed"],
        "mega": rows, "kpa": kpa_rows, "modifiers": modifiers, "heavy": heavy_rows,
        "sources": [{"path": p.relative_to(ROOT).as_posix(), "sha256": hashlib.sha256(p.read_bytes()).hexdigest()} for p in source_paths],
        "snippetSha256": hashlib.sha256(json.dumps(snippets, sort_keys=True).encode()).hexdigest(),
        "gtJarSha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
        "snippets": snippets,
    }
    template = (Path(__file__).parent / "performance.template.html").read_text(encoding="utf-8")
    return template.replace("__REPORT_DATA__", json.dumps(data, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="compile again and compare the existing report without writing")
    args = parser.parse_args()
    expected = generate()
    if args.check:
        assert OUT.read_text(encoding="utf-8") == expected, "Report is stale: run this script without --check"
        print("PASS: 32,400 Mega rows + 312 KPA rows + 9 Heavy rows; source methods, Java numeric behavior and offline snapshot agree.")
    else:
        OUT.write_text(expected, encoding="utf-8")
        print(f"Exported 32,400 Mega rows + 312 KPA rows to {OUT}")


if __name__ == "__main__":
    main()
