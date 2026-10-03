import java.lang.reflect.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.entity.player.*;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.*;
import com.google.gson.*;
import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.*;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.*;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.*;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.*;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Fresh production sources and real MC NBT; fixture adapts loaded storage and FML startup only. */
public final class OriginalContractCheck extends RemasterRuntimeCheck {
 static class Q extends P {
  Q(){super();}
  public double getDistanceSq(double x,double y,double z){return (x-posX)*(x-posX)+(y-posY)*(y-posY)+(z-posZ)*(z-posZ);}
 }
 static class SW extends WorldServer {
  W storage;
  SW(){super(null,null,null,0,null,null);}
  public long getSeed(){return 7717;}
  public long getTotalWorldTime(){return storage.time;}
  public boolean blockExists(int x,int y,int z){return storage.blockExists(x,y,z);}
  public IChunkProvider getChunkProvider(){return storage.getChunkProvider();}
  public Block getBlock(int x,int y,int z){return storage.getBlock(x,y,z);}
  public int getBlockMetadata(int x,int y,int z){return storage.getBlockMetadata(x,y,z);}
  public TileEntity getTileEntity(int x,int y,int z){return storage.getTileEntity(x,y,z);}
  public void markTileEntityChunkModified(int x,int y,int z,TileEntity tile){}
  public void markBlockForUpdate(int x,int y,int z){}
  public boolean func_147451_t(int x,int y,int z){return true;}
  public boolean spawnEntityInWorld(net.minecraft.entity.Entity e){loadedEntityList.add(e);return true;}
 }
 static void init()throws Exception {
  Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);u=(sun.misc.Unsafe)uf.get(null);
  Class.forName("net.minecraft.init.Blocks");Constructor<BlockAir> air=BlockAir.class.getDeclaredConstructor();air.setAccessible(true);setBlock("air",air.newInstance());setBlock("stone",new BlockStone());
  com.miaokatze.gtsr.common.blocks.BlocksGTSR.prosperityZenithLog=new com.miaokatze.gtsr.common.dimension.prosperity.architecture.BlockZenithLog("ProsperityZenithLog","gtsr:prosperity_zenith_log_side","gtsr:prosperity_zenith_log_top");
  com.miaokatze.gtsr.common.dimension.prosperity.architecture.RuinsArchitecture.registerBlocks();
  com.miaokatze.gtsr.common.dimension.prosperity.architecture.RoyalArchitecture.registerBlocks();
  ForgottenLakeEncounterRegistry.sealedChest=new BlockSealedChest();ForgottenLakeEncounterRegistry.unsealedChest=new BlockUnsealedChest();
  Field registry=RemasterBlocks.class.getDeclaredField("REGISTRY");registry.setAccessible(true);Map map=(Map)registry.get(null);
  for(int i=0;i<94;i++){Method f=RemasterBlocks.class.getDeclaredMethod("block"+i);f.setAccessible(true);RemasterBlock b=(RemasterBlock)f.invoke(null);map.put(b.id(),b);}
 }
 static Q player(World w)throws Exception {
  Q p=(Q)u.allocateInstance(Q.class);p.living=true;p.worldObj=w;p.capabilities=new PlayerCapabilities();p.inventory=new InventoryPlayer(p);return p;
 }
 static JsonObject named(RemasterSite s,String id){for(JsonObject n:RemasterRuntime.nodes(s))if(id.equals(n.get("id").getAsString()))return n;throw new AssertionError(id);}
 static void targetFields(TileRemasterNode t)throws Exception {
  JsonObject puzzle=RemasterData.get(t.getWorldObj()).site(t.siteId).plan().metadata.getAsJsonObject("productionPuzzle");JsonArray targets=puzzle.getAsJsonArray("target");
  if(targets==null)targets=puzzle.getAsJsonArray("answer");NBTTagCompound state=RemasterData.get(t.getWorldObj()).state(t.siteId);
  NBTTagCompound p=new NBTTagCompound();for(int i=0;i<targets.size();i++)p.setInteger("field"+i,targets.get(i).getAsInt());
  p.setInteger("progress",RemasterRuntime.array(puzzle,"order").size());state.setTag("site-puzzle",p);
 }
 static void guards()throws Exception {
  W w=isolatedWorld(7);RemasterSite s=new RemasterSite("fallen_foundry",0,7,0,80,0);RemasterData d=RemasterData.get(w);d.register(s);
  TileRemasterNode t=tile(w,s,named(s,"control-0"));Q p=player(w);p.posX=t.xCoord+.5;p.posY=t.yCoord+.5;p.posZ=t.zCoord+.5;targetFields(t);
  check(!RemasterRuntime.action(p,t,100),"live required side-line guards reject production control confirmation");
  NBTTagCompound state=d.state(s.id()).getCompoundTag("site-puzzle");check(!state.getBoolean("pending")&&!state.getBoolean("solved"),"guard refusal leaves puzzle uncommitted");
  state.setBoolean("solved",true);check(!RemasterRuntime.bossReady(w,s.id()),"shared solved alone cannot wake boss while required guards live");state.setBoolean("solved",false);
  JsonArray authored=RemasterRuntime.array(s.plan().metadata,"spawns");for(int i=0;i<authored.size();i++){JsonObject n=authored.get(i).getAsJsonObject();if("fallen_foundry-module-1".equals(RemasterRuntime.string(n,"module",""))){d.flag(s.id(),"entity:"+i,true);RemasterRuntime.death(w,s.id(),i);}}
  check(RemasterRuntime.action(p,t,100),"only corresponding first side-line dead admits first controller");check(state.getBoolean("pending"),"actual stability begins after guard acceptance");
  state.setBoolean("solved",true);check(!RemasterRuntime.bossReady(w,s.id()),"other required lines still gate boss");
  String[] big={"fallen_foundry","subsided_factory","boiler_shrine","weaving_mill","sniper_watch","mirror_barracks","resonant_station"};
  for(String id:big)for(int v=0;v<3;v++){
   W matrix=isolatedWorld(27);RemasterSite site=new RemasterSite(id,v,27,4000,110,4000);RemasterData ledger=RemasterData.get(matrix);ledger.register(site);Q actor=player(matrix);
   List<TileRemasterNode> controls=new ArrayList<>();Set<String> modules=new HashSet<>();
   for(JsonObject n:RemasterRuntime.nodes(site))if("control".equals(RemasterRuntime.string(n,"role",""))&&!RemasterOriginalContract.guardModule(site,RemasterRuntime.string(n,"id","")).isEmpty()){
    TileRemasterNode control=tile(matrix,site,n);controls.add(control);modules.add(RemasterOriginalContract.guardModule(site,control.nodeId));
    check(RemasterRuntime.installNode(matrix,site,n),"actual production installs authored controller "+id+v+control.nodeId);ledger.flag(site.id(),"node:"+control.nodeId,true);
   }
   check(!controls.isEmpty(),"matrix has real original controllers "+id+v);targetFields(controls.get(0));
   NBTTagCompound puzzle=ledger.state(site.id()).getCompoundTag("site-puzzle");puzzle.setBoolean("solved",true);
   check(!RemasterRuntime.bossReady(matrix,site.id()),"hand-written solved cannot bypass guards and installed original controls "+id+v);puzzle.setBoolean("solved",false);
   JsonArray spawns=RemasterRuntime.array(site.plan().metadata,"spawns");int required=0,optional=0;
   for(int i=0;i<spawns.size();i++){JsonObject spawn=spawns.get(i).getAsJsonObject();if(RemasterOriginalContract.eligibleGuard(spawn)&&modules.contains(RemasterRuntime.string(spawn,"module",""))){required++;ledger.flag(site.id(),"entity:"+i,true);RemasterRuntime.death(matrix,site.id(),i);}else optional++;}
   check(required>0,"frozen semantic zones have admitted guards "+id+v);
   for(int i=0;i<controls.size();i++){
    TileRemasterNode control=controls.get(i);actor.posX=control.xCoord+.5;actor.posY=control.yCoord+.5;actor.posZ=control.zCoord+.5;
    check(RemasterRuntime.action(actor,control,100),"required indexed actual deaths permit corresponding confirmation "+id+v+control.nodeId);
    if(i+1<controls.size()){puzzle.setBoolean("solved",true);check(!RemasterRuntime.bossReady(matrix,site.id()),"every original controller needs separate confirmation "+id+v);puzzle.setBoolean("solved",false);}
   }
   for(int tick=1;tick<=241;tick++){matrix.time=tick;RemasterRuntime.tick(controls.get(0));}
   check(RemasterRuntime.solved(matrix,site.id(),"site-puzzle")&&RemasterRuntime.bossReady(matrix,site.id()),"all and only required zones plus true controls and stable puzzle wake boss "+id+v);
   check(!ledger.flag(site.id(),"boss-dead"),"declared boss death never becomes a precondition "+id+v);
   NBTTagCompound saved=new NBTTagCompound();ledger.writeToNBT(saved);RemasterData restored=new RemasterData();restored.readFromNBT(saved);matrix.perWorldStorage.setData("gtsr.prosperityRemaster7",restored);
   check(RemasterRuntime.bossReady(matrix,site.id()),"real MC NBT preserves completed original gate "+id+v);
  }
 }
 static void story()throws Exception {
  W w=isolatedWorld(9);RemasterSite s=new RemasterSite("trench_infirmary",0,9,0,80,0);RemasterData.get(w).register(s);
  TileRemasterNode a=tile(w,s,named(s,"objective-0")),b=tile(w,s,named(s,"objective-1"));
  String one=RemasterRuntime.view(a).get("clue").getAsString(),two=RemasterRuntime.view(b).get("clue").getAsString();
  check(one.contains("不要先问阵营"),"real GUI payload preserves infirmary treatment original text");
  check(two.contains("给对面战壕那个人")&&two.contains("还救过人"),"real GUI payload preserves unique enemy rescue medicine record");
  check(!one.contains("最后被救下")&&!two.contains("医者划去了"),"completion ending is not shown before completion");
  for(JsonObject n:RemasterRuntime.nodes(s))if(n.has("navigationHint")&&n.get("navigationHint").getAsBoolean()){
   TileRemasterNode nav=tile(w,s,n);String clue=RemasterRuntime.view(nav).get("clue").getAsString();
   check(clue.equals(RemasterRuntime.string(n,"text","")),"pure navigation payload contains only its authored directions");
   check(!clue.contains("不要先问阵营")&&!clue.contains("本人现场"),"navigation carries neither original narrative nor puzzle parameter text");
  }
 }
 static NBTTagCompound history(Q p){return p.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getCompoundTag("gtsr.lostChronicleEvidence");}
 static boolean has(Q p,String key,int k){NBTTagList list=history(p).getTagList(key,8);for(int i=0;i<list.tagCount();i++)if(Integer.toString(k).equals(list.getStringTagAt(i)))return true;return false;}
 static void history()throws Exception {
  SW w=(SW)u.allocateInstance(SW.class);w.storage=isolatedWorld(7717);w.storage.time=20;
  set(w,World.class,"perWorldStorage",new MapStorage((ISaveHandler)null));set(w,World.class,"provider",u.allocateInstance(WorldProviderProsperityRuins.class));set(w,World.class,"loadedEntityList",new ArrayList<>());
  Q p=player(w);set(w,World.class,"playerEntities",new ArrayList<>(Collections.singletonList(p)));
  RemasterSite s=new RemasterSite("trench_infirmary",0,7717,1600,80,1600);RemasterData data=RemasterData.get(w);data.register(s);
  JsonObject record=named(s,"objective-0");TileRemasterNode t=tile(w.storage,s,record);t.setWorldObj(w);p.posX=t.xCoord+.5;p.posY=t.yCoord+.5;p.posZ=t.zCoord+.5;
  HistoryEvents events=new HistoryEvents();TickEvent.WorldTickEvent tick=new TickEvent.WorldTickEvent(cpw.mods.fml.relauncher.Side.SERVER,TickEvent.Phase.END,w);
  check(RuinsEncounterData.get(w).existingSitesNear(s.x,s.z,512).isEmpty(),"fresh revision-seven world has no persisted legacy sites");
  events.tick(tick);check(!has(p,"visited",12),"registered but ungenerated site grants no visit");
  data.flag(s.id(),"geom:"+(t.xCoord>>4)+":"+(t.zCoord>>4),true);
  w.storage.loaded=false;events.tick(tick);check(!has(p,"visited",12),"unloaded generated owner grants no visit");w.storage.loaded=true;
  double originalY=p.posY;p.posY=254;events.tick(tick);check(!has(p,"visited",12),"wrong altitude beyond real prefab bound grants no visit");p.posY=originalY;
  p.capabilities.isCreativeMode=true;events.tick(tick);check(!has(p,"visited",12),"creative preview grants no original visit");p.capabilities.isCreativeMode=false;events.tick(tick);
  check(has(p,"visited",12),"actual HistoryEvents.tick bridges generated revision-seven site visit");
  check(!has(p,"completed",12),"reading one record does not complete unfinished puzzle");
  // A shared solved flag cannot hand an exploration ending to another player passing the endpoint.
  JsonObject endpoint=null;for(JsonObject n:RemasterRuntime.nodes(s))if("chest".equals(RemasterRuntime.string(n,"role",""))&&"site-puzzle".equals(RemasterRuntime.string(n,"reference",""))&&"puzzle-completed".equals(RemasterRuntime.string(n,"unlock",""))){endpoint=n;break;}
  check(endpoint!=null,"exploration has real final site-puzzle endpoint");int[] at=RemasterRuntime.nodePosition(w,s,endpoint);
  TileEntitySealedChest chest=new TileEntitySealedChest();chest.setWorldObj(w);chest.xCoord=at[0];chest.yCoord=at[1];chest.zCoord=at[2];chest.initializeRemaster(RemasterRuntime.integer(endpoint,"tier",1),s.id(),endpoint.get("id").getAsString());
  w.storage.blocks.put(w.storage.key(at[0],at[1],at[2]),ForgottenLakeEncounterRegistry.sealedChest);w.storage.metas.put(w.storage.key(at[0],at[1],at[2]),2);w.storage.tiles.put(w.storage.key(at[0],at[1],at[2]),chest);
  chest.updateContainingBlockInfo();
  data.flag(s.id(),"node:"+endpoint.get("id").getAsString(),true);data.flag(s.id(),"geom:"+(at[0]>>4)+":"+(at[2]>>4),true);
  NBTTagCompound puzzle=new NBTTagCompound();puzzle.setBoolean("solved",true);data.state(s.id()).setTag("site-puzzle",puzzle);
  Q passer=player(w);passer.posX=at[0]+.5;passer.posY=at[1]+.5;passer.posZ=at[2]+.5;set(w,World.class,"playerEntities",new ArrayList<>(Collections.singletonList(passer)));events.tick(tick);
  check(!has(passer,"completed",12),"shared solved and true final chest do not grant another player's exploration route");
  p=player(w);
  set(w,World.class,"playerEntities",new ArrayList<>(Collections.singletonList(p)));
  List<JsonObject> route=RemasterOriginalContract.records(s);check(route.size()==2,"medical preserves exactly two original personal memory landmarks");
  // Read the last record out of order first; then visit and read every real record in source order.
  JsonObject last=route.get(route.size()-1);TileRemasterNode lastTile=tile(w.storage,s,last);lastTile.setWorldObj(w);p.posX=lastTile.xCoord+.5;p.posY=lastTile.yCoord+.5;p.posZ=lastTile.zCoord+.5;
  check(RemasterRuntime.action(p,lastTile,100),"real final record can be inspected out of order");
  check(!p.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getBoolean("gtsr.r7.route:"+s.id()+":"+lastTile.nodeId),"out-of-order read cannot skip personal route order");
  for(JsonObject n:route){TileRemasterNode readable=tile(w.storage,s,n);readable.setWorldObj(w);p.posX=readable.xCoord+.5;p.posY=readable.yCoord+.5;p.posZ=readable.zCoord+.5;check(RemasterRuntime.action(p,readable,100),"actual personal record action "+readable.nodeId);}
  p.posX=at[0]+.5;p.posY=at[1]+.5;p.posZ=at[2]+.5;events.tick(tick);
  check(has(p,"completed",12),"actual HistoryEvents.tick completes true puzzle plus personal ordered records at real endpoint");
  NBTTagCompound playerSave=p.getEntityData().copy() instanceof NBTTagCompound?(NBTTagCompound)p.getEntityData().copy():null;
  Q reload=player(w);reload.personal=playerSave;check(has(reload,"visited",12)&&has(reload,"completed",12),"actual player NBT persists remaster original visit and ending");
 }
 static void endpoints()throws Exception {
  for(int k=7;k<27;k++)for(int v=0;v<3;v++){
   String id=RuinSite.NAMES[k];SW w=(SW)u.allocateInstance(SW.class);w.storage=isolatedWorld(7717);w.storage.time=20;
   set(w,World.class,"perWorldStorage",new MapStorage((ISaveHandler)null));set(w,World.class,"provider",u.allocateInstance(WorldProviderProsperityRuins.class));set(w,World.class,"loadedEntityList",new ArrayList<>());
   Q p=player(w);set(w,World.class,"playerEntities",new ArrayList<>(Collections.singletonList(p)));RemasterSite s=new RemasterSite(id,v,7717,2400,110,2400);RemasterData d=RemasterData.get(w);d.register(s);
   JsonObject endpoint=null;for(JsonObject n:RemasterRuntime.nodes(s))if("chest".equals(RemasterRuntime.string(n,"role",""))&&!"exploration".equals(RemasterRuntime.string(n,"kind",""))&&!"right-click".equals(RemasterRuntime.string(n,"unlock",""))){endpoint=n;break;}
   check(endpoint!=null,"all 60 original small variants retain an actual conditional endpoint "+id+v);
   int[] at=RemasterRuntime.nodePosition(w,s,endpoint);TileEntitySealedChest chest=new TileEntitySealedChest();chest.setWorldObj(w);chest.xCoord=at[0];chest.yCoord=at[1];chest.zCoord=at[2];
   w.storage.blocks.put(w.storage.key(at[0],at[1],at[2]),ForgottenLakeEncounterRegistry.sealedChest);w.storage.metas.put(w.storage.key(at[0],at[1],at[2]),2);w.storage.tiles.put(w.storage.key(at[0],at[1],at[2]),chest);chest.initializeRemaster(RemasterRuntime.integer(endpoint,"tier",1),s.id(),endpoint.get("id").getAsString());
   d.flag(s.id(),"node:"+endpoint.get("id").getAsString(),true);d.flag(s.id(),"geom:"+(at[0]>>4)+":"+(at[2]>>4),true);
   NBTTagCompound puzzle=new NBTTagCompound();puzzle.setBoolean("solved",true);d.state(s.id()).setTag("site-puzzle",puzzle);
   for(JsonElement shape:RemasterRuntime.array(s.plan().metadata,"shapeMechanisms")){NBTTagCompound finished=new NBTTagCompound();finished.setBoolean("solved",true);d.state(s.id()).setTag("shape:"+shape.getAsJsonObject().get("id").getAsString(),finished);}
   JsonArray spawns=RemasterRuntime.array(s.plan().metadata,"spawns");for(int i=0;i<spawns.size();i++){JsonObject spawn=spawns.get(i).getAsJsonObject();if(RemasterOriginalContract.eligibleGuard(spawn)){d.flag(s.id(),"entity:"+i,true);RemasterRuntime.death(w,s.id(),i);}}
   for(JsonObject record:RemasterOriginalContract.records(s)){
    TileRemasterNode readable=tile(w.storage,s,record);readable.setWorldObj(w);p.posX=readable.xCoord+.5;p.posY=readable.yCoord+.5;p.posZ=readable.zCoord+.5;
    d.flag(s.id(),"geom:"+(readable.xCoord>>4)+":"+(readable.zCoord>>4),true);
    new HistoryEvents().tick(new TickEvent.WorldTickEvent(cpw.mods.fml.relauncher.Side.SERVER,TickEvent.Phase.END,w));
    if("memory".equals(readable.role))check(RemasterRuntime.action(p,readable,100),"actual memory action establishes personal ordered route "+id+v+readable.nodeId);
   }
   p.posX=at[0]+.5;p.posY=at[1]+.5;p.posZ=at[2]+.5;
   check(RemasterRuntime.chestReady(chest),"real existing endpoint gate admits completed authored conditions "+id+v);
   new HistoryEvents().tick(new TickEvent.WorldTickEvent(cpw.mods.fml.relauncher.Side.SERVER,TickEvent.Phase.END,w));
   check(has(p,"visited",k)&&has(p,"completed",k),"actual fresh-world history tick bridges each original small variant endpoint "+id+v);
  }
 }
 public static void main(String[] args)throws Exception {
  init();String scenario=args.length>0?args[0]:"all";
  if(scenario.equals("guards")||scenario.equals("all"))guards();
  if(scenario.equals("story")||scenario.equals("all"))story();
  if(scenario.equals("history")||scenario.equals("all"))history();
  if(scenario.equals("endpoints")||scenario.equals("all"))endpoints();
  System.out.println("original-contract "+scenario+" "+checks+" PASS");
 }
}
