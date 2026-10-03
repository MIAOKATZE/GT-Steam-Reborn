'use strict';
// One read-only workshop junction plaque; the natural tree remains its floor owner.
const fs = require('fs'), path = require('path'), vm = require('vm');
const root = path.resolve(__dirname, '../..');
const plan = fs.readdirSync(path.join(root, 'plan')).map(n => path.join(root, 'plan', n))
  .find(p => fs.existsSync(path.join(p, 'preview/prefabs7')));
const file = path.join(plan, 'preview/prefabs7/forgotten_lake_court-v0.js');
const ctx = { window: {} }; vm.createContext(ctx); vm.runInContext(fs.readFileSync(file, 'utf8'), ctx);
const doc = ctx.window.REMASTER_PREFAB7_DATA['forgotten_lake_court-v0'], m = doc.metadata;
const cells = new Map();
for (const [x,y,z,n,i] of doc.voxel.runs) for(let j=0;j<n;j++) cells.set([x+j,y,z].join(','),doc.voxel.palette[i]);
const at=[272,80,137], feet=[271,80,137], floor=[271,79,137], id='nav-court-workshop-junction';
const hint={id,x:at[0],y:at[1],z:at[2],role:'memory',block:'gtsr:draft_notice_board#0',
  label:'工坊枝路指引',text:'沿木桥抵达本层工坊。面向工坊内侧的工序控制台，先阅读原始记录再操作；宝箱仍须满足各自的封印条件。退路沿来时木桥返回螺旋步道。',
  navigationHint:true,readOnly:true,guideTarget:[275,80,138],kind:'workshop-junction'};
for(const p of [feet,[feet[0],feet[1]+1,feet[2]],at,[at[0],at[1]+1,at[2]]]) {
  if(cells.has(p.join(','))&&!(p.join(',')===at.join(',')&&cells.get(p.join(','))===hint.block)) throw Error('Occupied '+p);
}
for(const p of [floor,[at[0],at[1]-1,at[2]]]) if(!/ProsperityZenithLog/.test(cells.get(p.join(','))||'')) throw Error('No original natural floor '+p);
for(const n of [...m.nodes,...(m.lootPlan7||[]),...(m.navigationHints||[])])
  if(n.id!==id&&[n.x,n.y,n.z].join(',')===at.join(','))throw Error('Node conflict');
for(const shape of m.shapeMechanisms||[]) for(const d of shape.delta||[]) if(d.at.join(',')===at.join(','))throw Error('Shape conflict');
const existing=(m.navigationHints||[]).find(n=>n.id===id);
if(!existing){
  (m.navigationHints||(m.navigationHints=[])).push(hint);
  m.interactionApproaches.push({node:id,feet,eye:[271.5,81.62,137.5],floor:{at:floor,material:cells.get(floor.join(','))},distance:Math.hypot(1,1.12),rayUnobstructed:true});
  for(const field of ['navigationHintCoverage','interactionApproachCoverage']) {
    const coverage=m[field]||(m[field]={required:0,verified:0,missing:0}); coverage.required++;coverage.verified++;
  }
  cells.set(at.join(','),hint.block);
  const points=[...cells].map(([k,material])=>[...k.split(',').map(Number),material]);
  points.sort((a,b)=>a[1]-b[1]||a[2]-b[2]||a[0]-b[0]);
  const palette=[],index=new Map(),runs=[];
  for(const [x,y,z,material] of points){if(!index.has(material)){index.set(material,palette.length);palette.push(material);}const i=index.get(material),r=runs.at(-1);if(r&&r[1]===y&&r[2]===z&&r[0]+r[3]===x&&r[4]===i)r[3]++;else runs.push([x,y,z,1,i]);}
  doc.voxel={palette,runs,count:points.length};
  if(process.argv[2]==='apply')fs.writeFileSync(file,'window.REMASTER_PREFAB7_DATA=window.REMASTER_PREFAB7_DATA||{};window.REMASTER_PREFAB7_DATA["forgotten_lake_court-v0"]='+JSON.stringify(doc)+';');
}
console.log(JSON.stringify({pass:true,id,at,feet,changed:!existing,applied:process.argv[2]==='apply',navigationHints:m.navigationHints.length}));
