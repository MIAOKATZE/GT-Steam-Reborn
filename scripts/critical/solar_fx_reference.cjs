// Real R4 oracle: evaluate unmodified solar_fx.js against the approved geometry and VOXEL_TRANSFORM.
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { approvedViewer } = require('./viewer_reference.cjs');
const source = path.resolve(process.argv[3] || path.join(__dirname, '../../plan/临时计划/临界机器设计计划v2'));
const context = approvedViewer(source);
context.VOXEL_MODELS = Object.fromEntries(['base_t3', 'loom', 'solar'].map(slug => [slug,
  JSON.parse(fs.readFileSync(path.join(source, 'models', `${slug}.geometry.json`), 'utf8'))]));
vm.runInContext(fs.readFileSync(path.join(source, 'preview/solar_fx.js'), 'utf8'), context);
const cases = [];
for (const phase of [0, .10, .20, .32, .50, .58, .81, .96, 1]) {
  for (const seconds of [0, 13.37, 123.45]) {
    for (const [distance, setting] of [[0,0], [96,0], [160,0], [192,0], [0,1], [160,1], [0,2]]) {
      context.V2_NATIVE_FX_DISTANCE = distance;
      context.V2_NATIVE_PARTICLE_SETTING = setting;
      const particles = context.V2_SOLAR_MECHANISM_PARTICLES('solar', phase, seconds);
      cases.push({ phase, seconds, distance, setting, lod: context.V2_NATIVE_FX_INSPECT.factor,
        nominalCounts: context.V2_NATIVE_FX_INSPECT.nominalCounts, counts: context.V2_NATIVE_FX_INSPECT.counts,
        expected: particles.map(q => ({ family: q.type, index: Number(q.id.split(':').at(-1)),
          center: q.center, color: q.rgba.slice(0,3), alpha: q.rgba[3], glow: q.glowRGBA.slice(0,3),
          glowAlpha: q.glowRGBA[3], size: q.size })) });
    }
  }
}
fs.writeFileSync(process.argv[2], JSON.stringify(cases));
console.log(`Extracted ${cases.length} Solar FX scenarios (${cases.reduce((n,q)=>n+q.expected.length,0)} particles) from actual solar_fx.js`);
