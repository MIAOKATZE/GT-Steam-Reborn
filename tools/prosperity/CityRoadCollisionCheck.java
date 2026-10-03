import java.lang.reflect.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import com.google.gson.*;
import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.architecture.*;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.*;

/** Production bridge writes, actual natural water and unmodified Minecraft .5-step movement. */
public final class CityRoadCollisionCheck {
 static final int[][] DIR={{1,0},{-1,0},{0,1},{0,-1}};
 static final Map<String,Block> MATERIALS=new HashMap<>();
 static class Cell {
  final String id;final int meta;
  Cell(String id,int meta){this.id=id;this.meta=meta;}
 }
 static Block material(String id){Block b=MATERIALS.get(id);if(b==null){b=RemasterRuntime.resolve(id);MATERIALS.put(id,b);}return b;}
 static class W extends RemasterRuntimeCheck.W {
  int ox,oz;int[][] height,natural,water,meta;boolean[][] road;
  Map<Long,Cell> writesByCell,authored,bridgeWrites;boolean original;
  long packed(int x,int y,int z){return ((long)(x-ox+2)<<32)^((long)(z-oz+2)<<9)^y;}
  public Block getBlock(int x,int y,int z){int u=x-ox+2,v=z-oz+2;if(u<0||v<0||u>=772||v>=388)return Blocks.air;
   if(original&&road[u][v]&&y==height[u][v])return Blocks.stonebrick;
   Cell c=writesByCell.get(packed(x,y,z));if(c!=null)return material(c.id);
   return y<=natural[u][v]?Blocks.stone:y<=water[u][v]?Blocks.water:Blocks.air;
  }
  public int getBlockMetadata(int x,int y,int z){Cell c=writesByCell.get(packed(x,y,z));return original?0:c==null?0:c.meta;}
  public List getCollidingBoundingBoxes(Entity e,AxisAlignedBB box){List<AxisAlignedBB> out=new ArrayList<>();for(int x=(int)Math.floor(box.minX)-1;x<=(int)Math.floor(box.maxX)+1;x++)for(int y=(int)Math.floor(box.minY)-1;y<=(int)Math.floor(box.maxY)+1;y++)for(int z=(int)Math.floor(box.minZ)-1;z<=(int)Math.floor(box.maxZ)+1;z++){Block b=getBlock(x,y,z);if(b!=Blocks.air)b.addCollisionBoxesToList(this,x,y,z,box,out,e);}return out;}
  public boolean func_147470_e(AxisAlignedBB b){return false;}
  public boolean isSideSolid(int x,int y,int z,net.minecraftforge.common.util.ForgeDirection side,boolean fallback){return getBlock(x,y,z).isOpaqueCube();}
  boolean road(int u,int v){return u>=0&&v>=0&&u<768&&v<384&&road[u+2][v+2];}
 }
 static class E extends Entity {
  E(World w){super(w);setSize(.6F,1.8F);stepHeight=.5F;}
  protected void entityInit(){}protected void readEntityFromNBT(NBTTagCompound n){}protected void writeEntityToNBT(NBTTagCompound n){}
  protected void func_145775_I(){}protected boolean canTriggerWalking(){return false;}public boolean isWet(){return false;}
 }
 static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
 static boolean move(W w,int u,int v,int nu,int nv){E e=new E(w);e.setPosition(w.ox+u+.5,w.height[u+2][v+2]+1,w.oz+v+.5);e.onGround=true;for(int i=0;i<12;i++)e.moveEntity(0,-.1,0);return move(w,e,nu,nv);}
 static boolean move(W w,E e,int nu,int nv){double x=w.ox+nu+.5,z=w.oz+nv+.5;for(int i=0;i<24;i++){double dx=x-e.posX,dz=z-e.posZ;if(Math.abs(dx)+Math.abs(dz)<.005)break;e.moveEntity(Math.max(-.1,Math.min(.1,dx)),-.08,Math.max(-.1,Math.min(.1,dz)));}for(int i=0;i<12;i++)e.moveEntity(0,-.1,0);return Math.abs(e.posX-x)<.01&&Math.abs(e.posZ-z)<.01&&Math.abs(e.boundingBox.minY-w.height[nu+2][nv+2]-1)<.01&&w.getCollidingBoundingBoxes(e,e.boundingBox).isEmpty()&&!w.getCollidingBoundingBoxes(e,AxisAlignedBB.getBoundingBox(e.boundingBox.minX,e.boundingBox.minY-.02,e.boundingBox.minZ,e.boundingBox.maxX,e.boundingBox.minY-.001,e.boundingBox.maxZ)).isEmpty();}
 static boolean replay(W w,List<int[]> path){int[] a=path.get(0);E e=new E(w);e.setPosition(w.ox+a[0]+.5,w.height[a[0]+2][a[1]+2]+1,w.oz+a[1]+.5);e.onGround=true;for(int i=1;i<path.size();i++){int[] b=path.get(i);if(!move(w,e,b[0],b[1])){int x=w.ox+b[0],z=w.oz+b[1],h=w.height[b[0]+2][b[1]+2];System.out.println("MC_ROUTE_BLOCKED waypoint="+i+" target="+x+","+h+","+z+" actualFeet="+e.boundingBox.minY+" actualXZ="+e.posX+","+e.posZ+" floor="+w.getBlock(x,h,z).getUnlocalizedName()+" feetBlock="+w.getBlock(x,h+1,z).getUnlocalizedName()+" headBlock="+w.getBlock(x,h+2,z).getUnlocalizedName());return false;}}return true;}
 static byte[][][] cache;
 static boolean edge(W w,int u,int v,int d){if(cache[u][v][d]==0)cache[u][v][d]=(byte)(move(w,u,v,u+DIR[d][0],v+DIR[d][1])?1:2);return cache[u][v][d]==1;}
 static boolean commonCorridor(int u,int v,int nu,int nv,int x,int z){return Math.abs(x-u)<=7&&Math.abs(z-v)<=7||u%96<8&&nu%96<8&&u/96==nu/96&&x/96==u/96&&x%96<8||v%96<8&&nv%96<8&&v/96==nv/96&&z/96==v/96&&z%96<8||u>=680&&nu>=680&&u<760&&nu<760&&v>=104&&nv>=104&&v<280&&nv<280&&x>=680&&x<760&&z>=104&&z<280;}
 static List<int[]> detour(W w,int u,int v,int nu,int nv){ArrayDeque<int[]> q=new ArrayDeque<>();Map<Integer,Integer> parent=new HashMap<>();int start=u*384+v,target=nu*384+nv;parent.put(start,-1);q.add(new int[]{u,v});while(!q.isEmpty()){int[] a=q.remove();for(int d=0;d<4;d++){int x=a[0]+DIR[d][0],z=a[1]+DIR[d][1],key=x*384+z;if(!w.road(x,z)||!commonCorridor(u,v,nu,nv,x,z)||parent.containsKey(key)||!edge(w,a[0],a[1],d))continue;parent.put(key,a[0]*384+a[1]);if(key==target){List<int[]> path=new ArrayList<>();while(key>=0){path.add(new int[]{key/384,key%384});key=parent.get(key);}Collections.reverse(path);return path;}q.add(new int[]{x,z});}}return null;}
 static W world(RemasterSite s)throws Exception {
  require(s.roadVersion==1,"normal candidate must be version one");
  W w=(W)RemasterRuntimeCheck.u.allocateInstance(W.class);w.ox=s.x;w.oz=s.z;w.height=new int[772][388];w.natural=new int[772][388];w.water=new int[772][388];w.meta=new int[772][388];w.road=new boolean[772][388];w.writesByCell=new HashMap<>();w.authored=new HashMap<>();w.loaded=true;w.seed=s.seed;w.blocks=new HashMap<>();w.metas=new HashMap<>();w.tiles=new HashMap<>();
  RemasterRuntimeCheck.set(w,World.class,"provider",RemasterRuntimeCheck.u.allocateInstance(com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins.class));RemasterRuntimeCheck.set(w,World.class,"theProfiler",new Profiler());
  for(int u=-2;u<770;u++)for(int v=-2;v<386;v++){w.natural[u+2][v+2]=ProsperityTerrainProfile.heightAt(s.seed,s.x+u,s.z+v);w.water[u+2][v+2]=ChunkProviderProsperityRuins.naturalWaterTopAt(s.seed,s.x+u,s.z+v);w.height[u+2][v+2]=RemasterCityBridge.floorAt(s,s.x+u,s.z+v);w.road[u+2][v+2]=RemasterCityBridge.walkable(s,s.x+u,s.z+v);w.meta[u+2][v+2]=-1;}
  w.bridgeWrites=new HashMap<>();
  List<RemasterSite> plots=RemasterPlanner.savedCityPlots(s);require(plots.size()==28,"all immutable city plots required");
  for(RemasterSite plot:plots)for(int cx=plot.minX()>>4;cx<=plot.maxX()>>4;cx++)for(int cz=plot.minZ()>>4;cz<=plot.maxZ()>>4;cz++)RemasterWorldgen.geometry(plot,plot.plan(),(x,y,z,b,m,f)->{if(x<s.x-2||x>=s.x+770||z<s.z-2||z>=s.z+386)return true;Cell c=new Cell(b.toString(),m);w.authored.put(w.packed(x,y,z),c);w.writesByCell.put(w.packed(x,y,z),c);return true;},cx,cz);
  for(int cx=s.minX()>>4;cx<=s.maxX()>>4;cx++)for(int cz=s.minZ()>>4;cz<=s.maxZ()>>4;cz++){final int ownerX=cx,ownerZ=cz;RemasterWorldgen.geometry(s,null,(x,y,z,b,m,f)->{
   require((x>>4)==ownerX&&(z>>4)==ownerZ,"foreign owner");require(y>=1&&y<=254,"bridge world bounds");String id=b.toString();require(id.equals("minecraft:stonebrick")&&m==0||id.equals("minecraft:stone_brick_stairs")&&m>=0&&m<4||id.equals("minecraft:fence")&&m==0||id.equals("minecraft:air")&&m==0,"unsupported bridge block/half");
   Cell source=w.authored.get(w.packed(x,y,z));require(source==null||source.id.equals(id)&&source.meta==m,"bridge overwrites immutable source at "+x+","+y+","+z+": "+(source==null?"":source.id));
   Cell write=new Cell(id,m);w.bridgeWrites.put(w.packed(x,y,z),write);w.writesByCell.put(w.packed(x,y,z),write);if(id.equals("minecraft:stone_brick_stairs"))w.meta[x-s.x+2][z-s.z+2]=m;return true;},cx,cz);}
  // Compare every actual write in reverse owner order, including AIR, piers and fences.
  final int[] reverse={0};for(int cx=s.maxX()>>4;cx>=s.minX()>>4;cx--)for(int cz=s.maxZ()>>4;cz>=s.minZ()>>4;cz--){final int ownerX=cx,ownerZ=cz;RemasterWorldgen.geometry(s,null,(x,y,z,b,m,f)->{Cell c=w.writesByCell.get(w.packed(x,y,z));require((x>>4)==ownerX&&(z>>4)==ownerZ&&c!=null&&c.id.equals(b.toString())&&c.meta==m,"reverse chunk order changes bridge output");reverse[0]++;return true;},cx,cz);}require(reverse[0]>0,"no production bridge writes");
  for(Cell source:w.authored.values())material(source.id);
  return w;
 }
 static JsonObject structure(W w,RemasterSite s){
  int walk=0,rails=0,thin=0,piers=0,low=0,cuts=0,wet=0,maxLift=0;
  for(int u=0;u<768;u++)for(int v=0;v<384;v++){
   int x=s.x+u,z=s.z+v,h=RemasterCityBridge.floorAt(s,x,z);if(h==0)continue;int n=w.natural[u+2][v+2];maxLift=Math.max(maxLift,h-n);
   require(h>w.water[u+2][v+2],"bridge below real natural water at "+x+","+z);if(w.water[u+2][v+2]>=n)wet++;
   if(w.road(u,v)){walk++;require(w.getBlock(x,h,z)!=Blocks.air&&w.getBlock(x,h+1,z)==Blocks.air&&w.getBlock(x,h+2,z)==Blocks.air,"walk mask lacks actual supported headroom");}
   else {require(w.getBlock(x,h+1,z)==Blocks.fence,"nonwalk edge lacks real fence");rails++;}
   Cell source=w.authored.get(w.packed(x,h,z));if(source!=null&&!source.id.equals("minecraft:air"))continue;
   int supports=0;for(int y=n;y<h;y++){Cell c=w.bridgeWrites.get(w.packed(x,y,z));if(c!=null&&!c.id.equals("minecraft:air")){require(c.id.equals("minecraft:stonebrick"),"invalid pier material");supports++;}}
   if(h<n){require(n-h<=2&&w.water[u+2][v+2]<h,"native cut exceeds dry headroom bounds");for(int y=1;y<h;y++)require(!w.bridgeWrites.containsKey(w.packed(x,y,z)),"native cut adds unnecessary underfloor fill");cuts++;}
   if(h-n<=3){require(supports==Math.max(0,h-n),"near-ground bridge support gap at "+x+","+h+","+z);if(h>=n)low++;}
   else if(supports==1){require(w.getBlock(x,h-1,z)==Blocks.stonebrick,"thin deck missing underlayer");thin++;}
   else {require(supports==h-n,"pier is discontinuous or thick fill");piers++;}
  }
  require(walk>0&&rails>0,"missing walk interior or fence edges");if(maxLift>3)require(thin>0&&piers>0,"elevated spans require both thin decks and piers");
  JsonObject r=new JsonObject();r.addProperty("walkMaskColumns",walk);r.addProperty("fenceColumns",rails);r.addProperty("thinElevatedDeckColumns",thin);r.addProperty("fullHeightPierColumns",piers);r.addProperty("nearGroundSupportColumns",low);r.addProperty("nativeCutColumns",cuts);r.addProperty("realWetColumns",wet);r.addProperty("maxLift",maxLift);r.addProperty("actualSourceOverlayPreserved",true);return r;
 }
 static JsonArray connections(W w,RemasterSite city){
  List<RemasterCityBridge.Connection> all=RemasterCityBridge.connections(city);require(all.size()==28,"all 28 original plot entrances need bridge routes");JsonArray report=new JsonArray();
  for(RemasterCityBridge.Connection c:all){List<int[]> path=new ArrayList<>();for(int[] p:c.points){require(p[1]==w.height[p[0]-w.ox+2][p[2]-w.oz+2],"connection floor differs from emitted walk surface");path.add(new int[]{p[0]-w.ox,p[2]-w.oz});}int[] first=c.points.get(0);require(first[0]==c.plot.entryX()&&first[1]==c.plot.entryY()-1&&first[2]==c.plot.entryZ(),"connector changed authored entrance");boolean forward=replay(w,path);Collections.reverse(path);boolean backward=replay(w,path);JsonObject row=new JsonObject();row.addProperty("plot",c.plot.id());row.addProperty("forward",forward);row.addProperty("backward",backward);row.addProperty("points",path.size());report.add(row);require(forward&&backward,"actual MC plot entrance route blocked: "+c.plot.id());}return report;
 }
 static JsonObject dryRise(W w){
  for(int u=0;u<768;u++)for(int v=0;v<384;v++)if(w.road(u,v)&&w.water[u+2][v+2]<0)for(int d=0;d<4;d++){int x=u+DIR[d][0],z=v+DIR[d][1];if(!w.road(x,z)||w.water[x+2][z+2]>=0||w.height[x+2][z+2]!=w.height[u+2][v+2]+1)continue;w.original=true;boolean old=move(w,u,v,x,z);w.original=false;if(old)continue;boolean forward=move(w,u,v,x,z),backward=move(w,x,z,u,v);if(!forward||!backward)continue;JsonObject p=new JsonObject();p.addProperty("lowXYZ",(w.ox+u)+","+w.height[u+2][v+2]+","+(w.oz+v));p.addProperty("highXYZ",(w.ox+x)+","+w.height[x+2][z+2]+","+(w.oz+z));p.addProperty("oldBrickBlocked",true);p.addProperty("productionForwardAndReverseWalked",true);return p;}
  throw new AssertionError("missing real normal dry rise old-brick RED / production bidirectional GREEN representative");
 }
 static void savedV0(RemasterSite current){
  RemasterSite old=new RemasterSite(current.prefab,current.variant,current.seed,current.x,88,current.z,current.layout,0);RemasterSite read=RemasterSite.read(old.save());require(read.y==88&&read.roadVersion==0,"saved v0 anchor/version changed");int[] count={0};
  for(int cx=old.minX()>>4;cx<=old.maxX()>>4;cx++)for(int cz=old.minZ()>>4;cz<=old.maxZ()>>4;cz++){final int ox=cx,oz=cz;RemasterWorldgen.geometry(read,null,(x,y,z,b,m,f)->{require((x>>4)==ox&&(z>>4)==oz&&y==87&&b.toString().equals("minecraft:stonebrick")&&m==0,"v0 must remain savedY-1 single brick plane");count[0]++;return true;},cx,cz);}require(count[0]==60544,"v0 released street footprint changed");
 }
 static void startup()throws Exception {
  NativeTerrainFixture.initialize();Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);RemasterRuntimeCheck.u=(sun.misc.Unsafe)f.get(null);Class.forName("net.minecraft.init.Blocks");Constructor<BlockAir> air=BlockAir.class.getDeclaredConstructor();air.setAccessible(true);RemasterRuntimeCheck.setBlock("air",air.newInstance());RemasterRuntimeCheck.setBlock("stone",new BlockStone());Constructor<BlockStoneBrick> bc=BlockStoneBrick.class.getDeclaredConstructor();bc.setAccessible(true);RemasterRuntimeCheck.setBlock("stonebrick",bc.newInstance());Constructor<BlockStairs> sc=BlockStairs.class.getDeclaredConstructor(Block.class,int.class);sc.setAccessible(true);RemasterRuntimeCheck.setBlock("stone_brick_stairs",sc.newInstance(Blocks.stonebrick,0));RemasterRuntimeCheck.setBlock("fence",new BlockFence("planks",Material.wood));Constructor<BlockStaticLiquid> wc=BlockStaticLiquid.class.getDeclaredConstructor(Material.class);wc.setAccessible(true);RemasterRuntimeCheck.setBlock("water",wc.newInstance(Material.water));
  MATERIALS.put("minecraft:air",Blocks.air);MATERIALS.put("minecraft:stone",Blocks.stone);MATERIALS.put("minecraft:stonebrick",Blocks.stonebrick);MATERIALS.put("minecraft:stone_brick_stairs",Blocks.stone_brick_stairs);MATERIALS.put("minecraft:fence",Blocks.fence);
  BlocksGTSR.ruinDebris=new com.miaokatze.gtsr.common.dimension.prosperity.block.BlockRuinDebris();BlocksGTSR.ruinedCasing=new com.miaokatze.gtsr.common.dimension.prosperity.block.BlockRuinedCasing();BlocksGTSR.prosperityZenithLog=new BlockZenithLog("ProsperityZenithLog","gtsr:prosperity_zenith_log_side","gtsr:prosperity_zenith_log_top");BlocksGTSR.prosperityStone=new com.miaokatze.gtsr.common.blocks.BlockProsperityStone("ProsperityStone","gtsr:prosperity_stone");RuinsArchitecture.registerBlocks();RoyalArchitecture.registerBlocks();
  com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterRegistry.sealedChest=new com.miaokatze.gtsr.common.dimension.prosperity.encounter.BlockSealedChest();
  Field registry=RemasterBlocks.class.getDeclaredField("REGISTRY");registry.setAccessible(true);Map<String,RemasterBlock> blocks=(Map<String,RemasterBlock>)registry.get(null);
  for(Method factory:RemasterBlocks.class.getDeclaredMethods())if(factory.getName().matches("block[0-9]+")){factory.setAccessible(true);RemasterBlock block=(RemasterBlock)factory.invoke(null);blocks.put(block.id(),block);}
 }
 public static void main(String[] args)throws Exception {
  startup();JsonArray reports=new JsonArray();for(long seed:new long[]{20261001L,20261002L,20261003L}){
   RemasterSite city=null;outer:for(int gx=-12;gx<=12;gx++)for(int gz=-12;gz<=12;gz++)for(RemasterSite s:RemasterPlanner.cell(seed,0,gx,gz))if(s.layout.equals("city-grid")){city=s;break outer;}require(city!=null,"missing normal real city candidate seed="+seed);require(RemasterCityBridge.viable(city),"selected bridge not viable: "+RemasterCityBridge.failure(city));savedV0(city);W w=world(city);cache=new byte[768][384][4];JsonObject result=structure(w,city);result.add("plotEntranceConnections",connections(w,city));result.add("normalDryRiseOldRedNewGreen",dryRise(w));int links=0,direct=0,bypassed=0,failed=0;JsonArray failures=new JsonArray();
   for(int u=0;u<768;u++)for(int v=0;v<384;v++)if(w.road(u,v))for(int d:new int[]{0,2}){int nu=u+DIR[d][0],nv=v+DIR[d][1];if(!w.road(nu,nv))continue;links++;for(int direction:new int[]{d,d^1}){int a=direction==d?u:nu,b=direction==d?v:nv,c=direction==d?nu:u,e=direction==d?nv:v;if(edge(w,a,b,direction)){direct++;continue;}List<int[]> path=detour(w,a,b,c,e);if(path!=null){require(replay(w,path),"complete single-entity detour failed");bypassed++;}else{failed++;if(failures.size()<30)failures.add(new JsonPrimitive((w.ox+a)+","+w.height[a+2][b+2]+","+(w.oz+b)+" -> "+(w.ox+c)+","+w.height[c+2][e+2]+","+(w.oz+e)));}}}
   result.addProperty("seed",seed);result.addProperty("cityOrigin",city.x+","+city.y+","+city.z);result.addProperty("roadVersion",city.roadVersion);result.addProperty("adjacentWalkMaskLinks",links);result.addProperty("actualEntityDirections",direct+bypassed+failed);result.addProperty("directEntityDirections",direct);result.addProperty("localContinuousDetourDirections",bypassed);result.addProperty("failedDirections",failed);result.add("failures",failures);result.addProperty("reverseChunkOrderExact",true);result.addProperty("v0SavedPlaneVerified",true);reports.add(result);System.out.println(result);if(Arrays.asList(args).contains("--first"))break;
  }
  JsonObject out=new JsonObject();out.add("cities",reports);out.addProperty("historicalEMPTYCoordinates","old seed20261001 dry(-23952,70,-21039)->(-23952,71,-21038), west(-23951,64,-21360)->(-23952,65,-21360); historical only, never normal PASS");out.addProperty("fixtureAdaptations","procedural exact native solid/water columns; actual production bridge writes and immutable source plot overlay; real vanilla stair/fence collision and Entity.moveEntity .5step; callbacks disabled; no autojump/movement adaptation");java.nio.file.Files.write(java.nio.file.Paths.get("temp/city-road-collision.json"),new GsonBuilder().setPrettyPrinting().create().toJson(out).getBytes(java.nio.charset.StandardCharsets.UTF_8));for(JsonElement row:reports)require(row.getAsJsonObject().get("failedDirections").getAsInt()==0,"road walk-mask graph has inaccessible adjacent endpoints");
 }
}
