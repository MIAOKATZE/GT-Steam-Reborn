package com.miaokatze.gtsr.common.structure;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import com.miaokatze.gtsr.common.machine.base.MTEGTSRRedstoneHatch;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/** 一台控制器的一轮完整结构校验；所有 piece 共用计数，最终通过后才提交绑定。 */
public final class GTSRRedstoneHatchCheck {

    public static final int LIMIT = 4;

    private final IMetaTileEntity controller;
    private final Set<MTEGTSRRedstoneHatch> pending = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<MTEGTSRRedstoneHatch> bound = Collections.newSetFromMap(new IdentityHashMap<>());
    private int depth;
    private boolean failed;

    public GTSRRedstoneHatchCheck(IMetaTileEntity controller) {
        this.controller = controller;
    }

    public boolean isChecking() {
        return depth > 0;
    }

    public void begin() {
        if (depth == 0) clear();
        depth++;
    }

    /** 同一仓室被重叠 piece 重访只计算一次。 */
    public int visit(MTEGTSRRedstoneHatch hatch) {
        pending.add(hatch);
        return pending.size();
    }

    public boolean isOverLimit() {
        return pending.size() > LIMIT;
    }

    /** 原生 clearHatches 同样丢弃探测结果，供可选分段确定长度后重新扫描已接受的 pieces。 */
    public void clear() {
        for (MTEGTSRRedstoneHatch hatch : bound) {
            if (hatch.getController() == controller) hatch.setController(null);
        }
        bound.clear();
        pending.clear();
        failed = false;
    }

    /** 必须由 finally 调用；异常退出等价于失败，嵌套校验只在最外层提交。 */
    public void finish(boolean success) {
        failed |= !success || isOverLimit();
        if (--depth != 0) return;
        if (!failed) {
            for (MTEGTSRRedstoneHatch hatch : pending) {
                hatch.setController(controller);
            }
            bound.addAll(pending);
        }
        pending.clear();
        failed = false;
    }
}
