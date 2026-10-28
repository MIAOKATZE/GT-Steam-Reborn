package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.*;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/** Real GT switch -> canonical journal cancellation; no world/client startup or output injection. */
public final class CriticalSolarShutdownContract {
    static NBTTagCompound paid;
    static final class Fixture {
        SparseWorld world;CriticalMachineState state;MTECriticalMachine loom;BaseMetaTileEntity main;
        Fixture(CriticalMachineKind kind)throws Exception {
            world=world();state=state(world,31,ForgeDirection.NORTH);
            NBTTagCompound n=new NBTTagCompound();state.writeToNBT(n);
            n.setTag("config",new CriticalConfiguration(CriticalTier.T3,kind,0,0,0).write());state.readFromNBT(n);
            geometry(state);set(state,"jobFingerprint",field(state,"modelFingerprint"));fill(state);
            loom=CriticalV32Contract.attach(state);
            CriticalWorldData data=CriticalWorldData.get(world);
            data.acquire(state.getStructureId(),"owner",state.getOriginX(),state.getOriginY(),state.getOriginZ());
            data.bindTask(state.getStructureId(),"owner",state.getJobId(),state.getOriginX(),state.getOriginY(),state.getOriginZ());
            check(loom.forceLoomStructureCheck(),"complete canonical loom for shutdown fixture: "+kind);
            set(state,"state",1);set(state,"stage",((java.util.List<?>)field(state,"stageCache")).size());CriticalV32Contract.invoke(state,"build");
            main=(BaseMetaTileEntity)world.getTileEntity(state.blockWorldX(56,35),state.yCoord,state.blockWorldZ(56,35));main.enableWorking();
            fund(120);
        }
        void fund(int progress)throws Exception {
            set(state,"energyRuntime",paid.copy());state.energyRuntimeData().setInteger("progress",progress);
            state.setRuntimeBatch(progress,400,0,"工作 "+progress+"/400");
        }
    }
    public static void main(String[] args)throws Exception {
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(sun.misc.Unsafe)f.get(null);materials();
        try(var file=new FileInputStream(args[0])){paid=CompressedStreamTools.readCompressed(file);}
        Fixture solar=new Fixture(CriticalMachineKind.SOLAR);
        solar.main.disableWorking();solar.state.updateEntity();
        check(!solar.state.energyRuntimeData().getBoolean("funded")&&solar.state.getCycleDuration()==0
            &&!solar.state.energyRuntimeData().hasKey("outputs"),"main power off discards the paid solar batch instead of preserving a resumable output");
        check(solar.state.getStackInSlot(CriticalMachineState.INPUT_SLOTS)==null,"shutdown never creates item output");
        boolean empty=true;for(var tank:solar.state.getTankInfo(net.minecraftforge.common.util.ForgeDirection.UNKNOWN))empty&=tank.fluid==null;
        check(empty&&solar.state.getBufferedEnergy()==0,"discarding a paid batch creates neither fluid refunds nor energy refunds");
        solar.main.enableWorking();solar.fund(399);solar.main.disableWorking();
        check(!solar.state.energyRuntimeData().getBoolean("funded"),"native main callback cancels immediately before a subsequent tick");
        solar.main.enableWorking();
        check(!solar.state.energyRuntimeData().getBoolean("funded")&&solar.state.getCycleDuration()==0,
            "same-tick native off/on cannot resume the discarded batch");
        NBTTagCompound cancelled=(NBTTagCompound)solar.state.energyRuntimeData().copy();solar.main.disableWorking();solar.main.disableWorking();
        check(solar.state.energyRuntimeData().equals(cancelled),"repeated off paths are idempotent");
        Fixture terminal=new Fixture(CriticalMachineKind.SOLAR);set(terminal.state,"owner","");
        ((MTECriticalMachine)terminal.main.getMetaTileEntity()).getLocalCriticalState().setLoomOwnedMain(terminal.state);
        var player=(net.minecraft.entity.player.EntityPlayerMP)unsafe.allocateInstance(net.minecraft.entity.player.EntityPlayerMP.class);
        player.worldObj=terminal.world;player.posX=terminal.state.xCoord+.5;player.posY=terminal.state.yCoord+.5;player.posZ=terminal.state.zCoord+.5;
        check(terminal.state.setProductionEnabled(player,false)&&!terminal.state.energyRuntimeData().getBoolean("funded"),
            "authorized terminal setter discards the same paid batch");
        Fixture uncertain=new Fixture(CriticalMachineKind.SOLAR);uncertain.state.energyRuntimeData().setBoolean("uncertain",true);
        NBTTagCompound audit=(NBTTagCompound)uncertain.state.energyRuntimeData().copy();uncertain.main.disableWorking();
        check(uncertain.state.energyRuntimeData().equals(audit)&&uncertain.state.getCycleDuration()==400,
            "uncertain input/output commit retains its complete audit journal when switched off");
        Fixture nativeUncertain=new Fixture(CriticalMachineKind.SOLAR);set(nativeUncertain.state,"nativeFundingUncertain",true);
        audit=(NBTTagCompound)nativeUncertain.state.energyRuntimeData().copy();nativeUncertain.main.disableWorking();
        check(nativeUncertain.state.energyRuntimeData().equals(audit),"legacy uncertain native funding is not erased");
        Fixture unloaded=new Fixture(CriticalMachineKind.SOLAR);CriticalWorldData lock=CriticalWorldData.get(unloaded.world);
        lock.claimNight(unloaded.state.getStructureId());audit=(NBTTagCompound)unloaded.state.energyRuntimeData().copy();
        unloaded.world.loaded=false;unloaded.state.onChunkUnload();unloaded.state.updateSolarPowerDemand();
        check(unloaded.state.getProductionSwitchState()==null&&unloaded.state.energyRuntimeData().equals(audit)
            &&lock.holdsNight(unloaded.state.getStructureId()),"unloaded main is unknown and preserves both paid journal and night demand");
        Fixture oldLayout=new Fixture(CriticalMachineKind.SOLAR);
        MTECriticalMachine oldMachine=(MTECriticalMachine)oldLayout.main.getMetaTileEntity();CriticalMachineState oldState=oldMachine.getLocalCriticalState();
        NBTTagCompound oldNbt=new NBTTagCompound();oldNbt.setInteger("geometryVersion",1);oldNbt.setInteger("state",2);
        oldNbt.setTag("config",new CriticalConfiguration(CriticalTier.T3,CriticalMachineKind.SOLAR,0,0,0).write());oldState.readFromNBT(oldNbt);
        set(oldState,"energyRuntime",paid.copy());oldState.bindHost(oldLayout.main);oldLayout.world.loaded=false;
        audit=(NBTTagCompound)oldState.energyRuntimeData().copy();oldState.updateSolarPowerDemand();
        check(oldState.getProductionSwitchState()==null&&oldState.energyRuntimeData().equals(audit),
            "legacy main-owned journal also treats an unloaded cached host as unknown rather than a manual off");
        Fixture missing=new Fixture(CriticalMachineKind.SOLAR);missing.world.tiles.remove(key(missing.main.xCoord,missing.main.yCoord,missing.main.zCoord));
        audit=(NBTTagCompound)missing.state.energyRuntimeData().copy();missing.state.updateSolarPowerDemand();
        check(missing.state.getProductionSwitchState()==null&&missing.state.energyRuntimeData().equals(audit),
            "missing or mismatched main lookup cannot be interpreted as a manual power-off");
        Fixture structure=new Fixture(CriticalMachineKind.SOLAR);audit=(NBTTagCompound)structure.state.energyRuntimeData().copy();
        ((MTECriticalMachine)structure.main.getMetaTileEntity()).stopMachine(gregtech.api.util.shutdown.ShutDownReasonRegistry.STRUCTURE_INCOMPLETE);
        check(structure.main.isAllowedToWork()&&structure.state.energyRuntimeData().equals(audit),
            "GT structural stop temporary disable is restored without discarding the paid solar batch");
        for(CriticalMachineKind kind:new CriticalMachineKind[]{CriticalMachineKind.TURBINE,CriticalMachineKind.BATTERY,CriticalMachineKind.SUN}) {
            Fixture other=new Fixture(kind);audit=(NBTTagCompound)other.state.energyRuntimeData().copy();other.main.disableWorking();
            check(other.state.energyRuntimeData().equals(audit)&&other.state.getCycleDuration()==400,
                "other machine power-off preserves its existing pause semantics: "+kind);
        }
        Fixture delivered=new Fixture(CriticalMachineKind.SOLAR);delivered.state.energyRuntimeData().setBoolean("funded",false);
        delivered.state.energyRuntimeData().removeTag("outputs");delivered.state.energyRuntimeData().setString("lastGeneratedEU","1234");
        delivered.state.setRuntimeBatch(0,0,0,"批次交付完成");audit=(NBTTagCompound)delivered.state.energyRuntimeData().copy();delivered.main.disableWorking();
        check(delivered.state.energyRuntimeData().equals(audit),"closing after delivery neither rolls back completed output nor edits historical receipts");
        Fixture legacy=new Fixture(CriticalMachineKind.SOLAR);legacy.state.energyRuntimeData().setBoolean("funded",false);
        set(legacy.state,"batchId","legacy-paid-solar");set(legacy.state,"batchGenerated",987L);set(legacy.state,"pendingGenerated",321L);
        legacy.main.disableWorking();
        check(legacy.state.getBatchId().isEmpty()&&(long)field(legacy.state,"batchGenerated")==0
            &&(long)field(legacy.state,"pendingGenerated")==321,
            "legacy current solar batch is discarded while previously generated energy remains intact");
        Fixture removed=new Fixture(CriticalMachineKind.SOLAR);lock=CriticalWorldData.get(removed.world);
        lock.claimNight(removed.state.getStructureId());audit=(NBTTagCompound)removed.state.energyRuntimeData().copy();
        MTECriticalMachine removedMain=(MTECriticalMachine)removed.main.getMetaTileEntity();removedMain.onUnload();
        check(lock.holdsNight(removed.state.getStructureId())&&removed.state.energyRuntimeData().equals(audit),
            "GT main onUnload preserves night demand and the paid batch");
        // The real GTSR lifecycle body runs; only GT's unrelated server powerfail tracker needs a platform seam.
        var actualBase=removedMain.getBaseMetaTileEntity();
        removedMain.setBaseMetaTileEntity((gregtech.api.interfaces.tileentity.IGregTechTileEntity)java.lang.reflect.Proxy.newProxyInstance(
            getClassLoader(),new Class<?>[]{gregtech.api.interfaces.tileentity.IGregTechTileEntity.class},
            (proxy,method,arguments)->method.getName().equals("isServerSide")?false:method.invoke(actualBase,arguments)));
        removedMain.onRemoval();removed.world.tiles.remove(key(removed.main.xCoord,removed.main.yCoord,removed.main.zCoord));
        removed.state.updateSolarPowerDemand();
        check(!lock.holdsNight(removed.state.getStructureId())&&removed.state.energyRuntimeData().equals(audit),
            "real GT main removal releases night demand without pretending that a loaded switch was manually disabled");
        System.out.println("PASS CriticalSolarShutdownContract checks="+checks);
    }
    static ClassLoader getClassLoader(){return CriticalSolarShutdownContract.class.getClassLoader();}
}
