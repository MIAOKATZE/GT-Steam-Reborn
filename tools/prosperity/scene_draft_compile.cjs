/* Compile approved voxel drafts. Never writes production assets or modifies the native lake tree. */
'use strict';
const fs=require('fs'),path=require('path'),vm=require('vm'),crypto=require('crypto');
const root=path.resolve(__dirname,'../..'),out=path.resolve(root,process.argv[2]||'temp/scene-refine-v70');
fs.mkdirSync(out,{recursive:true});
const context={};vm.createContext(context);
for(const file of ['factory.js','foundry.js'])vm.runInContext(fs.readFileSync(path.join(root,'plan/prosperity/design-draft',file),'utf8'),context);
const A='minecraft:air#0',C='gtsr:draft7_seal_chest#0',CONSOLE='gtsr:draft_pressure_console#0';
const key=(x,y,z)=>`${x},${y},${z}`,point=p=>[p[0],p[1]-24,p[2]],reports=[];
for(const id of ['subsided_factory','fallen_foundry']){
 const d=context.ProsperitySceneDrafts[id](),factory=id==='subsided_factory';
 const contextKeys=new Set(d.contextKeys||[]),cells=new Map();
 let excluded=0;for(const [x,y,z,m]of d.voxels){if(contextKeys.has(key(x,y,z))||/^minecraft:(grass|dirt)#/.test(m)){excluded++;continue;}cells.set(key(x,y-24,z),[x,y-24,z,m]);}
 let addedAir=0;function fill(min,max){for(let x=min[0];x<=max[0];x++)for(let y=min[1];y<=max[1];y++)for(let z=min[2];z<=max[2];z++){const k=key(x,y,z);if(!cells.has(k)){cells.set(k,[x,y,z,A]);addedAir++;}}}
 const roofs=(d.roofKeys||[]).map(k=>point(k.split(',').map(Number))).filter(p=>cells.get(key(...p))?.[3]!==A&&cells.has(key(...p)));
 const roofColumn=new Map();for(const [x,y,z]of roofs)roofColumn.set(`${x},${z}`,Math.max(y,roofColumn.get(`${x},${z}`)??-Infinity));
 const moduleNames=factory?['receiving','washing','weaving','return','sludge','service','core']:['casting','furnace','cooling','recovery','receiving'];
 const rooms=d.rooms.map((r,i)=>({name:r.name,min:point(r.min),max:point(r.max),x:r.min[0],y:r.min[1]-24,z:r.min[2],w:r.max[0]-r.min[0]+1,d:r.max[2]-r.min[2]+1,h:r.max[1]-r.min[1],roofMinY:r.roofMinY-24,function:r.name,moduleId:moduleNames[i],style:r.style||'flat'}));
 // Negative space is physically present, including gable/saw roof upper volumes.
 for(const r of rooms)for(let x=r.x+1;x<r.x+r.w-1;x++)for(let z=r.z+1;z<r.z+r.d-1;z++)fill([x,r.y+1,z],[x,roofColumn.get(`${x},${z}`)??r.max[1]-1,z]);
 const airBoxes=d.airBoxes.map(b=>({...b,min:point(b.min),max:point(b.max)}));
 for(const b of airBoxes)fill(b.min,b.max);
 const routes=d.routes.map(r=>r.map(point));
 for(const points of routes)for(let i=1;i<points.length;i++){const a=points[i-1],b=points[i],n=Math.max(Math.abs(a[0]-b[0]),Math.abs(a[2]-b[2]),1),along=Math.abs(a[0]-b[0])>=Math.abs(a[2]-b[2]);for(let j=0;j<=n;j++){const p=a.map((v,c)=>Math.round(v+(b[c]-v)*j/n));for(let t=-2;t<=2;t++)fill([p[0]+(along?0:t),p[1]+1,p[2]+(along?t:0)],[p[0]+(along?0:t),p[1]+4,p[2]+(along?t:0)]);}}
 const nodes=d.nodes.map(n=>{const q={...n,y:n.y-24};if(q.approach)q.approach=point(q.approach);if(q.footprint)q.footprint={min:point(q.footprint.min),max:point(q.footprint.max)};if(q.clearance?.min)q.clearance={min:point(q.clearance.min),max:point(q.clearance.max)};q.module=factory?(moduleNames[d.rooms.findIndex(r=>r.name===n.module)]||'core'):n.module;if(q.role==='memory'){q.block=CONSOLE;q.previewBlock=CONSOLE;cells.set(key(q.x,q.y,q.z),[q.x,q.y,q.z,CONSOLE]);}return q;});
 const bossModule=factory?'factory-boss-group':'foundry-boss-group';
 const spawns=nodes.filter(n=>['guard','boss'].includes(n.role)).map(n=>{const boss=n.role==='boss',q={id:n.id,role:n.role,code:n.code||({'north-guard-a':'dr-18','north-guard-b':'dr-08','west-guard':'dr-04','east-guard':'dr-15'}[n.id]),x:n.x,y:n.y,z:n.z,module:boss?bossModule:n.module,spawn:true,clearance:boss?{width:factory?9:11,height:factory?16:14,depth:factory?9:11}:{width:3,height:3,depth:3}};q.actorPhase='independent';return q;});
 // Keep source solids immutable. Actors require supported clear birth positions.
 const solid=(x,y,z)=>cells.has(key(x,y,z))&&cells.get(key(x,y,z))[3]!==A;
 for(const s of spawns){if(s.role==='boss'){const radius=Math.floor(s.clearance.width/2);fill([s.x-radius,s.y,s.z-radius],[s.x+radius,s.y+s.clearance.height-1,s.z+radius]);continue;}
  const safe=(x,y,z)=>solid(x,y-1,z)&&!solid(x,y,z)&&!solid(x,y+1,z)&&!solid(x,y+2,z);
  if(!safe(s.x,s.y,s.z)){let found;for(let radius=1;radius<=8&&!found;radius++)for(let dx=-radius;dx<=radius&&!found;dx++)for(let dz=-radius;dz<=radius&&!found;dz++)if(safe(s.x+dx,s.y,s.z+dz))found=[s.x+dx,s.y,s.z+dz];if(!found)throw Error('Unsupported guard '+s.id);[s.x,s.y,s.z]=found;const n=nodes.find(n=>n.id===s.id);[n.x,n.y,n.z]=found;}
  fill([s.x,s.y,s.z],[s.x,s.y+2,s.z]);
 }
 for(const s of spawns){const n=nodes.find(n=>n.id===s.id);n.code=s.code;if(n.entityId)n.entityId=s.code;}
 const reference=JSON.parse(fs.readFileSync(path.join(root,'plan',fs.readdirSync(path.join(root,'plan')).find(n=>fs.existsSync(path.join(root,'plan',n,'config/revision6-spawners.json'))),'config/revision6-spawners.json'),'utf8'));
 const spawnerPlan=[];
 const occupied=(x,y,z)=>nodes.some(n=>n.y===y&&Math.hypot(n.x-x,n.z-z)<(n.role==='guard'?2:2))||spawnerPlan.some(n=>Math.hypot(n.x-x,n.z-z)<5);
 for(let ri=0;ri<rooms.length;ri++){if(factory&&ri===5)continue;const r=rooms[ri],y=r.y+1;
  const count=factory?(ri<3?2:1):(ri<4?2:1);
  for(let slot=0;slot<count;slot++){let picked;
   const bays=[];for(let z=r.z+3;z<r.z+r.d-3;z++)for(let x=r.x+3;x<r.x+r.w-3;x++)bays.push([x,z]);
   // Pick visible aisle/intersection bays, then disperse along the same accessible aisle.
   const center=[r.x+Math.floor(r.w/2),r.z+Math.floor(r.d/2)];
   bays.sort((a,b)=>Math.hypot(a[0]-center[0],a[1]-center[1])-Math.hypot(b[0]-center[0],b[1]-center[1]));
   for(const [x,z]of bays){if(picked)break;
    if(occupied(x,y,z)||!solid(x,y-1,z)||solid(x,y,z)||solid(x,y+1,z))continue;
    for(const dx of [-2,2]){const cx=x+dx,cz=z;
     if(cx<=r.x||cx>=r.x+r.w-1)continue;
     let clear=true;for(let xx=cx-1;xx<=cx+1;xx++)for(let zz=cz;zz<=cz+3;zz++)if(!solid(xx,y-1,zz)||solid(xx,y,zz)||solid(xx,y+1,zz)||solid(xx,y+2,zz))clear=false;
     if(clear){picked=[x,y,z,cx,cz];break;}
    }
    if(picked)break;
   }
   if(!picked)throw Error('No accessible spawner bay '+id+':'+ri+':'+slot);
   const [x,yy,z,cx,cz]=picked,tier=['fragile','stable','runaway'][spawnerPlan.length%3],sample=reference.records.find(n=>n.tier===tier),code=['dr-02','dr-04','dr-08','dr-15','dr-18'][spawnerPlan.length%5];
   const n={id:id+'-spawner-'+spawnerPlan.length,role:'spawner',x,y:yy,z,code,module:r.moduleId,tier,block:sample.block,label:'工位封印刷怪笼',policy:{...sample.policy,batch:{fragile:2,stable:4,runaway:6}[tier],sealAt:{fragile:10,stable:24,runaway:45}[tier],cooldown:12000,interval:200,intervalMin:200,intervalMax:800,range:16},spawnZone:{center:[cx,yy,cz],clearance:{width:3,depth:5,height:3,mode:'ground'}},approach:[cx,yy,cz],runtimeEnabled:true};
   cells.set(key(x,yy,z),[x,yy,z,n.block]);spawnerPlan.push(n);nodes.push({...n});
  }
 }
 const bossActivation={kind:'destroy-all-spawners',spawnerIds:spawnerPlan.map(n=>n.id)};
 const lootPlan7=nodes.filter(n=>n.role==='chest').map(n=>{const q={...n,block:C,tier:n.unlockMode==='combat'?3:n.unlockMode==='story'?2:1,lootPool:['低阶维护耗材','旧金属散件','有限补给']};if(q.unlockMode==='combat'){q.combatModule=q.id.includes('core')?bossModule:q.module;q.lootPool.push('独立奖励池');}if(!['direct','story','combat'].includes(q.unlockMode))throw Error('Unsupported unlock '+q.id);if(!solid(q.x,q.y-1,q.z))throw Error('Floating chest '+q.id);return q;});
 const encounterClusters7=[...new Set(spawns.map(s=>s.module))].map(module=>({id:module,module,members:spawns.filter(s=>s.module===module).map(s=>s.id)}));
 const views=d.viewpoints.map((v,i)=>({...v,id:id+'-view-'+(i+1),eye:point(v.eye),target:point(v.target)}));
 const metadata={id,variant:0,name:d.name,nominal:factory?[120,60,120]:d.size,compactScene:true,combatOnly:true,nativeScene:false,authoredGeometryFinal:true,nodes,rooms,airBoxes,routes,spawns,spawnerPlan,bossActivation,lootPlan7,encounterClusters7,roofKeys:roofs.map(p=>key(...p)),viewpoints:views,playerReviewViews:views,terrain:{...d.terrain,surfaceY:0,underground:factory},story:d.story,signatureFeatures:d.signatureFeatures,triggers:d.triggers,sourceDraft:'plan/prosperity/design-draft/'+(factory?'factory.js':'foundry.js'),contextPolicy:'Native terrain supplies the surface; excluded authored grass/dirt/background strips.'};
 metadata.sourceAuthorFile=metadata.sourceDraft;
 const palette=[],indices=new Map(),runs=[];
 for(const [x,y,z,m]of [...cells.values()].sort((a,b)=>a[1]-b[1]||a[2]-b[2]||a[0]-b[0])){if(!indices.has(m)){indices.set(m,palette.length);palette.push(m);}const k=indices.get(m),last=runs[runs.length-1];if(last&&last[1]===y&&last[2]===z&&last[0]+last[3]===x&&last[4]===k)last[3]++;else runs.push([x,y,z,1,k]);}
 const snapshot={metadata,voxel:{palette,runs}},file=path.join(out,`${id}-v0.js`);fs.writeFileSync(file,`window.REMASTER_PREFAB7_DATA=window.REMASTER_PREFAB7_DATA||{};window.REMASTER_PREFAB7_DATA[${JSON.stringify(id+'-v0')}]=${JSON.stringify(snapshot)};\n`);
 fs.writeFileSync(path.join(out,`geometry-${id}.json`),JSON.stringify({metadata,voxel:snapshot.voxel}));
 const sourceChanges=[];let preservedSolids=0;
 for(const [x,y,z,material]of d.voxels){if(material===A||contextKeys.has(key(x,y,z))||/^minecraft:(grass|dirt)#/.test(material))continue;const after=cells.get(key(x,y-24,z))?.[3];if(after===material)preservedSolids++;else{const memory=nodes.find(n=>n.role==='memory'&&n.x===x&&n.y===y-24&&n.z===z);if(!memory||after!==CONSOLE)throw Error('Unauthorized source geometry change '+key(x,y,z));sourceChanges.push({at:[x,y-24,z],before:material,after,reason:'registered-story-console'});}}
 const report={id,excludedContextCells:excluded,addedExplicitAir:addedAir,explicitAir:[...cells.values()].filter(v=>v[3]===A).length,solidCount:[...cells.values()].filter(v=>v[3]!==A).length,preservedSourceSolids:preservedSolids,authorizedSourceChanges:sourceChanges,unauthorizedSourceChanges:0,roofKeys:roofs.length,airBoxes:airBoxes.length,rooms:rooms.length,chests:lootPlan7.length,unlockCounts:lootPlan7.reduce((a,n)=>(a[n.unlockMode]=(a[n.unlockMode]||0)+1,a),{}),materials:palette,sha256:crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')};reports.push(report);
}
fs.writeFileSync(path.join(out,'geometry-report.json'),JSON.stringify(reports,null,2));console.log(JSON.stringify(reports.map(({materials,...r})=>r)));
