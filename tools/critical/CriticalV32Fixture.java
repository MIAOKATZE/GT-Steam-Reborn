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
public final class CriticalV32Fixture {
    static sun.misc.Unsafe unsafe;
    static int checks;
    static final Block AIR=new Block(Material.air) {};
    static void check(boolean pass,String name){if(!pass)throw new AssertionError(name);checks++;}
    static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static void set(Object o,String name,Object value)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    static String key(int x,int y,int z){return x+","+y+","+z;}
    @SuppressWarnings("unchecked") static void materials()throws Exception {
        Constructor<CriticalMaterials.MaterialBlock> c=CriticalMaterials.MaterialBlock.class.getDeclaredConstructor(
            String.class,boolean.class,boolean.class,boolean.class);c.setAccessible(true);
        for(Map.Entry<String,JsonElement> entry:CriticalGeometry.readJson("materials.json").entrySet()) {
            JsonObject p=entry.getValue().getAsJsonObject();String id=p.get("id").getAsString();
            Field f=CriticalMaterials.class.getDeclaredField("MATERIALS");
            f.setAccessible(true);Map<String,Block> map=(Map<String,Block>)f.get(null);
            if(!map.containsKey(id))map.put(id,c.newInstance(id,!p.get("alphaMode").getAsString().equals("opaque"),
                p.get("animated").getAsBoolean(),p.get("emissionStrength").getAsDouble()>0));
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
    static final class SparseWorld extends World {
        Map<String,Block> blocks;Map<String,TileEntity> tiles;IChunkProvider chunks;boolean loaded=true;int unloadedReads;
        SparseWorld(){super(null,"unused",(WorldProvider)null,null,null);}
        void read(){if(!loaded)unloadedReads++;}
        @Override public boolean setBlock(int x,int y,int z,Block block,int meta,int flags){blocks.put(key(x,y,z),block);return true;}
        @Override public boolean setBlockToAir(int x,int y,int z){blocks.remove(key(x,y,z));tiles.remove(key(x,y,z));return true;}
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
