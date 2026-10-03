import java.lang.reflect.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.storage.*;
import com.google.gson.*;
import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.*;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.*;

/** Calls the real city generation and death methods; only loaded storage and collision queries are adapted. */
public final class CityGuardCheck extends RemasterRuntimeCheck {
    static class CW extends W {
        boolean collision, liquid;
        int ownerX, ownerZ;
        public long getSeed() { return 20261001L; }
        public boolean blockExists(int x,int y,int z) { return loaded && y >= 1 && y <= 254 && Math.abs((x >> 4)-ownerX)<=1 && Math.abs((z >> 4)-ownerZ)<=1; }
        public List getCollidingBoundingBoxes(Entity e, AxisAlignedBB box) { return collision ? Collections.singletonList(box) : actualBlockCollisions(this,box); }
        public boolean checkNoEntityCollision(AxisAlignedBB box) { return !occupied; }
        public boolean isAnyLiquid(AxisAlignedBB box) { return liquid; }
    }
    static CW world(RemasterSite city) throws Exception {
        CW w=(CW)u.allocateInstance(CW.class);
        w.blocks=new HashMap<>();w.metas=new HashMap<>();w.tiles=new HashMap<>();w.loaded=true;
        int[] at=RemasterCityBridge.witnessPosition(city);
        check(at!=null,"city has an admitted witness position");
        w.ownerX=at[0]>>4;w.ownerZ=at[2]>>4;w.actualSideQueries=true;
        set(w,World.class,"perWorldStorage",new MapStorage((ISaveHandler)null));
        set(w,World.class,"provider",u.allocateInstance(WorldProviderProsperityRuins.class));
        set(w,World.class,"rand",new Random(7));set(w,World.class,"loadedEntityList",new ArrayList<>());
        return w;
    }
    static void place(CW w,RemasterSite city) {
        // Populate loaded neighboring owner slices once, as an actual chunk boundary may cross the 5x5 body.
        if(w.loaded && w.blocks.isEmpty())
            for(int cx=w.ownerX-1;cx<=w.ownerX+1;cx++)for(int cz=w.ownerZ-1;cz<=w.ownerZ+1;cz++)
                if(cx!=w.ownerX||cz!=w.ownerZ)RemasterWorldgen.placeGeometryChunk(w,city,cx,cz);
        RemasterWorldgen.placeChunk(w,city,w.ownerX,w.ownerZ);
    }
    static void concurrentPlanning(RemasterSite city) throws Exception {
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        Thread planner=new Thread(()->{
            try { synchronized(RemasterPlanner.class) {
                entered.countDown();check(entered.await(10,java.util.concurrent.TimeUnit.SECONDS),"both planning entry locks acquired");
                if(!RemasterCityBridge.viable(city))throw new AssertionError("concurrent city viability changed");
            }}catch(Throwable t){failure.set(t);}
        },"city-planner-entry");
        Thread bridge=new Thread(()->{
            try { synchronized(RemasterCityBridge.class) {
                entered.countDown();if(!entered.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("planning entry timed out");
                if(RemasterPlanner.savedCityPlots(city).size()!=28)throw new AssertionError("concurrent saved plot roster changed");
            }}catch(Throwable t){failure.set(t);}
        },"city-bridge-entry");
        planner.setDaemon(true);bridge.setDaemon(true);planner.start();bridge.start();
        planner.join(15000);bridge.join(15000);
        check(!planner.isAlive()&&!bridge.isAlive()&&failure.get()==null,"opposing public planner/bridge entries complete without lock inversion: "+failure.get());
    }
    public static void main(String[] args) throws Exception {
        NativeTerrainFixture.initialize();
        Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);u=(sun.misc.Unsafe)uf.get(null);
        Class.forName("net.minecraft.init.Blocks");
        Constructor<BlockAir> air=BlockAir.class.getDeclaredConstructor();air.setAccessible(true);setBlock("air",air.newInstance());
        setBlock("stone",new BlockStone());setBlock("stonebrick",new BlockStoneBrick());
        Constructor<BlockStairs> stairs=BlockStairs.class.getDeclaredConstructor(Block.class,int.class);stairs.setAccessible(true);setBlock("stone_brick_stairs",stairs.newInstance(Blocks.stonebrick,0));
        Constructor<BlockFence> fence=BlockFence.class.getDeclaredConstructor(String.class,net.minecraft.block.material.Material.class);fence.setAccessible(true);setBlock("fence",fence.newInstance("planks",net.minecraft.block.material.Material.wood));
        Constructor<BlockGrass> grass=BlockGrass.class.getDeclaredConstructor();grass.setAccessible(true);setBlock("grass",grass.newInstance());
        com.miaokatze.gtsr.common.blocks.BlocksGTSR.prosperityZenithLog = new com.miaokatze.gtsr.common.dimension.prosperity.architecture.BlockZenithLog("ProsperityZenithLog", "gtsr:prosperity_zenith_log_side", "gtsr:prosperity_zenith_log_top");
        com.miaokatze.gtsr.common.dimension.prosperity.architecture.RuinsArchitecture.registerBlocks();
        com.miaokatze.gtsr.common.dimension.prosperity.architecture.RoyalArchitecture.registerBlocks();
        Field registry=RemasterBlocks.class.getDeclaredField("REGISTRY");registry.setAccessible(true);Map map=(Map)registry.get(null);
        for(int i=0;i<94;i++){Method f=RemasterBlocks.class.getDeclaredMethod("block"+i);f.setAccessible(true);RemasterBlock b=(RemasterBlock)f.invoke(null);map.put(b.id(),b);}
        Field vanillaRegistry=Block.class.getField("blockRegistry");
        u.putObject(u.staticFieldBase(vanillaRegistry),u.staticFieldOffset(vanillaRegistry),new net.minecraft.util.RegistryNamespaced());
        Block.blockRegistry.addObject(1,"minecraft:stone",Blocks.stone);
        Block.blockRegistry.addObject(2,"minecraft:grass",Blocks.grass);
        Block.blockRegistry.addObject(98,"minecraft:stonebrick",Blocks.stonebrick);
        Block.blockRegistry.addObject(109,"minecraft:stone_brick_stairs",Blocks.stone_brick_stairs);
        Block.blockRegistry.addObject(85,"minecraft:fence",Blocks.fence);
        RemasterSite city=null;List<RemasterSite> parent=null;
        outer:for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++){
            List<RemasterSite> sites=RemasterPlanner.cell(20261001L,0,x,z);
            for(RemasterSite candidate:sites)if(candidate.layout.equals("city-grid")){city=candidate;parent=sites;break outer;}
        }
        check(city!=null,"actual deterministic natural city parent");
        concurrentPlanning(city);
        int index=-1;String spawnId="";JsonArray spawns=city.plan().metadata.getAsJsonArray("spawns");
        for(int i=0;i<spawns.size();i++)if(spawns.get(i).getAsJsonObject().get("code").getAsString().equals("di-07")){
            index=i;spawnId=spawns.get(i).getAsJsonObject().get("id").getAsString();break;
        }
        check(index>=0,"DI07 actual metadata array index");
        CW w=world(city);place(w,city);
        check(w.spawns==1,"production placeChunk generates DI07 on actual plaza");
        EntityOldEcho guard=(EntityOldEcho)w.loadedEntityList.get(0);
        check(guard.getKind()==EchoKind.DI07 && guard.getEncounterId().equals(city.id()) && guard.getDataWatcher().getWatchableObjectInt(23)==index,
            "actual entity source is city parent and metadata death node");
        int[] witness=RemasterCityBridge.witnessPosition(city);int plazaY=witness[1];
        check(guard.posX==witness[0]+.5 && guard.posY==plazaY && guard.posZ==witness[2]+.5,"actual relocated spawn coordinates");
        check(w.getBlock(witness[0],plazaY-1,witness[2])==Blocks.stonebrick,"actual production plaza footing");
        check(RemasterSpawn.safe(w,guard,true),"actual DI07 whole 5x5 support and body clearance on generated bridge");
        NBTTagCompound position=RemasterData.get(w).state(city.id()).getCompoundTag("spawn-pos:"+spawnId);
        check(position.getDouble("x")==guard.posX && position.getDouble("y")==guard.posY && position.getDouble("z")==guard.posZ,"persistent exact relocated witness position");
        int plots=0;
        for(RemasterSite plot:parent)if(plot.layout.equals("city-plot")){
            plots++;
            check(guard.boundingBox.maxX<plot.minX() || guard.boundingBox.minX>plot.maxX()+1 || guard.boundingBox.maxZ<plot.minZ() || guard.boundingBox.minZ>plot.maxZ()+1,
                "actual guard collision box never intersects final plot "+plot.prefab);
        }
        check(plots==28,"all 28 final plots checked");
        int oldWrites=w.writes;place(w,city);check(w.spawns==1 && w.writes==oldWrites,"repeat placeChunk does not regenerate geometry or DI07");
        NBTTagCompound saved=new NBTTagCompound();RemasterData.get(w).writeToNBT(saved);RemasterData restored=new RemasterData();restored.readFromNBT(saved);
        w.perWorldStorage.setData("gtsr.prosperityRemaster7",restored);place(w,city);check(w.spawns==1,"real NBT reload does not respawn DI07");
        RemasterRuntime.death(w,guard.getEncounterId(),guard.getDataWatcher().getWatchableObjectInt(23));
        check(RemasterData.get(w).flag(city.id(),"dead:"+spawnId),"actual metadata death node marked");
        check(w.spawns==2 && w.loadedEntityList.get(1) instanceof net.minecraft.entity.item.EntityItem,"actual city death issues witness");
        net.minecraft.entity.item.EntityItem drop=(net.minecraft.entity.item.EntityItem)w.loadedEntityList.get(1);
        check(RemasterWitness.authentic(drop.getEntityItem(),w,"di-07"),"city parent witness source authenticated");
        check(drop.posX==position.getDouble("x") && drop.posY==position.getDouble("y") && drop.posZ==position.getDouble("z"),"death original drops at persisted relocated plaza");
        RemasterRuntime.death(w,city.id(),index);place(w,city);check(w.spawns==2,"repeated death and generation never duplicate DI07/original");
        CW unloaded=world(city);unloaded.loaded=false;place(unloaded,city);check(unloaded.spawns==0,"unloaded owner cannot create DI07");
        CW collision=world(city);collision.collision=true;place(collision,city);check(collision.spawns==0,"block collision prevents DI07");
        collision.collision=false;place(collision,city);check(collision.spawns==1,"failed collision remains retryable without replaying geometry");
        CW occupied=world(city);occupied.occupied=true;place(occupied,city);check(occupied.spawns==0,"entity collision prevents DI07");
        CW liquid=world(city);liquid.liquid=true;place(liquid,city);check(liquid.spawns==0,"liquid prevents DI07");
        RemasterSite legacy=new RemasterSite(city.prefab,city.variant,city.seed,city.x,city.y,city.z,"city-grid");
        CW oldWorld=world(legacy);place(oldWorld,legacy);
        check(oldWorld.spawns==1,"unfinished legacy witness admits on original saved flat road");
        EntityOldEcho oldGuard=(EntityOldEcho)oldWorld.loadedEntityList.get(0);
        check(oldGuard.posX==legacy.x+724.5 && oldGuard.posY==legacy.y && oldGuard.posZ==legacy.z+196.5,
            "legacy witness retains savedY footing without native reanchoring");
        System.out.println("PASS: "+checks+" real city-plaza generation/entity/NBT/death/loaded/collision/28-plot assertions");
    }
}
