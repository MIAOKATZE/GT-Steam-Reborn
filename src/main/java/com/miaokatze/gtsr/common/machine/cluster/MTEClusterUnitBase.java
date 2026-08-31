package com.miaokatze.gtsr.common.machine.cluster;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.isAir;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.common.fluid.FluidStackTank;
import com.miaokatze.gtsr.common.gui.cluster.MTEClusterUnitNativeGui;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRMultiBlockBase;
import com.miaokatze.gtsr.common.util.GTSRUtils;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
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
 *
 * <p>
 * tier 分族（r9 扩为七族）：A 外壳 / B 齿轮箱 / C 管道 / D 框架 / C' 燃烧室 / 金属 / 线圈各族独立
 * 记录匹配 tier，{@link #checkMachine} 开头 {@link #resetTierFamilies()} 全复位，结构检查后所有参与
 * tier 判定的族必须存在且同级才得出 {@link #getUnitStructureTier()}——跨 tier 混搭不成型；主控下发的集群
 * tier 经 clusterTier 独立保存（{@link #getClusterTier()}），{@link #isTierValidForConnection()}
 * 要求两者一致。单元底材贴图、增幅数值、物流链蒸汽 tier 取单元自身已验证 tier；集群拓扑仍以
 * 主控结构 tier 为全局等级。
 *
 * <p>
 * 零内部容量（3.4.5）：16000L 通用内部槽改为可选——{@link #enableInternalFluidTank()} 仅增幅
 * 子类构造期启用；加工/物流子类不启用并覆写 {@link #getCapacity()}/{@link #getFillableStack()}/
 * {@link #getDrainableStack()}/{@link #fill(FluidStack, boolean)}/{@link #drain(int, boolean)}/
 * {@link #getTankInfo(ForgeDirection)} 及 NBT tank 路径收紧为拒绝/空（详见各方法契约注释）。
 *
 * <p>
 * 独立运行信号（3.4.6）：{@link #onPostTick} 服务端末尾以 {@link #isUnitRunning()} 驱动
 * {@code setActive}（范式同 MTESingularityDrillingHub），不复用 {@link #checkProcessing()}
 * （恒 NO_RECIPE）；底材贴图随 unitStructureTier 四档联动（客户端 byte 通道镜像 + NBT 兜底，
 * 范式同 MTELargeSteamFurnace）。
 */
public abstract class MTEClusterUnitBase<T extends MTEClusterUnitBase<T>> extends MTEGTSRMultiBlockBase<T>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int TANK_CAPACITY = 16_000;

    /**
     * 模块成型后定点反解所属总控的搜索半径（Chebyshev 格）：覆盖满配 9 延伸段最远挂点到总控
     * 控制器的距离（约 8+8k，k=9 时 80 格）；只在 checkMachine 成功边沿用一次。
     */
    private static final int CLUSTER_SEARCH_RADIUS = 80;

    /** A 外壳族：与集群总控相同的四档 tier 顺序。 */
    private static final List<Pair<Block, Integer>> CASING_FAMILY = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockCasings1, 10),
        Pair.of(GregTechAPI.sBlockCasings2, 0),
        Pair.of(GregTechAPI.sBlockCasings4, 2),
        Pair.of(GregTechAPI.sBlockCasings4, 0));

    /** B 齿轮箱族：gt.blockcasings2 meta 2/3/4/5（青铜/钢/钛/钨钢齿轮箱），与管道族语义严格分离。 */
    private static final List<Pair<Block, Integer>> GEARBOX_FAMILY = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockCasings2, 2),
        Pair.of(GregTechAPI.sBlockCasings2, 3),
        Pair.of(GregTechAPI.sBlockCasings2, 4),
        Pair.of(GregTechAPI.sBlockCasings2, 5));

    /** C 管道族：与集群总控相同的四档 tier 顺序。 */
    private static final List<Pair<Block, Integer>> PIPE_FAMILY = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockCasings2, 12),
        Pair.of(GregTechAPI.sBlockCasings2, 13),
        Pair.of(GregTechAPI.sBlockCasings2, 14),
        Pair.of(GregTechAPI.sBlockCasings2, 15));

    /** D 框架族：与集群总控相同的四档 tier 顺序。 */
    private static final List<Pair<Block, Integer>> FRAME_FAMILY = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockFrames, 300),
        Pair.of(GregTechAPI.sBlockFrames, 305),
        Pair.of(GregTechAPI.sBlockFrames, 28),
        Pair.of(GregTechAPI.sBlockFrames, 316));

    /**
     * 金属族（r9，磁选 'b'）：铁块 0 档 / 钢块（gt.blockmetal6:13）/ 钕块（gt.blockmetal5:0）/
     * 钐块（gt.blockmetal6:5），四档 tier 顺序（权威规格 plan/结构）。
     */
    private static final List<Pair<Block, Integer>> METAL_FAMILY = ImmutableList.of(
        Pair.of(Blocks.iron_block, 0),
        Pair.of(GregTechAPI.sBlockMetal6, 13),
        Pair.of(GregTechAPI.sBlockMetal5, 0),
        Pair.of(GregTechAPI.sBlockMetal6, 5));

    /**
     * 线圈族（r9，热离 'a'）：gt.blockcasings5 白铜(:0) / 坎塔尔(:1) / 钛铂钒(:3) / HSS-G(:4)。
     * ⚠ 第四档为 meta 4（GT5U lang 证据，规格注释误写 meta 0，唯一授权偏离）。
     */
    private static final List<Pair<Block, Integer>> COIL_FAMILY = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockCasings5, 0),
        Pair.of(GregTechAPI.sBlockCasings5, 1),
        Pair.of(GregTechAPI.sBlockCasings5, 3),
        Pair.of(GregTechAPI.sBlockCasings5, 4));

    /**
     * tier→外壳底材纹理索引（3.5.2）：0 青铜镀铜砖 / 1 钢 / 2 钛 / 3 钨钢。类加载期一次解析，
     * getTexture 体内零动态贴图查找（NEI 红线），底材四档切换经此常量表。
     */
    private static final int[] TIER_CASING_TEXTURE_IDS = {
        GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10),
        GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0),
        GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 2),
        GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 0) };

    /** 内部流体槽内容（增幅缓冲，3.4.5 增幅子类豁免）；未启用内部槽的子类恒为 null。 */
    protected FluidStack mFluid;

    /** 可选内部 tank 视图；仅 {@link #enableInternalFluidTank()}（增幅子类构造期）启用，默认 null。 */
    protected FluidStackTank fluidTank;

    /** 所属集群总控引用；未入集群时为 null。 */
    protected MTESteamMineralLogisticsCluster cluster;

    /** 主控下发的集群 tier；未收集时为 -1（运行期由总控重下发，不入 NBT）。 */
    protected int clusterTier = -1;

    /** A 外壳族本次成型匹配的 tier；未参与/未复位后为 -1。 */
    private int casingFamilyTier = -1;

    /** B 齿轮箱族本次成型匹配的 tier；未参与为 -1。 */
    private int gearboxFamilyTier = -1;

    /** C 管道族本次成型匹配的 tier；未参与为 -1。 */
    private int pipeFamilyTier = -1;

    /** D 框架族本次成型匹配的 tier；未参与为 -1。 */
    private int frameFamilyTier = -1;

    /** C' 燃烧室族（r9 熔炉模块 D 位同族）本次成型匹配的 tier；未参与为 -1。 */
    private int fireboxFamilyTier = -1;

    /** 金属族（r9 磁选 'b'）本次成型匹配的 tier；未参与为 -1。 */
    private int metalFamilyTier = -1;

    /** 线圈族（r9 热离 'a'）本次成型匹配的 tier；未参与为 -1。 */
    private int coilFamilyTier = -1;

    /** 单元自身多方块验证后的 tier（分族同级校验通过才落值）；客户端经 byte 通道镜像用于贴图。 */
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

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public IStructureDefinition<T> getStructureDefinition() {
        StructureDefinition.Builder<T> builder = StructureDefinition.<T>builder()
            .addShape(STRUCTURE_PIECE_MAIN, getUnitShape())
            .addElement('A', tieredCasingElement());
        addUnitStructureElements(builder);
        return builder.build();
    }

    /** checkMachine 开头调用：全部 tier 分族与派生 unitStructureTier 复位 -1，杜绝上次扫描残留。 */
    protected void resetTierFamilies() {
        casingFamilyTier = -1;
        gearboxFamilyTier = -1;
        pipeFamilyTier = -1;
        frameFamilyTier = -1;
        fireboxFamilyTier = -1;
        metalFamilyTier = -1;
        coilFamilyTier = -1;
        unitStructureTier = -1;
    }

    /** A 外壳族 tier 元素（青铜/钢/钛/钨钢四档）：匹配写 casingFamilyTier，提示读该族当前 tier。 */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected IStructureElement tieredCasingElement() {
        return ofBlocksTiered(
            MTEClusterUnitBase::getCasingTier,
            CASING_FAMILY,
            -1,
            (MTEClusterUnitBase t, Integer tier) -> t.casingFamilyTier = tier,
            (MTEClusterUnitBase t) -> t.casingFamilyTier);
    }

    /**
     * B 齿轮箱族 tier 元素（gt.blockcasings2 meta 2/3/4/5）：新建 helper，不复用/不污染现有
     * 管道族（meta 12-15）语义。匹配写 gearboxFamilyTier。
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected IStructureElement tieredGearboxElement() {
        return ofBlocksTiered(
            MTEClusterUnitBase::getGearboxTier,
            GEARBOX_FAMILY,
            -1,
            (MTEClusterUnitBase t, Integer tier) -> t.gearboxFamilyTier = tier,
            (MTEClusterUnitBase t) -> t.gearboxFamilyTier);
    }

    /** C 管道族 tier 元素（gt.blockcasings2 meta 12/13/14/15）：匹配写 pipeFamilyTier。 */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected IStructureElement tieredPipeElement() {
        return ofBlocksTiered(
            MTEClusterUnitBase::getPipeTier,
            PIPE_FAMILY,
            -1,
            (MTEClusterUnitBase t, Integer tier) -> t.pipeFamilyTier = tier,
            (MTEClusterUnitBase t) -> t.pipeFamilyTier);
    }

    /** D 框架族 tier 元素：匹配写 frameFamilyTier。 */
    @SuppressWarnings("rawtypes")
    protected IStructureElement tieredFrameElement() {
        return ofBlocksTiered(
            MTEClusterUnitBase::getFrameTier,
            FRAME_FAMILY,
            -1,
            (MTEClusterUnitBase t, Integer tier) -> t.frameFamilyTier = tier,
            (MTEClusterUnitBase t) -> t.frameFamilyTier);
    }

    /** C' 燃烧室族 tier 元素（r9，与集群总控 FIREBOX_FAMILY 同族）：匹配写 fireboxFamilyTier。 */
    @SuppressWarnings("rawtypes")
    protected IStructureElement tieredFireboxElement() {
        return ofBlocksTiered(
            ClusterStructureDef::getFireboxTier,
            ClusterStructureDef.FIREBOX_FAMILY,
            -1,
            (MTEClusterUnitBase t, Integer tier) -> t.fireboxFamilyTier = tier,
            (MTEClusterUnitBase t) -> t.fireboxFamilyTier);
    }

    /** 金属族 tier 元素（r9，磁选 'b'：铁/钢/钕/钐）：匹配写 metalFamilyTier。 */
    @SuppressWarnings("rawtypes")
    protected IStructureElement tieredMetalElement() {
        return ofBlocksTiered(
            MTEClusterUnitBase::getMetalTier,
            METAL_FAMILY,
            -1,
            (MTEClusterUnitBase t, Integer tier) -> t.metalFamilyTier = tier,
            (MTEClusterUnitBase t) -> t.metalFamilyTier);
    }

    /** 线圈族 tier 元素（r9，热离 'a'：白铜/坎塔尔/钛铂钒/HSS-G）：匹配写 coilFamilyTier。 */
    @SuppressWarnings("rawtypes")
    protected IStructureElement tieredCoilElement() {
        return ofBlocksTiered(
            MTEClusterUnitBase::getCoilTier,
            COIL_FAMILY,
            -1,
            (MTEClusterUnitBase t, Integer tier) -> t.coilFamilyTier = tier,
            (MTEClusterUnitBase t) -> t.coilFamilyTier);
    }

    /** GT 玻璃元素（r9 helper：加工族 E 位共用，gt.blockglass1:10）。 */
    @SuppressWarnings("rawtypes")
    protected IStructureElement glassElement() {
        return ofBlock(GregTechAPI.sBlockGlass1, 10);
    }

    /** 严格空气元素（r9 helper：'-' 与 'e' 粒子候选空气位共用）。 */
    @SuppressWarnings("rawtypes")
    protected IStructureElement airElement() {
        return isAir();
    }

    /** 过渡 helper：'B'=管道族（E2b 后子类改用 tieredGearboxElement()/tieredPipeElement()）。 */
    @SuppressWarnings("rawtypes")
    protected void addPipeElement(StructureDefinition.Builder builder) {
        builder.addElement('B', tieredPipeElement());
    }

    private static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings1 && meta == 10) return 0;
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 1;
        if (block == GregTechAPI.sBlockCasings4 && meta == 2) return 2;
        if (block == GregTechAPI.sBlockCasings4 && meta == 0) return 3;
        return null;
    }

    private static Integer getGearboxTier(Block block, int meta) {
        if (block != GregTechAPI.sBlockCasings2) return null;
        if (meta == 2) return 0;
        if (meta == 3) return 1;
        if (meta == 4) return 2;
        if (meta == 5) return 3;
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

    private static Integer getFrameTier(Block block, int meta) {
        if (block != GregTechAPI.sBlockFrames) return null;
        if (meta == 300) return 0;
        if (meta == 305) return 1;
        if (meta == 28) return 2;
        if (meta == 316) return 3;
        return null;
    }

    /** 金属族 tier 反解（r9 磁选 'b'）：铁块 0 / 钢 1 / 钕 2 / 钐 3。 */
    private static Integer getMetalTier(Block block, int meta) {
        if (block == Blocks.iron_block && meta == 0) return 0;
        if (block == GregTechAPI.sBlockMetal6) {
            if (meta == 13) return 1;
            if (meta == 5) return 3;
            return null;
        }
        if (block == GregTechAPI.sBlockMetal5 && meta == 0) return 2;
        return null;
    }

    /** 线圈族 tier 反解（r9 热离 'a'）：白铜 0 / 坎塔尔 1 / 钛铂钒 3 档(:3) / HSS-G 3 档(:4)。 */
    private static Integer getCoilTier(Block block, int meta) {
        if (block != GregTechAPI.sBlockCasings5) return null;
        if (meta == 0) return 0;
        if (meta == 1) return 1;
        if (meta == 3) return 2;
        if (meta == 4) return 3;
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
        resetTierFamilies();
        if (!checkPiece(
            STRUCTURE_PIECE_MAIN,
            getStructureOffsetA(),
            getStructureOffsetB(),
            getStructureOffsetC(),
            errors)) return;
        int resolved = resolveUnitStructureTier();
        if (resolved == -1) {
            errors.add(gregtech.api.structure.error.StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (resolved == -2) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.tier_mismatch"));
            return;
        }
        unitStructureTier = resolved;
        // 终验反馈缺陷1根因B：模块成型成功且尚未接入集群时，定点反解挂点所属总控并触发其事件式
        // 重检（仅此一 tick 的一次性查询，非周期扫描；见 requestClusterRecheckOnFormation）
        if (cluster == null && aBaseMetaTileEntity != null && aBaseMetaTileEntity.isServerSide()) {
            requestClusterRecheckOnFormation(aBaseMetaTileEntity);
        }
    }

    /**
     * 模块成型后的定点总控反解（终验反馈缺陷1根因B）：模块接入集群的唯一入口是总控 checkMachine
     * 的挂点 unitSlot 收集，而总控只在自身放置/单元移除/事件式重检时扫描——先建集群后放模块时模块
     * 永远接不上（模块自身成型成功没有任何路径通知集群）。本方法在模块 checkMachine 成功那一 tick
     * 执行一次：遍历维度已加载 TileEntity 列表（远轻于逐格枚举），命中与模块控制器距离不超过
     * {@value #CLUSTER_SEARCH_RADIUS} 格的 {@link MTESteamMineralLogisticsCluster} 且其结构已
     * 成型时调用其 {@code requestStructureRecheck()} 置 mStartUpCheck=100（与总控 onUnitRemoved
     * 同口径的事件式单次重检），100 tick 后总控 checkMachine 扫到挂点即接入本模块。半径 80 覆盖
     * 满配 9 延伸段最远挂点到总控的距离（约 8+8k，k=9 时 80 格）；多总控场景下各自重检均只接入
     * 自己挂点上的模块，无串扰。查询不到（集群未建）静默返回，等待总控成型时自然扫到。禁止进入
     * 每 tick 周期路径（红线：禁止周期扫描，本方法只在成型成功边沿调用一次）。
     */
    private void requestClusterRecheckOnFormation(IGregTechTileEntity unitBase) {
        World world = unitBase.getWorld();
        if (world == null) return;
        int cx = unitBase.getXCoord();
        int cy = unitBase.getYCoord();
        int cz = unitBase.getZCoord();
        for (Object o : world.loadedTileEntityList) {
            if (!(o instanceof TileEntity te)) continue;
            if (Math.abs(te.xCoord - cx) > CLUSTER_SEARCH_RADIUS || Math.abs(te.yCoord - cy) > CLUSTER_SEARCH_RADIUS
                || Math.abs(te.zCoord - cz) > CLUSTER_SEARCH_RADIUS) continue;
            if (!(te instanceof IGregTechTileEntity gte)) continue;
            if (!(gte.getMetaTileEntity() instanceof MTESteamMineralLogisticsCluster cluster)) continue;
            if (!cluster.isClusterStructureFormed()) continue;
            cluster.requestStructureRecheck();
        }
    }

    /**
     * 结构后验证（3.4.4）：收集本次成型实际写入的各族 tier（≥0 者参与判定；结构未用到该族的
     * 单元形态合法，不强制参与）。参与族必须全部同级，否则跨 tier 混搭不成型。
     *
     * @return 同级 tier；无任何参与族返回 -1；跨 tier 混搭返回 -2
     */
    private int resolveUnitStructureTier() {
        int[] familyTiers = { casingFamilyTier, gearboxFamilyTier, pipeFamilyTier, frameFamilyTier, fireboxFamilyTier,
            metalFamilyTier, coilFamilyTier };
        int resolved = -1;
        for (int familyTier : familyTiers) {
            if (familyTier < 0) continue;
            if (resolved < 0) {
                resolved = familyTier;
            } else if (resolved != familyTier) {
                return -2;
            }
        }
        return resolved;
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
        // r9：客户端 'e' 候选注册配对注销（与 onPostTick 客户端一次性注册成对）
        ClusterParticleFx.clearAirCandidates(this);
        fxCandidatesRegistered = false;
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

    /** 总控在自身结构完全成型后下发统一 tier（clusterTier 写入口，与单元自身 tier 分开保存）。 */
    public void onStructureTier(int tier) {
        this.clusterTier = tier;
    }

    public int getStructureTier() {
        return clusterTier;
    }

    /** @return 主控下发的集群 tier；未关联/未下发时为 -1。 */
    public int getClusterTier() {
        return clusterTier;
    }

    /**
     * @return 单元自身验证后的结构 tier（分族同级校验通过才 ≥0；未成型为 -1）。该 tier 驱动底材
     *         贴图、增幅数值与物流链蒸汽 tier，与 {@link #getClusterTier()} 分离保存。
     */
    public int getUnitStructureTier() {
        return unitStructureTier;
    }

    /** 连接 tier 资格（3.4.4）：单元自身成型且自身 tier 与主控下发 tier 一致才为 true。 */
    public boolean isTierValidForConnection() {
        return unitStructureTier >= 0 && unitStructureTier == clusterTier;
    }

    /** 单元多方块自身是否成型。 */
    public final boolean isUnitStructureFormed() {
        return mMachine;
    }

    /** 集群连接与单元自身成型齐备才允许功能开启；总控 tier 在连接后单独下发。 */
    public boolean isModuleEnabled() {
        return cluster != null && mMachine;
    }

    /** 经济判定统一使用的工作进度原语；isUnitRunning() 仅供贴图/active 叠层。 */
    public boolean isWorkInProgress() {
        return mMachine && cluster != null && mMaxProgresstime > 0 && mProgresstime < mMaxProgresstime;
    }

    /**
     * 独立运行信号（3.4.6）：基类口径 = 自身结构成型 && 已连接集群 && 总闸允许工作。
     * 加工/热离/磁选子类覆写扩展（追加独立运行条件与能源自支付判定）；本方法只读字段，
     * 不触发结构重检或配方检查。
     */
    public boolean isUnitRunning() {
        return mMachine && cluster != null
            && getBaseMetaTileEntity() != null
            && getBaseMetaTileEntity().isAllowedToWork();
    }

    /**
     * 独立运行信号驱动（3.4.6，范式同 MTESingularityDrillingHub）：super.onPostTick 以
     * mMaxProgresstime&gt;0 置 active，而本族 {@link #checkProcessing()} 恒 NO_RECIPE，
     * 故服务端末尾直接以 {@link #isUnitRunning()} 覆写 active（setActive 触发贴图包同步）。
     * 不以 getUnitStatus() 作为 active 来源，避免递归/周期检查。
     * <p>
     * 客户端（r9 上移）：一次性扫描 {@link #getUnitShape()} 的 'e' 标记空气位注册为粒子候选
     * （{@link ClusterParticleFx#registerAirCandidates}，空列表跳过——矩阵无 'e' 的模块不注册），
     * onRemoval 配对注销。
     */
    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide()) {
            aBaseMetaTileEntity.setActive(isUnitRunning());
        } else if (!fxCandidatesRegistered) {
            fxCandidatesRegistered = true;
            List<int[]> offsets = computeAirCandidateOffsets();
            if (!offsets.isEmpty()) ClusterParticleFx.registerAirCandidates(this, offsets);
        }
    }

    /** 客户端 'e' 候选一次性注册标记（注册/清理仅在实例生命周期内各一次）。 */
    private boolean fxCandidatesRegistered = false;

    /**
     * 本单元矩阵全部 'e' 标记空气位相对控制器 {@code (offsetA, offsetB, offsetC)} 的偏移
     * （canonical [Z][Y][X] 扫描；空列表 = 本模块无粒子候选位）。
     */
    private List<int[]> computeAirCandidateOffsets() {
        String[][] shape = getUnitShape();
        List<int[]> offsets = new ArrayList<>();
        for (int z = 0; z < shape.length; z++) {
            for (int y = 0; y < shape[z].length; y++) {
                String line = shape[z][y];
                for (int x = 0; x < line.length(); x++) {
                    if (line.charAt(x) == 'e') offsets.add(
                        new int[] { x - getStructureOffsetA(), y - getStructureOffsetB(), z - getStructureOffsetC() });
                }
            }
        }
        return offsets;
    }

    public abstract String getUnitTypeNameKey();

    /** 未成型优先显示红色，避免总控收集到尚未成型的控制器时误显示可用；只读字段，不触发任何重检。 */
    public ClusterUnitStatus getUnitStatus() {
        if (!mMachine) return ClusterUnitStatus.NO_POWER_OR_INVALID;
        return cluster == null ? ClusterUnitStatus.STANDBY : ClusterUnitStatus.WORKING;
    }

    /**
     * 启用可选内部流体槽（3.4.5 增幅豁免）：仅增幅子类构造期调用一次；加工/物流子类不调用——
     * 它们覆写 {@link #getCapacity()}/{@link #getFillableStack()}/{@link #getDrainableStack()}/
     * {@link #fill(FluidStack, boolean)}/{@link #drain(int, boolean)}/{@link #getTankInfo(ForgeDirection)}
     * 与 saveNBTData/loadNBTData 的 tank 路径为拒绝/空。重复调用幂等。
     */
    protected void enableInternalFluidTank() {
        if (fluidTank == null) {
            fluidTank = new FluidStackTank(() -> mFluid, fluid -> mFluid = fluid, this::getRealCapacity);
        }
    }

    /** @return 16000L 通用内部槽是否启用（默认关闭，仅增幅子类构造期开启）。 */
    protected boolean isInternalFluidTankEnabled() {
        return fluidTank != null;
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

    /**
     * tank 收口契约（3.4.5）：基类默认 = 内部槽启用才有 16000L 名义容量，未启用为 0（零内部
     * 容量）；增幅子类启用内部槽并可覆写为增幅缓冲容量，加工/物流子类覆写收紧（拒绝/空）。
     */
    public int getCapacity() {
        return isInternalFluidTankEnabled() ? TANK_CAPACITY : 0;
    }

    public boolean isFluidInputAllowed(FluidStack aFluid) {
        return true;
    }

    /**
     * tank 收口契约：内部槽未启用恒 null；增幅子类经启用后的 mFluid 增幅缓冲读取，加工/物流
     * 子类覆写钉 null（物流另有双 tank 视图）。
     */
    public FluidStack getFillableStack() {
        return isInternalFluidTankEnabled() ? mFluid : null;
    }

    public FluidStack setFillableStack(FluidStack aFluid) {
        if (!isInternalFluidTankEnabled()) return null;
        mFluid = aFluid;
        return mFluid;
    }

    /** tank 收口契约：同 {@link #getFillableStack()}。 */
    public FluidStack getDrainableStack() {
        return isInternalFluidTankEnabled() ? mFluid : null;
    }

    public FluidStack setDrainableStack(FluidStack aFluid) {
        if (!isInternalFluidTankEnabled()) return null;
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

    /**
     * tank 收口契约：内部槽未启用恒拒收（0）；增幅子类启用后按锁定流体闸门
     * （{@link #isFluidInputAllowed}）放行；加工/物流子类覆写为拒绝/自有 tank 分发。
     */
    public int fill(FluidStack aFluid, boolean doFill) {
        if (aFluid == null || aFluid.getFluid() == null
            || aFluid.amount <= 0
            || !isInternalFluidTankEnabled()
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

    /** tank 收口契约：内部槽未启用恒 null；增幅子类正常放出，加工/物流子类覆写为拒绝/自有 tank。 */
    public FluidStack drain(int maxDrain, boolean doDrain) {
        if (maxDrain <= 0 || mFluid == null || !canTankBeEmptied() || !isInternalFluidTankEnabled()) return null;
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

    /**
     * tank 收口契约：内部槽未启用返回空数组（对外零 tank 暴露）；增幅子类返回内部槽信息，
     * 加工/物流子类覆写为空/自有双 tank 信息。
     */
    public FluidTankInfo[] getTankInfo(ForgeDirection side) {
        if (!isInternalFluidTankEnabled()) return new FluidTankInfo[0];
        return new FluidTankInfo[] { new FluidTankInfo(mFluid, getCapacity()) };
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        if (isInternalFluidTankEnabled() && mFluid != null) {
            aNBT.setTag("mFluid", mFluid.writeToNBT(new NBTTagCompound()));
        }
        aNBT.setInteger("unitStructureTier", unitStructureTier);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        // tank 收口契约：内部槽未启用的子类不回读 mFluid，杜绝旧存档通用流体经基类旁路复活。
        mFluid = isInternalFluidTankEnabled() ? FluidStack.loadFluidStackFromNBT(aNBT.getCompoundTag("mFluid")) : null;
        // 旧档无 tier 键时回 -1；服务端下次 checkMachine 会按分族校验重derive。
        unitStructureTier = aNBT.hasKey("unitStructureTier") ? aNBT.getInteger("unitStructureTier") : -1;
    }

    /** 客户端 byte 通道：镜像服务端 unitStructureTier 供 getTexture 渲染（MTELargeSteamFurnace 范式）。 */
    @Override
    public void onValueUpdate(byte aValue) {
        unitStructureTier = aValue;
    }

    @Override
    public byte getUpdateData() {
        return (byte) unitStructureTier;
    }

    /** 集群单元使用真实进度/状态词条，不显示恒定 NO_RECIPE 结果词条。 */
    @Override
    public boolean shouldDisplayCheckRecipeResult() {
        return false;
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
     * 单元贴图（3.5.2 tier 底材联动）：底材随 unitStructureTier 四档切换（青铜/钢/钛/钨钢，客户端
     * 经 byte 通道镜像 + NBT 兜底）；前脸叠 {@link #unitOverlayInactive()}/{@link #unitOverlayActive()}
     * 子类静态常量（IIconContainer，与 GT getActiveOverlay/getInactiveOverlay 钩子口径一致），
     * 缺 overlay（null）时只返回底材不崩。
     * <p>
     * NEI 安全红线：方法体内零 icon 分配（无 customOptional/new CustomIcon）、零 aBaseMetaTileEntity
     * 解引用、零动态贴图查找——底材索引取自类加载期常量表，叠层经子类覆写的静态常量回调经
     * {@code TextureFactory.of(IIconContainer)} 包装（GT 内置常量包装件，非新分配 icon）。
     */
    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstone) {
        ITexture baseCasing = Textures.BlockIcons.getCasingTextureForId(casingTextureIdForTier(unitStructureTier));
        if (side == facing) {
            IIconContainer overlay = active ? unitOverlayActive() : unitOverlayInactive();
            return overlay == null ? new ITexture[] { baseCasing }
                : new ITexture[] { baseCasing, TextureFactory.of(overlay) };
        }
        return new ITexture[] { baseCasing };
    }

    /** 前脸 inactive 叠层；基类无叠层返回 null，子类返回本类 static final IIconContainer 常量。 */
    protected IIconContainer unitOverlayInactive() {
        return null;
    }

    /** 前脸 active 叠层；基类无叠层返回 null，子类返回本类 static final IIconContainer 常量。 */
    protected IIconContainer unitOverlayActive() {
        return null;
    }

    /**
     * tier → 外壳底材纹理索引（静态共用表入口，3.5.2）：总控 getCasingTextureID 与本族 getTexture
     * 共用同一类加载期常量表；越界/未成型回退青铜（索引常量，零 icon 分配，NEI 安全红线）。
     */
    static int tierCasingTextureId(int tier) {
        if (tier < 0 || tier >= TIER_CASING_TEXTURE_IDS.length) return TIER_CASING_TEXTURE_IDS[0];
        return TIER_CASING_TEXTURE_IDS[tier];
    }

    /** tier → 外壳底材纹理索引（0 青铜镀铜砖 / 1 钢 / 2 钛 / 3 钨钢）；越界/未成型回退青铜。 */
    protected int casingTextureIdForTier(int tier) {
        return tierCasingTextureId(tier);
    }

    /**
     * 模块 hatch 贴图统一刷新（3.5.2 tier 联动，切片 2 下沉）：以当前 {@link #getUnitStructureTier()}
     * 对应底材贴图刷新指定 hatch 集合——hatch 贴图自身状态不随宿主（MTEHatch.updateTexture 为
     * final，改 texturePage/Index + issueTileUpdate），必须显式刷新。各模块在自身成型成功末尾对
     * 全部自有 hatch 列表调用；结构失败不调用（保持 hatchAdder 静态青铜 hint 口径）。
     */
    protected void refreshHatchTextures(List<? extends MTEHatch> hatches) {
        int textureId = casingTextureIdForTier(getUnitStructureTier());
        for (MTEHatch hatch : hatches) {
            if (hatch != null) hatch.updateTexture(textureId);
        }
    }

    /**
     * GT 原生 GUI（终验反馈：全部集群模块不使用独立 UI）：默认返回共享原生 GUI
     * {@link MTEClusterUnitNativeGui}（模块类型/tier/六态/运行/连接通用词条）；物流子类覆写
     * 返回富词条子类。空手右击经 GT 基类默认路径打开（MTECrustMatterAggregator 同款语义）。
     */
    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new MTEClusterUnitNativeGui(this);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        // v1.11.15 单元统一骨架（构造期仅此一次）：type(BLUE) → desc(工序主色/普通色) → desc_2(AQUA
        // 拆行) → 子类功能群 → 结构块(按子类 shape 实际尺寸) → ctrl(YELLOW) → 子类仓室群 →
        // hint(GRAY 裸键) → 品牌行 → toolTipFinisher("GTSR")。
        EnumChatFormatting descColor = getUnitDescColor();
        tt.addMachineType(EnumChatFormatting.BLUE + StatCollector.translateToLocal(getUnitTypeNameKey()))
            .addInfo(
                descColor == null ? StatCollector.translateToLocal(getUnitDescKey())
                    : descColor + StatCollector.translateToLocal(getUnitDescKey()))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal(UNIT_DESC_2_KEY));
        addUnitTooltipInfo(tt);
        tt.beginStructureBlock(getUnitSizeX(), getUnitSizeY(), getUnitSizeZ(), false)
            .addController(EnumChatFormatting.YELLOW + StatCollector.translateToLocal(UNIT_CTRL_KEY));
        addUnitStructureTooltipInfo(tt);
        tt.addStructureHint(UNIT_PAD_HINT_KEY)
            .addInfo(GTSRUtils.getAddedByLine())
            .toolTipFinisher("GTSR");
        return tt;
    }

    // ------------------------------------------------------------------
    // Tooltip 骨架常量与扩展点（v1.11.15）
    // ------------------------------------------------------------------

    private static final String UNIT_DESC_KEY = "gtsr.tooltip.cluster.unit.desc";

    private static final String UNIT_DESC_2_KEY = "gtsr.tooltip.cluster.unit.desc_2";

    private static final String UNIT_CTRL_KEY = "gtsr.tooltip.cluster.unit.ctrl";

    private static final String UNIT_PAD_HINT_KEY = "gtsr.tooltip.cluster.unit.pad_hint";

    /**
     * 子类功能群钩子：在 desc 之后、结构块之前调用（wiki 键序 type→desc→数值→ctrl）。默认空实现，
     * 子类覆写追加 YELLOW 字段 + GOLD 数值 + RED 消耗的功能行；lang 只放纯文本标签，数值/单位/颜色
     * 全部由 Java {@code String.format} + {@link EnumChatFormatting} 注入，禁止读取运行态可变数据。
     */
    protected void addUnitTooltipInfo(MultiblockTooltipBuilder tt) {}

    /**
     * 子类仓室群钩子：在 ctrl 之后、hint 之前调用（wiki 键序 ctrl→仓室→计数→hint）。默认空实现，
     * 仅确有仓室/挂点的子类覆写（物流四 I/O、增幅输入仓、自持能源仓等）。
     */
    protected void addUnitStructureTooltipInfo(MultiblockTooltipBuilder tt) {}

    /**
     * 单元描述行工序主色（计划 §2.2 主色列）：返回 {@code null} 使用普通色（默认）。
     */
    protected EnumChatFormatting getUnitDescColor() {
        return null;
    }

    /**
     * 单元描述键（v1.11.15 W1 修正）：默认共享描述键；子类覆写返回
     * {@code gtsr.tooltip.cluster.unit.<name>.desc} 专属描述行。
     */
    protected String getUnitDescKey() {
        return UNIT_DESC_KEY;
    }

    /** 结构块 X 尺寸：子类 shape 全部行的最大宽度（按实际统计，不写死）。 */
    private int getUnitSizeX() {
        int x = 0;
        for (String[] layer : getUnitShape()) {
            for (String row : layer) {
                x = Math.max(x, row.length());
            }
        }
        return x;
    }

    /** 结构块 Y 尺寸：子类 shape 每层行数。 */
    private int getUnitSizeY() {
        return getUnitShape()[0].length;
    }

    /** 结构块 Z 尺寸：子类 shape 层数。 */
    private int getUnitSizeZ() {
        return getUnitShape().length;
    }

    /** 统计本单元 shape 中指定字符出现次数（如 'P' 能源位计数），供仓室群钩子引用。 */
    protected final int countUnitShapeChar(char target) {
        int count = 0;
        for (String[] layer : getUnitShape()) {
            for (String row : layer) {
                for (int i = 0; i < row.length(); i++) {
                    if (row.charAt(i) == target) count++;
                }
            }
        }
        return count;
    }

    /** GOLD 数值段（wiki 颜色规范：数值 GOLD）。 */
    protected static String gold(String value) {
        return EnumChatFormatting.GOLD + value;
    }

    /** RED 消耗段（wiki 颜色规范：消耗 RED）。 */
    protected static String red(String value) {
        return EnumChatFormatting.RED + value;
    }

    /** 秒值格式化：整数秒不带小数，亚秒保留 1 位（如 {@code 0.8 s}）。 */
    protected static String fmtSeconds(double seconds) {
        String number = seconds == Math.rint(seconds) ? String.format("%d", (long) seconds)
            : String.format("%.1f", seconds);
        return number + " s";
    }

    /** 每秒流率格式化（{@code L/s}）。 */
    protected static String fmtLps(int litersPerSecond) {
        return String.format("%d L/s", litersPerSecond);
    }

    /** 批量流体用量格式化（{@code L}，每批口径由 lang 标签承载）。 */
    protected static String fmtL(int liters) {
        return String.format("%d L", liters);
    }

    /** 毫升级流体用量格式化（{@code mB}，每物品口径由 lang 标签承载）。 */
    protected static String fmtMb(int millibuckets) {
        return String.format("%d mB", millibuckets);
    }

    /** 链步耗时值段（GOLD，秒——数据源 {@link ChainLink} 基础表）。 */
    protected static String linkSeconds(ChainLink link) {
        return gold(fmtSeconds(link.getBaseSecondsPrecise()));
    }

    /** 链步蒸汽消耗值段（RED，L/s——数据源 {@link ChainLink} 基础表）。 */
    protected static String linkSteam(ChainLink link) {
        return red(fmtLps(link.getBaseSteamLps()));
    }

    /**
     * 增幅四档值段（{@link ClusterParams.BoosterType#getBoosterValue}）：按档以 {@code /} 相连，
     * 每值后拼 {@code valueSuffix}（如 {@code "%"}；计数值传空串），调用方再着色。
     */
    protected static String boosterTierValues(ClusterParams.BoosterType type, String valueSuffix) {
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < ClusterParams.TIER_COUNT; i++) {
            if (i > 0) values.append('/');
            values.append(type.getBoosterValue(i))
                .append(valueSuffix);
        }
        return values.toString();
    }

    /** 增幅液四档流率段（{@link ClusterParams#amplifierFluidLps}）：{@code 50/200/1000/2000 L/s}。 */
    protected static String boosterTierLps(ClusterParams.BoosterType type) {
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < ClusterParams.TIER_COUNT; i++) {
            if (i > 0) values.append('/');
            values.append(ClusterParams.amplifierFluidLps(type, i));
        }
        return values + " L/s";
    }
}
