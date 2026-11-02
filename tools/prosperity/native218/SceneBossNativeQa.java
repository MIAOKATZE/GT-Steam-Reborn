package audit;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.*;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.*;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

/** Owned native fixture only. Production membership, packets, cache and rendering run normally. */
public final class SceneBossNativeQa {
    static final String P="com.miaokatze.gtsr.", R=P+"common.dimension.prosperity.remaster.";
    final Mui2ClientQa q;
    final Path out;
    final JsonArray clients=new JsonArray(), stages=new JsonArray(), fixtures=new JsonArray();
    final AtomicLong packetSequence=new AtomicLong();
    volatile JsonObject lastPacket,lastServer;
    volatile Throwable serverFailure;
    volatile Object world,player;
    final Object[] sites=new Object[2];
    final List<int[]> chunks=new ArrayList<int[]>();
    volatile boolean requested,ready,pauseRequested,pauseEntered,pauseFinished;
    volatile int action,ack,serverTicks,chunkCursor;
    Object migrationTile;
    byte[] migrationInventoryBefore;
    int migrationPhase,migrationSeedTick,migrationExpectedTier;
    int ticks,phase,step,stepTick,overlayFrames,insideMissing,insideSamples,pauseClientTicks,stepStartPlayerTicks;
    long stepStartSequence;
    final boolean expectStablePause=Boolean.getBoolean("gtsr.native218.expectStablePause");
    boolean pauseShot;
    long started=System.currentTimeMillis();
    static final String[] LABELS={"factory-inside","factory-above-membership","factory-return","factory-edge-inside","factory-outside","factory-reenter","foundry-inside","foundry-server-pause"};
    static final int[] DURATIONS={160,100,100,100,80,120,160,180};
    SceneBossNativeQa(Mui2ClientQa owner){
        q=owner;out=Paths.get(System.getProperty("gtsr.uiqa.receipt")).getParent();
        q.receipt.addProperty("scope","Owned full GL client + integrated server on a private desktop. Two production compact-prefab scenes placed by RemasterWorldgen.placeChunk in the real Prosperity WorldServer. Normal membership/EncounterProgress/network/cache/HUD remain unmodified. The final bounded server-thread pause is an explicit slow-TPS reproduction, separate from ordinary standing.");
        q.receipt.addProperty("inputType","Synthetic owned world launch and server-side teleports; physical keyboard/mouse NOT_RUN");
        q.receipt.addProperty("slowServerExpectation",expectStablePause?"Scene owner remains until actual exit or identity change":"Baseline reproduction: scene owner expires after 60 client ticks without a new heartbeat");
        q.receipt.add("nativeSceneFixtures",fixtures);q.receipt.add("sceneStages",stages);q.receipt.add("clientSamples",clients);
    }
    static Object call(Object t,String n,Object...a)throws Exception{return Mui2ClientQa.call(t,n,a);}
    static Object field(Object t,String n)throws Exception{return Mui2ClientQa.field(t,n);}
    static void set(Object t,String n,Object v)throws Exception{Mui2ClientQa.set(t,n,v);}
    static Class<?> type(String n)throws Exception{return Mui2ClientQa.type(n);}
    static int n(Object x){return ((Number)x).intValue();}
    synchronized void event(String name,JsonObject row)throws Exception{row.addProperty("event",name);row.addProperty("elapsedMillis",System.currentTimeMillis()-started);Files.write(out.resolve("native-events.jsonl"),(row.toString()+"\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    void observePacket(Object payload)throws Exception{
        io.netty.buffer.ByteBuf bytes=payload instanceof byte[]?io.netty.buffer.Unpooled.wrappedBuffer((byte[])payload):(io.netty.buffer.ByteBuf)payload;
        try{JsonObject p=SceneBossHudProbe.packet(bytes);p.addProperty("sequence",packetSequence.incrementAndGet());p.addProperty("receivedMillis",System.currentTimeMillis());p.addProperty("clientTick",ticks);if(p.get("discriminator").getAsInt()==1)lastPacket=p;event("actual-S2C-gtsr_encounter",p);}
        finally{if(payload instanceof byte[])bytes.release();}
    }
    void boot()throws Exception{
        Object settings=field(q.mc,"gameSettings|field_71474_y");set(settings,"pauseOnLostFocus|field_82881_y",false);set(settings,"hideGUI|field_74319_N",false);
        Object ws=type("net.minecraft.world.WorldSettings").getConstructor(long.class,type("net.minecraft.world.WorldSettings$GameType"),boolean.class,boolean.class,type("net.minecraft.world.WorldType")).newInstance(21820261103L,Enum.valueOf((Class)type("net.minecraft.world.WorldSettings$GameType"),"CREATIVE"),false,false,field(type("net.minecraft.world.WorldType"),"FLAT|field_77138_c"));
        call(ws,"enableCommands|func_77159_a");call(q.mc,"launchIntegratedServer|func_71371_a",System.getProperty("gtsr.uiqa.world"),"Owned Prosperity HUD v218",ws);
    }
    void initialize(Object server,Object p)throws Exception{
        int dim=n(field(type(P+"config.Config"),"prosperityDimId"));
        world=call(type("net.minecraftforge.common.DimensionManager"),"getWorld",dim);if(world==null){call(type("net.minecraftforge.common.DimensionManager"),"initDimension",dim);world=call(type("net.minecraftforge.common.DimensionManager"),"getWorld",dim);}
        q.check("native.real-prosperity-world",field(world,"provider|field_73011_w").getClass().getName().equals(P+"common.dimension.prosperity.WorldProviderProsperityRuins"),"Actual registered WorldServer provider, dimension="+dim);
        q.check("native.reused-player-retains-registered-world",n(field(p,"dimension|field_71093_bK"))!=dim||field(p,"worldObj|field_70170_p")==world,"Reusing an already loaded DimensionManager world; never reinitialize an active player world");
        Object rules=call(world,"getGameRules|func_82736_K");call(rules,"setOrCreateGameRule|func_82764_b","doMobSpawning","false");call(rules,"setOrCreateGameRule|func_82764_b","doDaylightCycle","false");call(world,"setWorldTime|func_72877_b",6000L);
        Object data=call(type(R+"RemasterData"),"get",world);long seed=((Number)call(world,"getSeed|func_72905_C")).longValue();
        for(int i=0;i<2;i++){
            sites[i]=type(R+"RemasterSite").getConstructor(String.class,int.class,long.class,int.class,int.class,int.class,String.class).newInstance(i==0?"subsided_factory":"fallen_foundry",0,seed,4096+i*256,96,4096,"compact-prefab");
            call(data,"register",sites[i]);int minX=n(call(sites[i],"minX")),maxX=n(call(sites[i],"maxX")),minZ=n(call(sites[i],"minZ")),maxZ=n(call(sites[i],"maxZ"));
            for(int cx=minX>>4;cx<=maxX>>4;cx++)for(int cz=minZ>>4;cz<=maxZ>>4;cz++)chunks.add(new int[]{i,cx,cz});
            JsonObject row=new JsonObject();row.addProperty("site",String.valueOf(call(sites[i],"id")));row.addProperty("minX",minX);row.addProperty("maxX",maxX);row.addProperty("minZ",minZ);row.addProperty("maxZ",maxZ);row.addProperty("entryX",n(call(sites[i],"entryX")));row.addProperty("entryY",n(call(sites[i],"entryY")));row.addProperty("entryZ",n(call(sites[i],"entryZ")));fixtures.add(row);
        }
        Object caps=field(p,"capabilities|field_71075_bZ");set(caps,"isFlying|field_75100_b",true);call(p,"sendPlayerAbilities|func_71016_p");
        q.receipt.addProperty("productionCodeSource",type(R+"RemasterWorldgen").getProtectionDomain().getCodeSource().getLocation().toString());q.receipt.addProperty("geometryOwnerChunksRequested",chunks.size());
        event("fixture-registered",new JsonObject());
    }
    void placeNext()throws Exception{
        int[] c=chunks.get(chunkCursor++);call(call(world,"getChunkProvider|func_72863_F"),"loadChunk|func_73158_c",c[1],c[2]);call(type(R+"RemasterWorldgen"),"placeChunk",world,sites[c[0]],c[1],c[2]);
        if(chunkCursor%16==0){JsonObject r=new JsonObject();r.addProperty("placedChunks",chunkCursor);r.addProperty("totalChunks",chunks.size());event("fixture-geometry-progress",r);}
        if(chunkCursor==chunks.size()){
            Object data=call(type(R+"RemasterData"),"get",world);int generated=0,authored=0,missing=0;JsonArray empty=new JsonArray();
            for(int[] cell:chunks){Object site=sites[cell[0]];int sx=n(field(site,"x")),sz=n(field(site,"z"));if((sx&15)!=0||(sz&15)!=0)throw new AssertionError("This fixture requires aligned anchors");Object plan=call(site,"plan");boolean hasRuns=!((List<?>)call(plan,"slice",cell[1]-(sx>>4),cell[2]-(sz>>4))).isEmpty();boolean geom=Boolean.TRUE.equals(call(data,"flag",String.valueOf(call(site,"id")),"geom:"+cell[1]+":"+cell[2]));if(geom)generated++;if(hasRuns){authored++;if(!geom)missing++;}else{JsonObject row=new JsonObject();row.addProperty("site",String.valueOf(call(site,"id")));row.addProperty("cx",cell[1]);row.addProperty("cz",cell[2]);row.addProperty("actualProductionSliceRunCount",0);row.addProperty("geom",geom);empty.add(row);}}
            q.receipt.add("emptyDescriptorCornerChunks",empty);q.check("native.production-geometry-all-authored-chunks",missing==0&&generated==authored,"Actual production RemasterPrefab.slice: "+authored+" authored chunks, "+empty.size()+" empty descriptor corners; persisted geom="+generated+", missing authored="+missing);
            q.receipt.addProperty("geometryOwnerChunksGenerated",generated);if(Boolean.getBoolean("gtsr.native218.probeLegacyChest"))seedLegacyChest();ready=true;event("fixture-geometry-ready",new JsonObject());
        }
    }
    Object chestNbt()throws Exception{Object tag=type("net.minecraft.nbt.NBTTagCompound").newInstance();call(migrationTile,"writeToNBT|func_145841_b",tag);return tag;}
    byte[] inventoryBytes()throws Exception{Object list=type("net.minecraft.nbt.NBTTagList").newInstance();call(field(player,"inventory|field_71071_by"),"writeToNBT|func_70442_a",list);Object root=type("net.minecraft.nbt.NBTTagCompound").newInstance();call(root,"setTag|func_74782_a","Inventory",list);java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();call(type("net.minecraft.nbt.CompressedStreamTools"),"write|func_74800_a",root,new java.io.DataOutputStream(bytes));return bytes.toByteArray();}
    void seedLegacyChest()throws Exception{
        Object site=sites[0];String id=String.valueOf(call(site,"id"));
        for(Object raw:(List<?>)call(type(R+"RemasterRuntime"),"nodes",site)){
            JsonObject node=(JsonObject)raw;if(!node.has("role")||!"chest".equals(node.get("role").getAsString())||!node.has("unlockMode")||!"direct".equals(node.get("unlockMode").getAsString()))continue;
            int[] at=(int[])call(type(R+"RemasterRuntime"),"nodePosition",world,site,node);Object tile=call(world,"getTileEntity|func_147438_o",at[0],at[1],at[2]);
            if(tile==null||!tile.getClass().getName().equals(P+"common.dimension.prosperity.encounter.TileEntitySealedChest"))continue;
            migrationTile=tile;migrationExpectedTier=n(call(type(R+"RemasterRuntime"),"resolveChestTier",tile));
            q.check("native.loot.actual-direct-node-identity",migrationExpectedTier>=1&&migrationExpectedTier<=3&&Boolean.TRUE.equals(call(tile,"isRemasterNode",id,node.get("id").getAsString()))&&call(world,"getBlock|func_147439_a",at[0],at[1],at[2])==field(type(P+"common.dimension.prosperity.encounter.ForgottenLakeEncounterRegistry"),"sealedChest")&&n(call(tile,"getOpeningTicks"))<0,"Actual registered site/node, live sealed TE and unopened direct chest; expected tier="+migrationExpectedTier);
            migrationInventoryBefore=inventoryBytes();Object legacy=chestNbt();call(legacy,"setInteger|func_74768_a","tier",5);call(legacy,"setInteger|func_74768_a","lootContract",1);call(tile,"readFromNBT|func_145839_a",legacy);
            JsonObject row=new JsonObject();row.addProperty("site",id);row.add("node",node);row.addProperty("x",at[0]);row.addProperty("y",at[1]);row.addProperty("z",at[2]);row.addProperty("seededLegacyTier",n(call(tile,"getTier")));row.addProperty("seededLootContract",n(call(chestNbt(),"getInteger|func_74762_e","lootContract")));row.addProperty("expectedProductionTier",migrationExpectedTier);row.addProperty("scope","Only tier/lootContract changed through old NBT on the actual authored unopened TE; next ordinary WorldServer tick performs migration, no direct updateEntity or private migration call");q.receipt.add("nativeLegacyChestMigration",row);event("native-legacy-chest-seeded",row);migrationSeedTick=serverTicks;migrationPhase=1;return;
        }
        throw new AssertionError("No live authored direct sealed chest for bounded migration probe");
    }
    void observeMigration()throws Exception{
        if(migrationPhase==0||migrationPhase==3||serverTicks<=migrationSeedTick)return;
        if(call(world,"getTileEntity|func_147438_o",n(field(migrationTile,"xCoord|field_145851_c")),n(field(migrationTile,"yCoord|field_145848_d")),n(field(migrationTile,"zCoord|field_145849_e")))!=migrationTile||call(type("net.minecraftforge.common.DimensionManager"),"getWorld",n(field(field(world,"provider|field_73011_w"),"dimensionId|field_76574_g")))!=world)throw new AssertionError("Migration must continue on the actual registered world's live TE");
        Object tag=chestNbt();int tier=n(call(migrationTile,"getTier")),contract=n(call(tag,"getInteger|func_74762_e","lootContract"));
        if(migrationPhase==1){q.check("native.loot.ordinary-server-tick-migrates",tier==migrationExpectedTier&&tier<=3&&contract==2,"Live real TE after ordinary WorldServer tick: tier="+tier+", contract="+contract);call(migrationTile,"readFromNBT|func_145839_a",call(tag,"copy|func_74737_b"));migrationPhase=2;migrationSeedTick=serverTicks;JsonObject row=new JsonObject();row.addProperty("tier",tier);row.addProperty("lootContract",contract);row.addProperty("savedAndReadBack",true);event("native-legacy-chest-migrated-and-read-back",row);return;}
        if(serverTicks<migrationSeedTick+5)return;q.check("native.loot.saved-readback-and-natural-ticks-stable",tier==migrationExpectedTier&&contract==2&&n(call(migrationTile,"getOpeningTicks"))<0,"Same registered TE after write/read and five further ordinary ticks; no re-roll or opening");byte[] after=inventoryBytes();q.check("native.loot.existing-player-inventory-bytes-unchanged",Arrays.equals(migrationInventoryBefore,after),"Actual InventoryPlayer.writeToNBT + native CompressedStreamTools binary encoding, before/after bounded migration; bytes="+after.length);q.receipt.getAsJsonObject("nativeLegacyChestMigration").addProperty("completedNaturalServerTick",serverTicks);migrationPhase=3;
    }
    void move(int index)throws Exception{
        Object s=sites[index>=6?1:0];int sx=n(field(s,"x")),sy=n(field(s,"y")),sz=n(field(s,"z"));
        double x=sx+(index>=6?56.5:84.5),y=sy+(index>=6?1:-18),z=sz+(index>=6?61.5:68.5);
        if(index==1){Object d=call(type(R+"RemasterCatalog"),"descriptor",String.valueOf(field(s,"prefab")),0);y=sy+((JsonObject)d).getAsJsonArray("max").get(1).getAsInt()+3;}
        if(index==3)x=n(call(s,"minX"))+.5;
        if(index==4)x=n(call(s,"minX"))-2.5;
        call(type(P+"common.dimension.testlab.TestLabCommand"),"transfer",player,world,x,y,z,90f,0f);
        Object caps=field(player,"capabilities|field_71075_bZ");set(caps,"isFlying|field_75100_b",true);call(player,"sendPlayerAbilities|func_71016_p");
        JsonObject row=SceneBossHudProbe.server(player);row.addProperty("stage",LABELS[index]);Object blocks=field(type("net.minecraft.block.Block"),"blockRegistry|field_149771_c");for(int dy=-1;dy<=1;dy++){Object block=call(world,"getBlock|func_147439_a",(int)Math.floor(x),(int)Math.floor(y)+dy,(int)Math.floor(z));row.addProperty("actualBlockDy"+dy,String.valueOf(call(blocks,"getNameForObject|func_148750_c",block)));}event("native-server-teleport",row);
    }
    @SubscribeEvent public void server(TickEvent.ServerTickEvent e){
        if(q.done||e.phase!=TickEvent.Phase.END||!requested)return;
        try{
            Object server=FMLCommonHandler.instance().getMinecraftServerInstance();List<?> ps=(List<?>)field(call(server,"getConfigurationManager|func_71203_ab"),"playerEntityList|field_72404_b");if(ps.isEmpty())return;player=ps.get(0);q.serverPlayer=player;serverTicks++;
            if(world==null){initialize(server,player);return;}
            if(!ready){placeNext();return;}
            int dim=n(field(field(world,"provider|field_73011_w"),"dimensionId|field_76574_g"));if(call(type("net.minecraftforge.common.DimensionManager"),"getWorld",dim)!=world||(n(field(player,"dimension|field_71093_bK"))==dim&&field(player,"worldObj|field_70170_p")!=world))throw new AssertionError("Registered WorldServer/player identity changed during the owned scene probe");
            observeMigration();
            if(action!=0){int a=action;move(a-1);action=0;ack=a;}
            if(serverTicks%10==0){JsonObject row=SceneBossHudProbe.server(player);row.addProperty("serverQaTick",serverTicks);lastServer=row;event("native-server-snapshot",row);}
            if(pauseRequested){pauseRequested=false;pauseEntered=true;JsonObject row=SceneBossHudProbe.server(player);row.addProperty("pauseMillis",4500);event("controlled-server-thread-pause-enter",row);Thread.sleep(4500);pauseFinished=true;event("controlled-server-thread-pause-exit",SceneBossHudProbe.server(player));}
        }catch(Throwable error){serverFailure=error;error.printStackTrace();}
    }
    JsonObject sample()throws Exception{
        JsonObject r=SceneBossHudProbe.client();r.addProperty("clientQaTick",ticks);r.addProperty("stage",LABELS[Math.min(step,LABELS.length-1)]);r.addProperty("stageTick",stepTick);r.addProperty("serverQaTick",serverTicks);r.addProperty("overlayAllFrames",overlayFrames);r.addProperty("pauseEntered",pauseEntered);r.addProperty("pauseFinished",pauseFinished);r.addProperty("nowMillis",System.currentTimeMillis());if(lastServer!=null)r.add("lastServer",lastServer);if(lastPacket!=null)r.add("lastPacket",lastPacket);clients.add(r);return r;
    }
    void nextStep()throws Exception{stepTick=0;insideMissing=0;insideSamples=0;stepStartPlayerTicks=n(field(player,"ticksExisted|field_70173_aa"));stepStartSequence=packetSequence.get();action=step+1;ack=0;phase=3;q.phase=300+step;}
    @SubscribeEvent public void client(TickEvent.ClientTickEvent e){
        if(q.done||e.phase!=TickEvent.Phase.END||++ticks<40)return;
        try{
            q.mc=call(type("net.minecraft.client.Minecraft"),"getMinecraft|func_71410_x");if(serverFailure!=null)throw new RuntimeException("Owned server fixture",serverFailure);
            if(System.currentTimeMillis()-started>Long.getLong("gtsr.uiqa.timeoutMillis",840000))throw new AssertionError("Native scene timeout phase="+phase+" chunk="+chunkCursor);
            if(phase==0){boot();phase=1;return;}
            Object cp=field(q.mc,"thePlayer|field_71439_g");if(cp==null)return;
            if(phase==1){if(n(field(cp,"ticksExisted|field_70173_aa"))<60)return;q.wire=UiQaWireCounter.install(q,cp);requested=true;phase=2;return;}
            if(phase==2){if(!ready)return;nextStep();return;}
            if(ack!=step+1)return;stepTick++;
            JsonObject c=null;if(stepTick%5==0)c=sample();
            if(step<7&&stepTick>=40&&step!=1&&step!=4){if(c==null)c=SceneBossHudProbe.client();insideSamples++;if(!c.get("hasSignal").getAsBoolean())insideMissing++;}
            if(step==7){
                if(stepTick==20)pauseRequested=true;
                if(pauseEntered&&!pauseFinished)pauseClientTicks++;
                if(pauseEntered&&!pauseFinished&&!pauseShot&&!SceneBossHudProbe.client().get("hasSignal").getAsBoolean()){pauseShot=true;q.shot("foundry-controlled-slow-server-cache-expired");event("client-cache-expired-during-server-pause",sample());}
                if(pauseEntered&&!pauseFinished&&pauseClientTicks==70&&SceneBossHudProbe.client().get("hasSignal").getAsBoolean())q.shot("foundry-controlled-slow-server-owner-retained");
                if(stepTick==140)q.shot("foundry-after-server-resumed");
            }else if(stepTick==(step==4?50:80))q.shot(LABELS[step]);
            if(stepTick<DURATIONS[step]||q.pendingShot!=null)return;
            JsonObject result=sample();result.addProperty("ordinaryInsideSamples",insideSamples);result.addProperty("ordinaryInsideMissingSamples",insideMissing);stages.add(result);
            String expectedSite=String.valueOf(call(sites[step>=6?1:0],"id"));int expectedKind=step==1||step==4?0:step>=6?3:2;int naturalAdvance=n(field(player,"ticksExisted|field_70173_aa"))-stepStartPlayerTicks;boolean packetMatches=lastPacket!=null&&lastPacket.has("valid")&&lastPacket.get("valid").getAsBoolean()&&lastPacket.get("sequence").getAsLong()>stepStartSequence&&lastPacket.get("kind").getAsInt()==expectedKind&&(expectedKind==0||expectedSite.equals(lastPacket.get("site").getAsString()));result.addProperty("naturalPlayerTickAdvance",naturalAdvance);result.addProperty("actualNewPacketMatchesStage",packetMatches);q.check("native."+LABELS[step]+".live-authority",naturalAdvance>0&&packetMatches,"Actual registered server player natural tick advance="+naturalAdvance+", a new valid S2C scene packet matches stage="+packetMatches);
            if(step!=1&&step!=4&&step!=7){boolean matching=result.get("hasSignal").getAsBoolean()&&result.get("kind").getAsInt()==(step>=6?3:2)&&String.valueOf(call(sites[step>=6?1:0],"id")).equals(result.get("site").getAsString());q.record("native."+LABELS[step]+".scene-heartbeats",insideMissing==0&&matching,"Normal native standing after settle, missing samples="+insideMissing+"/"+insideSamples+", final scene identity matches="+matching);}
            if(step==1||step==4)q.record("native."+LABELS[step]+".explicit-exit-clears",!result.get("hasSignal").getAsBoolean(),"Actual production membership exit sends kind0 and clears the scene owner");
            if(step==7){q.record("native.controlled-slow-server."+(expectStablePause?"owner-retained":"observed-cache-expiry"),pauseClientTicks>60&&(expectStablePause?!pauseShot:pauseShot),"Explicit 4500ms pause of the owned integrated-server thread; actual client ticks during pause="+pauseClientTicks+", client clock unmodified");q.record("native.controlled-slow-server.reacquired",pauseFinished&&result.get("hasSignal").getAsBoolean(),"Normal server ticks resumed; actual scene packet/cache reacquired");q.finish(null);return;}
            step++;nextStep();
        }catch(Throwable error){q.finish(error);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void overlay(RenderGameOverlayEvent.Post e){if(e.type==RenderGameOverlayEvent.ElementType.ALL)overlayFrames++;}
    @SubscribeEvent public void render(TickEvent.RenderTickEvent e){q.render(e);}
    void stopping(){q.receipt.addProperty("nativeFMLServerStopping",true);}
    void stopped(){q.receipt.addProperty("nativeFMLServerStopped",true);try{Files.write(Paths.get(System.getProperty("gtsr.uiqa.receipt")),new GsonBuilder().setPrettyPrinting().create().toJson(q.receipt).getBytes(StandardCharsets.UTF_8));}catch(Exception error){error.printStackTrace();}}
}
