import java.lang.reflect.*;
import java.util.*;
import com.google.gson.*;
import sun.misc.Unsafe;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.IChunkProvider;
import com.miaokatze.gtsr.common.critical.*;

/** Calls actual candidate group visibility and TE packet/visual clocks against an in-memory world boundary. */
public final class CriticalClientStateProbe {
 public static final class MemoryWorld extends World {
  long now;Map<String,Block> blocks;int checks,maxChecks;boolean loaded=true;
  private MemoryWorld(){super(null,"fixture",(WorldProvider)null,null,null);}
  protected IChunkProvider createChunkProvider(){return null;}protected int func_152379_p(){return 0;}public Entity getEntityByID(int id){return null;}
  public long getTotalWorldTime(){return now;}public boolean blockExists(int x,int y,int z){checks++;return loaded;}
  public int getLightBrightnessForSkyBlocks(int x,int y,int z,int minimum){return 0x800080;}
  public Block getBlock(int x,int y,int z){return blocks.get(x+":"+y+":"+z);}public int getBlockMetadata(int x,int y,int z){return 0;}
 }
 static void expect(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
 static void put(MemoryWorld w,CriticalGeometry.Voxel v){w.blocks.put(v.x+":"+v.y+":"+v.z,v.block);}
 static NBTTagCompound packet(CriticalMachineKind kind,int ticks,int duration,boolean working,int reason){
  NBTTagCompound n=new NBTTagCompound();n.setTag("config",new CriticalConfiguration(CriticalTier.T1,kind,0,0,0).write());n.setInteger("state",2);n.setBoolean("valid",true);n.setBoolean("enabled",true);n.setBoolean("working",working);n.setInteger("reason",reason);n.setInteger("cycleTicks",ticks);n.setInteger("cycleDuration",duration);n.setString("structureId","fixture-"+kind);n.setString("batchId","batch-1");return n;
 }
 static void sync(TileEntityCriticalController te,NBTTagCompound n){te.onDataPacket(null,new S35PacketUpdateTileEntity(40,0,32,1,n));}
 public static JsonObject run()throws Exception {
  Field uf=Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);Unsafe u=(Unsafe)uf.get(null);
  MemoryWorld w=(MemoryWorld)u.allocateInstance(MemoryWorld.class);w.blocks=new HashMap<>();w.loaded=true;CriticalGUIProbe.field(World.class,"isRemote").setBoolean(w,true);
  TileEntityCriticalController te=new TileEntityCriticalController();te.setWorldObj(w);te.xCoord=40;te.zCoord=32;
  w.now=1000;sync(te,packet(CriticalMachineKind.SOLAR,100,400,true,0));w.now=1005;expect(te.getVisualCycleTicks(.5f)==105.5,"Client clock interpolation");w.now=1500;expect(te.getVisualCycleTicks(.5f)==400,"Cycle clamp");w.now=995;expect(te.getVisualCycleTicks(.5f)==100.5,"Backward world time clamp");
  w.now=1600;sync(te,packet(CriticalMachineKind.SOLAR,110,400,false,0));w.now=1620;expect(te.getVisualCycleTicks(.5f)==110,"Paused clock extrapolated");
  w.now=1700;sync(te,packet(CriticalMachineKind.SOLAR,120,400,true,1));w.now=1720;expect(te.getVisualCycleTicks(.5f)==120&&!te.isWorking(),"Reason pause ignored");
  for(String gate:new String[]{"enabled","valid"}){NBTTagCompound n=packet(CriticalMachineKind.SOLAR,130,400,true,0);n.setBoolean(gate,false);w.now=1800;sync(te,n);w.now=1820;expect(te.getVisualCycleTicks(.5f)==130&&!te.isWorking(),"Client gate ignored: "+gate);}
  w.now=1900;sync(te,packet(CriticalMachineKind.SOLAR,20,400,true,0));w.now=1903;expect(te.getVisualCycleTicks(.5f)==23.5,"Packet did not reanchor");
  w.now=2000;sync(te,packet(CriticalMachineKind.SOLAR,0,1,true,0));w.now=2003;expect(te.getVisualCycleTicks(.5f)==1,"One tick clamp");
  // Test production packet creation, not only hand-authored incoming data.
  CriticalGUIProbe.field(te.getClass(),"state").setInt(te,1);CriticalGUIProbe.field(te.getClass(),"stage").setInt(te,3);CriticalGUIProbe.field(te.getClass(),"cursor").setInt(te,71);CriticalGUIProbe.field(te.getClass(),"clientJobTotal").setInt(te,913);
  NBTTagCompound encoded=((S35PacketUpdateTileEntity)te.getDescriptionPacket()).func_148857_g();
  for(String name:new String[]{"stage","cursor","jobTotal","reason","working","structureId","batchId"})expect(encoded.hasKey(name),"Description packet missing "+name);
  expect(encoded.getInteger("stage")==3&&encoded.getInteger("cursor")==71&&encoded.getInteger("jobTotal")==913,"Description packet progress lost");
  Class<?> visibility=Class.forName("com.miaokatze.gtsr.client.critical.CriticalGroupVisibility");Constructor<?> constructor=visibility.getDeclaredConstructor(TileEntityCriticalController.class);constructor.setAccessible(true);Method update=visibility.getDeclaredMethod("update",TileEntityCriticalController.class),matches=visibility.getDeclaredMethod("matches",TileEntityCriticalController.class);update.setAccessible(true);matches.setAccessible(true);
  JsonArray visibilityChecks=new JsonArray();
  for(CriticalMachineKind kind:CriticalMachineKind.values()){
   w.blocks.clear();w.now+=100;sync(te,packet(kind,0,400,true,0));
   // Preserve actual geometry/groups; assign real candidate proxy block identities at the fixture registry boundary.
   Class<?> bt=Class.forName("com.miaokatze.gtsr.common.critical.CriticalMaterials$MaterialBlock");Constructor<?> bc=bt.getDeclaredConstructor(String.class,boolean.class,boolean.class,boolean.class,boolean.class);bc.setAccessible(true);Map<String,Block> identities=new HashMap<>();
   String slug=kind.name().toLowerCase(Locale.ROOT);List<CriticalGeometry.Voxel> dynamic=new ArrayList<>();
   for(String modelName:new String[]{"loom",slug}){CriticalGeometry.Model model=CriticalGeometry.getModel(modelName);for(CriticalGeometry.Voxel v:model.voxels)if(model.animations.has(v.group)){Block block=identities.get(v.material);if(block==null){block=(Block)bc.newInstance(v.material,true,false,false,false);identities.put(v.material,block);}CriticalGUIProbe.field(v.getClass(),"block").set(v,block);dynamic.add(v);}}
   Object groups=constructor.newInstance(te);Set<?> visible=null;int max=0;
   for(int i=0;i<20;i++){w.now++;w.checks=0;visible=(Set<?>)update.invoke(groups,te);max=Math.max(max,w.checks);expect(visible.isEmpty(),"Missing group initially rendered");expect(w.checks<=512,"Exceeded block check budget");}
   for(CriticalGeometry.Voxel v:dynamic)put(w,v);
   int scanTicks=(dynamic.size()+511)/512+dynamic.size()/512+30;for(int i=0;i<scanTicks;i++){w.now++;w.checks=0;visible=(Set<?>)update.invoke(groups,te);max=Math.max(max,w.checks);expect(w.checks<=512,"Budget during full scan");}
   List<?> sourceGroups=(List<?>)CriticalGUIProbe.field(visibility,"groups").get(groups);expect(visible.size()==sourceGroups.size(),"Complete proxy groups did not show "+slug);int complete=visible.size();
   Object group=sourceGroups.get(0);String key=(String)CriticalGUIProbe.field(group.getClass(),"key").get(group);CriticalGeometry.Voxel removed=(CriticalGeometry.Voxel)((List<?>)CriticalGUIProbe.field(group.getClass(),"voxels").get(group)).get(0);w.blocks.remove(removed.x+":"+removed.y+":"+removed.z);
   int invalidated=0;for(int i=1;i<=scanTicks;i++){w.now++;w.checks=0;visible=(Set<?>)update.invoke(groups,te);expect(w.checks<=512,"Budget during invalidation");if(!visible.contains(key)){invalidated=i;break;}}expect(invalidated>0,"Missing proxy never invalidated");
   expect(invalidated<=14,"Invalidation latency >14 ticks: "+slug+" "+invalidated);
   int before=visible.size();for(int state:new int[]{3,4}){CriticalGUIProbe.field(te.getClass(),"state").setInt(te,state);CriticalGUIProbe.field(te.getClass(),"structureValid").setBoolean(te,false);w.now++;w.checks=0;visible=(Set<?>)update.invoke(groups,te);expect(!visible.isEmpty(),"Dismantle/repair hid all intact groups");}
   expect((Boolean)matches.invoke(groups,te),"Stable structure identity mismatch");CriticalGUIProbe.field(te.getClass(),"structureId").set(te,"different");expect(!(Boolean)matches.invoke(groups,te),"Different structure identity accepted");
   JsonObject result=new JsonObject();result.addProperty("machine",slug);result.addProperty("dynamicVoxels",dynamic.size());result.addProperty("completeGroups",complete);result.addProperty("maxChecksPerTick",max);result.addProperty("invalidationTicks",invalidated);visibilityChecks.add(result);
  }
  // Enter the untouched production TESR method with real controller/world-boundary objects.
  net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();
  mc.thePlayer=(net.minecraft.client.entity.EntityClientPlayerMP)u.allocateInstance(net.minecraft.client.entity.EntityClientPlayerMP.class);mc.thePlayer.posX=40;mc.thePlayer.posY=100;mc.thePlayer.posZ=40;
  w.blocks.clear();w.now+=100;NBTTagCompound paused=packet(CriticalMachineKind.SUN,200,400,false,1);sync(te,paused);
  for(String name:new String[]{"loom","sun"}){CriticalGeometry.Model model=CriticalGeometry.getModel(name);for(CriticalGeometry.Voxel v:model.voxels)if(model.animations.has(v.group))put(w,v);}
  com.miaokatze.gtsr.client.critical.CriticalControllerRenderer renderer=new com.miaokatze.gtsr.client.critical.CriticalControllerRenderer();int calls=0;
  for(int i=0;i<20;i++){w.now++;CriticalGLProbe.camera();int md=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_MODELVIEW_STACK_DEPTH),td=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_STACK_DEPTH);
   org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_ALPHA_TEST);org.lwjgl.opengl.GL11.glDepthMask(false);
   net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit,17,23);
   renderer.renderTileEntityAt(te,40,0,32,.5f);calls++;CriticalGLProbe.check("actual paused TESR");CriticalGLProbe.verifyStacks("actual paused TESR",md,td);
   expect(!org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND)&&!org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_ALPHA_TEST)&&!org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK),"TESR attrib state leaked");
   expect(net.minecraft.client.renderer.OpenGlHelper.lastBrightnessX==17&&net.minecraft.client.renderer.OpenGlHelper.lastBrightnessY==23,"TESR brightness leaked");
  }
  expect("build".equals(CriticalGUIProbe.field(renderer.getClass(),"poseMode").get(renderer)),"Paused batch returned to idle pose");
  expect(CriticalGUIProbe.field(renderer.getClass(),"posePhase").getDouble(renderer)==.5D,"Paused batch lost phase");
  double seconds=CriticalGUIProbe.field(renderer.getClass(),"poseSeconds").getDouble(renderer);w.now+=20;CriticalGLProbe.camera();renderer.renderTileEntityAt(te,40,0,32,.5f);calls++;
  expect(CriticalGUIProbe.field(renderer.getClass(),"posePhase").getDouble(renderer)==.5D,"Paused phase extrapolated");expect(CriticalGUIProbe.field(renderer.getClass(),"poseSeconds").getDouble(renderer)>seconds,"Ambient clock froze with batch");
  int pixels=CriticalGLProbe.capture("sun-paused-real-tesr");expect(pixels>30,"Actual TESR empty");renderer.onResourceManagerReload(null);
  JsonObject receipt=new JsonObject();receipt.addProperty("status","PASS");receipt.addProperty("controllerCodeSource",te.getClass().getProtectionDomain().getCodeSource().getLocation().toString());receipt.addProperty("visibilityCodeSource",visibility.getProtectionDomain().getCodeSource().getLocation().toString());receipt.addProperty("clockAndPacketChecks",17);receipt.addProperty("actualTESRMethodCalls",calls);receipt.addProperty("pausedPhase",.5);receipt.addProperty("pausedMode","build");receipt.addProperty("pausedVisiblePixels",pixels);receipt.add("visibility",visibilityChecks);receipt.addProperty("scope","Actual production packet/visual clock, CriticalGroupVisibility and renderTileEntityAt method; fixture time/block world and real candidate proxy identities/player coordinates. No full MC networking, loaded chunks or vanilla dispatcher.");return receipt;
 }
}
