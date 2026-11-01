"""Small real LaunchWrapper/UniMixins diagnostic; no Minecraft process is launched."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time
import zipfile


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_JDK = Path.home() / ".gradle/jdks/azul_systems__inc_-8-amd64-windows.2"


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument("--jdk", type=Path, default=DEFAULT_JDK)
    parser.add_argument("--pipeline", choices=["early", "late", "absent"])
    parser.add_argument("--side", choices=["CLIENT", "SERVER"], default="CLIENT")
    parser.add_argument("--gtsr-jar", type=Path, help="Use compiled production classes/configs instead of source javac")
    parser.add_argument("--out", type=Path, default=ROOT / "build/launchwrapper-diagnose")
    args = parser.parse_args()
    cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
    libraries = [
        next((cache / "net.minecraft/launchwrapper/1.12").glob("*/*.jar")),
        ROOT / "build/client-testlab-task03-gt265/mods/+unimixins-all-1.7.10-0.3.1.jar",
        next((cache / "net.sf.jopt-simple/jopt-simple/4.5").glob("*/*.jar")),
    ]
    launcher = Path("E:/CodeGT/plan/stage07-20261101/testlab-launcher/libraries")
    libraries += list(launcher.glob("org/apache/logging/log4j/*/2.0-beta9-fixed/*.jar"))
    libraries += [next((cache / "commons-io/commons-io/2.4").glob("*/*.jar"))]
    libraries += [next((cache / "org.apache.commons/commons-lang3/3.3.2").glob("*/*.jar"))]
    libraries += list(launcher.glob("com/google/guava/guava/*/*.jar"))
    libraries += list(launcher.glob("com/google/code/gson/gson/*/*.jar"))
    forge = next(launcher.glob("net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/*.jar"))
    asm = next((cache / "org.ow2.asm/asm-debug-all/5.0.4").glob("*/*.jar"))
    raw_cp = ";".join(map(str, libraries + [forge, asm]))
    personalspace = ROOT / "build/client-testlab-task03-gt265/mods/personalspace-1.0.41.jar"
    with zipfile.ZipFile(personalspace) as jar:
        assert len(jar.read("me/eigenraven/personalspace/world/PersonalWorldProvider.class")) > 0
    out = args.out.resolve()
    out.mkdir(exist_ok=True, parents=True)
    classes = out / "classes"
    classes.mkdir(exist_ok=True)
    cp = ";".join(map(str, libraries))
    if args.pipeline:
        generated = out / "fixture-src"
        fixtures = {
            "net/minecraft/world/World.java": "package net.minecraft.world; public class World {}",
            "net/minecraft/world/WorldProvider.java": "package net.minecraft.world; public class WorldProvider { public World worldObj; }",
            "com/miaokatze/gtsr/common/critical/CriticalNightClock.java": "package com.miaokatze.gtsr.common.critical; public class CriticalNightClock { public static boolean isNight(net.minecraft.world.World world) { return true; } }",
            "diagnostic/trigger/EarlyTrigger.java": "package diagnostic.trigger; public class EarlyTrigger {}",
        }
        for name, source in fixtures.items():
            path = generated / name
            path.parent.mkdir(exist_ok=True, parents=True)
            path.write_text(source, encoding="utf8")
        mixin_source = ROOT / "src/main/java/com/miaokatze/gtsr/mixin/PersonalWorldProviderCriticalNightMixin.java"
        late_loader = ROOT / "src/main/java/com/miaokatze/gtsr/main/GTSRLateMixinLoader.java"
        late_config = (ROOT / "src/main/resources/mixins.gtsr.late.json").read_bytes()
        early_config = (ROOT / "src/main/resources/mixins.gtsr.json").read_bytes()
        if args.gtsr_jar:
            with zipfile.ZipFile(args.gtsr_jar) as jar:
                assert b"mixins.gtsr.late.json" not in jar.read("META-INF/MANIFEST.MF")
                early_config = jar.read("mixins.gtsr.json")
                late_config = jar.read("mixins.gtsr.late.json")
        assert b'"PersonalWorldProviderCriticalNightMixin"' not in early_config
        subprocess.run([str(args.jdk / "bin/javac.exe"), "-proc:none", "-encoding", "UTF-8", "-cp", cp,
                        "-d", str(classes), str(Path(__file__).with_name("MixinPipelineProbe.java")),
                        str(Path(__file__).with_name("ProbeTweaker.java")),
                        *([] if args.gtsr_jar else [str(mixin_source), str(late_loader)]),
                        *map(str, generated.rglob("*.java"))], check=True)
        if args.gtsr_jar:
            with zipfile.ZipFile(args.gtsr_jar) as jar:
                for name in ["com/miaokatze/gtsr/main/GTSRLateMixinLoader.class",
                             "com/miaokatze/gtsr/mixin/PersonalWorldProviderCriticalNightMixin.class",
                             "mixins.gtsr.refmap.json"]:
                    path = classes / name
                    path.parent.mkdir(exist_ok=True, parents=True)
                    path.write_bytes(jar.read(name))
        config = {"required": True, "minVersion": "0.8.5", "package": "com.miaokatze.gtsr.mixin",
                  "target": "@env(DEFAULT)", "compatibilityLevel": "JAVA_8",
                  "mixins": ["PersonalWorldProviderCriticalNightMixin"]}
        (classes / "mixins.probe.early.json").write_text(json.dumps(config), encoding="utf8")
        (classes / "mixins.gtsr.late.json").write_bytes(late_config)
    (out / "log4j2.xml").write_text('<Configuration status="WARN"><Appenders><Console name="Console" target="SYSTEM_OUT"><PatternLayout pattern="%m%n"/></Console></Appenders><Loggers><Root level="debug"><AppenderRef ref="Console"/></Root></Loggers></Configuration>', encoding="utf8")
    subprocess.run([str(args.jdk / "bin/javac.exe"), "-proc:none", "-encoding", "UTF-8", "-cp", raw_cp,
                    "-d", str(classes), str(Path(__file__).with_name("LaunchWrapperProbe.java"))], check=True)
    inputs = libraries + [personalspace, *Path(__file__).parent.glob("*.java"), Path(__file__)]
    if not args.pipeline:
        inputs += [forge, asm]
    if args.pipeline:
        inputs += [args.gtsr_jar] if args.gtsr_jar else [mixin_source, late_loader,
            ROOT / "src/main/resources/mixins.gtsr.json", ROOT / "src/main/resources/mixins.gtsr.late.json"]
    manifest = {str(p.resolve()): hashlib.sha256(p.read_bytes()).hexdigest() for p in inputs}
    (out / "inputs.json").write_text(json.dumps(manifest, indent=2), encoding="utf8")
    mode = args.pipeline or "early"
    cmd = [str(args.jdk / "bin/java.exe"), "-Xms32m", "-Xmx256m", "-XX:-OmitStackTraceInFastThrow",
           "-Dmixin.debug.verbose=true", "-Dmixin.env.disableRefMap=" + str(not args.gtsr_jar).lower(),
           "-Dlog4j.configurationFile=" + (out / "log4j2.xml").as_uri(), "-Dprobe.side=" + args.side,
           "-cp", str(classes) + ";" + (cp if args.pipeline else raw_cp)]
    if args.pipeline:
        cmd += ["net.minecraft.launchwrapper.Launch", "--tweakClass", "diagnostic.launchwrapper.ProbeTweaker",
                mode, str(personalspace), str(out / "PersonalWorldProvider.transformed.class")]
        if mode == "early":
            cmd += ["--mixin", "mixins.probe.early.json"]
    else:
        cmd += ["diagnostic.launchwrapper.LaunchWrapperProbe", mode, str(personalspace)]
    started = time.monotonic()
    result = subprocess.run(cmd, cwd=out, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    duration = time.monotonic() - started
    result_name = f"pipeline-{mode}-{args.side.lower()}" if args.pipeline else "early"
    (out / f"{result_name}.log").write_bytes(result.stdout)
    lines = result.stdout.decode("utf8", errors="replace").splitlines()
    print("\n".join(line for line in lines if any(s in line for s in [
        "PIPELINE_", "ACTUAL_PROVIDER_", "ABSENT_OPTIONAL_", "PRODUCTION_LATE_", "REPRODUCED_",
        "NEGATIVE_", "RESOURCE_AFTER_", "CLASS_BYTES_", "INVALID_AFTER_", "Caused by:", "FML_",
        "Preparing mixins.probe", "Preparing mixins.gtsr", "Mixing PersonalWorldProvider", "Error loading class: me/eigenraven"])))
    if result.returncode:
        print("\n".join(lines[-35:]))
    result.check_returncode()
    marker = b"PIPELINE_REPRODUCED=true" if args.pipeline == "early" else (
        b"ABSENT_OPTIONAL_TARGET=PASS" if mode == "absent" else
        b"ACTUAL_PROVIDER_MIXIN_APPLY=PASS" if args.pipeline else b"REPRODUCED_LW_CNFE_NPE=true")
    assert marker in result.stdout
    checks = {"status": "PASS", "mode": mode, "side": args.side, "duration_seconds": round(duration, 3),
              "expectation": "REPRODUCE_KNOWN_FAILURE" if mode == "early" else (
                  "OPTIONAL_ABSENT_NO_PROBE" if mode == "absent" else "FIXED_MIXIN_APPLIES"),
              "java": subprocess.check_output([str(args.jdk / "bin/java.exe"), "-version"], stderr=subprocess.STDOUT).decode(errors="replace"),
              "heap_mib": 256, "exit_code": result.returncode, "marker": marker.decode(), "inputs": manifest,
              "scope": "Real LaunchWrapper/Mixin prepare/apply and real PersonalSpace bytes; Minecraft/WorldProvider/CriticalNightClock dependencies are isolated stubs, no game launch"}
    (out / f"{result_name}.json").write_text(json.dumps(checks, indent=2), encoding="utf8")


if __name__ == "__main__":
    main()
