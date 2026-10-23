package audit;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.eventhandler.*;
import cpw.mods.fml.common.gameevent.*;
import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Owned QA helper only. Never package this mod in the production deliverable. */
@Mod(modid="gtsrmui2clientqa",name="GTSR owned MUI2 client QA",version="1",dependencies="after:gregtech;required-after:gtsr;after:betterquesting;after:gtit;after:gtswn")
public final class Mui2ClientQa {
    final JsonObject receipt=new JsonObject(); final JsonArray checks=new JsonArray(),heapSamples=new JsonArray();
    TicketRestartQa ticketRestart;
    UiGalleryBatch galleryBatch;
    volatile Object host,machine,serverPlayer; volatile int request; volatile Throwable serverFailure;
    Object mc,screen,gui,panel,input,apply,session; int ticks,phase,wait,stable; boolean done,gate,lifecycleSeeded; volatile int stopRunnableExecutions; int hoverFrames,tooltipPostDraws; String pendingShot;
    UiQaScript script; UiQaWireCounter wire; long closedPackets; int actionBefore,flowBefore; Object initialPanel,pendingTooltip; long renderStart; final Map<Integer,List<Long>> frameTimes=new TreeMap<Integer,List<Long>>(); long started=System.currentTimeMillis();
    static Class<?> type(String s)throws Exception{return Class.forName(s);}
    static Object field(Object t,String names)throws Exception {
        for(String n:names.split("\\|"))for(Class<?> c=t instanceof Class?(Class<?>)t:t.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(t instanceof Class?null:t);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(names);
    }
    static void set(Object t,String names,Object value)throws Exception {
        for(String n:names.split("\\|"))for(Class<?> c=t instanceof Class?(Class<?>)t:t.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(t instanceof Class?null:t,adapt(f.getType(),value));return;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(names);
    }
    static Object call(Object t,String names,Object...a)throws Exception {
        List<Method> ms=new ArrayList<Method>();boolean classValue=t instanceof Class&&Arrays.asList("getName","getSimpleName","isInstance","isAssignableFrom","isArray","getComponentType","getEnumConstants").contains(names.split("\\|")[0]);Class<?> root=t instanceof Class&&!classValue?(Class<?>)t:t.getClass();
        for(Class<?> c=root;c!=null;c=c.getSuperclass())ms.addAll(Arrays.asList(c.getDeclaredMethods()));ms.addAll(Arrays.asList(root.getMethods()));
        for(Method m:ms)for(String n:names.split("\\|")){
            if(!m.getName().equals(n)||m.getParameterTypes().length!=a.length||Modifier.isAbstract(m.getModifiers()))continue;boolean fits=true;Class<?>[] ts=m.getParameterTypes();
            for(int i=0;i<ts.length;i++)if(a[i]!=null&&!ts[i].isInstance(a[i])&&!(ts[i].isPrimitive()&&a[i] instanceof Number)&&!(ts[i]==boolean.class&&a[i] instanceof Boolean)&&!(ts[i]==char.class&&a[i] instanceof Character))fits=false;
            if(fits){Object[] converted=a.clone();try{for(int i=0;i<ts.length;i++)converted[i]=adapt(ts[i],a[i]);}catch(IllegalArgumentException bad){continue;}m.setAccessible(true);try{return m.invoke(t instanceof Class&&!classValue?null:t,converted);}catch(InvocationTargetException x){throw new RuntimeException(m.toString(),x.getCause());}}
        }throw new NoSuchMethodException(names+"/"+a.length+" on "+root);
    }
    static Object adapt(Class<?> type,Object value) {
        if(!(value instanceof Number)||!type.isPrimitive())return value;
        Number n=(Number)value;double d=n.doubleValue();
        if(type==double.class)return n.doubleValue();if(type==float.class)return n.floatValue();
        if(d!=Math.rint(d))throw new IllegalArgumentException("Non-integral primitive argument");
        if(type==long.class)return n.longValue();if(type==int.class&&d>=Integer.MIN_VALUE&&d<=Integer.MAX_VALUE)return n.intValue();
        if(type==short.class&&d>=Short.MIN_VALUE&&d<=Short.MAX_VALUE)return n.shortValue();
        if(type==byte.class&&d>=Byte.MIN_VALUE&&d<=Byte.MAX_VALUE)return n.byteValue();
        throw new IllegalArgumentException("Primitive argument range");
    }
    int number(Object o){return ((Number)o).intValue();}
    void record(String name,boolean pass,String detail){JsonObject c=new JsonObject();c.addProperty("name",name);c.addProperty("pass",pass);c.addProperty("detail",detail);checks.add(c);System.out.println("MUI2_CLIENT_QA "+c);}
    void check(String name,boolean pass,String detail){record(name,pass,detail);if(!pass)throw new AssertionError(name+": "+detail);}
    @Mod.EventHandler public void preinit(FMLPreInitializationEvent e)throws Exception {
        if(!Boolean.getBoolean("gtsr.uiqa.enabled")||!FMLCommonHandler.instance().getSide().isClient())return;
        Path cfg=e.getModConfigurationDirectory().toPath().resolve("gtsr/gtsr.cfg");if(!Files.isRegularFile(cfg))return;
        String text=new String(Files.readAllBytes(cfg),StandardCharsets.UTF_8);
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?ms)^\\s*testlab\\s*\\{(.*?)^\\s*\\}").matcher(text);
        gate=m.find()&&java.util.regex.Pattern.compile("(?m)^\\s*B:enabled\\s*=\\s*true\\s*$").matcher(m.group(1)).find();
    }
    @Mod.EventHandler public void init(FMLInitializationEvent e)throws Exception {if(gate){Object listener=this;if("ticket-restart".equals(System.getProperty("gtsr.uiqa.mode"))){ticketRestart=new TicketRestartQa(this);listener=ticketRestart;}else if("gallery-batch".equals(System.getProperty("gtsr.uiqa.mode"))){galleryBatch=new UiGalleryBatch(this);listener=galleryBatch;}FMLCommonHandler.instance().bus().register(listener);net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(listener);}}
    @Mod.EventHandler public void serverStarted(FMLServerStartedEvent event) {
        if(galleryBatch==null)return;
        try{UiGalleryBatchBqStartup.beforeFirstLogin(galleryBatch);}
        catch(Throwable failure){galleryBatch.serverFailure=failure;record("batch.bq-barrier.failed",false,failure.toString());}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void syntheticHover(net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Pre event) {
        if(done||pendingTooltip==null)return;
        try{int[] point=screenPoint(pendingTooltip);set(event,"mouseX",point[0]);set(event,"mouseY",point[1]);call(call(screen,"getContext"),"updateState",point[0],point[1],0f);call(screen,"onFrameUpdate");}catch(Throwable failure){finish(failure);}
    }
    @SubscribeEvent public void tooltipPainted(com.cleanroommc.modularui.screen.RichTooltipEvent.PostText event) {
        if(done||pendingTooltip==null)return;tooltipPostDraws++;
        try{JsonObject paint=new JsonObject();paint.addProperty("x",event.getX());paint.addProperty("y",event.getY());paint.addProperty("text",String.valueOf(call(event.getTooltip(),"getAsStrings")));receipt.add("actualTooltipPostText",paint);}catch(Throwable failure){receipt.addProperty("tooltipPaintMetadataFailure",failure.toString());}
    }
    @Mod.EventHandler public void stopping(FMLServerStoppingEvent event) {
        if(galleryBatch!=null){galleryBatch.stopping();return;}
        if(ticketRestart!=null){ticketRestart.stopping();return;}
        if(!gate||lifecycleSeeded)return;lifecycleSeeded=true;
        try {
            call(type("com.miaokatze.gtsr.common.gui.mui2.Mui2ServerActions"),"enqueue",new Runnable(){public void run(){stopRunnableExecutions++;}});
            UUID id=UUID.fromString("00000000-0000-0000-0000-000000012138");
            Constructor<?> ctor=type("com.miaokatze.gtsr.common.terminal.ClusterTerminalData$ClusterSession").getDeclaredConstructor(int.class,int.class,int.class,int.class);ctor.setAccessible(true);
            ((Map)field(type("com.miaokatze.gtsr.common.terminal.ClusterTerminalData"),"SESSIONS")).put(id,ctor.newInstance(1048577,-4096,70,-4096));
            ((Map)field(type("com.miaokatze.gtsr.common.terminal.ClusterTerminalData"),"SESSION_OWNERS")).put(id,new Object());
            JsonObject seed=new JsonObject();seed.addProperty("queue",((Queue<?>)field(type("com.miaokatze.gtsr.common.gui.mui2.Mui2ServerActions"),"PENDING")).size());seed.addProperty("sessions",((Map<?,?>)field(type("com.miaokatze.gtsr.common.terminal.ClusterTerminalData"),"SESSIONS")).size());seed.addProperty("owners",((Map<?,?>)field(type("com.miaokatze.gtsr.common.terminal.ClusterTerminalData"),"SESSION_OWNERS")).size());receipt.add("lastTickLifecycleSeed",seed);
        }catch(Throwable t){receipt.addProperty("lifecycleSeedFailure",t.toString());}
    }
    @Mod.EventHandler public void stopped(FMLServerStoppedEvent event) {
        if(galleryBatch!=null){galleryBatch.stopped();return;}
        if(ticketRestart!=null){ticketRestart.stopped();return;}
        if(!gate||!lifecycleSeeded)return;
        try {
            int queue=((Queue<?>)field(type("com.miaokatze.gtsr.common.gui.mui2.Mui2ServerActions"),"PENDING")).size(),sessions=((Map<?,?>)field(type("com.miaokatze.gtsr.common.terminal.ClusterTerminalData"),"SESSIONS")).size(),owners=((Map<?,?>)field(type("com.miaokatze.gtsr.common.terminal.ClusterTerminalData"),"SESSION_OWNERS")).size();
            JsonObject after=new JsonObject();after.addProperty("queue",queue);after.addProperty("sessions",sessions);after.addProperty("owners",owners);after.addProperty("dummyRunnableExecutions",stopRunnableExecutions);receipt.add("afterServerStoppedLifecycle",after);
            check("lifecycle.final-stop-clears-owned-static-holders",receipt.has("lastTickLifecycleSeed")&&queue==0&&sessions==0&&owners==0&&stopRunnableExecutions==0,"required-after:gtsr FMLServerStoppedEvent actual in-process map/queue dump; dummy Runnable has no world effects");
        }catch(Throwable t){receipt.addProperty("status","FAIL");receipt.addProperty("lifecycleFailure",t.toString());}
        try{Files.write(Paths.get(System.getProperty("gtsr.uiqa.receipt")),new GsonBuilder().setPrettyPrinting().create().toJson(receipt).getBytes(StandardCharsets.UTF_8));}catch(Exception e){e.printStackTrace();}
    }
    @SubscribeEvent public void server(TickEvent.ServerTickEvent e){
        if(done||e.phase!=TickEvent.Phase.END||request==0)return;
        try{
            Object server=FMLCommonHandler.instance().getMinecraftServerInstance(),world=call(server,"worldServerForDimension|func_71218_a",0);
            List<?> ps=(List<?>)field(call(server,"getConfigurationManager|func_71203_ab"),"playerEntityList|field_72404_b");if(ps.isEmpty())return;serverPlayer=ps.get(0);
            if(request==100&&script!=null){script.server();request=0;return;}
            if(request==1){
                Object rules=call(world,"getGameRules|func_82736_K");call(rules,"setOrCreateGameRule|func_82764_b","doMobSpawning","false");call(rules,"setOrCreateGameRule|func_82764_b","doDaylightCycle","false");
                receipt.addProperty("actualClientRenderDistance",String.valueOf(field(field(mc,"gameSettings|field_71474_y"),"renderDistanceChunks|field_151451_c")));
                try{Object configuration=call(server,"getConfigurationManager|func_71203_ab");call(configuration,"setViewDistance|func_152611_a",3);receipt.addProperty("actualIntegratedViewDistance",String.valueOf(call(configuration,"getViewDistance|func_72395_o")));}catch(Exception unavailable){receipt.addProperty("integratedViewDistanceReadbackFailure",unavailable.toString());}

                Object prototype=null;for(Object p:(Object[])field(type("gregtech.api.GregTechAPI"),"METATILEENTITIES"))if(p!=null&&p.getClass().getName().equals("com.miaokatze.gtsr.common.machine.tcds.MTEThermoChemicalDenseSteamGenerator")){prototype=p;break;}
                check("fixture.registered-production-TCDS",prototype!=null,"Actual GT registry prototype");
                call(call(world,"getChunkProvider|func_72863_F"),"loadChunk|func_73158_c",0,0);Object stone=field(type("net.minecraft.init.Blocks"),"stone|field_150348_b");
                for(int x=5;x<=12;x++)for(int z=5;z<=13;z++)call(world,"setBlock|func_147465_d",x,69,z,stone,0,3);
                call(serverPlayer,"setPositionAndUpdate|func_70634_a",8.5,71d,10.5);Object item=call(prototype,"getStackForm",1L);
                check("fixture.GT-ItemMachines-placement",(Boolean)call(call(item,"getItem|func_77973_b"),"placeBlockAt",item,serverPlayer,world,8,70,8,1,.5f,.5f,.5f,0),"Actual placement in fresh owned world");
                host=call(world,"getTileEntity|func_147438_o",8,70,8);machine=call(host,"getMetaTileEntity");
                Object terminal=call(field(type("com.miaokatze.gtsr.common.api.enums.GTSRItemList"),"HubTerminal"),"get",1);
                Object inv=field(serverPlayer,"inventory|field_71071_by");call(inv,"setInventorySlotContents|func_70299_a",0,terminal);set(inv,"currentItem|field_70461_c",0);
                call(call(server,"getConfigurationManager|func_71203_ab"),"func_152605_a|addOp",call(serverPlayer,"getGameProfile|func_146103_bH"));check("fixture.owned-profile-OP2",(Boolean)call(serverPlayer,"canCommandSenderUseCommand|func_70003_b",2,"gtsrtest"),"Only owned offline Mui2Audit profile; actual command permission readback");
                check("fixture.actual-player",serverPlayer.getClass().getName().equals("net.minecraft.entity.player.EntityPlayerMP"),serverPlayer.getClass().getName());
                receipt.addProperty("initialFlow",number(call(machine,"getFlow")));
            }else if(request==2){
                Object dir=Enum.valueOf((Class)type("net.minecraftforge.common.util.ForgeDirection"),"UP");
                check("route.actual-onRightclick",(Boolean)call(machine,"onRightclick",host,serverPlayer,dir,.5f,.5f,.5f),"Production handheld route, GT global switch not changed");
            }else if(request==3){call(machine,"setFlow",456);}
            request=0;
        }catch(Throwable t){serverFailure=t;request=0;t.printStackTrace();}
    }
    @SubscribeEvent public void client(TickEvent.ClientTickEvent e){
        if(done||e.phase!=TickEvent.Phase.END||++ticks<40)return;
        try{
            if(ticks%100==0){Runtime runtime=Runtime.getRuntime();JsonObject memory=new JsonObject();memory.addProperty("elapsedMillis",System.currentTimeMillis()-started);memory.addProperty("phase",phase);memory.addProperty("maxBytes",runtime.maxMemory());memory.addProperty("committedBytes",runtime.totalMemory());memory.addProperty("usedBytes",runtime.totalMemory()-runtime.freeMemory());heapSamples.add(memory);receipt.add("heapSamples",heapSamples);}
            mc=call(type("net.minecraft.client.Minecraft"),"getMinecraft|func_71410_x");if(pendingShot!=null)return;
            if(serverFailure!=null)throw new RuntimeException("Server fixture",serverFailure);
            if(System.currentTimeMillis()-started>Long.getLong("gtsr.uiqa.timeoutMillis",600000L))throw new AssertionError("Timed out phase="+phase);
            Object player=field(mc,"thePlayer|field_71439_g");
            if(phase>=1000&&script!=null){script.tick();return;}
            if(phase==0){
                receipt.addProperty("inputType","Synthetic in-process ModularScreen mouse/key dispatch + lwjgl3ify injectTextEvent via actual MUI2 hit-test/focus; not physical mouse, keyboard or IME");
                receipt.addProperty("runtime","Full client with integrated server, real GL, actual EntityPlayerMP and native network; dedicated NOT_RUN in this receipt");
                receipt.addProperty("productionCodeSource",type("com.miaokatze.gtsr.common.gui.mui2.TcdsTerminalFactory").getProtectionDomain().getCodeSource().getLocation().toString());
                receipt.addProperty("GT-global-switch",String.valueOf(field(type("gregtech.api.modularui2.GTGuis"),"GLOBAL_SWITCH_MUI2")));
                Object settings=field(mc,"gameSettings|field_71474_y");set(settings,"pauseOnLostFocus|field_82881_y",false);String actualLanguage=String.valueOf(field(settings,"language|field_74363_ab"));receipt.addProperty("actualLanguage",actualLanguage);String expectedLanguage=System.getProperty("gtsr.uiqa.language");if(expectedLanguage!=null)check("environment.actual-language",expectedLanguage.equals(actualLanguage),"Actual GameSettings.language="+actualLanguage);
                Class<?> ws=type("net.minecraft.world.WorldSettings"),gt=type("net.minecraft.world.WorldSettings$GameType");
                Object config=ws.getConstructor(long.class,gt,boolean.class,boolean.class,type("net.minecraft.world.WorldType")).newInstance(61312138L,Enum.valueOf((Class)gt,"CREATIVE"),false,false,field(type("net.minecraft.world.WorldType"),"FLAT|field_77138_c"));
                call(config,"enableCommands|func_77159_a");call(mc,"launchIntegratedServer|func_71371_a",System.getProperty("gtsr.uiqa.world","Mui2AuditFresh"),"Owned MUI2 QA",config);next(1);return;
            }
            if(phase==1){if(player==null||number(field(player,"ticksExisted|field_70173_aa"))<60)return;request=1;next(2);return;}
            if(player==null)throw new AssertionError("Client disconnected phase="+phase);
            if(phase==2){if(request!=0)return;Object world=field(mc,"theWorld|field_71441_e"),ch=call(world,"getTileEntity|func_147438_o",8,70,8);if(ch==null||call(ch,"getMetaTileEntity")==null||++stable<20)return;if(wire==null)wire=UiQaWireCounter.install(this,player);if("gallery".equals(System.getProperty("gtsr.uiqa.mode"))){script=new UiQaScript(this,Paths.get(System.getProperty("gtsr.uiqa.plan")));script.gallery.track(call(host,"getWorld|func_145831_w"),8,70,8,host);next(1000);return;}request=2;next(3);return;}
            if(phase==3||phase==20){
                if(request!=0||!captureScreen())return;if(field(session,"snapshot")==null)return;if(++wait<25)return;
                check(phase==3?"route.real-MUI2-screen":"lifecycle.reopen-real-MUI2-screen",String.valueOf(call(panel,"getName")).equals("tcds_terminal"),"screen="+screen.getClass().getName()+" gui="+gui.getClass().getName()+" panel="+call(panel,"getName"));
                if(phase==20){check("lifecycle.reopen-new-panel",panel!=initialPanel,"Fresh factory/session after close");check("lifecycle.reopen-server-value",number(field(field(session,"snapshot"),"flow"))==456,"Persisted authoritative flow456");shot("tcds-reopened");next(21);return;}
                initialPanel=panel;checkBounds(panel);shot("tcds-initial");next(4);return;
            }
            if(phase==4){edit("321");flowBefore=number(call(machine,"getFlow"));actionBefore=number(field(session,"sequence"));click(apply);next(5);return;}
            if(phase==5){if(!applied(321))return;check("C2S.valid-flow",number(call(machine,"getFlow"))==321&&number(field(session,"sequence"))==actionBefore+1,"Real native network; flow="+call(machine,"getFlow"));check("lifecycle.stable-panel-after-action",panel==initialPanel,"No window rebuild");shot("tcds-applied");next(6);return;}
            if(phase==6){edit("2147483648");actionBefore=number(field(session,"sequence"));click(apply);next(7);return;}
            if(phase==7){if(++wait<20)return;check("input.overflow-local-rejection",number(call(machine,"getFlow"))==321&&number(field(session,"sequence"))==actionBefore&&String.valueOf(field(session,"receiptKey")).endsWith("invalid"),"No server request for int overflow");edit("0");click(apply);next(8);return;}
            if(phase==8){if(!applied(1))return;check("C2S.zero-clamped-authoritatively",number(call(machine,"getFlow"))==1,"Server clamp1; receipt applied");edit("789");request=3;next(9);return;}
            if(phase==9){if(request!=0||field(session,"snapshot")==null||number(field(field(session,"snapshot"),"flow"))!=456)return;if(++wait<25)return;check("sync.focused-draft-survives",String.valueOf(call(input,"getText")).equals("789"),"Remote server source456; local draft="+call(input,"getText"));check("sync.same-panel-instance",panel==initialPanel,"Snapshot changed without panel rebuild");shot("tcds-draft-preserved");next(10);return;}
            if(phase==10){key((char)0,1);next(11);return;}
            if(phase==11){if(++wait<30)return;if(field(mc,"currentScreen|field_71462_r")!=null)key((char)0,1);next(12);return;}
            if(phase==12){if(++wait<30)return;Object current=field(mc,"currentScreen|field_71462_r");check("lifecycle.closed-screen",current==null,"Actual wrapper Esc close; current="+(current==null?"null":current.getClass().getName()));Object open=field(serverPlayer,"openContainer|field_71070_bA"),inventory=field(serverPlayer,"inventoryContainer|field_71069_bz");check("lifecycle.server-container-closed",open==inventory,"Real close-window network reached server");closedPackets=wire==null?0:wire.muiPackets();next(13);return;}if(phase==13){if(++wait<40)return;check("lifecycle.closed-no-MUI2-custom-payloads",wire!=null&&wire.muiPackets()==closedPackets,"40 actual client/server ticks; packet counter delta="+(wire==null?-1:wire.muiPackets()-closedPackets));request=2;next(20);return;}
            if(phase==21){if(++wait<20)return;pendingTooltip=find(panel,"tcds_formula_help");shot("tcds-formula-tooltip");next(22);return;}if(phase==22){if(++wait<20)return;String plan=System.getProperty("gtsr.uiqa.plan");if(plan!=null){script=new UiQaScript(this,Paths.get(plan));next(1000);return;}finish(null);}
        }catch(Throwable t){t.printStackTrace();finish(t);}
    }
    void next(int p){phase=p;wait=0;}
    boolean applied(int f)throws Exception {Object s=field(session,"snapshot");call(session,"draft");return !Boolean.TRUE.equals(field(session,"awaiting"))&&s!=null&&number(field(s,"flow"))==f;}
    boolean captureScreen()throws Exception {
        gui=field(mc,"currentScreen|field_71462_r");if(gui==null||!type("com.cleanroommc.modularui.api.IMuiScreen").isInstance(gui))return false;
        screen=call(type("com.cleanroommc.modularui.screen.ModularScreen"),"getCurrent");panel=call(call(screen,"getPanelManager"),"getMainPanel");input=find(panel,"tcds_flow_input");apply=find(panel,"tcds_apply");
        if(input==null||apply==null)return false;Object psm=call(call(screen,"getSyncManager"),"getMainPSM");session=call(psm,"findSyncHandler","tcds_session");receipt.addProperty("widgetCount",countWidgets(panel));Map<?,?> handlers=(Map<?,?>)field(psm,"syncHandlers");receipt.addProperty("mainHandlerCount",handlers.size());JsonObject handlerTypes=new JsonObject();for(Map.Entry<?,?> entry:handlers.entrySet())handlerTypes.addProperty(String.valueOf(entry.getKey()),entry.getValue().getClass().getName());receipt.add("mainHandlerTypes",handlerTypes);Object container=call(screen,"getContainer");List<?> slots=(List<?>)field(container,"inventorySlots|field_75151_b");receipt.addProperty("actualContainerSlotCount",slots.size());JsonArray slotMap=new JsonArray();for(Object slot:slots){JsonObject mapping=new JsonObject();mapping.addProperty("containerIndex",number(field(slot,"slotNumber|field_75222_d")));mapping.addProperty("inventoryIndex",number(field(slot,"slotIndex|field_75225_a")));mapping.addProperty("slotClass",slot.getClass().getName());slotMap.add(mapping);}receipt.add("actualContainerSlotMap",slotMap);return session!=null;
    }
    int countWidgets(Object w)throws Exception {int n=1;for(Object c:(List<?>)call(w,"getChildren"))n+=countWidgets(c);return n;}
    Object find(Object w,String name)throws Exception {if(name.equals(String.valueOf(call(w,"getName"))))return w;for(Object c:(List<?>)call(w,"getChildren")){Object found=find(c,name);if(found!=null)return found;}return null;}
    void checkBounds(Object w)throws Exception {Object a=call(w,"getArea");int x=number(field(a,"x")),y=number(field(a,"y")),width=number(field(a,"width")),height=number(field(a,"height"));Object sa=call(call(screen,"getContext"),"getScreenArea");int sw=number(field(sa,"width")),sh=number(field(sa,"height"));check("layout.panel-within-real-scaled-canvas",x>=0&&y>=0&&x+width<=sw&&y+height<=sh,"panel "+x+","+y+" "+width+"x"+height+" canvas "+sw+"x"+sh);}
    int[] screenPoint(Object w)throws Exception {
        Object area=call(w,"getArea"),located;
        float x=number(field(area,"width"))/2f,y=number(field(area,"height"))/2f;
        if(type("com.cleanroommc.modularui.api.layout.IViewport").isInstance(w)){
            Object parent=call(w,"getParent");located=call(type("com.cleanroommc.modularui.screen.viewport.LocatedWidget"),"of",parent);x+=number(field(area,"rx"));y+=number(field(area,"ry"));
        }else located=call(type("com.cleanroommc.modularui.screen.viewport.LocatedWidget"),"of",w);
        Object matrix=call(located,"getTransformationMatrix");return new int[]{number(call(matrix,"transformX",x,y)),number(call(matrix,"transformY",x,y))};
    }
    void click(Object w)throws Exception {int[] pos=screenPoint(w);int x=pos[0],y=pos[1];call(call(screen,"getContext"),"updateState",x,y,0f);set(call(screen,"getContext"),"mouseButton",0);call(screen,"onFrameUpdate");if(!(Boolean)call(screen,"onMouseInputPre",0,true))call(screen,"onMousePressed",0);if(!(Boolean)call(screen,"onMouseInputPre",0,false))call(screen,"onMouseRelease",0);}
    void key(char c,int code)throws Exception {if(!(Boolean)call(screen,"onKeyPressed",c,code))call(type("com.cleanroommc.modularui.screen.ClientScreenHandler"),"keyTyped",gui,c,code);}
    void edit(String value)throws Exception {click(input);key((char)0,207);int erase=Math.max(15,String.valueOf(call(input,"getText")).length()+1);for(int i=0;i<erase;i++)key('\b',14);Object textEvent=type("me.eigenraven.lwjgl3ify.api.InputEvents$TextEvent").getConstructor(String.class).newInstance(value);call(type("me.eigenraven.lwjgl3ify.api.InputEvents"),"injectTextEvent",textEvent);check("input.actual-keyboard-draft-"+value,value.equals(String.valueOf(call(input,"getText"))),"Actual ModularScreen key dispatch + lwjgl3ify injectTextEvent; text="+call(input,"getText")+" focused="+call(input,"isFocused"));}
    void shot(String name){pendingShot=name;hoverFrames=0;tooltipPostDraws=0;}
    @SubscribeEvent public void render(TickEvent.RenderTickEvent e){if(done)return;if(e.phase==TickEvent.Phase.START){renderStart=System.nanoTime();return;}if(screen!=null&&renderStart!=0){List<Long> samples=frameTimes.get(phase);if(samples==null){samples=new ArrayList<Long>();frameTimes.put(phase,samples);}if(samples.size()<1000)samples.add(System.nanoTime()-renderStart);}if(pendingShot==null)return;try{if(pendingTooltip!=null){Object tooltip=call(pendingTooltip,"getTooltip");int dwell=number(call(pendingTooltip,"getTicksHovered"));if((dwell<number(call(tooltip,"getShowUpTimer"))+2||tooltipPostDraws<1)&&++hoverFrames<600)return;record("tooltip.actual-hit-test",Boolean.TRUE.equals(call(pendingTooltip,"isHovering")),"Synthetic Forge DrawScreenEvent pointer through actual hover lookup");record("tooltip.actual-rendered-post-text",tooltipPostDraws>0,"Actual normal-frame RichTooltipEvent.PostText draws="+tooltipPostDraws+" natural GUI dwell ticks="+dwell+"; physical pointer NOT_RUN");pendingTooltip=null;}Object gl=type("com.gtnewhorizons.angelica.glsm.GLStateManager");int error=number(call(gl,"glGetError"));check("GL."+pendingShot,error==0,"Actual client GL error="+error);Object frame=call(mc,"getFramebuffer|func_147110_a");Object message=call(type("net.minecraft.util.ScreenShotHelper"),"saveScreenshot|func_148259_a",Paths.get(System.getProperty("gtsr.uiqa.receipt")).getParent().toFile(),pendingShot+".png",number(field(mc,"displayWidth|field_71443_c")),number(field(mc,"displayHeight|field_71440_d")),frame);System.out.println("MUI2_QA_SCREENSHOT "+message);call(frame,"unbindFramebuffer|func_147609_e");pendingShot=null;}catch(Throwable t){finish(t);}}
    void finish(Throwable failure){if(done)return;done=true;receipt.add("checks",checks);if(wire!=null)receipt.add("nativeCustomPayloadMeasurements",wire.snapshot());boolean allChecksPass=failure==null;for(JsonElement c:checks)allChecksPass&=c.getAsJsonObject().get("pass").getAsBoolean();receipt.addProperty("status",allChecksPass?"PASS":"FAIL");JsonObject frames=new JsonObject();for(Map.Entry<Integer,List<Long>> entry:frameTimes.entrySet()){List<Long> values=entry.getValue();Collections.sort(values);if(values.isEmpty())continue;JsonObject q=new JsonObject();q.addProperty("n",values.size());q.addProperty("p50Millis",values.get((values.size()-1)/2)/1e6);q.addProperty("p95Millis",values.get((int)Math.floor((values.size()-1)*.95))/1e6);q.addProperty("maxMillis",values.get(values.size()-1)/1e6);frames.add("phase"+entry.getKey(),q);}receipt.add("actualRenderEventDurations",frames);receipt.addProperty("renderMeasurementScope","FML RenderTick START-to-END wall time by bounded phase; full client/background mods included; not exclusive widget CPU, not benchmark attribution");receipt.addProperty("elapsedMillis",System.currentTimeMillis()-started);receipt.addProperty("phase",phase);receipt.addProperty("physicalInputAndIME","NOT_RUN");receipt.addProperty("dedicatedServer","NOT_RUN");receipt.addProperty("pilotSlotConservation","NOT_RUN: initial TCDS pilot has no business slots; scripted production slot results are individual checks/scriptEvents");if(failure!=null){receipt.addProperty("failure",failure.toString());JsonArray stack=new JsonArray();for(Throwable c=failure;c!=null;c=c.getCause()){stack.add(new JsonPrimitive(c.toString()));for(StackTraceElement s:c.getStackTrace())stack.add(new JsonPrimitive(s.toString()));}receipt.add("stack",stack);}try{Files.write(Paths.get(System.getProperty("gtsr.uiqa.receipt")),new GsonBuilder().setPrettyPrinting().create().toJson(receipt).getBytes(StandardCharsets.UTF_8));if(mc!=null){call(mc,"shutdown|func_71400_g");}}catch(Exception e){e.printStackTrace();}}
}
