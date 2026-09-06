package com.miaokatze.gtsr.common.machine.cluster;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.util.Vec3Impl;
import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.event.GTSRMachineEvent;
import com.miaokatze.gtsr.common.gui.cluster.MTESteamMineralLogisticsClusterNativeGui;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRMultiBlockBase;
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;
import com.miaokatze.gtsr.common.machine.base.MTESteamInputHatchGeneric;
import com.miaokatze.gtsr.common.terminal.ClusterTerminalData;
import com.miaokatze.gtsr.common.terminal.TerminalNet;
import com.miaokatze.gtsr.common.terminal.TerminalUiType;
import com.miaokatze.gtsr.common.util.GTSRUtils;
import com.miaokatze.gtsr.main.GTSteamReborn;

import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.MultiblockTooltipBuilder;
import io.netty.buffer.Unpooled;

/**
 * 蒸汽矿物物流集群总控：结构成型编排 + 运行态汇聚（O2 模块化：主控只做编排与状态汇聚，
 * 热量/经济/链执行/公式/增幅聚合等逻辑全部委托批次1 各类，主控不新增私有大逻辑群）。
 *
 * <p>
 * 委托关系：结构定义 {@link ClusterStructureDef}（主段 20 深 + 8 深延伸段、四族 tier、F/H/G 挂点、
 * A 总控仓室自由化）；拓扑簿记 {@link ClusterTopology}；预热状态机 {@link ClusterPreheatController}
 * （每 tick 驱动）；蒸汽/润滑 20t 原子结算 {@link ClusterSteamEconomy}（settlePreheatFull /
 * settleRunFull 完整状态口径）；链批执行 {@link ClusterChainExecutor}（三参入口，本类实现
 * {@link ClusterBatchHost} 契约）；C 公式聚合 {@link ExecutionPlan}；增幅聚合 {@link BoosterState}。
 *
 * <p>
 * 服务端编排（onPostTick）：客户端粒子 → 未成型衰减 → 周期重连 → 每 tick 热量推进（供给态用
 * 20t 结算锁存 thermalSupplyOkLatched，修复旧版只在 20t 结算内推进热量的 20 倍定标错误）→
 * 粒子窗口驱动 → 每 20t 结算编排（吞吐窗口发布 → 物流单元软锤/低温边沿轮询 → 关机早退 /
 * 预热结算 / 运行结算 + 冷却递减 + 链执行 + 增幅液实扣 + 断供中止）。配方运行绑定
 * （r-logi-power-bind）：关机只阻止开下一批，在飞批次继续按结算口径计费直至配方读完产出排空；
 * 运行中断供（蒸汽/润滑结算失败或增幅液断供）立即终止在飞配方（吞料）、主控断电并一次性
 * 通知物主（gtsr.chat.cluster.supply_abort）。主控不再持有总线/集中供电模型（plan §3.3.2）：
 * 输入/输出总线归物流模块，热离/磁选能源仓由单元自身扣减。
 *
 * <p>
 * 统一蒸汽源（plan §3.3.2/E5）：A 外壳位（总控仓室自由化）可容纳标准输入仓 / 蒸汽输入仓
 * （入 mInputHatches）与耐压蒸汽输入仓（{@link MTEHatchPressureSteamInput}，类非 MTEHatchInput
 * 无法入标准列表，由 A 位结构 adder 经 {@code registerPressureSteamHatch} 直收本类列表）；{@link
 * #getClusterFluidInputHatches()} 返回二者统一可枚举列表供 {@link ClusterSteamEconomy} 结算；
 * 结构校验：通用输入仓 1..10、蒸汽仓类合计 0..10（终验反馈 FA）。
 *
 * <p>
 * 日志：仅保留 4 类错误边沿 WARN（主结构破坏、结构破坏致满热降温、
 * 供给充足→短缺、模块低温关机）。
 *
 * <p>
 * 零配方说明：总控不跑任何配方——getRecipeMap()/createProcessingLogic() 在父类中均默认返回
 * null，本类不覆写，实际矿物处理由集群内工作单元按批次执行。
 */
public class MTESteamMineralLogisticsCluster extends MTEGTSRMultiBlockBase<MTESteamMineralLogisticsCluster>
    implements ISurvivalConstructable, ClusterBatchHost {

    /** 蒸汽/润滑液秒级结算节拍（tick）。 */
    private static final int SETTLE_INTERVAL_TICKS = 20;

    /** 日志统一前缀。 */
    private static final String LOG_PREFIX = "[GTSR-JQ] ";

    private static IStructureDefinition<MTESteamMineralLogisticsCluster> STRUCTURE_DEFINITION = null;

    /** 集群拓扑：结构成型时收集的全部单元模块（结构重检时先 clear 再重建）。 */
    protected final ClusterTopology topology = ClusterTopology.empty();

    /** 预热状态机（tickServer 每 tick 驱动、进度/就绪查询与 NBT 由其自理）。 */
    protected final ClusterPreheatController preheat = new ClusterPreheatController();

    /** 蒸汽经济：每秒一次的蒸汽+润滑液原子结算（完整状态口径 EconomySettleResult）。 */
    protected final ClusterSteamEconomy economy = new ClusterSteamEconomy();

    /** 垫位登记：(segment,padId) → 已占用单元；同垫去重与失效剔除共用，checkMachine 复位时清空。 */
    private final Map<Long, MTEClusterUnitBase> occupiedSlots = new HashMap<>();

    /**
     * 耐压蒸汽输入仓（A 外壳位结构 adder 直收 registerPressureSteamHatch；checkMachine 复位清空重建、未成型延伸段 prune 剔除；与 mInputHatches 合成统一结算源）。
     */
    private final List<MTEHatchPressureSteamInput> pressureSteamHatches = new ArrayList<>();

    /** 物理电源边沿锁存（软锤复位/模块低温关机检测，20t 轮询；结构重检时清空）。 */
    private final IdentityHashMap<MTEBasicLogisticsUnit, Boolean> logisticsPowerLatch = new IdentityHashMap<>();

    /** 最近一次结构扫描的模块冲突（drainModuleConflicts 取走；结构仍成型，仅上报——GUI/日志）。 */
    private final List<ClusterStructureError> lastModuleConflicts = new ArrayList<>();

    /** 延伸段数（主段外成功成型的延伸段个数；0 = 仅主段，合法）。 */
    protected int extensionCount = 0;

    /**
     * 开关机（GUI ToggleButton 驱动）；新放置默认 true（plan §3.6.1：成型+流体足即预热）。
     * 配方运行绑定语义（r-logi-power-bind）：关机只阻止开下一批，在飞批次继续按结算口径
     * 计费（蒸汽/润滑按 wip 口径、增幅液照扣）直至配方读完产出排空；断供中止也会经
     * {@link #supplyAbort} 置 false（主控断电）。
     */
    protected boolean machineEnabled = true;

    /** 吞吐记账：集群累计处理矿数（NBT 持久，批执行经 addProcessedOre 累加）。 */
    protected long totalProcessedOre = 0L;

    /** 吞吐记账：最近一秒处理矿数（20t 窗口发布，停机/未成型清 0；进度词条直读）。 */
    protected double lastThroughputOrePerSec = 0D;

    /** 真实批吞吐 20t 对齐窗口累计（ClusterBatchHost.addRealBatchThroughput 累加，结算时发布清零）。 */
    private int throughputWindowItems = 0;

    /** 20t 双流体原子结算锁存（蒸汽+润滑均足=true）：驱动断供降温与 ClusterBatchHost 判据。 */
    protected boolean thermalSupplyOkLatched = false;

    /**
     * 断供中止一次性通知锁存（r-logi-power-bind，NBT 持久 "supplyAbortNotified"）：任一断供中止
     * 边沿置 true 并向物主发一次 gtsr.chat.cluster.supply_abort（范式同
     * MTELargeSolarOverpressureArray.stopForMissingWater 的 mNoWaterNotified——latch 随 NBT 持久、
     * 重载后不重复骚扰，重新开机 {@link #setMachineEnabled(true)} 清除）。
     */
    private boolean supplyAbortNotified = false;

    /** 载入重连提示（x,y,z,dim,pad,segment 六元组；读档回填，周期重连时消费）。 */
    private final List<int[]> pendingReconnectHints = new ArrayList<>();

    /** FX 吞吐记账锚点：最近一次链批执行成功的服务端 tick（getBaseMetaTileEntity().getTimer()；MIN_VALUE=从未）。 */
    protected long lastBatchServerTick = Long.MIN_VALUE;

    /** @return 最近一次真实批成功的服务端 tick（吞吐记账锚点；旧 isFxWorking 40t 窗口判据已删）。 */
    public long getLastBatchServerTick() {
        return lastBatchServerTick;
    }

    /** 客户端粒子工作态（getUpdateData/onValueUpdate bit0 通道同步；onPostTick 客户端分支据此喷粒子）。 */
    protected boolean mWorkingForFX = false;

    /** 终端 GUI 当前选中的物流单元下标（GUI 交互态；越界由 getSelectedLogisticsUnit 兜底 null）。 */
    protected int selectedLogisticsIndex = 0;

    // —— 边沿锁存（日志防重与结构破坏降温判断）——

    /** 加热中锁存（供给短缺 WARN 边沿与结构破坏降温判断用）。 */
    private boolean wasHeating = false;

    /** 满热锁存（结构破坏致满热降温 WARN 边沿用）。 */
    private boolean wasFullHeat = false;

    /** 供给正常锁存（充足→短缺 WARN 边沿用）。 */
    private boolean wasSupplyOk = false;

    /** 最近一次成功批实际命中的链步集合（瞬态，r6-S8 EU 实扣参与闸；不持久化）。 */
    private EnumSet<ChainLink> lastBatchLinks = EnumSet.noneOf(ChainLink.class);

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
     * {@link #getPreheatProgress()}；吞吐接入 {@link #getLastThroughputOrePerSec()}（20t 真实窗口发布）。
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
        int tier = Math.max(1, Math.min(4, ChannelDataAccessor.getChannelData(stackSize, "tier")));
        ItemStack tierTrigger = gregtech.api.util.GTUtility.copyAmount(tier, stackSize);
        buildPiece(
            ClusterStructureDef.PIECE_MAIN,
            tierTrigger,
            hintsOnly,
            ClusterStructureDef.mainOffsetA(),
            ClusterStructureDef.mainOffsetB(),
            ClusterStructureDef.mainOffsetC());
        int extSegments = extensionSegments(stackSize);
        for (int k = 0; k < extSegments; k++) {
            buildPiece(
                ClusterStructureDef.PIECE_EXT,
                tierTrigger,
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
        int tier = Math.max(1, Math.min(4, ChannelDataAccessor.getChannelData(stackSize, "tier")));
        ItemStack tierTrigger = gregtech.api.util.GTUtility.copyAmount(tier, stackSize);
        int built = survivalBuildPiece(
            ClusterStructureDef.PIECE_MAIN,
            tierTrigger,
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
                tierTrigger,
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

    /** stackSize 段数语义：总段数（含主段）→ 延伸段数 = 总段数-1，钳到 {@link ClusterTopology} 上限。 */
    private static int extensionSegments(ItemStack stackSize) {
        if (stackSize == null || stackSize.stackSize < 1) return 0;
        return Math.min(stackSize.stackSize - 1, ClusterTopology.MAX_EXTENSION_SEGMENTS);
    }

    /**
     * 结构检查全流程（父类 MTEMultiBlockBase：不加错误即成型，mMachine 由父类按 errors 空否统一管理）。
     *
     * <p>
     * 流程：复位（拓扑/垫位/延伸计数/耐压仓/挂点中心/电源边沿锁存 → 四 tier 归 -1 → 旧单元断开）→
     * 主段 checkPiece → 延伸段循环（失配即停；失配段之后仍可识别延伸结构时上报断层错误
     * {@link ClusterStructureError#extensionBreak}）→ 四族同级校验 → 输入仓上限校验
     * （通用 1..10、蒸汽类合计 0..10，plan §3.3.2 删除总线/能源校验；终验反馈 FA）→ tier 统一下发 → 收尾
     * （模块冲突取走上报、挂点中心注册、供给锁存乐观复位）。
     *
     * <p>
     * 所有失败路径统一 rollbackFormation（回滚四 tier、拆除本次收集连接、清拓扑）并输出
     * 主结构破坏边沿日志（仅此前已成型时）；模块冲突不阻断成型（仅上报）。
     */
    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        boolean wasFormed = mMachine;
        int prevSegments = topology.getSegmentCount();
        int prevUnits = topology.getUnits()
            .size();

        // 1) 复位
        List<MTEClusterUnitBase> previousUnits = new ArrayList<>(topology.getUnits());
        topology.clear();
        occupiedSlots.clear();
        extensionCount = 0;
        pressureSteamHatches.clear();
        logisticsPowerLatch.clear();
        ClusterParticleFx.clearClusterAirCandidates(this);
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
            failFormation("主段结构失配", wasFormed, prevSegments, prevUnits);
            return;
        }

        // 3) 延伸段循环：失配即停（正常终止，仅主段合法）；第 5 参 null 防止"仅主段"合法形态被
        // 边界外失配误伤。断层检测：失配段之后仍可识别延伸结构（下一延伸段底框采样非空气）→
        // 断层错误上报 + 断裂段登记，结构不成型（E1a 遗留收尾）
        for (int k = 0; k < ClusterTopology.MAX_EXTENSION_SEGMENTS; k++) {
            if (!checkPiece(
                ClusterStructureDef.PIECE_EXT,
                ClusterStructureDef.extOffsetA(k),
                ClusterStructureDef.extOffsetB(),
                ClusterStructureDef.extOffsetC(k),
                null)) {
                if (hasStructureBehind(k)) {
                    errors.add(ClusterStructureError.extensionBreak(k + 1));
                    failFormation("延伸段断层@" + (k + 1), wasFormed, prevSegments, prevUnits);
                    topology.setBrokenExtensionSegment(k + 1);
                    return;
                }
                break;
            }
            extensionCount = k + 1;
        }

        // 4) 四族同级
        if (mCasingTier < 0 || mPipeTier < 0 || mFrameTier < 0 || mFireboxTier < 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            failFormation("四族 tier 未定", wasFormed, prevSegments, prevUnits);
            return;
        }
        if (mCasingTier != mPipeTier || mCasingTier != mFrameTier || mCasingTier != mFireboxTier) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.tier_mismatch"));
            failFormation("四族 tier 混拼", wasFormed, prevSegments, prevUnits);
            return;
        }

        // 5) 输入仓上限校验（终验反馈 FA）：通用输入仓（MTEHatchInput 且非蒸汽类）1..10、
        // 蒸汽仓类（MTESteamInputHatchGeneric + 耐压仓）合计 0..10；先剔除失配延伸段中途收集的耐压仓
        pruneUnformedSegmentPressureHatches();
        int genericHatchCount = 0;
        int steamHatchCount = pressureSteamHatches.size();
        for (MTEHatch hatch : mInputHatches) {
            if (hatch instanceof MTESteamInputHatchGeneric) steamHatchCount++;
            else if (hatch instanceof MTEHatchInput) genericHatchCount++;
        }
        if (genericHatchCount < 1) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.missing_fluid_hatch"));
            failFormation("缺少通用输入仓", wasFormed, prevSegments, prevUnits);
            return;
        }
        if (genericHatchCount > 10) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.input_hatch_limit"));
            failFormation("通用输入仓超上限@" + genericHatchCount, wasFormed, prevSegments, prevUnits);
            return;
        }
        if (steamHatchCount > 10) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.steam_hatch_limit"));
            failFormation("蒸汽仓类超上限@" + steamHatchCount, wasFormed, prevSegments, prevUnits);
            return;
        }

        // 6) tier 定级统一下发；D2/D3 同级强制：自身已成型且 tier 与集群不一致的单元就地剔除
        // （disconnect+撤槽），不阻断其余单元与集群成型；未成型单元（tier<0）保留连接
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

        // 7) 收尾：供给锁存乐观复位（首个 20t 结算前按充足处理，防成型初即无谓衰减）→
        // 全输入 hatch 贴图按成型 tier 刷新（3.5.2；失败路径不刷新）→
        // 冲突取走上报 → 成型/段数边沿日志 → debug 扫描统计
        thermalSupplyOkLatched = true;
        updateAllHatchTextures();
        finishFormation(wasFormed, prevSegments, prevUnits);
    }

    /**
     * 全输入 hatch 贴图刷新（3.5.2 tier 联动，MTELargeSteamFurnace.updateAllHatchTextures 范式）：
     * 以 {@link #getCasingTextureID()} 刷新全部总控输入语义 hatch——mInputHatches 覆盖 A 位标准
     * 输入仓与蒸汽输入仓，pressureSteamHatches 覆盖耐压蒸汽输入仓。hatch 贴图自身状态不随宿主
     * （MTEHatch.updateTexture final，需显式刷新），仅成型成功末尾调用（结构失败/tier 未决不刷新，
     * 保持 hatchAdder 静态青铜 hint 口径）。末尾 issueTileUpdate 把含新 tier 位组的 byte 通道推给
     * 客户端（见 {@link #getUpdateData()}）。
     */
    private void updateAllHatchTextures() {
        int textureId = getCasingTextureID();
        for (MTEHatch hatch : mInputHatches) {
            if (hatch != null) hatch.updateTexture(textureId);
        }
        for (MTEHatchPressureSteamInput hatch : pressureSteamHatches) {
            if (hatch != null) hatch.updateTexture(textureId);
        }
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().issueTileUpdate();
        }
    }

    /** 四族 tier 回滚到未成型态（-1）。 */
    private void rollbackTiers() {
        mCasingTier = -1;
        mPipeTier = -1;
        mFrameTier = -1;
        mFireboxTier = -1;
    }

    /** 失败路径统一收尾：回滚 tier + 拆除本次收集连接清空拓扑 + 冲突取走 + 热量清零 + 主结构破坏边沿日志（含原因）。 */
    private void failFormation(String reason, boolean wasFormed, int prevSegments, int prevUnits) {
        rollbackFormation();
        // 结构失效即清零热量（切片 5d 增强项，幂等）：tickServer 的成型→未成型下降沿 reset（批次 1
        // 接线）至多晚一 tick，此处直调提前到失败时刻，两者幂等共存
        preheat.reset();
        lastModuleConflicts.clear();
        lastModuleConflicts.addAll(ClusterStructureDef.drainModuleConflicts());
        if (wasFormed) {
            GTSteamReborn.LOG
                .warn("{}主结构破坏: {} 原因={} 原段数={} 原模块数={}", LOG_PREFIX, logCoords(), reason, prevSegments, prevUnits);
        }
    }

    /** 成功路径收尾：冲突取走上报（不阻断成型）。 */
    private void finishFormation(boolean wasFormed, int prevSegments, int prevUnits) {
        lastModuleConflicts.clear();
        lastModuleConflicts.addAll(ClusterStructureDef.drainModuleConflicts());
    }

    /** 失败路径统一回滚：回滚四 tier + 拆除本次扫描已收集的单元连接并清空拓扑/垫位登记/耐压仓。 */
    private void rollbackFormation() {
        rollbackTiers();
        for (MTEClusterUnitBase unit : topology.getUnits()) {
            unit.disconnect();
        }
        topology.clear();
        occupiedSlots.clear();
        pressureSteamHatches.clear();
        extensionCount = 0;
    }

    /**
     * 延伸断层探测：第 failedK 延伸段失配后，采样第 failedK+1 延伸段底框（layer13/14 三个可靠
     * 实心位，坐标换算经 ClusterStructureDef 偏移族）是否存在非空气方块——存在即玩家在缺口
     * 之后仍建了延伸结构（断层，需上报错误）；全空气即正常短簇终止。
     */
    private boolean hasStructureBehind(int failedK) {
        int j = failedK + 1;
        if (j >= ClusterTopology.MAX_EXTENSION_SEGMENTS) return false;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        World world = base == null ? null : base.getWorld();
        if (world == null) return false;
        int aOff = ClusterStructureDef.mainOffsetA();
        int bOff = ClusterStructureDef.mainOffsetB();
        int cOff = ClusterStructureDef.extOffsetC(j);
        // (col, layer, depthRow) 采样：EXT 底框三个实心位（见 ClusterStructureDef.SHAPE_EXT
        // y13/y14）——{8,13,4}=y13 深4 列8 'B'（段中央 B 管道排）、{2,14,2}=y14 深2 列2 'C'、
        // {20,14,5}=y14 深5 列20 'A'。语义：第 j 段建成后这些位必为非空气方块；全空气即缺口
        // 之后无延伸结构（正常短簇终止，等级 1 仅 20 深基础层不成型也不误报断裂）。
        int[][] samples = { { 8, 13, 4 }, { 2, 14, 2 }, { 20, 14, 5 } };
        for (int[] s : samples) {
            Vec3Impl off = getExtendedFacing().getWorldOffset(new Vec3Impl(s[0] - aOff, s[1] - bOff, s[2] - cOff));
            int x = base.getXCoord() + off.get0();
            int y = base.getYCoord() + off.get1();
            int z = base.getZCoord() + off.get2();
            if (!world.blockExists(x, y, z)) continue;
            if (!world.getBlock(x, y, z)
                .isAir(world, x, y, z)) return true;
        }
        return false;
    }

    /**
     * 耐压蒸汽输入仓直收（A 外壳位结构 adder 回调入口，ClusterStructureDef.addControllerInputHatch
     * 调用；终验反馈 FA 取代旧硬编码偏移枚举收集 collectPressureSteamHatches——已删）。checkMachine
     * 复位段已清列表防陈旧引用，此处引用级去重防同轮重复收集。
     */
    public void registerPressureSteamHatch(MTEHatchPressureSteamInput hatch) {
        if (hatch == null || pressureSteamHatches.contains(hatch)) return;
        pressureSteamHatches.add(hatch);
    }

    /**
     * 剔除未成型延伸段中途收集的耐压仓：延伸段 checkPiece 失配（非断层、正常短簇终止）时，
     * 失败段内先于失配元素注册的耐压仓仍留在列表；按 {@link ClusterStructureDef#segmentOfWorldPos}
     * 反解段号，段号超出已成型段（&gt; extensionCount）或 TE 失联即移除。上限校验前调用。
     */
    private void pruneUnformedSegmentPressureHatches() {
        if (getBaseMetaTileEntity() == null) {
            pressureSteamHatches.clear();
            return;
        }
        pressureSteamHatches.removeIf(hatch -> {
            IGregTechTileEntity hatchTe = hatch.getBaseMetaTileEntity();
            if (hatchTe == null) return true;
            int segment = ClusterStructureDef
                .segmentOfWorldPos(this, hatchTe.getXCoord(), hatchTe.getYCoord(), hatchTe.getZCoord());
            return segment > extensionCount;
        });
    }

    /**
     * 客户端镜像延伸段数（字节通道 packed 域解码，r9）：{@link #updateClientFxAirCandidates}
     * 据此注册集群 'e' 精准候选；服务端权威为 {@link #extensionCount}，本镜像不入 NBT。
     */
    private int mSyncedExtensionCount = 0;

    /** 客户端 'e' 精准候选已注册的延伸段数（-1 = 未注册/已清；边沿去重与配对清理）。 */
    private int fxAirCandidatesExtensionCount = -1;

    /**
     * 客户端 'e' 精准候选注册/清理（r9，取代旧挂点中心惰性扫描）：粒子候选不再扫描世界单元，
     * 直接按字节同步的 tier（成型信号，&ge;0）与延伸段数注册
     * {@link ClusterStructureDef#clusterAirFxOffsets(int)} 的全部 'e' 位——主段 55 格 + 每延伸段
     * 15 格（重复注册整体替换，段数增长沿自动扩容）；tier 归 -1（结构断裂）时配对
     * {@link ClusterParticleFx#clearClusterAirCandidates} 清本客户端实例 key。每 tick 客户端分支
     * 调用，注册/清理仅在边沿各执行一次。
     */
    private void updateClientFxAirCandidates() {
        if (mCasingTier < 0) {
            if (fxAirCandidatesExtensionCount >= 0) {
                fxAirCandidatesExtensionCount = -1;
                ClusterParticleFx.clearClusterAirCandidates(this);
            }
            return;
        }
        if (fxAirCandidatesExtensionCount == mSyncedExtensionCount) return;
        ClusterParticleFx
            .registerClusterAirCandidates(this, ClusterStructureDef.clusterAirFxOffsets(mSyncedExtensionCount));
        fxAirCandidatesExtensionCount = mSyncedExtensionCount;
    }

    /** @return 四族 tier 全部相等且有效时返回该 tier 下标（0-3），否则 -1（混拼或未成型）。 */
    public int getStructureTierIndex() {
        if (mCasingTier < 0 || mCasingTier != mPipeTier || mCasingTier != mFrameTier || mCasingTier != mFireboxTier) {
            return -1;
        }
        return mCasingTier;
    }

    /**
     * NBT 载入 tier 回写（渲染过渡用，ClusterPersistence.read 调用）：载入初至首次结构重检前，
     * 控制器底材贴图按存档 tier 显示。结构重检仍是 tier 的最终权威——checkMachine 开头
     * {@link #rollbackTiers()} 复位 -1 后按实际方块重推导；越界值忽略（缺键走新机器默认 -1）。
     * 只写 mCasingTier 单族，{@link #getStructureTierIndex()} 在四族未齐时仍恒 -1，无逻辑旁路。
     */
    void applyLoadedCasingTier(int tier) {
        if (tier >= 0 && tier < ClusterParams.TIER_COUNT) mCasingTier = tier;
    }

    /** @return 集群拓扑（live 引用，结构重检时由 checkMachine 清空重建）。 */
    public ClusterTopology getTopology() {
        return topology;
    }

    /** @return 延伸段数（主段外成功成型的段数，0 = 仅主段）。 */
    public int getExtensionCount() {
        return extensionCount;
    }

    /** @return 最近一次结构扫描的模块冲突（结构仍成型；GUI 上报用，调用方不得修改）。 */
    public List<ClusterStructureError> getLastModuleConflicts() {
        return lastModuleConflicts;
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

    /** 垫位登记键打包：(segment,padId) → long（segment 上界 10、padId 上界 2，无碰撞）。 */
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
     * 触发一次 checkStructure，随后继续自减入负值不再触发）。事件式单次赋值，不得放进每 tick
     * 路径反复置位（结构重检保持事件式，禁止周期全量 checkMachine）。
     */
    public void onUnitRemoved(MTEClusterUnitBase unit) {
        // 切片 5d：topology.removeUnit 统一剔除清单+槽表（直接 getUnits().remove 只清清单、槽表残留
        // 幽灵槽使 GUI 快照仍显示该单元）；本控 occupiedSlots 登记由 forgetSlotsOf 清理
        topology.removeUnit(unit);
        forgetSlotsOf(unit);
        unit.disconnect();
        mStartUpCheck = 100;
    }

    /** @return 总控多方块结构当前是否成型（模块侧定点反解验证用，MTEClusterUnitBase 成型边沿调用）。 */
    public boolean isClusterStructureFormed() {
        return mMachine;
    }

    /**
     * 事件式重检请求（终验反馈缺陷1根因B）：模块自身成型成功但尚未接入集群时，由
     * {@code MTEClusterUnitBase.checkMachine} 成功边沿经定点反解（80 格内找到本总控且已成型）
     * 调用。与 {@link #onUnitRemoved} 同口径——单次置 mStartUpCheck=100，100 tick 后父类触发一次
     * checkStructure 扫描挂点接入该模块；事件式单次赋值，不得放进每 tick 路径反复置位
     * （红线：禁止周期全量 checkMachine）。
     */
    public void requestStructureRecheck() {
        mStartUpCheck = 100;
    }

    /**
     * 服务端编排（每 tick，O2：只编排与状态汇聚）。
     *
     * <p>
     * 顺序：super（父类事件式结构重检/runMachine）→ 客户端粒子分支（'e' 精准候选边沿注册 +
     * 工作态 mWorkingForFX 每 tick 喷粒子）→ 总控工作态覆写（决策 6：setActive 与 preheat 升温入参逐字
     * 同口径，零配方总控的正面贴图随热量/供给联动）→ 未成型衰减分支（头部懒加载守卫：决策 13，
     * 重载首检窗口内只衰减豁免，不衰减/不清红标/不写边沿日志/不结算）→ 周期重连容错 →
     * 每 tick 热量推进（供给态 = 20t 结算锁存）→ 每 20t 结算编排（settleSteamEconomy）。
     */
    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        // 客户端：'e' 精准候选边沿注册/清理（成型信号 = 字节同步 mCasingTier ≥ 0，段数增长沿自动
        // 扩容）；工作态（mWorkingForFX，getUpdateData bit0 结构正常工作口径）时每 tick 喷粒子，
        // 其余客户端逻辑无
        if (!aBaseMetaTileEntity.isServerSide()) {
            updateClientFxAirCandidates();
            if (mWorkingForFX) ClusterParticleFxClient.spawnParticles(this);
            return;
        }

        // 总控工作态覆写（SR-Cluster-r5 决策 6 + r-logi-power-bind 口径统一，范式
        // MTEClusterUnitBase.onPostTick）：本控零配方 → GT5U 基类 setActive(mMaxProgresstime>0)
        // 恒 false，正面贴图恒停机；改为显式覆写（isClusterWorkingForDisplay：成型+供给锁存+
        // [开机且允许工作 || 有在飞物流配方]），满热+供汽自然保持，关电/断供只挡下一批、
        // 在飞收尾期间动画照常；未成型时亦为 false（覆盖下方早退分支的工作态清位）
        aBaseMetaTileEntity.setActive(isClusterWorkingForDisplay());

        // 结构未成型：停机衰减（-5/tick）、经济红标清位、吞吐清 0；满热丢失边沿日志。
        // 懒加载守卫（SR-Cluster-r5 决策 13）：GT5U 重载后 mStartUpCheck=100 首检窗口内 mMachine
        // 尚未由 checkStructure 判定——此时不得衰减热量/清经济红标/写边沿日志/结算（NBT 热量跨
        // 重载冻结保持）；窗口过后结构真坏则照常衰减。（旧"仅关粒子窗"守卫已随静态窗口 API 删除——
        // 粒子门控唯一权威是字节同步 mWorkingForFX，客户端实例的挂点候选由 tier 归 -1 配对清理）
        if (!mMachine) {
            if (getmStartUpCheck() > 0) {
                return;
            }
            preheat.tickServer(false, false, false);
            economy.clearFlags();
            thermalSupplyOkLatched = false;
            lastThroughputOrePerSec = 0D;
            throughputWindowItems = 0;
            if (wasHeating || (wasFullHeat && !preheat.isReady())) {
                if (wasFullHeat && !preheat.isReady()) {
                    GTSteamReborn.LOG.warn(
                        "{}满热→降温: {} 原因=结构破坏 热量={}%",
                        LOG_PREFIX,
                        logCoords(),
                        (int) Math.round(preheat.getProgress() * 100D));
                }
                wasHeating = false;
                wasFullHeat = preheat.isReady();
            }
            return;
        }

        // 周期重连容错（NAC 口径：节流间隔内做一次全员连接修复；本机无能量分摊，仅重连）
        if (aTick % ClusterParams.RECONNECT_INTERVAL_TICKS == 0) {
            maintainClusterLinks();
        }

        // 每 tick 热量推进（修复 20 倍定标：供给态用 20t 结算锁存值；+16.667/-2.5/-5 由状态机自理）。
        // 双门控（终验反馈缺陷2，范式 MTEAmmoniaPlant:465 mMachine && isAllowedToWork()）：GUI 开关
        // 与物理电源（软锤/红石）任一关闭即按停机处理——预热停增、走 -1%/s 停机衰减（保热量设计）。
        preheat
            .tickServer(machineEnabled && getBaseMetaTileEntity().isAllowedToWork(), mMachine, thermalSupplyOkLatched);

        // 蒸汽/润滑液秒级结算编排（每 20 tick 一次）
        if (aTick % SETTLE_INTERVAL_TICKS == 0) {
            settleSteamEconomy();
        }
    }

    /**
     * 周期重连容错：遍历拓扑（快照遍历——剔除经 {@link ClusterTopology#removeUnit} 同时动清单与
     * 槽表，属主特权下也不得对 live 清表边遍历边结构性修改），TE 失联（getBaseMetaTileEntity()==null）
     * 的单元剔除并 disconnect；集群引用丢失（读档/区块重载边界）的单元重 connect。本控制器为
     * topology 唯一属主；切片 5d：剔除统一走 removeUnit 消幽灵槽（清单+槽表一起清）。
     */
    protected void maintainClusterLinks() {
        for (MTEClusterUnitBase unit : new ArrayList<>(topology.getUnits())) {
            if (unit.getBaseMetaTileEntity() == null) {
                topology.removeUnit(unit);
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

    /**
     * 蒸汽/润滑 20t 结算编排（完整状态口径，plan §3.6.2 数值总表 + r-logi-power-bind 配方运行绑定）。
     *
     * <p>
     * 顺序：吞吐窗口发布 → 物流单元物理电源边沿轮询（软锤复位/低温关机边沿）→ 分支结算：
     * <ul>
     * <li><b>关机早退（仅 !powerOn 且无在飞批次）</b>：供给锁存与红标清位、不执行链、不计费
     * （关机只阻止开下一批——无在飞批次时收尾费也为零）；</li>
     * <li><b>预热结算（未满热且无在飞批次）</b>：2000+10 原口径；</li>
     * <li><b>运行结算（满热，或无在飞批次之外的一切在飞收尾态）</b>：固定项 + 加权链路段 C——
     * C 聚合按 powerOn 选 {@link #enabledLogisticsUnits()}（开机=全量可执行链需量）或
     * 在飞 WIP 单元列表（关电收尾只对在飞配方计费）；r.ok 时冷却递减始终执行（在飞批次照常读秒）、
     * runChains 仅 powerOn 调用（关电不开新批）、wip&gt;0 照常逐台实扣增幅液（×wip 连续计费）。</li>
     * </ul>
     * 断供中止（wip&gt;0 时：结算 r.ok=false，或任一 active 增幅模块实扣失败，或
     * booster.getFailedCount()&gt;0=任一在场增幅模块本秒无法支付）：{@link #supplyAbort()}
     * 终止全部在飞配方（吞料）、主控断电、一次性聊天通知物主。
     *
     * <p>
     * 供给锁存 thermalSupplyOkLatched=结算 ok 且未中止；边沿日志共用尾部。
     * 刚满热的秒段（justReachedFullHeat）由 EconomySettleResult 口径处理，主控不再自行双调用。
     */
    private void settleSteamEconomy() {
        publishThroughputWindow();
        pollLogisticsPowerEdges();

        // powerOn = GUI 开机 && 物理电源开（软锤/红石）；WIP = 配方运行中的物流单元（在飞批次）
        boolean powerOn = machineEnabled && getBaseMetaTileEntity().isAllowedToWork();
        List<MTEBasicLogisticsUnit> wipUnits = collectWipLogisticsUnits();
        int wip = wipUnits.size();

        // 关机且无在飞批次：0 L/s / 0 L/s，供给锁存与红标清位，不执行链（热量走停机衰减 -1%/s）
        if (!powerOn && wip == 0) {
            thermalSupplyOkLatched = false;
            economy.clearFlags();
            logHeatAndSupplyEdges(null);
            return;
        }

        ClusterSteamEconomy.EconomySettleResult r;
        BoosterState booster = null;
        boolean aborted = false;
        if (!preheat.isReady() && wip == 0) {
            // 预热中：FIXED_CLUSTER_STEAM_LPS 蒸汽 + 润滑恒定段；本秒能抵达满热时
            // 结果携带 justReachedFullHeat，该秒不得再叠加运行结算（无双扣），下一秒起转 settleRunFull
            r = economy.settlePreheatFull(this);
        } else {
            // 运行结算（满热含无可执行链；或无在飞之外的在飞收尾态——关电/未满热但有在飞批次时
            // 不走预热/关机早退，按运行口径对在飞批次连续计费直至读完）：固定项
            // （FIXED_CLUSTER_STEAM_LPS × FIXED_STEAM_TIER_MULT[tier]，r6-S6 新口径）+ 加权链路段 C；
            // C 聚合按 powerOn 选全量启用单元（开机）或在飞 WIP 单元（关电收尾只对在飞计费）；
            // 切片 5b：聚合只计 isModuleEnabled 的物流单元（混合成型态不高估需量）
            booster = BoosterState.aggregate(topology.getBoosterUnits(), wip);
            double c = ExecutionPlan.computeAggregateSteamC(
                powerOn ? enabledLogisticsUnits() : wipUnits,
                topology,
                getStructureTierIndex(),
                booster);
            r = economy.settleRunFull(this, runFixedSteamLps(), c);
            boolean amplifierShortage = false;
            if (r.ok) {
                // 冷却递减始终执行（在飞批次的配方时间照常读秒，与是否开机无关）；链执行仅开机
                // （关电只阻止下一批）。本秒被递减的单元（含刚归零者）本轮不开批——
                // 与旧版「递减-20 后 continue」逐字同节拍（勿改，E4 补偿口径依赖）
                List<MTEBasicLogisticsUnit> decremented = decrementChainCooldowns();
                if (powerOn) runChains(decremented);
                if (wip > 0) {
                    // 按本秒 WIP 物流单元数连续计费；链批是否实际完成不影响实扣。
                    // 任一 active 模块实扣失败，或任一在场模块本秒无法支付（failed>0，含缺增幅液）
                    // = 增幅液断供 → 中止
                    for (MTEBasicAmplifierUnit amplifier : booster.getActiveUnits()) {
                        if (!amplifier.tryConsumeAmplifierFluid(amplifier.amplifierFluidPerSec() * wip)) {
                            amplifierShortage = true;
                        }
                    }
                    if (booster.getFailedCount() > 0) amplifierShortage = true;
                }
            }
            // 断供中止（wip>0）：蒸汽/润滑结算失败，或增幅液断供 → 终止全部在飞配方（吞料）、
            // 主控断电、一次性通知物主（防在飞批次无限白嫖热量/增幅与半途产出悬空）
            aborted = wip > 0 && (!r.ok || amplifierShortage);
            if (aborted) supplyAbort();
        }
        thermalSupplyOkLatched = r.ok && !aborted;
        logHeatAndSupplyEdges(r);
    }

    /**
     * 断供中止（r-logi-power-bind，运行中断供立即终止在飞配方）：全部物流单元
     * {@link MTEBasicLogisticsUnit#abortPendingRun}（输入开批已扣、产出暂存丢弃=吞料，进度/批冷却/
     * 处理窗口归零）→ 主控断电（setMachineEnabled(false)，重新开机需玩家手动开启）→ 供给锁存清位；
     * 一次性 latch supplyAbortNotified（NBT 持久、重新开机清除）为 false 时置 true 并向物主发
     * gtsr.chat.cluster.supply_abort（范式同 MTELargeSolarOverpressureArray.stopForMissingWater）。
     */
    private void supplyAbort() {
        for (MTEBasicLogisticsUnit unit : topology.getLogisticsUnits()) {
            unit.abortPendingRun();
        }
        setMachineEnabled(false);
        thermalSupplyOkLatched = false;
        if (!supplyAbortNotified) {
            supplyAbortNotified = true;
            GTSRMachineEvent.sendToOwner(getBaseMetaTileEntity().getOwnerUuid(), "gtsr.chat.cluster.supply_abort");
        }
    }

    /**
     * 运行期固定蒸汽项（L/s，r6-S6 新口径）：{@code FIXED_CLUSTER_STEAM_LPS × FIXED_STEAM_TIER_MULT[集群 tier]}
     * ——节汽增幅封顶与惩罚只作用于加权链路段，本固定项不受影响；tier 未定/越界按青铜档防御。
     */
    long runFixedSteamLps() {
        int tier = getStructureTierIndex();
        if (tier < 0 || tier >= ClusterParams.TIER_COUNT) tier = 0;
        double extensionFactor = 1D + 0.1D * topology.getExtensionCount();
        return (long) Math
            .ceil(ClusterParams.FIXED_CLUSTER_STEAM_LPS * ClusterParams.FIXED_STEAM_TIER_MULT[tier] * extensionFactor);
    }

    /**
     * 供给错误边沿日志：仅输出供给充足→短缺 WARN（含短缺项与可用量探测），同时维护
     * wasHeating/wasFullHeat/wasSupplyOk 边沿锁存（tickServer 结构破坏降温判断依赖）。
     * r=null 表示关机路径（不输出供给边沿）。
     */
    private void logHeatAndSupplyEdges(ClusterSteamEconomy.EconomySettleResult r) {
        boolean heating = machineEnabled && mMachine && thermalSupplyOkLatched;
        boolean fullHeat = preheat.isReady();
        if (r != null) {
            if (!r.ok && wasSupplyOk) {
                // 短缺项与实际可用量（跨仓模拟探测，仅边沿时执行一次）
                List<MTEHatch> hatches = getClusterFluidInputHatches();
                long steamAvail = GTSRHatchFluidAccess
                    .probeFluidAmountAcross(hatches, Materials.Steam.getGas(Math.max(1, r.settledSteamLps)));
                long lubAvail = GTSRHatchFluidAccess
                    .probeFluidAmountAcross(hatches, Materials.Lubricant.getFluid(Math.max(1, r.settledLubricantLps)));
                GTSteamReborn.LOG.warn(
                    "{}供给充足→短缺: {} 蒸汽需求={}L/s 可用={}L 润滑需求={}L/s 可用={}L 短缺项={}",
                    LOG_PREFIX,
                    logCoords(),
                    r.settledSteamLps,
                    steamAvail,
                    r.settledLubricantLps,
                    lubAvail,
                    (!r.steamEnough && !r.lubricantEnough) ? "蒸汽+润滑" : r.steamEnough ? "润滑" : "蒸汽");
            }
            wasSupplyOk = r.ok;
        }
        wasHeating = heating;
        wasFullHeat = fullHeat;
    }

    /**
     * 已启用物流单元过滤视图（切片 5b）：C 聚合（2000+C 的 C）只计 {@code isModuleEnabled()}
     * （已连接集群且自身成型）的物流单元——拓扑按 D2/D3 语义可暂留未成型（tier&lt;0）单元，其链即便
     * 结构上可执行也不计入蒸汽需求，防混合成型态高估需量。链执行侧仍按原全表驱动（执行器自带闸门）。
     */
    public int countWipLogisticsUnits() {
        int count = 0;
        for (MTEBasicLogisticsUnit unit : topology.getLogisticsUnits()) {
            if (unit != null && unit.isWorkInProgress()) count++;
        }
        return count;
    }

    /**
     * 在飞物流单元收集（r-logi-power-bind）：{@link MTEClusterUnitBase#isWorkInProgress()} 的单元
     * 列表（结构扫描序）——关电收尾时 C 聚合只对在飞配方计费（{@code settleSteamEconomy} 传入
     * {@code ExecutionPlan.computeAggregateSteamC}），booster 聚合取其 size。
     */
    private List<MTEBasicLogisticsUnit> collectWipLogisticsUnits() {
        List<MTEBasicLogisticsUnit> wip = new ArrayList<>();
        for (MTEBasicLogisticsUnit unit : topology.getLogisticsUnits()) {
            if (unit != null && unit.isWorkInProgress()) wip.add(unit);
        }
        return wip;
    }

    private List<MTEBasicLogisticsUnit> enabledLogisticsUnits() {
        List<MTEBasicLogisticsUnit> enabled = new ArrayList<>();
        for (MTEBasicLogisticsUnit unit : topology.getLogisticsUnits()) {
            if (unit.isModuleEnabled()) enabled.add(unit);
        }
        return enabled;
    }

    /**
     * 真实吞吐 20t 窗口发布：累计值发布到读数字段后清零（下一窗口由批执行重新累计）。
     */
    private void publishThroughputWindow() {
        lastThroughputOrePerSec = throughputWindowItems;
        throughputWindowItems = 0;
    }

    /**
     * 物流单元物理电源边沿轮询（20t）：清理低温通知位并执行链重校验；true→false 且热量不满、
     * 链非空时输出模块低温关机 WARN。首次观测
     * （prev==null）只建锁存不发事件（GT5U 软锤路径直改 BaseMetaTileEntity.mWorks，无 MTE
     * 回调钩子，主控以边沿轮询对齐 §3.6.4 复位语义）。
     */
    private void pollLogisticsPowerEdges() {
        List<MTEBasicLogisticsUnit> units = topology.getLogisticsUnits();
        for (MTEBasicLogisticsUnit unit : units) {
            boolean allowed = unit.isPowerAllowed();
            Boolean prev = logisticsPowerLatch.put(unit, allowed);
            if (prev == null || prev == allowed) continue;
            if (allowed) {
                unit.onSoftHammerReset();
            } else if (preheat.getProgress() < 1.0D && !unit.getChain()
                .isEmpty()) {
                    GTSteamReborn.LOG.warn(
                        "{}模块低温关机: {} 段={} 槽={} 链长={} 热量={}%",
                        LOG_PREFIX,
                        logCoords(),
                        unit.getSegmentIndex(),
                        unit.getPadId(),
                        unit.getChain()
                            .length(),
                        (int) Math.round(preheat.getProgress() * 100D));
                }
        }
        logisticsPowerLatch.keySet()
            .retainAll(units);
    }

    /**
     * 链执行钩子（结算成功且满热、且主控开机时由 settleSteamEconomy 调用；r-logi-power-bind 调序：
     * 冷却递减已拆出 {@link #decrementChainCooldowns} 并由调用方先行执行，本方法只执行冷却 ≤0
     * 且<b>本秒未被递减</b>的单元——刚归零的本秒不开批，逐字保持旧版「递减-20 后 continue」
     * 节拍（勿改，E4 补偿口径依赖）。
     *
     * <p>
     * 逐物流单元驱动：冷却归零/无冷却的单元交 {@code ClusterChainExecutor.executeBatch} 三参批执行
     * （本类即 ClusterBatchHost）；执行器自带门控（暂存未排空/低温/链不可执行等零副作用返 0）。
     *
     * @param decrementedThisSecond 本结算秒被 {@link #decrementChainCooldowns} 递减过的单元
     *                              （含刚归零者），本轮跳过，下一秒才具备开批资格
     * @return 本秒是否至少一条链实际执行成功
     */
    protected boolean runChains(List<MTEBasicLogisticsUnit> decrementedThisSecond) {
        boolean anyExecuted = false;
        for (MTEBasicLogisticsUnit unit : topology.getLogisticsUnits()) {
            if (unit.getChainCooldownTicks() > 0) continue;
            if (decrementedThisSecond != null && decrementedThisSecond.contains(unit)) continue;
            if (ClusterChainExecutor.executeBatch(this, unit, this) > 0) {
                anyExecuted = true;
            }
        }
        return anyExecuted;
    }

    /**
     * 批冷却递减（结算节拍统一 -20，r-logi-power-bind 自 runChains 拆出）：settleSteamEconomy 在
     * r.ok 时无条件调用——关电/断供收尾路径下在飞批次的配方时间同样照常读秒（进度读完即排空
     * 暂存产出），递减只作用于 &gt;0 的单元（0-size 槽由总线自身 tick 收口的同款幂等口径）。
     *
     * @return 本秒被递减过的单元清单（含由 &gt;0 减至 ≤0 的刚归零单元），调用方据此让
     *         {@link #runChains} 本轮跳过——保持旧版当秒不抢跑节拍
     */
    private List<MTEBasicLogisticsUnit> decrementChainCooldowns() {
        List<MTEBasicLogisticsUnit> decremented = new ArrayList<>();
        for (MTEBasicLogisticsUnit unit : topology.getLogisticsUnits()) {
            if (unit.getChainCooldownTicks() > 0) {
                unit.setChainCooldownTicks(unit.getChainCooldownTicks() - SETTLE_INTERVAL_TICKS);
                decremented.add(unit);
            }
        }
        return decremented;
    }

    // ==================== ClusterBatchHost 契约（E4 执行器只消费本三方法） ====================

    /** {@inheritDoc} 瞬时热量分率 0..1（预热进度；&lt;1.0 时批执行走低温吞批路径 §3.6.4）。 */
    @Override
    public double heatFraction() {
        return preheat.getProgress();
    }

    /** {@inheritDoc} 最近一次 20t 双流体原子结算锁存（蒸汽+润滑均足=true；断供降温同判据）。 */
    @Override
    public boolean isThermalSupplyOkLatched() {
        return thermalSupplyOkLatched;
    }

    /**
     * {@inheritDoc} 真实成功批吞吐累计：同秒段多链求和进 20t 对齐窗口（结算时发布为矿石/s 读数），
     * 同时刷新批成功记账锚点。
     */
    @Override
    public void addRealBatchThroughput(int items) {
        if (items <= 0) return;
        throughputWindowItems += items;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) {
            lastBatchServerTick = base.getTimer();
        }
    }

    // ==================== GUI 数据接口（E6 并行依赖，签名冻结） ====================

    /**
     * 拓扑紧凑快照（E6 解码协议，30 槽 × 5 字节 = 150 字节）：槽序 = segment 升序 × pad 升序
     * （{@link ClusterTopology#getSlots()} 顺序）。每槽 5 字节注册表以解码端（ClusterTerminalData
     * 冻结常量注释为准，S5b 随迁自旧 MUI2 轨同值副本）为准，编码端适配解码端：
     * <ul>
     * <li>[0] typeId：0=空槽；1..7=加工分型（1 粉碎/2 洗矿/3 离心/4 热力离心/5 筛选/6 磁选/7 熔炼）；
     * 8..12=增幅分型（= 8+BoosterType.ordinal()：8 并行/9 速度/10 主产物/11 副产物/12 节汽）；
     * 13=物流（无分型，在槽即 13）；255=占位未识别（加工/增幅控制器在槽但未自成型或未连接本主控，
     * GUI「未运行，暂无法识别」，不得伪装空位）；</li>
     * <li>[1] tier：主控下发集群 tier 0-3，0xFF=未下发（-1）/空槽；</li>
     * <li>[2] stateOrdinal：{@link ClusterUnitStatus} ordinal，0xFF=空槽（解码端现不读取，布局契约保留）；</li>
     * <li>[3] errId（优先级 1&gt;2&gt;3，空槽恒 0）：1=模块冲突（lastModuleConflicts 命中本槽）；
     * 2=tier 不匹配（已连接且自身成型 tier 与主控下发 tier 不一致）；3=未关联集群（cluster 引用非
     * 本主控）；4=延伸断裂为结构级错误，走 KEY_BREAK 独立通道，本快照不编出；</li>
     * <li>[4] linkId：物流槽=该单元在拓扑物流列表（结构扫描序）中的下标（0..254 字节域，T10 扩段后
     * 实际上限随拓扑物流单元数增长；与链路页选中下标同序）；非物流槽与空槽=255。</li>
     * </ul>
     */
    public byte[] buildTopologySnapshot() {
        byte[] out = new byte[ClusterTopology.SLOT_COUNT * 5];
        List<ClusterTopology.SlotSnapshot> slots = topology.getSlots();
        List<MTEBasicLogisticsUnit> logisticsUnits = topology.getLogisticsUnits();
        boolean[] conflictMarks = moduleConflictSlotMarks(slots.size());
        for (int i = 0; i < ClusterTopology.SLOT_COUNT && i < slots.size(); i++) {
            ClusterTopology.SlotSnapshot slot = slots.get(i);
            int o = i * 5;
            MTEClusterUnitBase unit = slot.unit;
            if (unit == null) {
                out[o] = (byte) ClusterTerminalData.TYPE_EMPTY;
                out[o + 1] = (byte) 0xFF;
                out[o + 2] = (byte) 0xFF;
                out[o + 3] = 0;
                out[o + 4] = (byte) 0xFF;
                continue;
            }
            out[o] = (byte) unitTypeId(unit);
            int tier = unit.getClusterTier();
            out[o + 1] = (byte) (tier < 0 || tier > 255 ? 0xFF : tier);
            out[o + 2] = (byte) unit.getUnitStatus()
                .ordinal();
            out[o + 3] = (byte) unitErrId(unit, conflictMarks[i]);
            out[o + 4] = (byte) logisticsLinkIndex(unit, logisticsUnits);
        }
        return out;
    }

    /**
     * 单元 → typeId（E6 稳定注册表，对齐 ClusterTerminalData 冻结注册表）。物流恒 13（单型无分型，
     * 解码端 255「未识别」语义只覆盖加工/增幅）；加工/增幅须自成型（{@code mMachine}）且 cluster
     * 引用为本主控才报分型，否则 255 占位；增幅 = 8+BoosterType.ordinal()；加工七类 instanceof
     * 分派，未知子类防御性回 255（不伪装空位）。
     */
    private int unitTypeId(MTEClusterUnitBase unit) {
        if (unit instanceof MTEBasicLogisticsUnit) return 13;
        boolean formed = unit.isUnitStructureFormed() && unit.getCluster() == this;
        if (!formed) return ClusterTerminalData.TYPE_UNRECOGNIZED;
        if (unit instanceof MTEBasicAmplifierUnit amplifier) {
            return amplifier.getBoosterType() != null ? 8 + amplifier.getBoosterType()
                .ordinal() : ClusterTerminalData.TYPE_UNRECOGNIZED;
        }
        if (unit instanceof MTEUnitCrusher) return 1;
        if (unit instanceof MTEUnitOreWasher) return 2;
        if (unit instanceof MTEUnitCentrifuge) return 3;
        if (unit instanceof MTEUnitThermalCentrifuge) return 4;
        if (unit instanceof MTEUnitSifter) return 5;
        if (unit instanceof MTEUnitMagneticSeparator) return 6;
        if (unit instanceof MTEUnitFurnace) return 7;
        return ClusterTerminalData.TYPE_UNRECOGNIZED;
    }

    /**
     * 单元 → errId（E6 稳定注册表，优先级 1&gt;2&gt;3）。1=模块冲突（本槽命中 lastModuleConflicts）；
     * 2=tier 不匹配（已连接、自身成型 tier ≥ 0 且与主控下发 tier 不一致——未成型的 -1 不算不匹配，
     * 走 typeId 255/状态通道）；3=未关联集群（cluster 引用非本主控）；其余 0。延伸断裂不在此编出
     * （结构级错误走 KEY_BREAK 独立通道，见方法 javadoc）。
     */
    private int unitErrId(MTEClusterUnitBase unit, boolean conflictMark) {
        if (conflictMark) return ClusterTerminalData.ERR_MODULE_CONFLICT;
        boolean connected = unit.getCluster() == this;
        if (connected && unit.getUnitStructureTier() >= 0 && !unit.isTierValidForConnection()) {
            return ClusterTerminalData.ERR_TIER_MISMATCH;
        }
        return connected ? 0 : ClusterTerminalData.ERR_NOT_CONNECTED;
    }

    /**
     * 单元 → linkId：物流槽=该单元在拓扑物流列表（结构扫描序）中的引用级下标（0..9，与链路页
     * KEY_SEL_LOGISTICS/KEY_LE_UNITS 同序，GUI tooltip 显示「已关联 #N+1」）；非物流槽与
     * 未入列单元（防御）=255。
     */
    private static int logisticsLinkIndex(MTEClusterUnitBase unit, List<MTEBasicLogisticsUnit> logisticsUnits) {
        if (!(unit instanceof MTEBasicLogisticsUnit logistics)) return 255;
        for (int i = 0; i < logisticsUnits.size() && i < 255; i++) {
            if (logisticsUnits.get(i) == logistics) return i;
        }
        return 255;
    }

    /**
     * 模块冲突槽标记（errId=1 数据源）：{@link #lastModuleConflicts}（结构扫描期挂点记录，仅结构
     * 重检时变化）中的 (segment,padId) → 槽下标 {@code segment*3+padId}（每段 3 垫槽，与快照槽序
     * 同构；越界下标防御丢弃）。{@link ClusterStructureError} 无参数访问器，(segment,padId) 经其
     * {@code serialize} 线上格式反解（LangText=判别子 0+lang key+参数列，LiteralText=判别子 1+文本，
     * 见 GT5U TranslatableText 线上协议）——确定性、服务端安全，不触碰客户端本地化。
     *
     * @param slotCount 快照槽位数
     * @return 槽位冲突标记（长度 slotCount；常态无冲突时零解析开销）
     */
    private boolean[] moduleConflictSlotMarks(int slotCount) {
        boolean[] marks = new boolean[slotCount];
        if (lastModuleConflicts.isEmpty()) return marks;
        for (ClusterStructureError error : lastModuleConflicts) {
            int[] segPad = parseModuleConflictSlot(error);
            if (segPad == null) continue;
            int index = segPad[0] * 3 + segPad[1];
            if (index >= 0 && index < slotCount) marks[index] = true;
        }
        return marks;
    }

    /** 反解单个模块冲突错误的 (segment,padId) 文本参数；非 module_conflict 键或格式不符返回 null（防御）。 */
    private static int[] parseModuleConflictSlot(ClusterStructureError error) {
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        try {
            error.serialize(buf);
            buf.readerIndex(0);
            if (buf.readUnsignedByte() != 0) return null; // LangText 判别子
            if (!ClusterStructureError.LANG_KEY_MODULE_CONFLICT.equals(buf.readStringFromBuffer(128))) return null;
            if (buf.readInt() < 2) return null; // 参数个数（segment, padId）
            int[] segPad = new int[2];
            for (int i = 0; i < 2; i++) {
                if (buf.readUnsignedByte() != 1) return null; // LiteralText 判别子
                segPad[i] = Integer.parseInt(
                    buf.readStringFromBuffer(8)
                        .trim());
            }
            return segPad;
        } catch (Exception ignored) {
            return null;
        } finally {
            buf.release();
        }
    }

    /** @return 热量百分比 0..100（量化口径，GUI 顶栏）。 */
    public int getHeatPercent() {
        return (int) Math.round(preheat.getProgress() * 100D);
    }

    /**
     * 蒸汽读数（停机/未成型为 0；运行中为最近一次结算需求，S8 显示转化：按最近成功结算的
     * 实际蒸汽种类 ÷divisor 折算——即「当前实际使用蒸汽种类」口径，普通蒸汽时与等效值一致）。
     */
    public int getSteamLps() {
        if (!mMachine || !machineEnabled) return 0;
        long gradeLiters = ClusterSteamEconomy.toGradeLiters(economy.getLastSteamLps(), economy.getLastSteamGrade());
        return (int) Math.min(Integer.MAX_VALUE, gradeLiters);
    }

    /**
     * S8/S10 接口：当前实际使用的蒸汽种类（最近一次成功结算锁存；未成功结算/零需求为 null，
     * 调用方按普通 Steam 处理）。供性能详情读数与 S10 性能面板做种类口径转化，本切片不改面板布局。
     */
    public ClusterParams.SteamGrade getActiveSteamGrade() {
        return economy.getLastSteamGrade();
    }

    /** @return 润滑油读数（L/s；停机/未成型为 0，运行中为最近一次结算需求）。 */
    public int getLubricantLps() {
        if (!mMachine || !machineEnabled) return 0;
        return (int) Math.min(Integer.MAX_VALUE, economy.getLastLubricantLps());
    }

    /** @return 真实窗口吞吐（矿石/s，20t 对齐窗口发布；停机/未成型为 0）。 */
    public int getThroughputPerSec() {
        return (int) Math.max(0L, (long) lastThroughputOrePerSec);
    }

    /** @return 供给异常位组（bit0=蒸汽不足，bit1=润滑不足，bit2=断供降温中；停机/未成型为 0）。 */
    public int getSupplyFlags() {
        if (!mMachine || !machineEnabled) return 0;
        int flags = 0;
        if (economy.isSteamShortage()) flags |= 0x01;
        if (!economy.isLubricantOk()) flags |= 0x02;
        if (!thermalSupplyOkLatched) flags |= 0x04;
        return flags;
    }

    /** @return 日志坐标串 "dim(x,y,z)"。 */
    private String logCoords() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return "?";
        return base.getWorld() == null ? "?"
            : base.getWorld().provider.dimensionId + "("
                + base.getXCoord()
                + ","
                + base.getYCoord()
                + ","
                + base.getZCoord()
                + ")";
    }

    // —— 字节通道（r9 重编码；T10 扩展域饱和口径）：bit0=工作态（结构正常工作四项判据）；
    // bit1-6=延伸段数与集群 tier 的无损打包域 packed = extensionCount×5 + (tier+1)（6 位上限
    // 63 → 显示域 extensionCount 0..11）。T10 结构上限扩到 19 段后，≥12 段的<b>权威</b>段数仍走
    // KEY 终端通道与 NBT（服务器侧 getExtensionCount 全量参与蒸汽/润滑系数），本字节通道仅承载
    // 客户端 FX/成型信号，编码端对 extensionCount 饱和钳到 11 防剥位错译。GT5U 事件通道剥 bit7
    // （BaseMetaTileEntity.receiveClientEvent 对事件 1 施加 &0x7F；初始数据同口径），7 位预算
    // （1 位工作态 + 6 位打包域）无法容纳 20 段 × 5 档 = 100 组合，协议扩位需另立切片决策。
    // ——（MTELargeSolarOverpressureArray 注释同口径在案）——

    @Override
    public void onValueUpdate(byte aValue) {
        mWorkingForFX = (aValue & 0x01) != 0;
        int packed = (aValue >>> 1) & 0x3F;
        mSyncedExtensionCount = packed / 5;
        // 客户端镜像集群 tier 供 getTexture 渲染与成型信号（服务端结构重检仍是权威；
        // MTELargeSteamFurnace/单元基类 byte 通道同范式）
        mCasingTier = packed % 5 - 1;
    }

    @Override
    public byte getUpdateData() {
        // 工作态 bit0 = isClusterWorkingForDisplay（与 onPostTick 的 setActive 覆写完全同源）：
        // 成型 + 20t 双流体供给锁存 + [开机（GUI 开关）且允许工作（软锤/红石）|| 有在飞物流配方]
        // ——粒子动画随结构工作态驱动（r-logi-power-bind：关电收尾在飞批次期间 bit0 保持）
        int ext = Math.max(0, Math.min(ClusterTopology.MAX_EXTENSION_SEGMENTS, extensionCount));
        int tierPlus1 = Math.max(0, Math.min(4, mCasingTier + 1));
        int packed = Math.min(ext, 11) * 5 + tierPlus1; // 6 位域饱和：ext≥12 时客户端 FX 层数钳到 11
        return (byte) ((packed << 1) | (isClusterWorkingForDisplay() ? 0x01 : 0x00));
    }

    /**
     * 主控工作态统一口径（r-logi-power-bind 抽取，onPostTick setActive 覆写与 getUpdateData bit0
     * 同源单一实现）：{@code mMachine && thermalSupplyOkLatched && ((machineEnabled &&
     * isAllowedToWork()) || countWipLogisticsUnits() > 0)}——关机/物理断电只阻止开下一批，
     * 在飞配方收尾期间动画与粒子照常；断供中止（锁存清位+全单元 abort）后立即回 false。
     */
    private boolean isClusterWorkingForDisplay() {
        return mMachine && thermalSupplyOkLatched
            && ((machineEnabled && getBaseMetaTileEntity().isAllowedToWork()) || countWipLogisticsUnits() > 0);
    }

    /** @return 预热状态机（NBT 编解码和 GUI 直读共用）。 */
    public ClusterPreheatController getPreheat() {
        return preheat;
    }

    /** @return 蒸汽经济结算器（红标/读数供 GUI 直读）。 */
    public ClusterSteamEconomy getSteamEconomy() {
        return economy;
    }

    /**
     * @return 统一蒸汽/润滑结算源（每次调用新建组合列表：mInputHatches + 耐压蒸汽输入仓；
     *         {@link ClusterSteamEconomy} 经本方法取仓，ME 仓兼容由 GTSRHatchFluidAccess 自理）。
     */
    public List<MTEHatch> getClusterFluidInputHatches() {
        List<MTEHatch> hatches = new ArrayList<>(mInputHatches.size() + pressureSteamHatches.size());
        hatches.addAll(mInputHatches);
        hatches.addAll(pressureSteamHatches);
        return hatches;
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

    /** @return 最近一秒处理矿数（20t 真实窗口发布；停机/未成型清 0；进度词条直读）。 */
    public double getLastThroughputOrePerSec() {
        return lastThroughputOrePerSec;
    }

    /** 载入重连提示清单回填（ClusterPersistence 读档路径写入，maintainClusterLinks 消费）。 */
    public void setPendingReconnectHints(List<int[]> hints) {
        pendingReconnectHints.clear();
        if (hints != null) pendingReconnectHints.addAll(hints);
    }

    /** @return 集群是否开机（GUI ToggleButton 双端同步；新放置默认 true）。 */
    public boolean isMachineEnabled() {
        return machineEnabled;
    }

    /**
     * 开关机 setter：值变化即标脏落盘；重新开机（true）清除断供中止一次性通知锁存
     * （{@link #supplyAbortNotified}，下次断供中止可再次通知）。
     */
    public void setMachineEnabled(boolean v) {
        if (machineEnabled != v) {
            machineEnabled = v;
            markDirty();
            if (v) supplyAbortNotified = false;
        }
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

    /** 记录最近一次成功批实际命中的链步（执行器批次提交点调用，瞬态；空批合法——纯物流批）。 */
    void recordBatchLinks(Set<ChainLink> links) {
        lastBatchLinks = links.isEmpty() ? EnumSet.noneOf(ChainLink.class) : EnumSet.copyOf(links);
    }

    /** @return 最近一次成功批的链步集合（供电类单元 EU 实扣参与闸用，瞬态）。 */
    Set<ChainLink> getLastBatchLinks() {
        return lastBatchLinks;
    }

    /** @return 任一物流单元处理窗口激活（链批进行中/配方时间冷却中），r6-S8 EU 实扣窗口闸。 */
    boolean isChainWindowActive() {
        for (MTEBasicLogisticsUnit unit : topology.getLogisticsUnits()) {
            if (unit != null && unit.isUnitRunning()) return true;
        }
        return false;
    }

    /** 总控与全部单元一致：不生成维护检修仓需求（决策 R6）。 */
    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    /**
     * 控制器底材贴图索引（3.5.2 tier 联动）：按 {@link #mCasingTier} 四档映射四族 casing 贴图
     * （与单元基类 TIER_CASING_TEXTURE_IDS 同一表，{@link MTEClusterUnitBase#tierCasingTextureId}
     * 类加载期常量入口）；未成型/越界回退青铜（MTELargeSteamFurnace.getCasingTextureID 范式）。
     * 方法体仅索引常量表，零 icon 分配（NEI 安全红线）。
     */
    private int getCasingTextureID() {
        return MTEClusterUnitBase.tierCasingTextureId(mCasingTier);
    }

    /**
     * 按面纹理（Enhanced 多方体系直系范式，MTECrustMatterAggregator 口径）——底材随集群 tier
     * 四档联动（青铜/钢/钛/钨钢，{@link #getCasingTextureID()}；未成型回退青铜）+ 正面采矿钻头
     * 叠层（OVERLAY_FRONT_ORE_DRILL）区分启停。叠层为 GT5U 内置常量，无需 registerIcons。
     */
    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()),
                TextureFactory.of(
                    aActive ? Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE
                        : Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL) };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()) };
    }

    /**
     * 全量结构 tooltip（v1.11.15 重排，键迁 gtsr.tooltip.cluster.*，wiki 键序
     * type→desc→数值→ctrl→仓室→计数→hint→品牌）：主控约 ≤25 行——类型/描述 → 蒸汽经济
     * （固定蒸汽 × 档位乘率、润滑、tier 蒸汽倍率、蒸汽种类门控）→ 预热 → 物流挂点 → 结构分段 → 结构块
     * （20×15×29）→ 控制器 → 仓室两行 → 终端 hint（裸键）→ 品牌行。数值全部由
     * {@code String.format} 自 {@link ClusterParams}/{@link ClusterTopology} 常量注入，
     * lang 只放纯文本标签（无 §/数值/单位）。
     */
    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.cluster.type"))
            .addInfo(EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.tooltip.cluster.desc"))
            .addInfo(
                EnumChatFormatting.YELLOW + String.format(
                    StatCollector.translateToLocal("gtsr.tooltip.cluster.steam_econ"),
                    gold(
                        String.format(
                            "%d L/s ×%s",
                            ClusterParams.FIXED_CLUSTER_STEAM_LPS,
                            joinInts(ClusterParams.FIXED_STEAM_TIER_MULT))),
                    gold(joinInts(ClusterParams.CLUSTER_LUBRICANT_LPS) + " L/s"),
                    gold(joinDoubles(ClusterParams.TIER_STEAM_MULT))))
            .addInfo(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.cluster.steam_gate"))
            .addInfo(
                EnumChatFormatting.YELLOW + String.format(
                    StatCollector.translateToLocal("gtsr.tooltip.cluster.preheat"),
                    gold(String.format("%d s", ClusterParams.PREHEAT_SECONDS))))
            .addInfo(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.cluster.preview_tier"))
            .addInfo(
                EnumChatFormatting.YELLOW + String.format(
                    StatCollector.translateToLocal("gtsr.tooltip.cluster.logistics"),
                    gold(String.format("%d", ClusterTopology.SLOT_COUNT / ClusterTopology.MAX_SEGMENTS)),
                    gold(String.format("%d", ClusterTopology.MAX_EXTENSION_SEGMENTS))))
            .addInfo(
                EnumChatFormatting.YELLOW + String.format(
                    StatCollector.translateToLocal("gtsr.tooltip.cluster.segments"),
                    gold(String.format("%d", ClusterParams.SEGMENT_DEPTH_MAIN)),
                    gold(String.format("%d", ClusterParams.SEGMENT_DEPTH_EXT)),
                    gold(String.format("%d", ClusterTopology.MAX_SEGMENTS))))
            .addSeparator()
            .beginStructureBlock(ClusterParams.SEGMENT_DEPTH_MAIN, 15, 29, false)
            .addController(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.cluster.ctrl"))
            .addStructureInfo(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.cluster.hatches"))
            .addStructureInfo(
                EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.cluster.no_bus_energy"))
            .addStructureHint("gtsr.tooltip.cluster.terminal")
            .addInfo(GTSRUtils.getAddedByLine())
            .toolTipFinisher();
        return tt;
    }

    /** int 表拼接（{@code 1/4/16/48}），供固定蒸汽档位乘率与润滑表展示。 */
    private static String joinInts(int[] values) {
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) joined.append('/');
            joined.append(values[i]);
        }
        return joined.toString();
    }

    /** double 表拼接（{@code 1.0/1.5/2.5/4.0}），供 tier 蒸汽倍率展示。 */
    private static String joinDoubles(double[] values) {
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) joined.append('/');
            joined.append(String.format("%.1f", values[i]));
        }
        return joined.toString();
    }

    /** GOLD 数值段（wiki 颜色规范：数值 GOLD）。 */
    private static String gold(String value) {
        return EnumChatFormatting.GOLD + value;
    }

    /** 限水平朝向、不旋转、不垂直翻转（与模板 MTELargeSteamFurnace 同限）。 */
    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        return (d, r, f) -> d.offsetY == 0 && r.isNotRotated() && !f.isVerticallyFliped();
    }

    /**
     * {@inheritDoc} 开关机位兜底直写（缺键保持默认 true）+ 断供中止一次性通知锁存
     * （r-logi-power-bind，NBT 持久跨重载防重复骚扰）+ 运行态（预热进度等）委托
     * {@link ClusterPersistence}。
     */
    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setBoolean("machineEnabled", machineEnabled);
        aNBT.setBoolean("supplyAbortNotified", supplyAbortNotified);
        ClusterPersistence.write(this, aNBT);
    }

    /** {@inheritDoc} 与 {@link #saveNBTData} 对应；缺键时保持新放置默认 true（plan §3.6.1）。 */
    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        machineEnabled = aNBT.hasKey("machineEnabled") ? aNBT.getBoolean("machineEnabled") : true;
        ClusterPersistence.read(this, aNBT);
        // 锁存读回置于 ClusterPersistence.read 之后：其内部 setMachineEnabled(重开机) 清除的是
        // 默认 false（无副作用），随后以 NBT 权威值覆盖，持久语义不受读档路径开关机影响
        supplyAbortNotified = aNBT.hasKey("supplyAbortNotified") && aNBT.getBoolean("supplyAbortNotified");
    }

    /**
     * {@inheritDoc} 拆除/区块卸载清理：配对注销本端实例的集群 'e' 精准候选注册并复位边沿标记
     * （服务端 key 由 checkMachine 复位段清理、客户端 key 由 tier 归 -1 分支与本路径配对清理）。
     */
    @Override
    public void onRemoval() {
        ClusterParticleFx.clearClusterAirCandidates(this);
        fxAirCandidatesExtensionCount = -1;
        super.onRemoval();
    }

    /** 总控零配方，使用真实状态词条，不显示恒定 NO_RECIPE 结果词条与配方信息区。 */
    @Override
    public boolean shouldDisplayCheckRecipeResult() {
        return false;
    }

    @Override
    public boolean showRecipeTextInGUI() {
        return false;
    }

    /**
     * GT 原生 GUI（终验反馈 FB 建类、FA 接线）：空手右击经 GT 基类默认路径打开集群总控原生 GUI
     * （成型/段数/tier/热量/蒸汽/润滑/吞吐/模块计数/供给异常词条，MTECrustMatterAggregator
     * 同款语义）；持枢纽终端右击仍开 MUI2 终端（onRightclick 分支保持不动）。
     */
    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new MTESteamMineralLogisticsClusterNativeGui(this);
    }

    /** 物流模块兼容入口的固定初始页：链路编辑页（零基页序 1；旧 MUI2 工厂 PAGE_CHAIN_EDIT 同值冻结）。 */
    public static final int PAGE_CHAIN_EDIT = 1;

    /**
     * 服务端打开集群终端（terminal-native-ui M6，PLAN §4.1 轨 A：{@link TerminalNet#sendOpen}，
     * 零 windowId）。分支逐字对齐旧 MUI2 工厂 open 分支（git 基线 b4fabb2 :43-58）：
     * te 为总控时按 initialPage 打开；为物流模块时解析所属 cluster 并固定初始页
     * {@link #PAGE_CHAIN_EDIT}（兼容入口保留语义）；未入集群（cluster null）或总控基 TE 失联
     * （getBaseMetaTileEntity null）时静默返回。玩家校验同 S3 先例
     * （EntityPlayerMP/FakePlayer，原服务端 open 守卫同口径）。
     */
    public static void openClusterTerminal(EntityPlayer player, IGregTechTileEntity te, int initialPage) {
        if (!(player instanceof EntityPlayerMP playerMP) || player instanceof FakePlayer) return;
        if (te == null) return;
        IMetaTileEntity mte = te.getMetaTileEntity();
        MTESteamMineralLogisticsCluster cluster;
        if (mte instanceof MTESteamMineralLogisticsCluster controller) {
            cluster = controller;
        } else if (mte instanceof MTEBasicLogisticsUnit unit) {
            cluster = unit.getCluster();
            initialPage = PAGE_CHAIN_EDIT;
        } else {
            return;
        }
        if (cluster == null || cluster.getBaseMetaTileEntity() == null) return;
        IGregTechTileEntity base = cluster.getBaseMetaTileEntity();
        TerminalNet.sendOpen(
            TerminalUiType.CLUSTER_TERMINAL,
            playerMP,
            base.getXCoord(),
            base.getYCoord(),
            base.getZCoord(),
            initialPage);
    }

    /**
     * {@inheritDoc} 持枢纽终端右击 = 打开集群终端界面（terminal-native-ui 轨 A：
     * {@link #openClusterTerminal} 服务端复核后发 open 包，初始页 0；空手/他物右击走父类默认机器
     * GUI）。持物右击方案同聚合器/枢纽（潜行右击会被 GT 基座拦截贴墙放方块，onRightclick 收不到）。
     * 终端入口提示见 tooltip structure hint。
     */
    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        ItemStack held = aPlayer.getHeldItem();
        if (held != null && GTSRItemList.HubTerminal.isStackEqual(held, false, true)) {
            if (aBaseMetaTileEntity.isServerSide()) {
                openClusterTerminal(aPlayer, aBaseMetaTileEntity, 0);
            }
            return true;
        }
        return super.onRightclick(aBaseMetaTileEntity, aPlayer);
    }
}
