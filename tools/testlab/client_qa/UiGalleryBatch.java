package audit;

import com.google.gson.*;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Explicit owned-only, visual-only event queue. No UiQaScript business suite is executed. */
public final class UiGalleryBatch {
    static final String G="com.miaokatze.gtsr.";
    final Mui2ClientQa q;
    final Path directory,workspace;
    final JsonArray events=new JsonArray(),jobs=new JsonArray();
    final Map<String,Object[]> fixtures=new LinkedHashMap<String,Object[]>();
    final Set<String> captured=new HashSet<String>(),jobIds=new HashSet<String>();
    List<JsonObject> queue=new ArrayList<JsonObject>();
    JsonObject row;
    Object targetGui,targetPanel,previousPanel,currentFixture;
    String job="pilot",currentKey="",pendingKey="",signature="";
    int index,stage,ticks,stableFrames,windowId,actionTick;
    volatile int request;
    volatile Throwable serverFailure;
    volatile Map<String,String> expectedValues=Collections.emptyMap();
    boolean done,jobRunning,stoppedByControl,cleaned;
    long launchMillis=Long.getLong("gtsr.uiqa.launchEpochMillis",System.currentTimeMillis()),worldReadyMillis,jobStartNanos,caseStartNanos,requestStartNanos,syncStartNanos,renderStartNanos,pngNanos,clientHandlerNanos;
    long fixtureWaitNanos,syncWaitNanos,renderWaitNanos;
    UiGalleryBatch(Mui2ClientQa owner)throws Exception {
        q=owner;directory=Paths.get(System.getProperty("gtsr.uiqa.receipt")).getParent();workspace=directory.getParent().getParent();
        q.receipt.add("scriptEvents",events);q.receipt.add("batchJobs",jobs);
        q.receipt.addProperty("capturePipeline","Opt-in ClientTick/ServerTick/RenderTick event queue; no business regression script");
        q.receipt.addProperty("physicalInputAndIME","NOT_RUN");
        loadJob(Paths.get(System.getProperty("gtsr.uiqa.plan")),"pilot");
    }
    static Object call(Object t,String n,Object...args)throws Exception{return Mui2ClientQa.call(t,n,args);}
    static Object field(Object t,String n)throws Exception{return Mui2ClientQa.field(t,n);}
    static void set(Object t,String n,Object v)throws Exception{Mui2ClientQa.set(t,n,v);}
    static Class<?> type(String n)throws Exception{return Mui2ClientQa.type(n);}
    static String str(JsonObject o,String key,String fallback){return o.has(key)?o.get(key).getAsString():fallback;}
    static int integer(JsonObject o,String key,int fallback){return o.has(key)?o.get(key).getAsInt():fallback;}
    static boolean flag(JsonObject o,String key){return o.has(key)&&o.get(key).getAsBoolean();}
    Object world()throws Exception{return field(q.serverPlayer,"worldObj|field_70170_p");}
    Object player()throws Exception{return field(q.mc,"thePlayer|field_71439_g");}
    Object direction()throws Exception{return Enum.valueOf((Class)type("net.minecraftforge.common.util.ForgeDirection"),"UP");}
    void loadJob(Path plan,String name)throws Exception {
        Path normalized=plan.toAbsolutePath().normalize();
        if(!normalized.startsWith(workspace)||!name.matches("[A-Za-z0-9_-]+")||jobIds.contains(name))throw new IllegalArgumentException("Use a new named plan under this owned workspace");
        byte[] raw=Files.readAllBytes(normalized);JsonArray array=new JsonParser().parse(new String(raw,StandardCharsets.UTF_8)).getAsJsonArray();
        List<JsonObject> next=new ArrayList<JsonObject>();Set<String> names=new HashSet<String>();
        for(JsonElement item:array){JsonObject r=item.getAsJsonObject();String file=r.get("name").getAsString();if(file.contains("/")||file.contains("\\")||!names.add(file))throw new IllegalArgumentException("Duplicate or unsafe capture name "+file);next.add(r);}
        Path copies=directory.resolve("batch-plans");Files.createDirectories(copies);Files.write(copies.resolve(name+".json"),raw,StandardOpenOption.CREATE_NEW);
        queue=next;job=name;jobIds.add(name);index=0;row=null;jobRunning=true;currentKey="";jobStartNanos=0;
        JsonObject e=new JsonObject();e.addProperty("batchJob",name);e.addProperty("sourcePlan",normalized.toString());e.addProperty("sha256",hex(MessageDigest.getInstance("SHA-256").digest(raw)));e.addProperty("plannedCaptures",queue.size());events.add(e);
    }
    static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format("%02x",v&255));return b.toString();}
    void dispatch(int value){requestStartNanos=System.nanoTime();request=value;}
    @SubscribeEvent public void client(TickEvent.ClientTickEvent e){
        if(done||e.phase!=TickEvent.Phase.END)return;long start=System.nanoTime();ticks++;
        try{
            q.mc=call(type("net.minecraft.client.Minecraft"),"getMinecraft|func_71410_x");
            if(System.currentTimeMillis()-launchMillis>Long.getLong("gtsr.uiqa.timeoutMillis",900000L))throw new AssertionError("Owned batch deadline");
            if(serverFailure!=null){Throwable error=serverFailure;serverFailure=null;throw new RuntimeException("Batch server event",error);}
            if(stage>=11&&stage<=14&&System.nanoTime()-caseStartNanos>12_000_000_000L)throw new AssertionError("Window/data event timed out: stage="+stage+" expectedWindow="+windowId+" actualGui="+field(q.mc,"currentScreen|field_71462_r")+" expectedSync="+expectedValues+" case="+row);
            if(stage==0){if(ticks<40)return;boot();stage=1;return;}
            if(stage==1){if(player()==null)return;dispatch(1);stage=2;return;}
            if(stage==2){if(request!=0||player()==null||q.serverPlayer==null)return;if(q.number(field(player(),"dimension|field_71093_bK"))!=1048577||q.number(field(q.serverPlayer,"dimension|field_71093_bK"))!=1048577)return;
                if(q.wire==null)q.wire=UiQaWireCounter.install(q,player());worldReadyMillis=System.currentTimeMillis();q.receipt.addProperty("startupToTestLabReadyMillis",worldReadyMillis-launchMillis);stage=10;return;}
            if(stage==90){if(request==0){closeClient();call(q.mc,"displayGuiScreen|func_147108_a",construct("net.minecraft.client.gui.GuiMainMenu"));done=true;q.receipt.addProperty("batchCleanupVerified",cleaned);q.finish(stoppedByControl?new java.util.concurrent.CancellationException("Explicit owned batch stop"):null);}return;}
            if(Files.isRegularFile(directory.resolve("stop.request"))){stoppedByControl=true;closeClient();dispatch(9);stage=90;return;}
            if(stage==80){pollControl();return;}
            if(stage==10){next();return;}
            if(stage==11){if(request!=0)return;
                if(isVisual(row)&&!str(row,"visual","").equals("chest")){openVisual();stage=13;syncStartNanos=System.nanoTime();return;}
                Object[] f=(Object[])currentFixture;Object clientWorld=field(q.mc,"theWorld|field_71441_e"),host=call(clientWorld,"getTileEntity|func_147438_o",f[1],f[2],f[3]);
                if(host==null)return;if(f[4]!=null&&type("gregtech.api.interfaces.tileentity.IGregTechTileEntity").isInstance(host)&&q.number(call(host,"getMetaTileID"))!=q.number(call(f[4],"getMetaTileID")))return;
                fixtureWaitNanos=System.nanoTime()-requestStartNanos;syncStartNanos=System.nanoTime();dispatch(3);stage=12;return;}
            if(stage==12){if(request!=0)return;stage=13;}
            if(stage==13){if(!screenReady(true))return;syncWaitNanos=System.nanoTime()-syncStartNanos;currentKey=pendingKey;act();return;}
            if(stage==14){if(System.nanoTime()-caseStartNanos>12_000_000_000L)throw new AssertionError("No stable synchronized render for "+row);return;}
            if(stage>10&&System.nanoTime()-caseStartNanos>12_000_000_000L)throw new AssertionError("Window/data event timed out "+row);
        }catch(Throwable error){fail(error);}finally{clientHandlerNanos+=System.nanoTime()-start;}
    }
    void boot()throws Exception {
        Object settings=field(q.mc,"gameSettings|field_71474_y");set(settings,"pauseOnLostFocus|field_82881_y",false);
        q.check("batch.actual-language",System.getProperty("gtsr.uiqa.language").equals(String.valueOf(field(settings,"language|field_74363_ab"))),"Actual owned client language");
        Class<?> ws=type("net.minecraft.world.WorldSettings"),gt=type("net.minecraft.world.WorldSettings$GameType");
        Object config=ws.getConstructor(long.class,gt,boolean.class,boolean.class,type("net.minecraft.world.WorldType")).newInstance(61312139L,Enum.valueOf((Class)gt,"CREATIVE"),false,false,field(type("net.minecraft.world.WorldType"),"FLAT|field_77138_c"));
        call(config,"enableCommands|func_77159_a");call(q.mc,"launchIntegratedServer|func_71371_a",System.getProperty("gtsr.uiqa.world"),"Owned UI event gallery",config);
    }
    void next()throws Exception {
        if(jobStartNanos==0)jobStartNanos=System.nanoTime();
        while(index<queue.size()&&captured.contains(queue.get(index).get("name").getAsString()))index++;
        if(index>=queue.size()){completeJob();return;}
        row=queue.get(index);q.phase=2000+index;caseStartNanos=System.nanoTime();fixtureWaitNanos=syncWaitNanos=renderWaitNanos=0;stableFrames=0;signature="";
        int width=integer(row,"width",1280),height=integer(row,"height",720),scale=integer(row,"scale",2);
        if(q.number(field(q.mc,"displayWidth|field_71443_c"))!=width||q.number(field(q.mc,"displayHeight|field_71440_d"))!=height){set(field(q.mc,"gameSettings|field_71474_y"),"guiScale|field_74335_Z",scale);call(q.mc,"resize|func_71370_a",width,height);}
        pendingKey=str(row,"kind",str(row,"visual",""))+":"+str(row,"variant","native")+":"+flag(row,"rich");
        if(pendingKey.equals(currentKey)&&field(q.mc,"currentScreen|field_71462_r")==targetGui){act();return;}
        previousPanel=targetPanel;closeClient();targetGui=null;targetPanel=null;dispatch(2);stage=11;
    }
    void closeClient()throws Exception {q.pendingTooltip=null;if(player()!=null)call(player(),"closeScreen|func_71053_j");}
    boolean isVisual(JsonObject r){return str(r,"variant","").equals("visual")||r.has("visual");}
    boolean screenReady(boolean fresh)throws Exception {
        Object gui=field(q.mc,"currentScreen|field_71462_r");if(gui==null)return false;
        if(gui.getClass().getName().equals("com.gtnewhorizons.modularui.common.internal.wrapper.ModularGui")){
            Object legacy=call(call(gui,"getContext"),"getMainWindow");if(!Boolean.TRUE.equals(call(legacy,"isInitialized")))return false;
            if(windowId<=0||q.number(field(field(player(),"openContainer|field_71070_bA"),"windowId|field_75152_c"))!=windowId)return false;
            q.gui=gui;q.screen=null;q.panel=legacy;if(fresh){if(legacy==previousPanel)return false;targetGui=gui;targetPanel=legacy;}return gui==targetGui&&legacy==targetPanel;
        }
        if(!type("com.cleanroommc.modularui.api.IMuiScreen").isInstance(gui))return false;
        Object screen=call(gui,"getScreen"),panel=call(screen,"getMainPanel"),area=call(panel,"getArea");
        if(q.number(field(area,"width"))<=0||q.number(field(area,"height"))<=0)return false;
        if(!isVisual(row)||str(row,"visual","").equals("chest")){
            Object clientContainer=field(player(),"openContainer|field_71070_bA");if(windowId<=0||q.number(field(clientContainer,"windowId|field_75152_c"))!=windowId)return false;
            Object psm=call(call(screen,"getSyncManager"),"getMainPSM");if(!Boolean.TRUE.equals(call(psm,"isInitialised")))return false;
            // Primitive values are recorded observations, never a live business-consistency gate.
            for(Object handler:((Map<?,?>)field(psm,"syncHandlers")).values()){
                String typeName=handler.getClass().getName();if(!typeName.startsWith(G))continue;
                if(typeName.endsWith("HubTerminalSession")){if(((Number)field(field(handler,"view"),"revision")).longValue()<=0)return false;}
                else try{if(field(handler,"snapshot")==null)return false;}catch(NoSuchFieldException noSnapshot){}
            }
        }
        q.gui=gui;q.screen=screen;q.panel=panel;
        if(fresh){if(panel==previousPanel)return false;targetGui=gui;targetPanel=panel;}
        return !fresh&&(gui!=targetGui||panel!=targetPanel)?false:true;
    }
    Map<String,String> primitives(Object psm)throws Exception {
        Map<String,String> values=new TreeMap<String,String>();
        for(Map.Entry<?,?> e:((Map<?,?>)field(psm,"syncHandlers")).entrySet())try{Object value=call(e.getValue(),"getValue");if(value instanceof Number||value instanceof Boolean||value instanceof String)values.put(String.valueOf(e.getKey()),String.valueOf(value));}catch(NoSuchMethodException noValue){}
        return values;
    }
    void act()throws Exception {
        if(!screenReady(false))throw new AssertionError("Target window changed before navigation");
        if(row.has("journalTab")||row.has("journalEntry")){Object model=call(q.screen,"model");if(row.has("journalTab"))call(model,"selectTab",Enum.valueOf((Class)type(G+"common.dimension.prosperity.lore.JournalTab"),row.get("journalTab").getAsString()));if(row.has("journalEntry"))call(model,"selectEntry",row.get("journalEntry").getAsString());}
        if(row.has("page")){
            int page=row.get("page").getAsInt();String prefix=str(row,"kind","").equals("CLUSTER_CONTROLLER")?"cluster_tab_":"critical_tab_";
            Object pages=q.find(q.panel,prefix.startsWith("cluster")?"cluster_pages":"critical_pages");
            if(pages!=null){call(pages,"setPage",page);reveal(pages);}
        }
        if(row.has("click")){
            String name=row.get("click").getAsString();if(name.equals("hub_rate")||name.equals("hub_capacity")){Object pages=q.find(q.panel,"hub_pages");if(pages!=null&&q.number(field(pages,"currentPageIndex"))==1)q.click(q.find(q.panel,"hub_numeric_cancel"));}
            if(!name.equals("critical_processing_modes")&&!name.equals("hub_rate")&&!name.equals("hub_capacity")&&!name.equals("hub_numeric_cancel"))throw new IllegalArgumentException("Batch click is navigation-only: "+name);
            Object button=q.find(q.panel,name);if(button==null)throw new AssertionError("Navigation missing "+name);reveal(button);q.click(button);
        }
        if(row.has("scroll")){Object widget=row.get("scroll").getAsString().equals("@native")?findList(q.panel):q.find(q.panel,row.get("scroll").getAsString());if(widget==null)throw new AssertionError("Scroll target missing");scroll(widget,100);}
        if(row.has("hover")){q.pendingTooltip=q.find(q.panel,row.get("hover").getAsString());q.hoverFrames=0;q.tooltipPostDraws=0;if(q.pendingTooltip==null)throw new AssertionError("Tooltip target missing");}
        actionTick=ticks;renderStartNanos=System.nanoTime();stage=14;
    }
    Object findList(Object w)throws Exception {if(type("com.cleanroommc.modularui.widgets.ListWidget").isInstance(w))return w;for(Object c:(List<?>)call(w,"getChildren")){Object found=findList(c);if(found!=null)return found;}return null;}
    void reveal(Object widget)throws Exception {
        Object viewport=q.find(q.panel,"critical_terminal_viewport");if(viewport==null)return;
        // Public scroll data only changes local presentation; the production tab still receives real hit-test input.
        Object scroll=call(viewport,"getScrollArea");for(String axis:new String[]{"X","Y"}){Object d=call(scroll,"getScroll"+axis);if(d!=null)call(d,"scrollTo",scroll,0);}
        call(q.screen,"onFrameUpdate");
    }
    void scroll(Object widget,int amount)throws Exception {int[] p=q.screenPoint(widget);Object context=call(q.screen,"getContext");call(context,"updateState",p[0],p[1],0f);call(q.screen,"onFrameUpdate");set(context,"mouseWheel",-120);for(int n=0;n<amount;n++)call(q.screen,"onMouseScroll",Enum.valueOf((Class)type("com.cleanroommc.modularui.api.UpOrDown"),"DOWN"),1);set(context,"mouseWheel",0);}
    @SubscribeEvent public void hover(net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Pre e){q.syntheticHover(e);}
    @SubscribeEvent public void tooltip(com.cleanroommc.modularui.screen.RichTooltipEvent.PostText e){q.tooltipPainted(e);}
    @SubscribeEvent public void render(TickEvent.RenderTickEvent e){
        if(done||e.phase!=TickEvent.Phase.END||stage!=14||ticks<=actionTick)return;
        try{
            if(!screenReady(false))return;String now="";if(q.screen==null){Object pos=call(q.panel,"getPos"),size=call(q.panel,"getSize");now=field(pos,"x")+":"+field(pos,"y")+":"+field(size,"width")+":"+field(size,"height");}else{Object a=call(q.panel,"getArea");for(String n:new String[]{"x","y","width","height"})now+=field(a,n)+":";}
            if(row.has("scroll")){Object widget=row.get("scroll").getAsString().equals("@native")?findList(q.panel):q.find(q.panel,row.get("scroll").getAsString());Object scroll=call(widget,"getScrollArea");for(String axis:new String[]{"X","Y"}){Object data=call(scroll,"getScroll"+axis);if(data!=null){if(Boolean.TRUE.equals(call(data,"isAnimating"))){stableFrames=0;return;}now+=":"+call(data,"getScroll");}}}
            if(row.has("page")){Object p=q.find(q.panel,str(row,"kind","").equals("CLUSTER_CONTROLLER")?"cluster_pages":"critical_pages");if(p!=null&&q.number(field(p,"currentPageIndex"))!=row.get("page").getAsInt())return;}
            if(!now.equals(signature)){signature=now;stableFrames=1;return;}if(++stableFrames<2)return;
            if(q.pendingTooltip!=null&&q.tooltipPostDraws<1)return;
            renderWaitNanos=System.nanoTime()-renderStartNanos;capture();q.pendingTooltip=null;index++;row=null;stage=10;
        }catch(Throwable error){fail(error);}
    }
    void capture()throws Exception {
        String name=row.get("name").getAsString();int gl=q.number(call(type("com.gtnewhorizons.angelica.glsm.GLStateManager"),"glGetError"));q.check("GL."+name,gl==0,"Actual RenderTick.END GL error="+gl);
        Object framebuffer=call(q.mc,"getFramebuffer|func_147110_a");long before=System.nanoTime();call(type("net.minecraft.util.ScreenShotHelper"),"saveScreenshot|func_148259_a",directory.toFile(),name+".png",q.number(field(q.mc,"displayWidth|field_71443_c")),q.number(field(q.mc,"displayHeight|field_71440_d")),framebuffer);long png=System.nanoTime()-before;pngNanos+=png;call(framebuffer,"unbindFramebuffer|func_147609_e");
        Path image=directory.resolve("screenshots").resolve(name+".png");if(!Files.isRegularFile(image))throw new AssertionError("PNG not written");
        JsonObject event=new JsonObject();event.addProperty("galleryCapture",name);event.addProperty("group",row.get("id").getAsString());event.addProperty("job",job);event.addProperty("guiClass",q.gui.getClass().getName());event.addProperty("screenClass",q.screen==null?q.gui.getClass().getName():q.screen.getClass().getName());event.addProperty("panel",q.screen==null?"MUI1.mainWindow":String.valueOf(call(q.panel,"getName")));event.addProperty("windowId",windowId);event.addProperty("syncStatus",q.screen==null?"MUI1_MATCHED_WINDOW_AND_INITIALIZED_MAIN_WINDOW":isVisual(row)?"LOCAL_PRODUCTION_BUILDER_VISUAL_FIXTURE":"MATCHED_WINDOW_PSM_INITIALIZED_CUSTOM_SNAPSHOT_READY_PRIMITIVES_OBSERVATION_ONLY");if(q.screen!=null&&!isVisual(row)){event.add("primitiveObservation",new Gson().toJsonTree(primitives(call(call(q.screen,"getSyncManager"),"getMainPSM"))));event.add("initialServerPrimitiveObservation",new Gson().toJsonTree(expectedValues));}event.addProperty("stableRenderFrames",stableFrames);event.addProperty("displayWidth",q.number(field(q.mc,"displayWidth|field_71443_c")));event.addProperty("displayHeight",q.number(field(q.mc,"displayHeight|field_71440_d")));event.addProperty("sha256",hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(image))));
        JsonObject geometry=new JsonObject();int cw,ch;if(q.screen==null){Object pos=call(q.panel,"getPos"),size=call(q.panel,"getSize");geometry.addProperty("x",q.number(field(pos,"x")));geometry.addProperty("y",q.number(field(pos,"y")));geometry.addProperty("width",q.number(field(size,"width")));geometry.addProperty("height",q.number(field(size,"height")));cw=q.number(field(q.gui,"width|field_146294_l"));ch=q.number(field(q.gui,"height|field_146295_m"));}else{Object a=call(q.panel,"getArea"),canvas=call(call(q.screen,"getContext"),"getScreenArea");for(String n:new String[]{"x","y","width","height"})geometry.addProperty(n,q.number(field(a,n)));cw=q.number(field(canvas,"width"));ch=q.number(field(canvas,"height"));}event.add("geometry",geometry);event.addProperty("panelWithinCanvas",geometry.get("x").getAsInt()>=0&&geometry.get("y").getAsInt()>=0&&geometry.get("x").getAsInt()+geometry.get("width").getAsInt()<=cw&&geometry.get("y").getAsInt()+geometry.get("height").getAsInt()<=ch);
        event.addProperty("page",str(row,"page",name));event.addProperty("navigationEvidence",row.has("page")?"PROGRAMMATIC_LOCAL_PAGE_SELECTION_NO_HITTEST_CLAIM":"NONE_OR_NAMED_LOCAL_NAVIGATION");Object viewport=q.screen==null?null:q.find(q.panel,"critical_terminal_viewport");if(viewport!=null){JsonObject vp=new JsonObject();Object scroll=call(viewport,"getScrollArea");for(String axis:new String[]{"X","Y"}){Object d=call(scroll,"getScroll"+axis);if(d!=null)vp.addProperty("scroll"+axis,q.number(call(d,"getScroll")));}Object va=call(viewport,"getArea");for(String n:new String[]{"x","y","rx","ry","width","height"})vp.addProperty(n,q.number(field(va,n)));JsonArray children=new JsonArray();for(Object child:(List<?>)call(viewport,"getChildren")){JsonObject childInfo=new JsonObject();childInfo.addProperty("name",String.valueOf(call(child,"getName")));childInfo.addProperty("class",child.getClass().getName());Object ca=call(child,"getArea");for(String n:new String[]{"x","y","rx","ry","width","height"})childInfo.addProperty(n,q.number(field(ca,n)));JsonArray grandchildren=new JsonArray();for(Object sub:(List<?>)call(child,"getChildren")){JsonObject subInfo=new JsonObject();subInfo.addProperty("name",String.valueOf(call(sub,"getName")));subInfo.addProperty("class",sub.getClass().getName());Object sa=call(sub,"getArea");for(String n:new String[]{"x","y","rx","ry","width","height"})subInfo.addProperty(n,q.number(field(sa,n)));grandchildren.add(subInfo);}childInfo.add("children",grandchildren);children.add(childInfo);}vp.add("children",children);event.add("viewport",vp);event.addProperty("viewportNavigationIntervention",row.has("page")||row.has("click")||row.has("scroll"));}event.addProperty("fixtureNote",str(row,"note",""));event.addProperty("caseWallMillis",(System.nanoTime()-caseStartNanos)/1e6);event.addProperty("fixtureWaitMillis",fixtureWaitNanos/1e6);event.addProperty("syncWaitMillis",syncWaitNanos/1e6);event.addProperty("naturalRenderWaitMillis",renderWaitNanos/1e6);event.addProperty("pngCaptureMillis",png/1e6);events.add(event);captured.add(name);System.out.println("UI_GALLERY_CAPTURE "+name+" pngMs="+png/1e6+" caseMs="+(System.nanoTime()-caseStartNanos)/1e6);persist("RUNNING");
    }
    void completeJob()throws Exception {
        JsonObject e=new JsonObject();e.addProperty("job",job);e.addProperty("status","CAPTURED_WITH_INDIVIDUAL_FINDINGS");e.addProperty("requested",queue.size());e.addProperty("hotBatchElapsedMillis",(System.nanoTime()-jobStartNanos)/1e6);e.addProperty("pngCaptureMillisTotal",pngNanos/1e6);e.addProperty("clientTickHandlerMillisTotal",clientHandlerNanos/1e6);jobs.add(e);Files.write(directory.resolve("batch-job-"+job+"-receipt.json"),json(e));jobRunning=false;closeClient();persist("WAITING_FOR_EXPLICIT_CONTROL");
        if(Boolean.getBoolean("gtsr.uiqa.batch.keepOpen")){stage=80;System.out.println("UI_GALLERY_IDLE job="+job+" control="+directory.resolve("batch-control.json"));}
        else{dispatch(9);stage=90;}
    }
    void pollControl()throws Exception {Path control=directory.resolve("batch-control.json");if(!Files.isRegularFile(control))return;JsonObject c=new JsonParser().parse(new String(Files.readAllBytes(control),StandardCharsets.UTF_8)).getAsJsonObject();String action=str(c,"action","");if(action.equals("finish")||action.equals("stop")){stoppedByControl=action.equals("stop");dispatch(9);stage=90;return;}String id=str(c,"job","");if(action.equals("queue")&&!jobIds.contains(id)){loadJob(Paths.get(c.get("plan").getAsString()),id);stage=10;}}
    void fail(Throwable failure){failure.printStackTrace();JsonObject e=new JsonObject();e.addProperty("status","FAIL");e.addProperty("group",row==null?"bootstrap":str(row,"id","unknown"));e.addProperty("name",row==null?"bootstrap":str(row,"name","unknown"));e.addProperty("failure",failure.toString());events.add(e);q.record("batch.case."+e.get("name").getAsString(),false,failure.toString());try{persist("FAILURE_RETAINED");}catch(Exception ignored){}if(stage<10||stage==90){done=true;q.finish(failure);}else{index++;row=null;currentKey="";stage=10;}}
    byte[] json(JsonObject data){return new GsonBuilder().setPrettyPrinting().create().toJson(data).getBytes(StandardCharsets.UTF_8);}
    void persist(String status)throws Exception {q.receipt.addProperty("batchStatus",status);q.receipt.addProperty("batchCapturedCount",captured.size());q.receipt.add("checks",q.checks);Files.write(directory.resolve("checks-live.json"),json(q.receipt));}
    void stopping(){try{persist(done?"SHUTTING_DOWN_AFTER_CLEANUP":"USER_WINDOW_CLOSED");}catch(Exception error){error.printStackTrace();}}
    void stopped(){try{q.receipt.addProperty("batchServerStopped",true);q.receipt.addProperty("batchCleanupVerified",cleaned);persist("SERVER_STOPPED");}catch(Exception error){error.printStackTrace();}}
    @SubscribeEvent public void server(TickEvent.ServerTickEvent e){
        if(done||e.phase!=TickEvent.Phase.END||request==0)return;
        try{Object server=FMLCommonHandler.instance().getMinecraftServerInstance();if(server==null)return;List<?> players=(List<?>)field(call(server,"getConfigurationManager|func_71203_ab"),"playerEntityList|field_72404_b");if(players.isEmpty())return;q.serverPlayer=players.get(0);
            if(request==1){Object configuration=call(server,"getConfigurationManager|func_71203_ab");call(configuration,"func_152605_a|addOp",call(q.serverPlayer,"getGameProfile|func_146103_bH"));call(call(server,"getCommandManager|func_71187_D"),"executeCommand|func_71556_a",q.serverPlayer,"gtsrtest enter");}
            else if(request==2)prepare();
            else if(request==3){Object[] f=(Object[])currentFixture;Object host=f[4];if(host!=null&&type("gregtech.api.interfaces.tileentity.IGregTechTileEntity").isInstance(host)&&((Number)call(host,"getTimer")).longValue()<2)return;openServer();Object container=field(q.serverPlayer,"openContainer|field_71070_bA");windowId=q.number(field(container,"windowId|field_75152_c"));try{expectedValues=primitives(call(call(container,"getSyncManager"),"getMainPSM"));}catch(NoSuchMethodException noMui){expectedValues=Collections.emptyMap();}}
            else if(request==9){call(q.serverPlayer,"closeScreen|func_71053_j");for(Object[] f:fixtures.values()){Object current=call(f[0],"getTileEntity|func_147438_o",f[1],f[2],f[3]);if(current==f[4]){call(f[0],"setBlockToAir|func_147468_f",f[1],f[2],f[3]);}else if(current!=null)throw new AssertionError("Owned fixture changed identity during cleanup");}fixtures.clear();cleaned=true;q.record("batch.owned-fixture-cleanup",true,"Every tracked exact tile removed, actual server container closed; no paid or construction operation requested");}
            request=0;
        }catch(Throwable error){serverFailure=error;request=0;}
    }
    void prepare()throws Exception {
        // Client-only visual panels have no server window: closing the default inventory can send
        // a redundant close-window packet after the new local screen opens. Real containers still close.
        if(!isVisual(row)||field(q.serverPlayer,"openContainer|field_71070_bA")!=field(q.serverPlayer,"inventoryContainer|field_71069_bz"))call(q.serverPlayer,"closeScreen|func_71053_j");Object inv=field(q.serverPlayer,"inventory|field_71071_by");set(inv,"currentItem|field_70461_c",0);call(inv,"setInventorySlotContents|func_70299_a",0,(Object)null);
        if(str(row,"variant","").equals("terminal")){Object terminal=call(field(type(G+"common.api.enums.GTSRItemList"),"HubTerminal"),"get",1);call(inv,"setInventorySlotContents|func_70299_a",0,terminal);}
        if(isVisual(row)&&!str(row,"visual","").equals("chest")){currentFixture=null;windowId=0;expectedValues=Collections.emptyMap();return;}
        String kind=str(row,"kind","chest");Object[] f=fixtures.get(kind);Object w=world();
        if(f==null){int n=fixtures.size(),x=16+(n%13)*3,y=70,z=48+(n/13)*3;call(call(w,"getChunkProvider|func_72863_F"),"loadChunk|func_73158_c",x>>4,z>>4);Object stone=field(type("net.minecraft.init.Blocks"),"stone|field_150348_b");call(w,"setBlock|func_147465_d",x,y-1,z,stone,0,3);
            if(str(row,"visual","").equals("chest")){Object block=field(type(G+"common.dimension.prosperity.encounter.ForgottenLakeEncounterRegistry"),"unsealedChest");call(w,"setBlock|func_147465_d",x,y,z,block,0,3);}else{Object en=Enum.valueOf((Class)type(G+"common.api.enums.MetaTileEntityID"),kind);int id=q.number(field(en,"ID"));Object prototype=((Object[])field(type("gregtech.api.GregTechAPI"),"METATILEENTITIES"))[id];Object stack=call(prototype,"getStackForm",1L);if(!(Boolean)call(call(stack,"getItem|func_77973_b"),"placeBlockAt",stack,q.serverPlayer,w,x,y,z,1,.5f,.5f,.5f,0))throw new AssertionError("Actual GT placement refused "+kind);}
            Object host=call(w,"getTileEntity|func_147438_o",x,y,z);f=new Object[]{w,x,y,z,host};fixtures.put(kind,f);
        }
        currentFixture=f;call(q.serverPlayer,"setPositionAndUpdate|func_70634_a",((Integer)f[1])+.5,((Integer)f[2])+1d,((Integer)f[3])+2.5);
        if(!str(row,"visual","").equals("chest")){Object machine=call(f[4],"getMetaTileEntity");if(str(row,"id","").startsWith("ui.terminal.node_editor.")){try{set(machine,"mBound",true);set(machine,"mNextRegistrationTick",Long.MAX_VALUE);}catch(NoSuchFieldException composedState){Object state=call(machine,"getHubState");set(state,"bound",true);set(state,"nextRegistrationTick",Long.MAX_VALUE);}}
            if(flag(row,"rich"))call(call(machine,"getCriticalState"),"setRuntimeBatch",123456,987654,2147483647L,"[VISUAL FIXTURE] Long status for layout inspection: input materials and energy pending; no construction, paid work or recipe has been executed. 中文长状态用于检查换行和滚动，控制器保持未形成及IDLE状态。");}
    }
    void openServer()throws Exception {
        Object[] f=(Object[])currentFixture;Object host=f[4];
        if(str(row,"visual","").equals("chest")){call(host,"beginReward",1);call(type(G+"common.gui.mui2.inventory.UnsealedChestFactory"),"open",q.serverPlayer,host);return;}
        Object machine=call(host,"getMetaTileEntity");boolean terminal=str(row,"variant","").equals("terminal");
        if(terminal&&machine.getClass().getName().endsWith("MTECriticalMachine")){Object held=call(q.serverPlayer,"getHeldItem|func_70694_bm");call(call(held,"getItem|func_77973_b"),"onItemUseFirst",held,q.serverPlayer,f[0],f[1],f[2],f[3],1,.5f,.5f,.5f);}
        else if(str(row,"id","").equals("ui.terminal.aggregator"))call(machine,"openConfigGui",q.serverPlayer);
        else call(host,"onRightclick",q.serverPlayer,direction(),.5f,.5f,.5f);
    }
    Object construct(String name,Object...args)throws Exception {for(Constructor<?> c:type(name).getConstructors())if(c.getParameterTypes().length==args.length)try{return c.newInstance(args);}catch(IllegalArgumentException mismatch){}throw new NoSuchMethodException("Constructor "+name);}
    void openVisual()throws Exception {
        String visual=str(row,"visual","");
        if(visual.equals("wand_families")){call(type(G+"client.gui.mui2.tools.WandMenuScreens"),"openFamilies");return;}
        if(visual.startsWith("wand_profile")){String family=visual.endsWith("singularity")?"singularity":"construction";Object adapter=call(type(G+"common.debug.MachineDebugRegistry"),"findFamily",family),options=call(adapter,"options",(Object)null);call(type(G+"client.gui.mui2.tools.WandMenuScreens"),"openProfile",0,family,options,new int[Array.getLength(options)],true);return;}
        if(visual.equals("journal")){Object model=construct(G+"client.gui.mui2.lore.ProsperityJournalScreen$Model",true,999,999,999);call(type("com.cleanroommc.modularui.factory.ClientGUI"),"open",construct(G+"client.gui.mui2.lore.ProsperityJournalScreen",model));return;}
        Object panel;
        if(visual.equals("navigator")){Object model=construct(G+"client.gui.mui2.lore.ProsperityNavigatorScreen$Model");panel=call(model,"buildPanel");}
        else if(visual.equals("altar")){Object reading=construct(G+"common.dimension.prosperity.altar.SpacetimeAltarStoryNetwork$Reading");set(reading,"dimension",1048577);set(reading,"instance","owned-visual-only-no-authority");set(reading,"nonce",new UUID(0,0));panel=call(construct(G+"client.gui.mui2.lore.SpacetimeAltarStoryScreen$Model",reading),"buildPanel");}
        else throw new IllegalArgumentException("Unknown local production builder visual "+visual);
        call(type("com.cleanroommc.modularui.factory.ClientGUI"),"open",construct("com.cleanroommc.modularui.screen.ModularScreen","gtsr",panel));
    }
}
