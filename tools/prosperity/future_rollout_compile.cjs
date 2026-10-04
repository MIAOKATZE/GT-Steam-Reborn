'use strict';
const fs=require('fs'),path=require('path'),vm=require('vm'),crypto=require('crypto');
const ROOT=path.resolve(__dirname,'../..'),OUT=path.resolve(ROOT,process.argv[2]||'temp/refinement-v72/structure');
fs.mkdirSync(OUT,{recursive:true});
const ctx={console};ctx.window=ctx;vm.createContext(ctx);
for(const file of ['catalog.js','voxel-kit.js','models/medium.js','models/small.js','models/ruins-industrial.js','models/ruins-civic.js','models/city-expansion.js','models/open-forms.js'])
 vm.runInContext(fs.readFileSync(path.join(ROOT,'plan/prosperity/future-design',file),'utf8'),ctx,{filename:file});
if(process.argv.includes('--refine'))vm.runInContext(fs.readFileSync(path.join(ROOT,'plan/prosperity/future-design/models/rollout-placement.js'),'utf8'),ctx,{filename:'rollout-placement.js'});
const verify=require('./future_design_verify.cjs').verify, reports=[];
for(const entry of ctx.PROSPERITY_FUTURE_DESIGN.structures){
 if(entry.id==='abandoned_city')continue;
 const model=ctx.PROSPERITY_FUTURE_MODELS[entry.id](),result=verify(model);
 if(entry.implemented===true){model.designOnly=false;model.implemented=true;model.productionIdentity=entry.id;model.designStatus='implemented-source-exact';for(const n of model.nodes)if(n.code){n.designOnly=false;n.implemented=true;}}
 fs.writeFileSync(path.join(OUT,entry.id+'.json'),JSON.stringify({entry,model}));
 reports.push({id:entry.id,category:entry.category,size:entry.size,modelSource:model.source,roles:result.counts.nodeRoles,ok:result.ok,errors:result.errors,
  codes:[...new Set(model.nodes.filter(n=>n.code).map(n=>n.code))],chests:model.nodes.filter(n=>n.role==='chest').map(({id,x,y,z,unlockMode,type,tier,storyNode,combatModule,storyRelic})=>({id,x,y,z,unlockMode,type,tier,storyNode,combatModule,storyRelic}))});
}
fs.writeFileSync(path.join(OUT,'model-baseline.json'),JSON.stringify({models:reports.length,passed:reports.every(r=>r.ok),reports},null,2));
console.log(JSON.stringify({models:reports.length,passed:reports.every(r=>r.ok),failures:reports.filter(r=>!r.ok).map(r=>[r.id,r.errors]),categories:Object.fromEntries([...new Set(reports.map(r=>r.category))].map(c=>[c,reports.filter(r=>r.category===c).length]))}));
