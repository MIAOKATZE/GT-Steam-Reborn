package coreglobal.followup.qa;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/** Owned render-thread comparison against the renamed, frozen pre-optimization production bytecode. */
public final class RigMeshNativeCase implements FollowupQA.Case {
    private static final String TYPE = "com.miaokatze.gtsr.client.encounter.";
    private static final int SIZE = 192;
    private final Map<String,Object> result = new LinkedHashMap<String,Object>();
    private final List<Map<String,Object>> models = new ArrayList<Map<String,Object>>();
    private final List<Map<String,Object>> frames = new ArrayList<Map<String,Object>>();
    private final List<String[]> golden = new ArrayList<String[]>();
    private final Set<String> active = new LinkedHashSet<String>();
    private JsonObject expectedClips;
    private int authoredClips;
    private Object mc, manager, fallback, legacy, current, override;
    private List<Object> packs;
    private Path run;
    private int modelIndex, sampleIndex, frameBuffer, colorTexture, passed, comparisons;
    private List<String> clips;
    private double[] center;
    private double radius;
    private String code;
    private byte[] source;
    private Map<String,Object> model;
    private boolean initialized, finished;
    private interface Action { Object run() throws Exception; }
    private void check(boolean value, String name) { ExtensionSupport.check(value, name); passed++; }
    private static Object call(Object target, String names, Object...args) throws Exception { return ExtensionSupport.call(target,names,args); }
    private static Object get(Object target, String names) throws Exception { return ExtensionSupport.get(target,names); }
    private static byte[] read(InputStream input) throws IOException {
        if(input==null)throw new FileNotFoundException("Missing frozen reference resource");
        try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] block=new byte[8192];for(int n;(n=in.read(block))>=0;)out.write(block,0,n);return out.toByteArray();
        }
    }
    private static String sha(byte[] bytes) throws Exception {
        StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format("%02x",b&255));return out.toString();
    }
    private static Object construct(String type, String code) throws Exception {
        try{return Class.forName(TYPE+type).getConstructor(String.class,String.class).newInstance("gtsr",code);}
        catch(InvocationTargetException error){Throwable cause=error.getCause();if(cause instanceof Exception)throw (Exception)cause;if(cause instanceof Error)throw (Error)cause;throw error;}
    }
    @SuppressWarnings("unchecked") private void initialize(Map<String,Object> context) throws Exception {
        run=ExtensionSupport.owned(context);mc=context.get("minecraft");manager=call(mc,"getResourceManager|func_110442_L");
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(RigMeshNativeCase.class.getResourceAsStream("/rigmesh-reference/active-models.txt"),StandardCharsets.UTF_8))){
            for(String line;(line=reader.readLine())!=null;){String value=line.trim();if(!value.isEmpty()&&!value.startsWith("#"))check(active.add(value),"Unique manifest model "+value);}
        }
        check(!active.isEmpty(),"Manifest contains active models");
        check(Arrays.equals(read(RigMeshNativeCase.class.getResourceAsStream("/rigmesh-reference/active-models.txt")),read((InputStream)call(call(manager,"getResource|func_110536_a",location("liminal/active-models.txt")),"getInputStream|func_110527_b"))),"Actual native manifest equals prepared source authority");
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(RigMeshNativeCase.class.getResourceAsStream("/rigmesh-reference/golden.tsv"),StandardCharsets.UTF_8))){
            Set<String> covered=new LinkedHashSet<String>();
            for(String line;(line=reader.readLine())!=null;)if(!line.isEmpty()){String[] row=line.split("\t");if(active.contains(row[0])){check(covered.add(row[0]),"Unique frozen model "+row[0]);golden.add(row);}}
            check(covered.equals(active),"Exact active model set has frozen source references");
        }
        expectedClips=new com.google.gson.JsonParser().parse(new String(read(RigMeshNativeCase.class.getResourceAsStream("/rigmesh-reference/clip-counts.json")),StandardCharsets.UTF_8)).getAsJsonObject();
        for(Class<?> at=manager.getClass();at!=null&&fallback==null;at=at.getSuperclass())for(Field field:at.getDeclaredFields())if(Map.class.isAssignableFrom(field.getType())){
            field.setAccessible(true);Object value=field.get(manager);if(value instanceof Map&&((Map<?,?>)value).containsKey("gtsr"))fallback=((Map<?,?>)value).get("gtsr");
        }
        check(fallback!=null&&fallback.getClass().getName().endsWith("FallbackResourceManager"),"Actual native GTSR fallback resource manager");
        Class<?> packInterface=Class.forName("net.minecraft.client.resources.IResourcePack");
        for(Class<?> at=fallback.getClass();at!=null&&packs==null;at=at.getSuperclass())for(Field field:at.getDeclaredFields())if(List.class.isAssignableFrom(field.getType())){
            field.setAccessible(true);List<?> value=(List<?>)field.get(fallback);if(value!=null&&!value.isEmpty()&&packInterface.isInstance(value.get(0)))packs=(List<Object>)value;
        }
        check(packs!=null,"Native resource-pack list captured for bounded restoration");
        result.put("thread",Thread.currentThread().getName());result.put("renderer",GL11.glGetString(GL11.GL_RENDERER));result.put("glVersion",GL11.glGetString(GL11.GL_VERSION));
        result.put("currentProductCodeSource",Class.forName(TYPE+"RigAsset").getProtectionDomain().getCodeSource().getLocation().toString());
        result.put("legacyReferenceCodeSource",Class.forName(TYPE+"RigAssetLegacyReference").getProtectionDomain().getCodeSource().getLocation().toString());
        result.put("scope","Manifest-selected source-bound models; original frozen RigAsset bytecode against current compiled meshes; every authored clip at two times, framebuffer exact bytes; native synthetic IResourcePack, no physical input, gameplay effects or audio");
        result.put("activeManifestModels",active);
        result.put("models",models);result.put("frameComparisons",frames);
        int oldFbo=GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try{
            frameBuffer=GL30.glGenFramebuffers();colorTexture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,colorTexture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,SIZE,SIZE,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,frameBuffer);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,colorTexture,0);
            check(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"Owned RGBA8 FBO complete");
        }finally{GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,oldFbo);GL11.glPopAttrib();}
        check(GL11.glGetError()==GL11.GL_NO_ERROR,"FBO allocation GL status");initialized=true;
    }
    private Object location(String path)throws Exception{return Class.forName("net.minecraft.util.ResourceLocation").getConstructor(String.class,String.class).newInstance("gtsr",path);}
    private String resourcePath(Object location)throws Exception{return String.valueOf(call(location,"getResourcePath|func_110623_a"));}
    private Object withPack(final String mode, final Action action) throws Exception {
        final String objPath="liminal/"+code+"/models/"+code+".obj";
        final List<Object> original=new ArrayList<Object>(packs);
        Class<?> iface=Class.forName("net.minecraft.client.resources.IResourcePack");
        Object pack=Proxy.newProxyInstance(iface.getClassLoader(),new Class<?>[]{iface},(proxy,method,args)->{
            String name=method.getName();
            if(name.equals("hashCode"))return System.identityHashCode(proxy);
            if(name.equals("equals"))return proxy==args[0];
            if(name.equals("toString")||name.equals("getPackName")||name.equals("func_130077_b"))return "Owned rigmesh "+mode;
            if(name.equals("getResourceDomains")||name.equals("func_110587_b"))return Collections.singleton("gtsr");
            if(name.equals("getPackMetadata")||name.equals("func_135058_a")||name.equals("getPackImage")||name.equals("func_110586_a"))return null;
            boolean matches=args!=null&&args.length==1&&objPath.equals(resourcePath(args[0]));
            if(name.equals("resourceExists")||name.equals("func_110589_b"))return matches;
            if(name.equals("getInputStream")||name.equals("func_110590_a")){
                if(!matches)throw new FileNotFoundException();
                if(mode.equals("io-error"))throw new IOException("owned-reference-I/O-failure");
                if(mode.equals("bad"))return new ByteArrayInputStream("v NaN 0 0\n".getBytes(StandardCharsets.US_ASCII));
                if(mode.equals("sentinel")){
                    ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(source);out.write("\n# owned OBJ precedence sentinel\n".getBytes(StandardCharsets.US_ASCII));return new ByteArrayInputStream(out.toByteArray());
                }
                return new ByteArrayInputStream(source);
            }
            throw new UnsupportedOperationException(name);
        });
        try{
            call(fallback,"addResourcePack|func_110538_a",pack);
            check(packs.size()==original.size()+1&&packs.get(packs.size()-1)==pack,"Pack installed through native addResourcePack");return action.run();
        }finally{
            packs.clear();packs.addAll(original);
            check(packs.size()==original.size(),"Native pack list restored");
            for(int i=0;i<original.size();i++)check(packs.get(i)==original.get(i),"Original native pack identity retained");
        }
    }
    @SuppressWarnings("unchecked") private void loadModel()throws Exception{
        String[] row=golden.get(modelIndex);code=row[0];source=read(RigMeshNativeCase.class.getResourceAsStream("/rigmesh-reference/"+code+".obj"));
        check(sha(source).equals(row[1]),code+" original OBJ provenance");
        try{call(manager,"getResource|func_110536_a",location("liminal/"+code+"/models/"+code+".obj"));throw new AssertionError("Default OBJ still supplied: "+code);}
        catch(FileNotFoundException expected){passed++;}
        current=construct("RigAsset",code);legacy=withPack("original",()->construct("RigAssetLegacyReference",code));
        check(((Number)get(current,"triangles")).intValue()==Integer.parseInt(row[3]),code+" actual current triangle count");
        check(((Number)get(legacy,"triangles")).intValue()==Integer.parseInt(row[3]),code+" frozen legacy triangle count");
        Object mesh=get(current,"mesh");check(shaResolved(mesh).equals(row[2]),code+" actual runtime compiled resolved-bit golden");
        JsonObject rig=(JsonObject)get(legacy,"rig");check(rig.toString().equals(((JsonObject)get(current,"rig")).toString()),code+" identical effective rig JSON");
        clips=new ArrayList<String>();for(Map.Entry<String,JsonElement> entry:rig.getAsJsonObject("animations").entrySet())clips.add(entry.getKey());
        check(!clips.isEmpty(),code+" authored animations");
        check(clips.size()==expectedClips.get(code).getAsInt(),code+" every source-authored clip retained in runtime");authoredClips+=clips.size();
        double[] min={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},max={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        Map<String,List<double[]>> old=(Map<String,List<double[]>>)get(legacy,"meshes");
        for(List<double[]> corners:old.values())for(double[] corner:corners)for(int i=0;i<3;i++){min[i]=Math.min(min[i],corner[i]);max[i]=Math.max(max[i],corner[i]);}
        center=new double[]{(min[0]+max[0])/2,(min[1]+max[1])/2,(min[2]+max[2])/2};radius=Math.max(1,Math.sqrt(Math.pow(max[0]-min[0],2)+Math.pow(max[1]-min[1],2)+Math.pow(max[2]-min[2],2))*.8);
        override=withPack("sentinel",()->construct("RigAsset",code));Object overrideMesh=get(override,"mesh");
        check(!Arrays.equals((byte[])call(mesh,"sourceSha256"),(byte[])call(overrideMesh,"sourceSha256")),code+" valid OBJ override chosen before bundled compiled mesh");
        check(shaResolved(overrideMesh).equals(row[2]),code+" overridden OBJ independently matches rendered geometry");
        for(String mode:new String[]{"bad","io-error"}){
            try{withPack(mode,()->construct("RigAsset",code));throw new AssertionError("Bad/failed existing OBJ fell back: "+mode+" "+code);}
            catch(IOException expected){
                check(!mode.equals("io-error")||"owned-reference-I/O-failure".equals(expected.getMessage()),code+" exact owned resource I/O failure propagated");
            }
            catch(UndeclaredThrowableException expected){
                // The actual native IResourcePack declaration may wrap this deliberately checked fault.
                // Only our precise marker/cause is accepted; unrelated proxy or loader errors still fail.
                Throwable cause=expected.getUndeclaredThrowable();
                check(mode.equals("io-error")&&cause instanceof IOException&&"owned-reference-I/O-failure".equals(cause.getMessage()),code+" exact native proxy I/O boundary rejected without fallback");
            }
        }
        model=new LinkedHashMap<String,Object>();model.put("code",code);model.put("sourceSha256",row[1]);model.put("clips",clips);model.put("primitiveBytes",call(mesh,"retainedPrimitiveBytes"));
        model.put("oldExpandedPrimitiveBytes",Integer.parseInt(row[3])*192L);model.put("resourcePackPriorityAndFailure","PASS");models.add(model);sampleIndex=0;
    }
    private String shaResolved(Object mesh)throws Exception{
        double[] p=(double[])call(mesh,"positions"),uv=(double[])call(mesh,"texCoords");ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        @SuppressWarnings("unchecked") Map<String,int[]> groups=(Map<String,int[]>)call(mesh,"groups");
        for(Map.Entry<String,int[]> entry:groups.entrySet()){
            byte[] n=entry.getKey().getBytes(StandardCharsets.UTF_8);out.writeInt(n.length);out.write(n);int[] corners=entry.getValue();out.writeInt(corners.length/2);
            for(int i=0;i<corners.length;i+=2){for(int a=0;a<3;a++)out.writeLong(Double.doubleToRawLongBits(p[corners[i]*3+a]));for(int a=0;a<2;a++)out.writeLong(Double.doubleToRawLongBits(uv[corners[i+1]*2+a]));}
        }
        out.close();return sha(bytes.toByteArray());
    }
    private byte[] render(Object asset,String clip,double tick)throws Exception{
        int oldFbo=GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glPushClientAttrib(-1);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();
        try{
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,frameBuffer);GL11.glViewport(0,0,SIZE,SIZE);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glColorMask(true,true,true,true);GL11.glClearColor(13/255F,23/255F,37/255F,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_FOG);GL11.glEnable(GL11.GL_ALPHA_TEST);GL11.glAlphaFunc(GL11.GL_GREATER,.01F);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glColor4f(1,1,1,1);
            GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(-radius,radius,-radius,radius,-radius*8,radius*8);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GL11.glRotated(17,1,0,0);GL11.glRotated(31,0,1,0);GL11.glTranslated(-center[0],-center[1],-center[2]);
            call(asset,"render",clip,tick,1F);ByteBuffer pixels=BufferUtils.createByteBuffer(SIZE*SIZE*4);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);GL11.glReadPixels(0,0,SIZE,SIZE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
            byte[] bytes=new byte[pixels.remaining()];pixels.get(bytes);check(GL11.glGetError()==GL11.GL_NO_ERROR,"Actual model draw/readpixels GL status");return bytes;
        }finally{
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();GL11.glPopClientAttrib();GL11.glPopAttrib();GL13.glActiveTexture(active);GL11.glMatrixMode(mode);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,oldFbo);
        }
    }
    @SuppressWarnings("unchecked") private void release(Object asset)throws Exception{
        if(asset==null)return;Map<String,Integer> lists=(Map<String,Integer>)get(asset,"lists");List<Integer> ids=new ArrayList<Integer>(lists.values());call(asset,"release");
        check(lists.isEmpty(),"Display-list owner cleared");for(int id:ids)check(!GL11.glIsList(id),"Owned native display list deleted");call(asset,"release");
    }
    private void compareFrame()throws Exception{
        String clip=clips.get(sampleIndex/2);JsonObject definition=((JsonObject)get(legacy,"rig")).getAsJsonObject("animations").getAsJsonObject(clip);
        double duration=definition.has("frames")?definition.get("frames").getAsDouble():1,speed=definition.has("speed")?definition.get("speed").getAsDouble():1;
        double tick=sampleIndex%2==0?0:(Math.max(0,Math.min(duration-.125,duration*.61+.125))/Math.max(.000001,speed));
        byte[] expected=render(legacy,clip,tick),actual=render(current,clip,tick);check(Arrays.equals(expected,actual),code+" old/new exact framebuffer "+clip+" "+tick);
        int foreground=0;for(int i=0;i<expected.length;i+=4)if((expected[i]&255)!=13||(expected[i+1]&255)!=23||(expected[i+2]&255)!=37)foreground++;
        // Death/spawn/visibility tracks may intentionally hide every group at some samples; require nonempty once per model.
        int prior=model.containsKey("maxForegroundPixels")?((Number)model.get("maxForegroundPixels")).intValue():0;model.put("maxForegroundPixels",Math.max(prior,foreground));
        if(sampleIndex==0){byte[] overridden=render(override,clip,tick);check(Arrays.equals(expected,overridden),code+" native OBJ override exact framebuffer");}
        Map<String,Object> frame=new LinkedHashMap<String,Object>();frame.put("code",code);frame.put("clip",clip);frame.put("gameTick",tick);frame.put("sha256",sha(actual));frame.put("exactBytes",actual.length);frame.put("foregroundPixels",foreground);frame.put("status","PASS");frames.add(frame);comparisons++;sampleIndex++;
        if(sampleIndex==clips.size()*2){
            check(((Number)model.get("maxForegroundPixels")).intValue()>0,code+" framebuffer contains actual model pixels");release(legacy);release(current);release(override);legacy=current=override=null;model.put("status","PASS");modelIndex++;
        }
    }
    public boolean tick(String phase,Map<String,Object> context)throws Exception{
        if(!phase.equals("render"))return false;if(finished)return true;
        try{
            if(!initialized){initialize(context);return false;}
            if(modelIndex<golden.size()){if(current==null)loadModel();else compareFrame();return false;}
            GL30.glDeleteFramebuffers(frameBuffer);GL11.glDeleteTextures(colorTexture);check(!GL30.glIsFramebuffer(frameBuffer)&&!GL11.glIsTexture(colorTexture),"Owned FBO and texture deleted");
            check(models.size()==active.size()&&comparisons==authoredClips*2,"All manifest models and authored clips at two sample times");result.put("status","PASS");result.put("checksPassed",passed);result.put("modelsPassed",models.size());result.put("authoredClipsPassed",authoredClips);result.put("frameComparisonsPassed",comparisons);result.put("nativePackListRestored",true);result.put("glListsAndFboReleased",true);
            ExtensionSupport.write(run.resolve("client/rigmesh-native-case.json"),result);finished=true;return true;
        }catch(Throwable error){
            try{release(legacy);release(current);release(override);}catch(Throwable cleanup){error.addSuppressed(cleanup);}
            if(frameBuffer!=0)GL30.glDeleteFramebuffers(frameBuffer);if(colorTexture!=0)GL11.glDeleteTextures(colorTexture);
            result.put("status","FAIL");result.put("failure",error.toString());result.put("checksPassed",passed);result.put("failedModel",code);result.put("sampleIndex",sampleIndex);if(run!=null)ExtensionSupport.write(run.resolve("client/rigmesh-native-case.json"),result);
            if(error instanceof Exception)throw (Exception)error;if(error instanceof Error)throw (Error)error;throw new IllegalStateException(error);
        }
    }
    public Map<String,Object> result(){return new LinkedHashMap<String,Object>(result);}
}
