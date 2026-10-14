package com.miaokatze.gtsr.common.critical;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.ForgeDirection;
import com.google.gson.*;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import gregtech.api.metatileentity.BaseMetaTileEntity;
/** Native geometry/matching/state methods with a sparse World; no FML bootstrap or full server. */
public final class CriticalIntegrityContract {
    static sun.misc.Unsafe unsafe;
    static int checks;
    static final Block AIR=new Block(Material.air) {};
    static void check(boolean pass,String name){if(!pass)throw new AssertionError(name);checks++;}
    static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static void set(Object o,String name,Object value)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    static String key(int x,int y,int z){return x+","+y+","+z;}
    @SuppressWarnings("unchecked") static void materials()throws Exception {
        Constructor<CriticalMaterials.MaterialBlock> c=CriticalMaterials.MaterialBlock.class.getDeclaredConstructor(
            String.class,boolean.class,boolean.class,boolean.class,boolean.class,boolean.class);c.setAccessible(true);
        for(Map.Entry<String,JsonElement> entry:CriticalGeometry.readJson("materials.json").entrySet()) {
            JsonObject p=entry.getValue().getAsJsonObject();String id=p.get("id").getAsString();
            for(boolean proxy:new boolean[]{false,true})for(boolean loom:new boolean[]{false,true}) {
                Field f=CriticalMaterials.class.getDeclaredField(loom?proxy?"LOOM_PROXY":"LOOM_STATIC":proxy?"PROXY":"STATIC");
                f.setAccessible(true);Map<String,Block> map=(Map<String,Block>)f.get(null);
                if(!map.containsKey(id))map.put(id,c.newInstance(id,proxy,!p.get("alphaMode").getAsString().equals("opaque"),
                    p.get("animated").getAsBoolean(),p.get("emissionStrength").getAsDouble()>0,loom));
            }
        }
    }
    static SparseWorld world()throws Exception {
        SparseWorld w=(SparseWorld)unsafe.allocateInstance(SparseWorld.class);w.blocks=new HashMap<>();w.tiles=new HashMap<>();
        w.playerEntities=new ArrayList<>();w.loaded=true;
        Field f=World.class.getDeclaredField("perWorldStorage");f.setAccessible(true);f.set(w,new MapStorage(null));
        w.chunks=(IChunkProvider)Proxy.newProxyInstance(IChunkProvider.class.getClassLoader(),new Class<?>[]{IChunkProvider.class},
            (p,m,a)->m.getName().equals("chunkExists")?w.loaded:null);
        return w;
    }
    static CriticalMachineState state(SparseWorld w,int revision,ForgeDirection facing)throws Exception {
        NBTTagCompound n=new NBTTagCompound();n.setInteger("geometryVersion",2);n.setInteger("v2LayoutRevision",revision);
        n.setInteger("structureFacing",facing.ordinal());n.setBoolean("loomController",true);n.setBoolean("loomJournalOwner",true);
        n.setString("structureId","11111111-1111-1111-1111-111111111111");n.setString("ownerUuid","owner");
        n.setString("jobId","paid-partial");n.setInteger("state",2);
        n.setTag("config",new CriticalConfiguration(CriticalTier.T3,CriticalMachineKind.SUN,0,0,0).write());
        CriticalMachineState s=new CriticalMachineState();s.readFromNBT(n);s.worldObj=w;s.xCoord=56;s.yCoord=68;s.zCoord=47;
        geometry(s);set(s,"jobFingerprint",field(s,"modelFingerprint"));return s;
    }
    @SuppressWarnings("unchecked")static List<CriticalGeometry.Voxel> geometry(CriticalMachineState s)throws Exception {
        Method m=CriticalMachineState.class.getDeclaredMethod("allVoxels");m.setAccessible(true);
        return (List<CriticalGeometry.Voxel>)m.invoke(s);
    }
    static void put(CriticalMachineState s,CriticalGeometry.Voxel v,Block b) {
        ((SparseWorld)s.worldObj).blocks.put(key(s.blockWorldX(v),s.blockWorldY(v.y),s.blockWorldZ(v)),b);
    }

    static void fill(CriticalMachineState s)throws Exception {
        SparseWorld w=(SparseWorld)s.worldObj;
        for(CriticalGeometry.Voxel v:geometry(s)) {
            put(s,v,CriticalLoomStructure.isLoomVoxel(v)?CriticalLoomStructure.shell(v):v.block);
            if(CriticalStructures.isController(v)) {
                BaseMetaTileEntity tile=(BaseMetaTileEntity)unsafe.allocateInstance(BaseMetaTileEntity.class);
                MTECriticalMachine m=(MTECriticalMachine)unsafe.allocateInstance(MTECriticalMachine.class);
                set(m,"presetKind",v.z==47?null:s.getKind());
                Field base=gregtech.api.metatileentity.MetaTileEntity.class.getDeclaredField("mBaseMetaTileEntity");base.setAccessible(true);base.set(m,tile);
                tile.setMetaTileEntity(m);
                Field f=BaseMetaTileEntity.class.getDeclaredField("mFacing");f.setAccessible(true);f.set(tile,v.z==47?s.getStructureFacing():s.getStructureFacing().getOpposite());
                tile.setWorldObj(w);tile.xCoord=s.blockWorldX(v);tile.yCoord=s.blockWorldY(v.y);tile.zCoord=s.blockWorldZ(v);
                w.tiles.put(key(tile.xCoord,tile.yCoord,tile.zCoord),tile);
            }
        }
    }
    static CriticalStructureMonitor monitor(CriticalMachineState s)throws Exception{return (CriticalStructureMonitor)field(s,"structureMonitor");}
    static void ticks(CriticalMachineState s,int ticks)throws Exception{
        for(int i=0;i<ticks;i++){s.tickStructureMonitor();check(monitor(s).getLastChecks()<=128,"bounded server tick");}
    }
    static void rate() {
        for(int total:new int[]{1,17,5999,6000,100001,767999}) {
            CriticalStructureMonitor m=new CriticalStructureMonitor();BitSet seen=new BitSet();
            for(int t=1;t<=6000;t++){
                boolean valid=m.tick(total,i->{seen.set(i);return true;});
                check(m.getLastChecks()<=(total+5999)/6000,"ceil rate bound");
                check(valid==(t==6000),"exact 6000-tick first round");
            }
            check(seen.cardinality()==total&&m.getCompletedRounds()==1,"all positions visited");
        }
        CriticalStructureMonitor huge=new CriticalStructureMonitor();
        huge.tick(900000,i->true);check(huge.getLastChecks()==128,"hard ceiling");
    }
    static void run()throws Exception {
        rate();materials();
        for(ForgeDirection facing:new ForgeDirection[]{ForgeDirection.NORTH,ForgeDirection.EAST,ForgeDirection.SOUTH,ForgeDirection.WEST}){
            SparseWorld w=world();CriticalMachineState s=state(w,31,facing);fill(s);
            List<CriticalGeometry.Voxel> all=geometry(s);
            Method match=CriticalMachineState.class.getDeclaredMethod("matches",CriticalGeometry.Voxel.class);match.setAccessible(true);
            for(CriticalGeometry.Voxel v:all)if(!(Boolean)match.invoke(s,v)) {
                TileEntity bad=w.getTileEntity(s.blockWorldX(v),s.blockWorldY(v.y),s.blockWorldZ(v));
                String detail=bad instanceof BaseMetaTileEntity gt?" front="+gt.getFrontFacing()+" mte="+gt.getMetaTileEntity()+" selector="+(gt.getMetaTileEntity() instanceof MTECriticalMachine m?m.isKindSelector():"?"):" tile="+bad;
                throw new AssertionError("fixture mismatch "+v.model+" "+v.x+","+v.y+","+v.z+" "+v.material+detail+" frame="+s.getStructureFacing());
            }

            check(!s.isGeometryValidated(),"reload starts invalid "+facing);
            check(s.isChunkLoadingEnabled(),"old save chunk loading defaults enabled");
            s.setChunkLoadingEnabled(false);NBTTagCompound disabled=new NBTTagCompound();s.writeToNBT(disabled);
            CriticalMachineState disabledReload=new CriticalMachineState();disabledReload.readFromNBT(disabled);check(!disabledReload.isChunkLoadingEnabled(),"chunk loading disabled flag round trips");
            s.setChunkLoadingEnabled(true);
            ticks(s,5999);check(!s.isGeometryValidated(),"partial scan cannot form");
            ticks(s,1);check(s.isGeometryValidated(),"full real geometry forms "+facing);
            check(!s.isWorking(),"idle no recipe");
            set(s,"enabled",false);check(s.isGeometryValidated(),"disabled geometry remains animated");set(s,"enabled",true);
            NBTTagCompound visual=s.getVisualData();CriticalMachineState client=new CriticalMachineState();client.readVisualData(visual);
            check(client.isGeometryValidated(),"visual gate sync");
            NBTTagCompound saved=new NBTTagCompound();s.writeToNBT(saved);saved.setBoolean("valid",true);
            CriticalMachineState reload=new CriticalMachineState();reload.readFromNBT(saved);check(!reload.isGeometryValidated(),"disk valid never trusted");
            Method chunks=CriticalMachineState.class.getDeclaredMethod("loomChunks");chunks.setAccessible(true);
            @SuppressWarnings("unchecked") List<ChunkCoordIntPair> desired=(List<ChunkCoordIntPair>)chunks.invoke(s);
            for(CriticalGeometry.Voxel v:all)check(desired.contains(new ChunkCoordIntPair(s.blockWorldX(v)>>4,s.blockWorldZ(v)>>4)),"full target preload coverage");
            if(facing!=ForgeDirection.NORTH)continue;
            for(boolean manual:new boolean[]{false,true}) {
                CriticalGeometry.Voxel missing=all.stream().filter(v->CriticalLoomStructure.isLoomVoxel(v)==manual&&!CriticalStructures.isController(v)).findFirst().orElseThrow();
                w.blocks.remove(key(s.blockWorldX(missing),s.blockWorldY(missing.y),s.blockWorldZ(missing)));
                int detect=0;while(s.isGeometryValidated()&&detect<6000){s.tickStructureMonitor();detect++;}
                check(!s.isGeometryValidated()&&detect<=6000,"damage detected within one sweep manual="+manual);
                set(s,"cycleDuration",100);set(s,"cycleTicks",12);set(s,"batchEu",123L);
                check(!s.isWorking(),"damaged recipe inactive");
                NBTTagCompound bad=s.getVisualData();check(!bad.getBoolean("valid"),"damage visual gate withdrawn");
                put(s,missing,manual?CriticalLoomStructure.shell(missing):missing.block);
                ticks(s,6000-detect);check(!s.isGeometryValidated(),"repair cannot validate contaminated round");
                ticks(s,5999);check(!s.isGeometryValidated(),"hand repair requires entire clean sweep");
                ticks(s,1);check(s.isGeometryValidated(),"hand repair restores full structure");
                Method user=CriticalMachineState.class.getDeclaredMethod("isUserBuilt",CriticalGeometry.Voxel.class);user.setAccessible(true);
                if(manual)check((Boolean)user.invoke(s,missing),"manual loom excluded from funded placement/repair");
            }
            List<CriticalGeometry.Voxel> ports=CriticalGeometry.optionalWorkPorts(s);check(ports.size()==13,"13 optional ports monitored");
            put(s,ports.get(0),all.get(0).block);ticks(s,6000);check(!s.isGeometryValidated(),"foreign upper work port invalidates");
            w.blocks.remove(key(s.blockWorldX(ports.get(0)),s.blockWorldY(ports.get(0).y),s.blockWorldZ(ports.get(0))));
            ticks(s,6000);check(s.isGeometryValidated(),"upper work port player repair recovers");
            for(CriticalGeometry.Voxel port:ports) {
                BaseMetaTileEntity gt=(BaseMetaTileEntity)unsafe.allocateInstance(BaseMetaTileEntity.class);
                gregtech.api.metatileentity.implementations.MTEHatchInputBus bus=(gregtech.api.metatileentity.implementations.MTEHatchInputBus)unsafe.allocateInstance(gregtech.api.metatileentity.implementations.MTEHatchInputBus.class);
                Field base=gregtech.api.metatileentity.MetaTileEntity.class.getDeclaredField("mBaseMetaTileEntity");base.setAccessible(true);base.set(bus,gt);gt.setMetaTileEntity(bus);
                w.tiles.put(key(s.blockWorldX(port),s.blockWorldY(port.y),s.blockWorldZ(port)),gt);
            }
            ticks(s,6000);check(s.isGeometryValidated(),"all 13 real GT buses accepted by continuous scan");
            NBTTagCompound anchorNBT=new NBTTagCompound();anchorNBT.setTag("visual",s.getVisualData());
            TileEntityCriticalRenderAnchor serverAnchor=new TileEntityCriticalRenderAnchor();serverAnchor.setWorldObj(w);serverAnchor.readFromNBT(anchorNBT);
            check(!serverAnchor.getVisualState().isGeometryValidated(),"persisted server anchor validity discarded");
            check(!serverAnchor.isLoomVisible(),"explicit invalid loom cannot fall through nonempty structure ID");
            TileEntityCriticalRenderAnchor unboundAnchor=new TileEntityCriticalRenderAnchor();unboundAnchor.readFromNBT(anchorNBT);unboundAnchor.setWorldObj(w);
            check(!unboundAnchor.getVisualState().isGeometryValidated()&&!unboundAnchor.isLoomVisible(),"disk NBT before World binding also discards validity");
            w.loaded=false;ticks(s,6000);check(!s.isGeometryValidated()&&w.unloadedReads==0,"unloaded chunks never read or force loaded");
            set(s,"energy",9000L);set(s,"batchId","paused-paid-batch");set(s,"cycleDuration",100);set(s,"cycleTicks",12);set(s,"batchEu",123L);set(s,"pendingGenerated",77L);
            s.updateEntity();check((Long)field(s,"energy")==9000L&&(Integer)field(s,"cycleTicks")==12&&(Long)field(s,"pendingGenerated")==77L,"damaged production update cannot process, consume EU or emit pending output");
            s.onChunkUnload();check(!s.isGeometryValidated(),"unload clears geometry cache");
        }
    }
    public static void main(String[] args)throws Exception{
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(sun.misc.Unsafe)f.get(null);
        run();JsonObject n=new JsonObject();n.addProperty("status","PASS");n.addProperty("checks",checks);
        n.addProperty("coverage","Production State tickStructureMonitor/matches/NBT/visual + 4 directions + real GT controller types; SparseWorld storage only");
        n.addProperty("limitation","No full server startup; no Forge ticket lifecycle, renderer GL, or native recipe energy integration");
        Files.writeString(Path.of(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(n));System.out.println(n);
    }
    static final class SparseWorld extends World {
        Map<String,Block> blocks;Map<String,TileEntity> tiles;IChunkProvider chunks;boolean loaded=true;int unloadedReads;
        SparseWorld(){super(null,"unused",(WorldProvider)null,null,null);}
        void read(){if(!loaded)unloadedReads++;}
        @Override public Block getBlock(int x,int y,int z){read();return blocks.getOrDefault(key(x,y,z),AIR);}
        @Override public int getBlockMetadata(int x,int y,int z){read();return 0;}
        @Override public boolean isAirBlock(int x,int y,int z){read();return !blocks.containsKey(key(x,y,z));}
        @Override public TileEntity getTileEntity(int x,int y,int z){read();return tiles.get(key(x,y,z));}
        @Override public boolean blockExists(int x,int y,int z){return loaded;}
        @Override public IChunkProvider getChunkProvider(){return chunks;}
        @Override public long getTotalWorldTime(){return 0;}
        @Override public void markBlockForUpdate(int x,int y,int z){}
        @Override public void markTileEntityChunkModified(int x,int y,int z,TileEntity t){}
        @Override public void func_147453_f(int x,int y,int z,Block b){}
        @Override protected IChunkProvider createChunkProvider(){throw new UnsupportedOperationException();}
        @Override protected int func_152379_p(){throw new UnsupportedOperationException();}
        @Override public Entity getEntityByID(int id){throw new UnsupportedOperationException();}
    }
}
