const fs = require('fs');
const path = require('path');
const vm = require('vm');

function approvedViewer(source) {
  const viewer = fs.readFileSync(path.join(source, 'preview/viewer.js'), 'utf8');
  const lines = viewer.split(/\r?\n/);
  const sample = lines.find(line => line.startsWith('function sample('));
  const start = viewer.indexOf('function rotate(v,a,t)');
  const end = viewer.indexOf(';window.VOXEL_SET_LOOM_MODE', start);
  if (!sample || start < 0 || end < 0) throw Error('Approved viewer sampler anchors changed');
  const context = { Math };
  context.window = context;
  vm.createContext(context);
  const work = ['function workSample(', 'function workSpinSample(']
    .map(prefix => lines.find(line => line.startsWith(prefix)) || '').join('\n');
  vm.runInContext(`const norm=a=>{let n=Math.hypot(...a)||1;return a.map(x=>x/n)};
    const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
    const dot=(a,b)=>a.reduce((s,v,i)=>s+v*b[i],0);
    ${sample}\n${work}\n${viewer.slice(start, end)}`, context);
  return context;
}

module.exports = { approvedViewer };
