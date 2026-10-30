"""Offline native biome-world authority regression, without downloads or a Forge/game launch.

--classpath must supply MCP-named Minecraft/Forge, matching GTSR dev and GT5U jars,
plus existing dependencies (Guava, Log4j, commons-lang3, etc.). Use Java 8.
Optional --baseline-ref and --baseline-snapshot compare the frozen pre-repair sources.
All source adapters are confined to generated files and documented in README-biome-authority.md.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = "src/main/java/com/miaokatze/gtsr/common/dimension/"
CORE = [JAVA + name + ".java" for name in (
    "framework/GTSRBiomeAuthority", "framework/GTSRWorldChunkManager", "framework/GTSRDimensionDef",
    "framework/genlayer/GTSRGenLayerChain", "framework/genlayer/GTSRGenLayerRosterFace",
    "framework/genlayer/GTSRGenLayerSelector")]
CONSUMERS = [JAVA + name + ".java" for name in (
    "framework/GTSRWorldProviderBase", "framework/GTSRChunkProviderBase", "framework/DimensionInterferenceGuard",
    "prosperity/entity/EntityGearPigeon", "prosperity/ruins/ProsperityWorldGenerator",
    "prosperity/ruins/RuinedMachinePlacer", "prosperity/ruins/ProsperityOutpostPlacer",
    "prosperity/ruins/ruin/RuinPlacer", "prosperity/ruins/ruin/StructureBurialTiers",
    "shattered/WorldProviderShatteredLands")]
CONSUMERS += ["src/main/java/com/miaokatze/gtsr/common/commands/GTSRCommand.java"]


def source(args, path, baseline):
    if not baseline:
        return (ROOT / path).read_text(encoding="utf-8")
    if args.baseline_snapshot and (args.baseline_snapshot / path).is_file():
        return (args.baseline_snapshot / path).read_text(encoding="utf-8")
    return subprocess.check_output(["git", "show", args.baseline_ref + ":" + path], cwd=ROOT).decode("utf-8")


def adapt(name, code):
    if name == "GTSRWorldChunkManager.java":
        code = code.replace("List<BiomeGenBase> biomes,", "List biomes,").replace("List<BiomeGenBase> biomes)", "List biomes)")
    if name == "GTSRWorldProviderBase.java":
        code = code.replace("@Override\n    public abstract Vec3 getSkyColor", "public abstract Vec3 getSkyColor")
    if name == "WorldProviderShatteredLands.java":
        code = code.replace("@Override\n    public boolean canDoLightning", "public boolean canDoLightning")
    if name == "GTSRChunkProviderBase.java":
        code = code.replace("func_147416_a(", "findClosestStructure(").replace("field_150604_aj", "topBlockMetadata")
    if name == "GTSRCommand.java":
        code = code.replace("public class GTSRCommand extends CommandBase {", "public class GTSRCommand extends CommandBase {\n"
                            " public int compareTo(Object other) { return super.compareTo((net.minecraft.command.ICommand) other); }")
    return code


def run(args, work, name, cp, extra=(), expected_failure=False):
    result = subprocess.run([args.java, "-cp", cp, name, *extra], capture_output=True, text=True)
    output = result.stdout + result.stderr
    (work / (name + ("-" + extra[0] if extra else "") + ".log")).write_text(output, encoding="utf-8")
    print(work.name + "/" + output.strip())
    if expected_failure:
        signature = ("AssertionError: Client manager construction replaced server authority"
                     if expected_failure is True else expected_failure)
        if result.returncode == 0 or signature not in output:
            raise RuntimeError("Expected native failure was not reproduced: " + signature)
    elif result.returncode:
        raise RuntimeError("Regression failed: " + str(work / (name + ".log")))
    return output


def compile_sources(args, work, files, cp):
    classes = work / "classes"
    classes.mkdir(exist_ok=True)
    result = subprocess.run([args.javac, "-encoding", "UTF-8", "-proc:none", "-cp", cp, "-d", str(classes),
                             *map(str, files)], capture_output=True, text=True)
    (work / "compile.log").write_text(result.stdout + result.stderr, encoding="utf-8")
    if result.returncode:
        raise RuntimeError("Compilation failed (not a regression result): " + str(work / "compile.log"))
    return str(classes) + os.pathsep + cp


def check_routing():
    """Fail closed on new unreviewed spatial consumers, rather than silently accepting a global one."""
    spatial_files = set()
    for path in (ROOT / "src/main/java").rglob("*.java"):
        code = re.sub(r"/\*.*?\*/|//[^\n]*", "", path.read_text(encoding="utf-8"), flags=re.S)
        if re.search(r"GTSRBiomeAuthority\s*\.\s*bind\s*\(", code):
            raise AssertionError("production legacy binding: " + str(path))
        if re.search(r"\.(?:ordinalAt|microStrengthAt|nearestBiomeChunk)\(", code):
            relative = path.relative_to(ROOT).as_posix()
            if relative == JAVA + "framework/GTSRWorldChunkManager.java":
                continue  # Source implementation delegates to this manager, not a global authority.
            spatial_files.add(relative)
            # Inspect each enclosing method, not just a file-level mention of forWorld.
            methods = []
            for signature in re.finditer(r"(?m)^[ \t]+(?:(?:public|protected|private|static|final|synchronized)\s+)*[\w<>\[\].]+\s+\w+\([^;{}]*?\)\s*\{", code):
                depth, end = 1, signature.end()
                while depth and end < len(code):
                    depth += (code[end] == "{") - (code[end] == "}")
                    end += 1
                methods.append((signature.start(), end))
            for call in re.finditer(r"\.(?:ordinalAt|microStrengthAt|nearestBiomeChunk)\(", code):
                enclosing = [(a, b) for a, b in methods if a <= call.start() < b]
                if not enclosing:
                    raise AssertionError("unreviewed spatial call outside a method: " + relative)
                a, b = min(enclosing, key=lambda bounds: bounds[1] - bounds[0])
                body = code[a:b]
                if "GTSRBiomeAuthority.forWorld(" not in body or re.search(
                        r"GTSRBiomeAuthority\.(?:forDimKey|forDimension)\(", body):
                    raise AssertionError("spatial method may consume a legacy source: " + relative)
        if re.search(r"GTSRBiomeAuthority\.(?:forDimKey|forDimension)\([^;]+?\)\s*\.(?:ordinalAt|microStrengthAt|nearestBiomeChunk)\(", code):
            raise AssertionError("global spatial lookup: " + str(path))
    expected = {JAVA + name + ".java" for name in (
        "framework/GTSRChunkProviderBase", "framework/DimensionInterferenceGuard",
        "prosperity/entity/EntityGearPigeon", "prosperity/ruins/ProsperityWorldGenerator",
        "shattered/WorldProviderShatteredLands")}
    expected.add("src/main/java/com/miaokatze/gtsr/common/commands/GTSRCommand.java")
    if spatial_files != expected:
        raise AssertionError("Review new spatial callsites: " + str(spatial_files ^ expected))
    authority = (ROOT / (JAVA + "framework/GTSRBiomeAuthority.java")).read_text(encoding="utf-8")
    if re.search(r"private\s+static[^;]*(?:Map|Set|List)<[^;]*(?:World|Source|GTSRWorldChunkManager)", authority):
        raise AssertionError("static world/source retention reintroduced")
    print("PASS routing: no production legacy bind/global spatial query; six reviewed exact-world consumer files")


def suite(args, output, baseline):
    mode = "baseline" if baseline else "patched"
    core = output / (mode + "-core")
    core.mkdir(parents=True, exist_ok=True)
    core_files = []
    for relative in CORE:
        target = core / Path(relative).name
        target.write_text(adapt(target.name, source(args, relative, baseline)), encoding="utf-8")
        core_files.append(target)
    logger = core / "GTSteamReborn.java"
    logger.write_text('package com.miaokatze.gtsr.main; public class GTSteamReborn { public static final '
                      'org.apache.logging.log4j.Logger LOG=org.apache.logging.log4j.LogManager.getLogger("BiomeFixture"); }')
    core_files += [logger, ROOT / "tools/dim1/BiomeAuthorityWorldScopeCheck.java",
                   ROOT / "tools/dim1/GenLayerIsolationCheck.java"]
    if not baseline:
        core_files.append(ROOT / "tools/dim1/BiomeAllocationReplacementCheck.java")
    core_cp = compile_sources(args, core, core_files, args.classpath)
    if baseline:
        run(args, core, "GenLayerIsolationCheck", core_cp, ("pool-only",),
            expected_failure="GTSR reclaimed a live external IntCache array")
        run(args, core, "GenLayerIsolationCheck", core_cp, ("window-only",),
            expected_failure="unaligned odd window")
    scope = run(args, core, "BiomeAuthorityWorldScopeCheck", core_cp, expected_failure=baseline)
    if not baseline:
        run(args, core, "BiomeAllocationReplacementCheck", core_cp)
        run(args, core, "GenLayerIsolationCheck", core_cp)
    consumers = output / (mode + "-consumers")
    consumers.mkdir(exist_ok=True)
    files = []
    for relative in CONSUMERS:
        target = consumers / Path(relative).name
        target.write_text(adapt(target.name, source(args, relative, baseline)), encoding="utf-8")
        files.append(target)
    shim = consumers / "ServerConfigurationManager.java"
    shim.write_text('package net.minecraft.server.management; public class ServerConfigurationManager {'
                    'public void transferPlayerToDimension(net.minecraft.entity.player.EntityPlayerMP p,int d,'
                    'net.minecraft.world.Teleporter t){throw new UnsupportedOperationException("compile only");}}')
    files += [shim, ROOT / "tools/dim1/BiomeAuthorityPlannerParityCheck.java"]
    if not baseline:
        files.append(ROOT / "tools/dim1/BiomeAuthorityConsumerCheck.java")
    cp = compile_sources(args, consumers, files, core_cp)
    planners = run(args, consumers, "BiomeAuthorityPlannerParityCheck", cp, (mode,))
    if not baseline:
        run(args, consumers, "BiomeAuthorityConsumerCheck", cp)
    return re.findall(r"SERVER_ONLY_FINGERPRINT=(-?\d+)", scope), re.findall(r"PLANNER_FINGERPRINT seed=(-?\d+) hash=(-?\d+)", planners)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath", required=True)
    parser.add_argument("--java", default="java")
    parser.add_argument("--javac", default="javac")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--baseline-ref")
    parser.add_argument("--baseline-snapshot", type=Path)
    args = parser.parse_args()
    check_routing()
    with tempfile.TemporaryDirectory(prefix="gtsr-biome-authority-") as temporary:
        output = args.output.resolve() if args.output else Path(temporary)
        baseline = suite(args, output, True) if args.baseline_ref else None
        patched = suite(args, output, False)
        if baseline is not None and baseline != patched:
            raise AssertionError("Clean-server identity/plane/micro or planner fingerprint changed: " + str((baseline, patched)))
    print("PASS biome authority: native source regression, consumer integration and clean-seed planner parity; no Forge/gameplay launch")


if __name__ == "__main__":
    main()
