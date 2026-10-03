'use strict';
// Serial source application. Geometry helpers own their bounded repair logic.
const fs=require('fs'),path=require('path'),vm=require('vm'),crypto=require('crypto');
const root=path.resolve(__dirname,'../..'),audit=JSON.parse(fs.readFileSync(path.join(root,'tools/prosperity/profiles/stair-route-baseline.json'),'utf8'));
const mode=process.argv[2]||'dry',kind=process.argv[3]||'exceptions',apply=mode==='apply';
const helper=require('./'+(kind==='watch'?'refine_watch_routes.cjs':'stair_exception_routes.cjs'));
const invoke=kind==='watch'?helper.applyWatchRoutes:helper.applyExceptions;
if(typeof invoke!=='function')throw Error('Missing helper entry point '+kind);
const rows=[];
for(const entry of audit.rows){
 if(entry.id==='forgotten_lake_court')continue;
 const target=kind==='watch'?['outpost_outpost_watch_post','ruin_ruin_watch_buried'].includes(entry.id):['fallen_foundry','subsided_factory','trench_infirmary','valve_yard','city_archive_cache','city_dome_hall','mirror_barracks','city_watch_tower'].includes(entry.id);
 if(!target)continue;
 const file=path.join(root,entry.file),before=fs.readFileSync(file),w={window:{}};vm.createContext(w);vm.runInContext(before.toString('utf8'),w);
 const d=Object.values(w.window.REMASTER_PREFAB7_DATA)[0],r=invoke(path.basename(file),d,{dryRun:!apply});
 if(r.blocked?.length)throw Error(entry.id+'/v'+entry.variant+' has '+r.blocked.length+' protected repair conflicts');
 if(apply&&r.changes?.length){if(!r.data)throw Error('Helper must return updated data');const m=r.data.metadata;fs.writeFileSync(file,'window.REMASTER_PREFAB7_DATA=window.REMASTER_PREFAB7_DATA||{};window.REMASTER_PREFAB7_DATA['+JSON.stringify(m.id+'-v'+m.variant)+']='+JSON.stringify(r.data)+';');}
 const sha=b=>crypto.createHash('sha256').update(b).digest('hex');rows.push({id:entry.id,variant:entry.variant,file:entry.file,beforeSHA256:sha(before),afterSHA256:sha(fs.readFileSync(file)),changes:r.changes||[],routes:r.routes||[],notes:r.notes||[]});
}
const phase=process.argv[4]?'-'+process.argv[4].replace(/[^A-Za-z0-9_-]/g,''):'';const out=path.join(root,'plan/implementation/20261002212217/stair-'+kind+'-'+mode+phase+'.json');fs.writeFileSync(out,JSON.stringify({schema:1,apply,kind,rows},null,2));console.log(JSON.stringify({apply,kind,variants:rows.length,changes:rows.reduce((n,r)=>n+r.changes.length,0),report:path.relative(root,out)}));
