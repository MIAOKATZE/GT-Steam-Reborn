'use strict';
const fs=require('fs'),path=require('path'),vm=require('vm');
const root=path.resolve(__dirname,'../..'),audit=JSON.parse(fs.readFileSync(path.join(root,'temp/stair-route-audit.json'))),fixtures=[],notes=[];
const floor=mat=>/^gtsr:ruins_(?:slag_masonry|sootstone_tiles|mossroot_paving|verdigris_pressed_brick|rootbound_brick|cast_iron_pillar|oath_inscription|rust_riveted_plate|furnace_firebrick)(?:_stairs)?#/.test(mat||'')||/^gtsr:ruins_rust_floor_grate#/.test(mat||'');
for(const entry of audit.rows){
 const indices=entry.id==='subsided_factory'?[2,6,11,16,21]:entry.id==='city_archive_cache'?[1]:entry.id==='city_watch_tower'&&entry.variant>0?[0]:[];
 if(!indices.length)continue;
 const w={window:{}};vm.createContext(w);vm.runInContext(fs.readFileSync(path.join(root,entry.file),'utf8'),w);const d=Object.values(w.window.REMASTER_PREFAB7_DATA)[0],m=d.metadata,index=new Map();
 for(const r of d.voxel.runs){const k=r[1]+','+r[2];if(!index.has(k))index.set(k,[]);index.get(k).push(r);}
 const original=(x,y,z)=>{for(const r of index.get(y+','+z)||[])if(x>=r[0]&&x<r[0]+r[3])return d.voxel.palette[r[4]];return null;};
 for(const state of ['closed','open']){
  const changes=new Map();if(state==='open')for(const s of m.shapeMechanisms||[])for(const q of s.delta||[])changes.set(q.at.join(','),q.open??null);
  const get=(x,y,z)=>changes.has([x,y,z].join(','))?changes.get([x,y,z].join(',')):original(x,y,z);
  for(const n of indices){const raw=m.routes[n],points=[];
   for(let j=1;j<raw.length;j++){const a=raw[j-1],b=raw[j],count=Math.max(Math.abs(a[0]-b[0]),Math.abs(a[2]-b[2]),1);for(let i=j===1?0:1;i<=count;i++){
    const p=a.map((v,k)=>Math.round(v+(b[k]-v)*i/count)),found=[];for(let dy=-6;dy<=6;dy++){let q=[p[0],p[1]+dy,p[2]];if(floor(get(...q))&&[1,2].every(h=>!get(q[0],q[1]+h,q[2])))found.push(q);}
    found.sort((x,y)=>Math.abs(x[1]-p[1])-Math.abs(y[1]-p[1]));const selected=found[0]||p;if(!found.length)notes.push({id:m.id,variant:m.variant,state,route:n,at:p,noClearFloor:true});if(JSON.stringify(selected)!==JSON.stringify(points.at(-1)))points.push(selected);
   }}
   const cells=[],seen=new Set(),half=4;for(const p of points)for(let x=p[0]-half;x<=p[0]+half;x++)for(let z=p[2]-half;z<=p[2]+half;z++)for(let y=p[1]-8;y<=p[1]+8;y++){const k=[x,y,z].join(',');if(seen.has(k))continue;seen.add(k);const mat=get(x,y,z);if(mat)cells.push([x,y,z,mat]);}
   fixtures.push({kind:'functional-route-proof',id:m.id,variant:m.variant,route:'routes:'+n+':'+state,width:5,points,cells,reason:'complete original route on actual authored floor; state '+state});
  }
 }
}
fs.writeFileSync(path.join(root,'temp/stair-functional-fixtures.json'),JSON.stringify(fixtures));fs.writeFileSync(path.join(root,'temp/stair-functional-notes.json'),JSON.stringify(notes,null,2));console.log(JSON.stringify({routes:fixtures.length,notes:notes.length}));
