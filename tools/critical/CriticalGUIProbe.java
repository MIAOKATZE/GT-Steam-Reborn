import java.lang.reflect.*;
import java.util.*;
import org.lwjgl.opengl.*;
import com.google.gson.*;
import sun.misc.Unsafe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.inventory.Slot;
import com.miaokatze.gtsr.common.critical.ContainerCriticalController;
import com.miaokatze.gtsr.client.critical.GuiCriticalController;

/** Background/layout fixture. Vanilla font/item rendering and mouse/server dispatch remain outside this check. */
public final class CriticalGUIProbe {
 static Field field(Class<?> type,String name)throws Exception {while(type!=null){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException e){type=type.getSuperclass();}}throw new NoSuchFieldException(name);}
 public static JsonObject run()throws Exception{
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);Unsafe u=(Unsafe)f.get(null);
  Minecraft mc=(Minecraft)u.allocateInstance(Minecraft.class);field(Minecraft.class,"theMinecraft").set(null,mc);
  mc.renderEngine=new TextureManager(null){@Override public void bindTexture(ResourceLocation r){net.minecraft.client.renderer.tileentity.CriticalTexture.bind(r.toString());}};
  ContainerCriticalController container=(ContainerCriticalController)u.allocateInstance(ContainerCriticalController.class);
  int[] telemetry=new int[23];telemetry[17]=200;telemetry[18]=400;field(container.getClass(),"telemetry").set(container,telemetry);
  List<Slot> slots=new ArrayList<>();for(int i=0;i<36;i++)slots.add(new Slot(null,i,10+i%9*18+(i>=18?168:0),138+(i%18/9)*18));
  for(int row=0;row<3;row++)for(int col=0;col<9;col++)slots.add(new Slot(null,9+row*9+col,94+col*18,242+row*18));
  for(int col=0;col<9;col++)slots.add(new Slot(null,col,94+col*18,300));field(container.getClass(),"inventorySlots").set(container,slots);
  GuiCriticalController gui=(GuiCriticalController)u.allocateInstance(GuiCriticalController.class);field(gui.getClass(),"container").set(gui,container);
  field(gui.getClass(),"inventorySlots").set(gui,container);field(gui.getClass(),"xSize").setInt(gui,348);field(gui.getClass(),"ySize").setInt(gui,326);
  field(gui.getClass(),"guiLeft").setInt(gui,34);field(gui.getClass(),"guiTop").setInt(gui,27);
  GL11.glViewport(0,0,768,768);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GL11.glDisable(GL11.GL_DEPTH_TEST);
  GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,416,380,0,-100,100);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();
  Method background=gui.getClass().getDeclaredMethod("drawGuiContainerBackgroundLayer",float.class,int.class,int.class);background.setAccessible(true);background.invoke(gui,0f,0,0);CriticalGLProbe.check("GUI production background");int pixels=CriticalGLProbe.capture("gui-production-background");
  JsonObject receipt=new JsonObject();receipt.addProperty("status","PASS");receipt.addProperty("codeSource",gui.getClass().getProtectionDomain().getCodeSource().getLocation().toString());receipt.addProperty("slots",slots.size());receipt.addProperty("visiblePixels",pixels);
  receipt.addProperty("scope","Untouched production GUI background + production GtsrGuiDrawing + actual Tessellator GL; fixture 72 slots and telemetry/TextureManager. Text/font, buttons, item icons, mouse, tooltips and server window dispatch untested.");return receipt;
 }
}
