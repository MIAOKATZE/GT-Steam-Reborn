'use strict';
// Bounded repairs for the independently classified primary stair exceptions.
// No files are mutated by importing this module. dryRun also leaves d untouched.
const fs=require('fs'),path=require('path');
const ROOT=path.resolve(__dirname,'../..'),K=p=>p.join(','),copy=o=>JSON.parse(JSON.stringify(o));
const FULL=/^gtsr:ruins_(slag_masonry|sootstone_tiles|mossroot_paving|verdigris_pressed_brick|rootbound_brick|cast_iron_pillar|oath_inscription|rust_riveted_plate|furnace_firebrick)(?:_stairs)?#/;
const walkFloor=mat=>FULL.test(mat)||/^gtsr:ruins_rust_floor_grate#/.test(mat||'');
function full(mat){return mat?.replace('_stairs#','#')||null;}
function stair(mat,meta){const names={rust_riveted_plate:'riveted_plate',furnace_firebrick:'firebrick'};const m=/^gtsr:ruins_([^#]+)#/.exec(full(mat)||'');if(!m||!FULL.test(mat))throw Error('Unregistered stair floor '+mat);return 'gtsr:ruins_'+(names[m[1]]||m[1])+'_stairs#'+meta;}
function samples(points){const out=[];for(let j=1;j<points.length;j++){const a=points[j-1],b=points[j],n=Math.max(...a.map((v,i)=>Math.abs(v-b[i])),1);if(a[0]!==b[0]&&a[2]!==b[2])throw Error('Orthogonal route required');if(Math.abs(a[1]-b[1])>Math.max(Math.abs(a[0]-b[0]),Math.abs(a[2]-b[2])))throw Error('Vertical jump');for(let i=j===1?0:1;i<=n;i++)out.push(a.map((v,k)=>Math.round(v+(b[k]-v)*i/n)));}return out;}
function applyExceptions(prefabName,input,options={}){
 const dry=options.dryRun!==false,d=copy(input),m=d.metadata;
 if(!['fallen_foundry','subsided_factory','trench_infirmary','valve_yard','city_archive_cache','city_dome_hall','mirror_barracks','city_watch_tower'].includes(m.id))return {changes:[],routes:[],notes:[],blocked:[]};
 const prior=m.structureDesignReview?.stairExceptionRoutes;
 const alreadyApplied=!!(prior?.length&&prior.every(r=>{const [t,n]=r.originalRouteKey.split(':');return JSON.stringify(t==='stairRoutes'?m.stairRoutes[+n]?.points:m.routes[+n])===JSON.stringify(r.actualPoints);}));
 const rows=JSON.parse(fs.readFileSync(path.join(ROOT,'temp/stair-inactive-classification.json'),'utf8')).records.filter(r=>r.id===m.id&&r.variant===m.variant),index=new Map(),edits=new Map(),changes=[],routes=[],notes=[],blocked=[];
 for(const r of d.voxel.runs){const k=r[1]+','+r[2];if(!index.has(k))index.set(k,[]);index.get(k).push(r);}
 const original=p=>{for(const r of index.get(p[1]+','+p[2])||[])if(p[0]>=r[0]&&p[0]<r[0]+r[3])return d.voxel.palette[r[4]];return null;},get=p=>edits.has(K(p))?edits.get(K(p)):original(p);
 const explicit=new Set(),floors=new Set(),boundedDoorClearance=new Set();
 for(const n of [...(m.nodes||[]),...(m.navigationHints||[])])explicit.add(K([n.x,n.y,n.z]));
 for(const a of m.interactionApproaches||[]){if(a.floor?.at)floors.add(K(a.floor.at));for(const p of [a.feet,a.feet&&[a.feet[0],a.feet[1]+1,a.feet[2]]])if(p)explicit.add(K(p));}
 for(const v of m.playerReviewViews||[]){if(v.floor?.at)floors.add(K(v.floor.at));for(const p of [v.headClearance?.feet,v.headClearance?.head])if(p)explicit.add(K(p));}
 for(const s of m.shapeMechanisms||[])for(const p of [s.control,...(s.delta||[]).map(x=>x.at)])if(p)explicit.add(K(p));
 for(const n of m.nodes||[])for(const p of n.sourceFixture||[])if(Array.isArray(p)&&p.length>=3)explicit.add(K(p.slice(0,3)));else if(Array.isArray(p?.at))explicit.add(K(p.at));
 for(const c of m.pollutionClusters||[])for(const p of c.positions||[])explicit.add(K(p.slice(0,3)));
 for(const a of m.damageManifest||[])for(const p of a.removedVoxels||[])explicit.add(K(p.slice(0,3)));
 // The authored broad buffer is retained verbatim in metadata. Its outside
 // corridors are repairable; the actual Boss room hull and actor remain fixed.
 const arenaRooms=(m.rooms||[]).filter(r=>(m.spawns||[]).some(n=>n.role==='boss'&&n.x>=r.x&&n.x<r.x+r.w&&n.z>=r.z&&n.z<r.z+r.d&&n.y>=r.y&&n.y<r.y+r.h));
 const boss=p=>arenaRooms.some(r=>p[0]>=r.x&&p[0]<r.x+r.w&&p[2]>=r.z&&p[2]<r.z+r.d&&p[1]>=r.y&&p[1]<=r.y+r.h+2);
 const roomShell=p=>(m.rooms||[]).some(r=>p[0]>=r.x&&p[0]<r.x+r.w&&p[2]>=r.z&&p[2]<r.z+r.d&&p[1]>r.y+1&&p[1]<=r.y+r.h+2&&(p[0]===r.x||p[0]===r.x+r.w-1||p[2]===r.z||p[2]===r.z+r.d-1||p[1]>=r.y+r.h-1));
 const protection=p=>explicit.has(K(p))?'node/view/approach/mechanism/damage':floors.has(K(p))?'proof-floor':/ProsperityZenithLog|ProsperityJadeLeaves/.test(get(p)||'')?'natural':(m.spawns||[]).some(n=>{const c=n.clearance||{width:3,depth:3,height:3};return Math.abs(p[0]-n.x)<c.width/2+.5&&Math.abs(p[2]-n.z)<c.depth/2+.5&&p[1]>=n.y-1&&p[1]<n.y+c.height;})?'actor':roomShell(p)?'room-shell':boss(p)?'boss-volume':null;
 function put(p,mat,reason,route){const before=get(p);if(before===mat)return;let protect=protection(p);if(protect==='room-shell'&&mat===null&&boundedDoorClearance.has(K(p))&&FULL.test(before))protect=null;if(protect==='proof-floor'&&mat&&full(before)===full(mat))protect=null;if(protect==='room-shell'&&mat&&/stairs/.test(mat)&&full(before)?.split('#')[0]===full(mat)?.split('#')[0]&&[1,2].every(h=>!get([p[0],p[1]+h,p[2]])))protect=null;if(protect==='boss-volume'&&before&&mat&&FULL.test(before)&&full(before)===full(mat)&&/stairs/.test(mat))protect=null;if(protect){blocked.push({at:p,before,after:mat,reason:protect,route});return;}if(before&&!FULL.test(before)&&mat!==before){blocked.push({at:p,before,after:mat,reason:'non-walk-structural fixture',route});return;}edits.set(K(p),mat);changes.push({at:p,before,after:mat,reason,route});}
 function author(key,oldPoints,knots,width,material,reason){
  const pts=samples(knots),wanted=new Map(),heads=new Map(),centerHeads=new Set(pts.flatMap(p=>[1,2].map(h=>K([p[0],p[1]+h,p[2]])))),centerFloors=new Set(pts.map(K)),r=Math.floor(width/2),routeRecord={originalRouteKey:key,originalPoints:copy(oldPoints),actualPoints:pts,width,traversalWidth:1,reason,trace:[],assertions:[],preservedSideFixtures:[]};
  for(let i=0;i<pts.length;i++){
   const p=pts[i],a=i?pts[i-1]:p,b=pts[i+1]||p,q=a[1]<p[1]?a:b[1]<p[1]?b:a[1]!==p[1]?a:b,delta=q[1]-p[1],direction=delta!==0?(q[0]!==p[0]?'x':'z'):(b[0]!==p[0]||a[0]!==p[0])?'x':'z';let meta=null;
   // The high floor is a stair; the low adjacent floor may remain full.
   if(delta<0){const dx=p[0]-q[0],dz=p[2]-q[2];meta=dx?dx>0?0:1:dz>0?2:3;}
   for(let side=-r;side<=r;side++){
    const at=[p[0]+(direction==='z'?side:0),p[1],p[2]+(direction==='x'?side:0)],floorMaterial=FULL.test(get(at))?full(get(at)):material,after=meta===null?floorMaterial:stair(floorMaterial,meta),k=K(at);
    if(wanted.has(k)&&wanted.get(k).after!==after&&meta===null)continue;
    wanted.set(k,{at,after});for(const h of [1,2]){const head=[at[0],at[1]+h,at[2]];heads.set(K(head),head);}
   }
  }
  for(const [k,p]of heads){if(wanted.has(k)){blocked.push({at:p,reason:'new flight headroom overlaps another floor',route:key});continue;}if(get(p)){
   // Rails, brackets and bound furniture at the outside edge remain in place.
   // The physical center is independently checked with default entity width.
   if(!centerHeads.has(k)&&(!FULL.test(get(p))||protection(p))){routeRecord.preservedSideFixtures.push({at:p,material:get(p),protection:protection(p)});continue;}
   put(p,null,'bounded route two-block headroom: '+reason,key);
  }}
  for(const w of wanted.values()){if(!centerFloors.has(K(w.at))&&get(w.at)&&(!FULL.test(get(w.at))||protection(w.at)))continue;put(w.at,w.after,'finite same-material stair/landing: '+reason,key);}
  for(let i=0;i<pts.length;i++){const p=pts[i],mat=get(p),clear=[1,2].every(h=>!get([p[0],p[1]+h,p[2]]));const ok=FULL.test(mat)&&clear;if(!ok)routeRecord.assertions.push({at:p,material:mat,clear});routeRecord.trace.push({at:p,material:mat,clear});}
  if(routeRecord.assertions.length)blocked.push(...routeRecord.assertions.map(x=>({...x,route:key,reason:'center route floor/clearance assertion'})));
  const [type,num]=key.split(':');if(type==='stairRoutes'){
   const previous=m.stairRoutes[+num].points;m.stairRoutes[+num].points=pts;m.stairRoutes[+num].handrail=false;
   for(let j=0;j<m.routes.length;j++)if(JSON.stringify(m.routes[j])===JSON.stringify(previous))m.routes[j]=copy(pts);
   // Existing routes can append the room connector after the old flight.
   for(let j=0;j<m.routes.length;j++)if(previous.length===2&&JSON.stringify(m.routes[j].slice(0,2))===JSON.stringify(previous))m.routes[j]=[...copy(pts),...m.routes[j].slice(2)];
  }else if(type==='routes')m.routes[+num]=pts;
  routes.push(routeRecord);
 }
 function flatConnector(a,b,route){
  const y=a[1],minX=Math.min(a[0],b[0])-3,maxX=Math.max(a[0],b[0])+3,minZ=Math.min(a[2],b[2])-3,maxZ=Math.max(a[2],b[2])+3;
  const room=(m.rooms||[]).find(r=>b[0]>r.x&&b[0]<r.x+r.w-1&&b[2]>r.z&&b[2]<r.z+r.d-1&&y===r.y+1),keys=new Map(),queue=[],goals=[];
  const supported=p=>{const mat=get(p);if(mat&&!FULL.test(mat)&&!/ruins_rust_floor_grate#/.test(mat))return false;if(!mat&&protection(p))return false;return [1,2].every(h=>{const q=[p[0],y+h,p[2]],v=get(q);return !v||FULL.test(v)&&!protection(q);});};
  if(supported(b))goals.push(K(b));else for(let r=1;r<=2;r++)for(let dx=-r;dx<=r;dx++)for(let dz=-r;dz<=r;dz++){if(Math.abs(dx)+Math.abs(dz)!==r)continue;const p=[b[0]+dx,y,b[2]+dz];if(supported(p)&&(!room||p[0]>room.x&&p[0]<room.x+room.w-1&&p[2]>room.z&&p[2]<room.z+room.d-1))goals.push(K(p));}
  for(let r=0;r<=2;r++){let found=false;for(let dx=-r;dx<=r&&!found;dx++)for(let dz=-r;dz<=r;dz++){if(Math.abs(dx)+Math.abs(dz)!==r)continue;const p=[a[0]+dx,y,a[2]+dz];if(supported(p)){queue.push(p);keys.set(K(p),null);found=true;break;}}if(found)break;}
  let end=null;for(let i=0;i<queue.length&&i<20000;i++){const p=queue[i],k=K(p);if(goals.includes(k)){end=k;break;}for(const [dx,dz]of [[1,0],[-1,0],[0,1],[0,-1]]){const q=[p[0]+dx,y,p[2]+dz],qk=K(q);if(q[0]<minX||q[0]>maxX||q[2]<minZ||q[2]>maxZ||keys.has(qk)||!supported(q))continue;keys.set(qk,k);queue.push(q);}}
  if(!end){blocked.push({route,reason:'no bounded flat connector around preserved fixtures',from:a,to:b});return samples([a,b]);}
  const result=[];for(let k=end;k!==null;k=keys.get(k))result.push(k.split(',').map(Number));result.reverse();
  for(const p of result){if(!get(p))put(p,'gtsr:ruins_sootstone_tiles#0','restore only selected authored corridor floor cell on bounded fixture detour',route);for(const h of [1,2]){const q=[p[0],y+h,p[2]];if(get(q))put(q,null,'only selected original corridor center clears compressed structural roof; semantic fixtures retained',route);}}
  return result;
 }
 function legacyFloors(points,route){
  const out=[];for(let j=1;j<points.length;j++){const a=points[j-1],b=points[j],n=Math.max(Math.abs(b[0]-a[0]),Math.abs(b[2]-a[2]),1);for(let i=j===1?0:1;i<=n;i++){
   const p=a.map((v,k)=>Math.round(v+(b[k]-v)*i/n)),candidates=[];for(let off=-6;off<=6;off++){const q=[p[0],p[1]+off,p[2]];if(walkFloor(get(q))&&[1,2].every(h=>!get([q[0],q[1]+h,q[2]])))candidates.push(q);}
   candidates.sort((a,b)=>Math.abs(a[1]-p[1])-Math.abs(b[1]-p[1]));if(candidates.length)out.push(candidates[0]);else {blocked.push({at:p,route,reason:'existing route prefix has no supported two-air physical surface'});out.push(p);}
  }}return out;
 }
 function fixtureDetour(points,route,config={}){
  const expected=[];for(let j=1;j<points.length;j++){const a=points[j-1],b=points[j],n=Math.max(Math.abs(b[0]-a[0]),Math.abs(b[2]-a[2]),1);for(let i=j===1?0:1;i<=n;i++)expected.push(a.map((v,k)=>Math.round(v+(b[k]-v)*i/n)));}
  const clear=p=>walkFloor(get(p))&&(!config.floor||config.floor(get(p),p))&&[1,2].every(h=>{const q=[p[0],p[1]+h,p[2]],mat=get(q);return !mat||FULL.test(mat)&&!protection(q);})&&(![1,2].some(h=>get([p[0],p[1]+h,p[2]]))||[3,4].every(h=>{const q=[p[0],p[1]+h,p[2]],mat=get(q);return !mat||FULL.test(mat)&&!explicit.has(K(q));}));
  const out=[];
  for(let ei=0;ei<expected.length;ei++){const p=expected[ei],lastExpected=ei===expected.length-1,candidates=[];for(let dx=-3;dx<=3;dx++)for(let dz=-3;dz<=3;dz++)for(let dy=-6;dy<=6;dy++){const q=[p[0]+dx,p[1]+dy,p[2]+dz];if(clear(q))candidates.push(q);}
   candidates.sort((a,b)=>3*(Math.abs(a[0]-p[0])+Math.abs(a[2]-p[2]))+Math.abs(a[1]-p[1])-3*(Math.abs(b[0]-p[0])+Math.abs(b[2]-p[2]))-Math.abs(b[1]-p[1]));
   if(!candidates.length){blocked.push({at:p,route,reason:'no preserved original floor near legacy functional route'});continue;}
   if(!out.length){out.push(candidates[0]);for(const h of [1,2]){const q=[out[0][0],out[0][1]+h,out[0][2]];if(get(q))put(q,null,'bounded existing primary route structural enclosure clearance',route);}continue;}
   const start=out.at(-1);if(candidates.some(q=>K(q)===K(start))&&(!config.forceLast||!lastExpected||K(start)===K(candidates[0])))continue;
   let chosen=null;
   for(const goal of candidates.slice(0,config.forceLast&&lastExpected?1:18)){
    const queue=[start],back=new Map([[K(start),null]]),lo=start.map((v,k)=>Math.min(v,goal[k])-(k===1?2:4)),hi=start.map((v,k)=>Math.max(v,goal[k])+(k===1?2:4));let end=null;
    for(let i=0;i<queue.length&&i<5000;i++){const q=queue[i];if(K(q)===K(goal)){end=K(q);break;}for(const [dx,dz]of [[1,0],[-1,0],[0,1],[0,-1]])for(const dy of [0,-1,1]){const n=[q[0]+dx,q[1]+dy,q[2]+dz],k=K(n);if(back.has(k)||n.some((v,j)=>v<lo[j]||v>hi[j])||!clear(n))continue;
      if(dy){const high=dy>0?n:q,low=dy>0?q:n,mat=get(high),meta=high[0]!==low[0]?(high[0]>low[0]?0:1):(high[2]>low[2]?2:3),guard=protection(high);if(!FULL.test(mat)||guard&&mat!==stair(mat,meta))continue;}
      back.set(k,K(q));queue.push(n);
    }}if(end){const chain=[];for(let k=end;k!==null;k=back.get(k))chain.push(k.split(',').map(Number));chosen=chain.reverse();break;}
   }
   if(!chosen){blocked.push({at:p,from:start,route,reason:'no bounded continuous original-floor fixture detour'});continue;}
   for(const p of chosen)for(const h of [1,2]){const q=[p[0],p[1]+h,p[2]];if(get(q))put(q,null,'bounded existing primary route structural enclosure clearance',route);}
   for(let i=1;i<chosen.length;i++){const a=chosen[i-1],b=chosen[i];if(a[1]!==b[1]){const high=a[1]>b[1]?a:b,low=high===a?b:a,meta=high[0]!==low[0]?(high[0]>low[0]?0:1):(high[2]>low[2]?2:3);put(high,stair(get(high),meta),'same-material stair on selected existing side-route floor',route);}}
   out.push(...chosen.slice(1));
  }
  return out;
 }
 if(alreadyApplied){routes.push(...copy(prior));notes.push('Existing functional routes retained; recheck transition AABB clearance.');}
 if(!alreadyApplied&&m.id==='fallen_foundry'){
  const flight=m.stairRoutes.findIndex(s=>s.task==='west-service-wing'),s0=m.stairRoutes[flight],s1=m.stairRoutes[flight+1];
  if(s0&&s1&&s0.points.length===2&&s1.points.length===2){
   const [a,b]=s0.points,drop=a[1]-b[1],middle=[a[0],b[1],a[2]+drop];author('stairRoutes:'+flight,s0.points,[a,middle,b],5,'gtsr:ruins_slag_masonry#0','restore exact first procedural flight without width-squared clearing');
   const [c,e]=s1.points,x=c[0]+8,drop2=c[1]-e[1],turn=[x,e[1],c[2]-drop2];author('stairRoutes:'+(flight+1),s1.points,[c,[c[0],c[1],c[2]+3],[x,c[1],c[2]+3],[x,c[1],c[2]],turn,[x,e[1],e[2]-3],[e[0],e[1],e[2]-3],e],5,'gtsr:ruins_slag_masonry#0','separate return flight by eight blocks; same original endpoints connected by finite transverse landings');
  }
 }
 if(!alreadyApplied&&m.id==='subsided_factory')for(const n of [18,23]){
  const s=m.stairRoutes[n];if(!s||s.points.length!==4)continue;const [a,b,c,e]=s.points,drop=b[1]-c[1],endZ=b[2]+drop+8;
  // Full flat connectors contain authored fixtures. Never rebuild or erase them.
  // Only the independently failed exterior transition is materialized here.
  const old=copy(s.points),lane=(m.variant===1&&n===18)?447:(m.variant===2&&n===23)?427:b[0],returnX=lane+8,local=[b,[lane,b[1],b[2]],[lane,b[1],b[2]+3],[lane,c[1],b[2]+drop+3],[lane,c[1],endZ],[returnX,c[1],endZ],[returnX,c[1],c[2]-3],[c[0],c[1],c[2]-3],c];
  // The old low knot is an existing slab. Three flat blocks before every turn
  // prevent transverse landing rows from filling the first descending step.
  author('local:'+n,old,local,s.width||5,'gtsr:ruins_sootstone_tiles#0','only finite exterior descent and return landing; existing flat connector fixtures untouched');
  const receipt=routes.at(-1),prefix=flatConnector(a,b,'stairRoutes:'+n),suffix=flatConnector(c,e,'stairRoutes:'+n);receipt.originalRouteKey='stairRoutes:'+n;receipt.actualPoints=[...prefix.slice(0,-1),...receipt.actualPoints,...suffix.slice(1)];
  receipt.functionalEndpoints={original:[a,e],actual:[receipt.actualPoints[0],receipt.actualPoints.at(-1)],sameFloor:true,endRoom:roomShell(e)?null:(m.rooms||[]).find(r=>e[0]>=r.x&&e[0]<r.x+r.w&&e[2]>=r.z&&e[2]<r.z+r.d)?.moduleId||null};
  receipt.trace=receipt.actualPoints.map(p=>({at:p,material:get(p),clear:[1,2].every(h=>!get([p[0],p[1]+h,p[2]]))}));
  receipt.connectorUnresolved=receipt.trace.filter(p=>!walkFloor(p.material)||!p.clear);if(receipt.connectorUnresolved.length){notes.push('Existing flat connector needs physical fixture-aware path review: stairRoutes:'+n);blocked.push(...receipt.connectorUnresolved.map(p=>({...p,route:'stairRoutes:'+n,reason:'full functional connector floor/clearance assertion'})));}
  m.stairRoutes[n].points=copy(receipt.actualPoints);m.stairRoutes[n].handrail=false;for(let i=0;i<m.routes.length;i++)if(JSON.stringify(m.routes[i])===JSON.stringify(old))m.routes[i]=copy(receipt.actualPoints);
 }
 if(!alreadyApplied&&m.id==='valve_yard'&&m.variant===2){
  const row=rows.find(x=>x.route==='routes:0');if(row)for(const c of row.columns)for(const h of [1,2])put([c.metadataLow[0],c.metadataLow[1]+h,c.metadataLow[2]],null,'open the existing authored ascending entrance under obstructing enclosure',row.route);
  // A one-cell orthogonal landing joins the retained console's east-side
  // platform to the original lower paving without removing console/valves.
  const corner=[43,-1,37];if(!get(corner)&&get([42,0,37])&&get([43,-1,38])&&[1,2].every(h=>!get([43,-1+h,37])))put(corner,'gtsr:ruins_mossroot_paving#0','one-cell original-platform side landing around retained console and steam valves','routes:0');
  const old=copy(m.routes[0]),actual=fixtureDetour(old,'routes:0');m.routes[0]=copy(actual);routes.push({originalRouteKey:'routes:0',originalPoints:old,actualPoints:actual,width:5,traversalWidth:1,reason:'same functional path through restored entrance, bounded existing floor detours preserve mechanism/decor fixtures',trace:actual.map(p=>({at:p,material:get(p),clear:[1,2].every(h=>!get([p[0],p[1]+h,p[2]]))})),assertions:[]});
 }
 if(!alreadyApplied&&m.id==='trench_infirmary'){
  const old=copy(m.routes[0]);
  // This local transition joins independently observed original lower/upper surfaces.
  const sideX=m.variant===2?64:[64,63,62,61].find(x=>Array.from({length:14},(_,i)=>[x,-11,55+i]).every(p=>(!get(p)||FULL.test(get(p)))&&[1,2].every(h=>{const q=[p[0],p[1]+h,p[2]],v=get(q);return !v||FULL.test(v)&&!protection(q);})));
  if(sideX===undefined)blocked.push({route:'routes:0',reason:'no finite lower-deck side lane avoids retained pollution and semantic fixtures'});
  author('local:0',old,m.variant===2?[[64,-11,55],[64,-11,48],[67,-8,48],[67,-8,45],[70,-7,45],[70,-7,48]]:[[64,-11,55],[sideX??64,-11,55],[sideX??64,-11,68],[(sideX??64)+4,-7,68],[72,-7,68],[72,-7,48],[70,-7,48]],1,'gtsr:ruins_mossroot_paving#0','finite three-step side ascent between original lower deck and upper original path');
  const receipt=routes.at(-1),prefix=(m.variant===2?legacyFloors:fixtureDetour)([...old.slice(0,3),[60,-11,60],[64,-11,60],[64,-11,55]],'routes:0');receipt.originalRouteKey='routes:0';const first=receipt.actualPoints[0],last=prefix.at(-1),bridge=samples([last,[first[0],last[1],last[2]],first]);receipt.actualPoints=[...prefix,...bridge.slice(1),...receipt.actualPoints.slice(1)];receipt.trace=receipt.actualPoints.map(p=>({at:p,material:get(p),clear:[1,2].every(h=>!get([p[0],p[1]+h,p[2]]))}));m.routes[0]=copy(receipt.actualPoints);
  for(let i=1;i<receipt.actualPoints.length;i++){const a=receipt.actualPoints[i-1],b=receipt.actualPoints[i];if(a[1]===b[1])continue;const high=a[1]>b[1]?a:b,low=high===a?b:a;if(Math.abs(high[1]-low[1])!==1){blocked.push({at:high,low,route:'routes:0',reason:'legacy prefix vertical jump'});continue;}if(high[0]!==low[0]&&high[2]!==low[2]){const corner=[[low[0],low[1],high[2]],[high[0],low[1],low[2]]].find(p=>walkFloor(get(p))&&[1,2].every(h=>!get([p[0],p[1]+h,p[2]])));if(!corner){blocked.push({at:high,low,route:'routes:0',reason:'legacy prefix rise needs orthogonal landing'});continue;}receipt.actualPoints.splice(i,0,corner);i--;continue;}const meta=high[0]!==low[0]?(high[0]>low[0]?0:1):(high[2]>low[2]?2:3);if(!FULL.test(get(high))){blocked.push({at:high,route:'routes:0',reason:'legacy prefix high floor is semantic non-walk fixture'});continue;}put(high,stair(get(high),meta),'same-material stair on exact existing lower-prefix floor, preserving functional endpoint approach','routes:0');}
  m.routes[0]=copy(receipt.actualPoints);
  notes.push('routes:0 retains original entrance and functional rooms; prefix uses existing lower deck, local stair joins original upper exit');
 }
 // The third foundry flight shared its initial columns with the repaired
 // second landing. Keep its original endpoints with an exterior return lane.
 if(m.id==='fallen_foundry'&&!routes.some(r=>r.originalRouteKey==='stairRoutes:9')){
  const old=copy(m.stairRoutes[9].points),[a,e]=old,lane=a[0]+16,drop=a[1]-e[1];
  author('stairRoutes:9',old,[a,[lane,a[1],a[2]],[lane,a[1],a[2]+3],[lane,e[1],a[2]+3+drop],[lane,e[1],e[2]+3],[e[0],e[1],e[2]+3],e],5,'gtsr:ruins_slag_masonry#0','finite separated third foundry flight preserving exact old endpoints');
 }
 if(m.id==='fallen_foundry'){
  const audit=JSON.parse(fs.readFileSync(path.join(ROOT,'temp/stair-route-audit.json'),'utf8')).rows.find(r=>r.id===m.id&&r.variant===m.variant);
  for(const n of [11,12])if(!routes.some(r=>r.originalRouteKey==='routes:'+n)){
   const flight=m.stairRoutes[n===11?8:9].points,end=m.routes[n].at(-1),connector=flatConnector(flight.at(-1),end,'routes:'+n),points=[...copy(flight),...connector.slice(1)];m.routes[n]=copy(points);
   routes.push({originalRouteKey:'routes:'+n,originalPoints:copy(audit.routeMatrix.find(r=>r.key==='routes:'+n).points),actualPoints:points,width:5,traversalWidth:1,reason:'complete original foundry flight and room connector endpoints through repaired physical stair lane',trace:[],assertions:[]});
  }
 }
 // The final archive landing erased the middle of a two-block descent.
 // Use the existing outer side lane around its retained catwalk railing.
 if(m.id==='city_archive_cache'&&!routes.some(r=>r.originalRouteKey==='routes:1')){
  const old=copy(m.routes[1]),x=old[0][0],y=old[2][1],z=old[2][2];
  author('local:archive-bottom',old,[[x,y+2,z-5],[x+3,y+2,z-5],[x+3,y,z-3],[x+3,y,z]],1,'gtsr:ruins_mossroot_paving#0','finite raised outer landing and two-step descent over existing supported corridor; original railing retained below/alongside');
  const receipt=routes.at(-1),prefix=legacyFloors([...old.slice(0,2),[x,y+2,z-5]],'routes:1'),suffix=legacyFloors([[x+3,y,z],old.at(-1)],'routes:1');receipt.originalRouteKey='routes:1';receipt.actualPoints=[...prefix,...receipt.actualPoints.slice(1),...suffix.slice(1)];
  for(let i=1;i<receipt.actualPoints.length;i++){const a=receipt.actualPoints[i-1],b=receipt.actualPoints[i];if(a[1]===b[1])continue;const high=a[1]>b[1]?a:b,low=high===a?b:a,meta=high[0]!==low[0]?(high[0]>low[0]?0:1):(high[2]>low[2]?2:3);if(FULL.test(get(high)))put(high,stair(get(high),meta),'exact existing authored corridor high-floor riser; original material retained','routes:1');}
  m.routes[1]=copy(receipt.actualPoints);if(m.stairRoutes[1])m.stairRoutes[1].points=copy(receipt.actualPoints);
 }
 if(m.id==='city_dome_hall'&&m.variant===2&&!routes.some(r=>r.originalRouteKey==='routes:0')){
  const old=copy(m.routes[0]);
  // The z26 truss is the retained room beam, not a walking surface. Descend
  // beside the existing ramp onto the room's y2 floor, walk below the beam,
  // then use three finite stairs to regain the original end platform.
  author('local:dome-under-beam',old,[[7,4,23],[10,4,23],[12,2,23],[14,2,23],[14,2,28],[11,5,28],[7,5,28]],1,'gtsr:ruins_mossroot_paving#0','finite side descent through original room floor below retained z26 roof truss, then same-material ascent to original platform');
  const receipt=routes.at(-1),prefix=fixtureDetour([...old.slice(0,2),[7,4,23]],'routes:0',{forceLast:true,floor:mat=>/^gtsr:ruins_(mossroot_paving|sootstone_tiles)(?:_stairs)?#/.test(mat)});receipt.originalRouteKey='routes:0';receipt.actualPoints=[...prefix,...receipt.actualPoints.slice(1)];receipt.functionalEndpoints={original:[old[0],old.at(-1)],actual:[receipt.actualPoints[0],receipt.actualPoints.at(-1)],retainedBeam:[7,6,26]};
  for(let i=0;i<m.routes.length;i++)if(JSON.stringify(m.routes[i])===JSON.stringify(old))m.routes[i]=copy(receipt.actualPoints);
 }
 if(m.id==='mirror_barracks'&&m.variant===2&&!routes.some(r=>r.originalRouteKey==='routes:1')){
  const old=copy(m.routes[1]),ap=m.interactionApproaches.find(r=>r.node==='shutter'),a=copy(ap.floor.at),door=[155,-12,163],outside=[155,-12,166],end=old.at(-1);
  author('local:mirror-return',old,[door,outside,[163,-4,166],[end[0]+5,-4,166]],1,'gtsr:ruins_mossroot_paving#0','finite exterior stair and return landing from existing south door to original high endpoint; roof is never used as a floor');
  const receipt=routes.at(-1),prefix=fixtureDetour([a,[155,-12,144],door],'routes:1'),last=prefix.at(-1),join=samples([last,[door[0],last[1],last[2]],door]);receipt.originalRouteKey='routes:1';const final=flatConnector(receipt.actualPoints.at(-1),end,'routes:1');receipt.actualPoints=[...prefix,...join.slice(1),...receipt.actualPoints.slice(1),...final.slice(1)];receipt.functionalEndpoints={original:[old[0],old.at(-1)],actual:[a,end],sourceNode:'shutter',startReason:'exact original shutter interaction approach; original route center intersects the physical tuner'};m.routes[1]=copy(receipt.actualPoints);if(m.stairRoutes[0])m.stairRoutes[0].points=copy(receipt.actualPoints);
  // Narrow load-bearing posts below the new finite outside walking strip.
  for(const p of receipt.actualPoints)if(p[2]>=164)for(let y=-13;y<p[1];y++){const q=[p[0],y,p[2]];if(!get(q))put(q,'gtsr:ruins_mossroot_paving#0','bounded stair/return load-bearing post to existing local ground datum','routes:1');}
 }
 // Three-block side-room differences need finite physical stairs, while the
 // central accident cuts and exact recorded loot floors remain unchanged.
 if(m.id==='subsided_factory'){
  m.structureDesignReview.stairFunctionalAccessRoutes=m.structureDesignReview.stairFunctionalAccessRoutes||[];
  for(const n of [2,6,11,16,21]){
   if(routes.some(r=>r.originalRouteKey==='routes:'+n))continue;
   const old=copy(m.routes[n]),a=old[0],room=m.rooms.find(r=>r.moduleId==='subsided_factory-module-'+({2:3,6:7,11:11,16:15,21:19}[n])),x=a[0],y=a[1],sideRoutes=new Map();
   for(const side of [-1,1]){
    const z=Array.from({length:9},(_,i)=>room.z+16+i).find(zz=>{
     const low=[x,y,zz];if(!walkFloor(get(low))||![1,2].every(h=>!get([x,y+h,zz])))return false;
     for(let i=1;i<=4;i++){const at=[x+side*i,y+Math.min(i,3),zz];if(protection(at)||get(at)&&!FULL.test(get(at)))return false;for(const h of [1,2]){const q=[at[0],at[1]+h,zz];if(get(q)&&(!FULL.test(get(q))||protection(q)))return false;}}return true;
    });
    if(z===undefined){blocked.push({route:'routes:'+n,side,reason:'no bounded side-ascent lane avoids all actor/hazard/semantic fixtures'});continue;}
    const low=[x,y,z],landing=[x+side*4,y+3,z];author('local:archive-'+n+'-'+side,old,[low,[x+side*3,y+3,z],landing],1,'gtsr:ruins_sootstone_tiles#0','finite same-material side ascent to existing archive deck; central accident cut retained');
    const receipt=routes.pop(),prefix=fixtureDetour([a,low],'routes:'+n);const join=samples([prefix.at(-1),[low[0],y,prefix.at(-1)[2]],low]);receipt.actualPoints=[...prefix,...join.slice(1),...receipt.actualPoints.slice(1)];sideRoutes.set(side,receipt);
    if(side===-1){receipt.originalRouteKey='routes:'+n;receipt.functionalEndpoints={original:copy([old[0],old.at(-1)]),actual:[copy(a),copy(landing)],endRoom:room.moduleId,reason:'old center endpoint lies in authored cut; use existing same-room loot deck'};routes.push(receipt);m.routes[n]=copy(receipt.actualPoints);if(m.stairRoutes[n]){m.stairRoutes[n].points=copy(receipt.actualPoints);m.stairRoutes[n].handrail=false;}}
    else {receipt.originalRouteKey='functional:archive-east-'+n;m.structureDesignReview.stairFunctionalAccessRoutes.push(receipt);}
   }
   const nodes=new Set(m.nodes.filter(t=>t.module===room.moduleId&&t.role==='chest').map(t=>t.id));
   for(const ap of m.interactionApproaches.filter(t=>nodes.has(t.node))){
    const goal=ap.floor.at,side=goal[0]>x?1:-1,receipt=sideRoutes.get(side);if(!receipt)continue;const landing=receipt.actualPoints.at(-1);let destination=goal,tail=[];
    const passHead=p=>[1,2].every(h=>{const v=get([p[0],p[1]+h,p[2]]);return !v||/^gtsr:draft5_(salt_bloom|corrosion_flake)#/.test(v);});
    const reachable=new Set([K(landing)]),reachQueue=[landing];for(let i=0;i<reachQueue.length&&i<2000;i++){const p=reachQueue[i];for(const [dx,dz]of [[1,0],[-1,0],[0,1],[0,-1]]){const q=[p[0]+dx,p[1],p[2]+dz],k=K(q);if(reachable.has(k)||q[0]<=room.x||q[0]>=room.x+room.w-1||q[2]<=room.z||q[2]>=room.z+room.d-1||!walkFloor(get(q))||!passHead(q))continue;reachable.add(k);reachQueue.push(q);}}
    if(goal[1]===landing[1]+1){
     const candidates=[];for(let dx=-2;dx<=2;dx++)for(let dz=-2;dz<=2;dz++){const q=[goal[0]+dx,landing[1],goal[2]+dz];if(q[0]<=room.x||q[0]>=room.x+room.w-1||q[2]<=room.z||q[2]>=room.z+room.d-1||!walkFloor(get(q))||![1,2].every(h=>!get([q[0],q[1]+h,q[2]])))continue;candidates.push(q);}
     candidates.sort((a,b)=>Math.abs(a[0]-goal[0])+Math.abs(a[2]-goal[2])-Math.abs(b[0]-goal[0])-Math.abs(b[2]-goal[2]));
     const candidate=candidates.find(q=>reachable.has(K(q)));if(!candidate){blocked.push({route:'functional:'+ap.node,reason:'decorative original pad lacks neighboring real room deck stand',goal});continue;}
     destination=candidate;const before=copy(ap);ap.floor={at:copy(destination),material:get(destination)};ap.feet=[destination[0],destination[1]+1,destination[2]];ap.eye=[ap.feet[0]+.5,ap.feet[1]+1.62,ap.feet[2]+.5];ap.distance=+Math.hypot(...ap.target.map((v,i)=>v-ap.eye[i])).toFixed(3);ap.headClearance=true;
     m.structureDesignReview.stairApproachRelocations=m.structureDesignReview.stairApproachRelocations||[];m.structureDesignReview.stairApproachRelocations.push({node:ap.node,before,after:copy(ap),reason:'neighboring existing true room deck supports an actual player; original decoration and exact chest identity remain'});
    }
    const queue=[landing],back=new Map([[K(landing),null]]);let end=null;
    for(let i=0;i<queue.length&&i<2000;i++){const p=queue[i];if(K(p)===K(destination)){end=K(p);break;}for(const [dx,dz]of [[1,0],[-1,0],[0,1],[0,-1]]){const q=[p[0]+dx,p[1],p[2]+dz],k=K(q);if(back.has(k)||q[0]<=room.x||q[0]>=room.x+room.w-1||q[2]<=room.z||q[2]>=room.z+room.d-1||!walkFloor(get(q))||!passHead(q))continue;back.set(k,K(p));queue.push(q);}}
    if(!end){blocked.push({route:'functional:'+ap.node,reason:'no preserved archive deck path from side stair to exact recorded loot floor',landing,goal});continue;}
    const flat=[];for(let k=end;k!==null;k=back.get(k))flat.push(k.split(',').map(Number));flat.reverse();m.structureDesignReview.stairFunctionalAccessRoutes.push({originalRouteKey:'functional:'+ap.node,actualPoints:[...copy(receipt.actualPoints),...flat.slice(1),...tail],width:1,traversalWidth:1,reason:'complete main-corridor to exact recorded original loot interaction floor, preserving identity and accident cut',endpointNode:ap.node,endpointFloor:copy(destination),state:'closed'});
   }
  }
 }
 // A retained membrane above one eastern rise requires a neighboring lane,
 // rather than removal of the functional pollution geometry.
 if(m.id==='subsided_factory'&&m.variant===2){
  const functional=m.structureDesignReview.stairFunctionalAccessRoutes||[],east=functional.find(r=>r.originalRouteKey==='functional:archive-east-16');
  if(east&&east.actualPoints.at(-1)[2]===284){
   const oldPrefix=copy(east.actualPoints),a=oldPrefix[0],low=[200,-89,285],landing=[204,-86,285];
   author('local:archive-east-membrane-bypass',east.originalPoints,[low,[203,-86,285],landing],1,'gtsr:ruins_sootstone_tiles#0','one-block adjacent supported stair lane avoids retained pollution membrane; original hazard and old stairs remain');
   const replacement=routes.pop(),prefix=fixtureDetour([a,low],'functional:archive-east-16'),join=samples([prefix.at(-1),[low[0],low[1],prefix.at(-1)[2]],low]);replacement.actualPoints=[...prefix,...join.slice(1),...replacement.actualPoints.slice(1),oldPrefix.at(-1)];
   for(const r of functional)if(JSON.stringify(r.actualPoints.slice(0,oldPrefix.length))===JSON.stringify(oldPrefix))r.actualPoints=[...copy(replacement.actualPoints),...r.actualPoints.slice(oldPrefix.length)];
  }
 }
 if(m.id==='city_watch_tower'&&m.variant>0&&!routes.some(r=>r.originalRouteKey==='routes:0')){
  const old=copy(m.routes[0]),entry=[18,2,8],rear=[18,2,25],door=[m.variant===1?18:14,2,28],room=m.rooms[1];
  const gauge=[17,3,15],gaugeMat=get(gauge);if(gaugeMat==='gtsr:draft_kiln_gauge_mark#0'&&!protection(gauge)){
   let destination=null;for(const x of [22,11])for(let z=9;z<24&&!destination;z++){const q=[x,3,z];if(!get(q)&&!get([x,4,z])&&walkFloor(get([x,2,z]))&&!protection(q))destination=q;}
   if(!destination)blocked.push({route:'routes:0',reason:'unbound gauge has no supported same-room wall location'});else{edits.set(K(gauge),null);edits.set(K(destination),gaugeMat);changes.push({at:gauge,before:gaugeMat,after:null,reason:'move only unbound full-cube gauge from central aisle to supported same-room wall',route:'routes:0'},{at:destination,before:null,after:gaugeMat,reason:'preserved unbound gauge at supported same-room wall',route:'routes:0'});m.structureDesignReview.stairDecorationRelocations=m.structureDesignReview.stairDecorationRelocations||[];m.structureDesignReview.stairDecorationRelocations.push({material:gaugeMat,from:gauge,to:destination,reason:'preserve decoration count and room function while opening one existing aisle cell'});}
  }
  boundedDoorClearance.add(K([door[0],4,29]));
  const pass=p=>walkFloor(get(p))&&[1,2].every(h=>{const q=[p[0],p[1]+h,p[2]],v=get(q);return !v||FULL.test(v)&&(!protection(q)||boundedDoorClearance.has(K(q)));});
  function existingPath(a,b,box,mutate=true){const queue=[a],back=new Map([[K(a),null]]),cost=new Map([[K(a),0]]);let end=null;while(queue.length){queue.sort((a,b)=>cost.get(K(a))-cost.get(K(b)));const p=queue.shift(),pk=K(p);if(pk===K(b)){end=pk;break;}for(const [dx,dz]of [[1,0],[-1,0],[0,1],[0,-1]]){const q=[p[0]+dx,p[1],p[2]+dz],k=K(q);if(q[0]<box[0]||q[0]>box[1]||q[2]<box[2]||q[2]>box[3]||!pass(q))continue;const n=cost.get(pk)+1+[1,2].filter(h=>get([q[0],q[1]+h,q[2]])).length*100;if(cost.has(k)&&cost.get(k)<=n)continue;cost.set(k,n);back.set(k,pk);queue.push(q);}}if(!end)return [];const out=[];for(let k=end;k!==null;k=back.get(k))out.push(k.split(',').map(Number));out.reverse();if(mutate)for(const p of out)for(const h of [1,2]){const q=[p[0],p[1]+h,p[2]];if(get(q))put(q,null,'finite actual doorway/aisle headroom; adjacent beam supports and all semantic fixtures retained','routes:0');}return out;}
  author('local:watch-entry',old,[[18,0,0],[18,0,5],[18,2,7],entry],1,'gtsr:ruins_mossroot_paving#0','bounded supported entrance flight from original natural-ground datum to existing main-room door');
  const receipt=routes.pop(),inside=existingPath(entry,rear,[9,24,8,25]),turn=existingPath(rear,[door[0],2,25],[9,24,8,25]);if(!inside.length||!turn.length)blocked.push({route:'routes:0',reason:'main room existing deck cannot connect preserved entry/rear doors'});
  author('local:watch-door-link',old,[[door[0],2,25],door],1,'gtsr:ruins_sootstone_tiles#0','two-cell supported connector between existing facing room doors, below retained roof route');
  const bridge=routes.pop();for(const p of bridge.actualPoints)for(let y=0;y<p[1];y++)if(!get([p[0],y,p[2]]))put([p[0],y,p[2]],'gtsr:ruins_slag_masonry#0','finite rear-door connector support to original local ground datum','routes:0');
  receipt.actualPoints=[...receipt.actualPoints,...inside.slice(1),...turn.slice(1),...bridge.actualPoints.slice(1)];receipt.originalRouteKey='routes:0';receipt.functionalEndpoints={original:copy([old[0],old.at(-1)]),actual:[copy(receipt.actualPoints[0]),copy(door)],endRoom:room.moduleId,reason:'generic source route ended on outer decoration/roof; connect actual original workband entrance'};routes.push(receipt);m.routes[0]=copy(receipt.actualPoints);m.routes[1]=copy(receipt.actualPoints);routes.push({...copy(receipt),originalRouteKey:'routes:1'});
  m.structureDesignReview.stairFunctionalAccessRoutes=m.structureDesignReview.stairFunctionalAccessRoutes||[];
  for(const ap of m.interactionApproaches.filter(a=>/^loot7-[4-7]$/.test(a.node))){let goal=copy(ap.floor.at);if(goal[1]!==2||!existingPath(door,goal,[room.x+1,room.x+room.w-2,room.z,room.z+room.d-2],false).length){const choices=[];for(let dx=-5;dx<=5;dx++)for(let dz=-5;dz<=5;dz++){const q=[goal[0]+dx,2,goal[2]+dz];if(q[0]>room.x&&q[0]<room.x+room.w-1&&q[2]>room.z&&q[2]<room.z+room.d-1&&pass(q)&&Math.hypot(q[0]+.5-ap.target[0],4.62-ap.target[1],q[2]+.5-ap.target[2])<=4.25)choices.push(q);}choices.sort((a,b)=>Math.abs(a[0]-goal[0])+Math.abs(a[2]-goal[2])-Math.abs(b[0]-goal[0])-Math.abs(b[2]-goal[2]));const chosen=choices.find(q=>existingPath(door,q,[room.x+1,room.x+room.w-2,room.z,room.z+room.d-2],false).length);if(!chosen){blocked.push({route:ap.node,reason:'no adjacent true workband deck stand',goal});continue;}const before=copy(ap);goal=chosen;ap.floor={at:copy(goal),material:get(goal)};ap.feet=[goal[0],3,goal[2]];ap.eye=[goal[0]+.5,4.62,goal[2]+.5];ap.distance=+Math.hypot(...ap.target.map((v,i)=>v-ap.eye[i])).toFixed(3);m.structureDesignReview.stairApproachRelocations=m.structureDesignReview.stairApproachRelocations||[];m.structureDesignReview.stairApproachRelocations.push({node:ap.node,before,after:copy(ap),reason:'neighboring existing workband deck replaces inaccessible decorative wall/column top, retaining original chest and all fixtures'});}
   const tail=existingPath(door,goal,[room.x+1,room.x+room.w-2,room.z,room.z+room.d-2]);if(!tail.length)blocked.push({route:ap.node,reason:'true existing workband deck cannot reach recorded interaction floor',goal});if(tail.length)m.structureDesignReview.stairFunctionalAccessRoutes.push({originalRouteKey:'functional:'+ap.node,actualPoints:[...copy(receipt.actualPoints),...tail.slice(1)],width:1,traversalWidth:1,endpointNode:ap.node,endpointFloor:copy(goal),state:'closed',reason:'complete original ground entry via actual main room and rear doors to same original workband loot identity'});
  }
 }
 if(m.id==='city_watch_tower'&&m.variant===2&&!m.structureDesignReview.stairLoot6PocketRepair){
  const wood=[[10,3,30],[10,4,30]],chair=[9,3,31],destination=[11,3,33];
  const moves=[...wood.map(at=>({at,before:'gtsr:royal_heartwood#0',after:null})),{at:chair,before:'gtsr:draft6_chair#0',after:null},{at:destination,before:null,after:'gtsr:draft6_chair#0'}];
  if(moves.some(q=>get(q.at)!==q.before||protection(q.at)&&!(K(q.at)==='10,4,30'&&protection(q.at)==='room-shell'))||!walkFloor(get([11,2,33]))||get([11,4,33]))blocked.push({route:'functional:loot7-6',reason:'bounded inner-column recess/chair relocation intersects a semantic fixture',moves:moves.map(q=>({...q,current:get(q.at),protection:protection(q.at)})),support:get([11,2,33]),head:get([11,4,33])});
  else{
   for(const q of moves){edits.set(K(q.at),q.after);changes.push({...q,reason:'bounded internal column-end recess and supported same-room chair relocation preserve workbench, original chest, hazard and exterior shell',route:'functional:loot7-6'});}
   const ap=m.interactionApproaches.find(a=>a.node==='loot7-6'),before=copy(ap),goal=[8,2,31];ap.floor={at:copy(goal),material:get(goal)};ap.feet=[8,3,31];ap.eye=[8.5,4.62,31.5];ap.distance=+Math.hypot(...ap.target.map((v,i)=>v-ap.eye[i])).toFixed(3);
   const route=m.structureDesignReview.stairFunctionalAccessRoutes.find(r=>r.originalRouteKey==='functional:loot7-6'),prefix=routes.find(r=>r.originalRouteKey==='routes:0').actualPoints;
   route.actualPoints=[...copy(prefix),...samples([[14,2,28],[14,2,30],[11,2,30],[10,2,30],[10,2,31],goal]).slice(1)];route.endpointFloor=copy(goal);route.reason='complete original entry to original loot6 via bounded internal recess; supported chair relocation preserves all furniture and pollution';
   m.structureDesignReview.stairApproachRelocations.push({node:ap.node,before,after:copy(ap),reason:'actual reachable pocket deck gives unobstructed sight of same original chest while retaining its neighboring workbench'});
   m.structureDesignReview.stairLoot6PocketRepair={columnRecess:wood,chair:{from:chair,to:destination},retained:['original chest','original workbench','pollution tendril','exterior wall','complete roof'],floor:goal};
  }
 }
 // Entity AABB crosses into the low column before it settles onto its stair.
 // Clearing only feet/head at floor+1/+2 leaves floor+3 roofs blocking that
 // transition. Preserve all semantic fixtures and actual room shells.
 for(const route of [...routes,...(m.structureDesignReview.stairFunctionalAccessRoutes||[])])for(let i=1;i<route.actualPoints.length;i++){
  const a=route.actualPoints[i-1],b=route.actualPoints[i];if(a[1]===b[1])continue;
  const low=a[1]<b[1]?a:b,q=[low[0],low[1]+3,low[2]];
  if(get(q))put(q,null,'finite low-column AABB clearance while crossing existing stair rise',route.originalRouteKey);
 }
 // Recheck after all flights: a later landing must never erase an earlier stair.
 for(const route of routes){route.trace=route.actualPoints.map(p=>({at:p,material:get(p),clear:[1,2].every(h=>!get([p[0],p[1]+h,p[2]]))}));for(let i=0;i<route.trace.length;i++){const q=route.trace[i];if(!walkFloor(q.material)||!q.clear)blocked.push({...q,route:route.originalRouteKey,reason:'final all-route floor/clearance assertion'});if(!i)continue;const a=route.trace[i-1].at,b=q.at;if(Math.max(...a.map((v,j)=>Math.abs(v-b[j])))>1)blocked.push({route:route.originalRouteKey,from:a,to:b,reason:'nonadjacent final floor samples'});if(a[1]===b[1])continue;const high=a[1]>b[1]?a:b,low=high===a?b:a,meta=high[0]!==low[0]?(high[0]>low[0]?0:1):(high[2]>low[2]?2:3);if(!FULL.test(get(high))||get(high)!==stair(get(high),meta))blocked.push({route:route.originalRouteKey,at:high,low,material:get(high),expectedMeta:meta,reason:'final stair orientation assertion'});}}
 // Coordinate audit data is deliberately nested under the converter-excluded review root.
 m.structureDesignReview=m.structureDesignReview||{};m.structureDesignReview.stairExceptionRoutes=routes;
 if(edits.size){const palette=[...d.voxel.palette],pi=new Map(palette.map((v,i)=>[v,i])),runs=[];function add(x,y,z,n,mat){if(!mat)return;if(!pi.has(mat)){pi.set(mat,palette.length);palette.push(mat);}const q=runs.at(-1),id=pi.get(mat);if(q&&q[1]===y&&q[2]===z&&q[0]+q[3]===x&&q[4]===id)q[3]+=n;else runs.push([x,y,z,n,id]);}
  for(const [x,y,z,n,id]of d.voxel.runs){let start=x;for(let i=x;i<x+n;i++)if(edits.has(K([i,y,z]))){if(i>start)add(start,y,z,i-start,d.voxel.palette[id]);add(i,y,z,1,edits.get(K([i,y,z])));start=i+1;}if(start<x+n)add(start,y,z,x+n-start,d.voxel.palette[id]);}
  for(const [k,mat]of edits){const p=k.split(',').map(Number);if(mat&&!original(p))add(...p,1,mat);}runs.sort((a,b)=>a[1]-b[1]||a[2]-b[2]||a[0]-b[0]);d.voxel={...d.voxel,palette,runs,count:runs.reduce((n,r)=>n+r[3],0)};
 }
 if(!dry&&!blocked.length){input.voxel=d.voxel;input.metadata=d.metadata;}
 return {changes,routes,notes,blocked,data:d,dryRun:dry,applied:!dry&&!blocked.length,prefabName};
}
module.exports={applyExceptions,samples};
