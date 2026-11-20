// SPDX-License-Identifier: AGPL-3.0-only
// Compile the checked-in preview sources to compact, registry-name keyed sparse block lists.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { gzipSync } = require('node:zlib');
const root = path.resolve(__dirname, '..', '..');
const preview = path.join(root, 'plan', '矿物加工集成中心工程');
const output = path.join(root, 'src', 'main', 'resources', 'assets', 'gtsr', 'orecenter');
global.window = global;
for (const name of ['block-palette', 'factory-base', 'factory-modules', 'voxel-model'])
    vm.runInThisContext(fs.readFileSync(path.join(preview, name + '.js'), 'utf8'), { filename:name });
fs.mkdirSync(output, {recursive:true});
const excluded = (x,y,z) => y === 2 && x === 0 && z === -19;
const key = (x,y,z) => (x+256)*131072+(z+256)*256+y;
function sparse(model) {
    const { blocks, width, depth, minX, minZ, palette } = model;
    const result = new Map();
    for (let i=0;i<blocks.length;i++) {
        const material = palette[blocks[i]];
        if (!blocks[i] || material.marking || material.previewOnly) continue;
        const x=i%width+minX, y=Math.floor(i/(width*depth)), z=Math.floor(i/width)%depth+minZ;
        if (!excluded(x,y,z)) result.set(key(x,y,z), [x,y,z,material.block,material.meta]);
    }
    return result;
}
function write(name, cells) {
    const points=[...cells.values()], palette=new Map();
    for (const [,,,block,meta] of points) palette.set(block+':'+meta, [block,meta]);
    const indices=new Map([...palette.keys()].map((k,i)=>[k,i]));
    const header = Buffer.alloc(6);
    header.writeUInt16BE(palette.size);
    header.writeUInt32BE(points.length,2);
    const labels = [...palette.values()].map(([b,m])=>{
        const name=Buffer.from(b,'utf8'), out=Buffer.alloc(2+name.length);
        out[0]=name.length;name.copy(out,1);out[out.length-1]=m;return out;
    });
    const body=Buffer.alloc(points.length*6);
    for(const [i,[x,y,z,b,m]] of points.entries()){
        body.writeInt16BE(x,i*6);
        body.writeUInt8(y,i*6+2);
        body.writeInt16BE(z,i*6+3);
        body.writeUInt8(indices.get(b+':'+m),i*6+5);
    }
    fs.writeFileSync(path.join(output,name+'.gz'), gzipSync(Buffer.concat([header,...labels,body]),{level:9}));
}
const realOuter=buildOreFactoryOuter;
global.buildOreFactoryOuter=()=>{};
const tower=Array.from({length:4},(_,tier)=>sparse(makeOreVoxelModel(tier,[],1)));
global.buildOreFactoryOuter=realOuter;
for (const [tier,cells] of tower.entries()) write('tower-service-'+tier,cells);
const isolated=Array.from({length:6},(_,sector)=>sparse(makeOreVoxelModel(0,[],1<<sector)));
for(const cells of isolated) for(const k of tower[0].keys()) cells.delete(k);
const common=isolated.map(cells=>new Map(cells));
for(let mask=1;mask<=63;mask++) {
    const full=sparse(makeOreVoxelModel(0,[],mask));
    for(const k of tower[0].keys()) full.delete(k);
    if (mask===63) write('outer-full',full);
    for(const [sector,cells] of common.entries()) if ((mask & (1<<sector))!==0) for(const [k,v] of cells) {
        const f=full.get(k);
        if (!f || f[3]!==v[3] || f[4]!==v[4]) cells.delete(k);
    }
}
for(const [i,cells] of isolated.entries()) write('outer-'+i,cells);
for(const [i,cells] of common.entries()) write('sector-'+i,cells);

const visual = makeOreVoxelModel(0, [], 63);
const crown = visual.motionGroups.find(group => group.key === 'crown-rotor');
const members = crown.cells.map(([x, y, z]) => {
    const material = visual.palette[visual.blocks[visual.index(x, y, z)]].key;
    if (!['gear', 'shell', 'pipe'].includes(material)) throw Error('Unexpected crown material: '+material);
    return { x, y:y-2, z:z+19, material:material==='pipe'?'shaft':material };
});
const centerMotion = { schema:1, id:'ore_center_crown', bounds:[-14,54,5,15,67,34], groups:[{
    id:crown.key, kind:'rotate_y', axis:'y', travel:crown.travel,
    periodMs:Math.round(2*Math.PI*1000/crown.travel),
    pivot:[crown.pivot[0]-.5,crown.pivot[1]-2.5,crown.pivot[2]+18.5], members,
}] };
fs.writeFileSync(path.join(output, 'crown-motion.json'), JSON.stringify(centerMotion)+'\n');

const boost = ['speed','parallel','yield','secondary'];
const process = ['crusher','washer','sifter','centrifuge','electrolyzer','dryer','furnace','reactor','separator','extractor','thermalCentrifuge','smelting'];
const palette = new Map(oreBlockPalette(0).map(p=>[p.key,p]));
for (const [slotIndex,slot] of oreFactorySlots.entries()) {
    // Store only canonical templates by slot offset. Apply the actual slot orientation and position in Java.
    if (slotIndex>5) break;
    const modules=slot.offset===0?boost:slot.offset<3?process:['logistics'];
    for(const id of modules)for(let grade=1;grade<=3;grade++){
        const point=new Map();
        const set=(x,y,z,k)=>{
            if (k === 'air') {
                point.delete(key(x,y+64,z));
                return;
            }
            const m=palette.get(k);
            if (!m) throw Error(k);
            point.set(key(x,y+64,z),[x,y+64,z,m.block,m.meta]);
        };
        const box=(x0,y0,z0,x1,y1,z1,k)=>{
            for(let y=y0;y<y1;y++)for(let z=z0;z<z1;z++)for(let x=x0;x<x1;x++)set(x,y,z,k);
        };
        buildOreFactoryModule({set,box},{x:0,y:0,z:0,turn:0,offset:slot.offset},id,grade);
        write('module-'+slot.offset+'-'+id.toLowerCase()+'-'+grade,point);
    }
}
console.log('塔:',tower.map(a=>a.size),'各扇壳:',isolated.map(a=>a.size),'各扇共通:',common.map(a=>a.size));
