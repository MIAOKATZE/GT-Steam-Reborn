// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.cluster;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
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
import com.miaokatze.gtsr.common.event.GTSRMachineEvent;
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
 * = ofChain(hatchAdder.atLeast(输入总线/输出总线/输入仓/输出仓), tiered 外壳)，矩阵内任意 A 位皆可
 * 承载四类 I/O hatch 之一（原 D/E/F/G 专用字符已按草稿还原为 'A'）；数量校验在 checkMachine 按
 * 真实注册列表计数（四类各 1..2，任一类 0 或 &gt;2 不成型）。B=tiered 管道
 * （{@link #tieredPipeElement()}，r9 由齿轮箱族改绑——权威规格）、C=tiered 框架
 * （{@link #tieredFrameElement()}），与 A 外壳经基类 {@code resolveUnitStructureTier} 分族同级
 * 强校验（跨 tier 混搭不成型）。<b>ME 系仓室兼容（T14）</b>：GT5U 5.09.54.20 实证
 * {@code MTEHatchInputBusME extends MTEHatchInputBus}（Stocking Input Bus (ME)）、
 * {@code MTEHatchOutputBusME extends MTEHatchOutputBus}（Output Bus (ME)）、
 * {@code MTEHatchInputME extends MTEHatchInput}（Stocking Input Hatch (ME)，流体）、
 * {@code MTEHatchOutputME extends MTEHatchOutput}（Output Hatch (ME)，流体）——四类 ME 仓室经
 * HatchElement 继承匹配自然通过 'A' 原生仓添加器并入各自标准列表，计入 {@link #checkMachine}
 * 各类 1..2 计数（旧 {@code addInputBusToMachineList} 对 {@code MTEHatchInputBusME} 的
 * instanceof 拒绝已删除）。流体批结算经 {@link GTSRHatchFluidAccess} 普通仓/ME 仓同口径；
 * ME 输出总线产物写入由 {@link ClusterChainExecutor} 趟 2 兜底 +
 * {@code GTSROutputBusCompat.storePartial} 模拟/实放两段式承载（既有）。<b>ME 输入总线读取
 * 契约（GT5U 源码实证）</b>：{@code getStackInSlot} 仅在 {@code startRecipeProcessing()} 窗口内
 * 返回 {@code slot.extracted}（AE 网络 SIMULATE 快照 live 引用），窗口外恒 null；
 * {@code endRecipeProcessing} 按快照差额 {@code poweredExtraction} 实扣、失败即停机——
 * {@link ClusterChainExecutor} 批取料（collectOreBatch/applyTakes）需在批事务外围补该握手，
 * 未握手时 ME 输入总线读取恒空（fail-safe：不复制不丢料，批次按缺料拒绝）。
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
 * <b>配方时间与配方运行绑定（SR-Cluster-r6 S3 + r-logi-power-bind）</b>：成功批提交后本批"配方时间"
 * （tick，见 {@link ChainLink#getBaseTicks()}/ExecutionPlan 时间口径，经执行器写入
 * {@link #chainCooldownTicks}）驱动 GT 多方块真实进度——{@link #onBatchProcessed(int)} 置
 * mMaxProgresstime/mProgresstime 起一炉真实配方运行（mEUt=0 无能耗，{@code checkProcessing} 恒
 * NO_RECIPE 不受影响）：输入已在开批时整批扣料吞入、产出整批暂存于 {@link #pendingOutputs}，
 * 基类 runMachine 逐 t 计数、整批时长内 {@link #isUnitRunning()} 保持 true（active 贯穿），
 * 进度读完后由 {@link #onPostTick} 经 {@link ClusterChainExecutor#emitPendingOutputs} 排空暂存产出
 * （输出总线满则逐 t 空转重试，零消耗零丢料），随后归零回 STANDBY；处理窗口 = 本批配方时间全长。
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
@IMetaTileEntity.SkipGenerateDescription
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

    /** 链脏标记（瞬态）：置位表示链需在下次执行前重校验（重校验由 E4/主控执行）。 */
    private boolean chainDirty;

    /**
     * 最近一次成功批的生效主产物峰步（S1-T9，链步下标、链序；-1 = 本批无命中配方步或尚无批）。
     * 瞬态不落 NBT，供终端详情行（KEY_F_DETAIL 的 PEAK 行）与执行器峰解析消费。
     */
    private int lastEffectivePeak = -1;

    /**
     * 最近一次成功批实际记账（charged）的批流体摘要（S1-T6，{@code fluidName:liters} 列表，仅
     * 洗矿水/化浴实际 charged 项）。瞬态不落 NBT，供终端详情行（KEY_F_DETAIL 的 FLUID 行）。
     */
    private List<String> lastBatchFluidSummary = java.util.Collections.emptyList();

    /**
     * 队列模式（S1 新增，NBT 持久键 {@code clusterQueueMode}，默认关）：开启后执行器开批闸要求
     * 收集输入中存在单种 item+meta 数量 ≥ 本批有效并行才开批（整批按同种矿队列推进），否则零副作用
     * 返 0（静默，状态机保持 STANDBY 口径）。螺丝刀右击切换。
     */
    private boolean queueMode;

    /** 最近一次已提交批的实际并行宽度与总份数，供终端展示。 */
    private int lastBatchParallel;
    private int lastBatchSize;

    public int getLastBatchParallel() {
        return lastBatchParallel;
    }

    public int getLastBatchSize() {
        return lastBatchSize;
    }

    public double getLastBatchDurationMultiplier() {
        return lastBatchParallel > 0 ? (double) lastBatchSize / lastBatchParallel : 0D;
    }

    public int getLastBatchChargedRounds() {
        return lastBatchParallel > 0 && lastBatchSize > 0 ? 1 + (lastBatchSize - 1) / lastBatchParallel : 0;
    }

    void recordBatchSize(int parallel, int size) {
        lastBatchParallel = parallel;
        lastBatchSize = size;
    }

    @Override
    public boolean supportsBatchMode() {
        return true;
    }

    /** 新物流模块默认保持单轮处理，由玩家开启 GT 原生批处理。 */
    @Override
    public boolean getDefaultBatchMode() {
        return false;
    }

    @Override
    public void setBatchMode(boolean enabled) {
        super.setBatchMode(enabled);
        markDirty();
    }

    /** 执行器复用 GT 父类的最大合批倍率。 */
    @Override
    public int getMaxBatchSize() {
        return super.getMaxBatchSize();
    }

    /** @return 最近一次成功批的生效主产物峰步（链步下标；-1 = 无命中/尚无批）。 */
    public int getLastEffectivePeak() {
        return lastEffectivePeak;
    }

    /** 生效峰步写入（ClusterChainExecutor.runChain 峰解析点调用；包内专用，瞬态）。 */
    void setLastEffectivePeak(int index) {
        this.lastEffectivePeak = index;
    }

    /** @return 最近一次成功批实际记账的批流体摘要（{@code fluidName:liters} 列表，只读视图）。 */
    public List<String> getLastBatchFluidSummary() {
        return lastBatchFluidSummary;
    }

    /** 批流体摘要写入（ClusterChainExecutor 批提交点调用；包内专用，瞬态）。 */
    void setLastBatchFluidSummary(List<String> summary) {
        this.lastBatchFluidSummary = summary == null ? java.util.Collections.<String>emptyList() : summary;
    }

    /** @return 队列模式是否开启（螺丝刀切换，NBT 持久）。 */
    public boolean isQueueMode() {
        return queueMode;
    }

    /**
     * 暂存产出（NBT 键 clusterPendingOutputs，进度沿用 GT 父类保存）：
     * 开批时执行器经输出预检证明整批放得下后扣料吞入，整批产出并不当场发放，而是暂存于此——
     * 待虚拟配方进度读零（{@link #onPostTick} 检测 {@code mMaxProgresstime <= 0}）后经
     * {@link ClusterChainExecutor#emitPendingOutputs} 排空，输出总线满则逐 t 空转重试
     * （零消耗零丢料）。重载只恢复已付批次的产出与剩余进度，不重新扣料、扣液或记吞吐。
     */
    private List<ItemStack> pendingOutputs;

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
     * 'A' 元素覆写（切片 3 四 I/O 自由化 + T14 ME 仓兼容，范式同 ClusterStructureDef A 总控仓室
     * 元素）：四类 I/O hatch 优先，tiered 外壳回退（tier 强校验语义经基类元素原样保留），
     * 并显式列出四类预览候选；矩阵内任意 A 位皆可承载四类 I/O hatch 之一；
     * GT5U ME 系四类仓室（MTEHatchInputBusME/MTEHatchOutputBusME/MTEHatchInputME/MTEHatchOutputME，
     * 均为对应基类子类）经 HatchElement 继承匹配同路入列，不再单独拒绝；数量校验在
     * {@link #checkMachine} 按真实注册列表计数（各 1..2）。原生 atLeast 使自动建造每类默认放一个后
     * 回退外壳，不限制世界校验的多仓准入；casingIndex+hint 保留静态青铜口径。
     */
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected IStructureElement tieredCasingElement() {
        return ofChain(
            ClusterInputHatchPolicy
                .rejectUnsupportedDualInput(
                    buildHatchAdder(MTEBasicLogisticsUnit.class).atLeast(InputBus, OutputBus, InputHatch, OutputHatch)
                        .descriptions(
                            () -> Arrays.asList(
                                "gtsr.structure.cluster.logistics.input_bus",
                                "gtsr.structure.cluster.logistics.output_bus",
                                "gtsr.structure.cluster.logistics.input_hatch",
                                "gtsr.structure.cluster.logistics.output_hatch"))
                        .casingIndex(HATCH_CASING_INDEX)
                        .hint(1))
                .build(),
            super.tieredCasingElement());
    }

    @Override
    protected int requiredCasingPorts() {
        return 4;
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

    /** T14：为批取料开启所有 ME 输入总线的窗口。调用方必须成对结束。 */
    public void beginMEBusProcessing() {
        for (MTEHatchInputBus bus : mInputBusses) {
            if (bus instanceof MTEHatchInputBusME) ((MTEHatchInputBusME) bus).startRecipeProcessing();
        }
    }

    /** Close every ME window and require each native extraction receipt before publishing a batch. */
    public boolean endMEBusProcessing(MTESteamMineralLogisticsCluster controller) {
        boolean paid = true;
        for (MTEHatchInputBus bus : mInputBusses) {
            if (bus instanceof MTEHatchInputBusME me) {
                var result = me.endRecipeProcessing(controller);
                // Do not short circuit: later buses must also close their recipe windows.
                if (result == null || !result.wasSuccessful()) paid = false;
            }
        }
        return paid;
    }

    /**
     * T2③：节流到期才开启 ME 窗口（空批握手门）：当前刻仍处 {@link #meProbeNotBeforeTick} 节流期内时
     * 不开窗直接返回 false（省去每 ME 总线每次握手最多 16 槽 AE SIMULATE 全量查询）；否则等价
     * {@link #beginMEBusProcessing()} 并返回 true。调用方以返回值承载"本次确实开过窗"，
     * 与全部关闭点严格成对（未开窗的路径一律不得调 endMEBusProcessing）。
     */
    public boolean beginMEBusProcessingIfDue() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        long now = base == null ? 0L : base.getTimer();
        if (now < meProbeNotBeforeTick) return false;
        beginMEBusProcessing();
        return true;
    }

    /**
     * T2③：空窗退避武装——仅当存在 ME 输入总线时把 {@link #meProbeNotBeforeTick} 置为当前刻 +
     * {@link ClusterParams#ME_PROBE_INTERVAL_TICKS}（无 ME 总线的集群字段恒 0、零行为变化）；
     * 由 ClusterChainExecutor 在「开过窗且空批」的 finally 关窗后调用（有消耗的窗口不武装，
     * ME 来料恢复延迟上界即本间隔；普通总线来料不经开窗节流、即时不受影响）。
     */
    public void armMeProbeThrottle() {
        if (!hasMeInputBus()) return;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        long now = base == null ? 0L : base.getTimer();
        meProbeNotBeforeTick = now + ClusterParams.ME_PROBE_INTERVAL_TICKS;
    }

    /** T2③：是否存在 ME 输入总线（沿 begin/endMEBusProcessing 的 instanceof 遍历同口径）。 */
    private boolean hasMeInputBus() {
        for (MTEHatchInputBus bus : mInputBusses) {
            if (bus instanceof MTEHatchInputBusME) return true;
        }
        return false;
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
     * {@link #setChainCooldownTicks(long)} 写入本批配方时间、完成扣料与输出暂存）：开处理窗口并起
     * 一炉真实配方运行——
     * <ul>
     * <li>mMaxProgresstime = 本批配方时间（tick）、mProgresstime = 0：GT 基类 runMachine 以 mEUt=0
     * 逐 t 推进真实进度条（输入开批已扣、产出已暂存 {@link #pendingOutputs}），完成时自行归零，
     * 随后 {@link #onPostTick} 排空暂存产出；</li>
     * <li>窗口 = 本批真实配方尚未完成的进度区间：窗口内
     * {@link #isUnitRunning()} 为 true（正面运行叠层与 active 贯穿整批时长）、
     * {@link #getUnitStatus()} 显示 WORKING，批结束回 STANDBY。</li>
     * </ul>
     * 配方进度由 GT 父类持久化，显示与冷却从真实进度推导。batch ≤ 0 忽略。
     */
    public void onBatchProcessed(int batch) {
        if (batch <= 0) return;
        long recipeTicks = Math.max(0L, chainCooldownTicks);
        mMaxProgresstime = (int) Math.min(Integer.MAX_VALUE, recipeTicks);
        mProgresstime = 0;
    }

    /** 处理窗口跟随真实配方进度，含读档启动检查期间暂停的进度。 */
    private boolean isInProcessingWindow() {
        return mMaxProgresstime > 0 && mProgresstime < mMaxProgresstime;
    }

    /**
     * 独立运行信号（决策 5 覆写）：基类口径（成型+连接+物理电源开）之上追加处理窗口判据——
     * 仅在成功批后的窗口内为 true（"仅处理矿石才工作"），驱动正面运行叠层与 active 位。
     */
    @Override
    public boolean isUnitRunning() {
        return super.isUnitRunning() && isInProcessingWindow();
    }

    // ------------------------------------------------------------------
    // 配方运行绑定（r-logi-power-bind）：产出暂存与排空、在飞批次中止
    // ------------------------------------------------------------------

    /** @return 是否持有暂存产出（在飞配方未读完或暂存未排空；执行器据此拒绝开新批防覆盖丢产出）。 */
    public boolean hasPendingOutputs() {
        return pendingOutputs != null;
    }

    /**
     * 暂存整批产出（ClusterChainExecutor 批事务提交点调用；直接引用赋值——列表由执行器新建，
     * 所有权移交本单元）。进度读零后由 {@link #onPostTick} 排空。
     */
    public void stashPendingOutputs(List<ItemStack> outputs) {
        this.pendingOutputs = outputs;
    }

    /**
     * 终止在飞配方运行（主控断供中止 supplyAbort 调用）：进度、批冷却与排空退避归零、暂存产出
     * 丢弃——输入在开批时已整批扣料吞入，此处不回填输入总线（吞料语义）；处理窗口闩同步清零
     * （工作态显示立即回 STANDBY）。对空闲单元调用为无害归零。
     */
    public void abortPendingRun() {
        mProgresstime = 0;
        mMaxProgresstime = 0;
        chainCooldownTicks = 0;
        pendingOutputs = null;
        drainRetryDelayTicks = 0;
        markDirty();
    }

    /**
     * 配方运行绑定排空与冷却同步：先 super（基类推进真实配方），持有暂存产出时每刻从
     * {@code mMaxProgresstime - mProgresstime} 更新冷却，使读档启动检查暂停期间不会提前归零。
     * 空闲时只递减失败退避。持有暂存产出且进度已读零（{@code mMaxProgresstime <= 0}）时
     * 经 {@link ClusterChainExecutor#emitPendingOutputs} 排空——探测-实放-失败回滚整组原子；
     * 输出总线满则保留暂存，按排空失败退避（T2④，
     * {@link ClusterParams#DRAIN_RETRY_BACKOFF_TICKS} tick）重试（零消耗零丢料）。
     */
    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide()) {
            // 有已付产出时跟随真实进度；空闲失败退避保持逐刻递减。
            if (pendingOutputs != null) {
                chainCooldownTicks = Math.max(0, mMaxProgresstime - mProgresstime);
            } else if (chainCooldownTicks > 0) chainCooldownTicks--;
            if (pendingOutputs != null && mMaxProgresstime <= 0) {
                // 排空失败退避（T2④）：退避期内递减跳过；成功清暂存语义不变、失败保留暂存零丢料
                if (drainRetryDelayTicks > 0) {
                    drainRetryDelayTicks--;
                } else if (ClusterChainExecutor.emitPendingOutputs(this, pendingOutputs)) {
                    pendingOutputs = null;
                    markDirty();
                } else {
                    drainRetryDelayTicks = ClusterParams.DRAIN_RETRY_BACKOFF_TICKS;
                }
            }
        }
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

    /**
     * 螺丝刀切换队列模式（S1 新增；范式 {@code MTELargeSolarOverpressureArray.onScrewdriverRightClick}）：
     * 仅服务端执行，切换即取反并标脏持久化（"clusterQueueMode"）；向玩家发
     * {@code gtsr.cluster.logistics.queue.on} / {@code gtsr.cluster.logistics.queue.off}
     * （ChatComponentTranslation 按客户端语言渲染，S3 落值）。
     */
    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        if (aPlayer.worldObj.isRemote) return;
        queueMode = !queueMode;
        getBaseMetaTileEntity().markDirty();
        GTSRMachineEvent
            .sendToPlayer(aPlayer, queueMode ? "gtsr.cluster.logistics.queue.on" : "gtsr.cluster.logistics.queue.off");
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
     * 链持久化：链存 "clusterChain" int 数组（ChainLink.ordinal，空链空数组）+ 峰步下标
     * "clusterChainPeak"（S1-T9，缺省 0）+ 队列模式 "clusterQueueMode"（S1，缺省 false）。
     * 批流体无自持缓存不落 NBT（SR-Cluster-r6 S2）；旧档 "clusterWaterTank"/"clusterChemTank" 键在
     * {@link #loadNBTData} 静默容忍忽略。暂存产出独立持久化，真实进度由 GT 父类保存。
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
        aNBT.setInteger("clusterChainPeak", chain.getPeakIndex());
        aNBT.setBoolean("clusterQueueMode", queueMode);
        aNBT.setInteger("clusterBatchParallel", lastBatchParallel);
        aNBT.setInteger("clusterBatchSize", lastBatchSize);
        if (pendingOutputs != null) {
            aNBT.setTag("clusterPendingOutputs", GTUtility.saveItemList(pendingOutputs));
        } else {
            aNBT.removeTag("clusterPendingOutputs");
        }
    }

    /**
     * 回读对称：按 ordinal 反解链整链重建（越界 ordinal 静默丢弃）；峰步 "clusterChainPeak"
     * hasKey 缺省 0、越界（&lt;0 或 ≥ 链长）回落 0（S1-T9）；队列模式 "clusterQueueMode" 缺省 false。
     * 旧档 tank 键（"clusterWaterTank"/"clusterChemTank"）不再读取——缺失/残留均静默忽略，不迁移
     * 不崩溃；存在暂存产出键时沿 GT 已保存进度续跑，完成待排空的批次保持进度零，仅重试输出。
     * 旧档缺暂存键仍按原规则清零进度，不从输入或配方重新生成产物。
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
        int storedPeak = aNBT.hasKey("clusterChainPeak") ? aNBT.getInteger("clusterChainPeak") : 0;
        rebuilt.setPeakIndex(storedPeak >= 0 && storedPeak < rebuilt.length() ? storedPeak : 0);
        chain = rebuilt;
        queueMode = aNBT.hasKey("clusterQueueMode") && aNBT.getBoolean("clusterQueueMode");
        lastBatchParallel = aNBT.getInteger("clusterBatchParallel");
        lastBatchSize = aNBT.getInteger("clusterBatchSize");
        if (aNBT.hasKey("clusterPendingOutputs", 9)) {
            pendingOutputs = GTUtility.loadItemList(aNBT.getTagList("clusterPendingOutputs", 10));
            chainCooldownTicks = Math.max(0, mMaxProgresstime - mProgresstime);
        } else {
            // 旧档未保存暂存产出，仍按原来的空闲恢复规则处理。
            abortPendingRun();
            mRunningOnLoad = false;
        }
    }

    // ------------------------------------------------------------------
    // 批配方时间（SR-Cluster-r6 S3：ClusterChainExecutor 写入，服务端 onPostTick 逐刻递减）
    // ------------------------------------------------------------------

    /**
     * 下次可尝试开批剩余 tick：成功开批由执行器写入整批配方时间，随后用于启动真实进度。
     * 持有已付暂存产出期间，{@link #onPostTick} 与读档路径都从 GT 剩余进度重建此值，
     * 保持批间零空转。流体/输出预检失败则写入固定短退避；空闲退避为瞬态，逐刻递减。
     */
    private long chainCooldownTicks;

    /** @return 本批配方时间剩余 tick（0 = 可立即执行下一批）。 */
    public long getChainCooldownTicks() {
        return chainCooldownTicks;
    }

    /** 设置本批配方时间，成功开批后由真实配方剩余进度保持同步。 */
    public void setChainCooldownTicks(long ticks) {
        this.chainCooldownTicks = ticks;
    }

    /**
     * ME 空批握手节流门（T2③）：空窗退避武装（{@link #armMeProbeThrottle()}）写入的最早重开窗 tick，
     * {@link #beginMEBusProcessingIfDue()} 据此拦截节流期内的握手。瞬态不落 NBT——读档丢失
     * = 多探测一次，无害。
     */
    private long meProbeNotBeforeTick;

    /**
     * 排空失败退避计数（T2④）：暂存产出排空失败（输出总线满）后武装为
     * {@link ClusterParams#DRAIN_RETRY_BACKOFF_TICKS}，退避期内 {@link #onPostTick} 逐刻递减并
     * 跳过排空重试，避免输出满期间每 tick 逐组探测-实放全量重跑。刻意独立于
     * {@link #chainCooldownTicks}——排空发生在配方读零之后，混写会污染批冷却节拍语义（成功开批
     * 覆写关系不可推理）。瞬态不落 NBT——读档丢失 = 多试一次排空，无害。
     */
    private int drainRetryDelayTicks;

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
     * 功能群（v1.11.15）：物流耗时行 + 润滑剂行 + 软锤启停行；软锤启停为纯文案行
     * （默认开机，{@code isAllowedToWork} 语义）。旧「处理窗口下限」行与同名词常量一并删除
     * （窗口 = 本批配方时间全长，S1-T2）。
     */
    @Override
    protected void addUnitTooltipInfo(MultiblockTooltipBuilder tt) {
        tt.addInfo(
            EnumChatFormatting.YELLOW + String.format(
                StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.logistics.segment_time"),
                gold(tierValues(ClusterParams.LOGISTICS_TIME_SEC, " s"))))
            .addInfo(
                EnumChatFormatting.YELLOW + String.format(
                    StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.logistics.lubricant"),
                    gold(tierValues(ClusterParams.LOGISTICS_UNIT_LUBRICANT_LPS, " L/s"))))
            .addInfo(
                EnumChatFormatting.YELLOW
                    + StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.logistics.soft_hammer"))
            .addInfo(
                EnumChatFormatting.YELLOW
                    + StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.logistics.queue"));
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
