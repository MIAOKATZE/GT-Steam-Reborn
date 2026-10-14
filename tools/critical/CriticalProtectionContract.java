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
import net.minecraftforge.event.world.ExplosionEvent;

import com.google.gson.*;

/** Actual production Block getters/callbacks and Forge events; sparse World avoids FML bootstrap. */
public final class CriticalProtectionContract {
    static sun.misc.Unsafe unsafe;
    static JsonArray checks = new JsonArray();
    static final Block AIR = new Block(Material.air) {};
    static void check(boolean pass, String name) {
        JsonObject row=new JsonObject();row.addProperty("name",name);row.addProperty("pass",pass);checks.add(row);
        if(!pass) throw new AssertionError(name);
    }
    static Object field(Object o,String name)throws Exception {
        Field f=(o instanceof Class?(Class<?>)o:o.getClass()).getDeclaredField(name);f.setAccessible(true);return f.get(o instanceof Class?null:o);
    }
    static void set(Object o,String name,Object value)throws Exception {
        Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);
    }
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
        w.playerEntities=new ArrayList<>();w.rand=new Random(31);
        Field f=World.class.getDeclaredField("perWorldStorage");f.setAccessible(true);f.set(w,new MapStorage(null));
        w.chunks=(IChunkProvider)Proxy.newProxyInstance(IChunkProvider.class.getClassLoader(),new Class<?>[]{IChunkProvider.class},
            (p,m,a)->m.getName().equals("chunkExists")?!(w.ownerUnloaded&&(Integer)a[0]==w.ownerX>>4&&(Integer)a[1]==w.ownerZ>>4):null);
        return w;
    }
    static CriticalMachineState state(SparseWorld w,int revision,ForgeDirection facing)throws Exception {
        NBTTagCompound n=new NBTTagCompound();n.setInteger("geometryVersion",2);n.setInteger("v2LayoutRevision",revision);
        n.setInteger("structureFacing",facing.ordinal());n.setBoolean("loomController",true);n.setBoolean("loomJournalOwner",true);
        n.setString("structureId","11111111-1111-1111-1111-111111111111");n.setString("ownerUuid","owner");
        n.setString("jobId","paid-partial");n.setInteger("state",1);
        n.setTag("config",new CriticalConfiguration(CriticalTier.T3,CriticalMachineKind.SUN,0,0,0).write());
        CriticalMachineState s=new CriticalMachineState();s.readFromNBT(n);s.worldObj=w;s.xCoord=56;s.yCoord=68;s.zCoord=47;
        w.ownerX=s.xCoord;w.ownerY=s.yCoord;w.ownerZ=s.zCoord;
        geometry(s);set(s,"jobFingerprint",field(s,"modelFingerprint"));return s;
    }
    @SuppressWarnings("unchecked")static List<CriticalGeometry.Voxel> geometry(CriticalMachineState s)throws Exception {
        Method m=CriticalMachineState.class.getDeclaredMethod("allVoxels");m.setAccessible(true);
        return (List<CriticalGeometry.Voxel>)m.invoke(s);
    }
    static void put(CriticalMachineState s,CriticalGeometry.Voxel v,Block b) {
        ((SparseWorld)s.worldObj).blocks.put(key(s.blockWorldX(v),s.blockWorldY(v.y),s.blockWorldZ(v)),b);
    }
    static CriticalWorldData claim(CriticalMachineState s)throws Exception {
        CriticalWorldData d=CriticalWorldData.get(s.worldObj);
        check(d.acquire(s.getStructureId(),"owner",s.getOriginX(),s.getOriginY(),s.getOriginZ(),s.getRegionSize(),
            s.getRegionHeight(),s.xCoord,s.yCoord,s.zCoord),"claim exact region");
        check(d.bindTask(s.getStructureId(),"owner",s.getJobId(),s.getOriginX(),s.getOriginY(),s.getOriginZ()),"claim exact job");
        d.updateProtection(s);return d;
    }
    static TileEntityCriticalRenderAnchor anchor(CriticalMachineState s)throws Exception {
        NBTTagCompound n=new NBTTagCompound();n.setInteger("ownerX",s.xCoord);n.setInteger("ownerY",s.yCoord);n.setInteger("ownerZ",s.zCoord);
        n.setTag("visual",s.getVisualData());TileEntityCriticalRenderAnchor a=new TileEntityCriticalRenderAnchor();a.setWorldObj(s.worldObj);
        a.xCoord=CriticalRenderAnchors.getX(s);a.yCoord=CriticalRenderAnchors.getY(s);a.zCoord=CriticalRenderAnchors.getZ(s);
        n.setInteger("x",a.xCoord);n.setInteger("y",a.yCoord);n.setInteger("z",a.zCoord);a.readFromNBT(n);
        ((SparseWorld)s.worldObj).tiles.put(key(a.xCoord,a.yCoord,a.zCoord),a);
        ((SparseWorld)s.worldObj).blocks.put(key(a.xCoord,a.yCoord,a.zCoord),CriticalRenderAnchors.BLOCK);return a;
    }
    static void run()throws Exception {
        for(int revision:new int[]{25,26,27,28,29,30,31})for(ForgeDirection facing:new ForgeDirection[]{ForgeDirection.NORTH,
            ForgeDirection.EAST,ForgeDirection.SOUTH,ForgeDirection.WEST}) {
            SparseWorld w=world();CriticalMachineState s=state(w,revision,facing);List<CriticalGeometry.Voxel> all=geometry(s);
            List<CriticalGeometry.Voxel> selected=new ArrayList<>();
            for(int kind=0;kind<3;kind++)for(CriticalGeometry.Voxel v:all) {
                if(CriticalLoomStructure.isLoomVoxel(v)||CriticalStructures.isController(v)||!(v.block instanceof CriticalMaterials.MaterialBlock))continue;
                CriticalMaterials.MaterialBlock b=(CriticalMaterials.MaterialBlock)v.block;
                if(kind==0&&!b.transparent&&!b.proxy||kind==1&&b.transparent||kind==2&&b.proxy) {selected.add(v);break;}
            }
            check(selected.size()==3,"solid transparent moving candidates "+revision+" "+facing);
            for(CriticalGeometry.Voxel v:selected) {put(s,v,v.block);((BitSet)field(s,"placed")).set(all.indexOf(v));}
            CriticalWorldData d=claim(s);
            for(CriticalGeometry.Voxel v:selected) {
                int x=s.blockWorldX(v),y=s.blockWorldY(v.y),z=s.blockWorldZ(v);Block b=v.block;
                check(b.getBlockHardness(w,x,y,z)==-1F,"actual block hardness -1 paid partial "+revision+" "+facing);
                check(b.getExplosionResistance(null,w,x,y,z,x,y,z)==Float.MAX_VALUE,"actual coordinate resistance paid");
                Explosion explosion=new Explosion(w,null,x,y,z,Float.MAX_VALUE);
                b.onBlockExploded(w,x,y,z,explosion);check(w.getBlock(x,y,z)==b,"actual exploded callback retains paid");
                explosion.affectedBlockPositions.add(new ChunkPosition(x,y,z));
                explosion.affectedBlockPositions.add(new ChunkPosition(x+1000,y,z));
                new CriticalRuntime.StructureChangeListener().onExplosion(new ExplosionEvent.Detonate(w,explosion,new ArrayList<>()));
                check(explosion.affectedBlockPositions.size()==1&&explosion.affectedBlockPositions.get(0).chunkPosX==x+1000,
                    "actual Forge detonate removes paid and keeps unrelated at arbitrary explosion power");
                w.blocks.put(key(x+1000,y,z),b);check(b.getBlockHardness(w,x+1000,y,z)==500F,"same material outside claim stays hardness500");
            }
            CriticalGeometry.Voxel unpaid=all.stream().filter(cell->!selected.contains(cell)
                && !CriticalLoomStructure.isLoomVoxel(cell)&&!CriticalStructures.isController(cell)).findFirst().orElseThrow();
            put(s,unpaid,unpaid.block);
            check(unpaid.block.getBlockHardness(w,s.blockWorldX(unpaid),s.blockWorldY(unpaid.y),s.blockWorldZ(unpaid))==500F,
                "same structure material at unpaid cell inside region stays hardness500");
            CriticalGeometry.Voxel manual=all.stream().filter(CriticalLoomStructure::isLoomVoxel).findFirst().orElseThrow();
            Block shell=CriticalLoomStructure.shell(manual);put(s,manual,shell);int mx=s.blockWorldX(manual),my=s.blockWorldY(manual.y),mz=s.blockWorldZ(manual);
            check(shell.getBlockHardness(w,mx,my,mz)==5F,"manual loom normal hardness5");
            check(shell.getExplosionResistance(null,w,mx,my,mz,mx,my,mz)==10000F,"manual loom original resistance");
            shell.onBlockExploded(w,mx,my,mz,new Explosion(w,null,mx,my,mz,1));check(w.isAirBlock(mx,my,mz),"actual exploded callback permits manual loom removal");
            set(s,"state",2);d.updateProtection(s);
            NBTTagCompound persisted=new NBTTagCompound();d.writeToNBT(persisted);CriticalWorldData restored=new CriticalWorldData();restored.readFromNBT(persisted);
            w.perWorldStorage.setData("gtsr_critical_regions",restored);
            CriticalGeometry.Voxel v=selected.get(0);int x=s.blockWorldX(v),y=s.blockWorldY(v.y),z=s.blockWorldZ(v);
            w.ownerUnloaded=true;
            check(v.block.getBlockHardness(w,x,y,z)==-1F,"completed paid hardness survives world claim NBT and unloaded controller without loading it");
            check(w.setBlockToAir(x,y,z)&&!CriticalBlockProtection.protects(w,x,y,z),"direct internal world removal remains available");put(s,v,v.block);
            w.ownerUnloaded=false;
            w.isRemote=true;TileEntityCriticalRenderAnchor a=anchor(s);
            Field storage=World.class.getDeclaredField("perWorldStorage");storage.setAccessible(true);storage.set(w,null);
            check(v.block.getBlockHardness(w,x,y,z)==-1F,"actual client getter uses synchronized anchor claim without WorldSavedData");
            CriticalMachineState visual=a.getVisualState();
            @SuppressWarnings("unchecked")Map<CriticalMachineState,Object> clients=(Map<CriticalMachineState,Object>)field(CriticalBlockProtection.class,"CLIENT");
            Object cached=clients.get(visual);visual.readVisualData(s.getVisualData());
            check(clients.get(visual)==cached,"unchanged client claim reuses paid coordinate index");
            NBTTagCompound removed=s.getVisualData();removed.getCompoundTag("protection").setByteArray("placed",new byte[0]);
            visual.readVisualData(removed);check(v.block.getBlockHardness(w,x,y,z)==500F,"client paid-bit withdrawal clears previous protection");
            visual.readVisualData(s.getVisualData());
            CriticalRenderAnchors.BLOCK.onBlockExploded(w,a.xCoord,a.yCoord,a.zCoord,new Explosion(w,null,a.xCoord,a.yCoord,a.zCoord,Float.MAX_VALUE));
            check(w.getTileEntity(a.xCoord,a.yCoord,a.zCoord)==a&&w.getBlock(a.xCoord,a.yCoord,a.zCoord)==CriticalRenderAnchors.BLOCK,
                "actual internal anchor exploded callback retains attachment");
            check(w.setBlockToAir(a.xCoord,a.yCoord,a.zCoord),"internal anchor remains removable by GUI world mutation");
            check(v.block.getBlockHardness(w,x,y,z)==500F,"unloaded client source cannot protect later manual block");
            new CriticalRuntime.StructureChangeListener().onWorldUnload(new net.minecraftforge.event.world.WorldEvent.Unload(w));
            if(revision==31&&facing==ForgeDirection.NORTH)check(!clients.containsKey(visual),"actual WorldEvent.Unload releases client world cache");
        }
    }
    public static void main(String[]args)throws Exception {
        JsonObject report=new JsonObject();try {
            Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(sun.misc.Unsafe)f.get(null);materials();run();report.addProperty("status","PASS");
        } catch(Throwable failure) {report.addProperty("status","FAIL");report.addProperty("failure",failure.toString());throw failure;}
        finally {report.add("checks",checks);Files.writeString(Path.of(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
    }
    static final class SparseWorld extends World {
        Map<String,Block>blocks;Map<String,TileEntity>tiles;IChunkProvider chunks;
        boolean ownerUnloaded;int ownerX,ownerY,ownerZ;
        SparseWorld(){super(null,"unused",(WorldProvider)null,null,null);}
        @Override public Block getBlock(int x,int y,int z){return blocks.getOrDefault(key(x,y,z),AIR);}
        @Override public int getBlockMetadata(int x,int y,int z){return 0;}
        @Override public boolean isAirBlock(int x,int y,int z){return !blocks.containsKey(key(x,y,z));}
        @Override public TileEntity getTileEntity(int x,int y,int z){
            if(ownerUnloaded&&x==ownerX&&y==ownerY&&z==ownerZ)throw new AssertionError("Unloaded controller must not be loaded by protection");
            return tiles.get(key(x,y,z));
        }
        @Override public boolean blockExists(int x,int y,int z){return true;}
        @Override public IChunkProvider getChunkProvider(){return chunks;}
        @Override public long getTotalWorldTime(){return 0;}
        @Override public List<Entity> getEntitiesWithinAABBExcludingEntity(Entity entity,net.minecraft.util.AxisAlignedBB bounds){return Collections.emptyList();}
        @Override public void playSoundEffect(double x,double y,double z,String sound,float volume,float pitch){}
        @Override public void spawnParticle(String particle,double x,double y,double z,double vx,double vy,double vz){}
        @Override public boolean setBlockToAir(int x,int y,int z){blocks.remove(key(x,y,z));tiles.remove(key(x,y,z));return true;}
        @Override public void markBlockRangeForRenderUpdate(int x,int y,int z,int xx,int yy,int zz){}
        @Override protected IChunkProvider createChunkProvider(){throw new UnsupportedOperationException();}
        @Override protected int func_152379_p(){throw new UnsupportedOperationException();}
        @Override public Entity getEntityByID(int id){throw new UnsupportedOperationException();}
    }
}
