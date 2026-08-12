package com.miaokatze.gtsr.common.machine.base;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.apache.commons.lang3.tuple.Pair;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.util.GTUtility;

/** 统一奇点模式基类：mSingularityMode 0=关/1=普通(Steam)/2=临界(Critical)；工作才消耗（canConsumeSingularity 门控）、关机仅计时；默认 200s。 */
public abstract class MTESingularityModeMachineBase<T extends MTESingularityModeMachineBase<T>>
    extends MTEEnhancedMultiBlockBase<T> {

    public static final int SINGULARITY_DURATION_TICKS = 4000; // 200s 默认
    public int mSingularityMode = 0; // 0=关 1=普通 2=临界
    public int mSingularityModeTicks = 0;
    protected int mSingularityCheckCooldown = 0;

    protected MTESingularityModeMachineBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    protected MTESingularityModeMachineBase(String aName) {
        super(aName);
    }

    /** 持续时间（tick），具体机器可覆写 */
    protected int getSingularityDurationTicks() {
        return SINGULARITY_DURATION_TICKS;
    }

    /** 模式关联的奇点物品，具体机器可覆写；mode==2 用 CriticalSteamEntangledSingularity，否则 SteamEntangledSingularity */
    protected ItemStack getSingularityItemForMode(int mode) {
        return mode == 2 ? GTSRItemList.CriticalSteamEntangledSingularity.get(1)
            : GTSRItemList.SteamEntangledSingularity.get(1);
    }

    /** 是否允许消耗奇点（进入/续杯）；默认仅工作中（允许工作且周期进行中），具体机器可覆写 */
    protected boolean canConsumeSingularity() {
        return getBaseMetaTileEntity().isAllowedToWork() && mMaxProgresstime > 0;
    }

    /** 奇点模式退出回调（默认空；地壳物质聚合器用于时运钳位） */
    protected void onSingularityModeExpired() {}

    /**
     * 每 20 tick 调用（onPostTick 节奏）：
     * 模式 0：canConsumeSingularity() 门控下，先试消耗 1 个临界奇点进入模式 2、失败再试普通奇点进入模式 1
     * （进入时重置时长并 markDirty）；
     * 模式 >0 且 mSingularityModeTicks<=0：canConsumeSingularity() 门控下按当前模式物品续杯（重置时长 + markDirty），
     * 失败则退出模式 0 并调 onSingularityModeExpired()。
     * 关机/停机期间倒计时照常递减（onPostTick），但不消耗奇点——倒计时到 0 且门控拒绝续杯时直接退出模式，
     * 等待下次开机重新消耗进入。
     */
    protected void checkSingularityMode() {
        // 开机门控（v1.10.51）：关机/停机不自动消耗奇点（含进入模式与续杯）
        if (!canConsumeSingularity()) return;
        if (mSingularityMode == 0) {
            // 临界奇点优先
            if (consumeSingularityFromInputBuses(1, getSingularityItemForMode(2))) {
                mSingularityMode = 2;
                mSingularityModeTicks = getSingularityDurationTicks();
                getBaseMetaTileEntity().markDirty();
            } else if (consumeSingularityFromInputBuses(1, getSingularityItemForMode(1))) {
                mSingularityMode = 1;
                mSingularityModeTicks = getSingularityDurationTicks();
                getBaseMetaTileEntity().markDirty();
            }
        } else if (mSingularityModeTicks <= 0) {
            // 倒计时耗尽瞬间：按当前模式对应物品无缝续杯，模式全程不断
            if (consumeSingularityFromInputBuses(1, getSingularityItemForMode(mSingularityMode))) {
                mSingularityModeTicks = getSingularityDurationTicks();
                getBaseMetaTileEntity().markDirty();
            } else {
                // 续杯失败退出模式（回调供具体机器收尾，如地壳物质聚合器的时运钳位）
                mSingularityMode = 0;
                onSingularityModeExpired();
                getBaseMetaTileEntity().markDirty();
            }
        }
    }

    /** onPostTick：super 后，服务端每 tick 递减 mSingularityModeTicks（>0 时 -1）；每 20 tick 调 checkSingularityMode() */
    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isClientSide()) return;
        // 奇点模式：每 tick 倒计时，每 20 tick 检查进入/无缝续杯。
        // 关机仅计时：倒计时不受开关机门控（canConsumeSingularity 只管消耗），到 0 无法续杯即退出模式
        if (mSingularityModeTicks > 0) mSingularityModeTicks--;
        if (++mSingularityCheckCooldown >= 20) {
            mSingularityCheckCooldown = 0;
            // 包配方窗口使 ME 输入总线的虚拟引用可读（getStackInSlot 仅窗口内有效）；
            // v1.10.61：dual 样板仓消耗分支已删除，窗口包装仅服务于输入总线奇点消耗，仍须保留；
            // 与基类 checkRecipe 的窗口嵌套安全（start/end 均幂等）
            startRecipeProcessing();
            checkSingularityMode();
            endRecipeProcessing();
        }
    }

    /**
     * 奇点消耗（amount 个指定奇点物品）：输入总线 mInputBusses 候选收集 + decrStackSize 实扣。
     * v1.10.61：移除 mDualInputHatches（样板仓）分支——奇点燃料仅走输入总线；
     * 遵循 v1.10.8 教训：绝不用 getAllItems() 对 CraftingInputME 的内嵌物品栈做活引用直改
     * （会损坏样板数据/存档复活=复制燃料），对返回副本的实现则扣减静默失效（免费燃料）。
     *
     * @return 是否成功消耗全部数量
     */
    protected boolean consumeSingularityFromInputBuses(int amount, ItemStack singularity) {
        if (singularity == null) return false;
        int remaining = amount;
        // 先收集所有可消耗的输入总线槽位，避免部分消耗后无法回滚
        List<Pair<MTEHatchInputBus, Integer>> candidates = new ArrayList<>();
        for (MTEHatchInputBus bus : GTUtility.validMTEList(mInputBusses)) {
            if (bus == null) continue;
            for (int i = 0; i < bus.getSizeInventory(); i++) {
                ItemStack stack = bus.getStackInSlot(i);
                if (stack != null && GTUtility.areUnificationsEqual(stack, singularity, true)) {
                    candidates.add(Pair.of(bus, i));
                    remaining -= stack.stackSize;
                    if (remaining <= 0) break;
                }
            }
            if (remaining <= 0) break;
        }
        if (remaining > 0) {
            return false;
        }
        // 实扣：输入总线以 decrStackSize 安全语义扣减（v1.10.8 基类教训）
        remaining = amount;
        for (Pair<MTEHatchInputBus, Integer> candidate : candidates) {
            MTEHatchInputBus bus = candidate.getLeft();
            int slot = candidate.getRight();
            ItemStack stack = bus.getStackInSlot(slot);
            if (stack == null) continue;
            int toConsume = Math.min(remaining, stack.stackSize);
            bus.decrStackSize(slot, toConsume);
            remaining -= toConsume;
            if (remaining <= 0) break;
        }
        for (MTEHatchInputBus bus : GTUtility.validMTEList(mInputBusses)) {
            if (bus != null) bus.updateSlots();
        }
        return true;
    }

    public int getSingularityModeForGui() {
        return mSingularityMode;
    }

    public int getSingularityTicksForGui() {
        return mSingularityModeTicks;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mSingularityMode", mSingularityMode);
        aNBT.setInteger("mSingularityModeTicks", mSingularityModeTicks);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        // 兼容旧键：无 mSingularityMode 键时读 mSingularityModeLevel（MSTA 旧档）
        if (aNBT.hasKey("mSingularityMode")) {
            mSingularityMode = aNBT.getInteger("mSingularityMode");
        } else {
            mSingularityMode = aNBT.getInteger("mSingularityModeLevel");
        }
        mSingularityModeTicks = aNBT.getInteger("mSingularityModeTicks");
    }
}
