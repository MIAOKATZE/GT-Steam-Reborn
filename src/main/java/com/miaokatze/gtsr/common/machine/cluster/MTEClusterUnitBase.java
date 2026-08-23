package com.miaokatze.gtsr.common.machine.cluster;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.ITierConverter;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.common.fluid.FluidStackTank;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRMultiBlockBase;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * 全部集群单元控制器的多方块基类。
 *
 * <p>
 * 单元自身先独立成型，才可由集群总控的 F 槽收集。结构未成型时，状态为红色且所有能力闸门关闭；
 * 集群侧的 collect/connect、周期重连和读档重连仍沿用原有双向引用契约。
 */
public abstract class MTEClusterUnitBase<T extends MTEClusterUnitBase<T>> extends MTEGTSRMultiBlockBase<T>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int TANK_CAPACITY = 16_000;

    /** A 外壳族：与集群总控相同的四档 tier 顺序。 */
    private static final List<Pair<Block, Integer>> CASING_FAMILY = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockCasings1, 10),
        Pair.of(GregTechAPI.sBlockCasings2, 0),
        Pair.of(GregTechAPI.sBlockCasings4, 2),
        Pair.of(GregTechAPI.sBlockCasings4, 0));

    /** B 管道族：与集群总控相同的四档 tier 顺序。 */
    private static final List<Pair<Block, Integer>> PIPE_FAMILY = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockCasings2, 12),
        Pair.of(GregTechAPI.sBlockCasings2, 13),
        Pair.of(GregTechAPI.sBlockCasings2, 14),
        Pair.of(GregTechAPI.sBlockCasings2, 15));

    /** C 燃烧室族：与集群总控相同的四档 tier 顺序。 */
    private static final List<Pair<Block, Integer>> FIREBOX_FAMILY = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockCasings3, 13),
        Pair.of(GregTechAPI.sBlockCasings3, 14),
        Pair.of(GregTechAPI.sBlockCasings4, 3),
        Pair.of(GregTechAPI.sBlockCasings3, 15));

    /** D 框架族：与集群总控相同的四档 tier 顺序。 */
    private static final List<Pair<Block, Integer>> FRAME_FAMILY = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockFrames, 300),
        Pair.of(GregTechAPI.sBlockFrames, 305),
        Pair.of(GregTechAPI.sBlockFrames, 28),
        Pair.of(GregTechAPI.sBlockFrames, 316));

    /** 单元自持小型流体槽，保留原 MTEBasicTank 公有 tank API。 */
    protected FluidStack mFluid;
    public final FluidStackTank fluidTank = new FluidStackTank(
        () -> mFluid,
        fluid -> mFluid = fluid,
        this::getRealCapacity);

    /** 所属集群总控引用；未入集群时为 null。 */
    protected MTESteamMineralLogisticsCluster cluster;

    /** 集群结构 tier 下标；未被总控成型扫描收集时为 -1。 */
    protected int structureTier = -1;

    /** 本单元自身多方块的 A 族 tier，仅供自身结构校验，绝不覆盖总控下发的 structureTier。 */
    private int unitStructureTier = -1;

    /** 所在总控 F 垫位；未收集时为 -1。 */
    protected int padId = -1;

    /** 所在总控结构段；未收集时为 -1。 */
    protected int segmentIndex = -1;

    protected MTEClusterUnitBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    protected MTEClusterUnitBase(String aName) {
        super(aName);
    }

    /** 子类返回它的 canonical [Z][Y][X] 草稿结构。 */
    protected abstract String[][] getUnitShape();

    /** 子类注册结构中除 A 外的专有字符元素。 */
    @SuppressWarnings("rawtypes")
    protected abstract void addUnitStructureElements(StructureDefinition.Builder builder);

    /** 控制器在 canonical 结构中的 (X, Y, Z) 偏移。 */
    protected abstract int getStructureOffsetA();

    protected abstract int getStructureOffsetB();

    protected abstract int getStructureOffsetC();

    @Override
    public IStructureDefinition<T> getStructureDefinition() {
        StructureDefinition.Builder<T> builder = StructureDefinition.<T>builder()
            .addShape(STRUCTURE_PIECE_MAIN, getUnitShape())
            .addElement(
                'A',
                ofBlocksTiered(
                    MTEClusterUnitBase::getCasingTier,
                    CASING_FAMILY,
                    -1,
                    (t, tier) -> t.onUnitStructureTier(tier),
                    T::getUnitStructureTier));
        addUnitStructureElements(builder);
        return builder.build();
    }

    /** 子结构成型时的 tier 回调，同样是总控对外冻结 API 的状态字段。 */
    protected final void onUnitStructureTier(int tier) {
        unitStructureTier = tier;
    }

    protected final int getUnitStructureTier() {
        return unitStructureTier;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected static void addPipeElement(StructureDefinition.Builder builder) {
        addTieredElement(builder, 'B', PIPE_FAMILY, MTEClusterUnitBase::getPipeTier);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected static void addFireboxElement(StructureDefinition.Builder builder) {
        addTieredElement(builder, 'C', FIREBOX_FAMILY, MTEClusterUnitBase::getFireboxTier);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected static void addFrameElement(StructureDefinition.Builder builder) {
        addTieredElement(builder, 'D', FRAME_FAMILY, MTEClusterUnitBase::getFrameTier);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void addTieredElement(StructureDefinition.Builder builder, char key,
        List<Pair<Block, Integer>> family, ITierConverter<Integer> resolver) {
        builder.addElement(
            key,
            ofBlocksTiered(
                resolver,
                family,
                -1,
                (MTEClusterUnitBase t, Integer tier) -> t.onUnitStructureTier(tier),
                (MTEClusterUnitBase t) -> t.getUnitStructureTier()));
    }

    private static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings1 && meta == 10) return 0;
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 1;
        if (block == GregTechAPI.sBlockCasings4 && meta == 2) return 2;
        if (block == GregTechAPI.sBlockCasings4 && meta == 0) return 3;
        return null;
    }

    private static Integer getPipeTier(Block block, int meta) {
        if (block != GregTechAPI.sBlockCasings2) return null;
        if (meta == 12) return 0;
        if (meta == 13) return 1;
        if (meta == 14) return 2;
        if (meta == 15) return 3;
        return null;
    }

    private static Integer getFireboxTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings3) {
            if (meta == 13) return 0;
            if (meta == 14) return 1;
            if (meta == 15) return 3;
        }
        if (block == GregTechAPI.sBlockCasings4 && meta == 3) return 2;
        return null;
    }

    private static Integer getFrameTier(Block block, int meta) {
        if (block != GregTechAPI.sBlockFrames) return null;
        if (meta == 300) return 0;
        if (meta == 305) return 1;
        if (meta == 28) return 2;
        if (meta == 316) return 3;
        return null;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            hintsOnly,
            getStructureOffsetA(),
            getStructureOffsetB(),
            getStructureOffsetC());
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            getStructureOffsetA(),
            getStructureOffsetB(),
            getStructureOffsetC(),
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        unitStructureTier = -1;
        if (!checkPiece(
            STRUCTURE_PIECE_MAIN,
            getStructureOffsetA(),
            getStructureOffsetB(),
            getStructureOffsetC(),
            errors)) return;
        if (unitStructureTier < 0) {
            errors.add(gregtech.api.structure.error.StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
        }
    }

    /** 总控结构元素收集时调用：记录所属集群引用。 */
    public void connect(MTESteamMineralLogisticsCluster cluster) {
        this.cluster = cluster;
    }

    /** 脱离集群时调用：仅清空集群引用。 */
    public void disconnect() {
        this.cluster = null;
    }

    @Override
    public void onRemoval() {
        if (cluster != null) cluster.onUnitRemoved(this);
        super.onRemoval();
    }

    public MTESteamMineralLogisticsCluster getCluster() {
        return cluster;
    }

    /** 总控结构元素收集时调用：记录所在垫位与段号。 */
    public void onCollected(int padId, int segmentIndex) {
        this.padId = padId;
        this.segmentIndex = segmentIndex;
    }

    public int getPadId() {
        return padId;
    }

    public int getSegmentIndex() {
        return segmentIndex;
    }

    /** 总控在自身结构完全成型后下发统一 tier。 */
    public void onStructureTier(int tier) {
        this.structureTier = tier;
    }

    public int getStructureTier() {
        return structureTier;
    }

    /** 单元多方块自身是否成型。 */
    public final boolean isUnitStructureFormed() {
        return mMachine;
    }

    /** 集群连接与单元自身成型齐备才允许功能开启；总控 tier 在连接后单独下发。 */
    public boolean isModuleEnabled() {
        return cluster != null && mMachine;
    }

    public abstract String getUnitTypeNameKey();

    /** 未成型优先显示红色，避免总控收集到尚未成型的控制器时误显示可用。 */
    public ClusterUnitStatus getUnitStatus() {
        if (!mMachine) return ClusterUnitStatus.NO_POWER_OR_INVALID;
        return cluster == null ? ClusterUnitStatus.STANDBY : ClusterUnitStatus.WORKING;
    }

    /** MTEBasicTank 兼容访问器。 */
    public FluidStackTank getFluidTank() {
        return fluidTank;
    }

    @Override
    public boolean isValidSlot(int aIndex) {
        return aIndex > 0;
    }

    public int getInputSlot() {
        return 0;
    }

    public int getOutputSlot() {
        return 1;
    }

    public int getStackDisplaySlot() {
        return 2;
    }

    /** 不做手工容器灌装。 */
    public boolean doesFillContainers() {
        return false;
    }

    /** 不做手工容器排空。 */
    public boolean doesEmptyContainers() {
        return false;
    }

    public boolean canTankBeFilled() {
        return true;
    }

    public boolean canTankBeEmptied() {
        return true;
    }

    public int getCapacity() {
        return TANK_CAPACITY;
    }

    public boolean isFluidInputAllowed(FluidStack aFluid) {
        return true;
    }

    public FluidStack getFillableStack() {
        return mFluid;
    }

    public FluidStack setFillableStack(FluidStack aFluid) {
        mFluid = aFluid;
        return mFluid;
    }

    public FluidStack getDrainableStack() {
        return mFluid;
    }

    public FluidStack setDrainableStack(FluidStack aFluid) {
        mFluid = aFluid;
        return mFluid;
    }

    public FluidStack getFluid() {
        return getDrainableStack();
    }

    public int getFluidAmount() {
        FluidStack fluid = getDrainableStack();
        return fluid == null ? 0 : fluid.amount;
    }

    public int fill(FluidStack aFluid, boolean doFill) {
        if (aFluid == null || aFluid.getFluid() == null
            || aFluid.amount <= 0
            || !canTankBeFilled()
            || !isFluidInputAllowed(aFluid)) return 0;
        if (mFluid == null || mFluid.getFluid() != aFluid.getFluid()) {
            if (mFluid != null) return 0;
            int filled = Math.min(aFluid.amount, getCapacity());
            if (doFill) {
                mFluid = aFluid.copy();
                mFluid.amount = filled;
                markDirty();
            }
            return filled;
        }
        int filled = Math.min(aFluid.amount, getCapacity() - mFluid.amount);
        if (filled > 0 && doFill) {
            mFluid.amount += filled;
            markDirty();
        }
        return filled;
    }

    public FluidStack drain(int maxDrain, boolean doDrain) {
        if (maxDrain <= 0 || mFluid == null || !canTankBeEmptied()) return null;
        int drainedAmount = Math.min(maxDrain, mFluid.amount);
        FluidStack drained = mFluid.copy();
        drained.amount = drainedAmount;
        if (doDrain) {
            mFluid.amount -= drainedAmount;
            if (mFluid.amount <= 0) mFluid = null;
            markDirty();
        }
        return drained;
    }

    public FluidStack drain(ForgeDirection side, FluidStack requested, int maxDrain, boolean doDrain) {
        if (requested == null || mFluid == null || !mFluid.isFluidEqual(requested)) return null;
        return drain(maxDrain, doDrain);
    }

    public FluidTankInfo[] getTankInfo(ForgeDirection side) {
        return new FluidTankInfo[] { new FluidTankInfo(mFluid, getCapacity()) };
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        if (mFluid != null) aNBT.setTag("mFluid", mFluid.writeToNBT(new NBTTagCompound()));
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mFluid = FluidStack.loadFluidStackFromNBT(aNBT.getCompoundTag("mFluid"));
    }

    /** 单元只充当结构与能力控制器，永不走 GT 配方执行。 */
    @Override
    public CheckRecipeResult checkProcessing() {
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    /**
     * 单元控制器贴图（12 变体统一继承本实现，不各自覆写）：底材镀铜砖块
     * （gregtech:gt.blockcasings meta10 = Casing_BronzePlatedBricks）+ 正面采矿钻头叠层
     * （OVERLAY_FRONT_ORE_DRILL）区分启停，范式对齐总控与 MTECrustMatterAggregator。
     */
    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstone) {
        int casingIndex = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10);
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingIndex),
                TextureFactory.of(
                    active ? Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE
                        : Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL) };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingIndex) };
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return new MultiblockTooltipBuilder().addMachineType(StatCollector.translateToLocal(getUnitTypeNameKey()))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.pad_hint"))
            .beginStructureBlock(1, 1, 1, true)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.pad_hint"))
            .toolTipFinisher("GTSR");
    }

    @Override
    public abstract IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity);
}
