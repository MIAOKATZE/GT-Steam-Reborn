package com.miaokatze.gtsr.common.machine.cluster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.gui.cluster.ClusterTerminalUiFactory;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRMultiBlockBase;
import com.miaokatze.gtsr.common.util.GTSRUtils;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * 蒸汽矿物物流集群总控：结构成型编排 + 运行时调度骨架。
 *
 * <p>
 * 结构定义由 {@link ClusterStructureDef} 提供（主段 + 可选延伸段，四族 tier 同级约束）；
 * 预热状态机由 {@link ClusterPreheatController} 承载；蒸汽/润滑液秒级原子结算由
 * {@link ClusterSteamEconomy} 承载；NBT 持久化由 {@link ClusterPersistence} 承载。
 *
 * <p>
 * 总控职责（本切片落地）：checkMachine 全流程（复位 → 主段 → 延伸段循环 → 四族同级 →
 * 仓校验 → tier 下发，失败路径一律回滚 tier 并拆除本次收集到的单元连接）；onPostTick 服务端
 * 编排骨架（周期重连容错 → 磁选/热力离心通电闸门 EU 扣减 → 每 20 tick 蒸汽结算与链执行钩子）。
 * 链执行本体（{@link #runChains}）与吞吐公式（{@link #computeTotalSteamLps}）已填充：
 * 批次链执行委托 {@code ClusterChainExecutor}，蒸汽 Lps 由 {@code ExecutionPlan.totalSteamLps}
 * （链长 × 增幅惩罚）与 {@code BoosterState.aggregate} 计算。
 *
 * <p>
 * 零配方说明：总控不跑任何配方——{@code getRecipeMap()} 与 {@code createProcessingLogic()}
 * 在父类 MTEMultiBlockBase 中均默认返回 null（已核实 5.09.54.20 源码），本类不覆写二者，
 * 实际的矿物处理由集群内工作单元按批次执行。
 */
public class MTESteamMineralLogisticsCluster extends MTEGTSRMultiBlockBase<MTESteamMineralLogisticsCluster>
    implements ISurvivalConstructable {

    /** 延伸段数上限（防呆，超限即视为结构终止；AssemblyLine 延伸口径）。 */
    private static final int MAX_EXTENSION_SEGMENTS = 64;

    /** 蒸汽/润滑液秒级结算节拍（tick）。 */
    private static final int SETTLE_INTERVAL_TICKS = 20;

    private static IStructureDefinition<MTESteamMineralLogisticsCluster> STRUCTURE_DEFINITION = null;

    /** 控制器底材贴图：镀铜砖块（gregtech:gt.blockcasings meta10 = Casing_BronzePlatedBricks）。 */
    private static final int CASING_TEXTURE_ID = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10);

    /** 集群拓扑：结构成型时收集的全部单元模块（结构重检时先 clear 再重建）。 */
    protected final ClusterTopology topology = ClusterTopology.empty();

    /** 预热状态机（M3 契约实现，tickServer 驱动、进度/就绪查询与 NBT 由其自理）。 */
    protected final ClusterPreheatController preheat = new ClusterPreheatController();

    /** 蒸汽经济：每秒一次的蒸汽+润滑液原子结算（预热 2000L/s / 运行按 Lps 计价）。 */
    protected final ClusterSteamEconomy economy = new ClusterSteamEconomy();

    /** 垫位登记：(segment,padId) → 已占用单元；同垫去重与失效剔除共用，checkMachine 复位时清空。 */
    private final Map<Long, MTEClusterUnitBase> occupiedSlots = new HashMap<>();

    /** 延伸段数（主段外成功成型的延伸段个数；0 = 仅主段，合法）。 */
    protected int extensionCount = 0;

    /** 开关机（GUI ToggleButton 驱动，M7 批接 GUI）：关机时集群停产但保留预热进度衰减逻辑。 */
    protected boolean machineEnabled = false;

    /** 吞吐记账：集群累计处理矿数（NBT 持久，M5 批接产出累加）。 */
    protected long totalProcessedOre = 0L;

    /** 吞吐记账：最近一秒处理矿数（链执行器每秒批后回填，停机/结构未成型清 0；进度词条直读）。 */
    protected double lastThroughputOrePerSec = 0D;

    /** 载入重连提示（x,y,z,dim,pad,segment 六元组；读档回填，周期重连时消费）。 */
    private final List<int[]> pendingReconnectHints = new ArrayList<>();

    /** FX 节流锚点：最近一次链批执行成功的服务端 tick（getBaseMetaTileEntity().getTimer()；MIN_VALUE=从未）。 */
    protected long lastBatchServerTick = Long.MIN_VALUE;

    /** 客户端粒子工作态（getUpdateData/onValueUpdate bit0 通道同步；onPostTick 客户端分支据此喷粒子）。 */
    protected boolean mWorkingForFX = false;

    /** 终端 GUI 初始页（ClusterTerminalUiFactory.open 服务端写入、GUI 侧读取；瞬态不落 NBT）。 */
    protected int guiInitialPage = 0;

    /** 终端 GUI 当前选中的物流单元下标（GUI 交互态；越界由 getSelectedLogisticsUnit 兜底 null）。 */
    protected int selectedLogisticsIndex = 0;

    // 四族 tier 字段（0-3，对应 ClusterParams.ClusterTier 下标）：ofBlocksTiered 挂接点，
    // 由结构元素 setter 写入；每次 checkMachine 前复位 -1，未成型即 -1
    protected int mCasingTier = -1, mPipeTier = -1, mFrameTier = -1, mFireboxTier = -1;

    public MTESteamMineralLogisticsCluster(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        registerProgressEntries();
    }

    public MTESteamMineralLogisticsCluster(String aName) {
        super(aName);
        registerProgressEntries();
    }

    /**
     * GTSR 进度词条注册：注册顺序 = GUI 终端显示顺序（预热进度/吞吐）。预热进度实时接入
     * {@link #getPreheatProgress()}；吞吐接入 {@link #getLastThroughputOrePerSec()}（链执行器回填）。
     */
    private void registerProgressEntries() {
        registerEntry(
            "gtsr.progress.cluster.preheat",
            "gtsr.gui.cluster.preheat",
            "%.1f%%",
            EnumChatFormatting.AQUA,
            this::getPreheatProgress);
        registerEntry(
            "gtsr.progress.cluster.throughput",
            "gtsr.gui.cluster.throughput",
            "%.0f",
            EnumChatFormatting.GOLD,
            this::getLastThroughputOrePerSec);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESteamMineralLogisticsCluster(mName);
    }

    /** 结构定义懒委托 {@link ClusterStructureDef#create()}（主段 + 延伸段，契约切片持有）。 */
    @Override
    public IStructureDefinition<MTESteamMineralLogisticsCluster> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = ClusterStructureDef.create();
        }
        return STRUCTURE_DEFINITION;
    }

    /** {@inheritDoc} 主段 + 延伸段全息投影：stackSize.stackSize 语义 = 目标总段数（含主段，最小 1）。 */
    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(
            ClusterStructureDef.PIECE_MAIN,
            stackSize,
            hintsOnly,
            ClusterStructureDef.mainOffsetA(),
            ClusterStructureDef.mainOffsetB(),
            ClusterStructureDef.mainOffsetC());
        int extSegments = extensionSegments(stackSize);
        for (int k = 0; k < extSegments; k++) {
            buildPiece(
                ClusterStructureDef.PIECE_EXT,
                stackSize,
                hintsOnly,
                ClusterStructureDef.extOffsetA(k),
                ClusterStructureDef.extOffsetB(),
                ClusterStructureDef.extOffsetC(k));
        }
    }

    /** {@inheritDoc} 主段优先，预算耗尽即止；延伸段按 stackSize 段数依次追加。 */
    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int built = survivalBuildPiece(
            ClusterStructureDef.PIECE_MAIN,
            stackSize,
            ClusterStructureDef.mainOffsetA(),
            ClusterStructureDef.mainOffsetB(),
            ClusterStructureDef.mainOffsetC(),
            elementBudget,
            env,
            false,
            true);
        if (built > 0) return built;
        int extSegments = extensionSegments(stackSize);
        for (int k = 0; k < extSegments; k++) {
            built = survivalBuildPiece(
                ClusterStructureDef.PIECE_EXT,
                stackSize,
                ClusterStructureDef.extOffsetA(k),
                ClusterStructureDef.extOffsetB(),
                ClusterStructureDef.extOffsetC(k),
                elementBudget,
                env,
                false,
                true);
            if (built > 0) return built;
        }
        return 0;
    }

    /** stackSize 段数语义：总段数（含主段）→ 延伸段数 = 总段数-1，钳到防呆上限。 */
    private static int extensionSegments(ItemStack stackSize) {
        if (stackSize == null || stackSize.stackSize < 1) return 0;
        return Math.min(stackSize.stackSize - 1, MAX_EXTENSION_SEGMENTS);
    }

    /**
     * 结构检查全流程（父类 MTEMultiBlockBase 为 void 签名：不加错误即成型，mMachine 由父类
     * {@code structureErrors.isEmpty()} 统一管理，本类不手动置位）。
     *
     * <p>
     * 流程：复位四族 tier 与拓扑（旧单元统一 disconnect）→ 主段 checkPiece → 延伸段循环
     * （失配即停，延伸段失配是正常终止不追加错误；上限 {@link #MAX_EXTENSION_SEGMENTS} 防呆）
     * → 四族同级校验（任一 &lt;0 或互不相等即失败）→ 输入总线/输出总线/流体输入仓非空校验
     * → 全过后 tier 统一下发 topology 全员。
     *
     * <p>
     * 所有失败路径一律回滚四 tier=-1，并拆除本次扫描已收集到的单元连接（checkPiece 的
     * 元素回调在失配前即可能已收集部分单元，未成型的集群不得保留这些反向引用）。
     */
    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        // 1) 复位：快照旧单元 → 清拓扑/垫位登记/延伸计数 → 四 tier 归 -1 → 旧单元统一断开
        List<MTEClusterUnitBase> previousUnits = new ArrayList<>(topology.getUnits());
        topology.clear();
        occupiedSlots.clear();
        extensionCount = 0;
        rollbackTiers();
        for (MTEClusterUnitBase unit : previousUnits) {
            unit.disconnect();
        }

        // 2) 主段
        if (!checkPiece(
            ClusterStructureDef.PIECE_MAIN,
            ClusterStructureDef.mainOffsetA(),
            ClusterStructureDef.mainOffsetB(),
            ClusterStructureDef.mainOffsetC(),
            errors)) {
            rollbackFormation();
            return;
        }

        // 3) 延伸段循环：失配即停（正常终止）；延伸 0 段（仅主段）合法。SR-终审 B2 修正：第 5 参
        // 传 null——checkPiece 失配元素必写 PositionedStructureError，共享主 errors 列表会让
        // "仅主段"的合法形态因边界外的第 1 延伸段失配而永远不成型（父类按 errors 空否判 mMachine）
        for (int k = 0; k < MAX_EXTENSION_SEGMENTS; k++) {
            if (!checkPiece(
                ClusterStructureDef.PIECE_EXT,
                ClusterStructureDef.extOffsetA(k),
                ClusterStructureDef.extOffsetB(),
                ClusterStructureDef.extOffsetC(k),
                null)) {
                break;
            }
            extensionCount = k + 1;
        }

        // 4) 四族同级：任一 <0（对应族未被结构元素写入）→ 通用错误；互不相等 → tier 混拼错误
        if (mCasingTier < 0 || mPipeTier < 0 || mFrameTier < 0 || mFireboxTier < 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            rollbackFormation();
            return;
        }
        if (mCasingTier != mPipeTier || mCasingTier != mFrameTier || mCasingTier != mFireboxTier) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.tier_mismatch"));
            rollbackFormation();
            return;
        }

        // 5) 仓校验：输入总线 / 输出总线 / 流体输入仓（蒸汽+润滑共用）任一缺失即失败
        // （lang key 预留，缺键时显示原键，由 lang 并行切片补齐）
        if (mInputBusses.isEmpty()) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.missing_input_bus"));
            rollbackFormation();
            return;
        }
        if (mOutputBusses.isEmpty()) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.missing_output_bus"));
            rollbackFormation();
            return;
        }
        if (mInputHatches.isEmpty()) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.missing_fluid_hatch"));
            rollbackFormation();
            return;
        }

        // 6) 全过：tier 定级（四值相等且非负，getStructureTierIndex 必然 >= 0）统一下发全员；
        // connect 已在 addClusterUnit 收集时完成；段数（主段+延伸段）落地供拓扑快照。
        // D2/D3 同级强制：自身已成型且 tier 与集群不一致的单元就地剔除（disconnect+撤槽），
        // 不阻断其余单元与集群成型；未成型单元（tier<0）保留连接，由 isModuleEnabled 闸门兜底
        int tier = getStructureTierIndex();
        topology.setSegmentCount(extensionCount + 1);
        for (MTEClusterUnitBase unit : new ArrayList<>(topology.getUnits())) {
            if (unit.getUnitStructureTier() >= 0 && unit.getUnitStructureTier() != tier) {
                topology.removeUnit(unit);
                forgetSlotsOf(unit);
                unit.disconnect();
                continue;
            }
            unit.onStructureTier(tier);
        }
    }

    /** 四族 tier 回滚到未成型态（-1）。 */
    private void rollbackTiers() {
        mCasingTier = -1;
        mPipeTier = -1;
        mFrameTier = -1;
        mFireboxTier = -1;
    }

    /** 失败路径统一收尾：回滚四 tier + 拆除本次扫描已收集的单元连接并清空拓扑/垫位登记。 */
    private void rollbackFormation() {
        rollbackTiers();
        for (MTEClusterUnitBase unit : topology.getUnits()) {
            unit.disconnect();
        }
        topology.clear();
        occupiedSlots.clear();
        extensionCount = 0;
    }

    /**
     * @return 四族 tier 全部相等且有效时返回该 tier 下标（0-3，对应 {@link ClusterParams.ClusterTier}），
     *         否则 -1（混拼或未成型）。
     */
    public int getStructureTierIndex() {
        if (mCasingTier < 0 || mCasingTier != mPipeTier || mCasingTier != mFrameTier || mCasingTier != mFireboxTier) {
            return -1;
        }
        return mCasingTier;
    }

    /** @return 集群拓扑（live 引用，结构重检时由 checkMachine 清空重建）。 */
    public ClusterTopology getTopology() {
        return topology;
    }

    /** @return 延伸段数（主段外成功成型的段数，0 = 仅主段）。 */
    public int getExtensionCount() {
        return extensionCount;
    }

    /**
     * 收集一个集群单元（结构元素回调入口，checkPiece 扫描期间调用）。
     *
     * <p>
     * 校验顺序：垫类型匹配（padId ↔ 单元实际族别）→ 同垫去重（同 padId 同 segment 已有
     * 单元即拒绝）→ topology 引用级去重 → 记录垫位 → connect。tier 不在此下发——结构 tier
     * 要到 checkMachine 末尾四族同级校验通过后才统一下发 topology 全员。
     *
     * @param unit    待收集单元
     * @param padId   垫位族别（{@link ClusterTopology#PAD_WORKING} / {@link ClusterTopology#PAD_BOOSTER}
     *                / {@link ClusterTopology#PAD_LOGISTICS}）
     * @param segment 单元所在结构段号（主段 0，延伸段 k 从 1 起或由结构定义约定）
     * @return true = 收集成功；false = 垫族不匹配或垫位重复被跳过
     */
    public boolean addClusterUnit(MTEClusterUnitBase unit, int padId, int segment) {
        if (unit == null) return false;
        if (!padMatchesUnit(padId, unit)) return false;
        long key = slotKey(segment, padId);
        if (occupiedSlots.containsKey(key)) return false;
        if (!topology.addUnit(unit)) return false;
        occupiedSlots.put(key, unit);
        topology.putSlot(segment, padId, unit);
        unit.onCollected(padId, segment);
        unit.connect(this);
        return true;
    }

    /** 垫族匹配：padId 与单元实际类型一致（工作垫 ↔ 处理单元、增幅垫 ↔ 增幅单元、物流垫 ↔ 物流单元）。 */
    private static boolean padMatchesUnit(int padId, MTEClusterUnitBase unit) {
        return switch (padId) {
            case ClusterTopology.PAD_WORKING -> unit instanceof MTEBasicProcessingUnit;
            case ClusterTopology.PAD_BOOSTER -> unit instanceof MTEBasicAmplifierUnit;
            case ClusterTopology.PAD_LOGISTICS -> unit instanceof MTEBasicLogisticsUnit;
            default -> false;
        };
    }

    /** 垫位登记键打包：(segment,padId) → long（segment 上界 64、padId 上界 2，无碰撞）。 */
    private static long slotKey(int segment, int padId) {
        return ((long) segment << 2) | padId;
    }

    /** 垫位登记清理：移除指定单元占用的槽位（单元剔除/结构重检共用）。 */
    private void forgetSlotsOf(MTEClusterUnitBase unit) {
        occupiedSlots.values()
            .removeIf(u -> u == unit);
    }

    /**
     * 单元被移除通知（单元侧破坏/失联时调用）：拓扑移除 + 断开连接 + 触发近期结构重检。
     *
     * <p>
     * mStartUpCheck=100 为 GT5U「一次性事件型重检」惯例（100 tick 后由父类 --mStartUpCheck==0
     * 触发一次 checkStructure，随后继续自减入负值不再触发）。注意这是事件式单次赋值，不得
     * 放进每 tick 路径反复置位——那会让父类倒数永远停在 99（见 MTESiemensMartinFurnace 修复注记）。
     */
    public void onUnitRemoved(MTEClusterUnitBase unit) {
        topology.getUnits()
            .remove(unit);
        forgetSlotsOf(unit);
        unit.disconnect();
        mStartUpCheck = 100;
    }

    /**
     * 服务端编排骨架（每 tick）。
     *
     * <p>
     * 顺序：super（父类结构重检/runMachine）→ 客户端直接返回（粒子另批处理）→ 结构未成型
     * 只驱动预热衰减 → 周期重连容错（{@link ClusterParams#RECONNECT_INTERVAL_TICKS} 节流）→
     * 通电闸门 EU 扣减（每 tick）→ 蒸汽/润滑秒级结算 + 链执行钩子（每 20 tick）。
     */
    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        // 客户端：工作态时每 tick 喷石头位粒子（'G' 位 cloud 粒子，委托 ClusterParticleFx），其余客户端逻辑无
        if (!aBaseMetaTileEntity.isServerSide()) {
            if (mWorkingForFX) ClusterParticleFx.spawnParticles(this);
            return;
        }

        // 结构未成型：只让预热状态机走「停机衰减」分支，蒸汽经济红标清位，吞吐读数清 0
        if (!mMachine) {
            preheat.tickServer(false, false, false);
            economy.clearFlags();
            lastThroughputOrePerSec = 0D;
            return;
        }

        // 周期重连容错（NAC 口径：节流间隔内做一次全员连接修复；本机无能量分摊，仅重连）
        if (aTick % ClusterParams.RECONNECT_INTERVAL_TICKS == 0) {
            maintainClusterLinks();
        }

        // 通电闸门：磁选/热力离心的持续供电每 tick 扣减（不足全扣，跨仓分摊）
        drainPoweredUnitEnergy(poweredUnitEnergyDemand());

        // 蒸汽/润滑液秒级结算（每 20 tick 一次）+ 链执行钩子
        if (aTick % SETTLE_INTERVAL_TICKS == 0) {
            settleSteamEconomy();
        }
    }

    /**
     * 周期重连容错：遍历拓扑，TE 失联（getBaseMetaTileEntity()==null）的单元剔除并 disconnect；
     * 集群引用丢失（读档/区块重载边界）的单元重 connect。本控制器为 topology 唯一属主，
     * 此处经 live 视图做结构性增删是属主特权（对外契约仍禁止遍历中增删）。
     */
    protected void maintainClusterLinks() {
        Iterator<MTEClusterUnitBase> it = topology.getUnits()
            .iterator();
        while (it.hasNext()) {
            MTEClusterUnitBase unit = it.next();
            if (unit.getBaseMetaTileEntity() == null) {
                it.remove();
                forgetSlotsOf(unit);
                unit.disconnect();
                continue;
            }
            if (unit.getCluster() == null) {
                unit.connect(this);
            }
        }
        if (!pendingReconnectHints.isEmpty()) {
            resolveReconnectHints();
        }
    }

    /**
     * 载入重连提示消费（BoundDrillNode 口径）：按坐标解析方块实体，命中集群单元且垫位未被
     * 本轮结构扫描占用的，补收进拓扑并 connect。命中或确定失效即移除提示，避免每周期重扫。
     */
    private void resolveReconnectHints() {
        World world = getBaseMetaTileEntity() == null ? null : getBaseMetaTileEntity().getWorld();
        if (world == null) return;
        int dim = world.provider.dimensionId;
        Iterator<int[]> it = pendingReconnectHints.iterator();
        while (it.hasNext()) {
            int[] hint = it.next();
            if (hint.length < 6 || hint[3] != dim) {
                // 畸形/异维度提示为死数据，直接移除避免每周期重扫（SR-终审 C2）
                it.remove();
                continue;
            }
            it.remove();
            if (!world.blockExists(hint[0], hint[1], hint[2])) continue;
            if (!(world.getTileEntity(hint[0], hint[1], hint[2]) instanceof IGregTechTileEntity gtte)) continue;
            IMetaTileEntity mte = gtte.getMetaTileEntity();
            if (!(mte instanceof MTEClusterUnitBase unit)) continue;
            long key = slotKey(hint[5], hint[4]);
            if (occupiedSlots.containsKey(key)) continue;
            if (addClusterUnit(unit, hint[4], hint[5])) {
                unit.onStructureTier(getStructureTierIndex());
            }
        }
    }

    /** @return 每 tick 通电闸门 EU 需求（在场且开机时的磁选×32 + 热力离心×32；关机即 0）。 */
    private long poweredUnitEnergyDemand() {
        if (!machineEnabled) return 0L;
        return (long) topology.countUnits(MTEUnitMagneticSeparator.class) * ClusterParams.MAGNETIC_EU_PER_TICK
            + (long) topology.countUnits(MTEUnitThermalCentrifuge.class) * ClusterParams.THERMOCENTRIFUGE_EU_PER_TICK;
    }

    /** 跨能源仓分摊扣 EU（不足则把已有的全扣，剩余缺口丢弃——闸门表现为断电）。 */
    private void drainPoweredUnitEnergy(long demand) {
        if (demand <= 0) return;
        long remaining = demand;
        for (var hatch : GTUtility.validMTEList(mEnergyHatches)) {
            if (remaining <= 0) break;
            if (hatch.getBaseMetaTileEntity() == null) continue;
            long stored = hatch.getEUVar();
            if (stored <= 0) continue;
            long drained = Math.min(stored, remaining);
            hatch.setEUVar(stored - drained);
            remaining -= drained;
        }
    }

    /**
     * 通电闸门查询：任一能源仓存量 EU &gt; 0 即视为通电（磁选/热力离心单元的 isModuleEnabled
     * 由单元切片接本方法；EU 被 {@link #drainPoweredUnitEnergy} 持续扣减，枯竭即返回 false）。
     */
    public boolean isPoweredUnitActive(MTEClusterUnitBase unit) {
        for (var hatch : GTUtility.validMTEList(mEnergyHatches)) {
            if (hatch.getBaseMetaTileEntity() == null) continue;
            if (hatch.getEUVar() > 0) return true;
        }
        return false;
    }

    /**
     * 蒸汽/润滑秒级结算（每 20 tick 一次，仅在结构成型路径进入）。
     *
     * <p>
     * 关机：预热状态机走停机分支，economy 不扣。开机：预热未满先 settlePreheat（2000L/s）
     * 得 steamOK；预热已满时以最近一次运行结算的短缺状态回填 steamOK（蒸汽断供可持续衰减
     * 预热进度）。随后 settleRun 原子扣蒸汽+润滑——缺润滑不停机，但 runChains 不跑。
     */
    private void settleSteamEconomy() {
        if (!machineEnabled) {
            preheat.tickServer(false, mMachine, false);
            lastThroughputOrePerSec = 0D;
            return;
        }
        boolean steamOK = preheat.isReady() ? !economy.isSteamShortage() : economy.settlePreheat(this);
        preheat.tickServer(machineEnabled, mMachine, steamOK);
        if (preheat.isReady() && economy.settleRun(this, computeTotalSteamLps()) && economy.isLubricantOk()) {
            runChains();
        }
    }

    /**
     * 本秒总蒸汽需求（Lps），委托 {@code ExecutionPlan.totalSteamLps}（链长 × 增幅惩罚等）。
     * tier &lt; 0（未成型/混拼）时由 ExecutionPlan 内部返回 0。
     */
    protected double computeTotalSteamLps() {
        return ExecutionPlan.totalSteamLps(
            topology.getLogisticsUnits(),
            topology,
            getStructureTierIndex(),
            BoosterState.aggregate(topology.getBoosterUnits()));
    }

    /**
     * 链执行钩子：预热完成且蒸汽/润滑结算成功时由 {@link #settleSteamEconomy} 调用（润滑
     * 由 {@code economy.isLubricantOk()} 门控——缺润滑不停机但链不跑）。
     *
     * <p>
     * 逐物流单元驱动：冷却中的单元按结算节拍递减冷却（重置由执行器在批执行成功后进行，
     * 发生在本递减之后，二者不冲突），冷却归零/无冷却的单元交 {@code ClusterChainExecutor}
     * 批执行（取料 → 工作单元链 → 产出回填，返回本批矿数，冷却未到返 0）。
     */
    protected void runChains() {
        for (MTEBasicLogisticsUnit unit : topology.getLogisticsUnits()) {
            if (unit.getChainCooldownTicks() > 0) {
                unit.setChainCooldownTicks(unit.getChainCooldownTicks() - SETTLE_INTERVAL_TICKS);
                continue;
            }
            if (ClusterChainExecutor.executeBatch(this, unit) > 0) {
                // FX 节流锚点：批执行成功记服务端 tick（客户端经 getUpdateData bit0 同步工作态，40t 窗口）
                lastBatchServerTick = getBaseMetaTileEntity().getTimer();
            }
        }
    }

    /** @return 最近一次链批执行成功的服务端 tick（Long.MIN_VALUE=从未；粒子 FX 工作窗口判定用）。 */
    public long getLastBatchServerTick() {
        return lastBatchServerTick;
    }

    // —— 字节通道：bit0=工作态（粒子开关，批执行 40t 窗口）；bit1-6 恒 63 预留（照聚合器口径）——

    @Override
    public void onValueUpdate(byte aValue) {
        mWorkingForFX = (aValue & 0x01) != 0;
    }

    @Override
    public byte getUpdateData() {
        // 工作态 = 距最近一次批执行 < 40t（结算节拍 20t 的双周期余量），判定委托 ClusterParticleFx
        return (byte) ((63 << 1) | (ClusterParticleFx.isFxWorking(this) ? 0x01 : 0x00));
    }

    /** @return 预热状态机（NBT 编解码与 GUI 直读共用）。 */
    public ClusterPreheatController getPreheat() {
        return preheat;
    }

    /** @return 蒸汽经济结算器（红标/读数供 GUI 直读）。 */
    public ClusterSteamEconomy getSteamEconomy() {
        return economy;
    }

    /** @return 流体输入仓 live 视图（蒸汽+润滑液经济结算的仓源，ME 仓兼容由访问层自理）。 */
    public List<MTEHatchInput> getClusterFluidInputHatches() {
        return mInputHatches;
    }

    /** @return 输入总线 live 视图（链执行器批次取料源）。 */
    public List<MTEHatchInputBus> getClusterInputBusses() {
        return mInputBusses;
    }

    /** @return 输出总线 live 视图（链执行器批次产出回填目标）。 */
    public List<MTEHatchOutputBus> getClusterOutputBusses() {
        return mOutputBusses;
    }

    /** 触发父类槽位重算（链执行器在总线/仓内容变化后调用，使 GT 槽位视图同步）。 */
    public void updateClusterSlots() {
        updateSlots();
    }

    /** @return 集群累计处理矿数（吞吐记账，NBT 持久）。 */
    public long getTotalProcessedOre() {
        return totalProcessedOre;
    }

    /** 吞吐记账累加（链执行批次产出时调用；本地累计仅用于显示与 NBT）。 */
    public void addProcessedOre(long count) {
        if (count <= 0) return;
        totalProcessedOre += count;
        markDirty();
    }

    /** @return 最近一秒处理矿数（链执行器每秒批后回填，停机/结构未成型清 0；进度词条直读）。 */
    public double getLastThroughputOrePerSec() {
        return lastThroughputOrePerSec;
    }

    /** 吞吐回填 setter（链执行器记账用；不做 NBT 持久——瞬态读数）。 */
    public void setLastThroughputOrePerSec(double v) {
        lastThroughputOrePerSec = v;
    }

    /** 载入重连提示清单回填（ClusterPersistence 读档路径写入，maintainClusterLinks 消费）。 */
    public void setPendingReconnectHints(List<int[]> hints) {
        pendingReconnectHints.clear();
        if (hints != null) pendingReconnectHints.addAll(hints);
    }

    /** @return 集群是否开机（GUI ToggleButton 双端同步，M7 批接 GUI）。 */
    public boolean isMachineEnabled() {
        return machineEnabled;
    }

    /** 开关机 setter：值变化即标脏落盘。 */
    public void setMachineEnabled(boolean v) {
        if (machineEnabled != v) {
            machineEnabled = v;
            markDirty();
        }
    }

    /** @return 终端 GUI 初始页（工厂 open 前服务端写入；瞬态，不落 NBT）。 */
    public int getGuiInitialPage() {
        return guiInitialPage;
    }

    /** 终端初始页 setter（ClusterTerminalUiFactory.open 在 openGui 前写入）。 */
    public void setGuiInitialPage(int page) {
        guiInitialPage = page;
    }

    /** @return 终端 GUI 当前选中的物流单元下标。 */
    public int getSelectedLogisticsIndex() {
        return selectedLogisticsIndex;
    }

    /** 选中物流单元下标 setter（GUI 交互写入，链路编辑页读）。 */
    public void setSelectedLogisticsIndex(int index) {
        selectedLogisticsIndex = index;
    }

    /** @return 选中的物流单元（拓扑快照越界/未成型返回 null，调用方自理）。 */
    public MTEBasicLogisticsUnit getSelectedLogisticsUnit() {
        List<MTEBasicLogisticsUnit> units = topology.getLogisticsUnits();
        if (selectedLogisticsIndex < 0 || selectedLogisticsIndex >= units.size()) return null;
        return units.get(selectedLogisticsIndex);
    }

    /** @return 预热进度（0-100 口径），委托预热状态机。 */
    public double getPreheatProgress() {
        return preheat.getProgress() * 100.0D;
    }

    /** @return 预热是否完成，委托预热状态机。 */
    public boolean isPreheatReady() {
        return preheat.isReady();
    }

    /** 总控与全部单元一致：不生成维护检修仓需求（决策 R6）。 */
    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    /**
     * 按面纹理：Enhanced 多方体系（MTEEnhancedMultiBlockBase 直系）没有
     * getActiveOverlay/getInactiveOverlay/glow overlay 钩子（那是 GT++ 蒸汽基族的抽象），此处照
     * MTECrustMatterAggregator 范式直接覆写 getTexture——底材镀铜砖块
     * （gregtech:gt.blockcasings meta10 = Casing_BronzePlatedBricks）+ 正面采矿钻头叠层
     * （OVERLAY_FRONT_ORE_DRILL）区分启停。叠层为 GT5U 内置常量，无需 registerIcons。
     */
    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_TEXTURE_ID),
                TextureFactory.of(
                    aActive ? Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE
                        : Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL) };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_TEXTURE_ID) };
    }

    /**
     * 全量结构 tooltip（模板 MTELargeSteamFurnace :402-450 范式）：机器类型/一句话 → 蒸汽公式摘要 →
     * 主段结构块（20×15×29）+ 控制器/仓位列 → 四族同级表 → 三垫沿深度 → 延伸层循环口径 →
     * 增幅流体表 → 终端入口 hint → AddedBy 收尾。文案键全部走 gtsr.tooltip.cluster.*（lang 切片补齐）。
     */
    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.cluster.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.cluster.desc"))
            .addSeparator()
            .addInfo(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.cluster.steam_formula"))
            .beginStructureBlock(20, 15, 29, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.cluster.ctrl"))
            .addInputBus(StatCollector.translateToLocal("gtsr.tooltip.cluster.input_bus"), 1)
            .addOutputBus(StatCollector.translateToLocal("gtsr.tooltip.cluster.output_bus"), 1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.cluster.steam_hatch"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.cluster.fluid_hatch"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.cluster.energy_hatch"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addStructureInfo("")
            .addStructureInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.cluster.tier_family"))
            .addStructureInfo(
                EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal("gtsr.tooltip.cluster.pads_depth"))
            .addStructureInfo(
                EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("gtsr.tooltip.cluster.extension"))
            .addStructureInfo(
                EnumChatFormatting.DARK_AQUA + StatCollector.translateToLocal("gtsr.tooltip.cluster.booster_fluids"))
            .addStructureHint("gtsr.tooltip.cluster.terminal_hint")
            .addInfo(GTSRUtils.getAddedByLine())
            .toolTipFinisher();
        return tt;
    }

    /** 限水平朝向、不旋转、不垂直翻转（与模板 MTELargeSteamFurnace 同限）。 */
    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        return (d, r, f) -> d.offsetY == 0 && r.isNotRotated() && !f.isVerticallyFliped();
    }

    /**
     * {@inheritDoc} 开关机位兜底直写 + 运行态（预热进度等）委托 {@link ClusterPersistence}。
     */
    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setBoolean("machineEnabled", machineEnabled);
        ClusterPersistence.write(this, aNBT);
    }

    /** {@inheritDoc} 与 {@link #saveNBTData} 对应。 */
    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        machineEnabled = aNBT.getBoolean("machineEnabled");
        ClusterPersistence.read(this, aNBT);
    }

    /**
     * {@inheritDoc} 持枢纽终端右击 = 打开集群终端界面（MUI2，{@link ClusterTerminalUiFactory} 三参入口，
     * 初始页 0；空手/他物右击走父类默认机器 GUI）。持物右击方案同聚合器/枢纽（潜行右击会被 GT 基座
     * 拦截贴墙放方块，onRightclick 收不到）。终端入口提示见 tooltip structure hint。
     */
    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        ItemStack held = aPlayer.getHeldItem();
        if (held != null && GTSRItemList.HubTerminal.isStackEqual(held, false, true)) {
            if (aBaseMetaTileEntity.isServerSide()) {
                ClusterTerminalUiFactory.open(aPlayer, aBaseMetaTileEntity, 0);
            }
            return true;
        }
        return super.onRightclick(aBaseMetaTileEntity, aPlayer);
    }
}
