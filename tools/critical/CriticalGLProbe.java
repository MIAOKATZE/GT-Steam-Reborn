import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.awt.image.*;
import javax.imageio.*;
import java.util.*;
import org.lwjgl.*;
import org.lwjgl.opengl.*;
import com.google.gson.*;
import com.miaokatze.gtsr.client.critical.CriticalControllerRenderer;
import com.miaokatze.gtsr.common.critical.CriticalGeometry;

/** Calls untouched production renderer using real patched Minecraft Tessellator in an invisible Pbuffer. */
public class CriticalGLProbe {
 static final int W=768,H=768;
 static Path out;
 static void check(String label){int error=GL11.glGetError();if(error!=0)throw new IllegalStateException(label+" GL "+error);}
 static void verifyStacks(String label,int model,int texture){
  if(GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH)!=model||GL11.glGetInteger(GL11.GL_TEXTURE_STACK_DEPTH)!=texture||GL11.glGetInteger(GL11.GL_MATRIX_MODE)!=GL11.GL_MODELVIEW)throw new IllegalStateException("Matrix state leaked "+label);
 }
 static void camera(){
  GL11.glViewport(0,0,W,H);GL11.glClearColor(.025f,.035f,.055f,1);GL11.glDepthMask(true);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
  GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(-105,105,-105,105,-500,500);
  GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GL11.glRotated(15,1,0,0);GL11.glRotated(-28,0,1,0);GL11.glTranslated(-40,-80,-40);
  GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_ALPHA_TEST);GL11.glAlphaFunc(GL11.GL_GREATER,.01f);
  GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glColor4f(1,1,1,1);
 }
 static int capture(String name)throws Exception{
  GL11.glFinish();ByteBuffer b=BufferUtils.createByteBuffer(W*H*4);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);
  BufferedImage im=new BufferedImage(W,H,BufferedImage.TYPE_INT_ARGB);int nonbackground=0;
  for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=(y*W+x)*4;int r=b.get(i)&255,g=b.get(i+1)&255,bl=b.get(i+2)&255,a=b.get(i+3)&255;
   if(r>15||g>18||bl>25)nonbackground++;im.setRGB(x,H-y-1,(a<<24)|(r<<16)|(g<<8)|bl);}
  ImageIO.write(im,"png",out.resolve(name+".png").toFile());return nonbackground;
 }
 public static void main(String[] args)throws Exception{
  out=Paths.get(args[0]);Pbuffer p=new Pbuffer(W,H,new PixelFormat(8,24,0),null,null);p.makeCurrent();
  JsonObject receipt=new JsonObject();receipt.addProperty("scope","Real Pbuffer GL + production renderer/FX/animation + patched MC Tessellator; fixture texture loader/material block identities. Not full game client/TESR dispatch.");
  receipt.addProperty("renderer",GL11.glGetString(GL11.GL_RENDERER));receipt.addProperty("version",GL11.glGetString(GL11.GL_VERSION));
  receipt.addProperty("productionRendererCodeSource",CriticalControllerRenderer.class.getProtectionDomain().getCodeSource().getLocation().toString());
  receipt.addProperty("productionGeometryCodeSource",CriticalGeometry.class.getProtectionDomain().getCodeSource().getLocation().toString());
  receipt.addProperty("tessellatorCodeSource",net.minecraft.client.renderer.Tessellator.class.getProtectionDomain().getCodeSource().getLocation().toString());
  CriticalControllerRenderer renderer=new CriticalControllerRenderer();Method render=renderer.getClass().getDeclaredMethod("renderModel",String.class,double.class,double.class,String.class);render.setAccessible(true);
  Class<?> fx=Class.forName("com.miaokatze.gtsr.client.critical.CriticalFX");Method effects=fx.getDeclaredMethod("render",String.class,CriticalGeometry.Model.class,double.class,double.class);effects.setAccessible(true);
  Method animateTexture=renderer.getClass().getDeclaredMethod("animateTexture",boolean.class,double.class),finishTexture=renderer.getClass().getDeclaredMethod("finishTexture");animateTexture.setAccessible(true);finishTexture.setAccessible(true);
  for(int frame=0;frame<8;frame++){GL11.glMatrixMode(GL11.GL_TEXTURE);GL11.glLoadIdentity();GL11.glMatrixMode(GL11.GL_MODELVIEW);animateTexture.invoke(renderer,true,frame*.5D);DoubleBuffer tex=BufferUtils.createDoubleBuffer(16);GL11.glGetDouble(GL11.GL_TEXTURE_MATRIX,tex);if(Math.abs(tex.get(13)-frame/8D)>1e-10)throw new IllegalStateException("Wrong animated frame offset "+frame);finishTexture.invoke(renderer);check("texture affine "+frame);}
  String[] machines={"solar","turbine","processing","entangler","sun","dimension","battery","assembly","accelerator"};
  JsonArray frames=new JsonArray();long start=System.nanoTime();int rendered=0;
  for(String slug:machines)for(int phase=0;phase<5;phase++){
   camera();double v=phase/4D;int md=GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH),td=GL11.glGetInteger(GL11.GL_TEXTURE_STACK_DEPTH);
   net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit,17,23);
   render.invoke(renderer,slug,v,40D+v*20,"build");verifyStacks(slug,md,td);
   if(net.minecraft.client.renderer.OpenGlHelper.lastBrightnessX!=17||net.minecraft.client.renderer.OpenGlHelper.lastBrightnessY!=23)throw new IllegalStateException("Emission brightness leaked "+slug);
   effects.invoke(null,slug,CriticalGeometry.getModel(slug),v,40D+v*20);verifyStacks(slug+" FX",md,td);check(slug+phase);
   int pixels=capture(slug+"-phase"+phase);if(pixels<30)throw new IllegalStateException("Empty "+slug+phase);
   JsonObject f=new JsonObject();f.addProperty("model",slug);f.addProperty("phase",v);f.addProperty("visiblePixels",pixels);frames.add(f);rendered++;
  }
  for(String slug:machines){camera();render.invoke(renderer,slug,0D,45D,"idle");effects.invoke(null,slug,CriticalGeometry.getModel(slug),0D,45D);check(slug+" idle");capture(slug+"-idle");rendered++;}
  // Base solids are chunk geometry, hence their TESR mesh intentionally contains no dynamic faces.
  for(String tier:new String[]{"t1","t2","t3"}){camera();render.invoke(renderer,"base_"+tier,0D,45D,"idle");check("base_"+tier);rendered++;}
  for(String tier:new String[]{"t1","t2","t3"})for(String kind:new String[]{"parallel","speed","efficiency"}){
   camera();GL11.glScaled(8,8,8);String slug="plugin_"+kind+"_"+tier;render.invoke(renderer,slug,.5D,45D,"build");check(slug);capture(slug);rendered++;
  }
  camera();render.invoke(renderer,"loom",.5D,45D,"build");check("loom");capture("loom");rendered++;
  for(String slug:machines)for(int frame=0;frame<8;frame++){camera();render.invoke(renderer,slug,.5D,frame*.5D,"build");check(slug+" texture frame "+frame);rendered++;}
  Field meshes=renderer.getClass().getDeclaredField("meshes");meshes.setAccessible(true);Map<?,?> map=(Map<?,?>)meshes.get(renderer);List<Integer> lists=new ArrayList<>();
  JsonArray performance=new JsonArray();
  for(String slug:machines){long[] samples=new long[30];
   for(int repeat=0;repeat<samples.length;repeat++){camera();GL11.glFinish();long begun=System.nanoTime();render.invoke(renderer,slug,.5D,45D,"build");effects.invoke(null,slug,CriticalGeometry.getModel(slug),.5D,45D);GL11.glFinish();samples[repeat]=System.nanoTime()-begun;check(slug+" warm");}
   long sum=0;for(long sample:samples)sum+=sample;Arrays.sort(samples);int calls=0,groups=0;
   for(Object mesh:(List<?>)map.get(slug)){groups++;calls++;Field emission=mesh.getClass().getDeclaredField("emission");emission.setAccessible(true);if(emission.get(mesh)!=null)calls++;}
   JsonObject data=new JsonObject();data.addProperty("model",slug);data.addProperty("samples",samples.length);data.addProperty("meanMilliseconds",sum/1e6/samples.length);data.addProperty("p95Milliseconds",samples[(int)Math.ceil(samples.length*.95)-1]/1e6);data.addProperty("displayListGroups",groups);data.addProperty("meshDrawCallsIncludingEmission",calls);data.addProperty("scope","warm private renderModel + FX at phase0.5, GLFinish; excludes world static chunks, TESR dispatch, client/UI");performance.add(data);
  }
  for(Object model:map.values())for(Object mesh:(List<?>)model){Field list=mesh.getClass().getDeclaredField("list");list.setAccessible(true);int id=list.getInt(mesh);if(!GL11.glIsList(id))throw new IllegalStateException("Missing list");lists.add(id);}
  int compiledSlugs=map.size();renderer.onResourceManagerReload(null);check("reload");for(int id:lists)if(GL11.glIsList(id))throw new IllegalStateException("Leaked list "+id);
  if(!map.isEmpty())throw new IllegalStateException("Mesh cache retained");camera();render.invoke(renderer,"sun",.5D,45D,"build");check("recompile");capture("sun-after-reload");renderer.onResourceManagerReload(null);
  try {receipt.add("gui",CriticalGUIProbe.run());} catch(Throwable problem) {problem.printStackTrace();JsonObject gui=new JsonObject();gui.addProperty("status","UNAVAILABLE");gui.addProperty("reason",problem.toString());receipt.add("gui",gui);}
  try {receipt.add("staticMaterials",CriticalStaticGLProbe.run());} catch(Throwable problem) {problem.printStackTrace();JsonObject data=new JsonObject();data.addProperty("status","UNAVAILABLE");data.addProperty("reason",problem.toString());receipt.add("staticMaterials",data);}
  try {receipt.add("clientState",CriticalClientStateProbe.run());} catch(Throwable problem) {problem.printStackTrace();JsonObject data=new JsonObject();data.addProperty("status","FAIL");data.addProperty("reason",problem.toString());receipt.add("clientState",data);}
  try {receipt.add("nightClock",CriticalNightProbe.run());} catch(Throwable problem) {problem.printStackTrace();JsonObject data=new JsonObject();data.addProperty("status","UNAVAILABLE");data.addProperty("reason",problem.toString());receipt.add("nightClock",data);}
  receipt.add("frames",frames);receipt.add("warmPerformance",performance);receipt.addProperty("renderCalls",rendered);receipt.addProperty("slugsCompiled",compiledSlugs);receipt.addProperty("displayListsDeleted",lists.size());receipt.addProperty("matrixStackAndEmissionBrightnessChecks",45);receipt.addProperty("textureFrameOffsetChecks",8);receipt.addProperty("vanillaTESRDispatcherCalls",0);receipt.addProperty("seconds",(System.nanoTime()-start)/1e9);receipt.addProperty("glErrors",0);receipt.addProperty("status",receipt.getAsJsonObject("clientState").get("status").getAsString().equals("PASS")?"PASS":"FAIL");
  Files.write(out.resolve("checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(receipt).getBytes("UTF-8"));System.out.println(receipt);p.destroy();
 }
}
