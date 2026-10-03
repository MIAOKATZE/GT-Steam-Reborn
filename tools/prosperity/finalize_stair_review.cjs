'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const root=path.resolve(__dirname,'../..'),out=path.join(root,'plan/implementation/20261002212217');
const read=p=>JSON.parse(fs.readFileSync(path.join(root,p),'utf8'));
const matrix=read('temp/stair-walk-collision.json');
if(matrix.fixtures!==4048||matrix.activeBlocked||matrix.inactiveMetadata||matrix.unmappedNonfloorCells||matrix.replacementRoutesFailed)throw Error('Every original rise and replacement route must have real collision proof before publication');
const authorCheck=require('child_process').spawnSync(process.execPath,['tools/prosperity/refine_structure_design.cjs','verify'],{cwd:root,encoding:'utf8'});
if(authorCheck.status!==0)throw Error('Author invariant verification failed: '+authorCheck.stdout+authorCheck.stderr);
const author=JSON.parse(authorCheck.stdout.trim());if(!author.pass||author.structures!==90||author.variants!==268)throw Error('Unexpected author source scope');
const report=read('plan/implementation/20261002212217/stair-review.json'),baseline=read('tools/prosperity/profiles/stair-route-baseline.json');
if(!report.initialConservativeCandidates){report.initialConservativeCandidates={unresolved:report.totals.unresolved,excluded:report.totals.excluded};for(const row of report.rows){row.initialCandidates={unresolved:row.unresolved,excluded:row.excluded};delete row.unresolved;delete row.excluded;}}
report.schema=2;report.totals.initialCandidateUnresolved=report.initialConservativeCandidates.unresolved;report.totals.unresolved=0;
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(path.join(root,p))).digest('hex');
const inputs=read('temp/stair-walk-fixtures.inputs.json');if(hash('temp/stair-walk-fixtures.json')!==inputs.fixtureSHA256||matrix.fixtureSHA256!==inputs.fixtureSHA256)throw Error('Fixture bytes changed after source capture or Minecraft proof');
for(const input of inputs.sourceSnapshots)if(hash(input.file)!==input.SHA256)throw Error('Source changed since collision fixture capture: '+input.file);
report.sourceSnapshots=baseline.rows.map(r=>({id:r.id,variant:r.variant,file:r.file,SHA256:hash(r.file)}));
report.verification={author,matrix:{...matrix,exceptions:undefined},perStructure:[],reproducer:['node tools/prosperity/stair_walk_matrix.cjs','python tools/prosperity/stair_collision_verify.py --matrix'],policy:'No inactive route exemption. Replacement routes require continuous Entity.moveEntity in both directions; normalized surfaces require the same physical endpoints to walk in both directions.'};
for(const row of baseline.rows){const evidence=matrix.exceptions.filter(e=>e.id===row.id&&e.variant===row.variant);report.verification.perStructure.push({id:row.id,variant:row.variant,originalRises:row.routeMatrix.reduce((n,r)=>n+r.transitions.length,0),actualFloorOffsets:evidence.filter(e=>e.status==='actual-authored-floor-height-differs-from-metadata'),replacementProofs:evidence.filter(e=>e.status==='replacement-route-continuous-physical-proof')});}
report.boundedRepairs=fs.readdirSync(out).filter(f=>/^stair-(watch|exceptions)-apply(?:-[A-Za-z0-9_-]+)?\.json$/.test(f)).map(file=>({file,SHA256:hash('plan/implementation/20261002212217/'+file)}));
report.interactionProofRepair={file:'stair-interaction-proofs-apply.json',SHA256:hash('plan/implementation/20261002212217/stair-interaction-proofs-apply.json'),geometryChanges:0};
report.royal=[];for(const [height,layout] of [[133,3],[139,3],[142,3],[146,3],[139,1],[139,2]]){const suffix=layout===3&&height===139?'':'-layout'+layout+'-h'+height;const p='temp/royal-stair-collision'+suffix+'.json',collision=read(p);if(collision.blocked||collision.branchBlocked||collision.connectorBlocked||collision.completeBidirectionalRoutesFailed)throw Error('Royal actual generator collision failure '+suffix);report.royal.push({height,layout,...collision});}
const geometry=read('temp/royal-stair-geometry.json');
if(!geometry.source||hash(geometry.source)!==geometry.sourceSHA256)throw Error('Royal production source changed after geometry capture');
for(const [height,layout] of [[133,3],[139,3],[142,3],[146,3],[139,1],[139,2]]){const suffix=layout===3&&height===139?'':'-layout'+layout+'-h'+height,g=read('temp/royal-stair-geometry'+suffix+'.json');if(g.sourceSHA256!==geometry.sourceSHA256)throw Error('Royal case geometry belongs to an older source '+suffix);}
report.royalGeometry={file:'temp/royal-stair-geometry.json',source:geometry.source,sourceSHA256:geometry.sourceSHA256,actualBlockCount:geometry.count,stairCount:geometry.stairs.length,walkingRoutes:geometry.walkRoutes.length};
fs.writeFileSync(path.join(out,'stair-review.json'),JSON.stringify(report,null,2));
fs.writeFileSync(path.join(out,'stair-review.md'),`# 楼梯实施与实体碰撞回执\n\n90个结构ID、268变体，4048处原始升降均有真实物理分类；矩阵无未证、阻挡或未知方块。\n\n${matrix.walked}处原高度直接通行，${matrix.normalizedActualFloorWalked}处按实际作者楼面高度通行，${matrix.replacementOldRiseProven}处旧升降由${matrix.replacementRoutesPassed}条完整替代通路证明。所有采信楼面和替代通路均以stepHeight=0.5的Minecraft实体双向行走。\n\n早期保守逐格候选保留在initialCandidates，供坐标核查，不作为路线豁免。有限源修复的before/after证据另存对应回执。王庭旧layout1/2与V3高度133/139/142/146均通过真实生成器碰撞检查及完整通路核验。\n\n复现：node tools/prosperity/stair_walk_matrix.cjs，再运行python tools/prosperity/stair_collision_verify.py --matrix。王庭：python tools/prosperity/stair_collision_verify.py --royal [--height H] [--layout L]。任何真实证明失败均非零退出。\n`);
console.log(JSON.stringify({published:true,fixtures:matrix.fixtures,blocked:0,variants:report.sourceSnapshots.length}));
