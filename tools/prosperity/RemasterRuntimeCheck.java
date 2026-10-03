import java.lang.reflect.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.*;
import com.google.gson.*;
import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.*;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.*;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.*;

/** Real production authority and MC NBT; only storage, loaded geometry and write faults are adapted. */
public class RemasterRuntimeCheck {
 static sun.misc.Unsafe u;
 static int checks;
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static void set(Object o,Class<?> type,String name,Object value)throws Exception {
  Field f=type.getDeclaredField(name);f.setAccessible(true);
  u.putObject(o,u.objectFieldOffset(f),value);
 }
 static void setBlock(String field,Block value)throws Exception {
  Field f=Blocks.class.getField(field);u.putObject(u.staticFieldBase(f),u.staticFieldOffset(f),value);
 }
 static class W extends World {
  Map<String,Block> blocks;Map<String,Integer> metas;Map<String,TileEntity> tiles;
  Set<String> unloadedChunks=new HashSet<>();IChunkProvider loadedChunksProxy;boolean actualSideQueries;
  long time,seed;boolean loaded,occupied,collision,liquid;Integer supportY;String failAt;int writes,failWrite,spawns,ownerChecks;P player;
  W(){super((ISaveHandler)null,(String)null,(WorldProvider)null,(WorldSettings)null,(Profiler)null);}
  String key(int x,int y,int z){return x+","+y+","+z;}
  public Block getBlock(int x,int y,int z){return blocks.getOrDefault(key(x,y,z),Blocks.air);}
  public int getBlockMetadata(int x,int y,int z){return metas.getOrDefault(key(x,y,z),0);}
  public boolean isSideSolid(int x,int y,int z,net.minecraftforge.common.util.ForgeDirection side,boolean fallback){return getBlock(x,y,z).isSideSolid(this,x,y,z,side);}
  public TileEntity getTileEntity(int x,int y,int z){return tiles.get(key(x,y,z));}
  public boolean blockExists(int x,int y,int z){return loaded&&y>=0&&y<256&&(unloadedChunks==null||!unloadedChunks.contains((x>>4)+","+(z>>4)));}
  public IChunkProvider getChunkProvider(){if(loadedChunksProxy==null)loadedChunksProxy=(IChunkProvider)Proxy.newProxyInstance(IChunkProvider.class.getClassLoader(),new Class[]{IChunkProvider.class},(proxy,method,args)->{if(method.getName().equals("chunkExists")){ownerChecks++;return loaded&&(unloadedChunks==null||!unloadedChunks.contains(args[0]+","+args[1]));}throw new AssertionError("retry must not load/provide chunks: "+method.getName());});return loadedChunksProxy;}
  public boolean setBlock(int x,int y,int z,Block b,int m,int flags){if(++writes==failWrite)return false;if(key(x,y,z).equals(failAt)){failAt=null;return false;}if(getBlock(x,y,z)==b&&getBlockMetadata(x,y,z)==m)return false;blocks.put(key(x,y,z),b);metas.put(key(x,y,z),m);tiles.remove(key(x,y,z));if(b.hasTileEntity(m)){TileEntity tile=b.createTileEntity(this,m);if(tile!=null){tile.setWorldObj(this);tile.xCoord=x;tile.yCoord=y;tile.zCoord=z;tiles.put(key(x,y,z),tile);}}return true;}
  public boolean setBlockMetadataWithNotify(int x,int y,int z,int m,int flags){metas.put(key(x,y,z),m);return true;}
  public void markBlockForUpdate(int x,int y,int z){}
  public boolean func_147451_t(int x,int y,int z){return true;}
  public void markTileEntityChunkModified(int x,int y,int z,TileEntity tile){}
  public void notifyBlocksOfNeighborChange(int x,int y,int z,Block b){}
  public void playSoundEffect(double x,double y,double z,String sound,float volume,float pitch){}
  public long getTotalWorldTime(){return time;}
  public long getSeed(){return seed;}
  public List getEntitiesWithinAABB(Class type,AxisAlignedBB box){return occupied?Collections.singletonList(new Object()):Collections.emptyList();}
  public List getCollidingBoundingBoxes(Entity e,AxisAlignedBB box){return collision?Collections.singletonList(box):Collections.emptyList();}
  public List getEntitiesWithinAABBExcludingEntity(Entity e,AxisAlignedBB box){return occupied?Collections.singletonList(new Object()):Collections.emptyList();}
  public boolean isAnyLiquid(AxisAlignedBB box){return liquid;}
  public boolean checkNoEntityCollision(AxisAlignedBB box){return !occupied;}
  public EntityPlayer getClosestPlayer(double x,double y,double z,double radius){return player;}
  public boolean isSideSolid(int x,int y,int z,net.minecraftforge.common.util.ForgeDirection side){return actualSideQueries?isSideSolid(x,y,z,side,false):supportY==null||y==supportY;}
  public boolean spawnEntityInWorld(Entity e){spawns++;if(loadedEntityList!=null)loadedEntityList.add(e);return true;}
  public Entity getEntityByID(int id){return null;}
  protected IChunkProvider createChunkProvider(){return null;}
  protected int func_152379_p(){return 0;}
 }
 static class P extends EntityPlayerMP {
  boolean living=true;
  int guiOpened;
  String lastMessage="";int messages;
  NBTTagCompound personal;
  P(){super(null,null,null,null);}
  public boolean isEntityAlive(){return living;}
  public double getDistanceSq(double x,double y,double z){return 0;}
  public void addChatMessage(net.minecraft.util.IChatComponent text){lastMessage=text.getUnformattedText();messages++;}
  public NBTTagCompound getEntityData(){if(personal==null)personal=new NBTTagCompound();return personal;}
  public void openGui(Object mod,int id,World world,int x,int y,int z){guiOpened++;}
 }
 static class MotionProbe extends EntityOldEcho {
  MotionProbe(World w){super(w);}
  public void moveEntity(double x,double y,double z){setPosition(posX+x,posY+y,posZ+z);}
 }
 static TileRemasterNode tile(W w,RemasterSite s,JsonObject n){
  TileRemasterNode t=new TileRemasterNode();t.setWorldObj(w);t.xCoord=s.x+n.get("x").getAsInt();
  t.yCoord=s.y+n.get("y").getAsInt();t.zCoord=s.z+n.get("z").getAsInt();
  t.siteId=s.id();t.nodeId=n.get("id").getAsString();t.role=n.get("role").getAsString();
  String key=n.get("block").getAsString();w.blocks.put(w.key(t.xCoord,t.yCoord,t.zCoord),RemasterRuntime.resolve(key));
  w.metas.put(w.key(t.xCoord,t.yCoord,t.zCoord),RemasterRuntime.meta(key));w.tiles.put(w.key(t.xCoord,t.yCoord,t.zCoord),t);return t;
 }
 static JsonObject node(RemasterSite s,String role){for(JsonObject n:RemasterRuntime.nodes(s))if(n.get("role").getAsString().equals(role))return n;throw new AssertionError(role);}
 static W isolatedWorld(long seed)throws Exception {
  W w=(W)u.allocateInstance(W.class);w.blocks=new HashMap<>();w.metas=new HashMap<>();w.tiles=new HashMap<>();w.loaded=true;w.seed=seed;
  set(w,World.class,"perWorldStorage",new MapStorage((ISaveHandler)null));set(w,World.class,"provider",u.allocateInstance(WorldProviderProsperityRuins.class));set(w,World.class,"rand",new Random(seed));set(w,World.class,"loadedEntityList",new ArrayList<>());return w;
 }
 static List<AxisAlignedBB> actualBlockCollisions(World w,AxisAlignedBB bounds){
  List<AxisAlignedBB> boxes=new ArrayList<>();
  W fixture=(W)w;boolean before=fixture.actualSideQueries;fixture.actualSideQueries=true;
  try{for(int x=(int)Math.floor(bounds.minX)-1;x<=(int)Math.floor(bounds.maxX)+1;x++)for(int y=(int)Math.floor(bounds.minY)-1;y<=(int)Math.floor(bounds.maxY)+1;y++)for(int z=(int)Math.floor(bounds.minZ)-1;z<=(int)Math.floor(bounds.maxZ)+1;z++)
   w.getBlock(x,y,z).addCollisionBoxesToList(w,x,y,z,bounds,boxes,null);
  }finally{fixture.actualSideQueries=before;}
  return boxes;
 }
 static boolean actualStandingFace(World w,int x,int feetY,int z){
  AxisAlignedBB body=AxisAlignedBB.getBoundingBox(x+.2,feetY+.001,z+.2,x+.8,feetY+1.8,z+.8);
  AxisAlignedBB support=AxisAlignedBB.getBoundingBox(x+.2,feetY-.02,z+.2,x+.8,feetY-.001,z+.8);
  return actualBlockCollisions(w,body).isEmpty()&&!actualBlockCollisions(w,support).isEmpty();
 }
 static void treeOverlayMapping()throws Exception {
  final int ax=60000,az=60000,y0=60;List<String> unsafeAcrossHeights=new ArrayList<>();JsonArray treeEvidence=new JsonArray();
  for(int desiredHeight:new int[]{133,142,146}){
   long seed=0;while(seed<100000&&com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityDecorPlacer.islandTreeHeightAt(seed,ax,az)!=desiredHeight)seed++;check(seed<100000,"find real deterministic tree height "+desiredHeight);
   W tw=isolatedWorld(seed);RemasterData td=RemasterData.get(tw);
   com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterStructure.placeInto(null,new com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder((x,y,z,b,m,f)->{if(!(b instanceof Block))throw new AssertionError("natural tree geometry has unresolved block at "+x+","+y+","+z);if(b==Blocks.air&&!tw.blocks.containsKey(tw.key(x,y,z)))return true;boolean wrote=tw.setBlock(x,y,z,(Block)b,m,f);if(b==Blocks.air)tw.blocks.remove(tw.key(x,y,z));return wrote;}),ax,az,y0,desiredHeight);
   String naturalEncounter=ForgottenLakeEncounterStructure.encounterId(tw,ax,az);
   for(int room=0;room<8;room++)for(int ordinal=0;ordinal<ForgottenLakeEncounterStructure.ROOM_CHEST_COUNTS[room];ordinal++){int[] c=ForgottenLakeEncounterStructure.chestPosition(ax,az,y0,desiredHeight,room,ordinal);tw.setBlock(c[0],c[1],c[2],ForgottenLakeEncounterRegistry.sealedChest,2,2);((TileEntitySealedChest)tw.getTileEntity(c[0],c[1],c[2])).initialize(2,naturalEncounter,room);}
   for(int ordinal=0;ordinal<9;ordinal++){int[] c=ForgottenLakeEncounterStructure.throneChestPosition(ax,az,y0,desiredHeight,ordinal);tw.setBlock(c[0],c[1],c[2],ForgottenLakeEncounterRegistry.sealedChest,2,2);((TileEntitySealedChest)tw.getTileEntity(c[0],c[1],c[2])).initialize(ordinal<3?3:ordinal<6?4:5,naturalEncounter,-1);}
   Map<String,TileEntity> naturalTiles=new HashMap<>(tw.tiles);
   RemasterSite expected=new RemasterSite("forgotten_lake_court",0,seed,ax-160,y0+desiredHeight-145,az-152,"tree-overlay");
   LinkedHashSet<String> owners=new LinkedHashSet<>();for(JsonObject n:RemasterRuntime.nodes(expected))if(n.get("y").getAsInt()>=78)owners.add(((expected.x+n.get("x").getAsInt())>>4)+","+((expected.z+n.get("z").getAsInt())>>4));owners.add(((ax-24)>>4)+","+((az-27)>>4));
   for(String owner:owners){String[] pair=owner.split(",");RemasterWorldgen.treeOverlay(tw,ax,az,y0,Integer.parseInt(pair[0]),Integer.parseInt(pair[1]));}
   RemasterSite actual=td.site(expected.id());check(actual!=null&&actual.x==ax-160&&actual.z==az-152&&actual.y==y0+desiredHeight-145,"real treeOverlay persists exact upper origin for height "+desiredHeight);
   for(Map.Entry<String,TileEntity> e:naturalTiles.entrySet()){TileEntitySealedChest legacy=(TileEntitySealedChest)e.getValue();check(tw.tiles.get(e.getKey())==legacy&&legacy.getEncounterId().equals(naturalEncounter)&&legacy.getRemasterSite().isEmpty(),"real legacy platform/throne chest identity and encounter authority survive overlay: "+e.getKey());}
   JsonObject king=null,guard=null;for(JsonElement e:actual.plan().metadata.getAsJsonArray("nodes")){JsonObject n=e.getAsJsonObject();if(n.get("id").getAsString().equals("tree-king"))king=n;if(n.get("id").getAsString().equals("tree-guard-0"))guard=n;}
   check(Arrays.equals(RemasterRuntime.nodePosition(tw,actual,king),ForgottenLakeEncounterStructure.throne(ax,az,y0,desiredHeight)),"source king maps to actual natural throne at height "+desiredHeight);check(Arrays.equals(RemasterRuntime.nodePosition(tw,actual,guard),ForgottenLakeEncounterStructure.guardPosition(ax,az,y0,desiredHeight,0,0)),"source guard0 maps to actual natural guard position at height "+desiredHeight);
   P tp=(P)u.allocateInstance(P.class);tp.worldObj=tw;tp.living=true;tp.capabilities=new PlayerCapabilities();int verified=0;List<String> unsafeNodes=new ArrayList<>();
   for(JsonObject n:RemasterRuntime.nodes(actual)){int[] at=RemasterRuntime.nodePosition(tw,actual,n);if(at==null)continue;String id=n.get("id").getAsString();Block floor=tw.getBlock(at[0],at[1]-1,at[2]);if(!floor.getMaterial().blocksMovement())unsafeNodes.add("floor:"+id+":"+Arrays.toString(at));boolean standing=false;for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})if(tw.getBlock(at[0]+d[0],at[1]-1,at[2]+d[1]).getMaterial().blocksMovement()&&!tw.getBlock(at[0]+d[0],at[1],at[2]+d[1]).getMaterial().blocksMovement()&&!tw.getBlock(at[0]+d[0],at[1]+1,at[2]+d[1]).getMaterial().blocksMovement())standing=true;if(!standing)unsafeNodes.add("standing:"+id+":"+Arrays.toString(at));if(!floor.getMaterial().blocksMovement()||!standing){JsonObject issue=new JsonObject();issue.addProperty("id",id);issue.addProperty("role",n.get("role").getAsString());issue.addProperty("reference",RemasterRuntime.string(n,"reference",""));issue.addProperty("H",desiredHeight);issue.addProperty("seed",seed);JsonArray sourceAt=new JsonArray(),actualAt=new JsonArray();for(String axis:new String[]{"x","y","z"})sourceAt.add(n.get(axis));for(int axis:at)actualAt.add(new JsonPrimitive(axis));issue.add("sourceCoords",sourceAt);issue.add("actualCoords",actualAt);issue.addProperty("floor",floor.getUnlocalizedName()+"/"+floor.getClass().getSimpleName());issue.addProperty("floorSolid",floor.getMaterial().blocksMovement());issue.addProperty("supportedStandingFace",standing);JsonArray faces=new JsonArray();for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){JsonObject face=new JsonObject();face.addProperty("dx",d[0]);face.addProperty("dz",d[1]);face.addProperty("floor",tw.getBlock(at[0]+d[0],at[1]-1,at[2]+d[1]).getUnlocalizedName());face.addProperty("feet",tw.getBlock(at[0]+d[0],at[1],at[2]+d[1]).getUnlocalizedName());face.addProperty("head",tw.getBlock(at[0]+d[0],at[1]+1,at[2]+d[1]).getUnlocalizedName());faces.add(face);}issue.add("operationFaces",faces);JsonArray candidates=new JsonArray();for(int radius=1;radius<=5&&candidates.size()<8;radius++)for(int dx=-radius;dx<=radius&&candidates.size()<8;dx++)for(int dz=-radius;dz<=radius&&candidates.size()<8;dz++){if(Math.abs(dx)+Math.abs(dz)!=radius)continue;int px=at[0]+dx,pz=at[2]+dz;if(tw.getBlock(px,at[1]-1,pz).getMaterial().blocksMovement()&&!tw.getBlock(px,at[1],pz).getMaterial().blocksMovement()&&!tw.getBlock(px,at[1]+1,pz).getMaterial().blocksMovement()){JsonArray candidate=new JsonArray();candidate.add(new JsonPrimitive(px-actual.x));candidate.add(new JsonPrimitive(at[1]-actual.y));candidate.add(new JsonPrimitive(pz-actual.z));candidates.add(candidate);}}issue.add("nearbySupportedTwoAirCandidatesSource",candidates);treeEvidence.add(issue);}
    boolean collisionFace=false;for(int[] side:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})if(actualStandingFace(tw,at[0]+side[0],at[1],at[2]+side[1]))collisionFace=true;check(collisionFace,"actual .6x1.8 player AABB has collision-free supported operation face: "+id+" H"+desiredHeight);
    TileEntity installed=tw.getTileEntity(at[0],at[1],at[2]);if(installed instanceof TileEntitySealedChest&&naturalTiles.get(tw.key(at[0],at[1],at[2]))==installed){check(((TileEntitySealedChest)installed).getRemasterSite().isEmpty()&&((TileEntitySealedChest)installed).getRemasterNode().isEmpty(),"existing natural sealed chest retains original legacy authority: "+id);}else check(td.flag(actual.id(),"node:"+id)&&installed!=null,"natural treeOverlay binds owner node or original-compatible box: "+id+" at "+Arrays.toString(at)+" block "+tw.getBlock(at[0],at[1],at[2]).getClass().getSimpleName()+" tile "+(installed==null?"null":installed.getClass().getSimpleName())+" expected "+n.get("block")+" meta "+tw.getBlockMetadata(at[0],at[1],at[2])+" origin "+(installed instanceof TileEntitySealedChest?((TileEntitySealedChest)installed).remasterGeometryOrigin.toString():"")+" original "+naturalTiles.containsKey(tw.key(at[0],at[1],at[2]))+" geom "+td.flag(actual.id(),"geom:"+(at[0]>>4)+":"+(at[2]>>4)));if(installed instanceof TileRemasterNode){check(RemasterRuntime.valid(tp,(TileRemasterNode)installed),"natural owner node validates: "+id);if(n.has("guideTarget")){JsonArray local=n.getAsJsonArray("guideTarget"),target=RemasterRuntime.view((TileRemasterNode)installed).getAsJsonArray("guideTarget");check(target.get(0).getAsInt()==actual.x+local.get(0).getAsInt()&&target.get(1).getAsInt()==actual.y+local.get(1).getAsInt()&&target.get(2).getAsInt()==actual.z+local.get(2).getAsInt(),"upper authored guide target follows actual tree origin: "+id);}}verified++;
   }check(verified>0,"actual natural fixture verifies upper interactives");if(!unsafeNodes.isEmpty())unsafeAcrossHeights.add("H"+desiredHeight+":"+unsafeNodes);
   NBTTagCompound saved=new NBTTagCompound();td.writeToNBT(saved);RemasterData loaded=new RemasterData();loaded.readFromNBT(saved);check(loaded.site(expected.id()).y==expected.y,"natural upper origin Y survives real NBT for height "+desiredHeight);
  }
  long oldSeed=17;W oldWorld=isolatedWorld(oldSeed);RemasterData oldData=RemasterData.get(oldWorld);String encounter=ForgottenLakeEncounterStructure.encounterId(oldWorld,ax,az);RemasterSite old=new RemasterSite("forgotten_lake_court",0,oldSeed,ax-154,31,az-154,"tree-overlay");oldData.register(old);oldData.state(old.id()).setString("legacyEncounter",encounter);int ocx=(ax-24)>>4,ocz=(az-27)>>4;oldData.flag(old.id(),"geom:"+ocx+":"+ocz,true);for(JsonObject n:RemasterRuntime.nodes(old))oldData.flag(old.id(),"node:"+n.get("id").getAsString(),true);oldData.flag(old.id(),"claimed:legacy-box",true);oldData.flag(old.id(),"witness-issued-once:dc-10",true);int oldWrites=oldWorld.writes;RemasterWorldgen.treeOverlay(oldWorld,ax,az,y0,ocx,ocz);RemasterSite correct=new RemasterSite("forgotten_lake_court",0,oldSeed,ax-160,60,az-152,"tree-overlay");check(oldData.site(correct.id())==null&&oldData.site(old.id()).y==31&&oldWorld.writes==oldWrites&&oldData.flag(old.id(),"claimed:legacy-box")&&oldData.flag(old.id(),"witness-issued-once:dc-10"),"existing old tree identity reuses geometry and claimed/witness ledgers without a second site or rewriting chunks");NBTTagCompound oldSave=new NBTTagCompound();oldData.writeToNBT(oldSave);RemasterData oldReload=new RemasterData();oldReload.readFromNBT(oldSave);check(oldReload.site(old.id()).y==31&&oldReload.site(correct.id())==null,"old saved Y and unique identity survive NBT reload");JsonObject treeReport=new JsonObject();treeReport.addProperty("generator","actual ForgottenLakeEncounterStructure.placeInto + RemasterWorldgen.treeOverlay");treeReport.addProperty("ax",ax);treeReport.addProperty("az",az);treeReport.addProperty("y0",y0);treeReport.addProperty("originAndOwnersAndLegacyIdentityPassed",true);treeReport.add("failures",treeEvidence);java.nio.file.Files.createDirectories(java.nio.file.Paths.get("temp"));java.nio.file.Files.write(java.nio.file.Paths.get("temp/runtime-tree-mapping-evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(treeReport).getBytes(java.nio.charset.StandardCharsets.UTF_8));check(unsafeAcrossHeights.isEmpty(),"actual natural tree has unsupported/unreachable nodes "+unsafeAcrossHeights);
 }
 static void treeEntrancePipeline()throws Exception {
  for(int scenario=0;scenario<7;scenario++){
   W tw=(W)u.allocateInstance(W.class);tw.blocks=new HashMap<>();tw.metas=new HashMap<>();tw.tiles=new HashMap<>();tw.loaded=true;
   set(tw,World.class,"perWorldStorage",new MapStorage((ISaveHandler)null));set(tw,World.class,"provider",u.allocateInstance(WorldProviderProsperityRuins.class));set(tw,World.class,"rand",new Random(17));set(tw,World.class,"loadedEntityList",new ArrayList<>());
   RemasterSite ts=new RemasterSite("forgotten_lake_court",0,17,40000,5,40000,"tree-overlay");RemasterData td=RemasterData.get(tw);td.register(ts);NBTTagCompound st=td.state(ts.id());st.setInteger("legacyAnchorX",40154);st.setInteger("legacyAnchorY",180);st.setInteger("legacyAnchorZ",40154);
   JsonObject binding=ts.plan().metadata.getAsJsonObject("legacyEntranceBinding"),board=null;for(JsonObject n:RemasterRuntime.nodes(ts))if(n.get("id").getAsString().equals(binding.get("node").getAsString()))board=n;
   int[] at=RemasterRuntime.nodePosition(tw,ts,board);int cx=at[0]>>4,cz=at[2]>>4;String authored=board.get("block").getAsString();Block notice=RemasterRuntime.resolve(authored),wood=com.miaokatze.gtsr.common.blocks.BlocksGTSR.prosperityZenithLog;
   tw.blocks.put(tw.key(at[0],at[1]-1,at[2]),wood);Block obstruction=scenario==1?wood:scenario==2?com.miaokatze.gtsr.common.blocks.BlocksGTSR.prosperityJadeLeaves:scenario==3?Blocks.stone:Blocks.air;
   if(scenario==4){tw.setBlock(at[0],at[1],at[2],notice,RemasterRuntime.meta(authored),3);obstruction=notice;}else tw.blocks.put(tw.key(at[0],at[1],at[2]),obstruction);
   TileEntity prior=tw.getTileEntity(at[0],at[1],at[2]);if(scenario>=5)tw.failAt=tw.key(at[0],at[1],at[2]);
   check(RemasterWorldgen.placeGeometryChunk(tw,ts,cx,cz),"real court geometry completes while respecting natural entrance admission scenario "+scenario);td.flag(ts.id(),"geom:"+cx+":"+cz,true);
   JsonArray source=binding.getAsJsonArray("sourceAt");check(tw.getBlock(ts.x+source.get(0).getAsInt(),ts.y+source.get(1).getAsInt(),ts.z+source.get(2).getAsInt())!=notice,"tree-overlay geometry skips authored scene-local entrance copy");
   if(scenario==0){TileRemasterNode raw=(TileRemasterNode)tw.getTileEntity(at[0],at[1],at[2]);check(raw!=null&&raw.siteId.isEmpty()&&raw.geometryOrigin.getString("site").equals(ts.id()),"first actual wood-platform geometry creates an unbound naturally sourced notice");RemasterWorldgen.completeChunk(tw,ts,cx,cz);P tp=(P)u.allocateInstance(P.class);tp.worldObj=tw;tp.living=true;tp.capabilities=new PlayerCapabilities();check(RemasterRuntime.valid(tp,raw)&&td.flag(ts.id(),"node:"+board.get("id").getAsString()),"actual low-platform production completion binds and validates entrance");tw.setBlock(at[0],at[1],at[2],Blocks.air,0,3);RemasterWorldgen.completeChunk(tw,ts,cx,cz);check(tw.getBlock(at[0],at[1],at[2])==Blocks.air,"closed completed entrance is never recreated after player removal");}
   else if(scenario>=5){check(td.flag(ts.id(),"tree-entrance:pending")&&tw.getTileEntity(at[0],at[1],at[2])==null,"failed first entrance write retains only pending admission");tw.loaded=false;tw.time=40;RemasterRuntime.RetryTicker ticker=new RemasterRuntime.RetryTicker();int blockedWrites=tw.writes;ticker.worldTick(new cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent(cpw.mods.fml.relauncher.Side.SERVER,cpw.mods.fml.common.gameevent.TickEvent.Phase.END,tw));check(tw.writes==blockedWrites,"pending entrance retry does not force an unloaded owner");tw.loaded=true;if(scenario==6)tw.setBlock(at[0],at[1],at[2],Blocks.stone,0,3);Map<String,Block> savedGeometry=new HashMap<>(tw.blocks);tw.time=80;ticker.worldTick(new cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent(cpw.mods.fml.relauncher.Side.SERVER,cpw.mods.fml.common.gameevent.TickEvent.Phase.END,tw));if(scenario==5)check(td.flag(ts.id(),"node:"+board.get("id").getAsString())&&!td.flag(ts.id(),"tree-entrance:pending")&&td.flag(ts.id(),"tree-entrance:closed"),"real loaded worldTick retries failed air-only entrance and binds it once");else check(!td.flag(ts.id(),"node:"+board.get("id").getAsString())&&td.flag(ts.id(),"tree-entrance:closed")&&tw.getBlock(at[0],at[1],at[2])==Blocks.stone,"player obstruction after failed admission closes pending retry without replacing it");for(Map.Entry<String,Block> voxel:savedGeometry.entrySet())if(!voxel.getKey().equals(tw.key(at[0],at[1],at[2])))check(tw.blocks.get(voxel.getKey())==voxel.getValue(),"pending worldTick preserves existing authored geometry");}
   else {check(td.flag(ts.id(),"tree-entrance:closed")&&!td.flag(ts.id(),"tree-entrance:pending")&&tw.getBlock(at[0],at[1],at[2])==obstruction&&tw.getTileEntity(at[0],at[1],at[2])==prior,"first blocked entrance permanently closes without replacing wood/leaves/player block or TE");tw.setBlock(at[0],at[1],at[2],Blocks.air,0,3);RemasterWorldgen.completeChunk(tw,ts,cx,cz);check(tw.getBlock(at[0],at[1],at[2])==Blocks.air,"clearing a rejected first entrance never admits a later replacement");}
  }
 }
 static void treeCompletionRegressions(String scenario)throws Exception {
  if(scenario.equals("null-retry")){
   W w=isolatedWorld(17);RemasterSite s=new RemasterSite("forgotten_lake_court",0,17,40000,69,40000,"tree-overlay");RemasterData d=RemasterData.get(w);d.register(s);JsonObject n=node(s,"chest");int[] at=RemasterRuntime.nodePosition(w,s,n);int cx=at[0]>>4,cz=at[2]>>4;
   w.setBlock(at[0],at[1],at[2],ForgottenLakeEncounterRegistry.sealedChest,2,3);TileEntity playerBox=w.getTileEntity(at[0],at[1],at[2]);RemasterWorldgen.placeChunk(w,s,cx,cz);String id=n.get("id").getAsString();check(w.getTileEntity(at[0],at[1],at[2])==playerBox&&d.flag(s.id(),"geom:"+cx+":"+cz)&&!d.flag(s.id(),"node:"+id),"first tree geometry rejects untokened player sealed chest");w.setBlock(at[0],at[1],at[2],Blocks.air,0,3);int before=w.writes;w.time=40;new RemasterRuntime.RetryTicker().worldTick(new cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent(cpw.mods.fml.relauncher.Side.SERVER,cpw.mods.fml.common.gameevent.TickEvent.Phase.END,w));check(w.writes==before&&w.getTileEntity(at[0],at[1],at[2])==null&&!d.flag(s.id(),"node:"+id),"tree retry never replaces removed rejected player chest without natural token");
  } else if(scenario.equals("pending-owner")){
   for(boolean replace:new boolean[]{false,true}){
    W w=isolatedWorld(17);int ax=40168,az=40169,y0=72,H=com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityDecorPlacer.islandTreeHeightAt(17,ax,az);check(H==134,"real seed17 fixture height");RemasterSite s=new RemasterSite("forgotten_lake_court",0,17,ax-160,y0+H-145,az-152,"tree-overlay");RemasterData d=RemasterData.get(w);d.register(s);NBTTagCompound st=d.state(s.id());st.setInteger("legacyAnchorX",ax);st.setInteger("legacyAnchorY",y0);st.setInteger("legacyAnchorZ",az);int[] at={ax-24,y0+1,az-27};int cx=at[0]>>4,cz=at[2]>>4;w.blocks.put(w.key(at[0],at[1]-1,at[2]),com.miaokatze.gtsr.common.blocks.BlocksGTSR.prosperityZenithLog);w.failAt=w.key(at[0],at[1],at[2]);RemasterWorldgen.placeChunk(w,s,cx,cz);check(d.flag(s.id(),"tree-entrance:pending")&&!d.flag(s.id(),"geom:"+cx+":"+cz)&&d.generatedOwners().isEmpty(),"entrance-only failed owner has pending admission without geometry success");
    NBTTagCompound saved=new NBTTagCompound();d.writeToNBT(saved);RemasterData reloaded=new RemasterData();reloaded.readFromNBT(saved);w.perWorldStorage.setData("gtsr.prosperityRemaster7",reloaded);d=reloaded;Map<String,Block> geometry=new HashMap<>(w.blocks);int before=w.writes;RemasterRuntime.RetryTicker retry=new RemasterRuntime.RetryTicker();w.loaded=false;w.time=40;retry.worldTick(new cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent(cpw.mods.fml.relauncher.Side.SERVER,cpw.mods.fml.common.gameevent.TickEvent.Phase.END,w));check(w.writes==before,"pending-only owner retry does not load absent chunk");w.loaded=true;if(replace)w.setBlock(at[0],at[1],at[2],Blocks.stone,0,3);before=w.writes;w.time=80;retry.worldTick(new cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent(cpw.mods.fml.relauncher.Side.SERVER,cpw.mods.fml.common.gameevent.TickEvent.Phase.END,w));if(replace)check(w.writes==before&&w.getBlock(at[0],at[1],at[2])==Blocks.stone&&d.flag(s.id(),"tree-entrance:closed")&&!d.flag(s.id(),"tree-entrance:pending"),"pending-only owner closes permanently on player obstruction");else {TileEntity tile=w.getTileEntity(at[0],at[1],at[2]);check(tile instanceof TileRemasterNode&&((TileRemasterNode)tile).nodeId.equals("entrance-story-board")&&d.flag(s.id(),"node:entrance-story-board")&&!d.flag(s.id(),"tree-entrance:pending")&&!d.flag(s.id(),"geom:"+cx+":"+cz),"real retry binds pending entrance without geometry flag or upper regeneration");check(w.writes==before+1,"pending-only retry writes exactly admitted entrance");}for(Map.Entry<String,Block> e:geometry.entrySet())check(w.blocks.get(e.getKey())==e.getValue(),"pending-only retry preserves geometry");
   }
  } else if(scenario.equals("legacy-v66")){
   W w=isolatedWorld(17);RemasterSite s=new RemasterSite("forgotten_lake_court",0,17,50000,31,50000,"tree-overlay");NBTTagCompound record=s.save(),state=new NBTTagCompound();record.setTag("state",state);state.setString("legacyEncounter",ForgottenLakeEncounterStructure.encounterId(w,s.x+154,s.z+154));state.setBoolean("claimed:loot7-8",true);state.setBoolean("witness-issued-once:dc-10",true);NBTTagList sites=new NBTTagList();sites.appendTag(record);NBTTagCompound oldSave=new NBTTagCompound();oldSave.setTag("sites",sites);RemasterData d=new RemasterData();d.readFromNBT(oldSave);w.perWorldStorage.setData("gtsr.prosperityRemaster7",d);
   JsonObject board=node(s,"memory");for(JsonObject n:RemasterRuntime.nodes(s))if(n.get("id").getAsString().equals("entrance-story-board"))board=n;w.setBlock(s.x+156,s.y+5,s.z+24,RemasterRuntime.resolve(board.get("block").getAsString()),RemasterRuntime.meta(board.get("block").getAsString()),3);TileRemasterNode original=(TileRemasterNode)w.getTileEntity(s.x+156,s.y+5,s.z+24);original.initialize(s.id(),"entrance-story-board","memory");NBTTagCompound tileSave=new NBTTagCompound();original.writeToNBT(tileSave);TileRemasterNode loaded=new TileRemasterNode();loaded.readFromNBT(tileSave);loaded.setWorldObj(w);w.tiles.put(w.key(loaded.xCoord,loaded.yCoord,loaded.zCoord),loaded);P player=(P)u.allocateInstance(P.class);player.worldObj=w;player.living=true;player.capabilities=new PlayerCapabilities();int before=w.writes;w.time=20;loaded.updateEntity();check(RemasterRuntime.valid(player,loaded)&&RemasterRuntime.node(loaded)!=null&&w.writes==before,"v66 physical entrance validates on ordinary TE tick without legacyAnchor or rewriting geometry");
   int[][] old={{8,224,89,221},{9,254,89,221},{10,224,89,225},{26,66,107,221},{34,63,116,146},{35,63,116,150},{41,66,125,63},{42,96,125,63},{53,224,143,63},{54,254,143,63}};
   for(int[] v:old){String id="loot7-"+v[0];JsonObject n=null;for(JsonObject candidate:RemasterRuntime.nodes(s))if(candidate.get("id").getAsString().equals(id))n=candidate;check(n!=null,"v66 node identity still exists "+id);int[] expected={s.x+v[1],s.y+v[2],s.z+v[3]};check(Arrays.equals(RemasterRuntime.nodePosition(w,s,n),expected),"v66 loot preserves old physical position "+id);w.setBlock(expected[0],expected[1],expected[2],ForgottenLakeEncounterRegistry.sealedChest,2,3);TileEntitySealedChest chest=(TileEntitySealedChest)w.getTileEntity(expected[0],expected[1],expected[2]);chest.initializeRemaster(n.get("tier").getAsInt(),s.id(),id);NBTTagCompound chestSave=new NBTTagCompound();chest.writeToNBT(chestSave);TileEntitySealedChest restored=new TileEntitySealedChest();restored.readFromNBT(chestSave);restored.setWorldObj(w);w.tiles.put(w.key(expected[0],expected[1],expected[2]),restored);Method find=RemasterRuntime.class.getDeclaredMethod("chestNode",TileEntitySealedChest.class);find.setAccessible(true);check(find.invoke(null,restored)!=null,"v66 real sealed NBT validates at old source position "+id);}
   before=w.writes;RemasterWorldgen.treeOverlay(w,s.x+154,s.z+154,s.y,(s.x+156)>>4,(s.z+24)>>4);check(w.writes==before&&d.flag(s.id(),"claimed:loot7-8")&&d.flag(s.id(),"witness-issued-once:dc-10")&&RemasterRuntime.valid(player,loaded),"v66 natural callback retains physical nodes and one-shot ledger without new geometry");NBTTagCompound roundTrip=new NBTTagCompound();d.writeToNBT(roundTrip);RemasterData reloaded=new RemasterData();reloaded.readFromNBT(roundTrip);w.perWorldStorage.setData("gtsr.prosperityRemaster7",reloaded);check(RemasterRuntime.valid(player,loaded)&&reloaded.flag(s.id(),"claimed:loot7-8")&&reloaded.state(s.id()).getInteger("treeLayoutRevision")==1,"explicit v66 revision and ordinary node compatibility survive NBT round-trip");
  }else throw new AssertionError(scenario);
 }
 public static void main(String[] args)throws Exception {
        NativeTerrainFixture.initialize();
  Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);u=(sun.misc.Unsafe)uf.get(null);
  Method mapping=TileEntity.class.getDeclaredMethod("addMapping",Class.class,String.class);mapping.setAccessible(true);mapping.invoke(null,TileEntitySealedChest.class,"gtsr.fixtureSealed");mapping.invoke(null,TileRemasterNode.class,"gtsr.fixtureNode");
  Class.forName("net.minecraft.init.Blocks");
  Constructor<BlockAir> air=BlockAir.class.getDeclaredConstructor();air.setAccessible(true);setBlock("air",air.newInstance());setBlock("stone",new BlockStone());
  com.miaokatze.gtsr.common.blocks.BlocksGTSR.ruinDebris=new com.miaokatze.gtsr.common.dimension.prosperity.block.BlockRuinDebris();
  com.miaokatze.gtsr.common.blocks.BlocksGTSR.ruinedCasing=new com.miaokatze.gtsr.common.dimension.prosperity.block.BlockRuinedCasing();
  com.miaokatze.gtsr.common.blocks.BlocksGTSR.prosperityStone=new com.miaokatze.gtsr.common.blocks.BlockProsperityStone("ProsperityStone","gtsr:prosperity_stone");
  com.miaokatze.gtsr.common.blocks.BlocksGTSR.prosperityZenithLog=new com.miaokatze.gtsr.common.dimension.prosperity.architecture.BlockZenithLog("ProsperityZenithLog","gtsr:prosperity_zenith_log_side","gtsr:prosperity_zenith_log_top");
  com.miaokatze.gtsr.common.blocks.BlocksGTSR.prosperityJadeLeaves=new com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityCanopyLeaves("ProsperityJadeLeaves","gtsr:prosperity_jade_leaves_side","gtsr:prosperity_jade_leaves_top");
  com.miaokatze.gtsr.common.dimension.prosperity.architecture.RuinsArchitecture.registerBlocks();
  com.miaokatze.gtsr.common.dimension.prosperity.architecture.RoyalArchitecture.registerBlocks();
  com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterRegistry.sealedChest=new com.miaokatze.gtsr.common.dimension.prosperity.encounter.BlockSealedChest();
  ForgottenLakeEncounterRegistry.unsealedChest=new BlockUnsealedChest();
  for(String itemName:new String[]{"coal","iron_ingot","gold_ingot","diamond","bread","written_book"}){Field itemField=net.minecraft.init.Items.class.getField(itemName);u.putObject(u.staticFieldBase(itemField),u.staticFieldOffset(itemField),new net.minecraft.item.Item());}
  Field registry=RemasterBlocks.class.getDeclaredField("REGISTRY");registry.setAccessible(true);
  Map map=(Map)registry.get(null);
  for(int i=0;i<94;i++){Method f=RemasterBlocks.class.getDeclaredMethod("block"+i);f.setAccessible(true);RemasterBlock b=(RemasterBlock)f.invoke(null);map.put(b.id(),b);}
  RemasterBlocks.setInteractionHandler(new RemasterBlocks.InteractionHandler(){
   public boolean activate(World world,int x,int y,int z,EntityPlayer player){return RemasterRuntime.activateNode(world,x,y,z,player);}
   public TileEntity createTileEntity(World world,int meta,String id){return new TileRemasterNode();}
   public float hardness(World world,int x,int y,int z,float fallback){return fallback;}
  });
  Set<String> palette=new HashSet<>();
  for(String id:RemasterCatalog.ids())for(int variant=0;variant<RemasterCatalog.variants(id);variant++)for(String material:RemasterCatalog.get(id,variant).palette)palette.add(material);
  for(String material:palette)check(RemasterRuntime.resolve(material)!=null,"actual block factory palette resolution "+material);
  if(args.length>0&&args[0].equals("--tree-mapping")){treeOverlayMapping();System.out.println("tree-mapping "+checks+" PASS");return;}
  if(args.length>0&&args[0].equals("--tree-completion-regressions")){treeCompletionRegressions(args[1]);System.out.println("tree-completion "+args[1]+" "+checks+" PASS");return;}
  W w=(W)u.allocateInstance(W.class);w.blocks=new HashMap<>();w.metas=new HashMap<>();w.tiles=new HashMap<>();w.loaded=true;
  set(w,World.class,"perWorldStorage",new MapStorage((ISaveHandler)null));
  set(w,World.class,"provider",u.allocateInstance(WorldProviderProsperityRuins.class));
  set(w,World.class,"rand",new Random(7));
  set(w,World.class,"loadedEntityList",new ArrayList<>());
  P p=(P)u.allocateInstance(P.class);p.living=true;p.worldObj=w;p.capabilities=new PlayerCapabilities();w.player=p;
  Field guiMod=com.miaokatze.gtsr.common.terminal.AggregatorGuiHandler.class.getDeclaredField("modInstance");guiMod.setAccessible(true);guiMod.set(null,new Object());
  boolean missing=false;try{RemasterRuntime.resolve("gtsr:missing_deliberately#0");}catch(IllegalArgumentException e){missing=true;}check(missing,"unknown block fails instead of default air");
  RemasterSite s=new RemasterSite("fallen_foundry",0,7,0,80,0);RemasterData data=RemasterData.get(w);data.register(s);
  RemasterSite oldAnchor=new RemasterSite("archivist_kiosk",0,99,27000,31,27000),newAnchor=new RemasterSite("archivist_kiosk",0,99,27000,104,27000);data.register(oldAnchor);data.register(newAnchor);
  w.loaded=false;int beforeLocateWrites=w.writes;check(data.nearestExisting(99,"archivist_kiosk",27000,27000).y==31&&w.writes==beforeLocateWrites,"saved old natural anchor wins over newer planner Y without loading or rewriting chunks");
  NBTTagCompound anchorSave=new NBTTagCompound();data.writeToNBT(anchorSave);RemasterData anchorReload=new RemasterData();anchorReload.readFromNBT(anchorSave);check(anchorReload.nearestExisting(99,"archivist_kiosk",27000,27000).y==31&&anchorReload.nearestExisting(100,"archivist_kiosk",27000,27000)==null,"NBT preserves original anchor and excludes other world seeds");w.loaded=true;
  RemasterSite pipeline=new RemasterSite("fallen_foundry",0,7,9000,80,9000);data.register(pipeline);
  JsonObject pipelineControl=node(pipeline,"control");int pcx=(pipeline.x+pipelineControl.get("x").getAsInt())>>4,pcz=(pipeline.z+pipelineControl.get("z").getAsInt())>>4;
  check(RemasterWorldgen.placeGeometryChunk(w,pipeline,pcx,pcz),"actual production geometry chunk succeeds");
  TileEntity rawGeometryTile=w.getTileEntity(pipeline.x+pipelineControl.get("x").getAsInt(),pipeline.y+pipelineControl.get("y").getAsInt(),pipeline.z+pipelineControl.get("z").getAsInt());
  check(rawGeometryTile instanceof TileRemasterNode&&((TileRemasterNode)rawGeometryTile).siteId.isEmpty(),"Forge geometry creates unbound real node tile");
  NBTTagCompound unboundSave=new NBTTagCompound();rawGeometryTile.writeToNBT(unboundSave);TileRemasterNode unboundReload=new TileRemasterNode();unboundReload.readFromNBT(unboundSave);unboundReload.setWorldObj(w);w.tiles.put(w.key(unboundReload.xCoord,unboundReload.yCoord,unboundReload.zCoord),unboundReload);
  RemasterWorldgen.placeChunk(w,pipeline,pcx,pcz);
  check(data.flag(pipeline.id(),"node:"+pipelineControl.get("id").getAsString()),"geometry to install to ledger binds fresh Forge-created tile");
  TileRemasterNode boundGeometryTile=(TileRemasterNode)w.getTileEntity(pipeline.x+pipelineControl.get("x").getAsInt(),pipeline.y+pipelineControl.get("y").getAsInt(),pipeline.z+pipelineControl.get("z").getAsInt());
  check(RemasterRuntime.valid(p,boundGeometryTile)&&RemasterRuntime.action(p,boundGeometryTile,0),"newly generated real node activates production authority");
  int previousGui=p.guiOpened;check(w.getBlock(boundGeometryTile.xCoord,boundGeometryTile.yCoord,boundGeometryTile.zCoord).onBlockActivated(w,boundGeometryTile.xCoord,boundGeometryTile.yCoord,boundGeometryTile.zCoord,p,1,0,0,0)&&p.guiOpened==previousGui+1,"production generated block callback opens actual GUI route");
  check(boundGeometryTile.geometryOrigin.hasNoTags(),"natural origin is consumed after persisted empty tile binds");
  TileRemasterNode tornSaveReload=new TileRemasterNode();tornSaveReload.readFromNBT(unboundSave);tornSaveReload.setWorldObj(w);w.tiles.put(w.key(tornSaveReload.xCoord,tornSaveReload.yCoord,tornSaveReload.zCoord),tornSaveReload);int beforeRecoveryWrites=w.writes;
  tornSaveReload.updateEntity();check(RemasterRuntime.node(tornSaveReload)!=null&&data.flag(pipeline.id(),"node:"+pipelineControl.get("id").getAsString())&&w.writes==beforeRecoveryWrites,"loaded natural tile recovers even when node ledger was saved first without rewriting geometry");
  boundGeometryTile=tornSaveReload;
  TileRemasterNode playerPlaced=new TileRemasterNode();playerPlaced.setWorldObj(w);playerPlaced.xCoord=boundGeometryTile.xCoord;playerPlaced.yCoord=boundGeometryTile.yCoord;playerPlaced.zCoord=boundGeometryTile.zCoord;w.tiles.put(w.key(playerPlaced.xCoord,playerPlaced.yCoord,playerPlaced.zCoord),playerPlaced);
  check(!RemasterRuntime.installNode(w,pipeline,pipelineControl)&&playerPlaced.siteId.isEmpty(),"same block blank player tile is never adopted without natural origin");
  RemasterRuntime.recordGeneratedTile(w,pipeline,playerPlaced.xCoord,playerPlaced.yCoord,playerPlaced.zCoord,playerPlaced);
  check(!RemasterRuntime.installNode(w,pipeline,pipelineControl),"unchanged tile identity cannot acquire natural generation token");
  w.tiles.put(w.key(boundGeometryTile.xCoord,boundGeometryTile.yCoord,boundGeometryTile.zCoord),boundGeometryTile);
  check(RemasterRuntime.installNode(w,pipeline,pipelineControl),"already bound same owner node stays idempotent");
  w.tiles.remove(w.key(boundGeometryTile.xCoord,boundGeometryTile.yCoord,boundGeometryTile.zCoord));int missingNodeWrites=w.writes;check(!RemasterRuntime.installNode(w,pipeline,pipelineControl)&&w.writes==missingNodeWrites,"retry never reconstructs an installed player-removed node");w.tiles.put(w.key(boundGeometryTile.xCoord,boundGeometryTile.yCoord,boundGeometryTile.zCoord),boundGeometryTile);
  RemasterSite deferred=new RemasterSite("fallen_foundry",0,7,24000,80,24000);data.register(deferred);JsonArray deferredSpawns=deferred.plan().metadata.getAsJsonArray("spawns");int deferredIndex=-1;JsonObject deferredSpawn=null;
  String delayedNeighbor=null;
  for(int spawnIndex=0;spawnIndex<deferredSpawns.size();spawnIndex++){
   JsonObject candidate=deferredSpawns.get(spawnIndex).getAsJsonObject();String code=candidate.get("code").getAsString();if(code.equals("dr-09")||code.equals("dc-10")||candidate.has("spawn")&&!candidate.get("spawn").getAsBoolean())continue;
   EchoKind kind=EchoKind.byCode(code);int x=deferred.x+candidate.get("x").getAsInt(),z=deferred.z+candidate.get("z").getAsInt(),cx=x>>4,cz=z>>4;
   int minCx=((int)Math.floor(x+.5-kind.width/2))>>4,maxCx=((int)Math.floor(x+.5+kind.width/2-.0001))>>4,minCz=((int)Math.floor(z+.5-kind.width/2))>>4,maxCz=((int)Math.floor(z+.5+kind.width/2-.0001))>>4;
   if(minCx!=cx)delayedNeighbor=minCx+","+cz;else if(maxCx!=cx)delayedNeighbor=maxCx+","+cz;else if(minCz!=cz)delayedNeighbor=cx+","+minCz;else if(maxCz!=cz)delayedNeighbor=cx+","+maxCz;else continue;
   deferredIndex=spawnIndex;deferredSpawn=candidate;break;
  }
  check(deferredSpawn!=null,"authored entity footprint crosses a real adjacent owner chunk");
  int deferredCx=(deferred.x+deferredSpawn.get("x").getAsInt())>>4,deferredCz=(deferred.z+deferredSpawn.get("z").getAsInt())>>4;
  w.unloadedChunks=new HashSet<>();w.unloadedChunks.add(delayedNeighbor);RemasterWorldgen.placeChunk(w,deferred,deferredCx,deferredCz);check(data.flag(deferred.id(),"geom:"+deferredCx+":"+deferredCz)&&!data.flag(deferred.id(),"entity:"+deferredIndex),"initial production population defers adjacent-unloaded footprint without consuming success ledger");
  RemasterRuntime.RetryTicker retryTicker=new RemasterRuntime.RetryTicker();int retrySpawnsBefore=w.spawns,retryWritesBefore=w.writes;
  w.loaded=false;w.time=120;retryTicker.worldTick(new cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent(cpw.mods.fml.relauncher.Side.SERVER,cpw.mods.fml.common.gameevent.TickEvent.Phase.END,w));check(w.spawns==retrySpawnsBefore&&!data.flag(deferred.id(),"entity:"+deferredIndex),"real worldTick never force-loads absent owner chunks");
  w.loaded=true;w.time=160;retryTicker.worldTick(new cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent(cpw.mods.fml.relauncher.Side.SERVER,cpw.mods.fml.common.gameevent.TickEvent.Phase.END,w));check(!data.flag(deferred.id(),"entity:"+deferredIndex),"owner-loaded real worldTick still rejects unloaded adjacent footprint");
  w.unloadedChunks.clear();w.time=200;retryTicker.worldTick(new cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent(cpw.mods.fml.relauncher.Side.SERVER,cpw.mods.fml.common.gameevent.TickEvent.Phase.END,w));check(data.flag(deferred.id(),"entity:"+deferredIndex)&&w.writes==retryWritesBefore,"real periodic worldTick admits birth when neighboring chunk finally loads without touching geometry");
  int admittedEntityCount=0;for(Object entity:w.loadedEntityList)if(entity instanceof EntityOldEcho&&((EntityOldEcho)entity).getEncounterId().equals(deferred.id())&&((EntityOldEcho)entity).getPlatformId()==deferredIndex)admittedEntityCount++;
  check(admittedEntityCount==1,"one successful deferred entity exists");w.time=240;retryTicker.worldTick(new cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent(cpw.mods.fml.relauncher.Side.SERVER,cpw.mods.fml.common.gameevent.TickEvent.Phase.END,w));int repeatedEntityCount=0;for(Object entity:w.loadedEntityList)if(entity instanceof EntityOldEcho&&((EntityOldEcho)entity).getEncounterId().equals(deferred.id())&&((EntityOldEcho)entity).getPlatformId()==deferredIndex)repeatedEntityCount++;check(repeatedEntityCount==1&&w.writes==retryWritesBefore,"later real worldTick never repeats successful birth or rebuilds geometry");
  W largeSave=(W)u.allocateInstance(W.class);largeSave.blocks=new HashMap<>();largeSave.metas=new HashMap<>();largeSave.tiles=new HashMap<>();largeSave.unloadedChunks=new HashSet<>();largeSave.loaded=true;largeSave.player=p;
  set(largeSave,World.class,"perWorldStorage",new MapStorage((ISaveHandler)null));set(largeSave,World.class,"provider",w.provider);set(largeSave,World.class,"rand",new Random(9));set(largeSave,World.class,"loadedEntityList",new ArrayList<>());
  RemasterData largeData=RemasterData.get(largeSave);RemasterSite largeSite=new RemasterSite("fallen_foundry",0,29,40000,80,40000);largeData.register(largeSite);
  int recoveryCx=(largeSite.x+deferredSpawn.get("x").getAsInt())>>4,recoveryCz=(largeSite.z+deferredSpawn.get("z").getAsInt())>>4;
  check(RemasterWorldgen.placeGeometryChunk(largeSave,largeSite,recoveryCx,recoveryCz),"loaded recovery owner has real production geometry before retry");largeData.flag(largeSite.id(),"geom:"+recoveryCx+":"+recoveryCz,true);
  int savedUnloadedOwners=0;for(int cx=largeSite.minX()>>4;cx<=largeSite.maxX()>>4&&savedUnloadedOwners<65;cx++)for(int cz=largeSite.minZ()>>4;cz<=largeSite.maxZ()>>4&&savedUnloadedOwners<65;cz++){
   if(Math.abs(cx-recoveryCx)<=1&&Math.abs(cz-recoveryCz)<=1)continue;
   largeData.flag(largeSite.id(),"geom:"+cx+":"+cz,true);largeSave.unloadedChunks.add(cx+","+cz);savedUnloadedOwners++;
  }
  largeSave.unloadedChunks.add(recoveryCx+","+recoveryCz);List<RemasterData.GeneratedOwner> cachedOwners=largeData.generatedOwners();check(cachedOwners.size()>=65,"large saved geometry fixture contains at least 65 real site owners");
  largeData.flag(largeSite.id(),"node:cache-test",true);check(cachedOwners==largeData.generatedOwners(),"ordinary node ledger updates reuse generated-owner cache");
  RemasterRuntime.RetryTicker boundedTicker=new RemasterRuntime.RetryTicker();int boundedWrites=largeSave.writes;largeSave.time=40;boundedTicker.worldTick(new cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent(cpw.mods.fml.relauncher.Side.SERVER,cpw.mods.fml.common.gameevent.TickEvent.Phase.END,largeSave));check(largeSave.ownerChecks==64&&largeSave.writes==boundedWrites,"one real worldTick checks at most 64 unloaded owners without geometry writes");
  largeSave.unloadedChunks.remove(recoveryCx+","+recoveryCz);
  for(int period=2;period<=4&&!largeData.flag(largeSite.id(),"entity:"+deferredIndex);period++){int previousChecks=largeSave.ownerChecks;largeSave.time=period*40;boundedTicker.worldTick(new cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent(cpw.mods.fml.relauncher.Side.SERVER,cpw.mods.fml.common.gameevent.TickEvent.Phase.END,largeSave));check(largeSave.ownerChecks-previousChecks<=64,"rotating retry windows preserve the 64-owner scan bound");}
  check(largeData.flag(largeSite.id(),"entity:"+deferredIndex)&&largeSave.writes==boundedWrites,"later loaded owner enters bounded scan window and recovers birth without geometry writes");
  for(JsonObject installed:RemasterRuntime.nodes(largeSite))largeData.flag(largeSite.id(),"node:"+installed.get("id").getAsString(),true);for(int i=0;i<deferredSpawns.size();i++)largeData.flag(largeSite.id(),"entity:"+i,true);
  largeSave.unloadedChunks.clear();largeSave.ownerChecks=0;largeSave.time=200;boundedTicker.worldTick(new cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent(cpw.mods.fml.relauncher.Side.SERVER,cpw.mods.fml.common.gameevent.TickEvent.Phase.END,largeSave));check(largeSave.ownerChecks==16&&largeSave.writes==boundedWrites,"fully loaded large saved state stops at 16 completion callbacks without rebuilding installed or removed nodes");
  String unchangedGeom="geom:"+recoveryCx+":"+recoveryCz;largeData.flag(largeSite.id(),unchangedGeom,true);check(cachedOwners==largeData.generatedOwners(),"unchanged geometry flag does not invalidate owner cache");largeData.flag(largeSite.id(),unchangedGeom,false);check(largeData.generatedOwners().size()==cachedOwners.size()-1,"geometry ownership change invalidates cached owners");largeData.flag(largeSite.id(),unchangedGeom,true);
  NBTTagCompound largeSaveNbt=new NBTTagCompound();largeData.writeToNBT(largeSaveNbt);RemasterData largeReload=new RemasterData();largeReload.readFromNBT(largeSaveNbt);check(largeReload.generatedOwners().size()==cachedOwners.size(),"owner cache rebuilds from unchanged real NBT format after reload");
  for(String siteId:RemasterCatalog.ids())for(int variant=0;variant<RemasterCatalog.variants(siteId);variant++){
   RemasterSite authored=new RemasterSite(siteId,variant,7,0,80,0);
   for(JsonObject n:RemasterRuntime.nodes(authored)){
    Block b=RemasterRuntime.resolve(n.get("block").getAsString());
    check(b.hasTileEntity(RemasterRuntime.meta(n.get("block").getAsString())),"every authored interactive coordinate owns a real tile "+siteId+":"+n.get("id"));
    if(n.get("role").getAsString().equals("chest"))check(b==ForgottenLakeEncounterRegistry.sealedChest,"new reward uses original sealed chest");
   }
  }
  JsonObject memory=node(s,"memory");check(RemasterRuntime.installNode(w,s,memory),"native/decorative memory installs readable tile");
  TileRemasterNode notice=(TileRemasterNode)w.getTileEntity(s.x+memory.get("x").getAsInt(),s.y+memory.get("y").getAsInt(),s.z+memory.get("z").getAsInt());
  check(RemasterRuntime.node(notice)!=null,"installed notice owns correct role/node/site");
  JsonObject reward=node(s,"chest");check(RemasterRuntime.installNode(w,s,reward),"original sealed reward installs and binds");
  TileEntitySealedChest sealed=(TileEntitySealedChest)w.getTileEntity(s.x+reward.get("x").getAsInt(),s.y+reward.get("y").getAsInt(),s.z+reward.get("z").getAsInt());
  check(sealed.isRemasterNode(s.id(),reward.get("id").getAsString()),"sealed chest authority binding");
  NBTTagCompound chestSave=new NBTTagCompound();sealed.writeToNBT(chestSave);TileEntitySealedChest restoredChest=new TileEntitySealedChest();restoredChest.readFromNBT(chestSave);
  check(restoredChest.isRemasterNode(s.id(),reward.get("id").getAsString()),"sealed ownership survives NBT");
  p.capabilities.isCreativeMode=true;sealed.tryUnlockByClick(p);check(sealed.getOpeningTicks()<0,"creative inspection cannot start reward");p.capabilities.isCreativeMode=false;
  check(!RemasterRuntime.chestReady(sealed),"puzzle reward respects original event gate");
  String rewardReference=reward.get("reference").getAsString();NBTTagCompound rewardSolved=new NBTTagCompound();rewardSolved.setBoolean("solved",true);data.state(s.id()).setTag(rewardReference.startsWith("shape-")?"shape:"+rewardReference:"site-puzzle",rewardSolved);
  check(RemasterRuntime.chestReady(sealed),"authoritative puzzle event admits original reward");
  ForgottenLakeEncounterRegistry.sealedChest.onBlockActivated(w,sealed.xCoord,sealed.yCoord,sealed.zCoord,p,1,0,0,0);
  check(sealed.getOpeningTicks()==0,"real sealed activation starts original visual sequence");
  for(int opening=0;opening<59;opening++)sealed.updateEntity();
  check(w.getBlock(sealed.xCoord,sealed.yCoord,sealed.zCoord)==ForgottenLakeEncounterRegistry.sealedChest,"original reward remains sealed before 60ticks");
  sealed.updateEntity();
  check(w.getTileEntity(sealed.xCoord,sealed.yCoord,sealed.zCoord) instanceof TileEntityUnsealedChest&&data.flag(s.id(),"claimed:"+reward.get("id").getAsString()),"60tick opening produces physical reward and claims once");
  TileEntityUnsealedChest physical=(TileEntityUnsealedChest)w.getTileEntity(sealed.xCoord,sealed.yCoord,sealed.zCoord);
  check(physical.getStackInSlot(10)!=null,"reward retains authored loot pool");
  check(RemasterRuntime.installNode(w,s,reward)&&w.getTileEntity(sealed.xCoord,sealed.yCoord,sealed.zCoord)==physical,"retry cannot recreate claimed sealed reward");
  TileRemasterNode oldDraft=tile(w,s,reward);w.blocks.put(w.key(oldDraft.xCoord,oldDraft.yCoord,oldDraft.zCoord),RemasterBlocks.get("gtsr:draft7_seal_chest"));w.metas.put(w.key(oldDraft.xCoord,oldDraft.yCoord,oldDraft.zCoord),0);
  check(RemasterRuntime.node(oldDraft)!=null,"legacy draft reward NBT retains authenticated ownership");
  data.state(s.id()).removeTag(rewardReference.startsWith("shape-")?"shape:"+rewardReference:"site-puzzle");
  w.supportY=70;int oldSpawnCount=w.spawns;
  EntityOldEcho grounded=RemasterSpawn.spawn(w,EchoKind.DR08,s.id(),500.5,80,500.5,4,false);
  check(grounded!=null&&grounded.posY==71,"ground birth descends to real support and anchors there");
  w.collision=true;check(RemasterSpawn.spawn(w,EchoKind.DR18,s.id(),500,90,500,5,false)==null,"flying birth rejects AABB collision");w.collision=false;
  w.liquid=true;check(RemasterSpawn.spawn(w,EchoKind.DR18,s.id(),500,90,500,5,false)==null,"flying birth also rejects world liquid-volume admission");w.liquid=false;
  w.loaded=false;check(RemasterSpawn.spawn(w,EchoKind.DR18,s.id(),500,90,500,5,false)==null,"flying birth rejects unloaded footprint");w.loaded=true;
  check(w.spawns==oldSpawnCount+1,"failed birth never admits entity or consumes quota");w.supportY=null;
  for(EchoKind flying:new EchoKind[]{EchoKind.DR18,EchoKind.DR15,EchoKind.DI03,EchoKind.DO02}){
   MotionProbe probe=new MotionProbe(w);probe.initializeEcho(flying,s.id(),500,90,500,false);probe.fallDistance=12;
   for(int tick=0;tick<100;tick++)probe.moveEntityWithHeading(0,0);
   check(probe.posY==90&&probe.motionY==0&&probe.fallDistance==0,"real movement physics has no idle/spawn gravity "+flying.code);
  }
  MotionProbe walker=new MotionProbe(w);walker.initializeEcho(EchoKind.DR08,s.id(),500,80,500,false);walker.setPosition(520,80,500);walker.ticksExisted=1;
  Method returnHome=EntityOldEcho.class.getDeclaredMethod("returnHome");returnHome.setAccessible(true);
  for(int tick=0;tick<110;tick++)returnHome.invoke(walker);
  check(walker.posX==520,"blocked ground return never teleports through walls after timeout");
  RemasterSite archive=new RemasterSite("archivist_kiosk",0,7,1500,80,1500);data.register(archive);
  p.capabilities.isCreativeMode=true;int readCount=0;
  for(JsonObject record:RemasterRuntime.nodes(archive))if(record.get("role").getAsString().equals("memory")&&!record.get("id").getAsString().equals("entrance-story-board")&&(!record.has("navigationHint")||!record.get("navigationHint").getAsBoolean())){
   TileRemasterNode recordTile=tile(w,archive,record);check(RemasterRuntime.action(p,recordTile,100),"actual original-record read action");
   readCount++;check(!RemasterRuntime.solved(w,archive.id(),"site-puzzle"),"all original-record reads retain parameter-verification gate and lit guidance");
  }
  p.capabilities.isCreativeMode=false;
  TileRemasterNode archiveHint=tile(w,archive,node(archive,"puzzle-object"));check(!RemasterRuntime.view(archiveHint).get("solved").getAsBoolean(),"reading retains unfinished guidance state");
  treeOverlayMapping();
  RemasterSite treeEntrance=new RemasterSite("forgotten_lake_court",0,17,38000,240,38000);data.register(treeEntrance);
  JsonObject treeBinding=treeEntrance.plan().metadata.getAsJsonObject("legacyEntranceBinding");check(treeBinding!=null,"production court supplies the natural entrance binding");JsonObject treeBoard=null;for(JsonObject candidate:RemasterRuntime.nodes(treeEntrance))if(candidate.get("id").getAsString().equals(treeBinding.get("node").getAsString()))treeBoard=candidate;check(treeBoard!=null,"binding preserves original story-board identity");String originalTreeNode=treeBoard.toString();
  int treeWrites=w.writes;check(RemasterRuntime.nodePosition(w,treeEntrance,treeBoard)==null&&!RemasterRuntime.installNode(w,treeEntrance,treeBoard)&&w.writes==treeWrites,"missing actual tree anchors defer the entrance without guessing scene coordinates");
  NBTTagCompound treeState=data.state(treeEntrance.id());treeState.setInteger("legacyAnchorX",38600);treeState.setInteger("legacyAnchorY",32);treeState.setInteger("legacyAnchorZ",38400);int[] treeAt=RemasterRuntime.nodePosition(w,treeEntrance,treeBoard);check(Arrays.equals(treeAt,new int[]{38576,33,38373})&&treeAt[1]!=treeEntrance.y+treeBoard.get("y").getAsInt()&&treeBoard.toString().equals(originalTreeNode),"true low tree platform overrides clamped high scene anchor without mutating cached source nodes");
  for(Block obstruction:new Block[]{Blocks.air,com.miaokatze.gtsr.common.blocks.BlocksGTSR.prosperityZenithLog,com.miaokatze.gtsr.common.blocks.BlocksGTSR.prosperityJadeLeaves,Blocks.stone}){w.blocks.put(w.key(treeAt[0],treeAt[1],treeAt[2]),obstruction);treeWrites=w.writes;check(!RemasterRuntime.installNode(w,treeEntrance,treeBoard)&&w.writes==treeWrites&&w.getBlock(treeAt[0],treeAt[1],treeAt[2])==obstruction,"completion never creates a bound entrance or replaces natural/player geometry");}
  String treeKey=treeBoard.get("block").getAsString();w.setBlock(treeAt[0],treeAt[1],treeAt[2],RemasterRuntime.resolve(treeKey),RemasterRuntime.meta(treeKey),3);TileEntity playerTreeBoard=w.getTileEntity(treeAt[0],treeAt[1],treeAt[2]);RemasterRuntime.recordGeneratedTile(w,treeEntrance,treeAt[0],treeAt[1],treeAt[2],playerTreeBoard);check(!RemasterRuntime.installNode(w,treeEntrance,treeBoard),"existing player notice receives no natural provenance from same-tile geometry recording");
  w.setBlock(treeAt[0],treeAt[1],treeAt[2],Blocks.air,0,3);TileEntity beforeTree=w.getTileEntity(treeAt[0],treeAt[1],treeAt[2]);w.setBlock(treeAt[0],treeAt[1],treeAt[2],RemasterRuntime.resolve(treeKey),RemasterRuntime.meta(treeKey),3);RemasterRuntime.recordGeneratedTile(w,treeEntrance,treeAt[0],treeAt[1],treeAt[2],beforeTree);TileRemasterNode naturalTree=(TileRemasterNode)w.getTileEntity(treeAt[0],treeAt[1],treeAt[2]);NBTTagCompound savedTree=new NBTTagCompound();naturalTree.writeToNBT(savedTree);TileRemasterNode reloadedTree=new TileRemasterNode();reloadedTree.readFromNBT(savedTree);reloadedTree.setWorldObj(w);w.tiles.put(w.key(treeAt[0],treeAt[1],treeAt[2]),reloadedTree);treeWrites=w.writes;check(RemasterRuntime.initializeNatural(reloadedTree)&&RemasterRuntime.valid(p,reloadedTree)&&w.writes==treeWrites,"natural entrance source survives NBT reload and activates at actual platform without rewriting geometry");
  TileRemasterNode sceneTree=tile(w,treeEntrance,treeBoard);check(!RemasterRuntime.valid(p,sceneTree)&&RemasterRuntime.node(reloadedTree)!=null,"source-local scene copy is invalid while saved actual tree-platform notice stays authoritative");
  treeEntrancePipeline();
  for(String regression:new String[]{"null-retry","pending-owner","legacy-v66"})treeCompletionRegressions(regression);
  RemasterSite treeGeometry=new RemasterSite("forgotten_lake_court",0,17,42000,32,42000,"tree-overlay");
  int[] treeGeometryWrites={0,0};
  for(int tx=Math.floorDiv(treeGeometry.minX(),16);tx<=Math.floorDiv(treeGeometry.maxX(),16);tx++)for(int tz=Math.floorDiv(treeGeometry.minZ(),16);tz<=Math.floorDiv(treeGeometry.maxZ(),16);tz++)
   RemasterWorldgen.geometry(treeGeometry,treeGeometry.plan(),(gx,gy,gz,block,meta,flags)->{String name=block.toString().toLowerCase(Locale.ROOT);treeGeometryWrites[0]++;if(name.contains("wood")||name.contains("leaves")||name.contains("zenithlog")||name.startsWith("gtsr:royal_zenith_log"))treeGeometryWrites[1]++;return true;},tx,tz);
  check(treeGeometryWrites[0]>0&&treeGeometryWrites[1]==0,"actual overlay geometry writes interactive detail without duplicating natural logs/leaves/wood stairs at scene anchor");
  JsonObject authoredNavigation=null;for(JsonObject record:RemasterRuntime.nodes(archive))if(record.has("navigationHint")&&record.get("navigationHint").getAsBoolean()){authoredNavigation=record;break;}
  check(authoredNavigation!=null,"archive supplies an authored local navigation plaque");
  RemasterSite navigationSite=new RemasterSite("archivist_kiosk",0,17,32000,80,32000);data.register(navigationSite);int navX=navigationSite.x+authoredNavigation.get("x").getAsInt(),navY=navigationSite.y+authoredNavigation.get("y").getAsInt(),navZ=navigationSite.z+authoredNavigation.get("z").getAsInt();
  RemasterWorldgen.placeChunk(w,navigationSite,navX>>4,navZ>>4);TileEntity navPhysical=w.getTileEntity(navX,navY,navZ);
  check(navPhysical instanceof TileRemasterNode&&data.flag(navigationSite.id(),"node:"+authoredNavigation.get("id").getAsString()),"production geometry installs and registers natural navigation plaque");
  TileRemasterNode navTile=(TileRemasterNode)navPhysical;JsonObject navView=RemasterRuntime.view(navTile);JsonArray navLocal=authoredNavigation.getAsJsonArray("guideTarget"),navAbsolute=navView.getAsJsonArray("guideTarget");
  check(navAbsolute.get(0).getAsInt()==navigationSite.x+navLocal.get(0).getAsInt()&&navAbsolute.get(1).getAsInt()==navigationSite.y+navLocal.get(1).getAsInt()&&navAbsolute.get(2).getAsInt()==navigationSite.z+navLocal.get(2).getAsInt(),"authored local direction is displayed as authoritative absolute destination");
  check(navView.get("readOnly").getAsBoolean()&&!navView.get("solved").getAsBoolean()&&navView.get("actionLabel").getAsString().contains("方向"),"navigation exposes explicit direction-only interaction without puzzle completion");
  String playerBeforeNav=p.getEntityData().toString();int entitiesBeforeNav=w.spawns,messagesBeforeNav=p.messages;p.capabilities.isCreativeMode=true;
  check(w.getBlock(navX,navY,navZ).onBlockActivated(w,navX,navY,navZ,p,1,0,0,0)&&p.messages>messagesBeforeNav&&p.lastMessage.equals(authoredNavigation.get("text").getAsString()),"actual natural plaque right click gives readable creative-safe direction feedback");
  check(RemasterRuntime.action(p,navTile,100)&&playerBeforeNav.equals(p.getEntityData().toString())&&w.spawns==entitiesBeforeNav&&!RemasterRuntime.solved(w,navigationSite.id(),"site-puzzle")&&!data.state(navigationSite.id()).hasKey("engineering"),"navigation read writes no history/evidence/engineering and never issues originals or solves a puzzle");p.capabilities.isCreativeMode=false;
  RemasterSite courtNavigation=new RemasterSite("forgotten_lake_court",0,17,36000,80,36000);data.register(courtNavigation);JsonObject courtDirection=null;for(JsonObject candidate:RemasterRuntime.nodes(courtNavigation))if(candidate.has("navigationHint")&&candidate.get("navigationHint").getAsBoolean()){courtDirection=candidate;break;}check(courtDirection!=null,"court supplies a separate authored navigation plaque");
  TileRemasterNode courtDirectionTile=tile(w,courtNavigation,courtDirection);String oldCourt="fixture-navigation-legacy-court";data.state(courtNavigation.id()).setString("legacyEncounter",oldCourt);ForgottenLakeEncounterData.get(w).registerLayout(oldCourt,3);ForgottenLakeEncounterData.get(w).kingDied(oldCourt);int beforeDirectionTick=w.spawns;w.time=300;RemasterRuntime.tick(courtDirectionTile);
  check(w.spawns==beforeDirectionTick&&!data.flag(courtNavigation.id(),"witness-issued-once:dc-10"),"court navigation tick never impersonates original royal evidence or emits unique originals");
  for(JsonElement identity:RemasterWitness.roster()){
   String code=identity.getAsString(),source;
   if(code.equals("dc-02"))source="fallen_foundry";else if(code.equals("dc-08"))source="subsided_factory";else if(code.equals("dc-10"))source="forgotten_lake_court";
   else source=RemasterCatalog.config().getAsJsonObject("story").getAsJsonObject("entities").getAsJsonObject(code).getAsJsonArray("sites").get(0).getAsString();
   RemasterSite origin=new RemasterSite(source,0,7,2000,80,2000);data.register(origin);
   net.minecraft.item.ItemStack witness=RemasterWitness.create(origin,code,"fixture");
   check(witness!=null&&!RemasterWitness.authentic(witness,w,code),"unissued identity rejected "+code);
   data.flag(origin.id(),"witness-issued:"+code+":fixture",true);
   check(RemasterWitness.authentic(witness,w,code),"authored issued identity accepted "+code);
  }
  TileRemasterNode t=tile(w,s,node(s,"control"));check(RemasterRuntime.valid(p,t),"real survival accepted");
  p.capabilities.isCreativeMode=true;check(RemasterRuntime.valid(p,t),"creative can inspect authentic console");p.capabilities.isCreativeMode=false;
  p.living=false;check(!RemasterRuntime.valid(p,t),"dead rejected");p.living=true;
  w.loaded=false;check(!RemasterRuntime.valid(p,t),"unloaded rejected");w.loaded=true;
  t.nodeId="forged";check(!RemasterRuntime.valid(p,t),"forged ownership rejected");t.nodeId=node(s,"control").get("id").getAsString();
  for(int button:new int[]{2,3,1,0})check(RemasterRuntime.action(p,t,button),"ordered valve action");
  check(!RemasterRuntime.action(p,t,100),"pressure confirmation rejects uncleared original side-line guards");
  Set<String> requiredModules=new HashSet<>();
  for(JsonObject controller:RemasterRuntime.nodes(s)){
   String module=RemasterOriginalContract.guardModule(s,RemasterRuntime.string(controller,"id",""));
   if(module.isEmpty())continue;requiredModules.add(module);
   TileRemasterNode installed=controller.get("id").getAsString().equals(t.nodeId)?t:tile(w,s,controller);
   check(RemasterRuntime.installNode(w,s,controller),"original side-line controller production binding");data.flag(s.id(),"node:"+installed.nodeId,true);
  }
  JsonArray originalSpawns=RemasterRuntime.array(s.plan().metadata,"spawns");
  for(int i=0;i<originalSpawns.size();i++){
   JsonObject spawn=originalSpawns.get(i).getAsJsonObject();
   if(RemasterOriginalContract.eligibleGuard(spawn)&&requiredModules.contains(RemasterRuntime.string(spawn,"module",""))){data.flag(s.id(),"entity:"+i,true);RemasterRuntime.death(w,s.id(),i);}
  }
  check(RemasterRuntime.action(p,t,100),"pressure verification");
  for(int i=1;i<239;i++){w.time=i;RemasterRuntime.tick(t);}
  check(!RemasterRuntime.solved(w,s.id(),"site-puzzle"),"pressure needs continuous settled ticks");
  w.time=239;RemasterRuntime.tick(t);check(RemasterRuntime.solved(w,s.id(),"site-puzzle"),"settled pressure accepted");
  data.flag(s.id(),"claimed:fixture",true);check(RemasterRuntime.action(p,t,101),"safe reset");
  check(!RemasterRuntime.solved(w,s.id(),"site-puzzle")&&data.flag(s.id(),"claimed:fixture"),"reset preserves claimed chest");
  NBTTagCompound saved=new NBTTagCompound();data.writeToNBT(saved);RemasterData reloaded=new RemasterData();reloaded.readFromNBT(saved);
  check(reloaded.flag(s.id(),"claimed:fixture"),"real MC NBT reload keeps one-shot ledger");
  TileRemasterNode shape=tile(w,s,node(s,"shape"));JsonObject spec=null;
  for(JsonElement e:s.plan().metadata.getAsJsonArray("shapeMechanisms"))if(shape.nodeId.equals("shape:"+e.getAsJsonObject().get("id").getAsString()))spec=e.getAsJsonObject();
  for(JsonElement e:spec.getAsJsonArray("delta")){JsonObject d=e.getAsJsonObject();JsonArray a=d.getAsJsonArray("at");String b=d.get("closed").isJsonNull()?"minecraft:air#0":d.get("closed").getAsString();
   int x=s.x+a.get(0).getAsInt(),y=s.y+a.get(1).getAsInt(),z=s.z+a.get(2).getAsInt();w.blocks.put(w.key(x,y,z),RemasterRuntime.resolve(b));w.metas.put(w.key(x,y,z),RemasterRuntime.meta(b));}
  Method apply=RemasterRuntime.class.getDeclaredMethod("applyShape",TileRemasterNode.class,boolean.class);apply.setAccessible(true);
  Map original=new HashMap(w.blocks);Map originalMeta=new HashMap(w.metas);w.writes=0;w.failWrite=2;
  check(!(Boolean)apply.invoke(null,shape,true),"injected write fault rejected");check(w.blocks.equals(original)&&w.metas.equals(originalMeta),"partial writes rolled back");
  w.failWrite=0;check((Boolean)apply.invoke(null,shape,true),"shape opens actual world geometry");
  w.occupied=true;check(!(Boolean)apply.invoke(null,shape,false),"closing onto entity rejected");w.occupied=false;
  check((Boolean)apply.invoke(null,shape,false),"shape reversible after vacating");
  RemasterSite boiler=new RemasterSite("boiler_shrine",0,7,1000,80,1000);data.register(boiler);
  TileRemasterNode cage=tile(w,boiler,node(boiler,"spawner"));JsonObject cageSpec=RemasterRuntime.node(cage);
  NBTTagCompound state=data.state(boiler.id()),spawner=new NBTTagCompound();state.setTag("spawner:"+cage.nodeId,spawner);
  int quota=cageSpec.getAsJsonObject("policy").get("sealAt").getAsInt();spawner.setInteger("count",quota-1);spawner.setLong("next",0);
  w.time=400;int old=w.spawns;RemasterRuntime.tick(cage);
  check(w.spawns-old==1&&spawner.getInteger("count")==quota,"last batch clamps to one successful birth");
  check(data.flag(boiler.id(),"sealed:"+cage.nodeId)&&spawner.getLong("unsealAt")==36400,"exact 36000tick seal deadline persisted");
  RemasterRuntime.initializeLoaded(cage);check(spawner.getLong("next")==500,"reload prevents catch-up spawning");
  w.time=36400;RemasterRuntime.tick(cage);check(!data.flag(boiler.id(),"sealed:"+cage.nodeId)&&spawner.getInteger("count")==0,"quota resets after unseal");
  RemasterSite fiction=new RemasterSite("fiction_expansion_project",0,7,4000,80,4000);data.register(fiction);
  TileRemasterNode console=tile(w,fiction,node(fiction,"control"));
  NBTTagCompound engineering=new NBTTagCompound();data.state(fiction.id()).setTag("engineering",engineering);
  TileRemasterNode station4=null;for(JsonObject station:RemasterRuntime.nodes(fiction))if(station.get("id").getAsString().equals("fiction-station-4"))station4=tile(w,fiction,station);
  engineering.setInteger("chapter",4);engineering.setBoolean("evidence:4",true);JsonArray stableAnswer=RemasterEngineering.spec(station4).getAsJsonArray("answer");for(int i=0;i<stableAnswer.size();i++)engineering.setInteger("field"+i,stableAnswer.get(i).getAsInt());
  check(RemasterRuntime.action(p,station4,100),"engineering stable test begins at actual station");
  for(int tick=1;tick<=99;tick++){w.time=tick+40000;RemasterRuntime.tick(console);RemasterRuntime.tick(station4);}
  check(engineering.getInteger("stableTicks")==99,"other console cannot steal stable-station tick");
  engineering.setInteger("field0",stableAnswer.get(0).getAsInt()+1);w.time++;RemasterRuntime.tick(station4);check(engineering.getInteger("stableTicks")==0,"changed engineering condition invalidates continuous stability");
  engineering.setBoolean("pending",false);
  engineering.setInteger("chapter",7);engineering.setInteger("phase",2);
  for(JsonElement identity:RemasterWitness.roster())engineering.setBoolean("testimony:"+identity.getAsString(),true);
  Method finish=RemasterEngineering.class.getDeclaredMethod("finish",EntityPlayer.class,TileRemasterNode.class);finish.setAccessible(true);
  w.time=50000;old=w.spawns;finish.invoke(null,null,console);
  check(w.spawns==old+1,"finale appears once as real entity");
  com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho finale=null;
  for(Object entity:w.loadedEntityList)if(entity instanceof com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho&&((com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho)entity).getKind()==com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind.DO02)finale=(com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho)entity;
  check(finale!=null&&!finale.isDead&&finale.getCustomNameTag().contains("99*"),"complete DO02 and 99* remain alive for tracker");
  w.time=50019;RemasterEngineering.updateFinale(w,fiction.id());check(!finale.isDead,"visible through nineteen server ticks");
  NBTTagCompound visibleEntitySave=new NBTTagCompound();finale.writeToNBT(visibleEntitySave);
  NBTTagCompound finaleSave=new NBTTagCompound();data.writeToNBT(finaleSave);RemasterData finaleReload=new RemasterData();finaleReload.readFromNBT(finaleSave);
  w.perWorldStorage.setData("gtsr.prosperityRemaster7",finaleReload);data=finaleReload;
  w.time=50020;new RemasterRuntime.FinaleTicker().worldTick(new cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent(cpw.mods.fml.relauncher.Side.SERVER,cpw.mods.fml.common.gameevent.TickEvent.Phase.END,w));check(finale.isDead,"persistent deadline erases original after twenty ticks even with entity-only reloading");
  old=w.spawns;RemasterEngineering.updateFinale(w,fiction.id());check(w.spawns==old,"reloaded final ledger never respawns DO02");
  com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho lateFinale=new com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho(w);lateFinale.readFromNBT(visibleEntitySave);w.loadedEntityList.add(lateFinale);
  RemasterEngineering.updateFinale(w,fiction.id());check(lateFinale.isDead,"late-loaded original UUID is removed without replay");
  TileRemasterNode fictionCage=tile(w,fiction,node(fiction,"spawner"));
  RemasterRuntime.initializeLoaded(fictionCage);RemasterRuntime.tick(fictionCage);
  NBTTagCompound disabled=data.state(fiction.id()).getCompoundTag("spawner:"+fictionCage.nodeId);
  check(disabled.getBoolean("permanentlyDisabled")&&disabled.getLong("unsealAt")==Long.MAX_VALUE,"engineering source permanently stops on load");
  old=w.spawns;w.time=90000;RemasterRuntime.tick(fictionCage);check(w.spawns==old&&data.flag(fiction.id(),"sealed:"+fictionCage.nodeId),"final source cannot restart at old seal deadline");
  RemasterSite city=new RemasterSite("prosperity_city_full",0,7,8000,80,8000);data.register(city);
  JsonArray citySpawns=city.plan().metadata.getAsJsonArray("spawns");int di07=-1;String di07Id="";
  for(int i=0;i<citySpawns.size();i++)if(citySpawns.get(i).getAsJsonObject().get("code").getAsString().equals("di-07")){di07=i;di07Id=citySpawns.get(i).getAsJsonObject().get("id").getAsString();break;}
  check(di07>=0,"natural city parent has authored di07 identity");
  NBTTagCompound relocated=new NBTTagCompound();relocated.setDouble("x",8720.5);relocated.setDouble("y",81);relocated.setDouble("z",8192.5);data.state(city.id()).setTag("spawn-pos:"+di07Id,relocated);
  old=w.spawns;RemasterRuntime.death(w,city.id(),di07);check(w.spawns==old+1,"citygrid authored di07 death issues one original");
  net.minecraft.entity.item.EntityItem originalDrop=(net.minecraft.entity.item.EntityItem)w.loadedEntityList.get(w.loadedEntityList.size()-1);
  check(originalDrop.posX==8720.5&&originalDrop.posZ==8192.5&&RemasterWitness.authentic(originalDrop.getEntityItem(),w,"di-07"),"city original uses persisted natural plaza coordinates and authenticated source");
  RemasterRuntime.death(w,city.id(),di07);check(w.spawns==old+1,"repeated city death cannot duplicate original");
  JsonObject pipelineChest=node(pipeline,"chest");int chestX=pipeline.x+pipelineChest.get("x").getAsInt(),chestY=pipeline.y+pipelineChest.get("y").getAsInt(),chestZ=pipeline.z+pipelineChest.get("z").getAsInt();
  check(RemasterWorldgen.placeGeometryChunk(w,pipeline,chestX>>4,chestZ>>4),"production reward geometry chunk succeeds");
  TileEntity naturalChest=w.getTileEntity(chestX,chestY,chestZ);
  check(naturalChest instanceof TileEntitySealedChest&&((TileEntitySealedChest)naturalChest).getRemasterSite().isEmpty(),"production palette creates original empty sealed chest rather than new draft chest");
  NBTTagCompound naturalChestSave=new NBTTagCompound();naturalChest.writeToNBT(naturalChestSave);TileEntitySealedChest naturalChestReload=new TileEntitySealedChest();naturalChestReload.readFromNBT(naturalChestSave);naturalChestReload.setWorldObj(w);w.tiles.put(w.key(chestX,chestY,chestZ),naturalChestReload);
  RemasterWorldgen.placeChunk(w,pipeline,chestX>>4,chestZ>>4);
  TileEntitySealedChest boundNaturalChest=(TileEntitySealedChest)w.getTileEntity(chestX,chestY,chestZ);
  check(boundNaturalChest.isRemasterNode(pipeline.id(),pipelineChest.get("id").getAsString())&&data.flag(pipeline.id(),"node:"+pipelineChest.get("id").getAsString()),"original natural chest NBT reload binds and records installation");
  TileEntitySealedChest tornChestReload=new TileEntitySealedChest();tornChestReload.readFromNBT(naturalChestSave);tornChestReload.setWorldObj(w);w.tiles.put(w.key(chestX,chestY,chestZ),tornChestReload);beforeRecoveryWrites=w.writes;tornChestReload.updateEntity();check(tornChestReload.isRemasterNode(pipeline.id(),pipelineChest.get("id").getAsString())&&w.writes==beforeRecoveryWrites,"original chest load callback recovers natural token without population or replacement");boundNaturalChest=tornChestReload;
  p.capabilities.isCreativeMode=true;check(ForgottenLakeEncounterRegistry.sealedChest.onBlockActivated(w,chestX,chestY,chestZ,p,1,0,0,0)&&boundNaturalChest.getOpeningTicks()<0,"actual generated reward activation keeps creative preview reward-free");p.capabilities.isCreativeMode=false;
  TileRemasterNode archiveConsole=tile(w,archive,node(archive,"control"));JsonArray archiveTargets=archive.plan().metadata.getAsJsonObject("productionPuzzle").getAsJsonArray("target");
  check(RemasterRuntime.action(p,archiveConsole,100)&&!RemasterRuntime.solved(w,archive.id(),"site-puzzle"),"read-complete archive still rejects incorrect console parameters");
  for(int i=0;i<archiveTargets.size();i++)for(int cycle=0;cycle<archiveTargets.get(i).getAsInt();cycle++)check(RemasterRuntime.action(p,archiveConsole,i),"archive actual console parameter interaction");
  check(RemasterRuntime.action(p,archiveConsole,100)&&RemasterRuntime.solved(w,archive.id(),"site-puzzle"),"personal evidence plus actual console parameters complete archive");
  System.out.println("passed: "+checks+" real MC authority/pressure/atomic-shape/NBT/spawner/pipeline/worldTick assertions");
 }
}
