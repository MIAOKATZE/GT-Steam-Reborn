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
        public boolean blockExists(int x,int y,int z) { return loaded && y >= 1 && y <= 254 && (x >> 4) == ownerX && (z >> 4) == ownerZ; }
        public List getCollidingBoundingBoxes(Entity e, AxisAlignedBB box) { return collision ? Collections.singletonList(box) : Collections.emptyList(); }
        public boolean checkNoEntityCollision(AxisAlignedBB box) { return !occupied; }
        public boolean isAnyLiquid(AxisAlignedBB box) { return liquid; }
    }
    static CW world(RemasterSite city) throws Exception {
        CW w=(CW)u.allocateInstance(CW.class);
        w.blocks=new HashMap<>();w.metas=new HashMap<>();w.tiles=new HashMap<>();w.loaded=true;
        w.ownerX=(city.x+724)>>4;w.ownerZ=(city.z+196)>>4;
        set(w,World.class,"perWorldStorage",new MapStorage((ISaveHandler)null));
        set(w,World.class,"provider",u.allocateInstance(WorldProviderProsperityRuins.class));
        set(w,World.class,"rand",new Random(7));set(w,World.class,"loadedEntityList",new ArrayList<>());
        return w;
    }
    static void place(CW w,RemasterSite city) { RemasterWorldgen.placeChunk(w,city,w.ownerX,w.ownerZ); }
    public static void main(String[] args) throws Exception {
        Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);u=(sun.misc.Unsafe)uf.get(null);
        Class.forName("net.minecraft.init.Blocks");
        Constructor<BlockAir> air=BlockAir.class.getDeclaredConstructor();air.setAccessible(true);setBlock("air",air.newInstance());
        setBlock("stone",new BlockStone());setBlock("stonebrick",new BlockStoneBrick());
        Constructor<BlockGrass> grass=BlockGrass.class.getDeclaredConstructor();grass.setAccessible(true);setBlock("grass",grass.newInstance());
        com.miaokatze.gtsr.common.dimension.prosperity.architecture.RuinsArchitecture.registerBlocks();
        com.miaokatze.gtsr.common.dimension.prosperity.architecture.RoyalArchitecture.registerBlocks();
        Field registry=RemasterBlocks.class.getDeclaredField("REGISTRY");registry.setAccessible(true);Map map=(Map)registry.get(null);
        for(int i=0;i<94;i++){Method f=RemasterBlocks.class.getDeclaredMethod("block"+i);f.setAccessible(true);RemasterBlock b=(RemasterBlock)f.invoke(null);map.put(b.id(),b);}
        Field vanillaRegistry=Block.class.getField("blockRegistry");
        u.putObject(u.staticFieldBase(vanillaRegistry),u.staticFieldOffset(vanillaRegistry),new net.minecraft.util.RegistryNamespaced());
        Block.blockRegistry.addObject(1,"minecraft:stone",Blocks.stone);
        Block.blockRegistry.addObject(2,"minecraft:grass",Blocks.grass);
        Block.blockRegistry.addObject(98,"minecraft:stonebrick",Blocks.stonebrick);
        RemasterSite city=null;List<RemasterSite> parent=null;
        outer:for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++){
            List<RemasterSite> sites=RemasterPlanner.cell(20261001L,0,x,z);
            for(RemasterSite candidate:sites)if(candidate.layout.equals("city-grid")){city=candidate;parent=sites;break outer;}
        }
        check(city!=null,"actual deterministic natural city parent");
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
        check(guard.posX==city.x+724.5 && guard.posY==city.y && guard.posZ==city.z+196.5,"actual relocated spawn coordinates");
        check(w.getBlock(city.x+724,city.y-1,city.z+196)==Blocks.stonebrick,"actual production plaza footing");
        NBTTagCompound position=RemasterData.get(w).state(city.id()).getCompoundTag("spawn-pos:"+spawnId);
        check(position.getInteger("x")==city.x+724 && position.getInteger("y")==city.y && position.getInteger("z")==city.z+196,"persistent relocated witness position");
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
        System.out.println("PASS: "+checks+" real city-plaza generation/entity/NBT/death/loaded/collision/28-plot assertions");
    }
}
