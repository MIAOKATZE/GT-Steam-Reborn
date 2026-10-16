import java.lang.reflect.*;
import java.nio.*;
import java.awt.image.*;
import javax.imageio.*;
import java.util.*;
import org.lwjgl.*;
import org.lwjgl.opengl.*;
import com.google.gson.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import com.miaokatze.gtsr.client.critical.CriticalMaterialRenderer;
import com.miaokatze.gtsr.common.critical.CriticalGeometry;

/** Actual patched RenderBlocks and actual production MaterialBlock + material renderer; fixture world/texture atlas. */
public final class CriticalStaticGLProbe {
 static IIcon icon(final int panel){return (IIcon)Proxy.newProxyInstance(IIcon.class.getClassLoader(),new Class[]{IIcon.class},(p,m,a)->{
  String n=m.getName();if(n.equals("getIconWidth")||n.equals("getIconHeight"))return 16;
  if(n.equals("getMinU"))return panel/3f;if(n.equals("getMaxU"))return (panel+1)/3f;
  if(n.equals("getMinV"))return 0f;if(n.equals("getMaxV"))return 1f;
  if(n.equals("getInterpolatedU"))return (float)((panel+((Double)a[0])/16)/3);
  if(n.equals("getInterpolatedV"))return (float)(((Double)a[0])/16);
  if(n.equals("getIconName"))return "fixture_atlas_"+panel;throw new UnsupportedOperationException(n);
 });}
 static int atlas(String id)throws Exception{
  String path="/assets/gtsr/textures/blocks/critical/";BufferedImage side=ImageIO.read(CriticalStaticGLProbe.class.getResourceAsStream(path+id+".png"));
  BufferedImage top=ImageIO.read(CriticalStaticGLProbe.class.getResourceAsStream(path+id+"__top.png"));java.io.InputStream stream=CriticalStaticGLProbe.class.getResourceAsStream(path+id+"_emission.png");BufferedImage mask=stream==null?new BufferedImage(16,16,2):ImageIO.read(stream);
  ByteBuffer b=BufferUtils.createByteBuffer(48*16*4);for(int y=0;y<16;y++)for(int x=0;x<48;x++){BufferedImage im=x<16?side:x<32?top:mask;int c=im.getRGB(x%16,y);b.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put((byte)(c>>24));}b.flip();int tex=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,tex);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,48,16,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);return tex;
 }
 public static JsonObject run()throws Exception{
  Minecraft mc=Minecraft.getMinecraft();Field unsafe= sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafe.setAccessible(true);sun.misc.Unsafe u=(sun.misc.Unsafe)unsafe.get(null);
  mc.gameSettings=(net.minecraft.client.settings.GameSettings)u.allocateInstance(net.minecraft.client.settings.GameSettings.class);mc.gameSettings.ambientOcclusion=0;
  Class<?> type=Class.forName("com.miaokatze.gtsr.common.critical.CriticalMaterials$MaterialBlock");Constructor<?> ctor=type.getDeclaredConstructor(String.class,boolean.class,boolean.class,boolean.class,boolean.class);ctor.setAccessible(true);
  Class<?> airType=Class.forName("net.minecraft.block.BlockAir");Constructor<?> airCtor=airType.getDeclaredConstructor();airCtor.setAccessible(true);Block air=(Block)airCtor.newInstance();
  CriticalMaterialRenderer renderer=new CriticalMaterialRenderer();JsonArray results=new JsonArray();Set<String> tested=new LinkedHashSet<>();
  for(String tier:new String[]{"t1","t2","t3"})for(CriticalGeometry.Voxel voxel:CriticalGeometry.getModel("base_"+tier).voxels){
   if(!tested.add(voxel.material))continue;JsonObject properties=com.miaokatze.gtsr.common.critical.CriticalMaterials.properties(voxel.material);String id=properties.get("id").getAsString();boolean glass=!properties.get("alphaMode").getAsString().equals("opaque");
   Block block=(Block)ctor.newInstance(id,false,glass,properties.get("animated").getAsBoolean(),properties.get("emissionStrength").getAsDouble()>0);
   if(block.canPlaceBlockAt(null,0,0,0))throw new IllegalStateException("Direct material placement allowed");
   Class<?> itemType=Class.forName("com.miaokatze.gtsr.common.critical.CriticalMaterials$ConstructionOnlyItemBlock");
   net.minecraft.item.ItemBlock item=(net.minecraft.item.ItemBlock)itemType.getConstructor(Block.class).newInstance(block);
   if(item.placeBlockAt(null,null,null,0,0,0,0,0f,0f,0f,0))throw new IllegalStateException("ItemBlock placement allowed");
   block.registerBlockIcons(new IIconRegister(){public IIcon registerIcon(String name){return icon(name.endsWith("_emission")?2:name.endsWith("__top")?1:0);}});
   IBlockAccess world=(IBlockAccess)Proxy.newProxyInstance(IBlockAccess.class.getClassLoader(),new Class[]{IBlockAccess.class},(p,m,a)->{String n=m.getName();if(n.equals("getBlock"))return ((Integer)a[0]==0&&(Integer)a[1]==0&&(Integer)a[2]==0)?block:air;if(n.equals("getLightBrightnessForSkyBlocks"))return 0x800080;if(n.equals("getBlockMetadata")||n.equals("isBlockProvidingPowerTo"))return 0;if(n.equals("getHeight"))return 256;if(m.getReturnType()==boolean.class)return false;return null;});
   RenderBlocks rb=new RenderBlocks(world);rb.setRenderBounds(0,0,0,1,1,1);int texture=atlas(id);
   GL11.glViewport(0,0,768,768);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glEnable(GL11.GL_TEXTURE_2D);
   GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(-1.2,1.2,-1.2,1.2,-10,10);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GL11.glRotated(25,1,0,0);GL11.glRotated(-35,0,1,0);GL11.glTranslated(-.5,-.5,-.5);
   Tessellator.instance.startDrawingQuads();boolean visible=renderer.renderWorldBlock(world,0,0,0,block,0,rb);Tessellator.instance.draw();CriticalGLProbe.check("static "+id);int pixels=CriticalGLProbe.capture("static-"+id);if(!visible||pixels<30)throw new IllegalStateException("Static empty "+id);
   if(rb.renderMinX!=0||rb.renderMaxX!=1||rb.renderMinY!=0||rb.renderMaxY!=1||rb.renderMinZ!=0||rb.renderMaxZ!=1)throw new IllegalStateException("Bounds leaked");
   JsonObject r=new JsonObject();r.addProperty("material",id);r.addProperty("pixels",pixels);r.addProperty("transparent",glass);r.addProperty("directAndItemPlacementRejected",true);results.add(r);GL11.glDeleteTextures(texture);
  }
  JsonObject receipt=new JsonObject();receipt.addProperty("status","PASS");receipt.addProperty("productionMaterialBlockCodeSource",type.getProtectionDomain().getCodeSource().getLocation().toString());receipt.addProperty("scope","Actual production MaterialBlock/renderWorldBlock + actual patchedMC RenderBlocks/Tessellator; isolated cube fixture world and 3-panel atlas of candidate PNG frame0. Not loaded game chunks/atlas stitching.");receipt.add("materials",results);return receipt;
 }
}
