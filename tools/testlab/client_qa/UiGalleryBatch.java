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
    final Set<String> populatedFixtures=new HashSet<String>();
    List<JsonObject> queue=new ArrayList<JsonObject>();
    JsonObject row;
    Object targetGui,targetPanel,previousPanel,currentFixture,v208Terminal;
    Object[] v208LiveTarget; boolean v208HistoryLocalOpened; volatile int v208RemoteIndex=-1;
    volatile JsonObject v208RemoteServerProof; JsonObject v208RemoteClientProof;
    String job="pilot",currentKey="",pendingKey="",signature="";
    int index,ticks,stableFrames,windowId,actionTick;
    volatile int stage;
    volatile int request;
    volatile Throwable serverFailure;
    volatile Map<String,String> expectedValues=Collections.emptyMap();
    volatile String expectedCriticalIdentity="";
    volatile Object legacyPacket;
    volatile String inertInventoryBefore,inertStateBefore;
    volatile Object inertServerContainer,inertServerPSM;
    volatile Map<String,String> inertExpectedValuesBefore;
    volatile JsonObject inertProof;
    Object inertClientContainer,inertClientPSM;
    JsonObject inertHit,lastNavigationProof;
    List<String> localClicks=new ArrayList<String>(); int localClickIndex; JsonArray localClickProofs=new JsonArray();
    int inertClientWindowBefore,inertCollectorWindowBefore;
    long inertPacketsBefore;
    volatile boolean done,cleaned,serverStopped;
    boolean jobRunning,stoppedByControl,localTabClick;
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
            // Lifecycle controls precede startup, sync and remote waiting-stage early returns.
            if(controlBeforeStage())return;
            if(System.currentTimeMillis()-launchMillis>Long.getLong("gtsr.uiqa.timeoutMillis",900000L))throw new AssertionError("Owned batch deadline");
            if(serverFailure!=null){Throwable error=serverFailure;serverFailure=null;throw new RuntimeException("Batch server event",error);}
            // Every active waiting stage must be bounded before any per-stage early return.
            if(stage>=11&&stage<=21&&System.nanoTime()-caseStartNanos>12_000_000_000L)throw new AssertionError("Window/data event timed out: stage="+stage+" request="+request+" expectedWindow="+windowId+" actualGui="+field(q.mc,"currentScreen|field_71462_r")+" expectedSync="+expectedValues+" case="+row);
            if(stage==0){if(ticks<40)return;boot();stage=1;return;}
            if(stage==1){if(player()==null)return;dispatch(1);stage=2;return;}
            if(stage==2){if(request!=0||player()==null||q.serverPlayer==null)return;if(q.number(field(player(),"dimension|field_71093_bK"))!=1048577||q.number(field(q.serverPlayer,"dimension|field_71093_bK"))!=1048577)return;
                if(q.wire==null)q.wire=UiQaWireCounter.install(q,player());worldReadyMillis=System.currentTimeMillis();q.receipt.addProperty("startupToTestLabReadyMillis",worldReadyMillis-launchMillis);stage=10;return;}
            if(stage==90){if(request==0){closeClient();call(q.mc,"displayGuiScreen|func_147108_a",construct("net.minecraft.client.gui.GuiMainMenu"));done=true;q.receipt.addProperty("batchCleanupVerified",cleaned);q.finish(stoppedByControl?new java.util.concurrent.CancellationException("Explicit owned batch stop"):null);}return;}
            if(stage==80){pollControl();return;}
            if(stage==10){next();return;}
            if(stage==11){if(request!=0)return;
                if(isVisual(row)&&!str(row,"visual","").equals("chest")){openVisual();stage=13;syncStartNanos=System.nanoTime();return;}
                Object[] f=(Object[])currentFixture;Object clientWorld=field(q.mc,"theWorld|field_71441_e"),host=call(clientWorld,"getTileEntity|func_147438_o",f[1],f[2],f[3]);
                if(host==null)return;if(f[4]!=null&&type("gregtech.api.interfaces.tileentity.IGregTechTileEntity").isInstance(host)&&q.number(call(host,"getMetaTileID"))!=q.number(call(f[4],"getMetaTileID")))return;
                fixtureWaitNanos=System.nanoTime()-requestStartNanos;syncStartNanos=System.nanoTime();dispatch(3);stage=12;return;}
            if(stage==12){if(request!=0)return;if(flag(row,"legacy"))openLegacy();stage=13;}
            if(stage==13){if(!screenReady(true))return;syncWaitNanos=System.nanoTime()-syncStartNanos;currentKey=pendingKey;act();return;}
            if(stage==21){UiGalleryBatchV208.startRemote(this);return;}
            if(stage==20){if(request!=0)return;if(!screenReady(true))return;UiGalleryBatchV208.verifyRemoteClient(this);syncWaitNanos=System.nanoTime()-syncStartNanos;currentKey=pendingKey;act();return;}
            if(stage==18){if(ticks<actionTick+2)return;if(localClickIndex<localClicks.size()){String name=localClicks.get(localClickIndex++);UiGalleryBatchV208.localClick(this,name);actionTick=ticks;return;}afterNavigation();return;}
            if(stage==14){if(System.nanoTime()-caseStartNanos>12_000_000_000L)throw new AssertionError("No stable synchronized render for "+row);return;}
            if(stage==15){if(request!=0)return;String widget=row.get("inertProbe").getAsString();if(widget.equals("aggregator_slot_24")){Object container=field(player(),"openContainer|field_71070_bA"),slot=((List<?>)field(container,"inventorySlots|field_75151_b")).get(24);if(call(slot,"getStack|func_75211_c")!=null||call(field(player(),"inventory|field_71071_by"),"getItemStack|func_70445_o")!=null)throw new AssertionError("Empty-slot probe requires empty actual slot and cursor");}Object target=q.find(q.panel,widget);if(target==null)throw new AssertionError("Inert probe widget missing "+widget);reveal(target);inertClientContainer=field(player(),"openContainer|field_71070_bA");inertClientPSM=call(call(q.screen,"getSyncManager"),"getMainPSM");inertClientWindowBefore=q.number(field(inertClientContainer,"windowId|field_75152_c"));q.check("focused.valid-matched-window-before."+row.get("name").getAsString(),windowId>0&&windowId==inertClientWindowBefore&&windowId==q.number(field(inertServerContainer,"windowId|field_75152_c")),"Actual server/client/collector window="+windowId);inertHit=hitEvidence(target);q.check("focused.actual-hit-target."+row.get("name").getAsString(),flag(inertHit,"targetInHoveredChain"),String.valueOf(inertHit));inertPacketsBefore=muiC2S();q.click(target);actionTick=ticks;stage=16;return;}
            if(stage==16){if(ticks<actionTick+6)return;dispatch(6);stage=17;return;}
            if(stage==17){if(request!=0)return;Object clientContainer=field(player(),"openContainer|field_71070_bA"),clientPSM=call(call(q.screen,"getSyncManager"),"getMainPSM");int clientWindow=q.number(field(clientContainer,"windowId|field_75152_c"));inertProof.addProperty("clientWindowBefore",inertClientWindowBefore);inertProof.addProperty("clientWindowAfter",clientWindow);inertProof.addProperty("clientContainerIdentityBefore",System.identityHashCode(inertClientContainer));inertProof.addProperty("clientContainerIdentityAfter",System.identityHashCode(clientContainer));inertProof.addProperty("clientPSMIdentityBefore",System.identityHashCode(inertClientPSM));inertProof.addProperty("clientPSMIdentityAfter",System.identityHashCode(clientPSM));inertProof.add("actualHitTarget",inertHit);inertProof.addProperty("muiC2SPacketsBefore",inertPacketsBefore);inertProof.addProperty("muiC2SPacketsAfter",muiC2S());Path proofs=directory.resolve("inert-probes");Files.createDirectories(proofs);Files.write(proofs.resolve(row.get("name").getAsString()+".json"),json(inertProof),StandardOpenOption.CREATE_NEW);q.check("focused.same-client-container-PSM-and-window."+row.get("name").getAsString(),clientContainer==inertClientContainer&&clientPSM==inertClientPSM&&clientWindow==inertClientWindowBefore&&windowId==clientWindow,"Actual client container/PSM identity retained and valid matching window="+clientWindow);if(!row.get("inertProbe").getAsString().startsWith("aggregator_slot_"))q.check("focused.disabled-no-C2S."+row.get("name").getAsString(),muiC2S()==inertPacketsBefore,"Actual ModularUI2 C2S custom payload count unchanged across guarded disabled-widget hit-test");actionTick=ticks;renderStartNanos=System.nanoTime();stage=14;return;}
            if(stage>10&&System.nanoTime()-caseStartNanos>12_000_000_000L)throw new AssertionError("Window/data event timed out "+row);
        }catch(Throwable error){fail(error);}finally{clientHandlerNanos+=System.nanoTime()-start;}
    }
    boolean canCleanupServer()throws Exception {
        if(serverStopped||q.serverPlayer==null)return false;
        Object server=FMLCommonHandler.instance().getMinecraftServerInstance();
        return server!=null&&Boolean.TRUE.equals(call(server,"isServerRunning|func_71278_l"));
    }
    boolean controlBeforeStage()throws Exception {
        boolean explicitStop=Files.isRegularFile(directory.resolve("stop.request"));
        boolean canCleanup=!serverStopped&&(!(explicitStop||stage==90)||canCleanupServer());
        int decision=UiGalleryBatchControl.decide(stage,explicitStop,serverStopped,cleaned,canCleanup);
        if(decision==UiGalleryBatchControl.NONE)return false;
        if(decision==UiGalleryBatchControl.CLEANUP){
            stoppedByControl=true;closeClient();stage=90;dispatch(9);return true;
        }
        request=0;done=true;
        q.receipt.addProperty("batchCleanupVerified",cleaned);
        q.receipt.addProperty("batchStopStage",stage);
        Throwable reason;
        if(decision==UiGalleryBatchControl.FAIL_SERVER_STOPPED){
            reason=new IllegalStateException("Owned integrated server stopped or became unavailable before stage90 cleanup; startup failure is not a product GUI result",serverFailure);
            q.record("batch.unexpected-server-stop",false,reason.toString());
            q.receipt.addProperty("batchResultScope","Owned server/startup lifecycle failure; do not infer product GUI or remote correctness");
            persist("FAILED_SERVER_STOPPED");
        }else{
            stoppedByControl=true;
            reason=new java.util.concurrent.CancellationException("Explicit owned batch stop before an active server was available; no server or world reopened");
            q.receipt.addProperty("batchCleanupScope","No active owned server available; server fixture cleanup not claimed");
            persist("EXPLICIT_STOP_NO_ACTIVE_SERVER");
        }
        q.finish(reason); // Client tick only: shutdown, never launch or reopen a world.
        return true;
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
        pendingKey=str(row,"kind",str(row,"visual",""))+":"+str(row,"variant","native")+":"+flag(row,"rich")+":"+flag(row,"legacy")+":"+flag(row,"populated")+":"+integer(row,"tcdsFuelKind",-1)+":"+flag(row,"actualHistory")+":"+flag(row,"actualRemote");
        if(flag(row,"actualRemote")){UiGalleryBatchV208.startRemote(this);return;}
        if(pendingKey.equals(currentKey)&&field(q.mc,"currentScreen|field_71462_r")==targetGui){act();return;}
        previousPanel=targetPanel;closeClient();targetGui=null;targetPanel=null;legacyPacket=null;dispatch(2);stage=11;
    }
    void closeClient()throws Exception {q.pendingTooltip=null;if(player()!=null)call(player(),"closeScreen|func_71053_j");}
    boolean isVisual(JsonObject r){return str(r,"variant","").equals("visual")||r.has("visual");}
    boolean screenReady(boolean fresh)throws Exception {
        Object gui=field(q.mc,"currentScreen|field_71462_r");if(gui==null)return false;
        if(UiGalleryBatchV208.isNei(row)){
            if(!gui.getClass().getName().equals("codechicken.nei.recipe.GuiCraftingRecipe"))return false;
            if(((List<?>)call(gui,"getCurrentRecipeHandlers")).isEmpty())return false;
            q.gui=gui;q.screen=null;q.panel=gui;
            if(fresh){targetGui=gui;targetPanel=gui;}return gui==targetGui;
        }
        if(flag(row,"legacy")){
            if(!gui.getClass().getName().startsWith(G+"client.gui.terminal."))return false;
            q.gui=gui;q.screen=null;q.panel=gui;if(fresh){if(gui==previousPanel)return false;targetGui=gui;targetPanel=gui;}return gui==targetGui;
        }
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
            if(!expectedCriticalIdentity.isEmpty()&&!expectedCriticalIdentity.equals(UiGalleryBatchV208.criticalIdentity(psm)))return false;
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
        if(!screenReady(false))throw new AssertionError("Target window changed before navigation");localTabClick=false;lastNavigationProof=null;localClickProofs=new JsonArray();
        if(flag(row,"legacy")){if(row.has("page")&&str(row,"kind","").equals("CLUSTER_CONTROLLER"))set(q.gui,"activePage",row.get("page").getAsInt());actionTick=ticks;renderStartNanos=System.nanoTime();stage=14;return;}
        if(row.has("journalTab")||row.has("journalEntry")){Object model=call(q.screen,"model");if(row.has("journalTab"))call(model,"selectTab",Enum.valueOf((Class)type(G+"common.dimension.prosperity.lore.JournalTab"),row.get("journalTab").getAsString()));if(row.has("journalEntry"))call(model,"selectEntry",row.get("journalEntry").getAsString());}
        if(isEntanglement(row)&&row.has("fluidDrafts")){
            Object psm=call(call(q.screen,"getSyncManager"),"getMainPSM"),handler=call(psm,"findSyncHandler","entanglement_session");
            for(int slot=0;slot<6;slot++){Object input=call(handler,"row",slot);if(input==null)continue;String kind=String.valueOf(field(input,"kind"));JsonElement draft=row.getAsJsonObject("fluidDrafts").get(kind);if(draft!=null)call(handler,"edit",slot,draft.getAsString());}
        }
        if(isEntanglement(row)&&row.has("drafts")){
            Object psm=call(call(q.screen,"getSyncManager"),"getMainPSM"),handler=call(psm,"findSyncHandler","entanglement_session");
            for(Map.Entry<String,JsonElement> draft:row.getAsJsonObject("drafts").entrySet())call(handler,"edit",Integer.parseInt(draft.getKey()),draft.getValue().getAsString());
        }
        if(row.has("page")){
            int page=row.get("page").getAsInt();String prefix=isEntanglement(row)?"entanglement_":str(row,"kind","").equals("CLUSTER_CONTROLLER")?"cluster_tab_":"critical_tab_";
            Object pages=q.find(q.panel,isEntanglement(row)?"entanglement_pages":prefix.startsWith("cluster")?"cluster_pages":"critical_pages");
            if(pages!=null){reveal(pages);Object tab=q.find(q.panel,isEntanglement(row)?(page==0?"entanglement_overview_tab":"entanglement_rules_tab"):prefix+page);if(tab!=null){reveal(tab);lastNavigationProof=hitEvidence(tab);q.check("navigation.actual-tab-hit."+row.get("name").getAsString(),flag(lastNavigationProof,"targetInHoveredChain"),String.valueOf(lastNavigationProof));q.click(tab);localTabClick=true;}else call(pages,"setPage",page);}
        }
        if(row.has("click")){
            String name=row.get("click").getAsString();if(name.equals("hub_rate")||name.equals("hub_capacity")){Object pages=q.find(q.panel,"hub_pages");if(pages!=null&&q.number(field(pages,"currentPageIndex"))==1)q.click(q.find(q.panel,"hub_numeric_cancel"));}
            if(!name.equals("critical_runtime_disclosure")&&!name.matches("critical_details_1[0-3]")&&!name.matches("critical_economy_family_(controller|equipment|S|H|E|O|F|M|C|G)")&&!name.equals("critical_processing_modes")&&!name.equals("hub_rate")&&!name.equals("hub_capacity")&&!name.equals("hub_numeric_cancel"))throw new IllegalArgumentException("Batch click is navigation-only: "+name);
            Object button=q.find(q.panel,name);if(button==null)throw new AssertionError("Navigation missing "+name);reveal(button);lastNavigationProof=hitEvidence(button);q.check("navigation.actual-local-hit."+row.get("name").getAsString(),flag(lastNavigationProof,"targetInHoveredChain"),String.valueOf(lastNavigationProof));q.click(button);
        }
        if(row.has("clicks")){localClicks=new ArrayList<String>();for(JsonElement item:row.getAsJsonArray("clicks"))localClicks.add(item.getAsString());localClickIndex=0;actionTick=ticks;stage=18;return;}
        afterNavigation();
    }
    void afterNavigation()throws Exception {
        if(row.has("scroll")){Object widget=row.get("scroll").getAsString().equals("@native")?findList(q.panel):q.find(q.panel,row.get("scroll").getAsString());if(widget==null)throw new AssertionError("Scroll target missing");scroll(widget,100);}
        if(row.has("hover")){q.pendingTooltip=q.find(q.panel,row.get("hover").getAsString());q.hoverFrames=0;q.tooltipPostDraws=0;if(q.pendingTooltip==null)throw new AssertionError("Tooltip target missing");}
        if(row.has("inertProbe")){String widget=row.get("inertProbe").getAsString();if(flag(row,"legacy")||!(widget.equals("aggregator_slot_24")&&str(row,"kind","").equals("SINGULARITY_CRUST_STEAM_BORER")||(widget.equals("hub_mode")||widget.equals("hub_teleport"))&&str(row,"id","").startsWith("ui.terminal.node_editor.")))throw new IllegalArgumentException("Only an empty aggregator slot or disabled nearby-node action is allowed");dispatch(5);stage=15;return;}
        actionTick=ticks;renderStartNanos=System.nanoTime();stage=14;
    }
    Object findList(Object w)throws Exception {if(type("com.cleanroommc.modularui.widgets.ListWidget").isInstance(w))return w;for(Object c:(List<?>)call(w,"getChildren")){Object found=findList(c);if(found!=null)return found;}return null;}
    void reveal(Object widget)throws Exception {
        Object viewport=q.find(q.panel,"critical_terminal_viewport");if(viewport==null)return;
        // Public local scroll data reveals the real target before actual hit-test input.
        Object scroll=call(viewport,"getScrollArea"),area=call(viewport,"getArea");call(q.screen,"onFrameUpdate");
        for(String axis:new String[]{"X","Y"}){Object data=call(scroll,"getScroll"+axis);if(data==null)continue;int[] point=q.screenPoint(widget);int n=axis.equals("X")?0:1,start=q.number(field(area,n==0?"x":"y")),visible=q.number(call(data,"getFullVisibleSize",scroll)),size=q.number(field(call(widget,"getArea"),n==0?"width":"height")),low=point[n]-size/2,high=point[n]+size/2,delta=low<start?low-start:high>start+visible?high-start-visible:0;if(delta!=0)call(data,"scrollTo",scroll,q.number(call(data,"getScroll"))+delta);call(q.screen,"onFrameUpdate");}
    }
    JsonObject hitEvidence(Object target)throws Exception {int[] point=q.screenPoint(target);Object context=call(q.screen,"getContext");call(context,"updateState",point[0],point[1],0f);call(q.screen,"onFrameUpdate");JsonObject proof=new JsonObject();proof.addProperty("widget",String.valueOf(call(target,"getName")));proof.addProperty("widgetClass",target.getClass().getName());proof.addProperty("x",point[0]);proof.addProperty("y",point[1]);proof.add("targetArea",rectangle(call(target,"getArea")));JsonArray chain=new JsonArray();boolean found=false;for(Object located:(List<?>)field(context,"hovered")){Object widget=call(located,"getElement");JsonObject item=new JsonObject();item.addProperty("name",String.valueOf(call(widget,"getName")));item.addProperty("class",widget.getClass().getName());chain.add(item);if(widget==target)found=true;}proof.add("actualHoveredChain",chain);proof.addProperty("targetInHoveredChain",found);proof.add("viewports",viewportStates());return proof;}
    void scroll(Object widget,int amount)throws Exception {int[] p=q.screenPoint(widget);Object context=call(q.screen,"getContext");call(context,"updateState",p[0],p[1],0f);call(q.screen,"onFrameUpdate");set(context,"mouseWheel",-120);for(int n=0;n<amount;n++)call(q.screen,"onMouseScroll",Enum.valueOf((Class)type("com.cleanroommc.modularui.api.UpOrDown"),"DOWN"),1);set(context,"mouseWheel",0);}
    @SubscribeEvent public void hover(net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Pre e){q.syntheticHover(e);if(q.pendingTooltip==null)try{set(e,"mouseX",-10000);set(e,"mouseY",-10000);if(q.screen!=null)call(call(q.screen,"getContext"),"updateState",-10000,-10000,0f);}catch(Exception failure){fail(failure);}}
    @SubscribeEvent public void tooltip(com.cleanroommc.modularui.screen.RichTooltipEvent.PostText e){q.tooltipPainted(e);}
    @SubscribeEvent public void render(TickEvent.RenderTickEvent e){
        if(done||e.phase!=TickEvent.Phase.END||stage!=14||ticks<=actionTick)return;
        try{
            if(!screenReady(false))return;String now="";if(UiGalleryBatchV208.isNei(row)){now=field(q.gui,"guiLeft|field_147003_i")+":"+field(q.gui,"guiTop|field_147009_r")+":"+field(q.gui,"xSize|field_146999_f")+":"+field(q.gui,"ySize|field_147000_g");}else if(flag(row,"legacy")){now=field(q.gui,"guiLeft|field_147003_i")+":"+field(q.gui,"guiTop|field_147009_r")+":"+legacySize()[0]+":"+legacySize()[1];}else if(q.screen==null){Object pos=call(q.panel,"getPos"),size=call(q.panel,"getSize");now=field(pos,"x")+":"+field(pos,"y")+":"+field(size,"width")+":"+field(size,"height");}else{Object a=call(q.panel,"getArea");for(String n:new String[]{"x","y","width","height"})now+=field(a,n)+":";}
            if(row.has("scroll")){Object widget=row.get("scroll").getAsString().equals("@native")?findList(q.panel):q.find(q.panel,row.get("scroll").getAsString());Object scroll=call(widget,"getScrollArea");for(String axis:new String[]{"X","Y"}){Object data=call(scroll,"getScroll"+axis);if(data!=null){if(Boolean.TRUE.equals(call(data,"isAnimating"))){stableFrames=0;return;}now+=":"+call(data,"getScroll");}}}
            if(row.has("page")&&!flag(row,"legacy")){Object p=q.find(q.panel,str(row,"kind","").equals("CLUSTER_CONTROLLER")?"cluster_pages":"critical_pages");if(p!=null&&q.number(field(p,"currentPageIndex"))!=row.get("page").getAsInt())return;}
            if(!now.equals(signature)){signature=now;stableFrames=1;return;}if(++stableFrames<2)return;
            if(q.pendingTooltip!=null&&q.tooltipPostDraws<1)return;
            renderWaitNanos=System.nanoTime()-renderStartNanos;capture();q.pendingTooltip=null;index++;row=null;stage=10;
        }catch(Throwable error){fail(error);}
    }
    void capture()throws Exception {
        String name=row.get("name").getAsString();int gl=q.number(call(type("com.gtnewhorizons.angelica.glsm.GLStateManager"),"glGetError"));q.check("GL."+name,gl==0,"Actual RenderTick.END GL error="+gl);
        Object framebuffer=call(q.mc,"getFramebuffer|func_147110_a");long before=System.nanoTime();call(type("net.minecraft.util.ScreenShotHelper"),"saveScreenshot|func_148259_a",directory.toFile(),name+".png",q.number(field(q.mc,"displayWidth|field_71443_c")),q.number(field(q.mc,"displayHeight|field_71440_d")),framebuffer);long png=System.nanoTime()-before;pngNanos+=png;call(framebuffer,"unbindFramebuffer|func_147609_e");
        Path image=directory.resolve("screenshots").resolve(name+".png");if(!Files.isRegularFile(image))throw new AssertionError("PNG not written");
        JsonObject event=new JsonObject();event.addProperty("galleryCapture",name);event.addProperty("group",row.get("id").getAsString());event.addProperty("job",job);event.addProperty("guiClass",q.gui.getClass().getName());event.addProperty("screenClass",q.screen==null?q.gui.getClass().getName():q.screen.getClass().getName());event.addProperty("panel",UiGalleryBatchV208.isNei(row)?"NEI.ACTUAL_RECIPE_SCREEN":flag(row,"legacy")?"LEGACY_PRODUCTION_GUISCREEN_VISUAL_FIXTURE":q.screen==null?"MUI1.mainWindow":String.valueOf(call(q.panel,"getName")));event.addProperty("windowId",windowId);event.addProperty("syncStatus",UiGalleryBatchV208.isNei(row)?"PUBLIC_NEI_ENTRY_REGISTERED_HANDLER_LOCAL_SCREEN_NO_SERVER_CONTAINER_CLAIM":flag(row,"legacy")?"SERVER_PRODUCTION_ASSEMBLED_SNAPSHOT_ACCEPTED_LOCAL_VISUAL_FIXTURE_NO_ROUTE_AUTH_CLAIM":q.screen==null?"MUI1_MATCHED_WINDOW_AND_INITIALIZED_MAIN_WINDOW":isVisual(row)?"LOCAL_PRODUCTION_BUILDER_VISUAL_FIXTURE":"MATCHED_WINDOW_PSM_INITIALIZED_CUSTOM_SNAPSHOT_READY_PRIMITIVES_OBSERVATION_ONLY");if(q.screen!=null&&!isVisual(row)){event.add("primitiveObservation",new Gson().toJsonTree(primitives(call(call(q.screen,"getSyncManager"),"getMainPSM"))));event.add("initialServerPrimitiveObservation",new Gson().toJsonTree(expectedValues));}event.addProperty("stableRenderFrames",stableFrames);event.addProperty("displayWidth",q.number(field(q.mc,"displayWidth|field_71443_c")));event.addProperty("displayHeight",q.number(field(q.mc,"displayHeight|field_71440_d")));event.addProperty("sha256",hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(image))));
        JsonObject geometry=new JsonObject();int cw,ch;if(UiGalleryBatchV208.isNei(row)){geometry.addProperty("x",q.number(field(q.gui,"guiLeft|field_147003_i")));geometry.addProperty("y",q.number(field(q.gui,"guiTop|field_147009_r")));geometry.addProperty("width",q.number(field(q.gui,"xSize|field_146999_f")));geometry.addProperty("height",q.number(field(q.gui,"ySize|field_147000_g")));cw=q.number(field(q.gui,"width|field_146294_l"));ch=q.number(field(q.gui,"height|field_146295_m"));}else if(flag(row,"legacy")){geometry.addProperty("x",q.number(field(q.gui,"guiLeft|field_147003_i")));geometry.addProperty("y",q.number(field(q.gui,"guiTop|field_147009_r")));geometry.addProperty("width",legacySize()[0]);geometry.addProperty("height",legacySize()[1]);cw=q.number(field(q.gui,"width|field_146294_l"));ch=q.number(field(q.gui,"height|field_146295_m"));}else if(q.screen==null){Object pos=call(q.panel,"getPos"),size=call(q.panel,"getSize");geometry.addProperty("x",q.number(field(pos,"x")));geometry.addProperty("y",q.number(field(pos,"y")));geometry.addProperty("width",q.number(field(size,"width")));geometry.addProperty("height",q.number(field(size,"height")));cw=q.number(field(q.gui,"width|field_146294_l"));ch=q.number(field(q.gui,"height|field_146295_m"));}else{Object a=call(q.panel,"getArea"),canvas=call(call(q.screen,"getContext"),"getScreenArea");for(String n:new String[]{"x","y","width","height"})geometry.addProperty(n,q.number(field(a,n)));cw=q.number(field(canvas,"width"));ch=q.number(field(canvas,"height"));}event.add("geometry",geometry);event.addProperty("panelWithinCanvas",geometry.get("x").getAsInt()>=0&&geometry.get("y").getAsInt()>=0&&geometry.get("x").getAsInt()+geometry.get("width").getAsInt()<=cw&&geometry.get("y").getAsInt()+geometry.get("height").getAsInt()<=ch);
        event.addProperty("page",str(row,"page",name));event.add("localNavigationHits",localClickProofs);if(lastNavigationProof!=null)event.add("actualLocalTabHit",lastNavigationProof);event.addProperty("navigationEvidence",row.has("page")?(localTabClick?"ACTUAL_MUI2_LOCAL_TAB_HITTEST_CLICK_AND_PAGE_READBACK":"PROGRAMMATIC_LOCAL_PAGE_SELECTION_NO_HITTEST_CLAIM"):"NONE_OR_NAMED_LOCAL_NAVIGATION");Object viewport=q.screen==null?null:q.find(q.panel,"critical_terminal_viewport");if(viewport!=null){JsonObject vp=new JsonObject();Object scroll=call(viewport,"getScrollArea");for(String axis:new String[]{"X","Y"}){Object d=call(scroll,"getScroll"+axis);if(d!=null)vp.addProperty("scroll"+axis,q.number(call(d,"getScroll")));}Object va=call(viewport,"getArea");for(String n:new String[]{"x","y","rx","ry","width","height"})vp.addProperty(n,q.number(field(va,n)));JsonArray children=new JsonArray();for(Object child:(List<?>)call(viewport,"getChildren")){JsonObject childInfo=new JsonObject();childInfo.addProperty("name",String.valueOf(call(child,"getName")));childInfo.addProperty("class",child.getClass().getName());Object ca=call(child,"getArea");for(String n:new String[]{"x","y","rx","ry","width","height"})childInfo.addProperty(n,q.number(field(ca,n)));JsonArray grandchildren=new JsonArray();for(Object sub:(List<?>)call(child,"getChildren")){JsonObject subInfo=new JsonObject();subInfo.addProperty("name",String.valueOf(call(sub,"getName")));subInfo.addProperty("class",sub.getClass().getName());Object sa=call(sub,"getArea");for(String n:new String[]{"x","y","rx","ry","width","height"})subInfo.addProperty(n,q.number(field(sa,n)));grandchildren.add(subInfo);}childInfo.add("children",grandchildren);children.add(childInfo);}vp.add("children",children);event.add("viewport",vp);event.addProperty("viewportNavigationIntervention",row.has("page")||row.has("click")||row.has("scroll"));}event.addProperty("populatedVisualState",flag(row,"populated"));event.addProperty("rendererMode",flag(row,"legacy")?"RETAINED_PRODUCTION_CLASS_VISUAL_FIXTURE":"CURRENT_PRODUCTION_TERMINAL_ROUTE");event.addProperty("sourceClassLoader",String.valueOf(q.gui.getClass().getClassLoader()));event.addProperty("sourceCodeLocation",String.valueOf(q.gui.getClass().getProtectionDomain().getCodeSource().getLocation()));event.addProperty("declaredRoute",str(row,"route",""));if(legacyPacket!=null&&!str(row,"kind","").startsWith("CRITICAL_")){event.addProperty("fixtureSnapshotPayloadSHA256",hex(MessageDigest.getInstance("SHA-256").digest((byte[])call(legacyPacket,"getPayload"))));event.addProperty("fixtureAnchor",call(legacyPacket,"getDim")+":"+call(legacyPacket,"getX")+":"+call(legacyPacket,"getY")+":"+call(legacyPacket,"getZ"));Path payloadDir=directory.resolve("snapshot-fixtures");Files.createDirectories(payloadDir);Files.write(payloadDir.resolve(name+".bin"),(byte[])call(legacyPacket,"getPayload"),StandardOpenOption.CREATE_NEW);event.addProperty("fixtureSnapshotPayloadFile","snapshot-fixtures/"+name+".bin");event.addProperty("fixtureSnapshotObservationScope",flag(row,"legacy")?"EXACT_ACCEPTED_LOCAL_VISUAL_FIXTURE_PAYLOAD":"SERVER_PRODUCTION_ASSEMBLED_AT_WINDOW_OPEN_NOT_FRAME_ATOMIC");}event.addProperty("fixtureNote",str(row,"note",""));event.addProperty("caseWallMillis",(System.nanoTime()-caseStartNanos)/1e6);event.addProperty("fixtureWaitMillis",fixtureWaitNanos/1e6);event.addProperty("syncWaitMillis",syncWaitNanos/1e6);event.addProperty("naturalRenderWaitMillis",renderWaitNanos/1e6);event.addProperty("pngCaptureMillis",png/1e6);JsonArray viewportState=viewportStates();event.add("terminalViewportState",viewportState);event.add("rootLayout",rootLayout());UiGalleryBatchV208.annotateCapture(this,event);events.add(event);captured.add(name);System.out.println("UI_GALLERY_CAPTURE "+name+" pngMs="+png/1e6+" caseMs="+(System.nanoTime()-caseStartNanos)/1e6);persist("RUNNING");validateOuterViewports(viewportState);
    }
    void completeJob()throws Exception {
        JsonObject e=new JsonObject();e.addProperty("job",job);e.addProperty("status","CAPTURED_WITH_INDIVIDUAL_FINDINGS");e.addProperty("requested",queue.size());e.addProperty("hotBatchElapsedMillis",(System.nanoTime()-jobStartNanos)/1e6);e.addProperty("pngCaptureMillisTotal",pngNanos/1e6);e.addProperty("clientTickHandlerMillisTotal",clientHandlerNanos/1e6);jobs.add(e);Files.write(directory.resolve("batch-job-"+job+"-receipt.json"),json(e));jobRunning=false;closeClient();persist("WAITING_FOR_EXPLICIT_CONTROL");
        if(Boolean.getBoolean("gtsr.uiqa.batch.keepOpen")){stage=80;System.out.println("UI_GALLERY_IDLE job="+job+" control="+directory.resolve("batch-control.json"));}
        else{dispatch(9);stage=90;}
    }
    void pollControl()throws Exception {Path control=directory.resolve("batch-control.json");if(!Files.isRegularFile(control))return;JsonObject c=new JsonParser().parse(new String(Files.readAllBytes(control),StandardCharsets.UTF_8)).getAsJsonObject();String action=str(c,"action","");if(action.equals("finish")||action.equals("stop")){stoppedByControl=action.equals("stop");dispatch(9);stage=90;return;}String id=str(c,"job","");if(action.equals("queue")&&!jobIds.contains(id)){loadJob(Paths.get(c.get("plan").getAsString()),id);stage=10;}}
    void fail(Throwable failure){failure.printStackTrace();JsonObject e=new JsonObject();e.addProperty("status","FAIL");e.addProperty("group",row==null?"bootstrap":str(row,"id","unknown"));e.addProperty("name",row==null?"bootstrap":str(row,"name","unknown"));e.addProperty("failure",failure.toString());e.add("waitObservation",UiGalleryBatchV208.waitObservation(this));if(request==7)request=0;events.add(e);q.record("batch.case."+e.get("name").getAsString(),false,failure.toString());try{persist("FAILURE_RETAINED");}catch(Exception ignored){}if(stage<10||stage==90){done=true;q.finish(failure);}else{index++;row=null;currentKey="";stage=10;}}
    byte[] json(JsonObject data){return new GsonBuilder().setPrettyPrinting().create().toJson(data).getBytes(StandardCharsets.UTF_8);}
    void persist(String status)throws Exception {q.receipt.addProperty("batchStatus",status);q.receipt.addProperty("batchCapturedCount",captured.size());q.receipt.add("checks",q.checks);Files.write(directory.resolve("checks-live.json"),json(q.receipt));}
    void stopping(){try{persist(done?"SHUTTING_DOWN_AFTER_CLEANUP":"USER_WINDOW_CLOSED");}catch(Exception error){error.printStackTrace();}}
    void stopped(){serverStopped=true;try{q.receipt.addProperty("batchServerStopped",true);q.receipt.addProperty("batchCleanupVerified",cleaned);if(!done&&(stage!=90||!cleaned)){q.receipt.addProperty("status","FAIL");q.receipt.addProperty("batchUnexpectedServerStopStage",stage);persist("FAILED_SERVER_STOPPED");}else persist("SERVER_STOPPED");}catch(Exception error){error.printStackTrace();}}
    @SubscribeEvent public void server(TickEvent.ServerTickEvent e){
        if(done||e.phase!=TickEvent.Phase.END||request==0)return;
        try{Object server=FMLCommonHandler.instance().getMinecraftServerInstance();if(server==null)return;List<?> players=(List<?>)field(call(server,"getConfigurationManager|func_71203_ab"),"playerEntityList|field_72404_b");if(players.isEmpty())return;q.serverPlayer=players.get(0);
            if(request==1){Object configuration=call(server,"getConfigurationManager|func_71203_ab");call(configuration,"func_152605_a|addOp",call(q.serverPlayer,"getGameProfile|func_146103_bH"));call(call(server,"getCommandManager|func_71187_D"),"executeCommand|func_71556_a",q.serverPlayer,"gtsrtest enter");}
            else if(request==2)prepare();
            else if(request==7){if(!UiGalleryBatchV208.observeRemoteServer(this))return;}
            else if(request==3){Object[] f=(Object[])currentFixture;Object host=f[4];if(host!=null&&type("gregtech.api.interfaces.tileentity.IGregTechTileEntity").isInstance(host)&&((Number)call(host,"getTimer")).longValue()<2)return;if(flag(row,"actualHistory")&&UiGalleryBatchV208.bootstrapHistory(this))return;if(flag(row,"legacy")){prepareLegacy();}else{if(!str(row,"kind","").startsWith("CRITICAL_")&&!isEntanglement(row)&&str(row,"variant","").equals("terminal"))prepareLegacy();openServer();Object container=field(q.serverPlayer,"openContainer|field_71070_bA");windowId=q.number(field(container,"windowId|field_75152_c"));try{Object mainPSM=call(call(container,"getSyncManager"),"getMainPSM");expectedValues=primitives(mainPSM);expectedCriticalIdentity=UiGalleryBatchV208.criticalIdentity(mainPSM);}catch(NoSuchMethodException noMui){expectedValues=Collections.emptyMap();}}}
            else if(request==5){inertServerContainer=field(q.serverPlayer,"openContainer|field_71070_bA");inertServerPSM=call(call(inertServerContainer,"getSyncManager"),"getMainPSM");inertCollectorWindowBefore=windowId;inertExpectedValuesBefore=expectedValues;inertInventoryBefore=inventoryLedger();prepareLegacy();inertStateBefore=hex(MessageDigest.getInstance("SHA-256").digest((byte[])call(legacyPacket,"getPayload")));}
            else if(request==6){String inventoryAfter=inventoryLedger();prepareLegacy();String stateAfter=hex(MessageDigest.getInstance("SHA-256").digest((byte[])call(legacyPacket,"getPayload")));Object serverContainer=field(q.serverPlayer,"openContainer|field_71070_bA"),serverPSM=call(call(serverContainer,"getSyncManager"),"getMainPSM");JsonObject proof=new JsonObject();proof.addProperty("serverWindowBefore",q.number(field(inertServerContainer,"windowId|field_75152_c")));proof.addProperty("serverWindowAfter",q.number(field(serverContainer,"windowId|field_75152_c")));proof.addProperty("collectorWindowBefore",inertCollectorWindowBefore);proof.addProperty("collectorWindowAfter",windowId);proof.addProperty("serverContainerIdentityBefore",System.identityHashCode(inertServerContainer));proof.addProperty("serverContainerIdentityAfter",System.identityHashCode(serverContainer));proof.addProperty("serverPSMIdentityBefore",System.identityHashCode(inertServerPSM));proof.addProperty("serverPSMIdentityAfter",System.identityHashCode(serverPSM));proof.addProperty("sameServerContainer",serverContainer==inertServerContainer);proof.addProperty("sameServerPSM",serverPSM==inertServerPSM);proof.addProperty("sameCollectorExpectedValues",expectedValues==inertExpectedValuesBefore);proof.addProperty("inventoryBefore",inertInventoryBefore);proof.addProperty("inventoryAfter",inventoryAfter);proof.addProperty("statePayloadBefore",inertStateBefore);proof.addProperty("statePayloadAfter",stateAfter);proof.addProperty("scope","Actual synthetic MUI2 hit-test on empty slot or guard-disabled nearby action; no item movement or submitted paid operation intended");inertProof=proof;Path proofs=directory.resolve("inert-probes");Files.createDirectories(proofs);Files.write(proofs.resolve(row.get("name").getAsString()+".server.json"),json(proof),StandardOpenOption.CREATE_NEW);q.check("focused.server-inventory-and-state-inert."+row.get("name").getAsString(),inertInventoryBefore.equals(inventoryAfter)&&inertStateBefore.equals(stateAfter)&&serverContainer==inertServerContainer&&serverPSM==inertServerPSM&&windowId==inertCollectorWindowBefore&&windowId>0&&expectedValues==inertExpectedValuesBefore,"Canonical full slot+cursor NBT ledger and production state payload unchanged after actual synthetic hit-test");}
            else if(request==9){call(q.serverPlayer,"closeScreen|func_71053_j");for(Object[] f:fixtures.values()){Object current=call(f[0],"getTileEntity|func_147438_o",f[1],f[2],f[3]);if(current==f[4]){call(f[0],"setBlockToAir|func_147468_f",f[1],f[2],f[3]);}else if(current!=null)throw new AssertionError("Owned fixture changed identity during cleanup");}fixtures.clear();cleaned=true;q.record("batch.owned-fixture-cleanup",true,"Every tracked exact tile removed, actual server container closed; no paid or construction operation requested");}
            request=0;
        }catch(Throwable error){serverFailure=error;request=0;}
    }
    void prepare()throws Exception {
        // Client-only visual panels have no server window: closing the default inventory can send
        // a redundant close-window packet after the new local screen opens. Real containers still close.
        if(!isVisual(row)||field(q.serverPlayer,"openContainer|field_71070_bA")!=field(q.serverPlayer,"inventoryContainer|field_71069_bz"))call(q.serverPlayer,"closeScreen|func_71053_j");Object inv=field(q.serverPlayer,"inventory|field_71071_by");set(inv,"currentItem|field_70461_c",0);call(inv,"setInventorySlotContents|func_70299_a",0,(Object)null);
        if(str(row,"variant","").equals("terminal")){Object terminal=flag(row,"rememberHistory")&&v208Terminal!=null?v208Terminal:call(field(type(G+"common.api.enums.GTSRItemList"),"HubTerminal"),"get",1);if(flag(row,"rememberHistory"))v208Terminal=terminal;call(inv,"setInventorySlotContents|func_70299_a",0,terminal);}
        if(flag(row,"actualHistory")){UiGalleryBatchV208.prepareLiveHistoryTarget(this);return;}
        if(isVisual(row)&&!str(row,"visual","").equals("chest")){currentFixture=null;windowId=0;expectedValues=Collections.emptyMap();expectedCriticalIdentity="";return;}
        String kind=str(row,"kind","chest"),fixtureKey=kind+(flag(row,"populated")?".visual-populated":"")+(row.has("tcdsFuelKind")?".fuel-"+row.get("tcdsFuelKind").getAsInt():"");Object[] f=fixtures.get(fixtureKey);Object w=world();
        if(f==null){int n=fixtures.size(),x=16+(n%13)*3,y=70,z=48+(n/13)*3;call(call(w,"getChunkProvider|func_72863_F"),"loadChunk|func_73158_c",x>>4,z>>4);Object stone=field(type("net.minecraft.init.Blocks"),"stone|field_150348_b");call(w,"setBlock|func_147465_d",x,y-1,z,stone,0,3);
            if(str(row,"visual","").equals("chest")){Object block=field(type(G+"common.dimension.prosperity.encounter.ForgottenLakeEncounterRegistry"),"unsealedChest");call(w,"setBlock|func_147465_d",x,y,z,block,0,3);}else{Object en=Enum.valueOf((Class)type(G+"common.api.enums.MetaTileEntityID"),kind);int id=q.number(field(en,"ID"));Object prototype=((Object[])field(type("gregtech.api.GregTechAPI"),"METATILEENTITIES"))[id];Object stack=call(prototype,"getStackForm",1L);if(!(Boolean)call(call(stack,"getItem|func_77973_b"),"placeBlockAt",stack,q.serverPlayer,w,x,y,z,1,.5f,.5f,.5f,0))throw new AssertionError("Actual GT placement refused "+kind);}
            Object host=call(w,"getTileEntity|func_147438_o",x,y,z);f=new Object[]{w,x,y,z,host};fixtures.put(fixtureKey,f);
        }
        currentFixture=f;call(q.serverPlayer,"setPositionAndUpdate|func_70634_a",((Integer)f[1])+.5,((Integer)f[2])+1d,((Integer)f[3])+2.5);
        if(!str(row,"visual","").equals("chest")){Object machine=call(f[4],"getMetaTileEntity");if(str(row,"id","").startsWith("ui.terminal.node_editor.")){try{set(machine,"mBound",true);set(machine,"mNextRegistrationTick",Long.MAX_VALUE);}catch(NoSuchFieldException composedState){Object state=call(machine,"getHubState");set(state,"bound",true);set(state,"nextRegistrationTick",Long.MAX_VALUE);}}
            if(flag(row,"rich"))call(call(machine,"getCriticalState"),"setRuntimeBatch",123456,987654,2147483647L,"[VISUAL FIXTURE] Long status for layout inspection: input materials and energy pending; no construction, paid work or recipe has been executed. 中文长状态用于检查换行和滚动，控制器保持未形成及IDLE状态。");}
        if(flag(row,"populated"))populate(f);
        if(row.has("tcdsFuelKind")){Object m=call(f[4],"getMetaTileEntity");int fuel=row.get("tcdsFuelKind").getAsInt();call(m,"setFlow",5123);set(m,"mHeat",100d);set(m,"mCurrentFuelKind",fuel);set(m,"mCurrentFuelConsumption",1234);set(m,"mCurrentGasConsumption",1234);set(m,"mCurrentLiquidConsumption",2345);set(m,"mCurrentGasHeatValue",800);set(m,"mCurrentLiquidHeatValue",1200);set(m,"mCurrentAirConsumption",3456);set(m,"mCurrentWaterConsumption",4567);set(m,"mCurrentOutputEquivalent",12345678);}
    }
    Object[] auxiliary(String kind,String key)throws Exception {
        Object[] existing=fixtures.get(key);if(existing!=null)return existing;
        int n=fixtures.size(),x=80+(n%13)*3,y=70,z=48+(n/13)*3;Object w=world();call(call(w,"getChunkProvider|func_72863_F"),"loadChunk|func_73158_c",x>>4,z>>4);
        call(w,"setBlock|func_147465_d",x,y-1,z,field(type("net.minecraft.init.Blocks"),"stone|field_150348_b"),0,3);
        Object en=Enum.valueOf((Class)type(G+"common.api.enums.MetaTileEntityID"),kind),prototype=((Object[])field(type("gregtech.api.GregTechAPI"),"METATILEENTITIES"))[q.number(field(en,"ID"))],stack=call(prototype,"getStackForm",1L);
        if(!Boolean.TRUE.equals(call(call(stack,"getItem|func_77973_b"),"placeBlockAt",stack,q.serverPlayer,w,x,y,z,1,.5f,.5f,.5f,0)))throw new AssertionError("Owned auxiliary placement failed "+kind);
        Object[] f=new Object[]{w,x,y,z,call(w,"getTileEntity|func_147438_o",x,y,z)};fixtures.put(key,f);return f;
    }
    Object[] nativeOutputBus(String key)throws Exception {
        int n=fixtures.size(),x=80+(n%13)*3,y=70,z=48+(n/13)*3;Object w=world();call(call(w,"getChunkProvider|func_72863_F"),"loadChunk|func_73158_c",x>>4,z>>4);
        call(w,"setBlock|func_147465_d",x,y-1,z,field(type("net.minecraft.init.Blocks"),"stone|field_150348_b"),0,3);
        Object item=Enum.valueOf((Class)type("gregtech.api.enums.ItemList"),"Hatch_Output_Bus_LV"),stack=call(item,"get",1L,new Object[0]);
        if(!Boolean.TRUE.equals(call(call(stack,"getItem|func_77973_b"),"placeBlockAt",stack,q.serverPlayer,w,x,y,z,1,.5f,.5f,.5f,0)))throw new AssertionError("Output bus placement refused");
        Object[] result=new Object[]{w,x,y,z,call(w,"getTileEntity|func_147438_o",x,y,z)};fixtures.put(key,result);return result;
    }
    void populate(Object[] f)throws Exception {
        String kind=str(row,"kind","");if(!populatedFixtures.add(kind))return;Object machine=call(f[4],"getMetaTileEntity");
        if(isEntanglement(row)){
            boolean critical=kind.equals("CRITICAL_SINGULARITY_COMPRESSOR");
            set(machine,"mStartUpCheck",Integer.MAX_VALUE);set(machine,"mUpdate",Integer.MAX_VALUE);set(machine,"mUpdated",false);
            set(machine,"mMachine",true);set(machine,"mMaxProgresstime",Integer.MAX_VALUE);set(machine,"mProgresstime",0);
            set(machine,"mHeat",.426d);set(machine,"mOverlimit",120d);set(machine,"mInstability",35d);set(machine,"mTear",critical?12d:0d);
            call(f[4],"enableWorking");
            String[] names=critical?new String[]{"densesteam","densesuperheatedsteam","densesupercriticalsteam","pyrotheum","cryotheum","ic2uumatter"}:new String[]{"steam","ic2superheatedsteam","supercriticalsteam","pyrotheum","cryotheum"};
            int[] amounts={250000,0,0,10000,500,500};Map<String,Long> limits=new LinkedHashMap<String,Long>();
            for(int i=0;i<names.length;i++){
                Object[] input=auxiliary("SINGULARITY_FLUID_INPUT_COMPARTMENT","entanglement."+kind+"."+i);Object hatch=call(input[4],"getMetaTileEntity");
                Object fluid=call(type("net.minecraftforge.fluids.FluidRegistry"),"getFluidStack",names[i],amounts[i]);
                if(fluid!=null&&amounts[i]>0){Object accepted=call(hatch,"fill",fluid,true);if(((Number)accepted).intValue()<=0)throw new AssertionError("Fixture fluid fill refused");}
                if(!Boolean.TRUE.equals(call(machine,"addSteamInputToMachineList",input[4],10)))throw new AssertionError("Input registration refused");
                limits.put(input[1]+","+input[2]+","+input[3]+","+i,0L);
            }
            Object control=call(machine,"inputControl");
            // Apply in six-row chunks through the same validated production method; keep all actual consumption zero.
            Map<String,Long> chunk=new LinkedHashMap<String,Long>();
            for(Map.Entry<String,Long> entry:limits.entrySet()){chunk.put(entry.getKey(),entry.getValue());if(chunk.size()==6){if(!Boolean.TRUE.equals(call(machine,"applyEntanglementLimits",chunk,call(control,"revision"))))throw new AssertionError("Fixture limits refused");chunk=new LinkedHashMap<String,Long>();}}
            if(!chunk.isEmpty()&&!Boolean.TRUE.equals(call(machine,"applyEntanglementLimits",chunk,call(control,"revision"))))throw new AssertionError("Fixture limits refused");
            Object[] bus=nativeOutputBus("entanglement.output."+kind);
            if(!Boolean.TRUE.equals(call(machine,"addOutputBusToMachineList",bus[4],10)))throw new AssertionError("Output bus registration refused");
            JsonObject proof=new JsonObject();proof.addProperty("scope","Owned GT controllers and real input hatch registrations; formed flag and held cycle are explicit visual fixture signals, not a built multiblock. All production limits zero; no fluid production, explosion or paid business is executed.");events.add(proof);
        }else if(kind.equals("STEAM_HUB_ARRAY")||kind.equals("WATER_HUB_ARRAY")){
            boolean water=kind.equals("WATER_HUB_ARRAY");String[] kinds=water?new String[]{"WATER_CACHE_NODE","REINFORCED_WATER_CACHE_NODE","OVERPRESSURE_WATER_CACHE_NODE"}:new String[]{"STEAM_CACHE_NODE","REINFORCED_STEAM_CACHE_NODE","OVERPRESSURE_STEAM_CACHE_NODE"};
            for(int i=0;i<12;i++){Object[] node=auxiliary(kinds[i%3],"populated."+kind+"."+i);Object m=call(node[4],"getMetaTileEntity");call(m,"setCustomName","QA "+(i%2==0?"receiving":"sending")+" node "+(i+1));call(m,"setOutputMode",i%2==0);call(m,"setAutoOutput",i%3==0);long cap=((Number)call(m,"getMaximumFluidCapacity")).longValue();call(m,"setFluidCapacityLimit",Math.max(1L,cap/(i%3+1)));long maxRate=((Number)call(m,"getMaximumHubTransferRate")).longValue();call(m,"setHubTransferRate",maxRate/(i%3+1));Object fluid=call(type("net.minecraftforge.fluids.FluidRegistry"),"getFluidStack",water?"water":"steam",i%4==0?0:(int)Math.min(2_000_000_000L,Math.max(1L,cap/(i%4+2))));call(m,"setFillableStack",fluid);call(machine,"registerCacheNode",node[1],node[2],node[3],1048577,i%2==0);}
        }else if(kind.equals("SINGULARITY_DRILLING_HUB")){
            for(int i=0;i<4;i++){Object[] node=auxiliary(i%2==0?"SINGULARITY_MINER_NODE":"SINGULARITY_DRILLING_NODE","populated.drilling."+i);Object m=call(node[4],"getMetaTileEntity");call(m,"setCustomName","QA worker "+(i+1));if(i%2==0)call(node[4],"disableWorking");call(machine,"registerCacheNode",node[1],node[2],node[3],1048577,false);}
        }else if(kind.equals("SINGULARITY_CRUST_STEAM_BORER")){
            Object inv=call(machine,"getPluginSlotInventory");String[] dimensions={"Ow","Ne","TF"};for(int i=0;i<dimensions.length;i++){Object stack=call(type("gtneioreplugin.plugin.item.ItemDimensionDisplay"),"getItem",dimensions[i]);if(stack==null)throw new AssertionError("Missing actual dimension display "+dimensions[i]);call(inv,"setInventorySlotContents|func_70299_a",i,stack);}call(machine,"forceRefreshPool");set(machine,"mOreMode",2);set(machine,"mFortuneLevel",3);List<?> entries=(List<?>)call(machine,"getOreEntries");if(entries.size()<8)throw new AssertionError("Actual ore pool did not populate: "+entries.size());
        }else if(kind.equals("CLUSTER_CONTROLLER")){
            call(machine,"setMachineEnabled",false);set(machine,"extensionCount",2);set(machine,"mStartUpCheck",Integer.MAX_VALUE);set(machine,"mUpdate",Integer.MAX_VALUE);set(machine,"mUpdated",false);Object topology=call(machine,"getTopology");String[][] kinds={{"CLUSTER_UNIT_CRUSHER","CLUSTER_BOOSTER_SPEED","CLUSTER_UNIT_LOGISTICS"},{"CLUSTER_UNIT_ORE_WASHER","CLUSTER_BOOSTER_PARALLEL","CLUSTER_UNIT_LOGISTICS"},{"CLUSTER_UNIT_CENTRIFUGE","CLUSTER_BOOSTER_PRIMARY","CLUSTER_UNIT_LOGISTICS"}};
            for(int segment=0;segment<kinds.length;segment++)for(int pad=0;pad<3;pad++){Object[] unit=auxiliary(kinds[segment][pad],"populated.cluster."+segment+"."+pad);Object m=call(unit[4],"getMetaTileEntity");set(m,"mStartUpCheck",Integer.MAX_VALUE);set(m,"mUpdate",Integer.MAX_VALUE);set(m,"mUpdated",false);set(m,"mMachine",true);set(m,"unitStructureTier",1);call(m,"onStructureTier",1);if(!Boolean.TRUE.equals(call(machine,"addClusterUnit",m,pad,segment)))throw new AssertionError("Actual topology registration refused "+segment+":"+pad);}
            call(topology,"setSegmentCount",3);Object iron=construct("net.minecraft.item.ItemStack",field(type("net.minecraft.init.Items"),"iron_ingot|field_151042_j"),1,0),gold=construct("net.minecraft.item.ItemStack",field(type("net.minecraft.init.Items"),"gold_ingot|field_151043_k"),1,0);call(machine,"addStatInput",iron,123456L);call(machine,"addStatOutput",gold,654321L);
        }else if(str(row,"id","").startsWith("ui.terminal.node_editor.")){
            if(type(G+"common.machine.base.MTEFilteredCacheNode").isInstance(machine)){long cap=((Number)call(machine,"getMaximumFluidCapacity")).longValue();call(machine,"setCustomName","QA nearby node");call(machine,"setFluidCapacityLimit",Math.max(1L,cap/2));call(machine,"setHubTransferRate",((Number)call(machine,"getMaximumHubTransferRate")).longValue()/2);String fluid=kind.contains("WATER")?"water":"steam";call(machine,"setFillableStack",call(type("net.minecraftforge.fluids.FluidRegistry"),"getFluidStack",fluid,(int)Math.min(2_000_000_000L,Math.max(1L,cap/3))));}
        }else throw new IllegalArgumentException("Unsupported populated visual state "+kind);
        JsonObject e=new JsonObject();e.addProperty("populatedVisualState",kind);e.addProperty("scope","Owned real GT tile placement plus explicit registration/model/slot/stat fixture values; main controllers remain unformed; cluster module formed/tier flags are explicit visual-only signals with both GT check/run gates held, no physically formed structure claim. No recipes, paid binding, construction or inventory transaction executed. Snapshot payload remains production-assembled.");events.add(e);
    }
    boolean isEntanglement(JsonObject value){String kind=str(value,"kind","");return kind.equals("STEAM_SINGULARITY_ENTANGLER")||kind.equals("CRITICAL_SINGULARITY_COMPRESSOR");}
    void openServer()throws Exception {
        if(flag(row,"actualHistory")){UiGalleryBatchV208.openHistory(this);return;}
        Object[] f=(Object[])currentFixture;Object host=f[4];
        if(str(row,"visual","").equals("chest")){call(host,"beginReward",1);call(type(G+"common.gui.mui2.inventory.UnsealedChestFactory"),"open",q.serverPlayer,host);return;}
        Object machine=call(host,"getMetaTileEntity");boolean terminal=str(row,"variant","").equals("terminal");
        if(terminal&&machine.getClass().getName().endsWith("MTECriticalMachine")){Object held=call(q.serverPlayer,"getHeldItem|func_70694_bm");call(call(held,"getItem|func_77973_b"),"onItemUseFirst",held,q.serverPlayer,f[0],f[1],f[2],f[3],1,.5f,.5f,.5f);}
        else if(str(row,"id","").equals("ui.terminal.aggregator"))call(machine,"openConfigGui",q.serverPlayer);
        else call(host,"onRightclick",q.serverPlayer,direction(),.5f,.5f,.5f);
    }
    Object construct(String name,Object...args)throws Exception {for(Constructor<?> c:type(name).getConstructors())if(c.getParameterTypes().length==args.length)try{return c.newInstance(args);}catch(IllegalArgumentException mismatch){}throw new NoSuchMethodException("Constructor "+name);}
    long muiC2S(){long count=0;for(Map.Entry<String,java.util.concurrent.atomic.AtomicLong> e:q.wire.values.entrySet())if(e.getKey().contains(":C2S:modularui2:packets"))count+=e.getValue().get();return count;}
    String inventoryLedger()throws Exception {StringBuilder b=new StringBuilder();Object container=field(q.serverPlayer,"openContainer|field_71070_bA");for(Object slot:(List<?>)field(container,"inventorySlots|field_75151_b")){Object stack=call(slot,"getStack|func_75211_c");b.append(stack==null?"null":call(stack,"writeToNBT|func_77955_b",construct("net.minecraft.nbt.NBTTagCompound"))).append('\n');}Object cursor=call(field(q.serverPlayer,"inventory|field_71071_by"),"getItemStack|func_70445_o");return b.append("cursor=").append(cursor==null?"null":call(cursor,"writeToNBT|func_77955_b",construct("net.minecraft.nbt.NBTTagCompound"))).toString();}
    String legacyType(){String kind=str(row,"kind","");if(str(row,"id","").startsWith("ui.terminal.node_editor."))return "CACHE_NODE";if(kind.equals("TCDS_CONTROLLER"))return "TCDS";if(kind.equals("STEAM_HUB_ARRAY"))return "STEAM_HUB";if(kind.equals("WATER_HUB_ARRAY"))return "WATER_HUB";if(kind.equals("SINGULARITY_DRILLING_HUB"))return "SINGULARITY_HUB";if(kind.equals("CLUSTER_CONTROLLER"))return "CLUSTER_TERMINAL";if(kind.equals("SINGULARITY_CRUST_STEAM_BORER"))return "AGGREGATOR";throw new IllegalArgumentException("Unsupported legacy fixture "+kind);}
    void prepareLegacy()throws Exception {
        Object[] f=(Object[])currentFixture;Object machine=call(f[4],"getMetaTileEntity");String name=legacyType();Object ui=Enum.valueOf((Class)type(G+"common.terminal.TerminalUiType"),name);Object payload;
        if(name.equals("CACHE_NODE"))payload=call(type(G+"common.terminal.CacheNodeTerminalData"),"assembleSnapshot",q.serverPlayer,f[4]);
        else if(name.equals("TCDS"))payload=call(type(G+"common.terminal.TcdsTerminalData"),"assembleSnapshot",machine);
        else if(name.equals("AGGREGATOR"))payload=call(type(G+"common.terminal.AggregatorTerminalData"),"assembleSnapshot",machine);
        else if(name.equals("CLUSTER_TERMINAL")){Object id=call(q.serverPlayer,"getUniqueID|func_110124_au");call(type(G+"common.terminal.ClusterTerminalData"),"resetSession",id);payload=call(type(G+"common.terminal.ClusterTerminalData"),"assembleSnapshot",machine,id);}
        else if(name.equals("SINGULARITY_HUB"))payload=call(type(G+"common.terminal.SingularityHubTerminalData"),"assembleSnapshot",machine);
        else payload=call(type(G+"common.terminal.CacheHubTerminalData"),"assembleSnapshot",ui,machine);
        legacyPacket=construct(G+"common.terminal.PacketTerminalData",ui,1048577,f[1],f[2],f[3],true,Integer.MAX_VALUE,payload);if(flag(row,"legacy")){windowId=0;expectedValues=Collections.emptyMap();}
    }
    void openLegacy()throws Exception {
        Object[] f=(Object[])currentFixture;String name=legacyType(),screenName;
        if(name.equals("CACHE_NODE")){screenName="GuiCacheNodeEditorScreen";call(type(G+"client.terminal.HubTerminalClientCache"),"accept",legacyPacket);}
        else if(name.equals("TCDS")){screenName="GuiTcdsTerminalScreen";call(type(G+"client.gui.terminal."+screenName),"accept",legacyPacket);}
        else if(name.equals("AGGREGATOR")){screenName="GuiAggregatorConfigScreen";call(type(G+"client.terminal.AggregatorClientCache"),"accept",legacyPacket);}
        else if(name.equals("CLUSTER_TERMINAL")){screenName="GuiClusterTerminalScreen";call(type(G+"client.terminal.ClusterTerminalClientCache"),"accept",legacyPacket);}
        else{screenName=name.equals("STEAM_HUB")?"GuiSteamHubStatusScreen":name.equals("WATER_HUB")?"GuiWaterHubStatusScreen":"GuiSingularityHubStatusScreen";call(type(G+"client.terminal.HubTerminalClientCache"),"accept",legacyPacket);}
        Object gui;if(name.equals("AGGREGATOR")){Object clientHost=call(field(q.mc,"theWorld|field_71441_e"),"getTileEntity|func_147438_o",f[1],f[2],f[3]);gui=construct(G+"client.gui.terminal."+screenName,construct(G+"common.gui.terminal.ContainerAggregatorConfig",player(),call(clientHost,"getMetaTileEntity")));}
        else if(name.equals("CLUSTER_TERMINAL"))gui=construct(G+"client.gui.terminal."+screenName,f[1],f[2],f[3],1048577,integer(row,"page",0));
        else gui=construct(G+"client.gui.terminal."+screenName,f[1],f[2],f[3],1048577);
        call(q.mc,"displayGuiScreen|func_147108_a",gui);
    }
    int[] legacySize(){String name=legacyType();return name.equals("AGGREGATOR")?new int[]{475,350}:name.equals("CLUSTER_TERMINAL")?new int[]{620,340}:new int[]{400,240};}
    JsonObject rectangle(Object area)throws Exception {JsonObject result=new JsonObject();for(String key:new String[]{"x","y","rx","ry","width","height"})result.addProperty(key,q.number(field(area,key)));return result;}
    JsonObject paddingState(Object box)throws Exception {JsonObject result=new JsonObject();for(String edge:new String[]{"left","top","right","bottom"}){result.addProperty(edge,q.number(field(box,edge)));String method="get"+Character.toUpperCase(edge.charAt(0))+edge.substring(1);result.addProperty("reported"+method.substring(3),q.number(call(box,method)));try{result.addProperty("scroll"+method.substring(3),q.number(field(box,"scrollPadding"+method.substring(3))));}catch(NoSuchFieldException ordinaryBox){}}return result;}
    JsonArray viewportStates()throws Exception {
        JsonArray states=new JsonArray();if(q.screen==null)return states;
        for(String name:new String[]{"tcds_body_viewport","hub_viewport","aggregator_viewport","cluster_viewport","critical_terminal_viewport","hub_nodes","critical_construction_live_details","critical_status_summary","aggregator_mode_hint","aggregator_help"}){Object widget=q.find(q.panel,name);if(widget==null)continue;JsonObject state=new JsonObject();state.addProperty("name",name);state.addProperty("outerViewport",name.endsWith("viewport"));try{Object area=call(widget,"getArea"),scroll=call(widget,"getScrollArea");state.add("area",rectangle(area));state.add("widgetPadding",paddingState(call(area,"getPadding")));state.add("scrollArea",rectangle(scroll));state.add("scrollPadding",paddingState(call(scroll,"getScrollPadding")));JsonArray children=new JsonArray();int contentWidth=0,contentHeight=0;for(Object child:(List<?>)call(widget,"getChildren")){JsonObject c=rectangle(call(child,"getArea"));c.addProperty("name",String.valueOf(call(child,"getName")));c.addProperty("class",child.getClass().getName());children.add(c);contentWidth=Math.max(contentWidth,c.get("width").getAsInt());contentHeight=Math.max(contentHeight,c.get("height").getAsInt());}state.add("children",children);state.addProperty("contentWidth",contentWidth);state.addProperty("contentHeight",contentHeight);for(String axis:new String[]{"X","Y"}){Object data=call(scroll,"getScroll"+axis);if(data==null)continue;JsonObject d=new JsonObject();for(String method:new String[]{"getScroll","getScrollSize","getThickness"})d.addProperty(method,q.number(call(data,method)));d.addProperty("getVisibleSize",q.number(call(data,"getVisibleSize",scroll)));d.addProperty("getFullVisibleSize",q.number(call(data,"getFullVisibleSize",scroll)));d.addProperty("isScrollBarActive",Boolean.TRUE.equals(call(data,"isScrollBarActive",scroll)));state.add(axis,d);}}catch(Exception metadataError){state.addProperty("metadataError",metadataError.toString());}states.add(state);}return states;
    }
    JsonObject rootLayout()throws Exception {JsonObject result=new JsonObject();if(q.screen==null)return result;result.add("actual",rectangle(call(q.panel,"getArea")));result.add("canvas",rectangle(call(call(q.screen,"getContext"),"getScreenArea")));for(String field:new String[]{"preferredWidth","preferredHeight","margin"})try{result.addProperty(field,q.number(field(q.screen,field)));}catch(NoSuchFieldException optionalField){}return result;}
    void validateOuterViewports(JsonArray states)throws Exception {if(q.screen==null)return;for(JsonElement element:states){JsonObject state=element.getAsJsonObject();if(!flag(state,"outerViewport"))continue;String key=state.get("name").getAsString();q.check("layout.viewport-metadata."+row.get("name").getAsString()+"."+key,!state.has("metadataError"),String.valueOf(state));for(String axis:new String[]{"X","Y"}){if(!state.has(axis))continue;JsonObject data=state.getAsJsonObject(axis);boolean active=data.get("isScrollBarActive").getAsBoolean();if(integer(row,"width",1280)==1280)q.check("layout.normal-no-outer-bar."+row.get("name").getAsString()+"."+axis,!active,"Actual outer scroll bar active="+active+"; inner list bars excluded");else{int content=state.get(axis.equals("X")?"contentWidth":"contentHeight").getAsInt(),visible=data.get("getFullVisibleSize").getAsInt();if(content>visible)q.check("layout.narrow-overflow-scrollable."+row.get("name").getAsString()+"."+axis,active&&data.get("getScrollSize").getAsInt()>=content,"Actual content="+content+" visible="+visible+" range="+data.get("getScrollSize")+" active="+active);}}}}
    void openVisual()throws Exception {
        String visual=str(row,"visual","");
        if(UiGalleryBatchV208.open(this,visual))return;
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
