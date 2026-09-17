package com.miaokatze.gtsr.common.machine.base;

import static gregtech.api.enums.GTValues.emptyItemStackArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;
import com.miaokatze.gtsr.common.api.compat.IGTSRHatchCasingProvider;
import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.blocks.TileRunawaySingularity;
import com.miaokatze.gtsr.common.event.GTSRMachineEvent;
import com.miaokatze.gtsr.common.event.GTSRSingularityStructChillEvent;
import com.miaokatze.gtsr.common.gui.MTESingularityMachineGui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusInput;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusOutput;

/** Shared processing logic and steam plumbing for the singularity machines. */
public abstract class MTESingularityMachineBase extends MTESingularityModeMachineBase<MTESingularityMachineBase>
    implements IGTSRHatchCasingProvider {

    protected static final int CYCLE_LENGTH = 20;
    protected static final double HEAT_DECAY_PER_SECOND = 0.01d;
    protected static final double[] GRADE_COEF = { 0.5d, 1.0d, 2.0d };
    protected static final String[] DENSE_FLUID_NAMES = { "densesteam", "densesuperheatedsteam",
        "densesupercriticalsteam" };
    protected static final String[] NORMAL_FLUID_NAMES = { "steam", "ic2superheatedsteam", "supercriticalsteam" };
    /** 失稳/撕裂停机自然衰减速率（%/s，v1.1 §2.5/§2.6） */
    protected static final double STATE_DECAY_PER_SECOND = 0.1d;
    /** 超限被动衰减（字面读法）：值 < 0.01 时固定降 0.01，否则降 值/2000，clamp ≥ 0（v1.2 拍板） */
    protected static final double OVERLIMIT_DECAY_SMALL = 0.01d;
    /** 失稳积累超限阈值（两机同构）：工作中且超限 > 200 时每秒 +超限/1000 */
    protected static final double INSTABILITY_THRESHOLD = 200.0d;
    /** 烈焰之炽焱（pyrotheum）/ 极寒之凛冰（cryotheum）流体注册名 */
    protected static final String PYROTHEUM_FLUID_NAME = "pyrotheum";
    protected static final String CRYOTHEUM_FLUID_NAME = "cryotheum";

    private static IIconContainer OVERLAY_OFF;
    private static IIconContainer OVERLAY_ON;

    public int mTier = 0;
    public double mHeat = 0.0d;
    // T4 超限模式三值（百分比口径，GUI 显示 %.1f%%）：NBT setDouble/getDouble 持久化，缺省 0（旧档兼容）。
    // 仅 SSE（超限 0-500 + 失稳）/ CESS（三值全实装）覆写钩子启用；聚合器/致密机不覆写，恒为 0 且管线零开销直通。
    /** 奇点超限程度（%）：SSE >500 爆 / CESS >=1000 爆；pyrotheum 增长，每秒被动衰减 */
    public double mOverlimit = 0.0d;
    /** 结构失稳程度（%）：>100 结构崩解爆炸链；超限>200 工作中积累，cryotheum 降温，停机衰减 */
    public double mInstability = 0.0d;
    /** 维度撕裂程度（%）：>100 维度撕裂爆炸链（仅 CESS 实装） */
    public double mTear = 0.0d;

    protected final List<MTEHatchPressureSteamInput> mPressureSteamInputs = new ArrayList<>();

    protected MTESingularityMachineBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    protected MTESingularityMachineBase(String aName) {
        super(aName);
    }

    protected abstract int getRequiredTier();

    protected abstract double getHeatMax();

    protected abstract long getHeatHalfPoint();

    protected abstract boolean includeDenseSteam();

    /** 致密态专属蒸汽探测：默认 false（普通+致密都探测）；临界纠缠奇点稳定装置覆写为 true（仅致密态变体）。 */
    protected boolean isDenseSteamOnly() {
        return false;
    }

    protected abstract ItemStack getAggregationOutput();

    protected String getTooltipKeyPrefix() {
        return "gtsr.tooltip.entangler.";
    }

    public String getGuiKeyPrefix() {
        return "gtsr.gui.entangler.";
    }

    protected boolean requiresOutputHatch() {
        return false;
    }

    protected boolean requiresInputBus() {
        return false;
    }

    public boolean isDenseStateManipulator() {
        return false;
    }

    public int getModeForGui() {
        return getSingularityModeForGui();
    }

    public int getFuelTicksForGui() {
        return getSingularityTicksForGui();
    }

    // 是否在 GUI 终端隐藏等级行（地壳物质聚合器无等级概念，默认显示）。
    public boolean isHideTierInGui() {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        OVERLAY_OFF = Textures.BlockIcons.custom("gtsr:MTESteamSingularityEntangler_OFF");
        OVERLAY_ON = Textures.BlockIcons.custom("gtsr:MTESteamSingularityEntangler_ON");
        super.registerIcons(aBlockIconRegister);
    }

    protected int getCasingTextureIndex() {
        return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
    }

    protected int getHatchCasingTextureIndex() {
        return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
    }

    @Override
    public int getGTSRHatchCasingTextureIndex() {
        return getHatchCasingTextureIndex();
    }

    protected void updateAllHatchTextures() {
        int textureID = getHatchCasingTextureIndex();
        for (MTEHatch h : mInputHatches) h.updateTexture(textureID);
        for (MTEHatch h : mOutputHatches) h.updateTexture(textureID);
        for (MTEHatch h : mInputBusses) h.updateTexture(textureID);
        for (MTEHatch h : mOutputBusses) h.updateTexture(textureID);
        for (MTEHatch h : mPressureSteamInputs) h.updateTexture(textureID);
        if (mDualInputHatches != null) {
            for (IDualInputHatch h : mDualInputHatches) {
                if (h != null) h.updateTexture(textureID);
            }
        }
    }

    @Override
    public void onValueUpdate(byte aValue) {
        mTier = aValue;
    }

    @Override
    public byte getUpdateData() {
        return (byte) mTier;
    }

    protected enum SingularityHatchElement implements IHatchElement<MTESingularityMachineBase> {

        SteamInput("GTSR.HatchElement.SteamInput", MTESingularityMachineBase::addSteamInputToMachineList,
            MTEHatchInput.class, MTEHatchPressureSteamInput.class) {

            @Override
            public long count(MTESingularityMachineBase t) {
                return t.mInputHatches.size() + t.mPressureSteamInputs.size();
            }

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEHatchPressureSteamInput.class);
            }
        },

        SteamInputBus("GTSR.HatchElement.SteamInputBus", MTESingularityMachineBase::addInputBusToMachineList,
            MTEHatchInputBus.class) {

            @Override
            public long count(MTESingularityMachineBase t) {
                return t.mInputBusses.size();
            }

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEHatchSteamBusInput.class);
            }
        },

        SteamOutputBus("GTSR.HatchElement.SteamOutputBus", MTESingularityMachineBase::addOutputBusToMachineList,
            MTEHatchOutputBus.class) {

            @Override
            public long count(MTESingularityMachineBase t) {
                return t.mOutputBusses.size();
            }

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEHatchSteamBusOutput.class);
            }
        },

        SteamOutputHatch("GTSR.HatchElement.SteamOutputHatch", MTESingularityMachineBase::addOutputHatchToMachineList,
            MTEHatchOutput.class) {

            @Override
            public long count(MTESingularityMachineBase t) {
                return t.mOutputHatches.size();
            }
        };

        private final String translationKey;
        private final List<Class<? extends IMetaTileEntity>> mteClasses;
        private final IGTHatchAdder<MTESingularityMachineBase> adder;

        @SafeVarargs
        SingularityHatchElement(String translationKey, IGTHatchAdder<MTESingularityMachineBase> adder,
            Class<? extends IMetaTileEntity>... mteClasses) {
            this.translationKey = translationKey;
            this.mteClasses = ImmutableList.copyOf(mteClasses);
            this.adder = adder;
        }

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return mteClasses;
        }

        @Override
        public IGTHatchAdder<? super MTESingularityMachineBase> adder() {
            return adder;
        }

        @Override
        public String getDisplayName() {
            // [GT-compat] beta 兼容层（beta1/beta2/beta3）：GTUtility.translate 于 beta-3 移除，改用 vanilla StatCollector（三版本通用）
            return StatCollector.translateToLocal(translationKey);
        }

        @Override
        public String getDescriptionLangKey() {
            return translationKey;
        }
    }

    public boolean addSteamInputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte == null) return false;
        if (mte instanceof MTEHatchInput) return addInputHatchToMachineList(aTileEntity, aBaseCasingIndex);
        if (mte instanceof MTEHatchPressureSteamInput hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            return mPressureSteamInputs.add(hatch);
        }
        return false;
    }

    public boolean addOutputBusToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte == null) return false;
        if (mte instanceof MTEHatchOutputBus hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            hatch.updateCraftingIcon(getMachineCraftingIcon());
            return mOutputBusses.add(hatch);
        }
        return false;
    }

    protected final CheckRecipeResult processAggregationCycle() {
        int grade = findHighestGrade(includeDenseSteam());
        if (grade < 0) return CheckRecipeResultRegistry.NO_RECIPE;
        long amount = sumGrade(grade, includeDenseSteam());
        drainGrade(grade, includeDenseSteam());
        double base = getHeatMax() * amount / (amount + getHeatHalfPoint());
        // T4 热量乘数钩子：默认 1.0（聚合器/致密机不受影响）；SSE=(1+超限/100)、CESS=(1+超限/100)(1+撕裂/100)
        mHeat += GRADE_COEF[grade] * base * getHeatMultiplier();
        if (mHeat >= 1.0d) {
            // v1.10.8：输出前探测输出总线余量——原实现直接 addOutputPartial，
            // 总线满时溢出即 VOID（奇点消失且 mHeat 清零）。
            if (canOutputSingularity()) {
                addOutputPartial(getAggregationOutput());
                mHeat = 0.0d;
            }
        }
        startCycle();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /**
     * 探测输出总线是否有空槽或可堆叠同物品槽（防止奇点输出溢出 VOID）。
     */
    protected final boolean canOutputSingularity() {
        ItemStack output = getAggregationOutput();
        if (output == null) return false;
        for (MTEHatchOutputBus bus : mOutputBusses) {
            if (bus == null) continue;
            for (int i = 0; i < bus.getSizeInventory(); i++) {
                ItemStack stack = bus.getStackInSlot(i);
                if (stack == null) return true;
                if (stack.isItemEqual(output) && stack.stackSize < stack.getMaxStackSize()) return true;
            }
        }
        return false;
    }

    protected final void startCycle() {
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        mOutputItems = emptyItemStackArray;
        mMaxProgresstime = CYCLE_LENGTH;
    }

    protected List<MTEHatch> getSteamInputHatches() {
        List<MTEHatch> all = new ArrayList<>(mInputHatches.size() + mPressureSteamInputs.size());
        all.addAll(mInputHatches);
        all.addAll(mPressureSteamInputs);
        return all;
    }

    private FluidStack[] gradeProbeStacks(int grade, boolean includeDense, boolean denseOnly) {
        FluidStack normal = FluidRegistry.getFluidStack(NORMAL_FLUID_NAMES[grade], 1);
        FluidStack dense = FluidRegistry.getFluidStack(DENSE_FLUID_NAMES[grade], 1);
        if (denseOnly) return new FluidStack[] { dense };
        if (includeDense) return new FluidStack[] { normal, dense };
        return new FluidStack[] { normal };
    }

    protected final boolean probeGrade(int grade, boolean includeDense, boolean denseOnly) {
        for (FluidStack request : gradeProbeStacks(grade, includeDense, denseOnly)) {
            if (request == null) continue;
            for (MTEHatch hatch : getSteamInputHatches()) {
                if (GTSRHatchFluidAccess.hasFluid(hatch, request.getFluid(), 1)) return true;
            }
        }
        return false;
    }

    protected final int findHighestGrade(boolean includeDense) {
        // v1.10.5 拆分后 1 级机（蒸汽奇点纠缠装置）设计上识别全部普通等级（蒸汽/过热/超临界），
        // 致密变体由 includeDense 参数控制；全等级识别与 tooltip 描述一致。
        for (int grade = 2; grade >= 0; grade--) {
            if (probeGrade(grade, includeDense, isDenseSteamOnly())) return grade;
        }
        return -1;
    }

    protected final long sumGrade(int grade, boolean includeDense) {
        long amount = 0;
        for (FluidStack request : gradeProbeStacks(grade, includeDense, isDenseSteamOnly())) {
            if (request == null) continue;
            for (MTEHatch hatch : getSteamInputHatches()) {
                FluidTankInfo[] tanks = hatch.getTankInfo(ForgeDirection.UNKNOWN);
                if (tanks == null) continue;
                for (FluidTankInfo tank : tanks) {
                    if (tank != null && tank.fluid != null && tank.fluid.isFluidEqual(request))
                        amount += tank.fluid.amount;
                }
            }
        }
        return amount;
    }

    protected final void drainGrade(int grade, boolean includeDense) {
        for (FluidStack request : gradeProbeStacks(grade, includeDense, isDenseSteamOnly())) {
            if (request == null) continue;
            for (MTEHatch hatch : getSteamInputHatches()) {
                // v1.10.55：直接 MAX_VALUE 实扣（"输入仓有多少消耗多少"设计语义）；
                // 原"模拟探测+实扣"两段在 beta-1 的 MTEHatchInputME.drain 忽略 doDrain 时会先模拟全扣再实扣 0，
                // 直接实扣双版本等效（beta-1/beta-2 的实扣路径一致）
                FluidStack full = request.copy();
                full.amount = Integer.MAX_VALUE;
                hatch.drain(ForgeDirection.UNKNOWN, full, true);
            }
        }
    }

    protected final int fillOutput(FluidStack stack) {
        int remaining = stack.amount;
        for (MTEHatchOutput hatch : mOutputHatches) {
            if (remaining <= 0) break;
            FluidStack toFill = stack.copy();
            toFill.amount = remaining;
            remaining -= hatch.fill(toFill, true);
        }
        return stack.amount - remaining;
    }

    protected boolean shouldDecayHeat() {
        return true;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide() || aTick % CYCLE_LENGTH != 0L) return;
        updateEntanglementSingularity(aBaseMetaTileEntity);
        // T4 超限模式秒级结算管线（默认未实装机 hasOverlimitMechanics()=false 零开销直通）；
        // 返回 true = 爆炸链已触发：首个触发即执行并终止本 tick 后续处理
        if (hasOverlimitMechanics() && updateOverlimitMechanics(aBaseMetaTileEntity)) return;
        if (!shouldDecayHeat()) return;
        if (!mMachine || !aBaseMetaTileEntity.isAllowedToWork()) {
            mHeat = Math.max(0.0d, mHeat - HEAT_DECAY_PER_SECOND);
            return;
        }
        if (mMaxProgresstime <= 0 && findHighestGrade(includeDenseSteam()) < 0) {
            mHeat = Math.max(0.0d, mHeat - HEAT_DECAY_PER_SECOND);
        }
    }

    // region 超限模式（T4-T7）：三值秒级结算管线 + 流体消耗 + 爆炸链执行。
    // 默认全部惰性：聚合器/致密机不覆写任何钩子（hasOverlimitMechanics()=false、getHeatMultiplier()=1.0），行为不变。

    /**
     * 超限机制是否实装（默认 false）：SSE/CESS 覆写 true 启用秒级结算管线与爆炸链；
     * 聚合器/致密机保持 false，onPostTick 管线零开销直通。
     */
    protected boolean hasOverlimitMechanics() {
        return false;
    }

    /** 维度撕裂机制是否实装（默认 false，仅 CESS 覆写 true）：撕裂停机衰减随此开关 */
    protected boolean hasTearMechanics() {
        return false;
    }

    /**
     * 热量乘数钩子（默认 1.0，processAggregationCycle 乘入）。
     * SSE = (1 + 超限/100)（500% 封顶 6×，不加失稳项）；CESS = (1 + 超限/100)(1 + 撕裂/100)；
     * 默认 1.0 保证聚合器/致密机不受影响。
     */
    protected double getHeatMultiplier() {
        return 1.0d;
    }

    /**
     * 撕裂积累钩子（默认无操作，仅 CESS 实装 v1.1 §2.6 条件积累 + 蠕变）；
     * 撕裂停机衰减与爆炸链在基类管线统一处理。
     */
    protected void accumulateTear(boolean working) {}

    /**
     * 超限流体消耗钩子（默认无操作）：每秒一次，仅在流体门槛（mMachine && isAllowedToWork()，空转也吃料）通过时调用。
     * SSE = 双流体（pyrotheum/cryotheum）；CESS 追加 UU 物质。
     */
    protected void consumeOverlimitFluids() {}

    /**
     * 爆炸链检查钩子（默认无操作）：按机器链条顺序检查，首个触发即执行并返回 true 终止本 tick。
     * SSE 双链：装置超限（>500%）→ 结构崩解（失稳>100%）；CESS 三链：装置超限（>=1000%）→ 结构崩解 → 维度撕裂。
     */
    protected boolean checkOverlimitExplosionChain() {
        return false;
    }

    /**
     * 秒级结算管线（v1.2 §4-42，顺序固定，onPostTick 20-tick 分支调用）：
     * 流体消耗 → 超限被动衰减（无条件）→ 积累（仅工作中）→ 停机衰减（仅停机）→ 爆炸链检查（首个触发即执行并终止本 tick）。
     * 工作中（D1）= shouldRenderEntanglementSingularity（结构有效+允许工作+周期进行中或蒸汽尚存），
     * 与热量衰减/奇点渲染语义一致。
     *
     * @return true = 本 tick 已触发爆炸，调用方必须立即返回
     */
    protected boolean updateOverlimitMechanics(IGregTechTileEntity aBaseMetaTileEntity) {
        // 1. 流体消耗（流体门槛 = mMachine && isAllowedToWork()：空转也吃料）
        if (mMachine && aBaseMetaTileEntity.isAllowedToWork()) {
            consumeOverlimitFluids();
        }
        // 2. 超限被动衰减（无条件，唯一自然降低手段；字面读法）：值 < 0.01 时 -0.01，否则 -值/2000，clamp ≥ 0
        if (mOverlimit > 0.0d) {
            mOverlimit = mOverlimit < OVERLIMIT_DECAY_SMALL ? Math.max(0.0d, mOverlimit - OVERLIMIT_DECAY_SMALL)
                : Math.max(0.0d, mOverlimit - mOverlimit / 2000.0d);
        }
        boolean working = shouldRenderEntanglementSingularity(aBaseMetaTileEntity);
        if (working) {
            // 3. 积累（仅工作中）：失稳——超限 > 200 时每秒 +超限/1000（两机同构）；撕裂——仅 CESS 钩子
            if (mOverlimit > INSTABILITY_THRESHOLD) {
                mInstability += mOverlimit / 1000.0d;
            }
            accumulateTear(working);
        } else {
            // 4. 停机衰减（仅停机）：失稳/撕裂各 -0.1%/s
            mInstability = Math.max(0.0d, mInstability - STATE_DECAY_PER_SECOND);
            if (hasTearMechanics()) {
                mTear = Math.max(0.0d, mTear - STATE_DECAY_PER_SECOND);
            }
        }
        // 5. 爆炸链检查（首个触发即执行并终止本 tick）
        return checkOverlimitExplosionChain();
    }

    /**
     * 双流体结算（SSE/CESS 同款，v1.1 §2.4/§2.5）：
     * pyrotheum 全量消耗 → 超限 +0.05x/(x+5000)；
     * cryotheum 全量消耗 → 失稳 -0.01x/(x+1000)，超扣（减少量 > 当前失稳）→ clamp 0 + disableWorking()
     * （GT 原生软锤状态，玩家软锤右键恢复）+ 全服聊天「结构失温→停机」。
     */
    protected final void consumePyrotheumAndCryotheum() {
        int pyrotheum = drainAllOfFluid(FluidRegistry.getFluidStack(PYROTHEUM_FLUID_NAME, 1));
        if (pyrotheum > 0) {
            mOverlimit += 0.05d * pyrotheum / (pyrotheum + 5000.0d);
        }
        int cryotheum = drainAllOfFluid(FluidRegistry.getFluidStack(CRYOTHEUM_FLUID_NAME, 1));
        if (cryotheum > 0) {
            double reduction = 0.01d * cryotheum / (cryotheum + 1000.0d);
            if (reduction > mInstability) {
                // 超扣：结构失温 → clamp 0 + 强制停机 + 全服聊天（先于停机发送）
                mInstability = 0.0d;
                GTSRMachineEvent event = newMachineEvent(GTSRSingularityStructChillEvent::new);
                event.setBroadcast(true)
                    .sendChat();
                getBaseMetaTileEntity().disableWorking();
            } else {
                mInstability -= reduction;
            }
        }
    }

    /**
     * 全量实扣指定流体（输入仓族 getSteamInputHatches()，与 sumGrade/drainGrade 同通道）：
     * 探测走 getTankInfo，命中后逐仓 drain MAX_VALUE 实扣（勿经 GTSRHatchFluidAccess 传 MAX_VALUE，其注释 :23-25 禁止），
     * 返回实际消耗总量（mB）。probe 为 null（流体未注册）或输入仓无此流体时返回 0。
     */
    protected final int drainAllOfFluid(@Nullable FluidStack probe) {
        if (probe == null) return 0;
        boolean present = false;
        for (MTEHatch hatch : getSteamInputHatches()) {
            FluidTankInfo[] tanks = hatch.getTankInfo(ForgeDirection.UNKNOWN);
            if (tanks == null) continue;
            for (FluidTankInfo tank : tanks) {
                if (tank != null && tank.fluid != null && tank.fluid.isFluidEqual(probe)) {
                    present = true;
                    break;
                }
            }
            if (present) break;
        }
        if (!present) return 0;
        FluidStack full = probe.copy();
        full.amount = Integer.MAX_VALUE;
        int drained = 0;
        for (MTEHatch hatch : getSteamInputHatches()) {
            FluidStack got = hatch.drain(ForgeDirection.UNKNOWN, full, true);
            if (got != null) drained += got.amount;
        }
        return drained;
    }

    /**
     * 爆炸链执行（v1.1 §2.7 时序）：纠缠奇点定位位顶替生成脱管失控奇点（type=RUNAWAY，spawn 内部 setBlock 即顶替）
     * → 全服聊天广播（先于爆炸发送）→ explodeMultiblock()。
     * RUNAWAY 分型受 T1-T3 类型门保护（自愈/停机回收/onRemoval 零触碰），独立存活至 duration 自毁。
     */
    protected final void detonateRunawaySingularity(GTSRMachineEvent event, double range, double speed, double damage,
        int duration, int attributeId, String color, double fxRadius) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        EntanglementSpec spec = getEntanglementSpec();
        if (base != null && spec != null) {
            TileRunawaySingularity.spawnSingularity(
                base.getWorld(),
                base.getXCoord() + spec.dx,
                base.getYCoord() + spec.dy,
                base.getZCoord() + spec.dz,
                range,
                speed,
                damage,
                duration,
                attributeId,
                color,
                fxRadius,
                TileRunawaySingularity.SingularityType.RUNAWAY); // 脱管失控分型：机器管理逻辑零触碰，独立存活至自毁
        }
        event.setBroadcast(true)
            .sendChat(); // 聊天先于爆炸发送
        explodeMultiblock();
    }

    /** 机器显示名 lang 键（gt.blockmachines.&lt;mName&gt;.name，GTSRMachineEvent machineKey 口径） */
    protected String getMachineDisplayNameKey() {
        return getLocalNameKey();
    }

    /** 事件子类统一构造器形状：(machineKey, x, y, z, dim) */
    @FunctionalInterface
    protected interface SingularityEventFactory<E extends GTSRMachineEvent> {

        E create(String machineKey, int x, int y, int z, int dim);
    }

    /** 以本机显示名与当前坐标维度实例化机器事件（供爆炸链/停机聊天使用） */
    protected final <E extends GTSRMachineEvent> E newMachineEvent(SingularityEventFactory<E> factory) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return factory.create(
            getMachineDisplayNameKey(),
            base.getXCoord(),
            base.getYCoord(),
            base.getZCoord(),
            base.getWorld().provider.dimensionId);
    }

    // endregion

    /**
     * 失控奇点渲染条件：默认机器工作（结构有效+允许工作+周期进行中或蒸汽尚存）才生成/保留奇点；
     * 致密态蒸汽操控装置覆写为结构成型且（允许工作或奇点模式进行中）。
     */
    protected boolean shouldRenderEntanglementSingularity(IGregTechTileEntity aBaseMetaTileEntity) {
        return mMachine && aBaseMetaTileEntity.isAllowedToWork()
            && (mMaxProgresstime > 0 || findHighestGrade(includeDenseSteam()) >= 0);
    }

    private void updateEntanglementSingularity(IGregTechTileEntity aBaseMetaTileEntity) {
        List<EntanglementSpec> specs = getEntanglementSpecs();
        if (specs.isEmpty()) return;
        // 启动豁免：控制器重载后结构判定延迟期间（GT mStartUpCheck≈5 秒），奇点判定同步豁免
        if (getmStartUpCheck() >= 0) return;
        // working：奇点渲染条件（默认结构有效+允许工作+周期进行中或蒸汽尚存，平滑周期间隙避免闪烁；
        // 各机器覆写条件见 shouldRenderEntanglementSingularity）
        boolean working = shouldRenderEntanglementSingularity(aBaseMetaTileEntity);
        World world = aBaseMetaTileEntity.getWorld();
        for (EntanglementSpec spec : specs) {
            int x = aBaseMetaTileEntity.getXCoord() + spec.dx;
            int y = aBaseMetaTileEntity.getYCoord() + spec.dy;
            int z = aBaseMetaTileEntity.getZCoord() + spec.dz;
            if (working) {
                Block block = world.getBlock(x, y, z);
                // 惰性生成：仅当定位点为空气才放置（不覆盖已有奇点 NBT，无比持久化）
                if (block.isAir(world, x, y, z)) {
                    TileRunawaySingularity.spawnSingularity(
                        world,
                        x,
                        y,
                        z,
                        spec.range,
                        spec.speed,
                        spec.damage,
                        spec.duration,
                        spec.attributeId,
                        spec.color,
                        spec.fxRadius,
                        TileRunawaySingularity.SingularityType.STABLE); // 机器生成托管分型：受三处类型门管辖
                } else if (block == BlocksGTSR.runawaySingularity) {
                    // 参数修复（自愈）：仅限 STABLE——与规格不符（如 NBT 丢失回退默认 600 tick）时重新应用，
                    // 防止 30 秒自毁或异常行为；elapsedTicks 不受影响。
                    // 类型门：NATURAL/RUNAWAY（含与 spec 位置重合的自然奇点）一律跳过，防止误改写
                    if (world.getTileEntity(x, y, z) instanceof TileRunawaySingularity t
                        && t.getType() == TileRunawaySingularity.SingularityType.STABLE
                        && (t.getRange() != spec.range || t.getSpeed() != spec.speed
                            || t.getDamage() != spec.damage
                            || t.getDuration() != spec.duration
                            || t.getAttributeId() != spec.attributeId
                            || !spec.color.equals(t.getColor())
                            || t.getFxRadius() != spec.fxRadius)) {
                        t.setParams(
                            spec.range,
                            spec.speed,
                            spec.damage,
                            spec.duration,
                            spec.attributeId,
                            spec.color,
                            spec.fxRadius);
                        t.markDirty();
                    }
                }
            } else if (world.getBlock(x, y, z) == BlocksGTSR.runawaySingularity
                && world.getTileEntity(x, y, z) instanceof TileRunawaySingularity t
                && t.getType() == TileRunawaySingularity.SingularityType.STABLE) {
                    // 立即惰性移除：关闭/挂机/结构破坏后下一次检查即消失（重载豁免见上方 getmStartUpCheck 门）；
                    // 类型门：仅移除 STABLE，NATURAL/RUNAWAY（含位置重合的自然奇点）保留
                    world.setBlockToAir(x, y, z);
                }
        }
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide()) return;
        // 仅当控制器方块已被移除（被拆）才清理奇点；区块卸载时方块仍在，保持奇点持久化
        if (!base.getWorld()
            .getBlock(base.getXCoord(), base.getYCoord(), base.getZCoord())
            .isAir(base.getWorld(), base.getXCoord(), base.getYCoord(), base.getZCoord())) {
            return;
        }
        for (EntanglementSpec spec : getEntanglementSpecs()) {
            int x = base.getXCoord() + spec.dx;
            int y = base.getYCoord() + spec.dy;
            int z = base.getZCoord() + spec.dz;
            if (base.getWorld()
                .getBlock(x, y, z) == BlocksGTSR.runawaySingularity
                && base.getWorld()
                    .getTileEntity(x, y, z) instanceof TileRunawaySingularity t
                && t.getType() == TileRunawaySingularity.SingularityType.STABLE) {
                // 类型门：仅清理 STABLE，NATURAL/RUNAWAY（含位置重合的自然奇点）保留
                base.getWorld()
                    .setBlockToAir(x, y, z);
            }
        }
    }

    @Override
    public int getMaxParallelRecipes() {
        return 1;
    }

    @Override
    public boolean isCorrectMachinePart(ItemStack aStack) {
        return true;
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    @Override
    public boolean supportsPowerPanel() {
        return false;
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new MTESingularityMachineGui<>(this);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        int casingIndex = getCasingTextureIndex();
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingIndex),
                TextureFactory.of(aActive ? OVERLAY_ON : OVERLAY_OFF) };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingIndex) };
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        String keyPrefix = getTooltipKeyPrefix();
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal(keyPrefix + "type"));
        tt.addInfo(StatCollector.translateToLocal(keyPrefix + "desc"));
        addSplitTooltipLines(tt, keyPrefix, "desc");
        tt.addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal(keyPrefix + "desc2"));
        addSplitTooltipLines(tt, keyPrefix, "desc2");
        tt.addInfo(EnumChatFormatting.GREEN + StatCollector.translateToLocal(keyPrefix + "desc3"));
        addSplitTooltipLines(tt, keyPrefix, "desc3");
        tt.addInfo(EnumChatFormatting.RED + StatCollector.translateToLocal(keyPrefix + "desc4"));
        addSplitTooltipLines(tt, keyPrefix, "desc4");
        tt.addInfo(EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal(keyPrefix + "desc5"));
        addSplitTooltipLines(tt, keyPrefix, "desc5");
        return tt;
    }

    /**
     * 在 descN 主行后追加可选拆行（descN_2/descN_3，AQUA 色）。
     * 拆行键在语言文件中不存在时不输出任何内容，与未配置拆行时的旧行为完全一致。
     */
    private void addSplitTooltipLines(MultiblockTooltipBuilder tt, String keyPrefix, String descKey) {
        for (int i = 2; i <= 3; i++) {
            String splitKey = keyPrefix + descKey + "_" + i;
            if (StatCollector.canTranslate(splitKey)) {
                tt.addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal(splitKey));
            }
        }
    }

    /**
     * 追加超限模式机制说明行（用户拍板追加，T5/T6 共用）：
     * overlimit_1..N 逐行（GOLD，GTNH 惯例每行一句、中文简洁），随后 overlimit_explode 爆炸链行（RED）。
     * 键不存在时不输出任何内容（与 addSplitTooltipLines 同策略），从 1 起连续编号、遇缺即止。
     */
    protected void addOverlimitTooltipLines(MultiblockTooltipBuilder tt, String keyPrefix) {
        for (int i = 1; i <= 8; i++) {
            String key = keyPrefix + "overlimit_" + i;
            if (!StatCollector.canTranslate(key)) break;
            tt.addInfo(EnumChatFormatting.GOLD + StatCollector.translateToLocal(key));
        }
        String explodeKey = keyPrefix + "overlimit_explode";
        if (StatCollector.canTranslate(explodeKey)) {
            tt.addInfo(EnumChatFormatting.RED + StatCollector.translateToLocal(explodeKey));
        }
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        return (d, r, f) -> d.offsetY == 0 && r.isNotRotated() && !f.isVerticallyFliped();
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mTier", mTier);
        aNBT.setDouble("mHeat", mHeat);
        // T4 超限三值（百分比口径）持久化
        aNBT.setDouble("mOverlimit", mOverlimit);
        aNBT.setDouble("mInstability", mInstability);
        aNBT.setDouble("mTear", mTear);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mTier = aNBT.getInteger("mTier");
        mHeat = aNBT.getDouble("mHeat");
        // getDouble 缺省 0 → 旧档兼容：老存档机器加载三值 0 正常运行
        mOverlimit = aNBT.getDouble("mOverlimit");
        mInstability = aNBT.getDouble("mInstability");
        mTear = aNBT.getDouble("mTear");
    }

    @Override
    public String[] getInfoData() {
        String tooltipKeyPrefix = getTooltipKeyPrefix();
        String guiKeyPrefix = getGuiKeyPrefix();
        ArrayList<String> info = new ArrayList<>();
        info.add(
            EnumChatFormatting.BLUE + StatCollector.translateToLocal(tooltipKeyPrefix + "type")
                + EnumChatFormatting.RESET);
        if (!mMachine) {
            info.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building"));
            return info.toArray(new String[0]);
        }
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal(guiKeyPrefix + "heat")
                + EnumChatFormatting.RED
                + String.format("%.1f%%", mHeat * 100.0d)
                + EnumChatFormatting.RESET);
        return info.toArray(new String[0]);
    }

    /** 纠缠奇点生成规格（D 定位块世界偏移 + NBT 参数）；null=本机不管理纠缠奇点 */
    public static class EntanglementSpec {

        public final int dx;
        public final int dy;
        public final int dz;
        public final double range;
        public final double speed;
        public final double damage;
        public final int duration;
        public final int attributeId;
        public final String color;
        public final double fxRadius;

        public EntanglementSpec(int dx, int dy, int dz, double range, double speed, double damage, int duration,
            int attributeId, String color, double fxRadius) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.range = range;
            this.speed = speed;
            this.damage = damage;
            this.duration = duration;
            this.attributeId = attributeId;
            this.color = color;
            this.fxRadius = fxRadius;
        }
    }

    @Nullable
    protected EntanglementSpec getEntanglementSpec() {
        return null;
    }

    /**
     * 纠缠奇点生成规格列表（多节点机器可覆盖返回多元素）；默认转发单例。
     */
    protected List<EntanglementSpec> getEntanglementSpecs() {
        EntanglementSpec spec = getEntanglementSpec();
        if (spec == null) return Collections.emptyList();
        return Collections.singletonList(spec);
    }
}
