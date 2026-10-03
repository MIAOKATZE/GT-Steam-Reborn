import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.*;
import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.architecture.*;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.*;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.*;

/** Independent geometry, real Block collision/ray tracing and production node authority proof. */
public final class SiteControllerCollisionCheck {
 static sun.misc.Unsafe u;
 static void set(Object o,Class<?> type,String name,Object value)throws Exception {
  Field f=type.getDeclaredField(name);f.setAccessible(true);u.putObject(o,u.objectFieldOffset(f),value);
 }
 static void block(String name,Block b)throws Exception {
  Field f=Blocks.class.getField(name);u.putObject(u.staticFieldBase(f),u.staticFieldOffset(f),b);
 }
 static class W extends World {
  Map<String,Block> blocks;Map<String,Integer> metas;Map<String,TileEntity> tiles;long seed;
  W(){super((ISaveHandler)null,(String)null,(WorldProvider)null,(WorldSettings)null,(Profiler)null);}
  String key(int x,int y,int z){return x+","+y+","+z;}
  public Block getBlock(int x,int y,int z){return blocks.getOrDefault(key(x,y,z),Blocks.air);}
  public int getBlockMetadata(int x,int y,int z){return metas.getOrDefault(key(x,y,z),0);}
  public TileEntity getTileEntity(int x,int y,int z){return tiles.get(key(x,y,z));}
  public boolean blockExists(int x,int y,int z){return y>=0&&y<256;}
  public Entity getEntityByID(int id){return null;}
  public int getActualHeight(){return 256;}
  public IChunkProvider getChunkProvider(){throw new AssertionError("Physical fixture must never load/provide chunks");}
  public boolean setBlock(int x,int y,int z,Block b,int m,int flags){
   if(getBlock(x,y,z)==b&&getBlockMetadata(x,y,z)==m)return false;
   blocks.put(key(x,y,z),b);metas.put(key(x,y,z),m);tiles.remove(key(x,y,z));
   if(b.hasTileEntity(m)){TileEntity t=b.createTileEntity(this,m);if(t!=null){t.setWorldObj(this);t.xCoord=x;t.yCoord=y;t.zCoord=z;tiles.put(key(x,y,z),t);}}return true;
  }
  public boolean setBlockMetadataWithNotify(int x,int y,int z,int m,int flags){metas.put(key(x,y,z),m);return true;}
  public boolean isSideSolid(int x,int y,int z,net.minecraftforge.common.util.ForgeDirection s,boolean fallback){return getBlock(x,y,z).isSideSolid(this,x,y,z,s);}
  public boolean isSideSolid(int x,int y,int z,net.minecraftforge.common.util.ForgeDirection s){return isSideSolid(x,y,z,s,false);}
  public List getCollidingBoundingBoxes(Entity e,AxisAlignedBB box){return collisions(this,box);}
  public List getEntitiesWithinAABB(Class type,AxisAlignedBB box){return Collections.emptyList();}
  public List getEntitiesWithinAABBExcludingEntity(Entity e,AxisAlignedBB box){return Collections.emptyList();}
  public boolean isAnyLiquid(AxisAlignedBB box){return false;}
  public boolean checkNoEntityCollision(AxisAlignedBB box){return true;}
  public void markBlockForUpdate(int x,int y,int z){}
  public boolean func_147451_t(int x,int y,int z){return true;}
  public void markTileEntityChunkModified(int x,int y,int z,TileEntity t){}
  public void notifyBlocksOfNeighborChange(int x,int y,int z,Block b){}
  public long getTotalWorldTime(){return 0;}
  public long getSeed(){return seed;}
  protected IChunkProvider createChunkProvider(){throw new AssertionError("No ChunkProvider");}
  protected int func_152379_p(){return 0;}
 }
 static class P extends EntityPlayerMP {
  NBTTagCompound personal;
  P(){super(null,null,null,null);}
  public boolean isEntityAlive(){return true;}
  public double getDistanceSq(double x,double y,double z){return (posX-x)*(posX-x)+(posY-y)*(posY-y)+(posZ-z)*(posZ-z);}
  public NBTTagCompound getEntityData(){if(personal==null)personal=new NBTTagCompound();return personal;}
  public void addChatMessage(IChatComponent m){}
 }
 static W world()throws Exception {
  W w=(W)u.allocateInstance(W.class);w.blocks=new HashMap<>();w.metas=new HashMap<>();w.tiles=new HashMap<>();w.seed=7717;
  set(w,World.class,"perWorldStorage",new MapStorage((ISaveHandler)null));
  set(w,World.class,"provider",u.allocateInstance(WorldProviderProsperityRuins.class));
  set(w,World.class,"rand",new Random(w.seed));set(w,World.class,"theProfiler",new Profiler());
  set(w,World.class,"loadedEntityList",new ArrayList<>());return w;
 }
 static List<AxisAlignedBB> collisions(W w,AxisAlignedBB box){
  List<AxisAlignedBB> out=new ArrayList<>();
  for(int x=(int)Math.floor(box.minX)-1;x<=(int)Math.floor(box.maxX)+1;x++)
   for(int y=(int)Math.floor(box.minY)-1;y<=(int)Math.floor(box.maxY)+1;y++)
    for(int z=(int)Math.floor(box.minZ)-1;z<=(int)Math.floor(box.maxZ)+1;z++)
     w.getBlock(x,y,z).addCollisionBoxesToList(w,x,y,z,box,out,null);
  return out;
 }
 static double[] vector(JsonArray a,RemasterSite s){return new double[]{a.get(0).getAsDouble()+s.x,a.get(1).getAsDouble()+s.y,a.get(2).getAsDouble()+s.z};}
 static void require(boolean b,String detail){if(!b)throw new AssertionError(detail);}
 static void initialize()throws Exception {
  Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);u=(sun.misc.Unsafe)uf.get(null);
  Class.forName("net.minecraft.init.Blocks");Constructor<BlockAir> ac=BlockAir.class.getDeclaredConstructor();ac.setAccessible(true);block("air",ac.newInstance());block("stone",new BlockStone());
  BlocksGTSR.ruinDebris=new com.miaokatze.gtsr.common.dimension.prosperity.block.BlockRuinDebris();
  BlocksGTSR.ruinedCasing=new com.miaokatze.gtsr.common.dimension.prosperity.block.BlockRuinedCasing();
  BlocksGTSR.prosperityStone=new com.miaokatze.gtsr.common.blocks.BlockProsperityStone("ProsperityStone","gtsr:prosperity_stone");
  BlocksGTSR.prosperityZenithLog=new BlockZenithLog("ProsperityZenithLog","gtsr:prosperity_zenith_log_side","gtsr:prosperity_zenith_log_top");
  BlocksGTSR.prosperityJadeLeaves=new com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityCanopyLeaves("ProsperityJadeLeaves","gtsr:prosperity_jade_leaves_side","gtsr:prosperity_jade_leaves_top");
  RuinsArchitecture.registerBlocks();RoyalArchitecture.registerBlocks();ForgottenLakeEncounterRegistry.sealedChest=new BlockSealedChest();
  Field rf=RemasterBlocks.class.getDeclaredField("REGISTRY");rf.setAccessible(true);Map registry=(Map)rf.get(null);
  for(Method f:RemasterBlocks.class.getDeclaredMethods())if(f.getName().matches("block[0-9]+")){f.setAccessible(true);RemasterBlock b=(RemasterBlock)f.invoke(null);registry.put(b.id(),b);}
  RemasterBlocks.setInteractionHandler(new RemasterBlocks.InteractionHandler(){
   public boolean activate(World w,int x,int y,int z,EntityPlayer p){return RemasterRuntime.activateNode(w,x,y,z,p);}
   public TileEntity createTileEntity(World w,int meta,String id){return new TileRemasterNode();}
   public float hardness(World w,int x,int y,int z,float fallback){return fallback;}
  });
 }
 static void physical(RemasterSite s,JsonObject node,JsonObject approach,JsonObject evidence)throws Exception {
  W w=world();RemasterData.get(w).register(s);double[] f=vector(approach.getAsJsonArray("feet"),s),eye=vector(approach.getAsJsonArray("eye"),s),target=vector(approach.getAsJsonArray("target"),s);
  // Metadata feet are voxel coordinates; player stands at the voxel centre.
  double px=f[0]+.5,py=f[1],pz=f[2]+.5;
  int x=s.x+node.get("x").getAsInt(),y=s.y+node.get("y").getAsInt(),z=s.z+node.get("z").getAsInt();
  int minX=(int)Math.floor(Math.min(px-.3,Math.min(eye[0],target[0])))-2,maxX=(int)Math.floor(Math.max(px+.3,Math.max(eye[0],target[0])))+2;
  int minZ=(int)Math.floor(Math.min(pz-.3,Math.min(eye[2],target[2])))-2,maxZ=(int)Math.floor(Math.max(pz+.3,Math.max(eye[2],target[2])))+2;
  JsonArray chunks=new JsonArray();
  for(int cx=minX>>4;cx<=maxX>>4;cx++)for(int cz=minZ>>4;cz<=maxZ>>4;cz++){
   RemasterWorldgen.geometry(s,s.plan(),(xx,yy,zz,b,m,flags)->{
    String key=b.toString()+"#"+m;TileEntity before=w.getTileEntity(xx,yy,zz);
    boolean wrote=w.setBlock(xx,yy,zz,RemasterRuntime.resolve(key),RemasterRuntime.meta(key),flags);
    if(wrote)RemasterRuntime.recordGeneratedTile(w,s,xx,yy,zz,before);return wrote;
   },cx,cz);JsonArray c=new JsonArray();c.add(new JsonPrimitive(cx));c.add(new JsonPrimitive(cz));chunks.add(c);
  }
  evidence.add("ownerChunks",chunks);evidence.addProperty("actualBlockCells",w.blocks.size());
  require(w.getBlock(x,y,z)==RemasterRuntime.resolve(node.get("block").getAsString()),"controller missing at "+x+","+y+","+z);
  require(RemasterRuntime.installNode(w,s,node),"production installNode rejected "+x+","+y+","+z);
  TileEntity t=w.getTileEntity(x,y,z);require(t instanceof TileRemasterNode,"controller tile missing");TileRemasterNode tile=(TileRemasterNode)t;
  require(tile.siteId.equals(s.id())&&tile.nodeId.equals("site-puzzle-controller")&&tile.role.equals("control"),"wrong tile bind");
  AxisAlignedBB body=AxisAlignedBB.getBoundingBox(px-.3,py+0.000001,pz-.3,px+.3,py+1.8,pz+.3);
  List<AxisAlignedBB> bodyBoxes=collisions(w,body);evidence.addProperty("bodyCollisionBoxes",bodyBoxes.size());require(bodyBoxes.isEmpty(),".6x1.8 body collision at "+px+","+py+","+pz+": "+bodyBoxes);
  AxisAlignedBB support=AxisAlignedBB.getBoundingBox(px-.3,py-.02,pz-.3,px+.3,py-.000001,pz+.3);List<AxisAlignedBB> supportBoxes=collisions(w,support);
  boolean plane=false;for(AxisAlignedBB b:supportBoxes)if(Math.abs(b.maxY-py)<.000001)plane=true;
  evidence.addProperty("supportCollisionBoxes",supportBoxes.size());require(plane,"no actual support plane at feet Y="+py+": "+supportBoxes);
  double[] floor=vector(approach.getAsJsonObject("floor").getAsJsonArray("at"),s);String floorKey=approach.getAsJsonObject("floor").get("material").getAsString();
  require(w.getBlock((int)floor[0],(int)floor[1],(int)floor[2])==RemasterRuntime.resolve(floorKey)&&w.getBlockMetadata((int)floor[0],(int)floor[1],(int)floor[2])==RemasterRuntime.meta(floorKey),"declared floor differs from production geometry");
  require(Math.abs(eye[0]-px)<1e-8&&Math.abs(eye[1]-py-1.62)<1e-8&&Math.abs(eye[2]-pz)<1e-8,"declared eye differs from standing player");
  double distance=Math.sqrt((eye[0]-target[0])*(eye[0]-target[0])+(eye[1]-target[1])*(eye[1]-target[1])+(eye[2]-target[2])*(eye[2]-target[2]));require(distance<=8,"ray exceeds 8 blocks");
  MovingObjectPosition hit=w.func_147447_a(Vec3.createVectorHelper(eye[0],eye[1],eye[2]),Vec3.createVectorHelper(target[0],target[1],target[2]),false,false,false);
  evidence.addProperty("rayDistance",distance);evidence.addProperty("rayHit",hit==null?"null":hit.blockX+","+hit.blockY+","+hit.blockZ);
  require(hit!=null&&hit.blockX==x&&hit.blockY==y&&hit.blockZ==z,"actual World.rayTraceBlocks fails controller: "+(hit==null?"null":hit.blockX+","+hit.blockY+","+hit.blockZ));
  P player=(P)u.allocateInstance(P.class);player.worldObj=w;player.posX=px;player.posY=py;player.posZ=pz;player.capabilities=new PlayerCapabilities();
  require(RemasterRuntime.valid(player,tile),"production validity rejects real-distance player");
  require(RemasterRuntime.action(player,tile,0),"field action rejected");
  require(RemasterData.get(w).state(s.id()).getCompoundTag("site-puzzle").getInteger("field0")==1,"control action did not reach site-puzzle stateKey");
  evidence.addProperty("physicalAndActionPassed",true);
 }
 public static void main(String[] args)throws Exception {
  initialize();JsonArray rows=new JsonArray();int ordinary=0,derived=0,failed=0;
  Method stateKey=RemasterRuntime.class.getDeclaredMethod("stateKey",TileRemasterNode.class);stateKey.setAccessible(true);
  for(String id:RemasterCatalog.ids())for(int v=0;v<RemasterCatalog.variants(id);v++){
   if(id.equals("fiction_expansion_project")||id.equals("forgotten_lake_court"))continue;ordinary++;
   RemasterSite s=new RemasterSite(id,v,7717,40003,60,40007);JsonObject row=new JsonObject();row.addProperty("prefab",id+"-v"+v);rows.add(row);
   try{
    // Runtime.nodes has already applied final coordinate ownership precedence.
    List<JsonObject> controls=new ArrayList<>();for(JsonObject n:RemasterRuntime.nodes(s))if(n.get("role").getAsString().equals("control"))controls.add(n);
    require(!controls.isEmpty(),"no final effective ordinary control");
    for(JsonObject n:controls){TileRemasterNode t=new TileRemasterNode();t.role="control";t.nodeId=n.get("id").getAsString();require(stateKey.invoke(null,t).equals("site-puzzle"),"ordinary control stateKey mismatch");}
    row.addProperty("effectiveOrdinaryControls",controls.size());row.addProperty("stateKey","site-puzzle");
    JsonObject m=s.plan().metadata;if(m.has("productionSiteController")){
     derived++;JsonObject descriptor=m.getAsJsonObject("productionSiteController"),node=null;
     for(JsonObject n:controls)if(n.get("id").getAsString().equals(descriptor.get("id").getAsString()))node=n;
     require(node!=null,"derived controller lost effective coordinate ownership");JsonArray approaches=m.getAsJsonArray("productionInteractionApproaches");require(approaches!=null&&approaches.size()==1,"missing derived approach");
     row.add("controller",descriptor);row.add("approach",approaches.get(0));physical(s,node,approaches.get(0).getAsJsonObject(),row);
    }else row.addProperty("existingControllerSemanticProofOnly",true);
   }catch(Throwable e){failed++;row.addProperty("failure",e.toString());System.err.println(id+"-v"+v+" "+e);}
  }
  JsonObject report=new JsonObject();report.addProperty("ordinaryVariants",ordinary);report.addProperty("newControllers",derived);report.addProperty("failed",failed);report.addProperty("passed",ordinary==264&&derived==198&&failed==0);report.addProperty("physics","real Block.addCollisionBoxesToList, support plane, World.rayTraceBlocks, production installNode/valid/action; no ChunkProvider");report.add("rows",rows);
  Files.write(Paths.get(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));
  System.out.println("ordinary="+ordinary+" derived="+derived+" failed="+failed);require(ordinary==264&&derived==198&&failed==0,"site controller physical/semantic verification failed");
 }
}
