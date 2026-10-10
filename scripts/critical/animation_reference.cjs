// Extract the actual approved viewer functions, not a second handwritten reference implementation.
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const root = path.resolve(__dirname, '../..');
const source = path.join(root, 'plan/临时计划/临界机器设计计划');
const viewer = fs.readFileSync(path.join(source, 'preview/viewer.js'), 'utf8');
const sample = viewer.split('\n').find(line => line.startsWith('function sample('));
const start = viewer.indexOf('function rotate(v,a,t)');
const end = viewer.indexOf(';window.VOXEL_SET_LOOM_MODE', start);
const functions = viewer.slice(start, end);
const context = {window: {}, Math};
vm.createContext(context);
vm.runInContext(`const norm=a=>{let n=Math.hypot(...a)||1;return a.map(x=>x/n)};const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];const dot=(a,b)=>a.reduce((s,v,i)=>s+v*b[i],0);${sample}\n${functions}`, context);
const cases = [];
for (const file of fs.readdirSync(path.join(source, 'models')).filter(x=>x.endsWith('.geometry.json'))) {
  const model = JSON.parse(fs.readFileSync(path.join(source, 'models', file), 'utf8'));
  for (const [group, data] of Object.entries(model.animationGroups)) {
    for (const phase of [0, .035, .15, .22, .5, .69, .83, .95, 1]) {
      for (const seconds of [0, 13.37, 123.45]) {
        for (const mode of ['idle', 'build', 'destroy']) {
          for (const point of [[0, 0, 0], [40.5, 93.25, -7.125]]) {
            cases.push({model: model.name, group, phase, seconds, mode, point,
              expected: context.window.VOXEL_TRANSFORM(point, data, phase, seconds, mode)});
          }
        }
      }
    }
  }
}
fs.writeFileSync(process.argv[2], JSON.stringify(cases));
console.log(`Extracted ${cases.length} canonical transform cases from approved viewer.js`);
