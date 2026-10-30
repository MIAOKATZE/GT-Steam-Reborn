#!/usr/bin/env python3
"""Local BQ/native-input/frontend and complete-source recipe regression runner. Never downloads jars."""
from argparse import ArgumentParser
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
import zipfile

from compat.fixtures import BQ, RECIPE, GRID


def main():
    parser = ArgumentParser(description=__doc__)
    cp = parser.add_mutually_exclusive_group(required=True)
    cp.add_argument('--classpath', help='Explicit os.pathsep-separated native/development jars')
    cp.add_argument('--classpath-file', type=Path, help='File containing the same classpath')
    parser.add_argument('--java-home', type=Path, help='JDK 17+ home (otherwise JAVA_HOME/PATH)')
    parser.add_argument('--source-root', type=Path, help='Alternate repository root/snapshot for regression comparison')
    parser.add_argument('--work-dir', type=Path, help='Output directory; defaults to a new temporary directory')
    parser.add_argument('--suite', choices=['all', 'bq', 'inputs', 'recipes', 'frontend', 'overlay'], default='all')
    parser.add_argument('--timeout', type=int, default=60, help='Per subprocess timeout in seconds')
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[2]
    source = (args.source_root or repo).resolve()
    work = (args.work_dir or Path(tempfile.mkdtemp(prefix='gtsr-compat-'))).resolve()
    work.mkdir(parents=True, exist_ok=True)
    java_home = args.java_home or (Path(os.environ['JAVA_HOME']) if 'JAVA_HOME' in os.environ else None)
    java = str(java_home / 'bin/java') if java_home else shutil.which('java')
    javac = str(java_home / 'bin/javac') if java_home else shutil.which('javac')
    if not java or not javac:
        parser.error('Supply --java-home with a JDK 17+ installation')
    classpath = args.classpath if args.classpath is not None else args.classpath_file.read_text().strip()
    jars = [Path(p).resolve() for p in classpath.split(os.pathsep) if p]
    if any(not p.is_file() or not zipfile.is_zipfile(p) for p in jars):
        parser.error('Classpath entries must be existing local jars; globs/directories are not expanded')
    classpath = os.pathsep.join(map(str, jars))
    (work / 'classpath-manifest.json').write_text(json.dumps([
        {'path': str(p), 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()} for p in jars], indent=2))
    print('Outputs:', work)
    base = source / 'src/main/java/com/miaokatze/gtsr'
    tests = repo / 'tools/tests'
    empty = work / 'empty-sourcepath'
    empty.mkdir(exist_ok=True)
    failures = []
    source_manifest = {}

    def run(name, command):
        try:
            result = subprocess.run(command, capture_output=True, text=True, timeout=args.timeout)
            log = result.stdout + result.stderr
            passed = result.returncode == 0
        except subprocess.TimeoutExpired as exc:
            log = 'TIMEOUT\n' + str(exc)
            passed = False
        (work / (name + '.log')).write_text(log)
        print(log, end='' if log.endswith('\n') else '\n')
        if not passed:
            failures.append(name)
        return passed

    def compile_suite(name, fixtures, sources):
        # Fresh per-suite output prevents stale compiled fixtures contaminating a subsequent run.
        suite = Path(tempfile.mkdtemp(prefix=name + '-', dir=work))
        classes = suite / 'classes'; classes.mkdir()
        fixture_dir = suite / 'fixtures'; fixture_dir.mkdir()
        for filename, content in fixtures.items():
            (fixture_dir / filename).write_text(content)
        inputs = [*sorted(fixture_dir.glob('*.java')), *sources]
        source_manifest[name] = [{'path': str(p), 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()} for p in inputs]
        (work / 'source-manifest.json').write_text(json.dumps(source_manifest, indent=2))
        passed = run(name + '-compile', [javac, '-encoding', 'UTF-8', '-proc:none', '-sourcepath', str(empty),
            '-classpath', classpath, '-d', str(classes), *map(str, inputs)])
        return suite, classes, passed

    def execute(name, classes, main_class, extra=(), cp_override=None):
        return run(name, [java, '-classpath', os.pathsep.join([str(classes), cp_override or classpath]),
            main_class, *map(str, extra)])

    suites = ['bq', 'inputs', 'recipes', 'frontend', 'overlay'] if args.suite == 'all' else [args.suite]
    for suite in suites:
        if suite == 'bq':
            folder, classes, ok = compile_suite('bq', BQ, [base / 'crossmod/bq/BqQuestInjector.java',
                base / 'crossmod/bq/BqCompat.java', tests / 'BqInjectionSafetyRegression.java'])
            if not ok: continue
            absent_jars = []
            for jar in jars:
                with zipfile.ZipFile(jar) as archive:
                    if 'betterquesting/api/questing/IQuest.class' not in archive.namelist():
                        absent_jars.append(str(jar))
            execute('bq-absent', classes, 'BqAbsentCheck', cp_override=os.pathsep.join(absent_jars))
            resources = folder / 'resources'; resources.mkdir()
            run('bq-tests', [java, '-classpath', os.pathsep.join([str(resources), str(classes), classpath]),
                'com.miaokatze.gtsr.crossmod.bq.BqInjectionSafetyRegression', str(resources)])
        elif suite == 'inputs':
            _, classes, ok = compile_suite('inputs', {}, [base / 'loader/recipes/RecipeLoaderUtils.java',
                tests / 'RecipeRequiredInputsRegression.java', tests / 'RecipeOptionalItemRegression.java'])
            if not ok: continue
            execute('inputs-tests', classes, 'com.miaokatze.gtsr.loader.recipes.RecipeRequiredInputsRegression')
            execute('optional-item-tests', classes, 'RecipeOptionalItemRegression')
        elif suite == 'recipes':
            fixtures = recipe_fixtures(base)
            _, classes, ok = compile_suite('recipes', fixtures, [base / 'loader/recipes/RecipeLoaderUtils.java',
                base / 'loader/recipes/HatchRecipes.java', base / 'loader/recipes/MultiblockMachineRecipes.java',
                tests / 'HatchRegistrationProbe.java', tests / 'MultiblockRequiredFluidRegression.java'])
            if not ok: continue
            execute('hatch-tests', classes, 'com.miaokatze.gtsr.loader.recipes.HatchRegistrationProbe')
            execute('multiblock-fluid-tests', classes, 'com.miaokatze.gtsr.loader.recipes.MultiblockRequiredFluidRegression')
        elif suite == 'frontend':
            _, classes, ok = compile_suite('frontend', GRID, [base / 'api/recipe/FluidGridFrontend.java',
                tests / 'FluidGridFrontendRegression.java'])
            if ok: execute('frontend-tests', classes, 'gregtech.api.recipe.FluidGridFrontendRegression')
        elif suite == 'overlay':
            names = ['crossmod/bq/BqQuestInjector.java', 'crossmod/bq/BqCompat.java', 'api/recipe/FluidGridFrontend.java',
                *['loader/recipes/' + n + '.java' for n in ['RecipeLoaderUtils', 'HatchRecipes',
                    'MultiblockMachineRecipes', 'ProcessingMachineRecipes', 'NodeAndHubRecipes']]]
            compile_suite('overlay', {}, [base / name for name in names])
    print('Failed steps:', ', '.join(failures) if failures else 'none')
    raise SystemExit(bool(failures))


def recipe_fixtures(base):
    text = '\n'.join((base / ('loader/recipes/' + n + '.java')).read_text()
        for n in ['HatchRecipes', 'MultiblockMachineRecipes'])
    fixtures = dict(RECIPE)
    for enum, package in [('GTSRItemList', 'com.miaokatze.gtsr.common.api.enums'), ('ItemList', 'gregtech.api.enums'),
        ('GregtechItemList', 'gtPlusPlus.xmod.gregtech.api.enums')]:
        names = sorted(set(re.findall(r'(?<![A-Za-z])' + enum + r'\.(\w+)', text)))
        fixtures[enum + '.java'] = 'package ' + package + ';public enum ' + enum + '{' + ','.join(names) + ''';
            public static String missing;public net.minecraft.item.ItemStack get(long n){return name().equals(missing)?null:
            new net.minecraft.item.ItemStack(net.minecraft.init.Items.iron_ingot,(int)n,ordinal());}}'''
    materials = sorted(set(re.findall(r'(?<![A-Za-z])Materials\.(\w+)', text)))
    fixtures['Materials.java'] = 'package gregtech.api.enums;public enum Materials{' + ','.join(materials) + ''';
        public net.minecraftforge.fluids.FluidStack getMolten(long n){return new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER,(int)n);}
        public net.minecraftforge.fluids.FluidStack getGas(long n){return getMolten(n);}}'''
    prefixes = sorted(set(re.findall(r'OrePrefixes\.(\w+)', text)))
    fixtures['OrePrefixes.java'] = 'package gregtech.api.enums;public enum OrePrefixes{' + ','.join(prefixes) + '}'
    tiers = sorted(set(re.findall(r'TierEU\.(\w+)', text)))
    fixtures['TierEU.java'] = 'package gregtech.api.enums;public class TierEU{' + ''.join('public static final int ' + n + '=32;' for n in tiers) + '}'
    fixtures['MaterialsAlloy.java'] = fixtures['MaterialsAlloy.java'].replace('getPlate(int n)', 'getFrameBox(int n)')[:-1] + 'public net.minecraft.item.ItemStack getPlate(int n){return getFrameBox(n);}}'
    fixtures['GTModHandler.java'] = fixtures['GTModHandler.java'][:-1] + 'public static net.minecraft.item.ItemStack getModItem(String a,String b,long n){return new net.minecraft.item.ItemStack(net.minecraft.init.Items.iron_ingot,(int)n);}}'
    fixtures['GTValues.java'] = fixtures['GTValues.java'].replace('public static final RA', 'public static final net.minecraftforge.fluids.FluidStack[] emptyFluidStackArray=new net.minecraftforge.fluids.FluidStack[0];public static final RA')
    fixtures['GTRecipeBuilder.java'] = '''package gregtech.api.util;public class GTRecipeBuilder {
        public static final int SECONDS=20,INGOTS=144;public static int registrations;
        public static net.minecraft.item.ItemStack[] lastInputs;
        public static final java.util.Map<Integer,net.minecraftforge.fluids.FluidStack[]> byOutput=new java.util.HashMap<>();
        private net.minecraft.item.ItemStack[] inputs,outputs;private net.minecraftforge.fluids.FluidStack[] fluids=new net.minecraftforge.fluids.FluidStack[0];
        public GTRecipeBuilder circuit(int n){return this;}
        public GTRecipeBuilder itemInputs(net.minecraft.item.ItemStack... a){inputs=a;return this;}
        public GTRecipeBuilder itemOutputs(net.minecraft.item.ItemStack... a){outputs=a;return this;}
        public GTRecipeBuilder fluidInputs(net.minecraftforge.fluids.FluidStack... a){fluids=gregtech.api.util.extensions.ArrayExt.removeNullFluids(a);return this;}
        public GTRecipeBuilder duration(int n){return this;}public GTRecipeBuilder eut(long n){return this;}
        public GTRecipeBuilder addTo(Object map){registrations++;lastInputs=inputs;if(outputs!=null&&outputs.length>0)byOutput.put(((int)outputs[0].writeToNBT(new net.minecraft.nbt.NBTTagCompound()).getShort("Damage")),fluids);return this;}}
        '''
    fixtures['Loader.java'] = "package cpw.mods.fml.common;public class Loader{public static Loader instance(){return new Loader();}public ModContainer activeModContainer(){return null;}}"
    fixtures['GameRegistry.java'] = '''package cpw.mods.fml.common.registry;public class GameRegistry {
        public static net.minecraft.item.Item findItem(String mod,String name){return net.minecraft.init.Items.iron_ingot;}}
        '''
    fixtures['GTVersionCompat.java'] = '''package com.miaokatze.gtsr.api.compat;public class GTVersionCompat {
        public static net.minecraft.block.Block getTieredBorosilicateGlassBlock(){return net.minecraft.init.Blocks.glass;}}
        '''
    fixtures['WerkstoffLoader.java'] = '''package bartworks.system.material;public class WerkstoffLoader {
        public static final WerkstoffLoader RhodiumPlatedPalladium=new WerkstoffLoader();
        public Object getGTMaterial(){return gregtech.api.enums.Materials.Steel;}}
        '''
    return fixtures


if __name__ == '__main__':
    main()
