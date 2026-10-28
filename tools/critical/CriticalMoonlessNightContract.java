package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.*;

import java.lang.reflect.Field;
import net.minecraft.nbt.NBTTagCompound;
import com.miaokatze.gtsr.client.critical.CriticalNightClient;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/** Real region/NBT ownership with controller observations supplied at the public state boundary. */
public final class CriticalMoonlessNightContract {
    static final String FIRST = "11111111-1111-1111-1111-111111111111";
    static final String SECOND = "22222222-2222-2222-2222-222222222222";

    static final class ObservedController extends CriticalMachineState {
        Boolean mainPower = true;
        boolean complete = true;
        CriticalMachineKind kind = CriticalMachineKind.SOLAR;
        String id = FIRST, job = "job-one";
        @Override public boolean isStructureComplete() { return complete; }
        @Override public boolean isStructureValidated() { return false; }
        @Override public boolean isEnabled() { return false; }
        @Override public Boolean getProductionSwitchState() { return mainPower; }
        @Override public CriticalMachineKind getMachineKind() { return kind; }
        @Override public String getStructureId() { return id; }
        @Override public String getJobId() { return job; }
        @Override public int getGeometryVersion() { return 1; }
        @Override public void bindHost(net.minecraft.tileentity.TileEntity tile) {}
    }

    static void install(SparseWorld world, ObservedController state) throws Exception {
        BaseMetaTileEntity tile = (BaseMetaTileEntity) unsafe.allocateInstance(BaseMetaTileEntity.class);
        MTECriticalMachine machine = (MTECriticalMachine) unsafe.allocateInstance(MTECriticalMachine.class);
        set(machine, "state", state);
        Field base = gregtech.api.metatileentity.MetaTileEntity.class.getDeclaredField("mBaseMetaTileEntity");
        base.setAccessible(true);
        base.set(machine, tile);
        tile.setMetaTileEntity(machine);
        world.tiles.put(key(40, 68, 33), tile);
    }

    public static void main(String[] args) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafe = (sun.misc.Unsafe) field.get(null);
        SparseWorld world = world();
        CriticalWorldData data = CriticalWorldData.get(world);
        check(data.acquire(FIRST, "owner", 0, 68, 0), "first region");
        check(data.bindTask(FIRST, "owner", "job-one", 0, 68, 0), "first task");
        check(data.acquire(SECOND, "owner-two", 100, 68, 0), "second region");
        ObservedController first = new ObservedController();
        install(world, first);
        check(data.claimNight(FIRST), "first main reserves dimension before any recipe starts");
        check(data.validateNightOwner(world), "unvalidated, disabled work permission still keeps powered night");
        check(!data.claimNight(world, SECOND), "second controller cannot steal powered idle night");
        world.loaded = false;
        check(data.validateNightOwner(world), "unloaded controller keeps ownership");
        check(!data.claimNight(world, SECOND) && world.unloadedReads == 0, "unloaded owner blocks second without TE read");
        world.loaded = true;
        first.mainPower = null;
        check(data.validateNightOwner(world), "unknown linked switch is not manual off");
        check(!data.claimNight(world, SECOND), "main reload wait cannot transfer ownership");
        NBTTagCompound persisted = new NBTTagCompound();
        data.writeToNBT(persisted);
        CriticalWorldData restored = new CriticalWorldData();
        restored.readFromNBT(persisted);
        check(restored.holdsNight(FIRST) && restored.validateNightOwner(world), "NBT owner survives reload wait");
        first.mainPower = false;
        check(!data.validateNightOwner(world) && !data.hasNightOwner(), "observed main off releases ownership");
        first.mainPower = true;
        check(data.claimNight(FIRST), "first can reacquire after power on");
        first.complete = false;
        check(!data.validateNightOwner(world), "dismantled controller releases ownership");
        first.complete = true;
        data.claimNight(FIRST);
        first.kind = CriticalMachineKind.SUN;
        check(!data.validateNightOwner(world), "wrong kind cannot retain owner");
        first.kind = CriticalMachineKind.SOLAR;
        data.claimNight(FIRST);
        first.job = "different-job";
        check(!data.validateNightOwner(world), "wrong task cannot retain owner");
        first.job = "job-one";
        data.claimNight(FIRST);
        first.id = SECOND;
        check(!data.validateNightOwner(world), "wrong structure identity cannot retain owner");
        first.id = FIRST;
        data.claimNight(FIRST);
        world.tiles.clear();
        check(!data.validateNightOwner(world), "loaded missing controller releases ownership");
        data.claimNight(FIRST);
        data.release(FIRST);
        check(!data.hasNightOwner(), "region removal releases ownership");
        int[] colors = new int[256];
        for (int i = 0; i < colors.length; i++) colors[i] = 0xff000000 | i * 257;
        int[] original = colors.clone();
        CriticalNightClient.darkenSkyLight(colors);
        for (int sky = 0; sky < 16; sky++) for (int block = 0; block < 16; block++)
            check(colors[sky * 16 + block] == original[block], "sky coordinate zero, block coordinate preserved");
        System.out.println("PASS CriticalMoonlessNightContract checks=" + checks);
    }
}
