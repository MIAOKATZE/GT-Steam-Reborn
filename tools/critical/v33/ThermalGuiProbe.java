package com.miaokatze.gtsr.common.critical;
import static com.miaokatze.gtsr.common.critical.ProbeSupport.*;
import java.util.*;import java.nio.file.*;import java.lang.reflect.*;
import com.cleanroommc.modularui.value.sync.*;import net.minecraft.network.PacketBuffer;import io.netty.buffer.Unpooled;
import net.minecraft.util.StatCollector;import net.minecraft.item.ItemStack;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import com.google.gson.*;
public class ThermalGuiProbe {
 static Object gui(Class<?> type,MTEMultiBlockBase machine)throws Exception {
  Object tile=Proxy.newProxyInstance(ThermalGuiProbe.class.getClassLoader(),new Class<?>[]{gregtech.api.interfaces.tileentity.IGregTechTileEntity.class},(p,m,a)->{if(m.getName().equals("getLastShutDownReason"))return gregtech.api.util.shutdown.ShutDownReasonRegistry.NONE;Class<?> t=m.getReturnType();if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;if(t==byte.class)return (byte)0;if(t==short.class)return (short)0;if(t==double.class)return 0D;if(t==float.class)return 0F;return null;});
  set(machine,"mBaseMetaTileEntity",tile);set(machine,"structureErrors",new ArrayList<>());set(machine,"mInventory",new ItemStack[2]);Field nf=field(machine,"numberFormat");nf.set(machine,nf.getType().getDeclaredConstructor().newInstance());
  Object g=U.allocateInstance(type);for(Class<?> c=type;c!=Object.class;c=c.getSuperclass())for(Field f:c.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())){f.setAccessible(true);if(MTEMultiBlockBase.class.isAssignableFrom(f.getType()))f.set(g,machine);if(f.getName().equals("baseMetaTileEntity"))f.set(g,tile);}return g;
 }
 static PanelSyncManager manager(Object gui)throws Exception {
  PanelSyncManager p=new PanelSyncManager(new ModularSyncManager(false),true);Method register=gui.getClass().getDeclaredMethod("registerSyncValues",PanelSyncManager.class);register.setAccessible(true);register.invoke(gui,p);return p;
 }
 static void transfer(IntSyncValue source,IntSyncValue target)throws Exception {source.updateCacheFromSource(false);PacketBuffer b=new PacketBuffer(Unpooled.buffer());source.write(b);target.readOnClient(0,b);b.release();}
 static JsonObject run(String name)throws Exception {
  Class<?> type=Class.forName("com.miaokatze.gtsr.common.machine."+name),guiType=Class.forName("com.miaokatze.gtsr.common.gui."+name+"Gui");MTEMultiBlockBase server=(MTEMultiBlockBase)U.allocateInstance(type),client=(MTEMultiBlockBase)U.allocateInstance(type);
  Object sg=gui(guiType,server),cg=gui(guiType,client);PanelSyncManager sp=manager(sg),cp=manager(cg);IntSyncValue spp=sp.findSyncHandler("progressTime",IntSyncValue.class),spm=sp.findSyncHandler("maxProgressTime",IntSyncValue.class),cpp=cp.findSyncHandler("progressTime",IntSyncValue.class),cpm=cp.findSyncHandler("maxProgressTime",IntSyncValue.class);
  JsonArray samples=new JsonArray();int mismatch=0;
  for(int tick=0;tick<=600;tick++){
   server.mProgresstime=tick;server.mMaxProgresstime=600;transfer(spp,cpp);transfer(spm,cpm);
   String st=server.generateCurrentRecipeInfoString(),ct=client.generateCurrentRecipeInfoString();if(server.mProgresstime!=client.mProgresstime||server.mMaxProgresstime!=client.mMaxProgresstime||!st.equals(ct))mismatch++;
   if(!ct.contains("30.00"))throw new AssertionError("formatter did not consume sync duration: "+ct);
   if(tick==0||tick==1||tick==20||tick==200||tick==400||tick==599||tick==600){JsonObject row=new JsonObject();row.addProperty("tick",tick);row.addProperty("clientProgress",client.mProgresstime);row.addProperty("clientMax",client.mMaxProgresstime);row.addProperty("text",ct);if(name.equals("MTELargeCokeOven")){String extra=(String)type.getMethod("getRecipeTimeText").invoke(client);if(!extra.contains("30.00s"))throw new AssertionError(extra);row.addProperty("cokeTime",extra);}samples.add(row);}
  }
  check(mismatch==0,name+" 601 native IntSyncValue write/readOnClient samples identical to server timeline and formatter");server.mProgresstime=0;server.mMaxProgresstime=0;transfer(spp,cpp);transfer(spm,cpm);check(client.mProgresstime==0&&client.mMaxProgresstime==0,name+" completion clears both client progress fields");
  JsonObject o=new JsonObject();o.addProperty("machine",name);o.addProperty("samples",601);o.addProperty("mismatches",mismatch);o.addProperty("machineSource",source(type));o.addProperty("guiSource",source(guiType));o.add("timeline",samples);return o;
 }
 public static void main(String[] args)throws Exception {
  bootstrap();Object translations=get(StatCollector.class,"localizedName");Map<String,String> lang=(Map<String,String>)get(translations,"languageList");lang.put("GT5U.gui.text.progress_recipe","%s / %s (%s%%)");lang.put("gtsr.gui.coke_oven.recipe_time","Recipe time: ");
  JsonArray rows=new JsonArray();for(String n:new String[]{"MTEReinforcedBrickBlastFurnace","MTELargeCokeOven","MTESiemensMartinFurnace"})rows.add(run(n));JsonObject o=new JsonObject();o.addProperty("status","PASS");o.addProperty("GT",source(MTEMultiBlockBase.class));o.addProperty("MUI",source(IntSyncValue.class));o.addProperty("boundary","Actual registerSyncValues + native IntSyncValue write/readOnClient + actual text methods, supplied deterministic server timeline; no rendered GUI or network transport");o.add("machines",rows);Files.write(Paths.get(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(o).getBytes("UTF-8"));
 }
}
