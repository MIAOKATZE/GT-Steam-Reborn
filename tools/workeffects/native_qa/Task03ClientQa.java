package audit;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Owned helper only: live StructureLib construction and ordinary native machine ticks, never a display proxy. */
public final class Task03ClientQa {
    static final String LAB="com.miaokatze.gtsr.common.dimension.testlab.",
        CLIENT="com.miaokatze.gtsr.client.machinevisual.MachineVisualClient",
        FX="com.miaokatze.gtsr.client.workeffects.";
    final Mui2ClientQa q;
    final Path out;
    final String[] machineIds=System.getProperty("gtsr.task03.machine","air_compressor").split(",");
    volatile String machineId=machineIds[0];
    volatile int caseIndex;
    final JsonArray serverSamples=new JsonArray(),frames=new JsonArray(),cases=new JsonArray();
    volatile Object task,world,host,machine;
    volatile Throwable serverFailure;
    volatile boolean startRequested,ready,productionSeen,stopRequested,stopped;
    volatile boolean integratedStopped;
    volatile int serverTicks,hx,hy,hz;
    volatile int obstacleRequest,obstacleAck,ox,oy,oz;
    volatile boolean obstaclePlaced,cameraRequest,cameraAck;
    volatile int offscreenCameraRequest,offscreenCameraAck;
    double savedCameraX,savedCameraY,savedCameraZ;
    float savedCameraYaw,savedCameraPitch;
    volatile double offscreenSectionDistance,offscreenControllerDistance;
    volatile double serverNativeHeat;
    int ticks,phase,phaseTick,shots;
    long firstSequence,offSequence,offCubes,firstRenderedCubes,firstFramesReceived;
    long inputFixtureWrites,outputItemsRemoved,outputFluidRemoved;
    long initialCloudCalls,initialGrateFaces;
    long offCloudCalls,offGrateFaces,blockedSequence,blockedCubes,blockedCloudCalls,residualCloudCalls,residualClientTicks;
    volatile double residualStartHeat,residualAfterActualStopHeat;
    volatile long residualActualStopMteTick,residualLatestMteTick;
    boolean obstructionDone,outletCaptured;
    boolean offscreenTestDone;
    long offscreenWorkSequence;
    long offscreenWaitStarted;
    JsonArray thermalWaitSamples=new JsonArray();
    Thread.UncaughtExceptionHandler ownedUncaughtHandler;
    int maxNativeClouds;
    double[] firstRenderedPose;
    double largestRenderedPoseDelta;
    long peakWaitStarted;
    double bestPeakMetric;
    String pendingShot;
    boolean finishing,done;
    boolean worldUnloadRequested;
    int cleanupTicks;
    Throwable failure;
    JsonObject completedCaseReport;
    JsonObject offscreenBefore;

    Task03ClientQa(Mui2ClientQa owner) {
        q=owner;out=Paths.get(System.getProperty("gtsr.uiqa.receipt")).getParent();
        Set<String> unique=new HashSet<String>();
        for(String id:machineIds)if(!unique.add(id)||!Arrays.asList("air_compressor","atmospheric_centrifuge","gear_steam_compressor","steam_fluid_drill",
            "crust_steam_borer","large_steam_furnace","large_coke_oven","reinforced_brick_blast_furnace",
            "siemens_martin_furnace","large_solar_overpressure_array","large_geothermal_steam_boiler").contains(id))
            throw new IllegalArgumentException("Unapproved or duplicate TASK03 machine "+id);
        q.receipt.add("nativeServerSamples",serverSamples);q.receipt.add("nativeRenderedFrames",frames);
        q.receipt.add("machineCases",cases);q.receipt.add("requestedMachines",new Gson().toJsonTree(machineIds));
        q.receipt.addProperty("scope","Owned TestLabDimension, live construction and hatches; ordinary GT world ticks; native subscription packets; actual world chunk+TESR render and full-frame screenshots. No manual phase, machine tick, advancement or heat assignment. Other machines, orientations, compatibility and load matrices are NOT_RUN in this receipt.");
        q.receipt.addProperty("inputType","Owned synthetic client boot/camera/quality switch; isolated native TestLab fixture placement and supply; physical input/IME NOT_RUN");
    }
    static Object call(Object t,String n,Object...a)throws Exception{return Mui2ClientQa.call(t,n,a);}
    static Object field(Object t,String n)throws Exception{return Mui2ClientQa.field(t,n);}
    static void set(Object t,String n,Object v)throws Exception{Mui2ClientQa.set(t,n,v);}
    static Class<?> type(String n)throws Exception{return Mui2ClientQa.type(n);}
    static long number(Object v){return ((Number)v).longValue();}
    static Object en(String name,String value)throws Exception{return Enum.valueOf((Class)type(name),value);}
    void check(String n,boolean ok,String detail){q.check("task03."+machineId+"."+n,ok,detail);}
    Object snapshot(Object m)throws Exception{return call(type(CLIENT),"snapshot",m);}
    long cubes()throws Exception{return number(field(type(CLIENT),"renderedCubes"));}
    Object clientMachine()throws Exception {
        Object w=field(q.mc,"theWorld|field_71441_e"),tile=call(w,"getTileEntity|func_147438_o",hx,hy,hz);
        return tile==null?null:call(tile,"getMetaTileEntity");
    }
    int uploadedHiddenCells(Object m)throws Exception {
        Object entry=call(type(CLIENT),"entry",m);int count=0;
        for(Object cell:((Map<?,?>)field(type("com.miaokatze.gtsr.client.machinevisual.MachineVisualVisibility"),"CELLS")).values())
            if(field(cell,"owner")==entry&&Boolean.TRUE.equals(field(cell,"uploadedHidden")))count++;
        return count;
    }
    int nativeClouds(Object value) {
        if(value==null)return 0;
        if(value.getClass().getName().endsWith(".EntityCloudFX"))return 1;
        int count=0;
        if(value instanceof Iterable)for(Object item:(Iterable<?>)value)count+=nativeClouds(item);
        else if(value instanceof Object[])for(Object item:(Object[])value)count+=nativeClouds(item);
        return count;
    }
    boolean thermal(){return machineId.equals("large_solar_overpressure_array")||machineId.equals("large_geothermal_steam_boiler");}
    boolean motionObstacle(){return machineId.equals("air_compressor")||machineId.equals("steam_fluid_drill")||machineId.equals("crust_steam_borer");}
    boolean ventObstacle(){return machineId.equals("large_coke_oven")||machineId.equals("siemens_martin_furnace")||machineId.equals("reinforced_brick_blast_furnace");}
    long cloudCalls()throws Exception{return number(field(type(FX+"CloudParticleQueue"),"emitted"));}
    long grateFaces()throws Exception{return number(field(type(FX+"WorkEffectRenderer"),"renderedFaces"));}
    int actualClouds()throws Exception{return nativeClouds(field(field(q.mc,"effectRenderer|field_71452_i"),"fxLayers|field_78876_b"));}
    JsonObject budgetSnapshot()throws Exception {
        JsonObject result=new JsonObject();
        for(String key:new String[]{"motionSubmissions","motionSubmittedBytes","restorationSubmissions","restorationSubmittedBytes","heldMotionMembers","heldMotionGroups","motionMemberLimit","motionGroupLimit","budgetDeferredHosts","budgetRotations"})result.addProperty(key,(Number)field(type(CLIENT),key));
        if(Boolean.getBoolean("gtsr.task03.offscreen")) {
            for(String key:new String[]{"restorationRequestsEnqueued","mandatoryRestorations","rendererRetirementAcks","absentRendererAcks"})result.addProperty(key,(Number)field(type("com.miaokatze.gtsr.client.machinevisual.MachineVisualVisibility"),key));
            result.add("restorationDiagnostics",new Gson().toJsonTree(call(type(CLIENT),"restorationDiagnostics")));
        }
        result.addProperty("scope","Logical Tessellator submission counts/bytes and bounded admission counters; not GPU memory, GPU time or performance benchmark");return result;
    }
    double[] localPoint(double x,double y,double z)throws Exception{return (double[])type("com.miaokatze.gtsr.common.machinevisual.MachineVisualSpace").getMethod("transform",type("com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing"),double.class,double.class,double.class).invoke(null,call(machine,"getExtendedFacing"),x,y,z);}
    int[] localCell(int x,int y,int z)throws Exception{return (int[])type("com.miaokatze.gtsr.common.machinevisual.MachineVisualSpace").getMethod("transform",type("com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing"),int.class,int.class,int.class).invoke(null,call(machine,"getExtendedFacing"),x,y,z);}

    static String jsonString(String value){return "\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n").replace("\t","\\t")+"\"";}
    void observeUncaughtExceptions() {
        final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        ownedUncaughtHandler=new Thread.UncaughtExceptionHandler(){public void uncaughtException(Thread thread,Throwable error){
            try {
                java.io.StringWriter text=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(text));
                String body="{\"thread\":"+jsonString(thread.getName())+",\"throwable\":"+jsonString(text.toString())+",\"delegatesPrevious\":"+(previous!=null)+"}";
                String filename="uncaught-"+thread.getName().replaceAll("[^a-zA-Z0-9_-]","_")+"-"+System.currentTimeMillis()+".json";
                Files.write(out.resolve(filename),body.getBytes(StandardCharsets.UTF_8));
            } catch(Exception recordFailure){recordFailure.printStackTrace();}
            if(previous!=null)previous.uncaughtException(thread,error);
            else if(!(error instanceof ThreadDeath))error.printStackTrace();
        }};
        Thread.setDefaultUncaughtExceptionHandler(ownedUncaughtHandler);
        q.receipt.addProperty("uncaughtExceptionScope","Default handler records full Throwable first, then delegates prior default (or Java's prior stderr behavior when absent). Per-thread handlers or later default replacement may bypass it.");
    }

    void boot()throws Exception {
        observeUncaughtExceptions();
        System.setProperty("gtsr.testlab.motionShape","true");
        q.receipt.addProperty("testlabSelectedMotionShape",true);
        Object settings=field(q.mc,"gameSettings|field_71474_y");
        set(settings,"pauseOnLostFocus|field_82881_y",false);set(settings,"particleSetting|field_74362_aa",0);
        set(settings,"hideGUI|field_74319_N",true);
        Object ws=type("net.minecraft.world.WorldSettings").getConstructor(long.class,type("net.minecraft.world.WorldSettings$GameType"),boolean.class,boolean.class,type("net.minecraft.world.WorldType")).newInstance(30320261101L,en("net.minecraft.world.WorldSettings$GameType","CREATIVE"),false,false,field(type("net.minecraft.world.WorldType"),"FLAT|field_77138_c"));
        call(ws,"enableCommands|func_77159_a");
        call(q.mc,"launchIntegratedServer|func_71371_a",System.getProperty("gtsr.uiqa.world"),"Owned TASK03 native work visual QA",ws);
    }

    @SubscribeEvent public void server(TickEvent.ServerTickEvent event) {
        if(done||event.phase!=TickEvent.Phase.END||!startRequested)return;
        try {
            Object server=FMLCommonHandler.instance().getMinecraftServerInstance();
            List<?> players=(List<?>)field(call(server,"getConfigurationManager|func_71203_ab"),"playerEntityList|field_72404_b");
            if(players.isEmpty())return;
            Object player=players.get(0);serverTicks++;
            if(stopRequested) {
                if(obstaclePlaced)removeObstacle();
                if(thermal()&&machine!=null){residualStartHeat=((Number)field(machine,"mHeat")).doubleValue();residualActualStopMteTick=number(call(host,"getTimer"));}
                if(task!=null)call(task,"stop",world);
                if(thermal()&&machine!=null){residualAfterActualStopHeat=((Number)field(machine,"mHeat")).doubleValue();serverNativeHeat=residualAfterActualStopHeat;}
                stopped=true;stopRequested=false;return;
            }
            if(stopped){if(thermal()&&serverTicks%20==0){serverNativeHeat=((Number)field(machine,"mHeat")).doubleValue();residualLatestMteTick=number(call(host,"getTimer"));JsonObject row=new JsonObject();row.addProperty("machine",machineId);row.addProperty("nativeHeatAfterActualStop",serverNativeHeat);row.addProperty("actualMteTick",residualLatestMteTick);serverSamples.add(row);}return;}
            if(task==null) {
                check("isolated-lab-enabled",Boolean.TRUE.equals(call(type(LAB+"TestLabDimension"),"enabled")),"Current candidate testlab opt-in");
                call(type(LAB+"TestLabCommand"),"enter",player);
                world=call(type(LAB+"TestLabDimension"),"world");
                int originX=caseIndex*256,centerChunk=(originX+80)>>4;
                for(int x=centerChunk-2;x<=centerChunk+2;x++)for(int z=1;z<=5;z++)call(type(LAB+"TestLabFixtures"),"requireChunk",world,x,z);
                for(Object candidate:(List<?>)call(type(LAB+"TestLabCatalog"),"tasks"))if(machineId.equals(call(candidate,"id"))){task=candidate;break;}
                check("live-catalog",task!=null,"Current production TestLabCatalog, not copied model geometry");
                call(task,"start",world,originX,65,0);host=field(task,"controller");machine=call(host,"getMetaTileEntity");
                hx=(int)number(call(host,"getXCoord"));hy=(int)number(call(host,"getYCoord"));hz=(int)number(call(host,"getZCoord"));
                q.receipt.addProperty("productionCodeSource",machine.getClass().getProtectionDomain().getCodeSource().getLocation().toString());
                q.receipt.addProperty("coreCodeSource",type("com.miaokatze.miaokatzecore.api.animation.AnimationV1").getProtectionDomain().getCodeSource().getLocation().toString());
                Object definition=call(machine,"visualDefinition");
                double minX=((Number)field(definition,"minX")).doubleValue(),maxX=((Number)field(definition,"maxX")).doubleValue();
                double minY=((Number)field(definition,"minY")).doubleValue(),maxY=((Number)field(definition,"maxY")).doubleValue();
                double minZ=((Number)field(definition,"minZ")).doubleValue(),maxZ=((Number)field(definition,"maxZ")).doubleValue();
                int sourceHeight=((Object[])call(definition,"shapeLayers")).length;
                if(sourceHeight>0)maxY=minY+sourceHeight;
                double[] center=localPoint((minX+maxX)*.5-.5,(minY+maxY)*.5-.5,(minZ+maxZ)*.5-.5);
                double height=maxY-minY,radius=Math.max(height*1.25,Math.max(maxX-minX,maxZ-minZ)*.62);
                double tx=hx+.5+center[0],ty=hy+.5+center[1],tz=hz+.5+center[2];
                double px=tx+radius*.957826,py=ty+height*.07-1.62,pz=tz-radius*.287348;
                float yaw=(float)Math.toDegrees(Math.atan2(-(tx-px),tz-pz));
                float pitch=(float)Math.toDegrees(Math.atan2(height*.07,radius));
                call(field(player,"playerNetServerHandler|field_71135_a"),"setPlayerLocation|func_147364_a",px,py,pz,yaw,pitch);
                if(machineId.equals("gear_steam_compressor"))focusGearWindow(player);
                JsonObject camera=new JsonObject();camera.addProperty("machine",machineId);camera.addProperty("radius",radius);camera.addProperty("targetHeightPixels",400);camera.addProperty("hideGUI",true);q.receipt.add("camera",camera);
                Object caps=field(player,"capabilities|field_71075_bZ");set(caps,"isFlying|field_75100_b",true);call(player,"sendPlayerAbilities|func_71016_p");
                return;
            }
            if(cameraRequest){focusOutletCamera(player);cameraRequest=false;cameraAck=true;}
            if(offscreenCameraRequest!=0){int action=offscreenCameraRequest;moveOffscreenCamera(player,action);offscreenCameraRequest=0;offscreenCameraAck=action;}
            if(obstacleRequest==1){placeObstacle();obstacleRequest=0;obstacleAck=1;}
            if(obstacleRequest==2){removeObstacle();obstacleRequest=0;obstacleAck=2;}
            call(type(LAB+"TestLabSupply"),"tick",world);
            int taskPhase=(int)number(field(task,"phase"));
            Object result=null;
            if(taskPhase==1&&number(field(task,"portCursor"))>=((List<?>)field(task,"requests")).size()) {
                // Reuse the canonical finalization without the historical direct preheat acceleration fixture.
                boolean formed=Boolean.TRUE.equals(call(type(LAB+"TestLabStructure"),"checkStructure",machine,host));
                check("canonical-formation",formed,"Live native checker after canonical hatch installation");
                set(task,"formed",formed);call(task,"captureBeforeThermalFixture");call(task,"seedInputs");
                refillRecipeInputs();
                set(task,"inputItemCount",call(task,"countInputItems"));set(task,"startedProcessing",serverTicks);
                call(host,"enableWorking");set(task,"phase",2);ready=true;
                check("zero-thermal-writes",number(field(task,"directThermalWrites"))==0,"No direct preheat/heat/NBT/progress fixture");
            } else result=call(task,"tick",world,serverTicks);
            if(result!=null) {
                String status=String.valueOf(field(result,"status"));
                if(!productionSeen||!status.equals("PASS"))check("natural-production-result",status.equals("PASS"),String.valueOf(field(result,"detail")));
                if(!productionSeen){productionSeen=true;q.receipt.add("productionResult",new Gson().toJsonTree(result));}
            }
            if(ready)refillRecipeInputs();
            if(productionSeen&&serverTicks%20==0)drainFixtureOutputs();
            if(serverTicks%20==0&&ready) {
                JsonObject row=new JsonObject();row.addProperty("serverTicks",serverTicks);row.addProperty("machine",machineId);
                row.addProperty("nativeDebug",String.valueOf(call(task,"debug",world)));
                row.add("authority",new Gson().toJsonTree(call(call(machine,"visualState"),"snapshot")));
                if(thermal()){serverNativeHeat=((Number)field(machine,"mHeat")).doubleValue();row.addProperty("nativeHeat",serverNativeHeat);}
                serverSamples.add(row);
            }
        } catch(Throwable error){if(stopRequested){q.receipt.addProperty("nativeStopFailure",error.toString());stopRequested=false;stopped=true;}serverFailure=error;error.printStackTrace();}
    }

    void focusGearWindow(Object player)throws Exception {
        double[] from=localPoint(0D,1D,-3D),to=localPoint(0D,1D,2D);
        double px=hx+.5+from[0],py=hy+.5+from[1]-1.62,pz=hz+.5+from[2];
        float yaw=(float)Math.toDegrees(Math.atan2(-(to[0]-from[0]),to[2]-from[2]));
        call(field(player,"playerNetServerHandler|field_71135_a"),"setPlayerLocation|func_147364_a",px,py,pz,yaw,0F);
        q.receipt.addProperty("gearVisibilityScope","Original front E glass at Y1,Z0,eye centered on original15-cell skip gap; no wall removal or added window. Closed side casing limits side-view visibility.");
    }

    void moveOffscreenCamera(Object player,int action)throws Exception {
        Object net=field(player,"playerNetServerHandler|field_71135_a");
        if(action==2){call(net,"setPlayerLocation|func_147364_a",savedCameraX,savedCameraY,savedCameraZ,savedCameraYaw,savedCameraPitch);return;}
        savedCameraX=((Number)field(player,"posX|field_70165_t")).doubleValue();savedCameraY=((Number)field(player,"posY|field_70163_u")).doubleValue();savedCameraZ=((Number)field(player,"posZ|field_70161_v")).doubleValue();
        savedCameraYaw=((Number)field(player,"rotationYaw|field_70177_z")).floatValue();savedCameraPitch=((Number)field(player,"rotationPitch|field_70125_A")).floatValue();
        Object group=((List<?>)field(call(machine,"visualDefinition"),"groups")).get(0),member=((List<?>)field(group,"members")).get(0);
        int[] p=localCell((int)number(field(member,"x")),(int)number(field(member,"y")),(int)number(field(member,"z")));
        double sx=((hx+p[0])>>4)*16+8,sy=((hy+p[1])>>4)*16+8,sz=((hz+p[2])>>4)*16+8;
        double x=hx+.5,y=savedCameraY,z=sz-40D,eye=y+((Number)call(player,"getEyeHeight|func_70047_e")).doubleValue();
        offscreenSectionDistance=Math.sqrt((x-sx)*(x-sx)+(eye-sy)*(eye-sy)+(z-sz)*(z-sz));
        offscreenControllerDistance=Math.sqrt((x-hx-.5)*(x-hx-.5)+(eye-hy-.5)*(eye-hy-.5)+(z-hz-.5)*(z-hz-.5));
        check("offscreen-distance",offscreenSectionDistance>=32&&offscreenSectionDistance<=48&&offscreenControllerDistance<64,"Actual source section distance="+offscreenSectionDistance+", controller distance="+offscreenControllerDistance+"; avoid Angelica near-important radius");
        call(net,"setPlayerLocation|func_147364_a",x,y,z,180F,0F);
    }

    void focusOutletCamera(Object player)throws Exception {
        Object definition=call(type("com.miaokatze.gtsr.common.workeffects.WorkEffectDefinitions"),"forHost",machine);
        List<?> emitters=(List<?>)call(definition,"emitters");double tx=0,ty=0,tz=0;
        for(Object emitter:emitters){Object cell=call(emitter,"cell");Object abc=type("com.gtnewhorizon.structurelib.util.Vec3Impl").getConstructor(int.class,int.class,int.class).newInstance((int)number(call(cell,"a")),(int)number(call(cell,"b")),(int)number(call(cell,"c")));
            Object offset=call(call(machine,"getExtendedFacing"),"getWorldOffset",abc);tx+=number(call(offset,"get0"));ty+=number(call(offset,"get1"))+.5+((Number)call(emitter,"centerYOffset")).doubleValue();tz+=number(call(offset,"get2"));}
        tx=hx+.5+tx/emitters.size();ty=hy+ty/emitters.size();tz=hz+.5+tz/emitters.size();
        double radius=8D,px=tx,pz=tz-radius;
        check("outlet-camera-air",Boolean.TRUE.equals(call(world,"isAirBlock|func_147437_c",(int)Math.floor(px),(int)Math.floor(ty),(int)Math.floor(pz))),"Original outlet viewed from a real external air cell within native cloud range");
        call(field(player,"playerNetServerHandler|field_71135_a"),"setPlayerLocation|func_147364_a",px,ty-1.62,pz,(float)Math.toDegrees(Math.atan2(-(tx-px),tz-pz)),0F);
    }

    void placeObstacle()throws Exception {
        int[] point;
        if(motionObstacle()) {
            int x=0,y=machineId.equals("air_compressor")?0:machineId.equals("steam_fluid_drill")?6:10,z=2;
            Object definition=call(machine,"visualDefinition");String[][] layers=(String[][])call(definition,"shapeLayers");
            int a=x+(int)number(call(definition,"offset",0)),b=(int)number(call(definition,"offset",1))-y,c=z+(int)number(call(definition,"offset",2));
            boolean in=b>=0&&b<layers.length&&c>=0&&c<layers[b].length&&a>=0&&a<layers[b][c].length();
            check("obstacle-source-skip",!in||layers[b][c].charAt(a)==' ',"Selected decoration-adjacent cell is outside original shape or literal skip, never required-air/hatch/material");
            point=localCell(x,y,z);
        } else {
            Object definition=call(type("com.miaokatze.gtsr.common.workeffects.WorkEffectDefinitions"),"forHost",machine);
            Object emitter=((List<?>)call(definition,"emitters")).get(0),above=call(emitter,"above");
            check("obstacle-selected-vent-above",above!=null,"Use selected outlet's external above cell; leave required-air mouth untouched");
            Object abc=type("com.gtnewhorizon.structurelib.util.Vec3Impl").getConstructor(int.class,int.class,int.class).newInstance((int)number(call(above,"a")),(int)number(call(above,"b")),(int)number(call(above,"c")));
            Object offset=call(call(machine,"getExtendedFacing"),"getWorldOffset",abc);point=new int[]{(int)number(call(offset,"get0")),(int)number(call(offset,"get1")),(int)number(call(offset,"get2"))};
        }
        ox=hx+point[0];oy=hy+point[1];oz=hz+point[2];
        check("obstacle-before-is-air",Boolean.TRUE.equals(call(world,"isAirBlock|func_147437_c",ox,oy,oz))&&call(world,"getTileEntity|func_147438_o",ox,oy,oz)==null,"Read actual owned world before placing ordinary stone at "+ox+","+oy+","+oz);
        call(type(LAB+"TestLabFixtures"),"track",world,ox,oy,oz);
        check("obstacle-stone-placed",Boolean.TRUE.equals(call(world,"setBlock|func_147465_d",ox,oy,oz,field(type("net.minecraft.init.Blocks"),"stone|field_150348_b"),0,3)),"Actual world placement and normal block update packet");
        obstaclePlaced=true;
        check("obstacle-native-still-formed",Boolean.TRUE.equals(call(type(LAB+"TestLabStructure"),"checkStructure",machine,host)),"Real canonical check still accepts the source skip/external cell occupied");
    }

    void removeObstacle()throws Exception {
        check("obstacle-owned-stone",call(world,"getBlock|func_147439_a",ox,oy,oz)==field(type("net.minecraft.init.Blocks"),"stone|field_150348_b"),"Remove only this helper's exact ordinary stone cell");
        call(world,"setBlockToAir|func_147468_f",ox,oy,oz);obstaclePlaced=false;
    }

    void refillRecipeInputs()throws Exception {
        Object recipe=field(task,"recipe");if(recipe==null)return;
        Object bus=call(task,"firstPort",type("gregtech.api.metatileentity.implementations.MTEHatchInputBus"));
        if(bus==null)return;
        int slot=0;
        for(Object input:(Object[])field(recipe,"mInputs"))if(input!=null) {
            int required=Math.max(1,(int)number(field(input,"stackSize|field_77994_a")));
            Object current=call(bus,"getStackInSlot|func_70301_a",slot);
            if(current==null||number(field(current,"stackSize|field_77994_a"))<required) {
                Object copy=call(input,"copy|func_77946_l");
                int count=Math.max(required,(int)number(call(copy,"getMaxStackSize|func_77976_d")));
                set(copy,"stackSize|field_77994_a",count);
                call(bus,"setInventorySlotContents|func_70299_a",slot,copy);inputFixtureWrites++;
            }
            slot++;
        }
    }

    void drainFixtureOutputs()throws Exception {
        for(Object port:(List<?>)field(task,"ports")) {
            Object meta=call(port,"getMetaTileEntity");
            if(type("gregtech.api.metatileentity.implementations.MTEHatchOutputBus").isInstance(meta)) {
                int slots=(int)number(call(port,"getSizeInventory|func_70302_i_"));
                for(int slot=0;slot<slots;slot++) {
                    Object stack=call(port,"getStackInSlot|func_70301_a",slot);if(stack==null)continue;
                    Object removed=call(port,"decrStackSize|func_70298_a",slot,number(field(stack,"stackSize|field_77994_a")));
                    if(removed!=null)outputItemsRemoved+=number(field(removed,"stackSize|field_77994_a"));
                }
            } else if(type("gregtech.api.metatileentity.implementations.MTEHatchOutput").isInstance(meta)) {
                Object drained=call(meta,"drain",en("net.minecraftforge.common.util.ForgeDirection","UNKNOWN"),Integer.MAX_VALUE,true);
                if(drained!=null)outputFluidRemoved+=number(field(drained,"amount"));
            }
        }
    }

    @SubscribeEvent public void client(TickEvent.ClientTickEvent event) {
        if(done||event.phase!=TickEvent.Phase.END)return;
        try {
            q.mc=call(type("net.minecraft.client.Minecraft"),"getMinecraft|func_71410_x");ticks++;q.phase=phase;
            if(finishing){
                if(!stopped&&task!=null)return;
                if(!worldUnloadRequested){worldUnloadRequested=true;call(q.mc,"displayGuiScreen|func_147108_a",type("net.minecraft.client.gui.GuiMainMenu").getConstructor().newInstance());call(q.mc,"loadWorld|func_71403_a",new Object[]{null});return;}
                if(task!=null&&!integratedStopped)return;
                if(field(q.mc,"theWorld|field_71441_e")!=null||++cleanupTicks<4)return;
                recordWorldCleanup();done=true;q.finish(failure);return;
            }
            if(serverFailure!=null)throw new RuntimeException("Owned native fixture",serverFailure);
            if(System.currentTimeMillis()-q.started>Long.getLong("gtsr.uiqa.timeoutMillis",600000L))throw new AssertionError("TASK03 timeout phase="+phase+" serverTicks="+serverTicks);
            if(phase==90) {
                if(!stopped)return;
                cases.add(completedCaseReport);
                Files.write(out.resolve(machineId+"-case.json"),new GsonBuilder().setPrettyPrinting().create().toJson(completedCaseReport).getBytes(StandardCharsets.UTF_8));
                check("case-complete",true,"Native fixture stopped after all screenshots, pose checks and OFF/restore checks");
                if(++caseIndex==machineIds.length){finish(null);return;}
                machineId=machineIds[caseIndex];task=null;host=null;machine=null;world=null;
                ready=false;productionSeen=false;serverTicks=0;shots=0;phaseTick=ticks;
                firstRenderedPose=null;largestRenderedPoseDelta=0;maxNativeClouds=0;
                peakWaitStarted=0;bestPeakMetric=0;
                completedCaseReport=null;
                obstacleRequest=0;obstacleAck=0;obstaclePlaced=false;obstructionDone=false;cameraRequest=false;cameraAck=false;outletCaptured=false;serverNativeHeat=0;
                offscreenCameraRequest=0;offscreenCameraAck=0;offscreenTestDone=false;
                thermalWaitSamples=new JsonArray();
                for(String key:new String[]{"obstructionTrace","residualHeatTrace","thermalWaitSamples","gearVisibilityScope","offscreenRestorationTrace"})q.receipt.remove(key);
                inputFixtureWrites=0;outputItemsRemoved=0;outputFluidRemoved=0;
                firstFramesReceived=number(field(type(CLIENT),"framesReceived"));firstRenderedCubes=cubes();
                initialCloudCalls=number(field(type(FX+"CloudParticleQueue"),"emitted"));initialGrateFaces=number(field(type(FX+"WorkEffectRenderer"),"renderedFaces"));
                stopped=false;phase=2;return;
            }
            if(phase==0){if(ticks<40)return;boot();phase=1;return;}
            Object player=field(q.mc,"thePlayer|field_71439_g");if(player==null)return;
            if(field(q.mc,"currentScreen|field_71462_r")!=null)call(q.mc,"displayGuiScreen|func_147108_a",new Object[]{null});
            if(phase==1){if(number(field(player,"ticksExisted|field_70173_aa"))<40)return;q.wire=UiQaWireCounter.install(q,player);firstFramesReceived=number(field(type(CLIENT),"framesReceived"));firstRenderedCubes=cubes();initialCloudCalls=number(field(type(FX+"CloudParticleQueue"),"emitted"));initialGrateFaces=number(field(type(FX+"WorkEffectRenderer"),"renderedFaces"));startRequested=true;phase=2;return;}
            if(!ready||pendingShot!=null)return;
            Object clientWorld=field(q.mc,"theWorld|field_71441_e");
            if(number(field(player,"dimension|field_71093_bK"))!=number(call(type(LAB+"TestLabDimension"),"id")))return;
            Object tile=call(clientWorld,"getTileEntity|func_147438_o",hx,hy,hz);if(tile==null)return;
            Object m=call(tile,"getMetaTileEntity");if(m==null)return;
            Object frame=snapshot(m);if(frame==null)return;
            boolean working=Boolean.TRUE.equals(call(type(CLIENT),"isWorking",m));
            int mask=(int)number(call(type(CLIENT),"safeGroupMask",m));
            int groups=((List<?>)field(call(m,"visualDefinition"),"groups")).size();
            int expectedMask=(1<<groups)-1;
            if(phase>=40&&phase<=46&&System.nanoTime()-offscreenWaitStarted>60000000000L){q.receipt.add("offscreenTimeoutDiagnostics",budgetSnapshot());throw new AssertionError("Offscreen restoration/return did not reach actual ACK within60 seconds; diagnostics preserved");}
            if(phase==2) {
                if(!working||mask!=expectedMask||number(call(type(CLIENT),"uploadedGroupMask",m))!=expectedMask)return;
                if(shots>=3) {
                    if(number(field(frame,"workSequence"))<=firstSequence)return;
                    // All three render callbacks have consumed pendingShot before this branch can change quality.
                    check("authoritative-progress",number(field(frame,"workSequence"))>firstSequence,"Distinct native successful advancement events, not a synthetic phase");
                    check("subscription-delivered",number(field(type(CLIENT),"framesReceived"))>firstFramesReceived,"Real MachineVisualNetwork subscription/nonce accepted by current client");
                    if(groups>0)check("dynamic-world-render",cubes()>firstRenderedCubes,"Actual production TESR submitted full-size moving members with safe mask="+mask);
                    q.receipt.add("wireAtWork",q.wire.snapshot());offSequence=number(field(frame,"workSequence"));
                    call(type(CLIENT),"setQuality",en(CLIENT+"$Quality","OFF"));phase=3;phaseTick=ticks;return;
                }
                if(shots==0){firstSequence=number(field(frame,"workSequence"));phaseTick=ticks;pendingShot=machineId+"-working-0";shots++;return;}
                if(ticks-phaseTick<11)return;
                pendingShot=machineId+"-working-"+shots;phaseTick=ticks;shots++;
                return;
            }
            if(phase==3) {
                if(ticks-phaseTick<12||uploadedHiddenCells(m)!=0)return;
                check("off-static-fallback",!working&&mask==0,"Shared OFF removes visual ownership and restores chunk static cells");
                offCubes=cubes();offCloudCalls=cloudCalls();offGrateFaces=grateFaces();pendingShot=machineId+"-off";phase=4;phaseTick=ticks;return;
            }
            if(phase==4) {
                if(ticks-phaseTick<12)return;
                if(number(field(frame,"workSequence"))<=offSequence)return;
                check("off-no-dynamic-submit",cubes()==offCubes,"Real renderer cube counter stable while OFF");
                check("off-no-cloud-or-foreground",cloudCalls()==offCloudCalls&&grateFaces()==offGrateFaces,"Shared OFF stops new cloud calls and foreground quads; existing native FX may fade naturally");
                check("off-production-continues",number(field(frame,"workSequence"))>offSequence,"Natural server advancement continues with visual quality OFF");
                call(type(CLIENT),"setQuality",en(CLIENT+"$Quality","STANDARD"));phase=5;phaseTick=ticks;return;
            }
            if(phase==5&&working&&mask==expectedMask&&number(call(type(CLIENT),"uploadedGroupMask",m))==expectedMask&&productionSeen) {
                pendingShot=machineId+"-resumed";phase=6;phaseTick=ticks;return;
            }
            maxNativeClouds=Math.max(maxNativeClouds,actualClouds());
            if(phase==40) {
                Object entry=call(type(CLIENT),"entry",m);
                if(offscreenCameraAck!=1||number(call(type(CLIENT),"renderFrame"))-number(field(entry,"lastRenderFrame"))<4)return;
                for(Object group:(List<?>)field(call(m,"visualDefinition"),"groups"))for(Object member:(List<?>)field(group,"members")) {
                    int[] p=(int[])call(entry,"at",number(field(member,"x")),number(field(member,"y")),number(field(member,"z")));
                    check("offscreen-source-chunk-loaded",Boolean.TRUE.equals(call(clientWorld,"blockExists|func_72899_e",p[0],p[1],p[2])),"Original source section stays loaded while outside view; this is not a world/chunk unload substitute");
                }
                call(type(CLIENT),"setQuality",en(CLIENT+"$Quality","OFF"));phase=41;phaseTick=ticks;return;
            }
            if(phase==41) {
                if(mask!=0||uploadedHiddenCells(m)!=0||number(field(type(CLIENT),"heldMotionMembers"))!=0||number(field(type(CLIENT),"heldMotionGroups"))!=0)return;
                JsonObject observed=budgetSnapshot();observed.add("beforeOffscreen",offscreenBefore);observed.addProperty("workSequenceBefore",offscreenWorkSequence);observed.addProperty("workSequenceAtAck",number(field(frame,"workSequence")));q.receipt.add("offscreenAckObserved",observed);
                if(number(field(frame,"workSequence"))<=offscreenWorkSequence)return;
                check("offscreen-real-restoration-ack",number(field(frame,"workSequence"))>offscreenWorkSequence,"No look-back: exact GPU upload/retirement ACK released all held reservations while native work continued");
                JsonObject proof=budgetSnapshot();proof.add("beforeOffscreen",offscreenBefore);proof.addProperty("sourceSectionDistance",offscreenSectionDistance);proof.addProperty("controllerDistance",offscreenControllerDistance);proof.addProperty("workSequenceBefore",offscreenWorkSequence);proof.addProperty("workSequenceAfter",number(field(frame,"workSequence")));q.receipt.add("offscreenRestorationTrace",proof);
                pendingShot=machineId+"-offscreen-off";phase=42;return;
            }
            if(phase==42){offscreenCameraRequest=2;phase=43;return;}
            if(phase==43&&offscreenCameraAck==2) {
                Object entry=call(type(CLIENT),"entry",m);if(number(call(type(CLIENT),"renderFrame"))-number(field(entry,"lastRenderFrame"))>1)return;
                pendingShot=machineId+"-offscreen-return-off";phase=44;return;
            }
            if(phase==44){call(type(CLIENT),"setQuality",en(CLIENT+"$Quality","STANDARD"));phase=45;return;}
            if(phase==45&&working&&mask==expectedMask&&number(call(type(CLIENT),"uploadedGroupMask",m))==expectedMask){pendingShot=machineId+"-offscreen-resumed";phase=46;phaseTick=ticks;return;}
            if(phase==46&&ticks-phaseTick>=5){offscreenTestDone=true;check("offscreen-return-restores-motion",true,"Original near camera and STANDARD return only after complete upload mask and a real nonzero native sampled pose");completeCase(frame);return;}
            if(phase==7) {
                if(obstacleAck!=1||call(clientWorld,"getBlock|func_147439_a",ox,oy,oz)!=field(type("net.minecraft.init.Blocks"),"stone|field_150348_b")||mask!=expectedMask||number(call(type(CLIENT),"uploadedGroupMask",m))!=expectedMask||number(field(frame,"workSequence"))<=blockedSequence)return;
                check("decoration-ignores-skip-stone",working&&Boolean.TRUE.equals(call(frame,"formed")),"Actual skip/top stone is visible in client; authorised internal decoration keeps its published groups while native advancement continues");
                blockedCubes=cubes();pendingShot=machineId+"-decoration-stone";phase=8;phaseTick=ticks;return;
            }
            if(phase==8&&ticks-phaseTick>=12) {
                check("decoration-stone-keeps-motion",mask==expectedMask&&number(call(type(CLIENT),"uploadedGroupMask",m))==expectedMask,"Unrelated source skip stone never revokes authorised decoration or forces static restoration");
                check("decoration-stone-production",number(field(frame,"workSequence"))>blockedSequence,"Actual successful native work continues with decoration and unrelated stone");
                JsonObject trace=new JsonObject();trace.addProperty("kind","decoration ignores unrelated source skip cell");trace.addProperty("xyz",ox+","+oy+","+oz);trace.addProperty("animatedMask",mask);trace.addProperty("uploadedHiddenCells",uploadedHiddenCells(m));trace.addProperty("workSequenceBefore",blockedSequence);trace.addProperty("workSequenceAfter",number(field(frame,"workSequence")));q.receipt.add("obstructionTrace",trace);
                obstacleRequest=2;phase=9;return;
            }
            if(phase==9&&obstacleAck==2&&Boolean.TRUE.equals(call(clientWorld,"isAirBlock|func_147437_c",ox,oy,oz))&&working&&mask==expectedMask&&number(call(type(CLIENT),"uploadedGroupMask",m))==expectedMask) {
                pendingShot=machineId+"-decoration-stone-removed";phase=10;phaseTick=ticks;return;
            }
            if(phase==10&&ticks-phaseTick>=5){check("decoration-cleanup-keeps-motion",mask==expectedMask,"Removing owned unrelated stone leaves the original decorative animation authorised");completeCase(frame);return;}
            if(phase==20) {
                if(obstacleAck!=1||call(clientWorld,"getBlock|func_147439_a",ox,oy,oz)!=field(type("net.minecraft.init.Blocks"),"stone|field_150348_b"))return;
                blockedCloudCalls=cloudCalls();blockedSequence=number(field(frame,"workSequence"));phase=21;phaseTick=ticks;return;
            }
            if(phase==21&&ticks-phaseTick>=30) {
                if(!working||number(field(frame,"workSequence"))<=blockedSequence)return;
                check("vent-above-block-stops-new-cloud",cloudCalls()==blockedCloudCalls,"Above cell ordinary stone stops new native cloud calls; existing FX are left to fade naturally");
                check("vent-block-production",working&&number(field(frame,"workSequence"))>blockedSequence,"Canonical formation and real recipe advancement remain valid while only the external above cell is occupied");
                JsonObject trace=new JsonObject();trace.addProperty("kind","selected vent external above cell");trace.addProperty("xyz",ox+","+oy+","+oz);trace.addProperty("cloudCallsBefore",blockedCloudCalls);trace.addProperty("cloudCallsAfter",cloudCalls());trace.addProperty("workSequenceBefore",blockedSequence);trace.addProperty("workSequenceAfter",number(field(frame,"workSequence")));q.receipt.add("obstructionTrace",trace);
                pendingShot=machineId+"-vent-blocked";phase=22;phaseTick=ticks;return;
            }
            if(phase==22){obstacleRequest=2;phase=23;return;}
            if(phase==23&&obstacleAck==2&&Boolean.TRUE.equals(call(clientWorld,"isAirBlock|func_147437_c",ox,oy,oz))&&cloudCalls()>blockedCloudCalls&&actualClouds()>0) {
                check("vent-above-clear-restarts",true,"Removing only owned stone restores real cloud spawning at the selected original outlet");pendingShot=machineId+"-vent-restored";phase=24;phaseTick=ticks;return;
            }
            if(phase==24&&ticks-phaseTick>=5){completeCase(frame);return;}
            if(phase==30&&stopped&&Boolean.TRUE.equals(call(type(CLIENT),"isFresh",m))&&!Boolean.TRUE.equals(call(frame,"working"))&&number(field(frame,"duration"))==0){residualCloudCalls=cloudCalls();residualClientTicks=number(call(m,"getWorkVisualClientTicks"));phase=32;phaseTick=ticks;return;}
            if(phase==32&&ticks-phaseTick>=40) {
                check("residual-native-heat-decreases",serverNativeHeat>0&&serverNativeHeat<residualStartHeat,"Actual native heat falls after ordinary stop: "+residualStartHeat+" -> "+serverNativeHeat);
                check("residual-no-work-but-cloud",!working&&cloudCalls()>residualCloudCalls,"No authoritative work advancement; finite new native clouds still consume positive residual heat");
                check("residual-client-tick-continues",number(call(m,"getWorkVisualClientTicks"))>residualClientTicks+20,"Actual onPostTick client calls continue after work stops");
                JsonObject trace=new JsonObject();trace.addProperty("heatBeforeActualServerStop",residualStartHeat);trace.addProperty("heatImmediatelyAfterActualServerStop",residualAfterActualStopHeat);trace.addProperty("nativeHeatAfterCooling",serverNativeHeat);trace.addProperty("actualStopMteTick",residualActualStopMteTick);trace.addProperty("latestCoolingMteTick",residualLatestMteTick);trace.addProperty("quantizedClientHeat",((Number)field(m,"mHeat")).doubleValue());trace.addProperty("cloudCallsAfterClientObservedStopped",residualCloudCalls);trace.addProperty("cloudCallsAfterResidualWindow",cloudCalls());trace.addProperty("authoritativeWorking",working);trace.addProperty("fullCoolingToZero","NOT_RUN");q.receipt.add("residualHeatTrace",trace);
                pendingShot=machineId+"-residual-heat";phase=31;phaseTick=ticks;return;
            }
            if(phase==31&&ticks-phaseTick>=5){completeCase(frame);return;}
            if(phase==6&&ticks-phaseTick>=5) {
                if(thermal()&&(serverNativeHeat<.075D||((Number)field(m,"mHeat")).doubleValue()<=0D)) {
                    if(ticks%100==0){JsonObject sample=new JsonObject();sample.addProperty("clientTicks",number(call(m,"getWorkVisualClientTicks")));sample.addProperty("quantizedClientHeat",((Number)field(m,"mHeat")).doubleValue());sample.addProperty("observedNativeHeat",serverNativeHeat);sample.addProperty("cloudCalls",cloudCalls());thermalWaitSamples.add(sample);q.receipt.add("thermalWaitSamples",thermalWaitSamples);}
                    return;
                }
                Object effects=call(type("com.miaokatze.gtsr.common.workeffects.WorkEffectDefinitions"),"forHost",m);
                if(effects!=null&&!((List<?>)call(effects,"emitters")).isEmpty()&&!outletCaptured) {
                    if(!cameraAck){cameraRequest=true;return;}
                    if(actualClouds()==0)return;
                    pendingShot=machineId+"-outlet";outletCaptured=true;return;
                }
                q.receipt.addProperty("particleTrace",String.valueOf(call(type(FX+"CloudParticleQueue"),"diagnostics")));
                q.receipt.addProperty("thermalTickTrace",String.valueOf(call(type(FX+"WorkEffectParticles"),"diagnostics")));
                q.receipt.addProperty("foregroundTrace",String.valueOf(call(type(FX+"WorkEffectRenderer"),"diagnostics")));
                q.receipt.add("finalClientSnapshot",new Gson().toJsonTree(frame));
                if(groups>0)check("different-rendered-poses",largestRenderedPoseDelta>1E-6,"Same-frame production render clocks and uploaded groups gave two distinct nonzero native group samples; maxDelta="+largestRenderedPoseDelta);
                if(effects!=null) {
                    if(!((List<?>)call(effects,"fireboxes")).isEmpty())check("actual-foreground-submit",number(field(type(FX+"WorkEffectRenderer"),"renderedFaces"))>initialGrateFaces,"Actual exposed native firebox foreground quads submitted in real world render");
                    if(!((List<?>)call(effects,"emitters")).isEmpty()) {
                        check("native-cloud-spawn-calls",number(field(type(FX+"CloudParticleQueue"),"emitted"))>initialCloudCalls,"Actual native cloud calls passed shared budget/owner/air gates; engine-level rendering is checked separately");
                        check("native-cloud-engine-alive",maxNativeClouds>0,"Actual EntityCloudFX instances observed in native EffectRenderer layers, max="+maxNativeClouds);
                    }
                }
                JsonObject resources=new JsonObject();resources.addProperty("inputRefillWrites",inputFixtureWrites);
                resources.addProperty("outputItemsRemovedToOwnedSink",outputItemsRemoved);resources.addProperty("outputFluidRemovedToOwnedSink",outputFluidRemoved);
                resources.addProperty("scope","Declared owned fixture supply/sink through actual hatch inventory/drain APIs; no conservation claim about supplied test resources");q.receipt.add("resourceFixture",resources);
                if(thermal())check("actual-client-tick",number(call(m,"getWorkVisualClientTicks"))>=20,"Actual MTE onPostTick client calls="+call(m,"getWorkVisualClientTicks"));
                check("production-confirmed",productionSeen,"Canonical fixture observed real input consumption and real output");
                if(motionObstacle()){blockedSequence=number(field(frame,"workSequence"));obstacleRequest=1;phase=7;return;}
                if(ventObstacle()){obstacleRequest=1;phase=20;return;}
                if(thermal()){phase=30;phaseTick=ticks;stopRequested=true;return;}
                completeCase(frame);
            }
        } catch(Throwable error){if(finishing){done=true;q.receipt.addProperty("ownedCleanupFailure",error.toString());q.finish(error);}else finish(error);}
    }

    void completeCase(Object frame)throws Exception {
        if(Boolean.getBoolean("gtsr.task03.offscreen")&&!offscreenTestDone&&!((List<?>)field(call(clientMachine(),"visualDefinition"),"groups")).isEmpty()) {
            offscreenBefore=budgetSnapshot();offscreenWorkSequence=number(field(frame,"workSequence"));offscreenWaitStarted=System.nanoTime();offscreenCameraRequest=1;phase=40;phaseTick=ticks;return;
        }
        q.receipt.add("finalClientSnapshot",new Gson().toJsonTree(frame));
        q.receipt.addProperty("particleTrace",String.valueOf(call(type(FX+"CloudParticleQueue"),"diagnostics")));
        q.receipt.addProperty("thermalTickTrace",String.valueOf(call(type(FX+"WorkEffectParticles"),"diagnostics")));
        q.receipt.addProperty("foregroundTrace",String.valueOf(call(type(FX+"WorkEffectRenderer"),"diagnostics")));
        JsonObject resources=new JsonObject();resources.addProperty("inputRefillWrites",inputFixtureWrites);resources.addProperty("outputItemsRemovedToOwnedSink",outputItemsRemoved);resources.addProperty("outputFluidRemovedToOwnedSink",outputFluidRemoved);resources.addProperty("scope","Declared owned fixture supply/sink through actual hatch APIs; no conservation claim about supplied test resources");q.receipt.add("resourceFixture",resources);
        JsonObject result=new JsonObject();result.addProperty("machine",machineId);result.addProperty("status","PASS");
        for(String key:new String[]{"productionResult","finalClientSnapshot","resourceFixture","particleTrace","thermalTickTrace","foregroundTrace","obstructionTrace","residualHeatTrace","thermalWaitSamples","gearVisibilityScope","offscreenRestorationTrace"})result.add(key,q.receipt.get(key));
        result.addProperty("largestRenderedPoseDelta",largestRenderedPoseDelta);result.addProperty("maxNativeClouds",maxNativeClouds);
        result.addProperty("defaultUncaughtHandlerStillOwned",Thread.getDefaultUncaughtExceptionHandler()==ownedUncaughtHandler);
        result.add("renderBudget",budgetSnapshot());
        completedCaseReport=result;phase=90;if(!stopped)stopRequested=true;
    }

    void recordWorldCleanup()throws Exception {
        JsonObject state=new JsonObject();
        state.addProperty("actualFMLServerStopped",integratedStopped);
        state.addProperty("entries",((Map<?,?>)field(type(CLIENT),"ENTRIES")).size());
        state.addProperty("inbound",((Queue<?>)field(type(CLIENT),"INBOUND")).size());
        state.addProperty("queued",number(call(field(type(CLIENT),"QUEUED"),"get")));
        state.addProperty("heldMotionMembers",number(field(type(CLIENT),"heldMotionMembers")));
        state.addProperty("heldMotionGroups",number(field(type(CLIENT),"heldMotionGroups")));
        String visibility="com.miaokatze.gtsr.client.machinevisual.MachineVisualVisibility";
        for(String key:new String[]{"CELLS","BUILDS","OUTPUTS","SECTIONS","RESTORATIONS"})state.addProperty("visibility"+key,((Map<?,?>)field(type(visibility),key)).size());
        state.addProperty("cloudRequests",((Map<?,?>)field(type(FX+"CloudParticleQueue"),"REQUESTS")).size());
        state.addProperty("cloudWorldIsNull",field(type(FX+"CloudParticleQueue"),"requestWorld")==null);
        state.addProperty("tcdsCandidates",((Map<?,?>)field(type("com.miaokatze.gtsr.common.machine.tcds.TcdsParticleFx"),"candidates")).size());
        state.addProperty("defaultUncaughtHandlerStillOwned",Thread.getDefaultUncaughtExceptionHandler()==ownedUncaughtHandler);
        q.receipt.add("afterWorldNullLifecycle",state);
        boolean clear=true;
        for(String key:new String[]{"entries","inbound","queued","heldMotionMembers","heldMotionGroups","visibilityCELLS","visibilityBUILDS","visibilityOUTPUTS","visibilitySECTIONS","visibilityRESTORATIONS","cloudRequests","tcdsCandidates"})clear&=state.get(key).getAsLong()==0;
        // Record without throwing: a cleanup failure must still produce a receipt and normal owned shutdown.
        q.record("task03.lifecycle.world-null-clears-owned-state",clear&&state.get("cloudWorldIsNull").getAsBoolean(),state.toString());
        q.record("task03.lifecycle.actual-server-stopped-before-jvm-shutdown",integratedStopped||task==null,"Real FMLServerStoppedEvent observed before invoking Minecraft.shutdown");
    }

    @SubscribeEvent public void render(TickEvent.RenderTickEvent event) {
        if(done||event.phase!=TickEvent.Phase.END||pendingShot==null)return;
        try {
            String name=pendingShot;
            Object m=clientMachine();if(m==null)return;
            Object entry=call(type(CLIENT),"entry",m);
            boolean workShot=name.contains("-working-")||name.endsWith("-resumed");
            boolean offscreenShot=name.endsWith("-offscreen-off");
            int groups=((List<?>)field(call(m,"visualDefinition"),"groups")).size(),expectedMask=(1<<groups)-1;
            long frameNow=number(call(type(CLIENT),"renderFrame"));
            if(!offscreenShot&&number(field(entry,"lastRenderFrame"))!=frameNow)return;
            if(workShot&&(!Boolean.TRUE.equals(call(type(CLIENT),"isAnimating",m))
                ||number(call(type(CLIENT),"uploadedGroupMask",m))!=expectedMask))return;
            long decorativeMs=number(field(entry,"decorativeMs"));
            JsonArray pose=new JsonArray();double[] values=new double[groups];boolean nonzero=false;int index=0;
            boolean targetPose=true;long longestPeriod=0;double targetMetric=1D;
            for(Object group:(List<?>)field(call(m,"visualDefinition"),"groups")) {
                double value=((Number)call(group,"sample",decorativeMs)).doubleValue();values[index++]=value;nonzero|=Math.abs(value)>1E-6;
                String kind=String.valueOf(field(group,"kind"));longestPeriod=Math.max(longestPeriod,number(field(group,"periodMs")));
                if(kind.equals("rotate_y")) {
                    double closeness=1D-Math.abs(Math.cos(2D*value));targetMetric=Math.min(targetMetric,closeness);
                    targetPose&=Math.abs(Math.cos(2D*value))<.105D;
                } else {
                    double travel=Math.abs(((Number)field(group,"travel")).doubleValue());
                    double ratio=travel==0D?1D:Math.abs(value)/travel;targetMetric=Math.min(targetMetric,ratio);targetPose&=ratio>=.9D;
                }
                JsonObject component=new JsonObject();component.addProperty("id",String.valueOf(field(group,"id")));
                component.addProperty("kind",String.valueOf(field(group,"kind")));component.addProperty("value",value);pose.add(component);
            }
            if(workShot&&groups>0&&!nonzero)return;
            if(name.endsWith("-working-2")&&groups>0) {
                if(peakWaitStarted==0)peakWaitStarted=System.nanoTime();
                bestPeakMetric=Math.max(bestPeakMetric,targetMetric);
                if(!targetPose) {
                    if((System.nanoTime()-peakWaitStarted)/1000000L>Math.max(15000L,longestPeriod*3L))
                        throw new AssertionError("Natural decorative clock repeatedly interrupted before target pose; bestPeakMetric="+bestPeakMetric+",decorativeMs="+decorativeMs);
                    return;
                }
            }
            if(workShot&&groups>0) {
                if(firstRenderedPose==null)firstRenderedPose=values.clone();
                else for(int i=0;i<values.length;i++)largestRenderedPoseDelta=Math.max(largestRenderedPoseDelta,Math.abs(values[i]-firstRenderedPose[i]));
            }
            JsonObject readiness=new JsonObject();readiness.addProperty("name",name);readiness.addProperty("renderFrame",frameNow);
            readiness.addProperty("decorativeMs",decorativeMs);readiness.addProperty("uploadedGroupMask",number(call(type(CLIENT),"uploadedGroupMask",m)));
            readiness.add("sameFrameNativeGroupSamples",pose);q.receipt.add("lastRenderReadiness",readiness);
            int error=org.lwjgl.opengl.GL11.glGetError();
            check("GL."+name,error==0,"Actual world render GL error="+error);
            Object framebuffer=call(q.mc,"getFramebuffer|func_147110_a");
            call(type("net.minecraft.util.ScreenShotHelper"),"saveScreenshot|func_148259_a",out.toFile(),name+".png",number(field(q.mc,"displayWidth|field_71443_c")),number(field(q.mc,"displayHeight|field_71440_d")),framebuffer);
            JsonObject row=new JsonObject();row.addProperty("name",name);row.addProperty("clientTick",ticks);
            row.addProperty("renderFrame",frameNow);row.addProperty("lastHostRenderFrame",number(field(entry,"lastRenderFrame")));
            row.addProperty("decorativeMs",decorativeMs);row.addProperty("uploadedGroupMask",number(call(type(CLIENT),"uploadedGroupMask",m)));
            row.addProperty("uploadedHiddenCells",uploadedHiddenCells(m));row.add("sameFrameNativeGroupSamples",pose);
            row.addProperty("sampleScope",offscreenShot?"Actual look-away camera frame; host TESR intentionally absent. Native GPU restoration/retirement ACK and held reservation zero are recorded separately; no synthetic draw.":"Read immediately after this host's real TESR frame; exact same entry clock and group samples used by its animated branch, with uploaded group ACK and isAnimating gates. No proxy invocation or manual phase.");
            if(name.endsWith("-working-2"))row.addProperty("targetPosePolicy","All translations >=90% of declared travel; rotation within approximately3 degrees of45 degrees modulo90; natural clock only");
            row.addProperty("renderCalls",number(field(type(CLIENT),"renderCalls")));row.addProperty("renderedCubes",cubes());
            row.add("renderBudget",budgetSnapshot());
            row.addProperty("cloud",String.valueOf(call(type(FX+"CloudParticleQueue"),"diagnostics")));
            int clouds=nativeClouds(field(field(q.mc,"effectRenderer|field_71452_i"),"fxLayers|field_78876_b"));
            maxNativeClouds=Math.max(maxNativeClouds,clouds);row.addProperty("actualNativeEntityCloudFXAlive",clouds);
            row.addProperty("foreground",String.valueOf(call(type(FX+"WorkEffectRenderer"),"diagnostics")));
            frames.add(row);Files.write(out.resolve(name+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(row).getBytes(StandardCharsets.UTF_8));
            pendingShot=null;
        } catch(Throwable error){finish(error);}
    }

    void finish(Throwable error){if(finishing)return;finishing=true;failure=error;if(error!=null)error.printStackTrace();if(!stopped)stopRequested=true;}
    void stopping(){if(task!=null&&!stopped)try{if(obstaclePlaced)removeObstacle();call(task,"stop",world);}catch(Exception error){q.receipt.addProperty("stopFailure",error.toString());}}
    void stopped(){integratedStopped=true;}
}
