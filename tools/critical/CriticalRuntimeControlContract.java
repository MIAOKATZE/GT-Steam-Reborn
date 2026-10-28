package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.*;

import java.lang.reflect.Field;
import net.minecraftforge.common.util.ForgeDirection;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/** Real linked GT power switches with the production journal and sparse-world geometry. */
public final class CriticalRuntimeControlContract {
    public static void main(String[] args) throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        unsafe = (sun.misc.Unsafe) f.get(null);
        materials();
        SparseWorld w = world();
        CriticalMachineState s = state(w, 31, ForgeDirection.NORTH);
        fill(s);
        MTECriticalMachine loom = CriticalV32Contract.attach(s);
        BaseMetaTileEntity loomTile = (BaseMetaTileEntity) loom.getBaseMetaTileEntity();
        BaseMetaTileEntity main = (BaseMetaTileEntity) w.getTileEntity(
            s.blockWorldX(56,35), s.yCoord, s.blockWorldZ(56,35));
        main.enableWorking();
        loomTile.disableWorking();
        check(s.isEnabled(), "READY production follows enabled main while fabrication loom is disabled");
        check(!s.isConstructionEnabled() && s.isProductionEnabled(), "construction and production expose separate switches");
        main.disableWorking();
        loomTile.enableWorking();
        check(!s.isEnabled(), "READY production stops on main power despite enabled fabrication loom");
        check(s.getRuntimeBlockerKey().equals("runtime.power_off"), "disabled main gives actionable runtime blocker");
        set(s,"state",1);
        check(s.isEnabled(), "construction follows fabrication loom despite disabled main");
        loomTile.disableWorking();
        check(!s.isEnabled(), "construction pauses with fabrication loom");
        set(s,"state",2);
        main.enableWorking();
        s.fail("本批蒸馏水不足，或缺少足额冷却剂、激活剂；未扣料");
        check(!s.getDisplayStatusKey().equals("ui.state.ready"), "missing runtime media never presents as ready");
        s.failRuntime("runtime.solar.missing_coolant", "本批缺少冷却剂；未扣料");
        check(s.getRuntimeBlockerKey().equals("runtime.solar.missing_coolant"), "typed coolant blocker survives presentation");
        check(s.getReasonCode() == 2, "missing coolant is classified as missing material");
        s.setRuntimeBatch(1,400,0,"工作 1/400");
        check(!s.getRuntimeBlockerKey().equals("runtime.solar.missing_coolant"), "successful runtime tick clears cached missing-media diagnosis");
        s.fail("等待批次输出空间");
        check(!s.getRuntimeBlockerKey().isEmpty(), "output blockage is retained");
        s.fail("尚未映射的真实失败");
        check(s.getRuntimeBlockerKey().equals("runtime.blocked"), "unknown real failure never becomes ready");
        set(s,"batchId","already-paid-batch");
        s.energyRuntimeData().setBoolean("funded",true);
        s.energyRuntimeData().setInteger("progress",123);
        String paidBatch=s.energyRuntimeData().toString();
        String thermal=s.getStellarRuntimeData().toString();
        String[] solarStatuses={"恒星尚未就绪，等待点火或持续燃料与电力","加工已暂停，恒星持续消耗氦三与电力","没有完整可执行批次"};
        String[] solarKeys={"runtime.sun.igniting","runtime.sun.production_paused","runtime.sun.waiting_recipe"};
        for(int i=0;i<solarStatuses.length;i++) {
            s.fail(solarStatuses[i]);
            check(s.getRuntimeStatusKey().equals(solarKeys[i])
                &&s.getBatchId().equals("already-paid-batch")&&s.energyRuntimeData().toString().equals(paidBatch)
                &&s.getStellarRuntimeData().toString().equals(thermal),"stellar presentation classifies state without mutating paid batch: "+solarKeys[i]);
        }
        s.getStellarRuntimeData().setBoolean("uncertain",true);
        check(s.getRuntimeBlockerKey().equals("runtime.uncertain"),"stellar phase-six diagnosis preserves uncertainty");
        s.fail("批次缺电，输入已保留在本机escrow");
        check(s.getRuntimeBlockerKey().equals("reason.1"),"explicit power failure takes precedence over stellar phase presentation");
        w.tiles.remove(key(main.xCoord,main.yCoord,main.zCoord));
        check(!s.isEnabled() && s.getRuntimeBlockerKey().equals("runtime.main_unavailable"), "missing linked main cannot run");
        System.out.println("PASS CriticalRuntimeControlContract checks=" + checks);
    }
}
