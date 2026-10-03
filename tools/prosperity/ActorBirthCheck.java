import java.util.*;import java.nio.file.*;import com.google.gson.*;import net.minecraft.block.*;import net.minecraft.entity.*;import net.minecraft.init.Blocks;import net.minecraft.util.*;import net.minecraft.world.*;import net.minecraft.profiler.Profiler;import com.miaokatze.gtsr.common.dimension.prosperity.*;import com.miaokatze.gtsr.common.dimension.prosperity.echo.*;import com.miaokatze.gtsr.common.dimension.prosperity.remaster.*;import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;

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

/** Offline loaded storage with real Minecraft block collision shapes. No chunk loading. */
final class ActorBirthFixture {
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
  public int getActualHeight(){return 256;}
  public Entity getEntityByID(int id){return null;}
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

}

/** Fresh production RemasterSpawn over one sparse actor region; no support synthesis. */
public class ActorBirthCheck {
 static class W extends ActorBirthFixture.W {
  boolean nativeGround;int minX,maxX,minZ,maxZ;Set<String> sourceSolid=new HashSet<>();
  public Block getBlock(int x,int y,int z){String k=key(x,y,z);if(blocks.containsKey(k))return blocks.get(k);if(!nativeGround)return Blocks.air;int h=ProsperityTerrainProfile.heightAt(seed,x,z);if(y<=h)return com.miaokatze.gtsr.common.blocks.BlocksGTSR.prosperityStone;return y<=ChunkProviderProsperityRuins.naturalWaterTopAt(seed,x,z)?Blocks.water:Blocks.air;}
  public boolean spawnEntityInWorld(Entity e){return true;}
 }
 static W world()throws Exception {W w=(W)ActorBirthFixture.u.allocateInstance(W.class);w.blocks=new HashMap<>();w.metas=new HashMap<>();w.tiles=new HashMap<>();w.sourceSolid=new HashSet<>();w.seed=20261001L;ActorBirthFixture.set(w,World.class,"provider",ActorBirthFixture.u.allocateInstance(WorldProviderProsperityRuins.class));ActorBirthFixture.set(w,World.class,"rand",new Random(1));ActorBirthFixture.set(w,World.class,"theProfiler",new Profiler());ActorBirthFixture.set(w,World.class,"loadedEntityList",new ArrayList<>());return w;}
 public static void main(String[]a)throws Exception {
  boolean actual=a.length>0&&!a[0].equals("geometry"),resonant=actual&&a[0].equals("resonant"),residual=actual&&a[0].equals("residual");
  ActorBirthFixture.initialize();java.lang.reflect.Constructor<BlockStaticLiquid> water=BlockStaticLiquid.class.getDeclaredConstructor(net.minecraft.block.material.Material.class);water.setAccessible(true);ActorBirthFixture.block("water",water.newInstance(net.minecraft.block.material.Material.water));NativeTerrainFixture.initialize();JsonObject report=new JsonObject();JsonArray rows=new JsonArray();int prefabs=0,total=0,ok=0,reject=0,skip=0,major=0,factory=0;Map<String,Integer> failures=new TreeMap<>();
  for(String id:RemasterCatalog.ids())for(int v=0;v<RemasterCatalog.variants(id);v++){
   if(actual&&(residual?!(id.equals("weaving_mill")&&(v==0||v==2)||id.equals("shore_crane")&&v==1||id.equals("weighbridge")&&v==1||id.equals("gear_garden")&&(v==1||v==2)||id.equals("city_machine_plinth")&&(v==0||v==1)):resonant?!id.equals("resonant_station"):(!id.equals("subsided_factory")||v!=2)))continue;prefabs++;JsonObject d=RemasterCatalog.descriptor(id,v);int sy=Math.max(40,2-d.get("yMin").getAsInt());sy=Math.min(sy,253-d.get("yMax").getAsInt());if(id.equals("subsided_factory"))sy=150;
   RemasterSite s=new RemasterSite(id,v,20261001L,actual?-46281:0,sy,actual?-187244:0);if(resonant||residual){int[][] pos={{-1899,-3988},{-955,-3924},{-1307,4860}};java.lang.reflect.Method anchor=RemasterPlanner.class.getDeclaredMethod("anchor",String.class,long.class,long.class,int.class,int.class);anchor.setAccessible(true);RemasterSite found=null;for(int attempt=0;attempt<64&&found==null;attempt++){int ox=attempt==0?0:(attempt%8-4)*16,oz=attempt==0?0:(attempt/8-4)*16;found=(RemasterSite)anchor.invoke(null,id,20261001L,((long)v)<<12,(resonant?pos[v][0]:30000)+ox+d.getAsJsonArray("min").get(0).getAsInt(),(resonant?pos[v][1]:-30000)+oz+d.getAsJsonArray("min").get(2).getAsInt());}if(found==null)throw new AssertionError("No legal resonant fixture anchor v"+v);s=found;sy=s.y;}if(actual&&!resonant&&!residual){boolean cell=false,near=false;for(RemasterSite q:RemasterPlanner.cell(s.seed,0,-23,-92))if(q.id().equals(s.id())&&q.y==s.y)cell=true;for(RemasterSite q:RemasterPlanner.near(s.seed,s.entryX()>>4,s.entryZ()>>4))if(q.id().equals(s.id()))near=true;if(!cell||!near)throw new AssertionError("known factory must remain selected in production cell and near index");}JsonArray spawns=RemasterRuntime.array(s.plan().metadata,"spawns");
   for(int i=0;i<spawns.size();i++){
    total++;JsonObject n=spawns.get(i).getAsJsonObject(),row=new JsonObject();String code=RemasterRuntime.string(n,"code","");row.addProperty("sourceId",id);row.addProperty("variant",v);row.addProperty("index",i);row.addProperty("id",RemasterRuntime.string(n,"id",""));row.addProperty("code",code);row.addProperty("module",RemasterRuntime.string(n,"module",""));row.addProperty("role",RemasterRuntime.string(n,"role",""));row.addProperty("sourceSHA256",d.get("sourceSha256").getAsString());
    int x=s.x+RemasterRuntime.integer(n,"x",0),y=sy+RemasterRuntime.integer(n,"y",0),z=s.z+RemasterRuntime.integer(n,"z",0);row.add("relativeXYZ",new Gson().toJsonTree(new int[]{x-s.x,y-sy,z-s.z}));row.add("fixtureXYZ",new Gson().toJsonTree(new int[]{x,y,z}));
    if(code.equals("dr-09")||code.equals("dc-10")||code.isEmpty()||n.has("spawn")&&!n.get("spawn").getAsBoolean()){row.addProperty("status","productionSkipped");skip++;rows.add(row);continue;}
    EchoKind kind=EchoKind.byCode(code);row.addProperty("width",kind.width);row.addProperty("height",kind.height);row.addProperty("flies",kind.flies());if(id.equals("subsided_factory"))factory++;
    W w=world();int radius=(int)Math.ceil(kind.width/2)+3;if(residual)radius+=4;if(actual&&!resonant&&!residual&&i==0)radius+=8;w.minX=x-radius;w.maxX=x+radius;w.minZ=z-radius;w.maxZ=z+radius;if(resonant&&i==0){w.minX=s.x+78;w.maxX=s.x+160;w.minZ=s.z+14;w.maxZ=s.z+68;}
    BlockSink sink=(xx,yy,zz,b,m,f)->{if(xx<w.minX||xx>w.maxX||zz<w.minZ||zz>w.maxZ||yy<y-34||yy>y+kind.height+3)return true;String k=w.key(xx,yy,zz),key=b.toString()+"#"+m;w.blocks.put(k,RemasterRuntime.resolve(key));w.metas.put(k,RemasterRuntime.meta(key));if(w.blocks.get(k)!=Blocks.air)w.sourceSolid.add(k);else w.sourceSolid.remove(k);return true;};
    for(int cx=w.minX>>4;cx<=w.maxX>>4;cx++)for(int cz=w.minZ>>4;cz<=w.maxZ>>4;cz++){RemasterTerrain.build(s,sink,cx,cz);RemasterWorldgen.geometry(s,s.plan(),sink,cx,cz);}
    w.nativeGround=actual;EntityOldEcho e=RemasterSpawn.spawn(w,kind,s.id(),x+.5,y,z+.5,i,false);row.addProperty("sourceGeometryAdmission",e!=null);if(e!=null){row.addProperty("admittedY",e.posY);ok++;}else{reject++;failures.put(id,failures.getOrDefault(id,0)+1);}
    JsonArray candidates=new JsonArray();int colliding=0;for(int step=0;step<(kind.flies()?1:33);step++){EntityOldEcho probe=new EntityOldEcho(w);probe.initializeEcho(kind,s.id(),x+.5,y-step,z+.5,false);AxisAlignedBB box=probe.boundingBox;boolean collision=!w.getCollidingBoundingBoxes(probe,box).isEmpty();if(collision)colliding++;JsonObject c=new JsonObject();c.addProperty("y",y-step);c.addProperty("collision",collision);if(collision){JsonArray hits=new JsonArray();for(String k:w.sourceSolid){String[] q=k.split(",");int bx=Integer.parseInt(q[0]),by=Integer.parseInt(q[1]),bz=Integer.parseInt(q[2]);List<AxisAlignedBB> boxes=new ArrayList<>();w.getBlock(bx,by,bz).addCollisionBoxesToList(w,bx,by,bz,box,boxes,null);if(!boxes.isEmpty()){JsonObject h=new JsonObject();h.addProperty("xyz",k);h.addProperty("block",w.getBlock(bx,by,bz).getClass().getName());h.addProperty("meta",w.getBlockMetadata(bx,by,bz));hits.add(h);}}c.add("sourceCollisionCells",hits);JsonArray naturalCells=new JsonArray();for(int bx=(int)Math.floor(box.minX);bx<=(int)Math.floor(box.maxX-.0001);bx++)for(int by=(int)Math.floor(box.minY);by<=(int)Math.floor(box.maxY-.0001);by++)for(int bz=(int)Math.floor(box.minZ);bz<=(int)Math.floor(box.maxZ-.0001);bz++){if(naturalCells.size()>=8)continue;String nk=w.key(bx,by,bz);if(!w.blocks.containsKey(nk)&&w.getBlock(bx,by,bz)!=Blocks.air){JsonObject h=new JsonObject();h.addProperty("xyz",nk);h.addProperty("block","native:ProsperityStone");naturalCells.add(h);}}c.add("nativeCollisionSample",naturalCells);}int missing=0;for(int bx=(int)Math.floor(box.minX);bx<=(int)Math.floor(box.maxX-.0001);bx++)for(int bz=(int)Math.floor(box.minZ);bz<=(int)Math.floor(box.maxZ-.0001);bz++)if(!w.isSideSolid(bx,(int)Math.floor(box.minY)-1,bz,net.minecraftforge.common.util.ForgeDirection.UP))missing++;c.addProperty("missingSupportCells",missing);if(missing>0){JsonArray bad=new JsonArray();for(int bx=(int)Math.floor(box.minX);bx<=(int)Math.floor(box.maxX-.0001);bx++)for(int bz=(int)Math.floor(box.minZ);bz<=(int)Math.floor(box.maxZ-.0001);bz++){int by=(int)Math.floor(box.minY)-1;if(!w.isSideSolid(bx,by,bz,net.minecraftforge.common.util.ForgeDirection.UP)){JsonObject t=new JsonObject();t.addProperty("xyz",w.key(bx,by,bz));t.addProperty("blockClass",w.getBlock(bx,by,bz).getClass().getName());t.addProperty("meta",w.getBlockMetadata(bx,by,bz));bad.add(t);}}c.add("unsupportedFootprintCells",bad);}candidates.add(c);}
    if(resonant&&i==0&&e==null){JsonArray positions=new JsonArray();for(int nx=84;nx<=154;nx++)for(int nz=18;nz<=52;nz++){EntityOldEcho found=RemasterSpawn.spawn(w,kind,s.id(),s.x+nx+.5,y,s.z+nz+.5,i,false);if(found==null)continue;AxisAlignedBB box=found.boundingBox;boolean sourceFloor=true;for(int bx=(int)Math.floor(box.minX);bx<=(int)Math.floor(box.maxX-.0001);bx++)for(int bz=(int)Math.floor(box.minZ);bz<=(int)Math.floor(box.maxZ-.0001);bz++)if(!w.sourceSolid.contains(w.key(bx,(int)Math.floor(box.minY)-1,bz)))sourceFloor=false;if(sourceFloor){JsonObject q=new JsonObject();q.add("sourcePose",new Gson().toJsonTree(new int[]{nx,y-s.y,nz}));q.addProperty("admittedWorldY",found.posY);q.addProperty("distanceSquared",(nx-(x-s.x))*(nx-(x-s.x))+(nz-(z-s.z))*(nz-(z-s.z)));positions.add(q);}}row.add("sameSourcePlatformCandidates",positions);}
    if(residual&&e==null){JsonArray positions=new JsonArray();for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){EntityOldEcho found=RemasterSpawn.spawn(w,kind,s.id(),x+dx+.5,y,z+dz+.5,i,false);if(found==null)continue;AxisAlignedBB box=found.boundingBox;boolean sourceFloor=true;for(int bx=(int)Math.floor(box.minX);bx<=(int)Math.floor(box.maxX-.0001);bx++)for(int bz=(int)Math.floor(box.minZ);bz<=(int)Math.floor(box.maxZ-.0001);bz++)if(!w.sourceSolid.contains(w.key(bx,(int)Math.floor(box.minY)-1,bz)))sourceFloor=false;if(sourceFloor){JsonObject q=new JsonObject();q.add("sourcePose",new Gson().toJsonTree(new int[]{x-s.x+dx,y-s.y,z-s.z+dz}));q.addProperty("admittedWorldY",found.posY);q.addProperty("distanceSquared",dx*dx+dz*dz);positions.add(q);}}row.add("nearbySourcePlatformCandidates",positions);}
    if(resonant&&i==0){EntityOldEcho proposed=RemasterSpawn.spawn(w,kind,s.id(),s.x+132.5,y,s.z+48.5,i,false);JsonObject q=new JsonObject();q.add("sourcePose",new Gson().toJsonTree(new int[]{132,y-s.y,48}));q.addProperty("admitted",proposed!=null);if(proposed!=null){q.addProperty("admittedWorldY",proposed.posY);q.addProperty("admittedSourceY",proposed.posY-s.y);AxisAlignedBB box=proposed.boundingBox;int sourceSupport=0;for(int bx=(int)Math.floor(box.minX);bx<=(int)Math.floor(box.maxX-.0001);bx++)for(int bz=(int)Math.floor(box.minZ);bz<=(int)Math.floor(box.maxZ-.0001);bz++)if(w.sourceSolid.contains(w.key(bx,(int)Math.floor(box.minY)-1,bz)))sourceSupport++;q.addProperty("sourceSupportCells",sourceSupport);}row.add("fixedPlatformPoseProbe",q);}
    boolean confirmed=e==null&&colliding==(kind.flies()?1:33);row.addProperty("confirmedGeometryBlocker",confirmed);if(confirmed)major++;if(e==null)row.add("candidateDiagnostics",candidates);if(actual&&!resonant&&!residual&&i==0){JsonArray near=new JsonArray();for(boolean nat:new boolean[]{false,true}){w.nativeGround=nat;int tried=0;JsonArray allowed=new JsonArray();for(int dx=-8;dx<=8;dx++)for(int dz=-8;dz<=8;dz++){tried++;EntityOldEcho q=RemasterSpawn.spawn(w,kind,s.id(),x+dx+.5,y,z+dz+.5,i,false);if(q!=null)allowed.add(new Gson().toJsonTree(new double[]{dx,q.posY,dz}));}JsonObject item=new JsonObject();item.addProperty("native",nat);item.addProperty("tested",tried);item.add("admittedOffsetsAndY",allowed);near.add(item);}row.add("nearbyOffsetProof",near);w.nativeGround=true;}
    w.nativeGround=true;EntityOldEcho natural=RemasterSpawn.spawn(w,kind,s.id(),x+.5,y,z+.5,i,false);row.addProperty("syntheticNativeAdmission",natural!=null);row.addProperty("actualPlannedAnchorVerified",actual&&!resonant&&!residual);row.addProperty("legalProductionAnchorVerified",actual);rows.add(row);
   }System.out.println("PREFAB "+id+" v"+v+" total="+total+" reject="+reject);
  }
  report.addProperty("prefabs",prefabs);report.addProperty("plannedSpawns",total);report.addProperty("sourceGeometryAdmitted",ok);report.addProperty("sourceGeometryRejected",reject);report.addProperty("productionSkipped",skip);report.addProperty("productionSkipRules","empty code, spawn=false, dr-09, dc-10; runtimeEnabled is not consulted by production Worldgen");report.addProperty("factoryTested",factory);report.addProperty("confirmedGeometryBlockers",major);report.addProperty("actualPlannedAnchorUnknown",actual&&!resonant&&!residual?0:total-skip);report.addProperty("mode",resonant?"normal-legal-resonant-anchor":residual?"normal-legal-residual-anchor":actual?"normal-known-selected-factory-anchor":"synthetic-source-geometry");report.addProperty("plannerCellSelectionVerified",actual&&!resonant&&!residual);report.addProperty("scope",actual?"normal6 production legal anchor + inclusive heightAt geology + naturalWaterTopAt water + sparse production AIR/geometry; independent entity admissions; surface layers, grotto, decoration, neighbor buildings unknown":"all268 production prefab geometry with actual RemasterSpawn/MC collision and synthetic vertical anchor; source geometry + AIR background; no actual normal world coverage");report.add("failuresByPrefab",new Gson().toJsonTree(failures));JsonArray overlaps=new JsonArray();for(int ii=0;ii<rows.size();ii++){JsonObject l=rows.get(ii).getAsJsonObject();if(!l.has("admittedY")||l.get("sourceId").getAsString().equals("forgotten_lake_court"))continue;for(int jj=ii+1;jj<rows.size();jj++){JsonObject rr=rows.get(jj).getAsJsonObject();if(!rr.has("admittedY")||!l.get("sourceId").equals(rr.get("sourceId"))||!l.get("variant").equals(rr.get("variant")))continue;JsonArray lp=l.getAsJsonArray("fixtureXYZ"),rp=rr.getAsJsonArray("fixtureXYZ");double lw=l.get("width").getAsDouble(),rw=rr.get("width").getAsDouble(),ly=l.get("admittedY").getAsDouble(),ry=rr.get("admittedY").getAsDouble();if(Math.abs(lp.get(0).getAsDouble()-rp.get(0).getAsDouble())<(lw+rw)/2&&Math.abs(lp.get(2).getAsDouble()-rp.get(2).getAsDouble())<(lw+rw)/2&&ly<ry+rr.get("height").getAsDouble()&&ry<ly+l.get("height").getAsDouble()){JsonObject hit=new JsonObject();hit.add("sourceId",l.get("sourceId"));hit.add("variant",l.get("variant"));hit.add("indices",new Gson().toJsonTree(new int[]{l.get("index").getAsInt(),rr.get("index").getAsInt()}));overlaps.add(hit);}}}report.add("pairwiseAdmittedBodyOverlaps",overlaps);report.addProperty("pairwiseOverlapCount",overlaps.size());report.add("rows",rows);Files.write(Paths.get(System.getProperty("actor.birth.report","temp/actor-birth-check.json")),new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(java.nio.charset.StandardCharsets.UTF_8));
 }
}
