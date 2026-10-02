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
  long time;boolean loaded,occupied;int writes,failWrite,spawns;P player;
  W(){super((ISaveHandler)null,(String)null,(WorldProvider)null,(WorldSettings)null,(Profiler)null);}
  String key(int x,int y,int z){return x+","+y+","+z;}
  public Block getBlock(int x,int y,int z){return blocks.getOrDefault(key(x,y,z),Blocks.air);}
  public int getBlockMetadata(int x,int y,int z){return metas.getOrDefault(key(x,y,z),0);}
  public TileEntity getTileEntity(int x,int y,int z){return tiles.get(key(x,y,z));}
  public boolean blockExists(int x,int y,int z){return loaded&&y>=0&&y<256;}
  public boolean setBlock(int x,int y,int z,Block b,int m,int flags){if(++writes==failWrite)return false;blocks.put(key(x,y,z),b);metas.put(key(x,y,z),m);return true;}
  public boolean setBlockMetadataWithNotify(int x,int y,int z,int m,int flags){metas.put(key(x,y,z),m);return true;}
  public void markBlockForUpdate(int x,int y,int z){}
  public void markTileEntityChunkModified(int x,int y,int z,TileEntity tile){}
  public void notifyBlocksOfNeighborChange(int x,int y,int z,Block b){}
  public long getTotalWorldTime(){return time;}
  public List getEntitiesWithinAABB(Class type,AxisAlignedBB box){return occupied?Collections.singletonList(new Object()):Collections.emptyList();}
  public List getCollidingBoundingBoxes(Entity e,AxisAlignedBB box){return Collections.emptyList();}
  public EntityPlayer getClosestPlayer(double x,double y,double z,double radius){return player;}
  public boolean isSideSolid(int x,int y,int z,net.minecraftforge.common.util.ForgeDirection side){return true;}
  public boolean spawnEntityInWorld(Entity e){spawns++;if(loadedEntityList!=null)loadedEntityList.add(e);return true;}
  public Entity getEntityByID(int id){return null;}
  protected IChunkProvider createChunkProvider(){return null;}
  protected int func_152379_p(){return 0;}
 }
 static class P extends EntityPlayerMP {
  boolean living=true;
  P(){super(null,null,null,null);}
  public boolean isEntityAlive(){return living;}
  public double getDistanceSq(double x,double y,double z){return 0;}
 }
 static TileRemasterNode tile(W w,RemasterSite s,JsonObject n){
  TileRemasterNode t=new TileRemasterNode();t.setWorldObj(w);t.xCoord=s.x+n.get("x").getAsInt();
  t.yCoord=s.y+n.get("y").getAsInt();t.zCoord=s.z+n.get("z").getAsInt();
  t.siteId=s.id();t.nodeId=n.get("id").getAsString();t.role=n.get("role").getAsString();
  String key=n.get("block").getAsString();w.blocks.put(w.key(t.xCoord,t.yCoord,t.zCoord),RemasterRuntime.resolve(key));
  w.metas.put(w.key(t.xCoord,t.yCoord,t.zCoord),RemasterRuntime.meta(key));w.tiles.put(w.key(t.xCoord,t.yCoord,t.zCoord),t);return t;
 }
 static JsonObject node(RemasterSite s,String role){for(JsonObject n:RemasterRuntime.nodes(s))if(n.get("role").getAsString().equals(role))return n;throw new AssertionError(role);}
 public static void main(String[] args)throws Exception {
  Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);u=(sun.misc.Unsafe)uf.get(null);
  Class.forName("net.minecraft.init.Blocks");
  Constructor<BlockAir> air=BlockAir.class.getDeclaredConstructor();air.setAccessible(true);setBlock("air",air.newInstance());setBlock("stone",new BlockStone());
  com.miaokatze.gtsr.common.dimension.prosperity.architecture.RuinsArchitecture.registerBlocks();
  com.miaokatze.gtsr.common.dimension.prosperity.architecture.RoyalArchitecture.registerBlocks();
  com.miaokatze.gtsr.common.blocks.BlocksGTSR.ruinDebris=new com.miaokatze.gtsr.common.dimension.prosperity.block.BlockRuinDebris();
  com.miaokatze.gtsr.common.blocks.BlocksGTSR.ruinedCasing=new com.miaokatze.gtsr.common.dimension.prosperity.block.BlockRuinedCasing();
  com.miaokatze.gtsr.common.blocks.BlocksGTSR.prosperityZenithLog=new com.miaokatze.gtsr.common.dimension.prosperity.architecture.BlockZenithLog("ProsperityZenithLog","gtsr:prosperity_zenith_log_side","gtsr:prosperity_zenith_log_top");
  com.miaokatze.gtsr.common.blocks.BlocksGTSR.prosperityJadeLeaves=new com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityCanopyLeaves("ProsperityJadeLeaves","gtsr:prosperity_jade_leaves_side","gtsr:prosperity_jade_leaves_top");
  com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterRegistry.sealedChest=new com.miaokatze.gtsr.common.dimension.prosperity.encounter.BlockSealedChest();
  Field registry=RemasterBlocks.class.getDeclaredField("REGISTRY");registry.setAccessible(true);
  Map map=(Map)registry.get(null);
  for(int i=0;i<94;i++){Method f=RemasterBlocks.class.getDeclaredMethod("block"+i);f.setAccessible(true);RemasterBlock b=(RemasterBlock)f.invoke(null);map.put(b.id(),b);}
  Set<String> palette=new HashSet<>();
  for(String id:RemasterCatalog.ids())for(int variant=0;variant<RemasterCatalog.variants(id);variant++)for(String material:RemasterCatalog.get(id,variant).palette)palette.add(material);
  for(String material:palette)check(RemasterRuntime.resolve(material)!=null,"actual block factory palette resolution "+material);
  W w=(W)u.allocateInstance(W.class);w.blocks=new HashMap<>();w.metas=new HashMap<>();w.tiles=new HashMap<>();w.loaded=true;
  set(w,World.class,"perWorldStorage",new MapStorage((ISaveHandler)null));
  set(w,World.class,"provider",u.allocateInstance(WorldProviderProsperityRuins.class));
  set(w,World.class,"rand",new Random(7));
  set(w,World.class,"loadedEntityList",new ArrayList<>());
  P p=(P)u.allocateInstance(P.class);p.living=true;p.worldObj=w;p.capabilities=new PlayerCapabilities();w.player=p;
  boolean missing=false;try{RemasterRuntime.resolve("gtsr:missing_deliberately#0");}catch(IllegalArgumentException e){missing=true;}check(missing,"unknown block fails instead of default air");
  RemasterSite s=new RemasterSite("fallen_foundry",0,7,0,80,0);RemasterData data=RemasterData.get(w);data.register(s);
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
  p.capabilities.isCreativeMode=true;check(!RemasterRuntime.valid(p,t),"creative rejected");p.capabilities.isCreativeMode=false;
  p.living=false;check(!RemasterRuntime.valid(p,t),"dead rejected");p.living=true;
  w.loaded=false;check(!RemasterRuntime.valid(p,t),"unloaded rejected");w.loaded=true;
  t.nodeId="forged";check(!RemasterRuntime.valid(p,t),"forged ownership rejected");t.nodeId=node(s,"control").get("id").getAsString();
  for(int button:new int[]{2,3,1,0})check(RemasterRuntime.action(p,t,button),"ordered valve action");
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
  System.out.println("passed: "+checks+" real MC authority/pressure/atomic-shape/NBT/spawner assertions");
 }
}
