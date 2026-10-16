// Bind candidate Java oracle evidence to the exact approved preview and packaged model inputs.
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const assert = require('assert');
const root = path.resolve(__dirname, '../..');
const source = 'plan/临时计划/临界机器设计计划v2';
const runtime = 'src/main/resources/assets/gtsr/critical/models';
const output = process.argv[2] || 'build/critical-v2-runtime-v12125/render-oracle';
const json = file => JSON.parse(fs.readFileSync(path.join(root, file), 'utf8'));
const sha = file => crypto.createHash('sha256').update(fs.readFileSync(path.join(root, file))).digest('hex');
const files = [`${source}/preview/viewer.js`, `${source}/preview/solar_fx.js`];
const models = {};
for (const slug of ['base_t3', 'loom', 'solar', 'sun', 'plugin_efficiency', 'plugin_parallel', 'plugin_speed']) {
  const sourceFile = `${source}/models/${slug}.geometry.json`, runtimeFile = `${runtime}/v2_${slug}.geometry.json`;
  const approved = json(sourceFile), packaged = json(runtimeFile);
  assert.deepStrictEqual(packaged.animationGroups, approved.animationGroups, `${slug} packaged animation drift`);
  files.push(sourceFile, runtimeFile);
  models[slug] = { blocks: packaged.blocks.length, animationGroups: Object.keys(packaged.animationGroups).length,
    gravityClusters: new Set((packaged.meta.gravitySources || []).map(s => s.clusterId || s.id)).size };
}
assert.equal(models.solar.blocks, 43118);
assert.equal(models.solar.animationGroups, 26);
const gravityClusters = ['base_t3', 'loom', 'solar'].reduce((n, slug) => n + models[slug].gravityClusters, 0);
assert.equal(gravityClusters, 71);
for (const group of ['receiver', 'thermal_relief_stem', 'solar_top_crown']) {
  const g = json(`${runtime}/v2_solar.geometry.json`).animationGroups[group];
  assert.equal(g.ambientRotation.periodSeconds, 32);
  assert.deepStrictEqual(g.workSpin, { turns: 2, ramp: .2, direction: 1 });
}
for (const file of ['CriticalAnimation', 'CriticalSolarFX', 'CriticalFX', 'CriticalSunFX',
  'CriticalControllerRenderer', 'CriticalMeshCache', 'CriticalLightCache', 'CriticalFXBudget', 'CriticalGroupVisibility',
  'CriticalMaterialTextures', 'CriticalMaterialRenderer'])
  files.push(`src/main/java/com/miaokatze/gtsr/client/critical/${file}.java`);
for (const file of ['viewer_reference.cjs', 'animation_reference.cjs', 'solar_fx_reference.cjs',
  'CriticalAnimationProbe.java', 'CriticalSolarFXProbe.java', 'render_oracle_inputs.cjs'])
  files.push(`scripts/critical/${file}`);
const animation = json(`${output}/animation-report.json`), fx = json(`${output}/solar-fx-report.json`);
assert.equal(animation.passed, true);
assert.equal(fx.passed, true);
assert.equal(fx.cachedModels, 3);
assert.equal(fx.reloadClearsCaches, true);
assert.deepStrictEqual(fx.counts, { idle_lod1: 243, idle_lod2: 122, idle_lod4: 61, idle_lod0: 0,
  work_lod1: 735, work_lod2: 368, work_lod4: 184, work_lod0: 0 });
files.push(`${output}/animation-cases.json`, `${output}/solar-fx-cases.json`,
  `${output}/animation-report.json`, `${output}/solar-fx-report.json`);
const report = { passed: true, verifiedAt: new Date().toISOString(), models, gravityClusters,
  animation, solarFX: fx, sha256: Object.fromEntries(files.map(file => [file, sha(file)])),
  sourceReview: { nativeMesh: 'cached by model slug; only rigid dynamic groups; reload clears lists',
    nativeLight: 'per-anchor FIFO cache, at most 16 sample positions per tick; refresh every 4 ticks',
    globalFX: '1536 claims per render frame; Solar 800 local cap; no world queries in Solar sampler',
    sun: 'packaged animation groups match approved viewer; existing CriticalSunFX branch retained',
    pause: 'machine pending and not working freezes pose/FX clock; shared TextureMap retains existing SUN client-tick semantics',
    limits: 'source review and standalone Java sampling do not replace native GL/performance measurements' } };
fs.writeFileSync(path.join(root, output, 'render-freeze.json'), JSON.stringify(report, null, 2) + '\n');
console.log(`PASS runtime render inputs bound: ${files.length} hashes; ${gravityClusters} gravity clusters; Solar ${models.solar.blocks}/${models.solar.animationGroups}`);
