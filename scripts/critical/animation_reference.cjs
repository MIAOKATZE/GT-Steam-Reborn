// Execute the actual approved viewer functions, never a second handwritten sampler.
const fs = require('fs');
const path = require('path');
const { approvedViewer } = require('./viewer_reference.cjs');
const source = path.resolve(process.argv[3] || path.join(__dirname, '../../plan/临时计划/临界机器设计计划v2'));
const context = approvedViewer(source);
const cases = [];
for (const file of fs.readdirSync(path.join(source, 'models')).filter(x => x.endsWith('.geometry.json'))) {
  const model = JSON.parse(fs.readFileSync(path.join(source, 'models', file), 'utf8'));
  for (const [group, data] of Object.entries(model.animationGroups)) {
    for (const phase of [0, .035, .15, .2, .22, .5, .69, .8, .83, .95, 1]) {
      for (const seconds of [0, 13.37, 123.45]) {
        for (const mode of ['idle', 'build', 'destroy', 'working']) {
          for (const point of [[0, 0, 0], [40.5, 93.25, -7.125]]) {
            cases.push({ model: path.basename(file, '.geometry.json'), group, phase, seconds, mode, point,
              expected: context.VOXEL_TRANSFORM(point, data, phase, seconds, mode) });
          }
        }
      }
    }
  }
}
fs.writeFileSync(process.argv[2], JSON.stringify(cases));
console.log(`Extracted ${cases.length} canonical transform cases from ${source}/preview/viewer.js`);
