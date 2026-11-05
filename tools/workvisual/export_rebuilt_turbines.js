/* Deterministically convert the approved full-cube preview into packaged StructureLib fixtures. */
'use strict';
const fs = require('fs'), vm = require('vm'), path = require('path');
const source = process.argv.slice(2).find(value=>!value.startsWith('--')) || 'plan/临时计划/20261103-动力阵列与巨型轮机重构预览';
const target = 'src/main/resources/assets/gtsr/machinevisual/motions.json';
const context = {window:{},document:{getElementById(){return null;}}};
vm.createContext(context);
vm.runInContext(fs.readFileSync(path.join(source,'mini-turbine.js'),'utf8'),context);
const script=fs.readFileSync(path.join(source,'preview.js'),'utf8');
vm.runInContext(script.slice(0,script.indexOf('  const canvas='))+'window.EXPORT={buildArray,buildTurbine,makeModel,state};})();',context);
const api=context.window.EXPORT;
function compile(model,id){
  let blocks=model.blocks.filter(b=>b.category!==5).map(b=>({...b}));
  const controller=[...model.controller.position];
  if(id==='kinetic_processing_array'){
    // Approved follow-up: R10 [-3,2,0] moves one block toward viewer-left, to [-2,2,0].
    // Accept the historical preview and the synchronized follow-up preview at this same final location.
    blocks.find(b=>b.category===10).material='steel';
    blocks.find(b=>b.category===10).category=1;
    controller[0]=-2;
    const cell=blocks.find(b=>b.x===controller[0]&&b.y===controller[1]&&b.z===controller[2]);
    if(cell)Object.assign(cell,{material:'controllerArray',category:10});
    else blocks.push({x:controller[0],y:controller[1],z:controller[2],material:'controllerArray',category:10,g:0});
  }
  // Preview -Z front uses +X on the viewer's left; StructureLib +A is viewer-right.
  blocks=blocks.map(b=>({...b,x:-b.x}));controller[0]=-controller[0];
  const min=[0,1,2].map(i=>Math.min(...blocks.map(b=>[b.x,b.y,b.z][i])));
  const max=[0,1,2].map(i=>Math.max(...blocks.map(b=>[b.x,b.y,b.z][i])));
  const offset=[controller[0]-min[0],max[1]-controller[1],controller[2]-min[2]];
  const cells=new Map();
  function token(b){
    if(b.category===10)return '~';
    if(id==='heavy_steam_turbine'&&b.y===0)return 'F';
    if(b.category===3)return id==='heavy_steam_turbine'?'D':'U';
    if(b.material==='glass')return 'G';
    if(/frame/i.test(b.material))return 'E';
    if(/gearbox/i.test(b.material))return 'D';
    if(/pipe|shaft/i.test(b.material))return b.g?'S':'C';
    if(b.g)return 'R';
    // KPA/Heavy hatches use accessible ordinary casing; Mega hatches use tier casing U.
    const hatch=id==='kinetic_processing_array'?b.y<=3&&b.z>=0:
      id.startsWith('mega_')?false:
      b.y>=1&&b.y<=3&&b.x<=-3&&b.z===-2;
    return hatch?'B':'H';
  }
  for(const b of blocks){const k=[b.x,b.y,b.z].join(',');if(cells.has(k))throw Error('duplicate '+id+' '+k);cells.set(k,token(b));}
  const groups=model.groups.flatMap((g,index)=>{
    if(index===0)return [];
    const members=blocks.filter(b=>b.g===index).map(b=>({x:b.x-controller[0],y:b.y-controller[1],z:b.z-controller[2],material:/pipe|shaft/i.test(b.material)?'shaft':'shell'}));
    const pivot=[-g.pivot[0]-controller[0],g.pivot[1]-controller[1],g.pivot[2]-controller[2]];
    return [{id:id+'_group_'+index,kind:g.kind==='piston'?'translate':'rotate_z',axis:g.kind==='piston'?'y':'z',travel:g.kind==='piston'?-1:0,periodMs:g.kind==='piston'?3000:4000,pivot,members,
      ...(g.kind==='piston'?{keyframes:[[0,0],[1500,0],[1740,-1],[1980,-1],[3000,0]]}:{})}];
  });
  // Required air is the exact conservative continuous sweep, not the whole exterior bounding box.
  let air=0;
  function addAir(x,y,z){const k=[x,y,z].join(',');if(!cells.has(k)){cells.set(k,'A');air++;}}
  for(const [x,y,z] of model.requiredAir||[]){if(cells.has([-x,y,z].join(',')))throw Error('Required bore occupied '+id);addAir(-x,y,z);}
  for(const g of groups){
    if(g.kind==='translate'){
      for(const c of g.members)for(let dy=-1;dy<=0;dy++)addAir(c.x+controller[0],c.y+controller[1]+dy,c.z+controller[2]);
    }else{
      const slices=new Map();
      for(const c of g.members){const r2=(Math.abs(c.x-g.pivot[0])+.5)**2+(Math.abs(c.y-g.pivot[1])+.5)**2;slices.set(c.z,Math.max(slices.get(c.z)||0,r2));}
      for(const [z,r2] of slices){const r=Math.sqrt(r2),[px,py]=g.pivot;
        for(let x=Math.floor(px-r+.5+1e-7);x<=Math.ceil(px+r+.5-1e-7)-1;x++)
          for(let y=Math.floor(py-r+.5+1e-7);y<=Math.ceil(py+r+.5-1e-7)-1;y++){
            const dx=Math.max(0,Math.abs(x-px)-.5),dy=Math.max(0,Math.abs(y-py)-.5);
            if(dx*dx+dy*dy>=r2-1e-7)continue;
            const k=[x+controller[0],y+controller[1],z+controller[2]].join(',');
            if(cells.has(k)&&!g.members.some(c=>c.x===x&&c.y===y&&c.z===z))throw Error('sweep conflict '+id+' '+k);
            addAir(x+controller[0],y+controller[1],z+controller[2]);
          }
      }
    }
  }
  const shapeLayers=[];
  for(let y=max[1];y>=min[1];y--){const rows=[];for(let z=min[2];z<=max[2];z++){let row='';for(let x=min[0];x<=max[0];x++)row+=cells.get([x,y,z].join(','))||' ';rows.push(row);}shapeLayers.push(rows);}
  const steel=[['GregTechAPI.sBlockCasings2',0]], bronzeSteel=[['GregTechAPI.sBlockCasings1',10],...steel];
  const shell=id==='heavy_steam_turbine'?bronzeSteel:[...steel,['GregTechAPI.sBlockCasings4',2],['GregTechAPI.sBlockCasings4',0]];
  const binding=(role,blockChoices)=>({role,blockChoices});
  const tokenMaterials={
    B:binding('casing',shell),H:binding('casing',shell),R:binding('casing',shell),
    C:binding('pipe',id==='heavy_steam_turbine'?[['GregTechAPI.sBlockCasings2',12],['GregTechAPI.sBlockCasings2',13]]:[13,14,15].map(meta=>['GregTechAPI.sBlockCasings2',meta])),
    S:binding('pipe',id==='heavy_steam_turbine'?[['GregTechAPI.sBlockCasings2',12],['GregTechAPI.sBlockCasings2',13]]:[13,14,15].map(meta=>['GregTechAPI.sBlockCasings2',meta])),
    D:binding('gearbox',id==='heavy_steam_turbine'?[2,3].map(meta=>['GregTechAPI.sBlockCasings2',meta]):[['GregTechAPI.sBlockCasings2',3],['GregTechAPI.sBlockCasings2',4],['BlocksGTSR.tungstenSteelGearbox',0]]),
    E:binding('frame',(id==='heavy_steam_turbine'?['Bronze','Steel']:['Steel','Titanium','TungstenSteel']).map(material=>['GregTechAPI.sBlockFrames','Materials.'+material+'.mMetaItemSubID'])),
    G:binding('reinforced_glass',id==='heavy_steam_turbine'?[['Blocks.glass',0]]:[['Blocks.glass',0],['GregTechAPI.sBlockGlass1',10],...Array.from({length:10},(_,meta)=>['BorosilicateGlass.getGlassBlock()',meta])])
  };
  if(id==='heavy_steam_turbine')tokenMaterials.F=binding('casing',bronzeSteel);
  else tokenMaterials.U=binding('voltage_casing',[...Array.from({length:9},(_,i)=>['GregTechAPI.sBlockCasings1',i+1]),...[10,11,12].map(meta=>['GregTechAPI.sBlockCasingsNH',meta])]);
  const definition={id,controller:id==='heavy_steam_turbine'?'HeavySteamTurbine':id==='kinetic_processing_array'?'KineticProcessingArray':'MegaSteamTurbineArray',schema:1,shapeLayers,offset,tokenMaterials,groups,skipTokens:[' '],requiredAirTokens:['A']};
  console.log(JSON.stringify({id,offset,size:max.map((v,i)=>v-min[i]+1),blocks:blocks.length,upgrades:blocks.filter(b=>b.category===3).length,glass:blocks.filter(b=>b.material==='glass').length,moving:groups.reduce((s,g)=>s+g.members.length,0),requiredAir:air}));
  return definition;
}
const additions=[compile(api.buildArray(),'kinetic_processing_array')];
for(let k=0;k<=4;k++){api.state.stacks=k;additions.push(compile(api.buildTurbine(),'mega_steam_turbine_'+k));}
additions.push(compile(context.window.BUILD_MINI_TURBINE(api.makeModel,'bronze'),'heavy_steam_turbine'));
const root=JSON.parse(fs.readFileSync(target,'utf8'));
root.source='Approved r16 2026-11-04: KPA nine-cube tier anvil and three casing feet; Mega convex 280-glass nose with solid rear shoulders, short 7x7 cut-corner glass bands with opaque collars and 5x5 clear bores, three connected 24-high portals and grounded central-passage frame posts; Mega 162-cube tier plinth and U hatches, KPA/Heavy B hatches; unchanged dynamic groups, unified material frames and GTSR tungstensteel gearbox';
root.definitions=root.definitions.filter(d=>!additions.some(a=>a.id===d.id)).concat(additions);
const output=JSON.stringify(root,null,2)+'\n';
if(process.argv.includes('--check')){
  if(fs.readFileSync(target,'utf8')!==output)throw Error('Packaged fixtures differ from approved full-cube models');
}else fs.writeFileSync(target,output);
