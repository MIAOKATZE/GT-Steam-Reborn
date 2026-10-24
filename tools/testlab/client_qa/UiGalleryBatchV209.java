package audit;

import com.google.gson.*;
import java.util.*;

/** Owned v209 probes only: real client C08 and bounded preview observations. */
final class UiGalleryBatchV209 {
    final UiGalleryBatch b;
    int seed,round,clientTicks,closedRounds;
    volatile int serverTicks;
    long roundStart,c08Before;
    boolean seedPrepared,paged,rendererEarlyReturnChecked,coldArmed;
    volatile Object stableServer;
    Object nativeOutput;
    final JsonArray rounds=new JsonArray();
    UiGalleryBatchV209(UiGalleryBatch batch){b=batch;}
    static Object call(Object t,String n,Object...a)throws Exception{return UiGalleryBatch.call(t,n,a);}
    static Object field(Object t,String n)throws Exception{return UiGalleryBatch.field(t,n);}
    static Class<?> type(String n)throws Exception{return UiGalleryBatch.type(n);}
    void configure()throws Exception {
        if(!UiGalleryBatch.flag(b.row,"v209"))return;
        nativeOutput=null;
        coldArmed=false;
        UiGalleryBatch.set(field(b.q.mc,"gameSettings|field_71474_y"),"fboEnable|field_151448_g",!UiGalleryBatch.flag(b.row,"fboDisabled"));
        // This is the owned sandbox's process-local option, never the user's configuration.
        if(b.row.has("reducedMotion"))UiGalleryBatch.set(type(UiGalleryBatch.G+"config.Config"),"criticalPreviewReducedMotion",UiGalleryBatch.flag(b.row,"reducedMotion"));
        if(UiGalleryBatch.flag(b.row,"reloadResources")){
            call(b.q.mc,"refreshResources|func_110436_a");
            b.q.record("v209.resources.reload",true,"Actual Minecraft.refreshResources on owned client main thread");
            String after=String.valueOf(call(type(UiGalleryBatch.G+"client.critical.CriticalMiniatureRenderer"),"diagnostics"));
            b.q.check("v209.resources.cache-cleared",after.contains("scenes=0,")&&after.contains("targetPixels=0"),"Actual reload listener clears native scenes and private render target: "+after);
        }
    }
    boolean previewReady()throws Exception {
        if(!UiGalleryBatch.flag(b.row,"previewProbe"))return true;
        if(UiGalleryBatch.flag(b.row,"compactBudgetProbe"))return String.valueOf(call(type(UiGalleryBatch.G+"client.critical.CriticalMiniatureRenderer"),"diagnostics",descriptor())).contains("ready=1,");
        String value=String.valueOf(call(type(UiGalleryBatch.G+"client.critical.CriticalMiniatureRenderer"),"diagnostics"));
        return !value.contains("ready=0,")&&!value.contains("faces=0,");
    }
    Object descriptor()throws Exception {
        Object descriptorType=type(UiGalleryBatch.G+"common.critical.CriticalPreviewDescriptor");
        if(b.row.has("showcaseKind")){
            Object kind=Enum.valueOf((Class)type(UiGalleryBatch.G+"common.critical.CriticalMachineKind"),b.row.get("showcaseKind").getAsString());
            Object tier=Enum.valueOf((Class)type(UiGalleryBatch.G+"common.critical.CriticalTier"),"T3");
            return call(descriptorType,"demo",kind,tier);
        }
        Object psm=call(call(b.q.screen,"getSyncManager"),"getMainPSM"),snapshot=call(call(psm,"findSyncHandler","critical.businessSnapshot"),"getValue");
        int[] values=(int[])field(field(snapshot,"construction"),"values");int stage=values[9];
        return call(descriptorType,"construction",call(snapshot,"layout"),call(snapshot,"configuration"),Math.max(0,stage-1),stage,values[11]<=0?0d:(double)values[10]/values[11],values[18]==0||values[16]!=0);
    }
    int metric(String text,String key){java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("(?:^|,)"+key+"=([0-9]+)").matcher(text);return matcher.find()?Integer.parseInt(matcher.group(1)):-1;}
    JsonArray drainRaw()throws Exception {
        JsonArray errors=new JsonArray();Class<?> driver=type("org.lwjgl.opengl.GL11C");
        for(int i=0;i<32;i++){int error=((Number)call(driver,"glGetError")).intValue();if(error==0)return errors;errors.add(new JsonPrimitive(error));}
        throw new AssertionError("Raw GL11C error drain exceeded bounded32 calls");
    }
    void rawCalibration()throws Exception {
        JsonObject result=new JsonObject();b.q.receipt.add("v209RawDriverCalibration",result);
        result.addProperty("scope","One ready SUN, no PNG; GL11C.glGetError is a native LWJGL3 driver entry called through reflection, never GLSM. Each phase independently drains before/after. Production draw phase has no external getter calls.");
        JsonObject baseline=new JsonObject();baseline.add("previousErrorsDrained",drainRaw());baseline.add("afterCleanup",drainRaw());result.add("baseline",baseline);
        if(!UiGalleryBatch.flag(b.row,"skipLegacyGetterCalibration")){
        JsonObject old=new JsonObject();old.add("beforeDrained",drainRaw());JsonArray queries=new JsonArray(),emitted=new JsonArray();Class<?> gl=type("org.lwjgl.opengl.GL11");
        for(int token:new int[]{0x0BA0,0x0BA3,0x0BA4,0x0BA5,0x84E0,0x8CA6,0x0BE1,0x0BE0,0x0B71,0x0BE2,0x0C11,0x0B50,0x0B44,0x0BA2,0x0C10}){
            JsonObject query=new JsonObject();query.addProperty("token",Integer.toHexString(token));
            if(token==0x0BA2||token==0x0C10){java.nio.IntBuffer buf=java.nio.ByteBuffer.allocateDirect(16).order(java.nio.ByteOrder.nativeOrder()).asIntBuffer();call(gl,"glGetIntegerv",token,buf);query.addProperty("method","GL11.glGetIntegerv(int,IntBuffer)");}
            else if(token==0x0B71||token==0x0BE2||token==0x0C11||token==0x0B50||token==0x0B44){query.addProperty("value",String.valueOf(call(gl,"glIsEnabled",token)));query.addProperty("method","GL11.glIsEnabled(int)");}
            else{query.addProperty("value",((Number)call(gl,"glGetInteger",token)).intValue());query.addProperty("method","GL11.glGetInteger(int)");}
            JsonArray errors=drainRaw();query.add("rawErrorsConsumed",errors);for(JsonElement error:errors)emitted.add(error);queries.add(query);
        }
        old.add("queries",queries);old.add("errorsProduced",emitted);old.add("afterFinalDrain",drainRaw());result.add("oldNativeGetterOnly",old);
        }
        JsonObject draw=new JsonObject();JsonObject stateBefore=null;
        if(UiGalleryBatch.flag(b.row,"compareBasicState")){stateBefore=glState();draw.add("logicalStateBefore",stateBefore);draw.add("stateQueryErrorsBefore",drainRaw());}
        draw.add("beforeDrained",drainRaw());
        boolean drawn=Boolean.TRUE.equals(call(type(UiGalleryBatch.G+"client.critical.CriticalMiniatureRenderer"),"draw",descriptor(),0,0,64,48,12d,false));
        JsonArray drawErrors=drainRaw();draw.addProperty("returnedReady",drawn);draw.add("errorsProduced",drawErrors);draw.add("afterFinalDrain",drainRaw());result.add("productionDrawOnly",draw);
        boolean stateMatches=true;
        if(stateBefore!=null){JsonObject stateAfter=glState();draw.add("logicalStateAfter",stateAfter);draw.add("stateQueryErrorsAfter",drainRaw());stateMatches=stateBefore.equals(stateAfter);draw.addProperty("selectedLogicalStateRestored",stateMatches);}
        JsonObject managed=null;
        if(!UiGalleryBatch.flag(b.row,"skipManagedGetterCalibration")){
            managed=new JsonObject();managed.add("beforeDrained",drainRaw());managed.add("state",glState());managed.add("errorsProduced",drainRaw());managed.add("afterFinalDrain",drainRaw());result.add("glsmGetterOnly",managed);
        }
        if(UiGalleryBatch.flag(b.row,"coreScopeCalibration")){
            JsonObject scope=new JsonObject();result.add("coreRenderStateStages",scope);Object instance=null;
            JsonObject ctor=new JsonObject();ctor.add("beforeDrained",drainRaw());
            try{instance=b.construct("com.miaokatze.miaokatzecore.client.preview.PreviewRenderScope$RenderState");}catch(Throwable error){ctor.addProperty("exception",error.toString());}
            ctor.add("errorsProduced",drainRaw());scope.add("constructor",ctor);
            if(instance!=null){
                JsonObject push=new JsonObject();push.add("beforeDrained",drainRaw());
                try{call(instance,"push");}catch(Throwable error){push.addProperty("exception",error.toString());}
                push.add("errorsProduced",drainRaw());scope.add("push",push);
                JsonObject restore=new JsonObject();restore.add("beforeDrained",drainRaw());
                try{call(instance,"restore");}catch(Throwable error){restore.addProperty("exception",error.toString());}
                restore.add("errorsProduced",drainRaw());scope.add("restore",restore);
            }
            scope.add("afterFinalDrain",drainRaw());
        }
        b.q.check("v209.raw.driver-draw-ready-and-clean",drawn&&drawErrors.size()==0,"Ready production draw with no external state queries produced raw driver errors="+drawErrors);
        if(stateBefore!=null)b.q.check("v209.raw.selected-logical-state-restored",stateMatches,"Selected GLSM FBO/viewport/scissor/matrix mode+MV/projection depths/active texture/enables/blend factors restored; the state-query calls occur outside the separately drained draw-only segment");
        if(managed!=null)b.q.check("v209.raw.glsm-getter-clean",managed.getAsJsonArray("errorsProduced").size()==0,"GLSM logical-state getters produced no raw driver errors; texture stack depth excluded");
        b.persist("RAW_CALIBRATION_FINISHED");
    }
    void beforeDraw()throws Exception {
        if(b.row!=null&&UiGalleryBatch.flag(b.row,"fboDisabled"))
            UiGalleryBatch.set(field(b.q.mc,"gameSettings|field_71474_y"),"fboEnable|field_151448_g",false);
        if(b.row!=null&&b.stage==14&&UiGalleryBatch.flag(b.row,"coldFallback")&&!coldArmed){
            call(type(UiGalleryBatch.G+"client.critical.CriticalMiniatureRenderer"),"clear");coldArmed=true;
        }
    }
    void afterDraw()throws Exception {
        if(b.row==null||b.stage!=14||!UiGalleryBatch.flag(b.row,"coldFallback")||!coldArmed)return;
        String state=String.valueOf(call(type(UiGalleryBatch.G+"client.critical.CriticalMiniatureRenderer"),"diagnostics"));
        if(!state.contains("ready=0,")||!state.contains("lists=0,")){
            JsonObject skipped=new JsonObject();skipped.addProperty("name",b.row.get("name").getAsString());skipped.addProperty("status","NOT_RUN");skipped.addProperty("scope","Native compact scene was already ready on its first visible draw; no genuine preparing fallback frame; unsupported hardware path NOT_RUN");skipped.addProperty("diagnostics",state);b.events.add(skipped);b.persist("OPTIONAL_FALLBACK_NOT_RUN");b.index++;b.row=null;b.stage=10;return;
        }
        b.q.check("v209.fallback.actual-cold-frame",state.contains("scenes=1,")&&state.contains("failed=0,"),"After actual NEI DrawScreenEvent.Post, scene remains preparing with no native lists: "+state+". Production draw returns false for !ready; no unsupported hardware claim.");
        b.stableFrames=1;b.renderWaitNanos=System.nanoTime()-b.renderStartNanos;b.capture();b.q.pendingTooltip=null;b.index++;b.row=null;b.stage=10;
    }
    void navigate()throws Exception {
        if(UiGalleryBatch.flag(b.row,"constructionPreviewFixture")){
            Object snapshot=call(type(UiGalleryBatch.G+"common.gui.CriticalBusinessSnapshot"),"empty");
            int[] values=(int[])field(field(snapshot,"construction"),"values");
            values[9]=4;values[10]=35;values[11]=100;values[12]=1;values[16]=7;values[17]=((Enum<?>)Enum.valueOf((Class)type(UiGalleryBatch.G+"common.critical.CriticalTier"),"T3")).ordinal();values[18]=0;
            int[] config=(int[])field(snapshot,"configuration");
            config[0]=((Enum<?>)Enum.valueOf((Class)type(UiGalleryBatch.G+"common.critical.CriticalMachineKind"),"SUN")).ordinal();config[1]=8;config[7]=1;config[9]=2;config[10]=31;
            UiGalleryBatch.set(snapshot,"displayConfiguration",call(snapshot,"decodeConfiguration"));
            ((String[])field(snapshot,"text"))[0]="reason.7";
            Object psm=call(call(b.q.screen,"getSyncManager"),"getMainPSM"),sync=call(psm,"findSyncHandler","critical.businessSnapshot");
            call(sync,"setValue",snapshot,false,false);b.expectedCriticalIdentity="";
            b.q.receipt.addProperty("v209ConstructionFixtureScope","CLIENT_CACHE_ONLY: production builder with paused stage4/35 of100 T3 Sun snapshot, layout2/revision31. No real construction, S2C claim, payment or machine state mutation.");
        }
        if(!b.row.has("showcaseKind"))return;
        Object handler=call(b.q.gui,"getHandler");List<?> recipes=(List<?>)field(handler,"arecipes");
        String expected=b.row.get("showcaseKind").getAsString();int found=-1;
        for(int i=0;i<recipes.size();i++){
            Object info=call(type(UiGalleryBatch.G+"api.recipe.CriticalRecipeMaps"),"displayInfo",field(recipes.get(i),"mRecipe"));
            if(info!=null&&Boolean.TRUE.equals(call(info,"showcase"))&&expected.equals(String.valueOf(field(info,"kind")))){found=i;break;}
        }
        b.q.check("v209.nei.showcase-entry."+expected,found>=0,"Production GT cached recipe operation=showcase, kind="+expected+", original recipe index="+found);
        if("SHOWCASE".equals(UiGalleryBatch.str(b.row,"neiKind",""))){
            Object props=call(call(field(handler,"recipeMap"),"getFrontend"),"getUIProperties");
            List<?> inputs=(List<?>)call(recipes.get(found),"getIngredients"),outputs=(List<?>)call(recipes.get(found),"getOtherStacks");
            b.q.check("v209.nei.native-zero-input-background."+expected,b.q.number(field(props,"maxItemInputs"))==0&&b.q.number(field(props,"maxFluidInputs"))==0&&b.q.number(field(props,"maxItemOutputs"))==1&&inputs.isEmpty()&&outputs.size()==1,"Real GT recipe map UI max inputs0/outputs1 and native cached recipe input/output stacks");
            nativeOutput=field(outputs.get(0),"item");
        }
        call(field(b.q.gui,"handlerPages"),"gotoRecipeIndex",found);call(b.q.gui,"refreshContainer");
        b.q.receipt.addProperty("v209NeiRecipeIndex."+expected,found);
    }
    void startHistory()throws Exception {
        b.previousPanel=b.targetPanel;b.closeClient();b.targetGui=null;b.targetPanel=null;
        b.dispatch(209);b.stage=30;
    }
    long c08(){long n=0;for(Map.Entry<String,java.util.concurrent.atomic.AtomicLong> e:b.q.wire.values.entrySet())if(e.getKey().contains(":C2S:native-C08:packets"))n+=e.getValue().get();return n;}
    boolean client()throws Exception {
        if(b.stage<30||b.stage>34)return false;
        if(System.nanoTime()-b.caseStartNanos>130_000_000_000L)throw new AssertionError("Bounded three-round C08 history deadline");
        if(b.stage==30){if(b.request!=0)return true;b.closeClient();b.actionTick=b.ticks;b.stage=31;return true;}
        if(b.stage==31){
            if(b.ticks<b.actionTick+6)return true;
            Object held=call(b.player(),"getHeldItem|func_70694_bm");
            if(held==null)return true;
            c08Before=c08();
            Object controller=field(b.q.mc,"playerController|field_71442_b");
            call(controller,"sendUseItem|func_78769_a",b.player(),field(b.q.mc,"theWorld|field_71441_e"),held);
            b.dispatch(210);b.stage=32;b.syncStartNanos=System.nanoTime();return true;
        }
        if(b.stage==32){
            if(b.request!=0||!b.screenReady(true))return true;
            b.q.check("v209.history.C08."+round,c08()>c08Before,"Actual client NetworkManager observed C08 from PlayerControllerMP.sendUseItem; physical input/TCP NOT_RUN");
            b.q.check("v209.history.panel."+round,"machine_terminal_history".equals(String.valueOf(call(b.q.panel,"getName"))),"Actual matching client/server history panel/window="+b.windowId);
            roundStart=System.nanoTime();clientTicks=0;serverTicks=0;b.request=211;b.stage=33;return true;
        }
        if(b.stage==33){
            if(!b.screenReady(false))throw new AssertionError("History screen/container changed during 30-second observation");
            clientTicks++;
            if(!paged&&clientTicks>=4){
                Object psm=call(call(b.q.screen,"getSyncManager"),"getMainPSM"),session=call(psm,"findSyncHandler","machine_history");
                b.q.check("v209.history.multiple-pages",b.q.number(call(session,"pages"))>=2,"Seven naturally recorded owned targets");
                click("machine_history_next");
                b.q.check("v209.history.next",b.q.number(field(session,"page"))==1,"Actual local MUI2 hit-test click selects second history page");
                click("machine_history_previous");
                b.q.check("v209.history.previous",b.q.number(field(session,"page"))==0,"Actual local MUI2 hit-test click returns to first page");paged=true;
            }
            if(System.nanoTime()-roundStart<30_000_000_000L)return true;
            JsonObject r=new JsonObject();r.addProperty("round",round+1);r.addProperty("elapsedSeconds",(System.nanoTime()-roundStart)/1e9);r.addProperty("clientTickObservations",clientTicks);r.addProperty("serverTickValidObservations",serverTicks);r.addProperty("windowId",b.windowId);r.addProperty("nativeC08Observed",c08()>c08Before);rounds.add(r);
            b.q.check("v209.history.stable."+round,clientTicks>=500&&serverTicks>=500,"Real 30s client/server tick observation: "+r);
            b.request=0;
            if(++round>=3){b.q.receipt.add("v209HistoryRounds",rounds);b.q.receipt.addProperty("v209HistoryNormalCloseRounds",closedRounds);b.stage=14;b.actionTick=b.ticks;b.renderStartNanos=System.nanoTime();b.caseStartNanos=System.nanoTime();return true;}
            b.closeClient();b.actionTick=b.ticks;b.stage=34;return true;
        }
        if(b.stage==34){
            if(b.ticks<b.actionTick+6)return true;
            Object clientContainer=field(b.player(),"openContainer|field_71070_bA"),serverContainer=field(b.q.serverPlayer,"openContainer|field_71070_bA");
            b.q.check("v209.history.normal-close."+round,clientContainer==field(b.player(),"inventoryContainer|field_71069_bz")&&serverContainer==field(b.q.serverPlayer,"inventoryContainer|field_71069_bz"),"Corresponding server/client history session closed normally; no automatic reopen");
            closedRounds++;b.stage=31;b.actionTick=b.ticks;return true;
        }
        return true;
    }
    void click(String name)throws Exception {
        Object widget=b.q.find(b.q.panel,name);JsonObject proof=b.hitEvidence(widget);
        b.q.check("v209.history.hit."+name,UiGalleryBatch.flag(proof,"targetInHoveredChain"),String.valueOf(proof));b.q.click(widget);
    }
    void server()throws Exception {
        if(b.request==209){
            if(!seedPrepared){b.prepare();seedPrepared=true;}
            if(seed<7){
                Object[] f=b.auxiliary("CRITICAL_SUN","v209.history.sun."+seed);
                if(((Number)call(f[4],"getTimer")).longValue()<2)return;
                call(b.q.serverPlayer,"setPositionAndUpdate|func_70634_a",((Integer)f[1])+.5,((Integer)f[2])+1d,((Integer)f[3])+2.5);
                b.q.check("v209.history.natural-record."+seed,Boolean.TRUE.equals(call(type(UiGalleryBatch.G+"common.gui.CriticalMachineTerminalFactory"),"open",b.q.serverPlayer,f[4])),"Owned real local factory records target; no synthesized history entry/grant");
                b.v208LiveTarget=f;b.currentFixture=f;seed++;return;
            }
            List<?> entries=(List<?>)call(type(UiGalleryBatch.G+"common.terminal.MachineTerminalHistory"),"read",b.q.serverPlayer);
            b.q.check("v209.history.record-count",entries.size()>=7,"Real successful local factories naturally recorded "+entries.size()+" entries");
            b.v208RemoteIndex=0;b.v208HistoryLocalOpened=true;b.request=0;return;
        }
        Object container=field(b.q.serverPlayer,"openContainer|field_71070_bA");
        if(b.request==210){
            if(!type("com.cleanroommc.modularui.screen.ModularContainer").isInstance(container))return;
            Object data=call(container,"getGuiData");if(!data.getClass().getName().endsWith("MachineHistoryFactory$Data"))return;
            b.windowId=b.q.number(field(container,"windowId|field_75152_c"));
            Object psm=call(call(container,"getSyncManager"),"getMainPSM");
            if(!Boolean.TRUE.equals(call(psm,"isInitialised")))return;
            b.expectedValues=b.primitives(psm);b.expectedCriticalIdentity="";stableServer=container;b.request=0;return;
        }
        if(b.request==211){
            if(container!=stableServer||!Boolean.TRUE.equals(call(call(container,"getGuiData"),"valid",b.q.serverPlayer)))throw new AssertionError("History server identity/slot/stack/terminal gate became invalid");
            serverTicks++;
        }
    }
    void beforeCapture()throws Exception {
        if(!UiGalleryBatch.flag(b.row,"v209"))return;
        if(UiGalleryBatch.flag(b.row,"coldFallback")){
            String state=String.valueOf(call(type(UiGalleryBatch.G+"client.critical.CriticalMiniatureRenderer"),"diagnostics"));
            b.q.check("v209.fallback.preparing-before-PNG",state.contains("ready=0,")&&state.contains("lists=0,"),"Capture actual preparation fallback before any second prepare/draw call: "+state);
        }
        if(UiGalleryBatch.flag(b.row,"fboDisabled")){
            boolean supported=Boolean.TRUE.equals(call(type("net.minecraft.client.renderer.OpenGlHelper"),"isFramebufferEnabled|func_148822_b"));
            b.q.check("v209.fallback.actual-disabled-before-PNG",!supported,"Observe actual OpenGlHelper result before screenshot; GameSettings flag alone is insufficient, actualEnabled="+supported);
            Object kind=Enum.valueOf((Class)type(UiGalleryBatch.G+"common.critical.CriticalMachineKind"),b.row.get("showcaseKind").getAsString());
            Object tier=Enum.valueOf((Class)type(UiGalleryBatch.G+"common.critical.CriticalTier"),"T3");
            Object descriptor=call(type(UiGalleryBatch.G+"common.critical.CriticalPreviewDescriptor"),"demo",kind,tier);
            boolean drawn=Boolean.TRUE.equals(call(type(UiGalleryBatch.G+"client.critical.CriticalMiniatureRenderer"),"draw",descriptor,0,0,64,48,12d,false));
            b.q.check("v209.fallback.production-false-before-PNG",!drawn,"Production renderer returns false in the observed disabled path before PNG; manual visible fallback label/diagram still required");
        }
        if(!rendererEarlyReturnChecked){
            Object renderer=b.construct(UiGalleryBatch.G+"client.critical.CriticalControllerRenderer");
            Object anchor=b.construct(UiGalleryBatch.G+"common.critical.TileEntityCriticalRenderAnchor");
            UiGalleryBatch.set(renderer,"renderingController",b.construct(UiGalleryBatch.G+"common.critical.CriticalMachineState"));
            java.lang.reflect.Constructor<?> ctor=type(UiGalleryBatch.G+"client.critical.CriticalLightCache").getDeclaredConstructor();ctor.setAccessible(true);
            UiGalleryBatch.set(renderer,"currentLight",ctor.newInstance());UiGalleryBatch.set(renderer,"activeGroups",new HashSet<String>());
            call(renderer,"renderTileEntityAt|func_147500_a",anchor,0d,0d,0d,0f);
            b.q.check("v209.world-renderer.early-return-cleanup",field(renderer,"renderingController")==null&&field(renderer,"currentLight")==null&&field(renderer,"activeGroups")==null,"Real invisible/no-installation anchor early return clears all three preseeded transient references");rendererEarlyReturnChecked=true;
        }
        if(b.row.has("previewProbe")){
            Object renderer=type(UiGalleryBatch.G+"client.critical.CriticalMiniatureRenderer");
            Object state=call(renderer,"diagnostics");
            b.q.receipt.add("v209Preview."+b.row.get("name").getAsString(),new Gson().toJsonTree(state));
            b.q.check("v209.preview.native-ready."+b.row.get("name").getAsString(),previewReady()&&String.valueOf(state).contains("failures=0,")&&String.valueOf(state).contains("failed=0,"),"Actual native preview diagnostics: "+state);
            if(UiGalleryBatch.flag(b.row,"compactBudgetProbe")){
                String scoped=String.valueOf(call(renderer,"diagnostics",descriptor()));int faces=metric(scoped,"faces"),lists=metric(scoped,"lists");
                b.q.receipt.addProperty("v209CompactModel."+b.row.get("name").getAsString(),scoped);
                b.q.check("v209.compact.budget."+b.row.get("name").getAsString(),faces>0&&faces<=2000&&lists>0&&lists<=28,"Per-descriptor native model, not aggregate cache: "+scoped+"; cap2000faces/28lists");
            }
        }
        if(UiGalleryBatch.flag(b.row,"constructionPreviewFixture")){
            Object psm=call(call(b.q.screen,"getSyncManager"),"getMainPSM"),snapshot=call(call(psm,"findSyncHandler","critical.businessSnapshot"),"getValue");
            int[] values=(int[])field(field(snapshot,"construction"),"values");
            b.q.check("v209.construction.paused-state-retained",values[9]==4&&values[10]==35&&values[11]==100&&values[16]==7&&values[18]==0,"Read-only displayed stage/progress/paused fixture unchanged across real rendering frames; not a production construction tick claim");
        }
    }
    JsonObject glState()throws Exception {
        Class<?> gl=type("com.gtnewhorizons.angelica.glsm.GLStateManager");JsonObject out=new JsonObject();
        // Query the same fixed-function emulation used by transformed production calls.
        // Texture-stack-depth has no GLSM getter and is deliberately outside this basic witness.
        for(int token:new int[]{0x0BA0,0x0BA3,0x0BA4,0x84E0,0x8CA6,0x0BE1,0x0BE0})out.addProperty(Integer.toHexString(token),((Number)call(gl,"glGetInteger",token)).intValue());
        for(int token:new int[]{0x0B71,0x0BE2,0x0C11,0x0B50,0x0B44})out.addProperty(Integer.toHexString(token),Boolean.TRUE.equals(call(gl,"glIsEnabled",token)));
        for(int token:new int[]{0x0BA2,0x0C10}){
            java.nio.IntBuffer buf=java.nio.ByteBuffer.allocateDirect(16).order(java.nio.ByteOrder.nativeOrder()).asIntBuffer();call(gl,"glGetInteger",token,buf);JsonArray a=new JsonArray();for(int i=0;i<4;i++)a.add(new JsonPrimitive(buf.get(i)));out.add(Integer.toHexString(token),a);
        }
        return out;
    }
    void afterCapture(JsonObject event)throws Exception {
        if(!UiGalleryBatch.flag(b.row,"v209"))return;
        if(UiGalleryBatch.flag(b.row,"coldFallback")){event.addProperty("fallbackScope","Actual first visible NEI frame after cold clear; ready0/native lists0 implies production preparing return-false branch. Diagram and simplified label require manual PNG inspection. Unsupported hardware remains NOT_RUN.");return;}
        if(b.row.has("showcaseKind")){
            Object kind=Enum.valueOf((Class)type(UiGalleryBatch.G+"common.critical.CriticalMachineKind"),b.row.get("showcaseKind").getAsString());
            Object tier=Enum.valueOf((Class)type(UiGalleryBatch.G+"common.critical.CriticalTier"),"T3");
            Object descriptor=call(type(UiGalleryBatch.G+"common.critical.CriticalPreviewDescriptor"),"demo",kind,tier);
            Class<?> glsm=type("com.gtnewhorizons.angelica.glsm.GLStateManager");
            b.q.check("v209.witness.pre-existing-error."+b.row.get("name").getAsString(),((Number)call(glsm,"glGetError")).intValue()==0,"No GL error between normal PNG capture and witness");
            if(UiGalleryBatch.flag(b.row,"nativeLegacyProbe")){
                JsonArray nativeQueries=new JsonArray();Class<?> raw=type("org.lwjgl.opengl.GL11");
                for(int token:new int[]{0x0BA0,0x0BA3,0x0BA4,0x0BA5,0x0B50}){
                    JsonObject query=new JsonObject();query.addProperty("token",Integer.toHexString(token));
                    query.addProperty("value",String.valueOf(call(raw,token==0x0B50?"glIsEnabled":"glGetInteger",token)));
                    query.addProperty("errorConsumedImmediately",((Number)call(glsm,"glGetError")).intValue());nativeQueries.add(query);
                }
                b.q.receipt.add("v209RawLegacyQueryCalibration",nativeQueries);
                b.q.receipt.addProperty("v209RawLegacyQueryScope","Diagnostic replay of the former helper-only raw legacy queries. Per-query errors consumed immediately; unsupported native FFP enums are excluded from the GLSM state witness. No production draw occurs in this calibration.");
            }
            JsonObject before=glState();
            b.q.check("v209.witness.query-error-before-draw."+b.row.get("name").getAsString(),((Number)call(glsm,"glGetError")).intValue()==0,"GLSM state-query phase itself emits no GL error; emulated modelview/projection state, no unsupported native legacy enums");
            boolean drawn=Boolean.TRUE.equals(call(type(UiGalleryBatch.G+"client.critical.CriticalMiniatureRenderer"),"draw",descriptor,0,0,64,48,12d,UiGalleryBatch.flag(b.row,"reducedMotion")));
            int drawError=((Number)call(glsm,"glGetError")).intValue();
            b.q.check("v209.witness.draw-error-before-queries."+b.row.get("name").getAsString(),drawError==0,"Production isolated draw phase GL error="+drawError);
            JsonObject after=glState();
            event.add("v209PreviewGlBefore",before);event.add("v209PreviewGlAfter",after);
            boolean fallback=UiGalleryBatch.flag(b.row,"fboDisabled");
            b.q.check("v209.preview.ready-and-state."+b.row.get("name").getAsString(),(fallback?!drawn:drawn)&&before.equals(after),"Real production draw returns expected readiness (fallback="+fallback+"); FBO/viewport/scissor/matrix stacks/mode/active texture/enables/blend factors preserved after screenshot");
            if(fallback)b.q.check("v209.preview.fbo-disabled-native-path",!Boolean.TRUE.equals(call(type("net.minecraft.client.renderer.OpenGlHelper"),"isFramebufferEnabled|func_148822_b")),"Actual OpenGlHelper FBO-disabled branch; 2D schematic must be visible in PNG");
            int error=((Number)call(glsm,"glGetError")).intValue();b.q.check("v209.preview.probe-gl-error."+b.row.get("name").getAsString(),error==0,"GLSM query phase after production draw GL error="+error);
            if(UiGalleryBatch.flag(b.row,"reducedMotion")||UiGalleryBatch.flag(b.row,"reducedMotionProbe")){
                String first=String.valueOf(call(type(UiGalleryBatch.G+"client.critical.CriticalMiniatureRenderer"),"diagnostics"));
                if(UiGalleryBatch.flag(b.row,"reducedMotionProbe"))call(type(UiGalleryBatch.G+"client.critical.CriticalMiniatureRenderer"),"draw",descriptor,0,0,64,48,12d,true);
                first=String.valueOf(call(type(UiGalleryBatch.G+"client.critical.CriticalMiniatureRenderer"),"diagnostics"));
                call(type(UiGalleryBatch.G+"client.critical.CriticalMiniatureRenderer"),"draw",descriptor,0,0,64,48,13d,true);
                String second=String.valueOf(call(type(UiGalleryBatch.G+"client.critical.CriticalMiniatureRenderer"),"diagnostics"));
                b.q.check("v209.preview.reduced-motion-frozen",first.equals(second),"Advancing explicit visual input by one second while reducedMotion=true preserves scene motion diagnostics");
            }
        }
        if(UiGalleryBatch.flag(b.row,"nativeOutputQuery")){
            b.q.check("v209.nei.native-output-query.open",nativeOutput!=null&&Boolean.TRUE.equals(call(type("codechicken.nei.recipe.GuiCraftingRecipe"),"openRecipeGui","item",new Object[]{nativeOutput})),"Actual public NEI output-item recipe query using real cached controller output; synthetic API request, physical R key NOT_RUN");
            Object gui=field(b.q.mc,"currentScreen|field_71462_r");boolean found=false;
            for(Object h:(List<?>)call(gui,"getCurrentRecipeHandlers"))if(type("gregtech.nei.GTNEIDefaultHandler").isInstance(h)&&"gtsr.recipe.critical.showcase".equals(String.valueOf(field(call(h,"getRecipeMap"),"unlocalizedName"))))found=true;
            b.q.check("v209.nei.native-output-query.handler",found,"Actual native output query includes the registered independent showcase handler");
        }
    }
}
