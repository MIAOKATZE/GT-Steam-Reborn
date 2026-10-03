import java.lang.reflect.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import com.google.gson.*;
import com.miaokatze.gtsr.common.dimension.prosperity.*;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.*;

/** Native-height solid storage, real natural water, exact authored overlays and vanilla movement. */
public final class CityBridgeCollisionCheck {
    static final int[][] DIR={{1,0},{-1,0},{0,1},{0,-1}};
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static Block material(String key) {
        String id=key.split("#")[0];
        if(id.equals("minecraft:air"))return Blocks.air;
        if(id.equals("minecraft:stonebrick"))return Blocks.stonebrick;
        if(id.equals("minecraft:stone_brick_stairs"))return Blocks.stone_brick_stairs;
        if(id.equals("minecraft:fence"))return Blocks.fence;
        return RemasterRuntime.resolve(key);
    }
    static class W extends RemasterRuntimeCheck.W {
        RemasterSite city;
        int[][] natural,water;
        List<RemasterSite> plots;
        public Block getBlock(int x,int y,int z) {
            String key=key(x,y,z);if(blocks.containsKey(key))return blocks.get(key);
            int u=x-city.x+2,v=z-city.z+2;
            if(u<0||v<0||u>=772||v>=388)return Blocks.air;
            return y<=natural[u][v]?Blocks.stone:y<=water[u][v]?Blocks.water:Blocks.air;
        }
        public List getCollidingBoundingBoxes(Entity e,AxisAlignedBB box){
            List<AxisAlignedBB> out=new ArrayList<>();
            for(int x=(int)Math.floor(box.minX)-1;x<=(int)Math.floor(box.maxX)+1;x++)
                for(int y=(int)Math.floor(box.minY)-1;y<=(int)Math.floor(box.maxY)+1;y++)
                    for(int z=(int)Math.floor(box.minZ)-1;z<=(int)Math.floor(box.maxZ)+1;z++){
                        Block b=getBlock(x,y,z);if(b!=Blocks.air)b.addCollisionBoxesToList(this,x,y,z,box,out,e);
                    }
            return out;
        }
        public boolean func_147470_e(AxisAlignedBB b){return false;}
        public boolean isSideSolid(int x,int y,int z,net.minecraftforge.common.util.ForgeDirection side,boolean fallback){return getBlock(x,y,z).isOpaqueCube();}
    }
    static class E extends Entity {
        E(World w){super(w);setSize(.6F,1.8F);stepHeight=.5F;}
        protected void entityInit(){}protected void readEntityFromNBT(NBTTagCompound n){}protected void writeEntityToNBT(NBTTagCompound n){}
        protected void func_145775_I(){}protected boolean canTriggerWalking(){return false;}public boolean isWet(){return false;}
    }
    static boolean move(W w,E e,int x,int h,int z){
        double tx=x+.5,tz=z+.5;
        for(int i=0;i<32;i++){
            double dx=tx-e.posX,dz=tz-e.posZ;if(Math.abs(dx)+Math.abs(dz)<.005)break;
            e.moveEntity(Math.max(-.1,Math.min(.1,dx)),-.08,Math.max(-.1,Math.min(.1,dz)));
        }
        for(int i=0;i<12;i++)e.moveEntity(0,-.1,0);
        return Math.abs(e.posX-tx)<.01&&Math.abs(e.posZ-tz)<.01&&Math.abs(e.boundingBox.minY-h-1)<.01
            &&w.getCollidingBoundingBoxes(e,e.boundingBox).isEmpty()
            &&!w.getCollidingBoundingBoxes(e,AxisAlignedBB.getBoundingBox(e.boundingBox.minX,e.boundingBox.minY-.02,e.boundingBox.minZ,e.boundingBox.maxX,e.boundingBox.minY-.001,e.boundingBox.maxZ)).isEmpty();
    }
    static E entity(W w,int[] a){E e=new E(w);e.setPosition(a[0]+.5,a[1]+1,a[2]+.5);e.onGround=true;for(int i=0;i<12;i++)e.moveEntity(0,-.1,0);return e;}
    static boolean replay(W w,List<int[]> path){E e=entity(w,path.get(0));for(int i=1;i<path.size();i++){int[] p=path.get(i);if(!move(w,e,p[0],p[1],p[2])){System.out.println("REPLAY_FAIL "+Arrays.toString(path.get(i-1))+" -> "+Arrays.toString(p)+" actual="+e.posX+","+e.boundingBox.minY+","+e.posZ);return false;}}return true;}
    static void init()throws Exception {
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);RemasterRuntimeCheck.u=(sun.misc.Unsafe)f.get(null);
        Class.forName("net.minecraft.init.Blocks");
        Constructor<BlockAir> air=BlockAir.class.getDeclaredConstructor();air.setAccessible(true);RemasterRuntimeCheck.setBlock("air",air.newInstance());
        RemasterRuntimeCheck.setBlock("stone",new BlockStone());
        Constructor<BlockStoneBrick> brick=BlockStoneBrick.class.getDeclaredConstructor();brick.setAccessible(true);RemasterRuntimeCheck.setBlock("stonebrick",brick.newInstance());
        Constructor<BlockStairs> stairs=BlockStairs.class.getDeclaredConstructor(Block.class,int.class);stairs.setAccessible(true);RemasterRuntimeCheck.setBlock("stone_brick_stairs",stairs.newInstance(Blocks.stonebrick,0));
        Constructor<BlockFence> fence=BlockFence.class.getDeclaredConstructor(String.class,net.minecraft.block.material.Material.class);fence.setAccessible(true);RemasterRuntimeCheck.setBlock("fence",fence.newInstance("planks",net.minecraft.block.material.Material.wood));
        Constructor<BlockStaticLiquid> water=BlockStaticLiquid.class.getDeclaredConstructor(net.minecraft.block.material.Material.class);water.setAccessible(true);RemasterRuntimeCheck.setBlock("water",water.newInstance(net.minecraft.block.material.Material.water));
        com.miaokatze.gtsr.common.blocks.BlocksGTSR.ruinDebris=new com.miaokatze.gtsr.common.dimension.prosperity.block.BlockRuinDebris();
        com.miaokatze.gtsr.common.blocks.BlocksGTSR.ruinedCasing=new com.miaokatze.gtsr.common.dimension.prosperity.block.BlockRuinedCasing();
        com.miaokatze.gtsr.common.blocks.BlocksGTSR.prosperityStone=Blocks.stone;
        com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterRegistry.sealedChest=
            new com.miaokatze.gtsr.common.dimension.prosperity.encounter.BlockSealedChest();
        com.miaokatze.gtsr.common.blocks.BlocksGTSR.prosperityZenithLog=new com.miaokatze.gtsr.common.dimension.prosperity.architecture.BlockZenithLog("ProsperityZenithLog","gtsr:prosperity_zenith_log_side","gtsr:prosperity_zenith_log_top");
        com.miaokatze.gtsr.common.dimension.prosperity.architecture.RuinsArchitecture.registerBlocks();
        com.miaokatze.gtsr.common.dimension.prosperity.architecture.RoyalArchitecture.registerBlocks();
        Field registry=RemasterBlocks.class.getDeclaredField("REGISTRY");registry.setAccessible(true);Map<String,RemasterBlock> map=(Map<String,RemasterBlock>)registry.get(null);
        for(Method method:RemasterBlocks.class.getDeclaredMethods())if(method.getName().matches("block[0-9]+")){method.setAccessible(true);RemasterBlock b=(RemasterBlock)method.invoke(null);map.put(b.id(),b);}
        NativeTerrainFixture.initialize();
    }
    static W world(RemasterSite city)throws Exception {
        W w=(W)RemasterRuntimeCheck.u.allocateInstance(W.class);w.city=city;w.plots=RemasterPlanner.savedCityPlots(city);
        w.natural=new int[772][388];w.water=new int[772][388];w.blocks=new HashMap<>();w.metas=new HashMap<>();w.tiles=new HashMap<>();w.loaded=true;w.seed=city.seed;w.actualSideQueries=true;
        RemasterRuntimeCheck.set(w,World.class,"provider",RemasterRuntimeCheck.u.allocateInstance(WorldProviderProsperityRuins.class));
        RemasterRuntimeCheck.set(w,World.class,"theProfiler",new Profiler());RemasterRuntimeCheck.set(w,World.class,"rand",new Random(1));
        RemasterRuntimeCheck.set(w,World.class,"loadedEntityList",new ArrayList<>());
        for(int u=-2;u<770;u++)for(int v=-2;v<386;v++){w.natural[u+2][v+2]=ProsperityTerrainProfile.heightAt(city.seed,city.x+u,city.z+v);w.water[u+2][v+2]=ChunkProviderProsperityRuins.naturalWaterTopAt(city.seed,city.x+u,city.z+v);}
        int streetX=city.x+2,streetZ=city.z+2,streetY=RemasterCityBridge.floorAt(city,streetX,streetZ);
        Block trunk=com.miaokatze.gtsr.common.blocks.BlocksGTSR.prosperityZenithLog;
        w.blocks.put(w.key(streetX,streetY+1,streetZ),trunk);w.blocks.put(w.key(streetX,streetY+2,streetZ),trunk);
        int spareX=city.x+10,spareZ=city.z+10,spareY=ProsperityTerrainProfile.heightAt(city.seed,spareX,spareZ)+1;
        require(!RemasterCityBridge.walkable(city,spareX,spareZ),"outside flora probe became corridor");
        w.blocks.put(w.key(spareX,spareY,spareZ),trunk);
        int[] witness=RemasterCityBridge.witnessPosition(city);require(witness!=null,"missing admitted witness spot");
        w.blocks.put(w.key(witness[0],witness[1]+4,witness[2]),trunk);
        // Full authored slice overlay, including explicit air; bridge emitted first like production.
        for(int cx=city.minX()>>4;cx<=city.maxX()>>4;cx++)for(int cz=city.minZ()>>4;cz<=city.maxZ()>>4;cz++){
            final int ownerX=cx,ownerZ=cz;
            RemasterCityBridge.geometry(city,(x,y,z,b,m,flags)->{
                require((x>>4)==ownerX&&(z>>4)==ownerZ&&y>=1&&y<=254,"foreign owner/world height");
                w.blocks.put(w.key(x,y,z),material(b.toString()+"#"+m));w.metas.put(w.key(x,y,z),m);return true;
            },cx,cz);
        }
        Map<String,Block> bridge=new HashMap<>(w.blocks);Map<String,Integer> metas=new HashMap<>(w.metas);
        for(int cx=city.maxX()>>4;cx>=city.minX()>>4;cx--)for(int cz=city.maxZ()>>4;cz>=city.minZ()>>4;cz--)RemasterCityBridge.geometry(city,(x,y,z,b,m,flags)->{
            require(bridge.get(w.key(x,y,z))==material(b.toString()+"#"+m)&&metas.get(w.key(x,y,z))==m,"reverse owner differs");return true;
        },cx,cz);
        for(RemasterSite p:w.plots)for(int cx=p.minX()>>4;cx<=p.maxX()>>4;cx++)for(int cz=p.minZ()>>4;cz<=p.maxZ()>>4;cz++)RemasterWorldgen.geometry(p,p.plan(),(x,y,z,b,m,flags)->{
            String key=w.key(x,y,z);Block block=material(b.toString()+"#"+m);
            require(!bridge.containsKey(key)||bridge.get(key)==block&&metas.get(key)==m,"bridge overwrites authored source "+p.prefab+" "+key);
            w.blocks.put(key,block);w.metas.put(key,m);return true;
        },cx,cz);
        require(w.getBlock(streetX,streetY+1,streetZ)==Blocks.air&&w.getBlock(streetX,streetY+2,streetZ)==Blocks.air,"owned street trunk head/feet not cleared");
        require(w.getBlock(spareX,spareY,spareZ)==trunk,"non-corridor tree removed");
        require(w.getBlock(witness[0],witness[1]+4,witness[2])==Blocks.air,"DI07 head-height trunk not cleared");
        return w;
    }
    static void witness(W w,JsonObject report) {
        int[] point=RemasterCityBridge.witnessPosition(w.city);require(point!=null,"missing witness position");
        for(int x=point[0]-2;x<=point[0]+2;x++)for(int z=point[2]-2;z<=point[2]+2;z++) {
            require(RemasterCityBridge.walkable(w.city,x,z)&&RemasterCityBridge.floorAt(w.city,x,z)==point[1]-1,"unequal witness footing");
            require(w.getBlock(x,point[1]-1,z)==Blocks.stonebrick&&w.isSideSolid(x,point[1]-1,z,net.minecraftforge.common.util.ForgeDirection.UP,false),"witness is not full actual UP support");
        }
        com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho guard=RemasterSpawn.spawn(w,
            com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind.DI07,w.city.id(),point[0]+.5,point[1],point[2]+.5,0,false);
        require(guard!=null,"actual DI07 admission rejects bridge witness spot");
        require(guard.posY==point[1]&&Math.abs(guard.width-4.55)<.001&&Math.abs(guard.height-6.014)<.001,"DI07 dimension or spawn moved below bridge");
        require(w.getCollidingBoundingBoxes(guard,guard.boundingBox).isEmpty(),"actual DI07 body collides");
        report.add("witnessPosition",new Gson().toJsonTree(point));report.addProperty("actualDI07FullFootingAndCollision",true);
    }
    static int rails(W w) {
        int tested=0;RemasterSite s=w.city;
        for(int u=0;u<768;u++)for(int v=0;v<384;v++) {
            int x=s.x+u,z=s.z+v;if(!RemasterCityTerrain.contains(s,x,z)||RemasterCityBridge.walkable(s,x,z))continue;
            int h=RemasterCityBridge.floorAt(s,x,z);require(w.getBlock(x,h+1,z)==Blocks.fence,"missing final edge fence");
            for(int d=0;d<4;d++) {
                int nx=x+DIR[d][0],nz=z+DIR[d][1];if(!RemasterCityBridge.walkable(s,nx,nz))continue;
                E e=entity(w,new int[]{nx,RemasterCityBridge.floorAt(s,nx,nz),nz});
                move(w,e,x,h,z);
                require(Math.abs(e.posX-x-.5)>.05||Math.abs(e.posZ-z-.5)>.05,"vanilla fence allows exterior crossing");tested++;
            }
        }
        require(tested>0,"empty rail collision proof");return tested;
    }
    public static void main(String[] args)throws Exception {
        init();JsonArray reports=new JsonArray();boolean first=Arrays.asList(args).contains("--first"),planOnly=Arrays.asList(args).contains("--plan");
        String report="temp/city-bridge-verification/report.json";int reportFlag=Arrays.asList(args).indexOf("--report");if(reportFlag>=0)report=args[reportFlag+1];
        for(long seed:new long[]{20261001L,20261002L,20261003L}) {
            RemasterSite city=null;int rejected=0;
            outer:for(int gx=-12;gx<=12;gx++)for(int gz=-12;gz<=12;gz++)for(RemasterSite s:RemasterPlanner.cell(seed,0,gx,gz))if(s.layout.equals("city-grid")){
                if(Arrays.asList(args).contains("--scan")&&!RemasterCityBridge.viable(s)){
                    rejected++;System.out.println("BRIDGE_REJECT "+seed+" "+s.x+","+s.z+" "+RemasterCityBridge.failure(s));continue;
                }
                city=s;break outer;
            }
            require(city!=null,"empty original domain seed="+seed);
            JsonObject r=new JsonObject();r.addProperty("seed",seed);r.addProperty("origin",city.x+","+city.y+","+city.z);
            r.addProperty("rejectedCandidates",rejected);
            boolean viable=RemasterCityBridge.viable(city);r.addProperty("viable",viable);r.addProperty("failure",RemasterCityBridge.failure(city));
            if(planOnly)for(RemasterSite plot:RemasterPlanner.savedCityPlots(city)) {
                System.out.println("ENTRY "+plot.prefab+" "+plot.entryX()+","+plot.entryY()+","+plot.entryZ()
                    +" native="+ProsperityTerrainProfile.heightAt(seed,plot.entryX(),plot.entryZ())
                    +" water="+ChunkProviderProsperityRuins.naturalWaterTopAt(seed,plot.entryX(),plot.entryZ()));
                if(plot.prefab.equals("city_water_tower"))for(int dz=-5;dz<=0;dz++)for(int dx=-2;dx<=2;dx++){
                    int xx=plot.entryX()+dx,zz=plot.entryZ()+dz;
                    String one=RemasterCityBridge.source(plot,xx,plot.entryY()+1,zz),two=RemasterCityBridge.source(plot,xx,plot.entryY()+2,zz);
                    System.out.println("MOUTH "+dx+","+dz+" native="+ProsperityTerrainProfile.heightAt(seed,xx,zz)+" source="+one+"/"+two);
                }
            }
            JsonArray paths=new JsonArray();for(RemasterCityBridge.Connection c:RemasterCityBridge.connections(city)){JsonObject row=new JsonObject();row.addProperty("plot",c.plot.prefab);row.addProperty("edges",c.points.size()-1);row.add("path",new Gson().toJsonTree(c.points));paths.add(row);}r.add("connections",paths);reports.add(r);
            System.out.println("BRIDGE_PLAN seed="+seed+" origin="+r.get("origin")+" viable="+viable+" paths="+paths.size()+" failure="+RemasterCityBridge.failure(city));
            if(viable&&!planOnly){W w=world(city);int passed=0;for(RemasterCityBridge.Connection c:RemasterCityBridge.connections(city)){require(replay(w,c.points),"forward source route "+c.plot.prefab);List<int[]> reverse=new ArrayList<>(c.points);Collections.reverse(reverse);require(replay(w,reverse),"reverse source route "+c.plot.prefab);passed++;}r.addProperty("twoWayMinecraftEntrances",passed);r.addProperty("railBlockedDirections",rails(w));r.addProperty("reverseOwnerExact",true);r.addProperty("ownedHeadAirAndOutsideTree",true);witness(w,r);}
            java.nio.file.Files.write(java.nio.file.Paths.get(report),new GsonBuilder().setPrettyPrinting().create().toJson(reports).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if(first)break;
        }
        if(!planOnly)for(JsonElement e:reports)require(e.getAsJsonObject().get("viable").getAsBoolean(),"bridge candidate invalid");
    }
}
