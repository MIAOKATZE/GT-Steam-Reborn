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
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.blocks.TileRunawaySingularity;
import com.miaokatze.gtsr.common.gui.MTESingularityMachineGui;
import com.miaokatze.gtsr.loader.BlockLoader;

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
public abstract class MTESingularityMachineBase extends MTESingularityModeMachineBase<MTESingularityMachineBase> {

    protected static final int CYCLE_LENGTH = 20;
    protected static final double HEAT_DECAY_PER_SECOND = 0.01d;
    protected static final double[] GRADE_COEF = { 0.5d, 1.0d, 2.0d };
    protected static final String[] DENSE_FLUID_NAMES = { "densesteam", "densesuperheatedsteam",
        "densesupercriticalsteam" };
    protected static final String[] NORMAL_FLUID_NAMES = { "steam", "ic2superheatedsteam", "supercriticalsteam" };

    private static IIconContainer OVERLAY_OFF;
    private static IIconContainer OVERLAY_ON;

    public int mTier = 0;
    public double mHeat = 0.0d;

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
            return GTUtility.translate(translationKey);
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
        mHeat += GRADE_COEF[grade] * base;
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

    protected final boolean consumeSingularityFromInputBuses(int amount) {
        ItemStack singularity = GTSRItemList.SteamEntangledSingularity.get(1);
        if (singularity == null) return false;
        for (MTEHatchInputBus bus : mInputBusses) {
            for (int i = 0; i < bus.getSizeInventory(); i++) {
                ItemStack stack = bus.getStackInSlot(i);
                if (stack != null && stack.getItem() == singularity.getItem() && stack.stackSize >= amount) {
                    bus.decrStackSize(i, amount);
                    return true;
                }
            }
        }
        // v1.10.8 修复：移除 mDualInputHatches.getAllItems() 活引用直接扣减——
        // 对 CraftingInputME 改写模式内嵌物品栈会损坏模式数据/存档复活（复制燃料），
        // 对返回副本的实现则扣减静默失效（免费燃料）。奇点燃料仅走输入总线（decrStackSize 安全语义）。
        return false;
    }

    protected boolean shouldDecayHeat() {
        return true;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide() || aTick % CYCLE_LENGTH != 0L) return;
        updateEntanglementSingularity(aBaseMetaTileEntity);
        if (!shouldDecayHeat()) return;
        if (!mMachine || !aBaseMetaTileEntity.isAllowedToWork()) {
            mHeat = Math.max(0.0d, mHeat - HEAT_DECAY_PER_SECOND);
            return;
        }
        if (mMaxProgresstime <= 0 && findHighestGrade(includeDenseSteam()) < 0) {
            mHeat = Math.max(0.0d, mHeat - HEAT_DECAY_PER_SECOND);
        }
    }

    /**
     * 失控奇点渲染条件：默认机器工作（结构有效+允许工作+周期进行中或蒸汽尚存）才生成/保留奇点；
     * 临界纠缠奇点稳定装置覆写为结构成型且允许工作；致密态蒸汽操控装置覆写为结构成型且
     * （允许工作或奇点模式进行中）。
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
                        spec.fxRadius);
                } else if (block == BlockLoader.blockRunawaySingularity) {
                    // 参数修复（自愈）：与规格不符（如 NBT 丢失回退默认 600 tick）时重新应用，
                    // 防止 30 秒自毁或异常行为；elapsedTicks 不受影响
                    if (world.getTileEntity(x, y, z) instanceof TileRunawaySingularity t
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
            } else if (world.getBlock(x, y, z) == BlockLoader.blockRunawaySingularity) {
                // 立即惰性移除：关闭/挂机/结构破坏后下一次检查即消失（重载豁免见上方 getmStartUpCheck 门）
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
                .getBlock(x, y, z) == BlockLoader.blockRunawaySingularity) {
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
        tt.addMachineType(StatCollector.translateToLocal(keyPrefix + "type"))
            .addInfo(StatCollector.translateToLocal(keyPrefix + "desc"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal(keyPrefix + "desc2"))
            .addInfo(EnumChatFormatting.GREEN + StatCollector.translateToLocal(keyPrefix + "desc3"))
            .addInfo(EnumChatFormatting.RED + StatCollector.translateToLocal(keyPrefix + "desc4"))
            .addInfo(EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal(keyPrefix + "desc5"));
        return tt;
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
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mTier = aNBT.getInteger("mTier");
        mHeat = aNBT.getDouble("mHeat");
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
