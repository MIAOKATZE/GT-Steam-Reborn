package audit;

import com.google.gson.*;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.*;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.world.WorldEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Owned, double-gated native ticket lifecycle test. No fake worlds or seeded ticket objects. */
public final class TicketRestartQa {
    static final String LOADER="com.miaokatze.gtsr.common.critical.CriticalChunkLoading";
    static final String MANAGER="com.miaokatze.gtsr.common.critical.CriticalChunkTicketManager";
    static final String FORGE="net.minecraftforge.common.ForgeChunkManager";
    static final String DIM="net.minecraftforge.common.DimensionManager";
    final Mui2ClientQa q;
    final JsonArray events=new JsonArray();
    final List<Cycle> cycles=new ArrayList<Cycle>();
    volatile Cycle active;
    volatile int request;
    volatile Throwable failure;
    int phase,ticks,wait;
    boolean done,closingFailure;
    Object mc;
    final String jvm=java.lang.management.ManagementFactory.getRuntimeMXBean().getName()+"/"+UUID.randomUUID();
    static final class Fixture {Object world,host,loader,ticket,chunk;String label;File persisted;}
    static final class Cycle {
        int number;volatile int serverTicks,setupAt;volatile Object server,player;volatile Thread thread;volatile boolean stopped;
        String savePath;final List<Fixture> fixtures=new ArrayList<Fixture>();
        final List<Object> orphanHosts=new ArrayList<Object>();final Set<Integer> high=new HashSet<Integer>(),low=new HashSet<Integer>();
    }
    TicketRestartQa(Mui2ClientQa owner){q=owner;q.receipt.addProperty("mode","ticket-restart");q.receipt.addProperty("jvmIdentity",jvm);q.receipt.add("ticketEvents",events);q.receipt.addProperty("fixtureScope","Real registered GT BaseMetaTileEntity and native GTSR/Forge ticket APIs; no formed 4480-block loom or journal-owner restoration claim");q.receipt.addProperty("inputType","Synthetic in-process world launch/unload and production ticket API; no UI suite, physical input, fake world, Unsafe or manual unload event");}
    static Object call(Object target,String name,Object...args)throws Exception{return Mui2ClientQa.call(target,name,args);}
    static Object field(Object target,String name)throws Exception{return Mui2ClientQa.field(target,name);}
    static Class<?> type(String name)throws Exception{return Mui2ClientQa.type(name);}
    int n(Object value){return ((Number)value).intValue();}
    void check(String name,boolean pass,String detail){q.check("ticket."+name,pass,detail);}
    void observe(String name,boolean pass,String detail){q.record("ticket."+name,pass,detail);if(!pass&&failure==null)failure=new AssertionError(name+": "+detail);}
    void error(Throwable error){if(failure==null)failure=error;error.printStackTrace();write(false);}
    void event(String name,JsonObject data){data.addProperty("event",name);data.addProperty("cycle",active==null?0:active.number);data.addProperty("thread",Thread.currentThread().getName());events.add(data);System.out.println("TICKET_RESTART_QA "+data);write(false);}
    Map<?,?> loadersMap()throws Exception{return (Map<?,?>)field(type(MANAGER),"REGISTERED");}
    Set<?> loaders()throws Exception{return (Set<?>)field(type(LOADER),"LOADERS");}
    Map<?,?> tickets()throws Exception{return (Map<?,?>)field(type(FORGE),"tickets");}
    int dim(Object world)throws Exception{return n(field(field(world,"provider|field_73011_w"),"dimensionId|field_76574_g"));}
    int worldRegistered(Object world)throws Exception{int count=0;for(Object owner:loadersMap().keySet())if(call(owner,"getWorldObj|func_145831_w")==world)count++;return count;}
    int worldLoaders(Object world)throws Exception{int count=0;for(Object loader:loaders()){Object owner=field(loader,"owner");if(owner!=null&&call(owner,"getWorldObj|func_145831_w")==world)count++;}return count;}
    boolean coverage(Fixture fixture)throws Exception{return fixture.ticket!=null&&((Collection<?>)call(fixture.ticket,"getChunkList")).contains(fixture.chunk)&&(Boolean)call(call(type(FORGE),"getPersistentChunksFor",fixture.world),"containsEntry",fixture.chunk,fixture.ticket);}
    boolean localEmpty(Fixture f)throws Exception{return field(f.loader,"owner")==null&&field(f.loader,"nativeTicket")==null&&((Set<?>)field(f.loader,"current")).isEmpty()&&((Set<?>)field(f.loader,"needed")).isEmpty()&&field(f.loader,"activeChunk")==null&&field(f.loader,"pendingLoad")==null&&!loaders().contains(f.loader);}
    JsonObject snapshot(Cycle c)throws Exception{JsonObject out=new JsonObject();out.addProperty("serverIdentity",System.identityHashCode(c.server));out.addProperty("running",(Boolean)call(c.server,"isServerRunning|func_71278_l"));out.addProperty("serverTicks",c.serverTicks);out.addProperty("serverThreadId",c.thread==null?-1:c.thread.getId());out.addProperty("nativeWorldTables",tickets().size());out.addProperty("localLoaders",loaders().size());out.addProperty("registered",loadersMap().size());out.addProperty("dimensionWorlds",((Map<?,?>)field(type(DIM),"worlds")).size());out.addProperty("worldTickTimes",((Map<?,?>)field(c.server,"worldTickTimes")).size());return out;}
    void validateTiming(Cycle c)throws Exception{Map<?,?> timing=(Map<?,?>)field(c.server,"worldTickTimes");for(Object id:(Object[])call(type(DIM),"getIDs")){Object value=timing.get(id);if(!(value instanceof long[])||((long[])value).length<100)throw new AssertionError("Loaded dimension lacks real server timing array: "+id);}}
    Object placeHost(Object world,Object player,int x)throws Exception{
        int y=70,z=8;call(call(world,"getChunkProvider|func_72863_F"),"loadChunk|func_73158_c",x>>4,z>>4);
        Object host=call(world,"getTileEntity|func_147438_o",x,y,z);
        if(host==null){Object prototype=null;for(Object candidate:(Object[])field(type("gregtech.api.GregTechAPI"),"METATILEENTITIES"))if(candidate!=null&&candidate.getClass().getName().equals("com.miaokatze.gtsr.common.machine.tcds.MTEThermoChemicalDenseSteamGenerator")){prototype=candidate;break;}
            check("registered-GT-prototype",prototype!=null,"Real GT registry, not a fake TileEntity");Object stack=call(prototype,"getStackForm",1L);check("native-GT-placement",(Boolean)call(call(stack,"getItem|func_77973_b"),"placeBlockAt",stack,player,world,x,y,z,1,.5f,.5f,.5f,0),"Actual GT ItemMachines.placeBlockAt");host=call(world,"getTileEntity|func_147438_o",x,y,z);
        }
        check("actual-valid-BaseMetaTile",type("gregtech.api.metatileentity.BaseMetaTileEntity").isInstance(host)&&!(Boolean)call(host,"isInvalid|func_145837_r")&&call(host,"getWorldObj|func_145831_w")==world,"Real live host in dimension "+dim(world));return host;
    }
    Fixture ensure(Cycle c,Object world,int x,String label)throws Exception{
        Fixture f=new Fixture();f.label=label;f.world=world;f.persisted=new File((File)call(world,"getChunkSaveLocation"),"forcedchunks.dat");f.host=placeHost(world,c.player,x);f.chunk=type("net.minecraft.world.ChunkCoordIntPair").getConstructor(int.class,int.class).newInstance(x>>4,8>>4);f.loader=type(LOADER).newInstance();
        check(label+".quota-positive",n(call(f.loader,"budget"))>0,"Administrator quota is observed, never changed");check(label+".production-ensure",(Boolean)call(f.loader,"ensureAllStructureChunks",world,f.host,Collections.singletonList(f.chunk)),"Real CriticalChunkLoading.ensureAllStructureChunks");f.ticket=field(f.loader,"nativeTicket");
        check(label+".native-coverage",coverage(f),"Actual native ticket requestedChunks and Forge persistent chunks contain this ticket");check(label+".actual-registration",loaders().contains(f.loader)&&loadersMap().get(f.host)==f.ticket,"Actual production LOADERS and REGISTERED entries");c.fixtures.add(f);return f;
    }
    void setupOverworld(Cycle c)throws Exception{
        q.receipt.addProperty("productionCriticalCodeSource",type(LOADER).getProtectionDomain().getCodeSource().getLocation().toString());q.receipt.addProperty("productionManagerCodeSource",type(MANAGER).getProtectionDomain().getCodeSource().getLocation().toString());
        check("GT-chunkloaders-enabled",Boolean.TRUE.equals(field(type("gregtech.api.enums.GTValues"),"enableChunkloaders")),"Existing owned pack configuration; no bypass");Object world=call(c.server,"worldServerForDimension|func_71218_a",0);c.savePath=((File)call(call(world,"getSaveHandler|func_72860_G"),"getWorldDirectory|func_75765_b")).getCanonicalPath();
        if(c.number==2){check("same-owned-save",cycles.get(0).savePath.equals(c.savePath),c.savePath);check("new-server-thread",cycles.get(0).thread!=c.thread,"Two distinct real server Thread instances in the same JVM");check("new-server-instance",cycles.get(0).server!=c.server,"Same JVM "+jvm+", distinct real IntegratedServer identities");}
        ensure(c,world,8,"overworld");Object orphan=placeHost(world,c.player,40);Object orphanChunk=type("net.minecraft.world.ChunkCoordIntPair").getConstructor(int.class,int.class).newInstance(40>>4,0);check("manager-only-native-ticket",(Boolean)call(type(MANAGER),"requestChunkLoad",orphan,orphanChunk),"Production manager API; real native ticket, no LOADERS insertion");c.orphanHosts.add(orphan);
        call(call(c.server,"getConfigurationManager|func_71203_ab"),"func_152605_a|addOp",call(c.player,"getGameProfile|func_146103_bH"));check("owned-profile-command-permission",(Boolean)call(c.player,"canCommandSenderUseCommand|func_70003_b",2,"gtsrtest"),"Only owned offline Mui2Audit profile");call(call(c.server,"getCommandManager|func_71187_D"),"executeCommand|func_71556_a",c.player,"gtsrtest enter");event("overworld-native-fixtures",snapshot(c));
    }
    void setupLab(Cycle c)throws Exception{
        Object world=field(c.player,"worldObj|field_70170_p");check("real-TestLab-dimension",dim(world)==1048577,"Actual normal command transfer, no fake world");Fixture f=ensure(c,world,8,"testlab");
        if(c.number==2){Object mod=field(type(MANAGER),"mod");Object foreign=call(type(FORGE),"requestTicket",mod,world,Enum.valueOf((Class)type("net.minecraftforge.common.ForgeChunkManager$Type"),"NORMAL"));check("second-server.foreign-native-ticket",foreign!=null,"Actual independent native ticket");call(type(FORGE),"forceChunk",foreign,f.chunk);
            call(f.loader,"release");check("second-server.strict-active-release",localEmpty(f)&&!loadersMap().containsKey(f.host)&&((Collection<?>)call(f.ticket,"getChunkList")).isEmpty(),"Real public release removed local refs and native self chunk claim");check("second-server.foreign-preserved",((Collection<?>)call(foreign,"getChunkList")).contains(f.chunk)&&(Boolean)call(call(type(FORGE),"getPersistentChunksFor",world),"containsEntry",f.chunk,foreign),"Other native ticket is untouched by Critical public release");call(type(FORGE),"releaseTicket",foreign);
            check("second-server.reensure-after-release",(Boolean)call(f.loader,"ensureAllStructureChunks",world,f.host,Collections.singletonList(f.chunk)),"No persistent shutdown flag");f.ticket=field(f.loader,"nativeTicket");check("second-server.renewed-native-coverage",coverage(f),"Renewed production native coverage remains until second real shutdown");
        }
        c.setupAt=c.serverTicks;event("all-native-fixtures-ready",snapshot(c));
    }
    @SubscribeEvent public void serverTick(TickEvent.ServerTickEvent e){if(done||e.phase!=TickEvent.Phase.END)return;try{Cycle c=active;if(c==null)return;Object server=FMLCommonHandler.instance().getMinecraftServerInstance();if(c.server==null){c.server=server;c.thread=Thread.currentThread();}if(c.server!=server)throw new AssertionError("Unexpected server identity");c.serverTicks++;validateTiming(c);List<?> players=(List<?>)field(call(server,"getConfigurationManager|func_71203_ab"),"playerEntityList|field_72404_b");if(!players.isEmpty())c.player=players.get(0);if(c.player==null)return;
        if(request==1){setupOverworld(c);request=0;}else if(request==2){setupLab(c);request=0;}else if(request==3){for(Fixture f:c.fixtures)check(f.label+".pre-stop-native-coverage",coverage(f)&&loaders().contains(f.loader)&&loadersMap().get(f.host)==f.ticket,"Held through live server tick before loadWorld(null)");check("pre-stop-local-counts-positive",loaders().size()>=2&&loadersMap().size()>=3,"Two real loaders plus one real manager-only ticket");event("pre-stop-active-ticket-proof",snapshot(c));request=0;}
    }catch(Throwable t){request=0;error(t);}}
    public void stopping(){try{Cycle c=active;if(c!=null)event("FMLServerStopping",snapshot(c));}catch(Throwable t){error(t);}}
    public void stopped(){try{Cycle c=active;if(c==null)return;JsonObject data=snapshot(c);observe("stopped.dimension-and-timing-maps-empty",((Map<?,?>)field(type(DIM),"worlds")).isEmpty()&&((Object[])call(type(DIM),"getIDs")).length==0&&((Map<?,?>)field(c.server,"worldTickTimes")).isEmpty(),"After actual stopServer unlink loop");observe("stopped.local-holders-empty",loaders().isEmpty()&&loadersMap().isEmpty(),"Actual production holders");observe("stopped.native-tickets-empty",tickets().isEmpty(),"Actual Forge global native tickets");c.stopped=true;event("FMLServerStopped",data);}catch(Throwable t){error(t);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void unloadHigh(WorldEvent.Unload e){observeUnload(e,true);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void unloadLow(WorldEvent.Unload e){observeUnload(e,false);}
    void observeUnload(WorldEvent.Unload e,boolean highest){try{Object world=field(e,"world");if(!type("net.minecraft.world.WorldServer").isInstance(world))return;Cycle c=active;if(c==null||c.server==null)return;int id=dim(world);JsonObject data=snapshot(c);data.addProperty("dimension",id);data.addProperty("worldLocalLoaders",worldLoaders(world));data.addProperty("worldRegistered",worldRegistered(world));data.addProperty("nativeWorldEntryPresent",tickets().containsKey(world));
        if(!(Boolean)call(c.server,"isServerRunning|func_71278_l")){
            if(highest){c.high.add(id);observe("unload-high.native-cleared-first."+id,tickets().isEmpty(),"ForgeInternalHandler registered before this HIGHEST observer");for(Fixture f:c.fixtures)if(f.world==world)observe("unload-high.local-reference-still-present."+id,field(f.loader,"nativeTicket")!=null&&loaders().contains(f.loader),"Before production NORMAL Supervisor");}
            else{c.low.add(id);observe("unload-low.world-local-empty."+id,worldLoaders(world)==0&&worldRegistered(world)==0,"After production NORMAL Supervisor");for(Fixture f:c.fixtures)if(f.world==world)observe("unload-low.loader-fields-empty."+id,localEmpty(f),"owner/native/current/needed/active/pending/LOADERS cleared");}
        }
        event(highest?"WorldUnload-HIGHEST-after-Forge":"WorldUnload-LOWEST-after-Supervisor",data);
    }catch(Throwable t){error(t);}}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void worldLoad(WorldEvent.Load e){try{Object world=field(e,"world");if(!type("net.minecraft.world.WorldServer").isInstance(world))return;JsonObject data=new JsonObject();data.addProperty("dimension",dim(world));data.addProperty("nativeWorldTablePresent",tickets().containsKey(world));File file=new File((File)call(world,"getChunkSaveLocation"),"forcedchunks.dat");data.add("persistedNativeTickets",persisted(file));Object callback=((Map<?,?>)field(type(FORGE),"callbacks")).get("gtsr");data.addProperty("gtsrCallbackClass",callback==null?"null":callback.getClass().getName());if(active!=null&&active.number==2&&(dim(world)==0||dim(world)==1048577))check("second-server.native-persisted-load-path."+dim(world),persisted(file).get("gtsrTickets").getAsInt()>0&&callback!=null,"Same save native forcedchunks.dat feeds the registered production callback; minimal BaseTile fixtures may be filtered, not a formed loom claim");event("WorldLoad-LOWEST-after-native-load",data);}catch(Throwable t){error(t);}}
    JsonObject persisted(File file)throws Exception {JsonObject data=new JsonObject();data.addProperty("path",file.getCanonicalPath());data.addProperty("bytes",file.isFile()?file.length():0);int count=0;if(file.isFile()){Object tag=call(type("net.minecraft.nbt.CompressedStreamTools"),"read|func_74797_a",file),holders=call(tag,"getTagList|func_150295_c","TicketList",10);for(int i=0;i<n(call(holders,"tagCount|func_74745_c"));i++){Object holder=call(holders,"getCompoundTagAt|func_150305_b",i);if("gtsr".equals(call(holder,"getString|func_74779_i","Owner")))count+=n(call(call(holder,"getTagList|func_150295_c","Tickets",10),"tagCount|func_74745_c"));}}data.addProperty("gtsrTickets",count);return data;}
    void quitToMainMenu()throws Exception{
        if(field(mc,"theWorld|field_71441_e")==null)return;
        Object menu=type("net.minecraft.client.gui.GuiIngameMenu").newInstance();
        call(mc,"displayGuiScreen|func_147108_a",menu);
        Object quit=null;
        for(Object button:(List<?>)field(menu,"buttonList|field_146292_n"))if(n(field(button,"id|field_146127_k"))==1){quit=button;break;}
        check("native-quit-button-present",quit!=null,"Real GuiIngameMenu return-to-menu button");
        call(menu,"actionPerformed|func_146284_a",quit);
        Object screen=field(mc,"currentScreen|field_71462_r");
        check("native-quit-returned-to-menu",field(mc,"theWorld|field_71441_e")==null&&screen!=null,"Native quit action performs disconnect + loadWorld(null) + main menu; actual screen="+(screen==null?"null":screen.getClass().getName()));
    }
    @SubscribeEvent public void clientTick(TickEvent.ClientTickEvent e){if(done||e.phase!=TickEvent.Phase.END||++ticks<40)return;try{mc=call(type("net.minecraft.client.Minecraft"),"getMinecraft|func_71410_x");if(ticks%100==0){Runtime r=Runtime.getRuntime();JsonObject h=new JsonObject();h.addProperty("elapsedMillis",System.currentTimeMillis()-q.started);h.addProperty("phase",phase);h.addProperty("maxBytes",r.maxMemory());h.addProperty("committedBytes",r.totalMemory());h.addProperty("usedBytes",r.totalMemory()-r.freeMemory());q.heapSamples.add(h);q.receipt.add("heapSamples",q.heapSamples);}
        if(System.currentTimeMillis()-q.started>Long.getLong("gtsr.uiqa.timeoutMillis",900000L))throw new AssertionError("Ticket lifecycle timeout phase="+phase);
        if(failure!=null&&!closingFailure){closingFailure=true;quitToMainMenu();phase=90;return;}
        if(phase==90){if(active!=null&&active.thread!=null&&active.thread.isAlive())return;finish();return;}
        Object player=field(mc,"thePlayer|field_71439_g");
        if(phase==0||phase==10){Cycle c=new Cycle();c.number=cycles.size()+1;cycles.add(c);active=c;Class<?> gt=type("net.minecraft.world.WorldSettings$GameType");Object settings=type("net.minecraft.world.WorldSettings").getConstructor(long.class,gt,boolean.class,boolean.class,type("net.minecraft.world.WorldType")).newInstance(61312138L,Enum.valueOf((Class)gt,"CREATIVE"),false,false,field(type("net.minecraft.world.WorldType"),"FLAT|field_77138_c"));call(settings,"enableCommands|func_77159_a");call(mc,"launchIntegratedServer|func_71371_a",System.getProperty("gtsr.uiqa.world"),"Owned native ticket restart QA",settings);phase=1;return;}
        Cycle c=active;
        if(phase==1){if(player==null||c.player==null||n(field(player,"ticksExisted|field_70173_aa"))<60)return;request=1;phase=2;return;}
        if(phase==2){if(request!=0)return;if(n(field(player,"dimension|field_71093_bK"))!=1048577||dim(field(c.player,"worldObj|field_70170_p"))!=1048577)return;if(++wait<20)return;wait=0;request=2;phase=3;return;}
        if(phase==3){if(request!=0)return;if(c.serverTicks<c.setupAt+120)return;check("server-"+c.number+".at-least-100-live-ticks",c.serverTicks-c.setupAt>=100,"Actual server END ticks since native fixture setup="+(c.serverTicks-c.setupAt));request=3;phase=4;return;}
        if(phase==4){if(request!=0)return;quitToMainMenu();phase=5;return;}
        if(phase==5){if(!c.stopped||c.thread.isAlive())return;check("server-"+c.number+".thread-fully-stopped",(Boolean)call(c.server,"isServerStopped|func_71241_aa")&&!c.thread.isAlive(),"Actual old server Thread.isAlive=false before reopening");check("server-"+c.number+".unload-event-pairs",c.high.contains(0)&&c.high.contains(1048577)&&c.high.equals(c.low),"Real Forge highest/lowest world event dimensions "+c.high);check("server-"+c.number+".post-thread-empty",loaders().isEmpty()&&loadersMap().isEmpty()&&((Map<?,?>)field(type(DIM),"worlds")).isEmpty(),"After server thread termination, not just process exit");for(Fixture f:c.fixtures){JsonObject data=persisted(f.persisted);data.addProperty("label",f.label);event("after-stop-native-persisted-ticket-file",data);check("server-"+c.number+".native-save-persisted."+f.label,data.get("gtsrTickets").getAsInt()>0,"Native Forge save preserved real GTSR ticket data before unload");}if(c.number==1){phase=10;return;}finish();}
    }catch(Throwable t){error(t);}}
    void write(boolean complete){try{q.receipt.add("checks",q.checks);q.receipt.addProperty("status",complete&&failure==null?"PASS":complete?"FAIL":"RUNNING");q.receipt.addProperty("phase",phase);q.receipt.addProperty("elapsedMillis",System.currentTimeMillis()-q.started);if(failure!=null)q.receipt.addProperty("failure",failure.toString());Files.write(Paths.get(System.getProperty("gtsr.uiqa.receipt")),new GsonBuilder().setPrettyPrinting().create().toJson(q.receipt).getBytes(StandardCharsets.UTF_8));}catch(Exception ex){ex.printStackTrace();}}
    void finish(){if(done)return;done=true;write(true);try{call(mc,"shutdown|func_71400_g");}catch(Exception e){e.printStackTrace();}}
}
