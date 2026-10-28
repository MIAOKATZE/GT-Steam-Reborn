package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.*;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import com.miaokatze.gtsr.client.critical.CriticalRuntimeAnimationProbe;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/** Paid energy receipts -> production visual descriptor -> anchor S35 -> client interpolation. No GL/FML world. */
public final class CriticalRuntimeAnimationContract {
    static NBTTagCompound solarReceipt;
    static CriticalMachineState configured(SparseWorld world,CriticalMachineKind kind)throws Exception {
        CriticalMachineState state=state(world,31,ForgeDirection.NORTH);
        NBTTagCompound n=new NBTTagCompound();state.writeToNBT(n);
        n.setTag("config",new CriticalConfiguration(CriticalTier.T3,kind,0,0,0).write());
        state.readFromNBT(n);geometry(state);set(state,"jobFingerprint",field(state,"modelFingerprint"));fill(state);return state;
    }
    static CriticalMachineState send(CriticalMachineState source,TileEntityCriticalRenderAnchor server,
        TileEntityCriticalRenderAnchor client)throws Exception {
        server.syncFrom(source,true);
        S35PacketUpdateTileEntity packet=(S35PacketUpdateTileEntity)server.getDescriptionPacket();
        client.onDataPacket(null,packet);return client.getVisualState();
    }
    static void advance(CriticalMachineState state,MTECriticalMachine host,boolean solar) {
        if(solar) {
            // The actual private startSolar receipt is real; offline solar tick/night networking is outside this fixture.
            int progress=state.energyRuntimeData().getInteger("progress")+1;
            state.energyRuntimeData().setInteger("progress",progress);
            state.setRuntimeBatch(progress,CriticalEnergyMath.CYCLE_TICKS,0,"工作 "+progress+"/400");
        } else CriticalEnergyRuntime.tick(state,host);
    }
    static void energy(CriticalMachineKind kind)throws Exception {
        boolean solar=kind==CriticalMachineKind.SOLAR;
        SparseWorld world=world();world.totalWorldTime=100;
        CriticalMachineState source=configured(world,kind);MTECriticalMachine loom=CriticalV32Contract.attach(source);
        CriticalWorldData data=CriticalWorldData.get(world);
        data.acquire(source.getStructureId(),"owner",source.getOriginX(),source.getOriginY(),source.getOriginZ());
        data.bindTask(source.getStructureId(),"owner",source.getJobId(),source.getOriginX(),source.getOriginY(),source.getOriginZ());
        check(loom.forceLoomStructureCheck(),"animation fixture has a complete canonical loom: "+kind);
        set(source,"state",1);set(source,"stage",((java.util.List<?>)field(source,"stageCache")).size());
        CriticalV32Contract.invoke(source,"build");
        BaseMetaTileEntity main=(BaseMetaTileEntity)world.getTileEntity(source.blockWorldX(56,35),source.yCoord,source.blockWorldZ(56,35));
        main.enableWorking();set(source,"structureValid",true);
        if(solar)set(source,"energyRuntime",solarReceipt.copy());
        else source.energyRuntimeData().setBoolean("funded",true);
        source.energyRuntimeData().setInteger("progress",40);advance(source,loom,solar);
        NBTTagCompound descriptor=source.getVisualData();
        check(descriptor.getString("batchId").isEmpty()&&descriptor.getCompoundTag("criticalEnergyRuntime").getBoolean("funded")
            &&descriptor.getInteger("cycleDuration")==400&&descriptor.getBoolean("working"),"real funded energy descriptor retains empty legacy ID: "+kind);
        TileEntityCriticalRenderAnchor server=new TileEntityCriticalRenderAnchor();server.setWorldObj(world);
        SparseWorld clientWorld=world();clientWorld.isRemote=true;clientWorld.totalWorldTime=100;
        Field accesses=net.minecraft.world.World.class.getDeclaredField("worldAccesses");accesses.setAccessible(true);accesses.set(clientWorld,new java.util.ArrayList<>());
        TileEntityCriticalRenderAnchor client=new TileEntityCriticalRenderAnchor();client.setWorldObj(clientWorld);
        CriticalMachineState visual=send(source,server,client);
        check(visual.getVisualBatchPhase(0)==41D/400D,"funded energy descriptor drives nonzero production renderer phase: "+kind);
        CriticalRuntimeAnimationProbe.sample(visual,41D/400D,"working",true);
        clientWorld.totalWorldTime=110;
        check(visual.getVisualBatchPhase(.5F)==51.5D/400D,"client interpolates real energy progress: "+kind);
        for(int i=0;i<20;i++){world.totalWorldTime++;advance(source,loom,solar);}
        clientWorld.totalWorldTime=120;visual=send(source,server,client);
        check(visual.getVisualBatchPhase(0)==61D/400D,"20tick anchor refresh advances the same energy batch: "+kind);
        main.disableWorking();world.totalWorldTime++;source.updateEntity();
        int paused=source.getCycleTicks();clientWorld.totalWorldTime=121;visual=send(source,server,client);
        clientWorld.totalWorldTime=140;
        check(visual.getVisualBatchPhase(.75F)==paused/400D,"paused descriptor prevents client extrapolation: "+kind);
        CriticalRuntimeAnimationProbe.sample(visual,paused/400D,"idle",false);
        if(solar) {
            check(!source.energyRuntimeData().getBoolean("funded")&&source.getCycleDuration()==0,
                "solar power-off discards its batch instead of preserving a resumable animation phase");
            // A new paid receipt is required; this contract covers its descriptor, not a second physical payment.
            set(source,"energyRuntime",solarReceipt.copy());source.energyRuntimeData().setInteger("progress",0);
        }
        main.enableWorking();world.totalWorldTime=141;advance(source,loom,solar);
        clientWorld.totalWorldTime=141;visual=send(source,server,client);clientWorld.totalWorldTime=146;
        check(visual.getVisualBatchPhase(.25F)==(paused+6.25D)/400D,(solar?"new paid solar descriptor advances from its new batch start: ":"resumed descriptor continues without resetting batch phase: ")+kind
            +" phase="+visual.getVisualBatchPhase(.25F)+" paused="+paused+" working="+visual.isWorking()+" status="+visual.getStatus());
        if(solar) {
            source.energyRuntimeData().setBoolean("funded",false);source.setRuntimeBatch(0,0,0,"批次交付完成");
        } else {
            source.energyRuntimeData().setInteger("progress",399);CriticalEnergyRuntime.tick(source,loom);
        }
        world.totalWorldTime=160;clientWorld.totalWorldTime=160;visual=send(source,server,client);
        check(!visual.hasVisualBatch()&&visual.getVisualBatchPhase(0)==0,"delivered batch clears visual phase: "+kind);
        if(solar)set(source,"energyRuntime",solarReceipt.copy());
        else source.energyRuntimeData().setBoolean("funded",true);
        source.energyRuntimeData().setInteger("progress",0);advance(source,loom,solar);
        world.totalWorldTime=161;clientWorld.totalWorldTime=161;visual=send(source,server,client);
        check(visual.getVisualBatchPhase(0)==1D/400D&&visual.getBatchId().isEmpty(),"next energy batch restarts phase without inventing a legacy ID: "+kind);
        set(source,"state",1);check(!source.hasVisualBatch()&&source.getConstructionVisualMode().equals("build"),"construction loom retains its independent build mode: "+kind);
        set(source,"state",3);check(!source.hasVisualBatch()&&source.getConstructionVisualMode().equals("destroy"),"construction loom retains its independent dismantle mode: "+kind);
    }
    static void legacy()throws Exception {
        SparseWorld world=world();CriticalMachineState source=configured(world,CriticalMachineKind.SUN);
        CriticalV32Contract.attach(source);set(source,"structureValid",true);set(source,"batchId","legacy-paid-fusion");
        source.setRuntimeBatch(100,400,0,"工作 100/400");
        BaseMetaTileEntity main=(BaseMetaTileEntity)world.getTileEntity(source.blockWorldX(56,35),source.yCoord,source.blockWorldZ(56,35));main.enableWorking();
        SparseWorld remote=world();remote.isRemote=true;
        CriticalMachineState visual=new CriticalMachineState();visual.worldObj=remote;visual.readVisualData(source.getVisualData());
        check(visual.hasVisualBatch()&&visual.getVisualBatchPhase(0)==.25,"legacy fusion visual batch remains available");
        CriticalRuntimeAnimationProbe.sample(visual,.25,"build",false);
    }
    public static void main(String[] args)throws Exception {
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(sun.misc.Unsafe)f.get(null);
        var mapping=net.minecraft.tileentity.TileEntity.class.getDeclaredMethod("addMapping",Class.class,String.class);
        mapping.setAccessible(true);mapping.invoke(null,TileEntityCriticalRenderAnchor.class,"gtsr.critical.render_anchor");
        try(var file=new FileInputStream(args[0])){solarReceipt=CompressedStreamTools.readCompressed(file);}
        check(solarReceipt.getBoolean("funded")&&solarReceipt.hasKey("inputs")&&solarReceipt.hasKey("outputs"),"solar fixture comes from the real finite-hatch startSolar payment receipt");
        materials();energy(CriticalMachineKind.SOLAR);energy(CriticalMachineKind.TURBINE);energy(CriticalMachineKind.BATTERY);legacy();
        CriticalRuntimeAnimationProbe.continuity();
        System.out.println("PASS CriticalRuntimeAnimationContract checks="+checks+" renderChecks="+CriticalRuntimeAnimationProbe.checks);
    }
}
