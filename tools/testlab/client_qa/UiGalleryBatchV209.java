package audit;

import com.google.gson.*;
import java.util.*;

/** Retained owned v209 history probes; obsolete rendering probes were removed. */
final class UiGalleryBatchV209 {
    final UiGalleryBatch b;
    int seed,round,clientTicks,closedRounds;
    volatile int serverTicks;
    long roundStart,c08Before;
    boolean seedPrepared,paged;
    volatile Object stableServer;
    final JsonArray rounds=new JsonArray();
    UiGalleryBatchV209(UiGalleryBatch batch){b=batch;}
    static Object call(Object t,String n,Object...a)throws Exception{return UiGalleryBatch.call(t,n,a);}
    static Object field(Object t,String n)throws Exception{return UiGalleryBatch.field(t,n);}
    static Class<?> type(String n)throws Exception{return UiGalleryBatch.type(n);}
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
}
