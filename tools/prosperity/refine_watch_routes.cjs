'use strict';
// Local, reviewable repair of the two old watch houses. No file writes on import.
const fs=require('fs'),path=require('path'),vm=require('vm');
const K=p=>p.join(','),copy=o=>JSON.parse(JSON.stringify(o));
const IDS=['outpost_outpost_watch_post','ruin_ruin_watch_buried'];
const FLOOR=/^gtsr:ruins_(mossroot_paving|sootstone_tiles|slag_masonry)(?:_stairs)?#/;
const MOSS=/^gtsr:ruins_mossroot_paving(?:_stairs)?#/;
function sample(points){const out=[];for(let j=1;j<points.length;j++){const a=points[j-1],b=points[j],n=Math.max(...a.map((v,i)=>Math.abs(v-b[i])),1);for(let i=j===1?0:1;i<=n;i++)out.push(a.map((v,k)=>Math.round(v+(b[k]-v)*i/n)));}return out;}
function applyWatchRoutes(prefabName,input,options={}){
 const dryRun=options.dryRun!==false,d=copy(input),m=d.metadata;
 if(!IDS.includes(m.id)||m.variant!==2)return {changes:[],routes:[],blocked:[],data:dryRun?d:input,dryRun};
 if(m.structureDesignReview?.watchRouteRepair)return {changes:[],routes:(m.structureDesignReview.stairExceptionRoutes||[]).filter(r=>r.originalRouteKey==='routes:0'),blocked:[],data:dryRun?d:input,dryRun,alreadyApplied:true,prefabName};
 const offset=m.id==='ruin_ruin_watch_buried'?-8:0,P=(x,y,z)=>[x,y+offset,z],index=new Map(),edits=new Map(),changes=[],blocked=[];
 for(const r of d.voxel.runs){const k=r[1]+','+r[2];if(!index.has(k))index.set(k,[]);index.get(k).push(r);}
 const original=p=>{for(const r of index.get(p[1]+','+p[2])||[])if(p[0]>=r[0]&&p[0]<r[0]+r[3])return d.voxel.palette[r[4]];return null;};
 const get=p=>edits.has(K(p))?edits.get(K(p)):original(p),protectedCells=new Set(),proofFloors=new Set();
 const protect=p=>{if(Array.isArray(p))protectedCells.add(K(p.slice(0,3)));};
 for(const n of [...(m.nodes||[]),...(m.navigationHints||[])])protect([n.x,n.y,n.z]);
 for(const a of m.interactionApproaches||[]){if(a.floor?.at)proofFloors.add(K(a.floor.at));protect(a.feet);if(a.feet)protect([a.feet[0],a.feet[1]+1,a.feet[2]]);}
 for(const v of m.playerReviewViews||[]){if(v.floor?.at)proofFloors.add(K(v.floor.at));protect(v.headClearance?.feet);protect(v.headClearance?.head);}
 for(const s of m.shapeMechanisms||[])for(const p of [s.control,...(s.delta||[]).map(x=>x.at)])protect(p);
 for(const c of m.pollutionClusters||[])for(const p of c.positions||[])protect(p);
 for(const a of m.damageManifest||[])for(const p of a.removedVoxels||[])protect(p);
 for(const n of m.nodes||[])if(Array.isArray(n.sourceFixture))for(const p of n.sourceFixture)protect(Array.isArray(p)?p:p?.at);
 const roomShell=p=>(m.rooms||[]).some(r=>p[0]>=r.x&&p[0]<r.x+r.w&&p[2]>=r.z&&p[2]<r.z+r.d&&p[1]>=r.y&&p[1]<=r.y+r.h+8&&(p[0]===r.x||p[0]===r.x+r.w-1||p[2]===r.z||p[2]===r.z+r.d-1||p[1]>=r.y+r.h));
 const protection=p=>protectedCells.has(K(p))?'node/proof/mechanism/damage':proofFloors.has(K(p))?'proof-floor':/ProsperityZenithLog|ProsperityJadeLeaves/.test(get(p)||'')?'natural':(m.spawns||[]).some(n=>{const c=n.clearance||{width:3,depth:3,height:3};return Math.abs(p[0]-n.x)<c.width/2+.5&&Math.abs(p[2]-n.z)<c.depth/2+.5&&p[1]>=n.y-1&&p[1]<n.y+c.height;})?'actor':(m.bossProtectedVolumes||[]).some(v=>p.every((n,i)=>n>=v.min[i]&&n<=v.max[i]))?'boss':roomShell(p)?'room-shell':null;
 function put(p,after,reason){const before=get(p);if(before===after)return true;const guard=protection(p);if(guard||before&&!MOSS.test(before)){blocked.push({at:p,before,after,reason:guard||'preserved structural/decorative fixture'});return false;}edits.set(K(p),after);changes.push({at:p,before,after,reason});return true;}
 const clear=p=>[1,2].every(h=>!get([p[0],p[1]+h,p[2]]));
 function eligible(p){if(K(p)===K(P(52,1,63))||K(p)===K(P(11,1,37)))return false;const floor=get(p);if(floor&&!FLOOR.test(floor))return false;if(!floor&&protection(p))return false;return [1,2].every(h=>{const q=[p[0],p[1]+h,p[2]],v=get(q);return !v||MOSS.test(v)&&!protection(q);});}
 function groundPath(a,b){
  const queue=[a],prev=new Map([[K(a),null]]);let end=null;
  for(let i=0;i<queue.length;i++){const p=queue[i],k=K(p);if(k===K(b)){end=k;break;}for(const [dx,dz]of [[1,0],[0,1],[-1,0],[0,-1]]){const q=[p[0]+dx,a[1],p[2]+dz],qk=K(q);if(q[0]<5||q[0]>67||q[2]<-2||q[2]>65||prev.has(qk)||!eligible(q))continue;prev.set(qk,k);queue.push(q);}}
  if(!end){blocked.push({reason:'no bounded same-height path around preserved watch-house fixtures',from:a,to:b});return [];}
  const out=[];for(let k=end;k!==null;k=prev.get(k))out.push(k.split(',').map(Number));return out.reverse();
 }
 const old=copy(m.routes[0]),first=sample(old.slice(0,2)),approach=sample([P(12,1,37),P(12,1,36),P(11,1,36),P(11,2,37),P(11,2,38)]),prefix=[...first.slice(0,-2),...approach,...approach.slice(0,-1).reverse()],back=P(52,1,64),entry=groundPath(approach[0],back),exit=groundPath(back,old.at(-1));
 // The back door is an existing opening, not a newly cut wall. Its threshold is full.
 const inside=sample([back,P(52,2,63),P(52,2,62),P(52,2,57),P(53,2,57),P(53,2,55)]);
 const flight=[P(53,2,55),P(52,3,55),P(51,4,55),P(50,5,55),P(49,5,55),P(48,5,55),P(47,5,55),P(46,5,55)];
 const excursion=[...inside,...flight.slice(1),...flight.slice(0,-1).reverse(),...inside.slice(0,-1).reverse()];
 const actual=[...prefix,...entry.slice(1),...excursion.slice(1),...exit.slice(1)],wanted=new Map();
 for(const p of actual){if(get(p)&&FLOOR.test(get(p)))continue;if(!get(p))wanted.set(K(p),{at:p,after:'gtsr:ruins_mossroot_paving#0'});}
 for(const p of [P(52,3,55),P(51,4,55),P(50,5,55)])wanted.set(K(p),{at:p,after:'gtsr:ruins_mossroot_paving_stairs#1'});
 wanted.set(K(P(52,2,63)),{at:P(52,2,63),after:'gtsr:ruins_sootstone_tiles_stairs#3'});
 wanted.set(K(P(11,2,37)),{at:P(11,2,37),after:'gtsr:ruins_mossroot_paving_stairs#2'});
 // A small solid stair stringer over the existing floor, never an area fill.
 for(const [x,top]of [[51,3],[50,4]])for(let y=3;y<=top;y++)wanted.set(K(P(x,y,55)),{at:P(x,y,55),after:'gtsr:ruins_mossroot_paving#0'});
 for(const p of actual)for(const h of [1,2]){const q=[p[0],p[1]+h,p[2]];if(wanted.has(K(q))){blocked.push({at:q,reason:'stair stringer overlaps route headroom'});continue;}if(get(q))put(q,null,'only superseded original moss walkway at selected replacement-route headroom');}
 for(const w of wanted.values())put(w.at,w.after,'finite original-material door landing, stair or narrow stair support');
 const trace=actual.map(p=>({at:p,material:get(p),clear:clear(p)}));
 for(const t of trace)if(!FLOOR.test(t.material)||!t.clear)blocked.push({...t,reason:'actual floor or two-block headroom assertion'});
 const room=m.rooms.find(r=>r.x===41&&r.z===43),door=P(52,2,62),platform=P(46,5,55);
 if(!room||!FLOOR.test(original(door))||!FLOOR.test(original(platform))||!clear(platform))blocked.push({reason:'original back-door threshold or original high platform evidence changed'});
 const receipt={originalRouteKey:'routes:0',originalPoints:old,actualPoints:actual,width:1,reason:'Existing back door and interior three-step west ascent reach original high platform; exterior entrance and return remain connected',functionalEndpoints:{original:[old[0],old.at(-1)],actual:[actual[0],actual.at(-1)],preserved:true,oldHighKnot:old[2],originalPlatform:platform,platformHeightPreserved:true,originalBackDoor:door},trace,assertions:{twoBlockHeadroom:trace.every(t=>t.clear),actualFloor:trace.every(t=>FLOOR.test(t.material)),protectedEdits:0,realMinecraftCollisionVerified:false}};
 if(blocked.length)return {changes,routes:[receipt],blocked,data:dryRun?copy(input):input,dryRun,prefabName};
 const palette=[...d.voxel.palette],pi=new Map(palette.map((v,i)=>[v,i])),runs=[];
 const add=(x,y,z,n,mat)=>{if(!mat)return;if(!pi.has(mat)){pi.set(mat,palette.length);palette.push(mat);}const r=runs.at(-1),id=pi.get(mat);if(r&&r[1]===y&&r[2]===z&&r[0]+r[3]===x&&r[4]===id)r[3]+=n;else runs.push([x,y,z,n,id]);};
 for(const [x,y,z,n,id]of d.voxel.runs){let start=x;for(let i=x;i<x+n;i++){const k=K([i,y,z]);if(!edits.has(k))continue;if(i>start)add(start,y,z,i-start,d.voxel.palette[id]);add(i,y,z,1,edits.get(k));start=i+1;}if(start<x+n)add(start,y,z,x+n-start,d.voxel.palette[id]);}
 for(const [k,mat]of edits){const p=k.split(',').map(Number);if(mat&&!original(p))add(...p,1,mat);}runs.sort((a,b)=>a[1]-b[1]||a[2]-b[2]||a[0]-b[0]);d.voxel={...d.voxel,palette,runs,count:runs.reduce((n,r)=>n+r[3],0)};
 m.routes[0]=copy(actual);m.stairRoutes=m.stairRoutes||[];m.stairRoutes.push({points:copy(flight.slice(0,5)),width:1,handrail:false,task:'old-watch-interior-high-platform',enclosed:false});
 m.structureDesignReview=m.structureDesignReview||{};m.structureDesignReview.stairExceptionRoutes=[...(m.structureDesignReview.stairExceptionRoutes||[]).filter(r=>r.originalRouteKey!=='routes:0'),receipt];m.structureDesignReview.watchRouteRepair={schema:1,changes:changes.length,realMinecraftCollisionVerified:false};
 if(!dryRun){input.voxel=d.voxel;input.metadata=d.metadata;}
 return {changes,routes:[receipt],blocked,data:dryRun?d:input,dryRun,prefabName};
}
module.exports={applyWatchRoutes,sample};
if(require.main===module){
 const root=path.resolve(__dirname,'../..'),pdir=fs.readdirSync(path.join(root,'plan')).find(f=>fs.existsSync(path.join(root,'plan',f,'preview/prefabs7'))),reports=[];
 for(const id of IDS){const file=id+'-v2.js',w={window:{}};vm.createContext(w);vm.runInContext(fs.readFileSync(path.join(root,'plan',pdir,'preview/prefabs7',file),'utf8'),w);const d=Object.values(w.window.REMASTER_PREFAB7_DATA)[0],before=JSON.stringify(d),r=applyWatchRoutes(file,d,{dryRun:true});if(before!==JSON.stringify(d))throw Error('dry run mutated input');reports.push({...r,data:undefined,inputUnchanged:true});}
 if(!reports.every(r=>r.alreadyApplied)){fs.writeFileSync(path.join(root,'temp/watch-route-repairs.json'),JSON.stringify({realMinecraftCollisionVerified:false,reports},null,2));fs.writeFileSync(path.join(root,'temp/watch-route-repairs.md'),'# Watch route dry run\n\nNo real Minecraft collision test is claimed. Preserve original house, back door and high platform.\n\n'+reports.map(r=>r.prefabName+': changes='+r.changes.length+', blocked='+r.blocked.length).join('\n'));}
 console.log(JSON.stringify(reports.map(r=>({file:r.prefabName,changes:r.changes.length,blocked:r.blocked.length,failures:r.blocked.slice(0,12)}))));
}
