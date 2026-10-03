'use strict';
// Repair only authored movable loot. Natural terrain and runtime geometry belong to the generator.
const fs = require('fs'), path = require('path'), vm = require('vm'), crypto = require('crypto');
const root = path.resolve(__dirname, '../..');
const plan = fs.readdirSync(path.join(root, 'plan')).map(n => path.join(root, 'plan', n))
  .find(p => fs.existsSync(path.join(p, 'preview/prefabs7')));
const file = path.join(plan, 'preview/prefabs7/forgotten_lake_court-v0.js');
const mode = process.argv[2] || 'verify';
if (!['apply', 'verify'].includes(mode)) throw Error('Use apply or verify');
const body = fs.readFileSync(file, 'utf8'), world = {window: {}};
vm.createContext(world); vm.runInContext(body, world);
const data = world.window.REMASTER_PREFAB7_DATA['forgotten_lake_court-v0'], m = data.metadata;
const key = p => p.join(','), point = n => [n.x, n.y, n.z];
const repairs = [
  ['loot7-8', [224,89,221], [227,89,220]],
  ['loot7-9', [254,89,221], [251,89,221]],
  ['loot7-10', [224,89,225], [223,89,227]],
  ['loot7-26', [66,107,221], [68,107,222]],
  ['loot7-34', [63,116,146], [63,116,143]],
  ['loot7-35', [63,116,150], [63,116,153]],
  ['loot7-41', [66,125,63], [68,125,64]],
  ['loot7-42', [96,125,63], [93,125,63]],
  ['loot7-53', [224,143,63], [226,143,64]],
  ['loot7-54', [253,145,66], [251,143,66]]
];
const cells = new Map();
for (const [x,y,z,n,i] of data.voxel.runs) for (let j=0;j<n;j++) cells.set(key([x+j,y,z]),data.voxel.palette[i]);
const get = p => cells.get(key(p));
const actor = p => (m.spawns||[]).some(n => {const c=n.clearance;return c && Math.abs(p[0]-n.x)<c.width/2+.5 && Math.abs(p[2]-n.z)<c.depth/2+.5 && p[1]<n.y+c.height && p[1]+2>n.y;});
const protectedCells = new Set((m.shapeMechanisms||[]).flatMap(s=>[s.control,...s.delta.map(n=>n.at)]).map(key));
const stand = p => !!get([p[0],p[1]-1,p[2]]) && !get(p) && !get([p[0],p[1]+1,p[2]]) && !actor(p);
function proof(n) {
  const p=point(n);
  for(const [dx,dz] of [[1,0],[-1,0],[0,1],[0,-1]]) {
    const feet=[p[0]+dx,p[1],p[2]+dz]; if(!stand(feet)) continue;
    const eye=[feet[0]+.5,feet[1]+1.62,feet[2]+.5], target=p.map(v=>v+.5);
    return {node:n.id,role:n.role,target,targetBlock:get(p),feet,eye,floor:{at:[feet[0],feet[1]-1,feet[2]],material:get([feet[0],feet[1]-1,feet[2]])},distance:Math.round(Math.hypot(...eye.map((v,i)=>v-target[i]))*1000)/1000,headClearance:true,rayUnobstructed:true};
  }
  throw Error('No cardinal source operating face: '+n.id);
}
const report=[];
for(const [id,original,to] of repairs) {
  const n=m.nodes.find(n=>n.id===id), loot=m.lootPlan7.find(n=>n.id===id);
  if(!n || !loot || key(point(n))!==key(point(loot))) throw Error('Node/loot mirror mismatch '+id);
  const from=point(n);
  if(key(from)!==key(original)&&key(from)!==key(to)) throw Error('Unexpected input '+id+': '+from);
  const identity=JSON.stringify({...n,x:undefined,y:undefined,z:undefined});
  if(mode==='apply' && key(from)!==key(to)) {
    if(get(from)!==n.block || get(to) || protectedCells.has(key(to)) || actor(to) || !get([to[0],to[1]-1,to[2]])) throw Error('Unsafe authored move '+id+' '+to);
    if(m.nodes.some(a=>a.id!==id&&key(point(a))===key(to))) throw Error('Node overlap '+id);
    cells.delete(key(from));cells.set(key(to),n.block);
    function sync(value) {
      if(Array.isArray(value)){for(const v of value)sync(v);return;}
      if(!value || typeof value!=='object')return;
      if(value.id===id && ['x','y','z'].every(k=>k in value)) [value.x,value.y,value.z]=to;
      // Coordinate-only references point at this exact movable object, never at an entire room.
      for(const [k,v] of Object.entries(value)) {
        if(['target','guideTarget'].includes(k) && Array.isArray(v) && key(v)===key(from))value[k]=[...to];
        else sync(v);
      }
    }
    sync(m);
  }
  if(mode==='verify'&&key(point(n))!==key(to))throw Error('Pending repair '+id);
  if(identity!==JSON.stringify({...n,x:undefined,y:undefined,z:undefined}))throw Error('Identity changed '+id);
  const a=proof(n), oldApproach=m.interactionApproaches.findIndex(a=>a.node===id);
  if(mode==='apply'){if(oldApproach<0)throw Error('Missing approach '+id);m.interactionApproaches[oldApproach]=a;for(const v of m.playerReviewViews||[])if(v.targetId===id){v.eye=a.eye;v.target=a.target;v.floor=a.floor;v.headClearance={feet:a.feet,head:[a.feet[0],a.feet[1]+1,a.feet[2]],clear:true};v.lineOfSight='clear';}}
  report.push({id,from:original,to:point(n),reference:n.reference,tier:n.tier,kind:n.kind,identityPreserved:true,sourceApproach:a});
}
if(key(point(m.nodes.find(n=>n.id==='entrance-story-board')))!=='136,4,125')throw Error('Legacy entrance moved');
if(mode==='apply') {
  const palette=[...data.voxel.palette], indices=new Map(palette.map((v,i)=>[v,i])), runs=[];
  const sorted=[...cells].map(([k,mat])=>[...k.split(',').map(Number),mat]).sort((a,b)=>a[1]-b[1]||a[2]-b[2]||a[0]-b[0]);
  for(const [x,y,z,mat] of sorted){let i=indices.get(mat);if(i===undefined){i=palette.length;palette.push(mat);indices.set(mat,i);}const r=runs.at(-1);if(r&&r[1]===y&&r[2]===z&&r[0]+r[3]===x&&r[4]===i)r[3]++;else runs.push([x,y,z,1,i]);}
  data.voxel={palette,runs,count:cells.size};
  m.royalLootPositionRepairs={revision:1,generator:'ForgottenLakeEncounterStructure.placeInto + RemasterWorldgen.treeOverlay',heights:[133,142,146],policy:'Move only loot into existing natural room floors; no natural geometry or entrance change',nodes:report};
  fs.writeFileSync(file,'window.REMASTER_PREFAB7_DATA=window.REMASTER_PREFAB7_DATA||{};window.REMASTER_PREFAB7_DATA["forgotten_lake_court-v0"]='+JSON.stringify(data)+';');
}
fs.mkdirSync(path.join(root,'temp'),{recursive:true});
fs.writeFileSync(path.join(root,'temp/royal-loot-position-repairs.json'),JSON.stringify({mode,sourceSha256:crypto.createHash('sha256').update(body).digest('hex'),nodes:report},null,2)+'\n');
console.log(JSON.stringify({pass:true,mode,repaired:report.length,entrance:[136,4,125],naturalGeometryWritten:0,nodes:report.map(({id,from,to})=>({id,from,to}))}));
