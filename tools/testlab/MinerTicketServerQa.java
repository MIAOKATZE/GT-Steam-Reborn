package audit;

import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;
import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.world.WorldEvent;

/** Owned dedicated-server companion. Reflection accepts the runtime MCP/SRG names. */
@Mod(modid="gtsrminerticketqa",name="GTSR owned miner ticket QA",version="1",dependencies="required-after:gtsr;after:gregtech")
public final class MinerTicketServerQa {
    static Class<?> type(String n)throws Exception{return Class.forName(n);}
    static Field fld(Object o,String n)throws Exception {for(Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(n);}
    static Object get(Object o,String n)throws Exception{return fld(o,n).get(o instanceof Class?null:o);}
    static void set(Object o,String n,Object v)throws Exception{fld(o,n).set(o instanceof Class?null:o,v);}
    static Object call(Object o,String names,Object...args)throws Exception {
        Class<?> root=o instanceof Class?(Class<?>)o:o.getClass();
        for(int pass=0;pass<2;pass++)for(Class<?> c=root;c!=null;c=pass==0?c.getSuperclass():null)for(Method m:pass==0?c.getDeclaredMethods():root.getMethods())for(String n:names.split("\\|")) {
            if(!m.getName().equals(n)||m.getParameterTypes().length!=args.length)continue;
            boolean fits=true;Class<?>[] ts=m.getParameterTypes();for(int i=0;i<ts.length;i++)if(args[i]!=null&&!ts[i].isInstance(args[i])&&!(ts[i].isPrimitive()&&(args[i] instanceof Number||args[i] instanceof Boolean)))fits=false;
            if(!fits)continue;m.setAccessible(true);try{return m.invoke(o instanceof Class?null:o,args);}catch(InvocationTargetException e){throw new RuntimeException(m.toString(),e.getCause());}
        }throw new NoSuchMethodException(names);
    }
    int ticks,phase,tier,nodeX;Object world,host,node,hub,qaTicket,mixedCritical,mixedWorker,mixedLoader;boolean finished,mixed;String mode="core";
    Object mixedCriticalTicket,mixedWorkerTicket;boolean mixedCriticalStable=true,mixedWorkerStable=true,mixedWorldStable=true;
    final JsonArray rows=new JsonArray();final JsonObject report=new JsonObject();
    @Mod.EventHandler public void init(FMLInitializationEvent e)throws Exception {
        Class<?> callback=type("net.minecraftforge.common.ForgeChunkManager$LoadingCallback");
        Object handler=Proxy.newProxyInstance(callback.getClassLoader(),new Class<?>[]{callback},(p,m,a)->null);
        call(type("net.minecraftforge.common.ForgeChunkManager"),"setForcedChunkLoadingCallback",this,handler);
        FMLCommonHandler.instance().bus().register(this);
        call(get(type("net.minecraftforge.common.MinecraftForge"),"EVENT_BUS"),"register",this);
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||finished)return;
        if(++ticks<100)return;
        try {
            if(phase==0){
                Path modeFile=Paths.get("miner-ticket-mode.txt");if(Files.isRegularFile(modeFile))mode=new String(Files.readAllBytes(modeFile),StandardCharsets.UTF_8).trim();report.addProperty("qaMode",mode);
                if(!Boolean.TRUE.equals(call(type("com.miaokatze.gtsr.common.dimension.testlab.TestLabDimension"),"enabled")))return;
                world=call(type("com.miaokatze.gtsr.common.dimension.testlab.TestLabDimension"),"world");
                qaTicket=call(type("net.minecraftforge.common.ForgeChunkManager"),"requestTicket",this,world,get(type("net.minecraftforge.common.ForgeChunkManager$Type"),"NORMAL"));
                if(qaTicket==null)throw new AssertionError("owned QA anchor ticket unavailable");
                call(type("net.minecraftforge.common.ForgeChunkManager"),"forceChunk",qaTicket,chunk(20000,20000));
                int hubId=((Number)get(get(type("com.miaokatze.gtsr.common.api.enums.MetaTileEntityID"),"SINGULARITY_DRILLING_HUB"),"ID")).intValue();
                hub=call(type("com.miaokatze.gtsr.common.dimension.testlab.TestLabFixtures"),"machine",world,16384,65,16384,hubId);
                if(mode.equals("mixed-stable")){setupMixed();phase=3;ticks=100;return;}
                geometry(hubId);
                phase=1;
            }
            if(phase==1){
                int id=((Number)get(get(type("com.miaokatze.gtsr.common.api.enums.MetaTileEntityID"),"SINGULARITY_MINER_NODE"),"ID")).intValue();
                nodeX=20495+tier*1024;
                host=call(type("com.miaokatze.gtsr.common.dimension.testlab.TestLabFixtures"),"machine",world,nodeX,65,16431,id);
                node=call(host,"getMetaTileEntity");set(node,"mMinerTier",tier);set(node,"mBound",true);set(node,"mHubX",16384);set(node,"mHubY",65);set(node,"mHubZ",16384);set(node,"mHubDim",((Number)call(type("com.miaokatze.gtsr.common.dimension.testlab.TestLabDimension"),"id")).intValue());set(node,"mHubType","singularity");
                call(host,"enableWorking");call(node,"onPostTick",host,100L);
                record();call(node,"releaseAllChunks");call(world,"setBlockToAir|func_147468_f",nodeX,65,16431);
                if(++tier<=4){ticks=100;return;}else {
                    try{type("com.miaokatze.gtsr.common.machine.base.RemoteWorkerChunkTickets");}catch(ClassNotFoundException old){finish(null);return;}
                    setupMixed();phase=3;ticks=100;return;
                }
            }
            if(phase==3){
                mixedWorldStable&=call(type("net.minecraftforge.common.DimensionManager"),"getWorld",call(type("com.miaokatze.gtsr.common.dimension.testlab.TestLabDimension"),"id"))==world;
                mixedCriticalStable&=((Map<?,?>)get(type("com.miaokatze.gtsr.common.critical.CriticalChunkTicketManager"),"REGISTERED")).get(mixedCritical)==mixedCriticalTicket&&nativeCoverage(mixedCriticalTicket,chunk(28000>>4,16384>>4));
                List<?> active=(List<?>)((Map<?,?>)get(type("com.miaokatze.gtsr.common.machine.base.RemoteWorkerChunkTickets"),"NODES")).get(mixedWorker);
                mixedWorkerStable&=active!=null&&!active.isEmpty()&&active.get(0)==mixedWorkerTicket&&nativeCoverage(mixedWorkerTicket,chunk(28127>>4,16431>>4));
            }
            if(phase==3&&ticks>=220){
                report.addProperty("mixedHeldTicks",120);
                boolean criticalHeld=((Map<?,?>)get(type("com.miaokatze.gtsr.common.critical.CriticalChunkTicketManager"),"REGISTERED")).containsKey(mixedCritical),workerHeld=((Map<?,?>)get(type("com.miaokatze.gtsr.common.machine.base.RemoteWorkerChunkTickets"),"NODES")).containsKey(mixedWorker);
                report.addProperty("mixedCriticalHeldBeforeStop",criticalHeld);report.addProperty("mixedWorkerHeldBeforeStop",workerHeld);report.addProperty("mixedBothTicketsHeldBeforeStop",criticalHeld&&workerHeld);
                report.addProperty("mixedCriticalNativeIdentityStable120Ticks",mixedCriticalStable);report.addProperty("mixedWorkerNativeIdentityStable120Ticks",mixedWorkerStable);
                report.addProperty("mixedWorldIdentityLive120Ticks",mixedWorldStable);
                finish(null);
            }
        }catch(Throwable x){finish(x);}
    }
    static Object chunk(int x,int z)throws Exception{return type("net.minecraft.world.ChunkCoordIntPair").getConstructor(int.class,int.class).newInstance(x,z);}
    boolean nativeCoverage(Object ticket,Object point)throws Exception{return ticket!=null&&((Collection<?>)call(ticket,"getChunkList")).contains(point)&&Boolean.TRUE.equals(call(call(type("net.minecraftforge.common.ForgeChunkManager"),"getPersistentChunksFor",world),"containsEntry",point,ticket));}
    void setupMixed()throws Exception {
        int criticalId=((Number)get(get(type("com.miaokatze.gtsr.common.api.enums.MetaTileEntityID"),mode.equals("mixed-stable")?"TCDS_CONTROLLER":"CRITICAL_LOOM"),"ID")).intValue();
        mixedCritical=call(type("com.miaokatze.gtsr.common.dimension.testlab.TestLabFixtures"),"machine",world,28000,65,16384,criticalId);
        mixedLoader=type("com.miaokatze.gtsr.common.critical.CriticalChunkLoading").newInstance();
        if(!Boolean.TRUE.equals(call(mixedLoader,"ensureAllStructureChunks",world,mixedCritical,Collections.singletonList(chunk(28000>>4,16384>>4)))))throw new AssertionError("mixed native critical ticket ensure failed");
        int workerId=((Number)get(get(type("com.miaokatze.gtsr.common.api.enums.MetaTileEntityID"),"SINGULARITY_MINER_NODE"),"ID")).intValue();
        mixedWorker=call(type("com.miaokatze.gtsr.common.dimension.testlab.TestLabFixtures"),"machine",world,28127,65,16431,workerId);Object worker=call(mixedWorker,"getMetaTileEntity");
        set(worker,"mBound",true);set(worker,"mHubX",16384);set(worker,"mHubY",65);set(worker,"mHubZ",16384);set(worker,"mHubDim",((Number)call(type("com.miaokatze.gtsr.common.dimension.testlab.TestLabDimension"),"id")).intValue());set(worker,"mHubType","singularity");call(mixedWorker,"enableWorking");call(worker,"onPostTick",mixedWorker,100L);mixed=true;
        mixedCriticalTicket=get(mixedLoader,"nativeTicket");mixedWorkerTicket=((List<?>)((Map<?,?>)get(type("com.miaokatze.gtsr.common.machine.base.RemoteWorkerChunkTickets"),"NODES")).get(mixedWorker)).get(0);
        report.addProperty("mixedScope",mode.equals("mixed-stable")?"Registered stable TCDS BaseMetaTile host for production CriticalChunkLoading native ticket, matching v138 accepted lifecycle seam, plus real bound worker; no formed critical loom or journal restoration claim":"Registered critical_loom host with external production CriticalChunkLoading native owner ticket plus real bound worker; no formed critical structure or journal restoration claim");
    }
    void writeReport()throws Exception {Files.write(Paths.get("miner-ticket-server-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void unload(WorldEvent.Unload e){
        if(!mixed||e.world!=world)return;try{
            Class<?> worker=type("com.miaokatze.gtsr.common.machine.base.RemoteWorkerChunkTickets"),critical=type("com.miaokatze.gtsr.common.critical.CriticalChunkTicketManager");
            boolean clear=((Map<?,?>)get(worker,"NODES")).isEmpty()&&((Map<?,?>)get(worker,"HUBS")).isEmpty()&&((Map<?,?>)get(worker,"BINDINGS")).isEmpty()&&((Map<?,?>)get(critical,"REGISTERED")).isEmpty()&&!((Map<?,?>)get(type("net.minecraftforge.common.ForgeChunkManager"),"tickets")).containsKey(world);
            report.addProperty("mixedUnloadClearsNativeAndLocal",clear);if(!clear)report.addProperty("status","FAIL");writeReport();System.out.println("[GTSR-MINER-QA] MIXED_UNLOAD clear="+clear);
        }catch(Throwable failure){report.addProperty("status","FAIL");report.addProperty("mixedUnloadFailure",failure.toString());try{writeReport();}catch(Exception ignored){}}
    }
    @Mod.EventHandler public void stopped(FMLServerStoppedEvent e)throws Exception {
        if(!mixed)return;Class<?> worker=type("com.miaokatze.gtsr.common.machine.base.RemoteWorkerChunkTickets");
        boolean clear=((Map<?,?>)get(worker,"NODES")).isEmpty()&&((Map<?,?>)get(worker,"HUBS")).isEmpty()&&((Map<?,?>)get(worker,"BINDINGS")).isEmpty()&&((Map<?,?>)get(type("com.miaokatze.gtsr.common.critical.CriticalChunkTicketManager"),"REGISTERED")).isEmpty()&&((Collection<?>)get(type("com.miaokatze.gtsr.common.critical.CriticalChunkLoading"),"LOADERS")).isEmpty()&&((Map<?,?>)get(type("net.minecraftforge.common.ForgeChunkManager"),"tickets")).isEmpty();
        report.addProperty("mixedServerStoppedAllMapsClear",clear);if(!clear||!report.has("mixedUnloadClearsNativeAndLocal"))report.addProperty("status","FAIL");writeReport();System.out.println("[GTSR-MINER-QA] MIXED_STOPPED clear="+clear);
    }
    Set<Object> canonicalFootprint(Object machine,int x,int y,int z)throws Exception {
        final Set<Object> chunks=new HashSet<Object>();chunks.add(chunk(x>>4,z>>4));Class<?> walker=type("com.gtnewhorizon.structurelib.structure.IStructureWalker");
        Object visitor=Proxy.newProxyInstance(walker.getClassLoader(),new Class<?>[]{walker},(p,m,a)->{if(m.getName().equals("visit")||m.getName().equals("blockNotLoaded")){chunks.add(chunk(((Number)a[2]).intValue()>>4,((Number)a[4]).intValue()>>4));return true;}return null;});
        call(call(machine,"getStructureDefinition"),"iterate","main",world,call(machine,"getExtendedFacing"),x,y,z,9,10,0,visitor);return chunks;
    }
    void geometry(int hubId)throws Exception {
        Object mainMachine=call(hub,"getMetaTileEntity");
        try{mainMachine.getClass().getMethod("getChunkLoadingFootprint");}catch(NoSuchMethodException old){report.addProperty("hubGeometryStatus","NOT_AVAILABLE_BASELINE");return;}
        int cases=0;boolean all=true;Set<String> directions=new HashSet<String>(),flips=new HashSet<String>();
        for(int[] pos:new int[][]{{15,15},{-1,-17},{-16,-16}}){
            Object tile=call(type("com.miaokatze.gtsr.common.dimension.testlab.TestLabFixtures"),"machine",world,pos[0],65,pos[1],hubId),machine=call(tile,"getMetaTileEntity"),limits=call(machine,"getAlignmentLimits");
            for(Object facing:type("com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing").getEnumConstants()){
                if(!Boolean.TRUE.equals(call(limits,"isNewExtendedFacingValid",facing)))continue;
                call(machine,"setExtendedFacing",facing);Set<Object> actual=new HashSet<Object>((Collection<?>)call(machine,"getChunkLoadingFootprint"));
                all&=actual.equals(canonicalFootprint(machine,pos[0],65,pos[1]));cases++;directions.add(call(facing,"getDirection").toString());flips.add(call(facing,"getFlip").toString());
            }
            call(world,"setBlockToAir|func_147468_f",pos[0],65,pos[1]);
        }
        report.addProperty("hubGeometryStatus",all&&cases>0?"PASS":"FAIL");report.addProperty("hubGeometryCases",cases);report.addProperty("hubGeometryAllCanonicalCells",all);report.addProperty("hubGeometryDirections",directions.toString());report.addProperty("hubGeometryFlips",flips.toString());
    }
    Set<Object> ownerChunks(int x,int y,int z)throws Exception {
        Object forces=call(type("net.minecraftforge.common.ForgeChunkManager"),"getPersistentChunksFor",world);Set<Object> out=new HashSet<Object>();
        for(Object entry:(Collection<?>)call(forces,"entries")){
            Map.Entry<?,?> pair=(Map.Entry<?,?>)entry;Object ticket=pair.getValue();String mod=(String)call(ticket,"getModId");if(!mod.equals("gtsr")&&!mod.equals("gregtech"))continue;
            Object data=call(ticket,"getModData");
            if(((Number)call(data,"getInteger|func_74762_e","OwnerX")).intValue()==x&&((Number)call(data,"getInteger|func_74762_e","OwnerY")).intValue()==y&&((Number)call(data,"getInteger|func_74762_e","OwnerZ")).intValue()==z)out.add(pair.getKey());
        }return out;
    }
    void record()throws Exception {
        Object self=chunk(nodeX>>4,16431>>4),hc=chunk(16384>>4,16384>>4);Set<Object> own=ownerChunks(nodeX,65,16431),hubOwn=ownerChunks(16384,65,16384);
        List<Object> expected=new ArrayList<Object>();call(node,"collectWorkChunks",expected);
        JsonObject row=new JsonObject();row.addProperty("tier",tier);row.addProperty("selfForced",own.contains(self));row.addProperty("hubForced",hubOwn.contains(hc));row.addProperty("selfFlag",(Boolean)get(node,"mSelfChunkRequested"));row.addProperty("workFlag",(Boolean)get(node,"mWorkChunksRequested"));row.addProperty("footprintCount",own.size());row.addProperty("fullWorkFootprint",own.containsAll(expected));
        row.addProperty("fullHubFootprint",hubOwn.containsAll(canonicalFootprint(call(hub,"getMetaTileEntity"),16384,65,16384)));
        Object live=call(type("net.minecraftforge.common.DimensionManager"),"getWorld",call(type("com.miaokatze.gtsr.common.dimension.testlab.TestLabDimension"),"id"));row.addProperty("worldIdentityLive",live==world);
        call(host,"disableWorking");call(node,"onPostTick",host,101L);
        Set<Object> stopped=ownerChunks(nodeX,65,16431);row.addProperty("stopRetainsSelfOnly",stopped.size()==1&&stopped.contains(self));row.addProperty("stopRetainsHub",ownerChunks(16384,65,16384).contains(hc));
        call(node,"releaseAllChunks");row.addProperty("removalClearsOwnAndHub",ownerChunks(nodeX,65,16431).isEmpty()&&ownerChunks(16384,65,16384).isEmpty());
        row.addProperty("worldClass",world.getClass().getName());row.addProperty("hostClass",host.getClass().getName());rows.add(row);System.out.println("[GTSR-MINER-QA] ROW "+row);
    }
    void finish(Throwable failure){
        finished=true;try{
            if(qaTicket!=null)call(type("net.minecraftforge.common.ForgeChunkManager"),"releaseTicket",qaTicket);
            boolean pass=failure==null;for(JsonElement value:rows){JsonObject row=value.getAsJsonObject();for(String key:new String[]{"selfForced","hubForced","fullWorkFootprint","fullHubFootprint","worldIdentityLive","stopRetainsSelfOnly","stopRetainsHub","removalClearsOwnAndHub"})pass&=row.get(key).getAsBoolean();}if(report.has("hubGeometryStatus")&&report.get("hubGeometryStatus").getAsString().equals("FAIL"))pass=false;
            report.addProperty("status",pass?"PASS":"FAIL");report.add("tiers",rows);report.addProperty("fullServer",true);report.addProperty("scope","Registered real GT hosts; real server-thread explicit production onPostTick. QA mod owns one distant world anchor outside all fixtures; node/hub assertions count only gregtech/gtsr owner-coordinate tickets, excluding QA. No formed-hub mining/output or natural offline-progression claim.");if(failure!=null){report.addProperty("failure",failure.toString());failure.printStackTrace();}
            writeReport();
            System.out.println("[GTSR-MINER-QA] COMPLETE "+report);
        }catch(Exception ex){ex.printStackTrace();}
    }
}
