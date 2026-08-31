package com.miaokatze.gtsr.common.machine.cluster;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;
import com.miaokatze.gtsr.common.gui.cluster.MTEBasicLogisticsUnitNativeGui;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;

/**
 * 物流模块：集群的单点链执行器骨架（E3b 切片：四 I/O 结构 + 软锤启停 + 双 tank 语义收紧 + 独立 GUI stub）。
 * <p>
 * <b>结构（r9 权威规格 5×4×4，控制器 (2,2,0)）</b>：全矩阵 A 位四 I/O 自由化（切片 3）——'A' 元素
 * = ofChain(tiered 外壳 + hatchAdder.anyOf(输入总线/输出总线/输入仓/输出仓))，矩阵内任意 A 位皆可
 * 承载四类 I/O hatch 之一（原 D/E/F/G 专用字符已按草稿还原为 'A'）；数量校验在 checkMachine 按
 * 真实注册列表计数（四类各 1..2，任一类 0 或 &gt;2 不成型）。B=tiered 管道
 * （{@link #tieredPipeElement()}，r9 由齿轮箱族改绑——权威规格）、C=tiered 框架
 * （{@link #tieredFrameElement()}），与 A 外壳经基类 {@code resolveUnitStructureTier} 分族同级
 * 强校验（跨 tier 混搭不成型）。{@link MTEHatchInputBusME} 在 {@link #addInputBusToMachineList}
 * 直接拒绝致结构不成型（范式同 GT5U MTETreeFarm：ME 输入总线会绕过物流批事务语义）。
 * <p>
 * <b>物理电源</b>：默认开机（SR-Cluster-r5 决策 1），启停经软锤切换（GT5U mWorks 原生 NBT
 * 持久化）；{@link #getUnitStatus()} 优先 {@code isAllowedToWork()}，关闭时显示"无功率/未通电"
 * 而非可工作。"仅处理矿石才工作"（决策 5）：运行态贴图/状态由处理窗口闩驱动——执行器成功批
 * 提交后调 {@link #onBatchProcessed(int)} 开窗 {@link #isUnitRunning()} 才为 true。
 * <p>
 * <b>零流体缓存（SR-Cluster-r6 S2 去缓存收口）</b>：旧自持水/化浴双 tank 与每 20t 自补液路径已
 * 删除，基类 mFluid 主 tank 维持零内部容量收口（读写恒 null、fill/drain 全拒、对外零 tank 暴露）；
 * 洗矿水/化工浴液在批执行时由 {@link ClusterChainExecutor} 经 {@link GTSRHatchFluidAccess}
 * （hasEnoughAcross/depleteFluidAcross 口径）直接对本模块输入仓结算，本类仅保留状态显示用粗检
 * {@link #hasBatchFluids}。管道/ME 对本单元不再有可填充面。
 * <p>
 * <b>配方时间与虚拟空配方（SR-Cluster-r6 S3）</b>：成功批提交后本批"配方时间"（tick，见
 * {@link ChainLink#getBaseTicks()}/ExecutionPlan 时间口径，经执行器写入 {@link #chainCooldownTicks}）
 * 驱动 GT 多方块真实进度——{@link #onBatchProcessed(int)} 置 mMaxProgresstime/mProgresstime 起一炉
 * 虚拟空配方（mEUt=0 无能耗，{@code checkProcessing} 恒 NO_RECIPE 不受影响），基类 runMachine 逐 t
 * 计数、整批时长内 {@link #isUnitRunning()} 保持 true（active 贯穿），批结束归零回 STANDBY；
 * 处理窗口闩保留 max(配方时间, {@value #MIN_PROCESSING_WINDOW_TICKS}t) 下限（r5 工作间隔语义）。
 * <p>
 * <b>交互</b>：右击不再跳转集群终端链编辑页，也不再有独立 MUI2 状态页——空手右击打开
 * GT 原生 GUI（{@link MTEBasicLogisticsUnitNativeGui}，物流富词条，基类 getGui 覆写）；
 * 链编辑入口只经集群 UI，本类保留 {@link #getChain()}/{@link #setChain(LogisticsChain)} 访问器。
 * 正面叠层经基类 E2a 钩子 {@link #unitOverlayInactive()}/{@link #unitOverlayActive()}（拆解机
 * 贴图四态，Textures.BlockIcons T:1306-1309），底材随 unitStructureTier 四档联动（3.5.2）。
 * <p>
 * 链 NBT 自落（{@link #saveNBTData}）：链存 "clusterChain"；旧档 "clusterWaterTank"/
 * "clusterChemTank" 键静默容忍忽略（不读不写，不迁移不崩溃）。
 * 类型名 key：gtsr.gui.cluster.unit_type.logistics。
 */
public class MTEBasicLogisticsUnit extends MTEClusterUnitBase<MTEBasicLogisticsUnit> {

    /** 四 hatch 挂点与 hatch 贴图共用的青铜底材贴图 id（同总控 hatch 挂点）。 */
    private static final int HATCH_CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10);

    /** 四类 I/O hatch 各自数量上限（切片 3：各 1..2，A 位自由化后由 checkMachine 计数校验）。 */
    private static final int IO_HATCH_LIMIT = 2;

    /** 正面叠层：拆解机贴图四态之停机态（Textures.BlockIcons T:1306）。 */
    private static final IIconContainer UNIT_OVERLAY_INACTIVE = Textures.BlockIcons.OVERLAY_FRONT_DISASSEMBLER;

    /** 正面叠层：拆解机贴图四态之运行态（T:1308；辉光态 T:1307/1309 留 E6 视觉迭代）。 */
    private static final IIconContainer UNIT_OVERLAY_ACTIVE = Textures.BlockIcons.OVERLAY_FRONT_DISASSEMBLER_ACTIVE;

    /** 本模块的有序链（永非 null；setChain(null) 亦只置空链）。 */
    private LogisticsChain chain = new LogisticsChain();

    /**
     * 处理窗口闩（SR-Cluster-r5 决策 5，瞬态无 NBT）：最近一次成功批提交后的"工作中"显示窗，
     * 窗口 = 提交时计时器 + max(批冷却, 40t)；{@link #isUnitRunning()} 与 {@link #getUnitStatus()}
     * 据此区分"就绪待机"与"正在加工"。重载后从零开始（仅显示语义，非玩家资产）。
     */
    private long processingDisplayUntilTick;

    /** 处理窗口下限（tick）：批冷却为 0（全透传批）时仍保持 2 秒工作态显示。 */
    private static final long MIN_PROCESSING_WINDOW_TICKS = 40L;

    /** 链脏标记（瞬态）：置位表示链需在下次执行前重校验（重校验由 E4/主控执行）。 */
    private boolean chainDirty;

    public MTEBasicLogisticsUnit(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEBasicLogisticsUnit(String aName) {
        super(aName);
    }

    // ------------------------------------------------------------------
    // 结构：r9 权威规格 5×4×4 四 I/O 矩阵（plan 3.3.3 + SR-Cluster-r9）
    // ------------------------------------------------------------------

    /**
     * 结构矩阵（[Z][Y][X]，z0=正面；r9 权威规格 5×4×4，四 I/O 自由化保持——矩阵内任意 A 位皆可
     * 承载四类 I/O hatch 之一）：
     *
     * <pre>
     * z0 = [ AAA /  AAA /  A~A / AAA ]   任意 A 位可承载四类 I/O hatch（checkMachine 各计 1..2）
     * z1 = [ CAC /  C C / CCACC / ABA ]
     * z2 = [ AAA / CAAAC /  AAA / ABA ]
     * z3 = [     /  CCC /      /     ]
     * </pre>
     *
     * 控制器 '~' 位于 (2,2,0)（offsets 2/2/0）。
     */
    @Override
    protected String[][] getUnitShape() {
        return new String[][] { { " AAA ", " AAA ", " A~A ", " AAA " }, { " CAC ", " C C ", "CCACC", " ABA " },
            { " AAA ", "CAAAC", " AAA ", " ABA " }, { "     ", " CCC ", "     ", "     " }, };
    }

    /**
     * 'A' 元素覆写（切片 3 四 I/O 自由化，范式同 ClusterStructureDef A 总控仓室元素）：tiered 外壳
     * （默认形态，四族 casing 之一，tier 强校验语义经基类元素原样保留）或 anyOf(输入总线/输出总线/
     * 输入仓/输出仓)——矩阵内任意 A 位皆可承载四类 I/O hatch 之一；数量校验在 {@link #checkMachine}
     * 按真实注册列表计数（各 1..2）。禁用 atLeast（GT5U atLeast 是「各元素至少一个」语义，此处不
     * 适用）；casingIndex+hint 齐备（静态青铜 hint 口径保留）；ME 输入总线经
     * {@link #addInputBusToMachineList} 覆写拒绝。
     */
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected IStructureElement tieredCasingElement() {
        return ofChain(
            super.tieredCasingElement(),
            buildHatchAdder(MTEBasicLogisticsUnit.class).anyOf(InputBus, OutputBus, InputHatch, OutputHatch)
                .casingIndex(HATCH_CASING_INDEX)
                .hint(1)
                .build());
    }

    /**
     * 专有结构元素（r9）：B=管道族（{@link #tieredPipeElement()}，原齿轮箱族改绑——权威规格）、
     * C=框架族（{@link #tieredFrameElement()}），与 A 外壳经基类 {@code resolveUnitStructureTier}
     * 分族同级强校验（跨 tier 混搭不成型）。四 I/O hatch 挂点不用专用字符——并入
     * {@link #tieredCasingElement()} 的 'A' 元素链。
     */
    @Override
    @SuppressWarnings("rawtypes")
    protected void addUnitStructureElements(StructureDefinition.Builder builder) {
        builder.addElement('B', tieredPipeElement());
        builder.addElement('C', tieredFrameElement());
    }

    @Override
    protected int getStructureOffsetA() {
        return 2;
    }

    @Override
    protected int getStructureOffsetB() {
        return 2;
    }

    @Override
    protected int getStructureOffsetC() {
        return 0;
    }

    /**
     * 结构校验追加：super（E2a 基类）复位分族 tier 并 checkPiece + 分族同级校验（失配即已写错误）；
     * 成型后按真实注册列表做四 I/O 计数校验（切片 3：A 位自由化后挂点不再天然保证非空/上限——
     * 输入总线/输出总线/输入仓/输出仓各 1..2，任一类 0 或 &gt;2 不成型，错误信息按类可识别）。
     * 成型成功末尾按 unitStructureTier 统一刷新四类 I/O hatch 贴图（切片 2 统一入口）。
     * errors 非空由父类折算为不成型。
     */
    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        super.checkMachine(aBaseMetaTileEntity, aStack, errors);
        if (!errors.isEmpty()) return;
        if (mInputBusses.isEmpty()) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.missing_input_bus"));
            return;
        }
        if (mInputBusses.size() > IO_HATCH_LIMIT) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.logi_input_bus_limit"));
            return;
        }
        if (mOutputBusses.isEmpty()) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.missing_output_bus"));
            return;
        }
        if (mOutputBusses.size() > IO_HATCH_LIMIT) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.logi_output_bus_limit"));
            return;
        }
        if (mInputHatches.isEmpty()) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.missing_fluid_hatch"));
            return;
        }
        if (mInputHatches.size() > IO_HATCH_LIMIT) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.logi_input_hatch_limit"));
            return;
        }
        if (mOutputHatches.isEmpty()) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.missing_output_hatch"));
            return;
        }
        if (mOutputHatches.size() > IO_HATCH_LIMIT) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.logi_output_hatch_limit"));
            return;
        }
        refreshHatchTextures(mInputBusses);
        refreshHatchTextures(mOutputBusses);
        refreshHatchTextures(mInputHatches);
        refreshHatchTextures(mOutputHatches);
    }

    /**
     * ME 输入总线拒绝（范式同 GT5U MTETreeFarm#addInputBusToMachineList）：ME 总线抽料绕过
     * 物流批事务的输入总线语义，直接返回 false 使 'A' 元素 hatchAdder 校验失败、结构不成型。
     */
    @Override
    public boolean addInputBusToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity != null && aTileEntity.getMetaTileEntity() instanceof MTEHatchInputBusME) return false;
        return super.addInputBusToMachineList(aTileEntity, aBaseCasingIndex);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEBasicLogisticsUnit(mName);
    }

    // ------------------------------------------------------------------
    // 四 I/O 契约（E4 执行器独占调用，签名冻结）
    // ------------------------------------------------------------------

    /** 本模块自身输入总线（GT 标准列表 live 视图；结构成型时 1..2 枚，A 位自由化计数校验）。 */
    public List<MTEHatchInputBus> getLogisticsInputBusses() {
        return mInputBusses;
    }

    /** 本模块自身输出总线（GT 标准列表 live 视图；结构成型时 1..2 枚，A 位自由化计数校验）。 */
    public List<MTEHatchOutputBus> getLogisticsOutputBusses() {
        return mOutputBusses;
    }

    /**
     * 本模块自身输入仓/输出仓（live 视图；各 1..2 枚；输入仓为洗矿水/化浴液批流体直结结算面
     * （SR-Cluster-r6 S2），输出仓为结构要求）。
     */
    public List<MTEHatchInput> getLogisticsInputHatches() {
        return mInputHatches;
    }

    public List<MTEHatchOutput> getLogisticsOutputHatches() {
        return mOutputHatches;
    }

    // ------------------------------------------------------------------
    // 物理电源（软锤启停，默认开机）
    // ------------------------------------------------------------------

    /** 物理电源开关（GUI/同步用）：BaseMetaTileEntity.isAllowedToWork()（mWorks）。 */
    public boolean isPowerAllowed() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return base != null && base.isAllowedToWork();
    }

    /** 本单元自身结构 tier（GUI 用公开访问器；未成型/未判定为 -1）。 */
    public int getLogisticsStructureTier() {
        return getUnitStructureTier();
    }

    /** 已连接集群（GUI 用）。 */
    public boolean isClusterConnected() {
        return cluster != null;
    }

    /**
     * 成功批提交回调（SR-Cluster-r5 决策 5，ClusterChainExecutor 步骤 10 调用；调用前执行器须已
     * {@link #setChainCooldownTicks(long)} 写入本批配方时间）：开处理窗口并起一炉虚拟空配方——
     * <ul>
     * <li>mMaxProgresstime = 本批配方时间（tick）、mProgresstime = 0：GT 基类 runMachine 以 mEUt=0
     * 逐 t 推进真实多方块进度条，完成时自行归零（虚拟空配方，不产物品/流体）；</li>
     * <li>窗口 = 当前计时器 + max(配方时间, {@link #MIN_PROCESSING_WINDOW_TICKS})：窗口内
     * {@link #isUnitRunning()} 为 true（正面运行叠层与 active 贯穿整批时长）、
     * {@link #getUnitStatus()} 显示 WORKING，批结束回 STANDBY。</li>
     * </ul>
     * 瞬态无 NBT：重载后窗口与进度归零（仅显示/节拍语义）。batch ≤ 0 忽略。
     */
    public void onBatchProcessed(int batch) {
        if (batch <= 0) return;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        long now = base == null ? 0L : base.getTimer();
        long recipeTicks = Math.max(0L, chainCooldownTicks);
        mMaxProgresstime = (int) Math.min(Integer.MAX_VALUE, recipeTicks);
        mProgresstime = 0;
        processingDisplayUntilTick = now + Math.max(recipeTicks, MIN_PROCESSING_WINDOW_TICKS);
    }

    /** 处理窗口判据：当前计时器仍在最近一次成功批的显示窗口内。 */
    private boolean isInProcessingWindow() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return base != null && base.getTimer() < processingDisplayUntilTick;
    }

    /**
     * 独立运行信号（决策 5 覆写）：基类口径（成型+连接+物理电源开）之上追加处理窗口判据——
     * 仅在成功批后的窗口内为 true（"仅处理矿石才工作"），驱动正面运行叠层与 active 位。
     */
    @Override
    public boolean isUnitRunning() {
        return super.isUnitRunning() && isInProcessingWindow();
    }

    /** 软锤复位回调（E4 调用）：标记链重校验（重校验本身由 E4/主控在下次执行前做）。 */
    public void onSoftHammerReset() {
        markChainDirty();
    }

    /** 标记链需重校验（链内容变更/软锤复位时置位；重校验在下次执行前由 E4/主控消费）。 */
    public void markChainDirty() {
        this.chainDirty = true;
    }

    /** 链脏标记读取（E4/GUI 用；瞬态，不持久化）。 */
    public boolean isChainDirty() {
        return chainDirty;
    }

    /** 链可执行（E4 契约）：链非空 && 已连接集群 && 物理电源开 && LogisticsChain.isExecutable(topology)。 */
    public boolean isChainExecutableNow() {
        if (chain.isEmpty() || cluster == null) return false;
        if (!isPowerAllowed()) return false;
        return chain.isExecutable(cluster.getTopology());
    }

    // ------------------------------------------------------------------
    // 状态机（电源门控优先）
    // ------------------------------------------------------------------

    /**
     * 状态细化（SR-Cluster-r5 决策 4 重映射，自上而下）：
     * <ol>
     * <li>未自成型 → NO_POWER_OR_INVALID；</li>
     * <li>总控停机 → SHUT_DOWN；</li>
     * <li>处理进度进行中（{@link #isWorkInProgress()}）→ WORKING；</li>
     * <li>物理电源关闭（!isAllowedToWork()，软锤/红石关闭）→ NO_POWER_OR_INVALID
     * （"关机/未通电"，不得显示可工作或待机）；</li>
     * <li>未入集群 / 链空 → STANDBY；</li>
     * <li>链不可执行（!{@link LogisticsChain#isExecutable}，如缺工作单元）→ STANDBY
     * （配置问题不再显示为红字离线）；</li>
     * <li>洗矿/化洗批流体不足（!{@link #hasBatchFluids}）→ FLUID_MISSING；</li>
     * <li>处理窗口内（最近成功批后 {@link #isInProcessingWindow()}）→ WORKING；</li>
     * <li>其余（就绪待批或批冷却中）→ STANDBY。</li>
     * </ol>
     */
    @Override
    public ClusterUnitStatus getUnitStatus() {
        if (!isUnitStructureFormed()) return ClusterUnitStatus.NO_POWER_OR_INVALID;
        MTESteamMineralLogisticsCluster cluster = getCluster();
        if (cluster != null && !cluster.isMachineEnabled()) return ClusterUnitStatus.SHUT_DOWN;
        if (isWorkInProgress()) return ClusterUnitStatus.WORKING;
        if (!isPowerAllowed()) return ClusterUnitStatus.NO_POWER_OR_INVALID;
        if (cluster == null) return ClusterUnitStatus.STANDBY;
        if (chain.isEmpty()) return ClusterUnitStatus.STANDBY;
        if (!chain.isExecutable(cluster.getTopology())) return ClusterUnitStatus.STANDBY;
        boolean needWater = chain.countOf(ChainLink.ORE_WASH) > 0;
        boolean needChemBath = chain.countOf(ChainLink.CHEM_BATH) > 0;
        if ((needWater || needChemBath) && !hasBatchFluids(needWater, needChemBath)) {
            return ClusterUnitStatus.FLUID_MISSING;
        }
        if (isInProcessingWindow()) return ClusterUnitStatus.WORKING;
        return ClusterUnitStatus.STANDBY;
    }

    // ------------------------------------------------------------------
    // 批流体直结（SR-Cluster-r6 S2：零自持缓存）
    // ------------------------------------------------------------------

    /**
     * 批流体判定（状态显示用粗检，{@link #getUnitStatus()} FLUID_MISSING 判据）：链含洗矿
     * （needWater）/化洗（needChemBath）时，对应流体须能在本模块输入仓列表合计探得每批用量
     * （{@link ClusterParams#WASH_WATER_PER_BATCH_L} / {@link ClusterParams#CHEM_BATH_FLUID_PER_BATCH_L}，
     * 均 1000L）；两者都不需要时恒 true。探测经 {@link GTSRHatchFluidAccess#hasEnoughAcross} 统一
     * 访问层（普通仓/ME 仓同口径，零自持缓存）：水系蒸馏水或普通水任一足额即可，化浴液含汞或
     * 过硫酸钠任一足额即可；精确的逐物品需求预检与实扣在 {@link ClusterChainExecutor} 批事务内对
     * 同一仓列表完成。
     */
    public boolean hasBatchFluids(boolean needWater, boolean needChemBath) {
        if (!isModuleEnabled()) return false;
        List<MTEHatchInput> hatches = getLogisticsInputHatches();
        if (needWater && !hasWaterAcross(hatches, ClusterParams.WASH_WATER_PER_BATCH_L)) return false;
        return !needChemBath || hasChemBathAcross(hatches, ClusterParams.CHEM_BATH_FLUID_PER_BATCH_L);
    }

    /**
     * 水系跨仓足额判定：蒸馏水优先探测，不足/不可用再探普通水，任一足额即 true
     * （蒸馏水口径同 {@code GTModHandler.getDistilledWater}，与执行器 isDistilledFluid 一致）。
     */
    private static boolean hasWaterAcross(List<MTEHatchInput> hatches, int amountMb) {
        FluidStack distilled = GTModHandler.getDistilledWater(amountMb);
        if (distilled != null && distilled.getFluid() != null
            && GTSRHatchFluidAccess.hasEnoughAcross(hatches, distilled)) return true;
        return FluidRegistry.WATER != null
            && GTSRHatchFluidAccess.hasEnoughAcross(hatches, new FluidStack(FluidRegistry.WATER, amountMb));
    }

    /** 化浴液跨仓足额判定：含汞或过硫酸钠任一足额即 true（GT5U 化学洗配方介质两口径）。 */
    private static boolean hasChemBathAcross(List<MTEHatchInput> hatches, int amountMb) {
        if (Materials.Mercury.mFluid != null
            && GTSRHatchFluidAccess.hasEnoughAcross(hatches, new FluidStack(Materials.Mercury.mFluid, amountMb)))
            return true;
        return Materials.SodiumPersulfate.mFluid != null && GTSRHatchFluidAccess
            .hasEnoughAcross(hatches, new FluidStack(Materials.SodiumPersulfate.mFluid, amountMb));
    }

    // ------------------------------------------------------------------
    // 链持有与交互（编辑入口只经集群 UI）
    // ------------------------------------------------------------------

    public LogisticsChain getChain() {
        return chain;
    }

    /** 整链替换（集群链编辑页/预设载入用）；null 安全——置为空链，并标记链重校验。 */
    public void setChain(LogisticsChain aChain) {
        this.chain = (aChain != null) ? aChain : new LogisticsChain();
        markChainDirty();
    }

    /** 朝向透传（等价 getBaseMetaTileEntity().getFrontFacing()）。 */
    public ForgeDirection getFrontFacing() {
        return getBaseMetaTileEntity().getFrontFacing();
    }

    @Override
    public String getUnitTypeNameKey() {
        return "gtsr.gui.cluster.unit_type.logistics";
    }

    /**
     * GT 原生 GUI（终验反馈：物流不使用独立 MUI2 UI）：覆写基类共享 GUI 为物流富词条子类
     * （段/垫、链摘要、批配方时间、物理电源）。空手右击经 GT 基类默认路径打开本 GUI
     * （MTECrustMatterAggregator 同款语义），MUI2 状态页跳转已删除。
     * <p>
     * 配方进度读秒行（v1.11.9 遗留覆写已删除）：showRecipeTextInGUI 恢复 GT5U 默认 true——
     * GUI 显示 GT5U 原生配方进度读秒行（mMaxProgresstime&gt;0 时 current/max 秒+百分比，
     * 空闲自动隐藏）；"没有找到合成表"仍由基类
     * {@code MTEClusterUnitBase.shouldDisplayCheckRecipeResult=false} 抑制。
     */
    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new MTEBasicLogisticsUnitNativeGui(this);
    }

    // ------------------------------------------------------------------
    // 贴图（正面拆解机叠层，经基类 E2a 钩子；底材随 tier 四档联动由基类 getTexture 承载）
    // ------------------------------------------------------------------

    /** 前脸停机叠层（基类钩子覆写：拆解机停机贴图）。 */
    @Override
    protected IIconContainer unitOverlayInactive() {
        return UNIT_OVERLAY_INACTIVE;
    }

    /** 前脸运行叠层（基类钩子覆写：拆解机运行贴图；active 由基类 isUnitRunning 驱动，随物理电源联动）。 */
    @Override
    protected IIconContainer unitOverlayActive() {
        return UNIT_OVERLAY_ACTIVE;
    }

    // ------------------------------------------------------------------
    // NBT 持久化
    // ------------------------------------------------------------------

    /**
     * 链持久化：链存 "clusterChain" int 数组（ChainLink.ordinal，空链空数组）。批流体无自持缓存
     * 不落 NBT（SR-Cluster-r6 S2）；旧档 "clusterWaterTank"/"clusterChemTank" 键在
     * {@link #loadNBTData} 静默容忍忽略。处理窗口闩与虚拟空配方进度均为瞬态不落 NBT。
     */
    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        List<ChainLink> links = chain.getLinks();
        int[] ordinals = new int[links.size()];
        for (int i = 0; i < links.size(); i++) {
            ordinals[i] = links.get(i)
                .ordinal();
        }
        aNBT.setIntArray("clusterChain", ordinals);
    }

    /**
     * 回读对称：按 ordinal 反解链整链重建（越界 ordinal 静默丢弃）。旧档 tank 键
     * （"clusterWaterTank"/"clusterChemTank"）不再读取——缺失/残留均静默忽略，不迁移不崩溃；
     * 虚拟空配方进度归零（批进度瞬态，重载后从零开始，杜绝不可见幽灵炉）。
     */
    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        LogisticsChain rebuilt = new LogisticsChain();
        List<ChainLink> parsed = new ArrayList<>();
        ChainLink[] values = ChainLink.values();
        for (int ordinal : aNBT.getIntArray("clusterChain")) {
            if (ordinal >= 0 && ordinal < values.length) parsed.add(values[ordinal]);
        }
        rebuilt.setLinks(parsed);
        chain = rebuilt;
        mProgresstime = 0;
        mMaxProgresstime = 0;
    }

    // ------------------------------------------------------------------
    // 批配方时间（SR-Cluster-r6 S3：ClusterChainExecutor 写入，主控每 20t 节拍递减）
    // ------------------------------------------------------------------

    /**
     * 本批配方时间剩余 tick：每批执行后由 ClusterChainExecutor 置为本批"配方时间"（tick，
     * ExecutionPlan.itemTimeSec × 20 四舍五入且至少 1 tick），总控每 20t 统一 -20、仍 &gt;0 的单元本秒跳过；
     * 该值同时是 {@link #onBatchProcessed(int)} 虚拟空配方的总时长与处理窗口基准。不持久化——
     * 重载/重摆后从零开始（节拍器语义，非玩家资产）。
     */
    private long chainCooldownTicks;

    /** @return 本批配方时间剩余 tick（0 = 可立即执行下一批）。 */
    public long getChainCooldownTicks() {
        return chainCooldownTicks;
    }

    /** 设置本批配方时间（ClusterChainExecutor 批执行后写入；不持久化，重载后从零开始）。 */
    public void setChainCooldownTicks(long ticks) {
        this.chainCooldownTicks = ticks;
    }

    // ------------------------------------------------------------------
    // Tooltip（v1.11.15）
    // ------------------------------------------------------------------

    /** 工序主色（计划 §2.2）：WHITE+GRAY 系——描述行 WHITE，辅助语义经 GRAY 行承载。 */
    @Override
    protected EnumChatFormatting getUnitDescColor() {
        return EnumChatFormatting.WHITE;
    }

    /** 单元描述键（v1.11.15 W1 修正）：物流模块专属描述行。 */
    @Override
    protected String getUnitDescKey() {
        return "gtsr.tooltip.cluster.unit.logistics.desc";
    }

    /**
     * 功能群（v1.11.15）：处理窗口下限行 + 软锤启停行——窗口下限取自
     * {@link #MIN_PROCESSING_WINDOW_TICKS}（tick ÷ {@link ChainLink#TICKS_PER_SECOND} 折秒）；
     * 软锤启停为纯文案行（默认开机，{@code isAllowedToWork} 语义）。
     */
    @Override
    protected void addUnitTooltipInfo(MultiblockTooltipBuilder tt) {
        tt.addInfo(
            EnumChatFormatting.YELLOW + String.format(
                StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.logistics.window"),
                gold(fmtSeconds(MIN_PROCESSING_WINDOW_TICKS / (double) ChainLink.TICKS_PER_SECOND))))
            .addInfo(
                EnumChatFormatting.YELLOW + String.format(
                    StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.logistics.segment_time"),
                    gold(tierValues(ClusterParams.LOGISTICS_TIME_SEC, " s"))))
            .addInfo(
                EnumChatFormatting.YELLOW + String.format(
                    StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.logistics.lubricant"),
                    gold(tierValues(ClusterParams.LOGISTICS_UNIT_LUBRICANT_LPS, " L/s"))))
            .addInfo(
                EnumChatFormatting.YELLOW
                    + StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.logistics.soft_hammer"));
    }

    private static String tierValues(int[] values, String suffix) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < ClusterParams.TIER_COUNT; i++) {
            if (i > 0) result.append('/');
            result.append(values[i])
                .append(suffix);
        }
        return result.toString();
    }

    /**
     * 仓室群（v1.11.15）：四类 I/O 行（输入总线/输出总线/输入仓/输出仓）——数量区间下限 1 来自
     * {@link #checkMachine} 的非空强制，上限引用 {@link #IO_HATCH_LIMIT}（Java 侧 GOLD 注入）。
     */
    @Override
    protected void addUnitStructureTooltipInfo(MultiblockTooltipBuilder tt) {
        String range = gold(String.format("1-%d", IO_HATCH_LIMIT));
        tt.addStructureInfo(
            EnumChatFormatting.YELLOW
                + String.format(StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.hatch.input_bus"), range))
            .addStructureInfo(
                EnumChatFormatting.YELLOW + String
                    .format(StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.hatch.output_bus"), range))
            .addStructureInfo(
                EnumChatFormatting.YELLOW + String
                    .format(StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.hatch.input_hatch"), range))
            .addStructureInfo(
                EnumChatFormatting.YELLOW + String
                    .format(StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.hatch.output_hatch"), range));
    }
}
