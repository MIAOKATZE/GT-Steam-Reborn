package com.miaokatze.gtsr.common.machine.cluster;

import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;

import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;
import com.miaokatze.gtsr.common.event.GTSRMachineEvent;
import com.miaokatze.gtsr.common.gui.cluster.MTEBasicLogisticsUnitGui;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;

/**
 * 物流模块：集群的单点链执行器骨架（E3b 切片：四 I/O 结构 + 默认关机 + 双 tank 语义收紧 + 独立 GUI stub）。
 * <p>
 * <b>结构（3×4×3，控制器 (1,2,0)）</b>：正面 z0 层四 I/O——D(0,0,0) 输入总线 / E(2,0,0) 输出总线 /
 * F(0,1,0) 输入仓 / G(2,1,0) 输出仓，各自独立 {@code atLeast} 单元素 hatch 挂点（四挂点互不共用），
 * 且 checkMachine 末尾显式校验四列表均非空（缺任一即不成型）。B=tiered 齿轮箱
 * （{@link #tieredGearboxElement()}，casings2:2/3/4/5）、C=tiered 框架
 * （{@link #tieredFrameElement()}），与 A 外壳经基类 {@code resolveUnitStructureTier} 分族同级
 * 强校验（跨 tier 混搭不成型）。{@link MTEHatchInputBusME} 在 {@link #addInputBusToMachineList}
 * 直接拒绝致结构不成型（范式同 GT5U MTETreeFarm：ME 输入总线会绕过物流批事务语义）。
 * <p>
 * <b>物理电源</b>：新放置模块在 {@link #onFirstTick} 一次性 {@code disableWorking()}（mWorks=false，
 * BaseMetaTileEntity 原生 NBT 持久化），"一次性已初始化"标记入 NBT 防区块重载重复关停用户已复位的
 * 模块；用户经软锤复位（GT 标准启停切换）。{@link #getUnitStatus()} 优先 {@code isAllowedToWork()}，
 * 关闭时显示"无功率/未通电"而非可工作。
 * <p>
 * <b>双 tank 语义（plan 3.4.5 收口）</b>：基类 mFluid 主 tank 全面弃用（get/set Fillable/Drainable 钉
 * null、无并行写入旁路）；自持水 tank（普通水+蒸馏水均收，蒸馏优先扣液路径由 E4 落）与化浴 tank
 * （仅含汞/过硫酸钠，GT5U 化学洗配方流体）两个独立 {@link FluidTank}。外部填充按流体类型严格路由，
 * 非法流体一律拒收；管道/ME 经 {@link #getTankInfo} 可见双 tank。自身输入仓每 20t 节流自动补液
 * （{@link #refillTanksFromHatches}，探测/实扣走 {@link GTSRHatchFluidAccess} 统一访问层）。
 * <p>
 * <b>交互</b>：右击不再跳转集群终端链编辑页，改为打开自身 GUI
 * （{@link MTEBasicLogisticsUnitGui.LogisticsUnitGuiFactory}，MUI2 最小 stub，批2 E6 全量重写）；
 * 链编辑入口只经集群 UI，本类保留 {@link #getChain()}/{@link #setChain(LogisticsChain)} 访问器。
 * 正面叠层经基类 E2a 钩子 {@link #unitOverlayInactive()}/{@link #unitOverlayActive()}（拆解机
 * 贴图四态，Textures.BlockIcons T:1306-1309），底材随 unitStructureTier 四档联动（3.5.2）。
 * <p>
 * 链与双 tank 的 NBT 自落（{@link #saveNBTData}）：链存 "clusterChain"，双 tank 存
 * "clusterWaterTank"/"clusterChemTank"，电源初始化标记存 "clusterLogiPowerInit"，
 * 低温通知位存 "clusterLogiLowTemp"。
 * 类型名 key：gtsr.gui.cluster.unit_type.logistics。
 */
public class MTEBasicLogisticsUnit extends MTEClusterUnitBase<MTEBasicLogisticsUnit> {

    /** 四 hatch 挂点与 hatch 贴图共用的青铜底材贴图 id（同总控 hatch 挂点）。 */
    private static final int HATCH_CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10);

    /** 正面叠层：拆解机贴图四态之停机态（Textures.BlockIcons T:1306）。 */
    private static final IIconContainer UNIT_OVERLAY_INACTIVE = Textures.BlockIcons.OVERLAY_FRONT_DISASSEMBLER;

    /** 正面叠层：拆解机贴图四态之运行态（T:1308；辉光态 T:1307/1309 留 E6 视觉迭代）。 */
    private static final IIconContainer UNIT_OVERLAY_ACTIVE = Textures.BlockIcons.OVERLAY_FRONT_DISASSEMBLER_ACTIVE;

    /** 本模块的有序链（永非 null；setChain(null) 亦只置空链）。 */
    private LogisticsChain chain = new LogisticsChain();

    /** 水 tank：普通水+蒸馏水（洗矿/简易洗批流体；蒸馏优先扣液路径由 E4 落）。 */
    private final FluidTank waterTank = new FluidTank(ClusterParams.LOGISTICS_TANK_CAPACITY_L);

    /** 化浴 tank：仅含汞/过硫酸钠（GT5U 化学洗 CHEM_BATH 配方介质）。 */
    private final FluidTank chemBathTank = new FluidTank(ClusterParams.LOGISTICS_TANK_CAPACITY_L);

    /** 一次性电源初始化标记：onFirstTick 默认关机只执行一次，NBT 持久化防重载复关。 */
    private boolean powerOnInitDone;

    /** 低温关机通知位：true 时 onLowTemperatureShutdown 不再刷屏，软锤复位清除，NBT 持久化。 */
    private boolean lowTempNotified;

    /** 链脏标记（瞬态）：置位表示链需在下次执行前重校验（重校验由 E4/主控执行）。 */
    private boolean chainDirty;

    public MTEBasicLogisticsUnit(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEBasicLogisticsUnit(String aName) {
        super(aName);
    }

    // ------------------------------------------------------------------
    // 结构：3×4×3 四 I/O 矩阵（plan 3.3.3）
    // ------------------------------------------------------------------

    /**
     * 结构矩阵（[Z][Y][X]，z0=正面）：
     *
     * <pre>
     * z0 = [DAE / FAG / A~A / AAA]   D输入总线 E输出总线 / F输入仓 G输出仓 / 控制器 / 底排
     * z1 = [CAC / C C / CAC / ABA]
     * z2 = [AAA / AAA / AAA / ABA]
     * </pre>
     *
     * 控制器 '~' 位于 (1,2,0)（offsets 1/2/0 不变）。
     */
    @Override
    protected String[][] getUnitShape() {
        return new String[][] { { "DAE", "FAG", "A~A", "AAA" }, { "CAC", "C C", "CAC", "ABA" },
            { "AAA", "AAA", "AAA", "ABA" }, };
    }

    /**
     * 专有结构元素：B/C 用基类 tier 族元素（tieredGearboxElement/tieredFrameElement，分族 tier
     * 经基类 resolveUnitStructureTier 同级强校验）；D/E/F/G 为四个互不共用的单元素 hatch 挂点
     * （各自独立 atLeast：D=输入总线、E=输出总线、F=输入仓、G=输出仓——任一位置放错 hatch 类型
     * 即该挂点 check 失败致结构不成型）。
     */
    @Override
    @SuppressWarnings("rawtypes")
    protected void addUnitStructureElements(StructureDefinition.Builder builder) {
        builder.addElement('B', tieredGearboxElement());
        builder.addElement('C', tieredFrameElement());
        builder.addElement(
            'D',
            buildHatchAdder(MTEBasicLogisticsUnit.class).atLeast(InputBus)
                .casingIndex(HATCH_CASING_INDEX)
                .hint(1)
                .build());
        builder.addElement(
            'E',
            buildHatchAdder(MTEBasicLogisticsUnit.class).atLeast(OutputBus)
                .casingIndex(HATCH_CASING_INDEX)
                .hint(1)
                .build());
        builder.addElement(
            'F',
            buildHatchAdder(MTEBasicLogisticsUnit.class).atLeast(InputHatch)
                .casingIndex(HATCH_CASING_INDEX)
                .hint(1)
                .build());
        builder.addElement(
            'G',
            buildHatchAdder(MTEBasicLogisticsUnit.class).atLeast(OutputHatch)
                .casingIndex(HATCH_CASING_INDEX)
                .hint(1)
                .build());
    }

    @Override
    protected int getStructureOffsetA() {
        return 1;
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
     * 成型后追加四 I/O 列表非空校验（belt-and-suspenders：四挂点单元素 atLeast 已天然保证非空，
     * 此处显式复核以固化"缺任一四 I/O 不成型"验收口径）。errors 非空由父类折算为不成型。
     */
    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        super.checkMachine(aBaseMetaTileEntity, aStack, errors);
        if (!errors.isEmpty()) return;
        if (mInputBusses.isEmpty()) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.missing_input_bus"));
            return;
        }
        if (mOutputBusses.isEmpty()) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.missing_output_bus"));
            return;
        }
        if (mInputHatches.isEmpty()) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.missing_fluid_hatch"));
            return;
        }
        if (mOutputHatches.isEmpty()) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.missing_output_hatch"));
            return;
        }
    }

    /**
     * ME 输入总线拒绝（范式同 GT5U MTETreeFarm#addInputBusToMachineList）：ME 总线抽料绕过
     * 物流批事务的输入总线语义，直接返回 false 使 D 挂点校验失败、结构不成型。
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

    /** 本模块自身输入总线（GT 标准列表 live 视图；结构成型时至少含 D 挂点一枚）。 */
    public List<MTEHatchInputBus> getLogisticsInputBusses() {
        return mInputBusses;
    }

    /** 本模块自身输出总线（GT 标准列表 live 视图；结构成型时至少含 E 挂点一枚）。 */
    public List<MTEHatchOutputBus> getLogisticsOutputBusses() {
        return mOutputBusses;
    }

    /** 本模块自身输入仓/输出仓（live 视图；输入仓供双 tank 补液，输出仓为结构要求）。 */
    public List<MTEHatchInput> getLogisticsInputHatches() {
        return mInputHatches;
    }

    public List<MTEHatchOutput> getLogisticsOutputHatches() {
        return mOutputHatches;
    }

    // ------------------------------------------------------------------
    // 物理电源（默认关机 + 低温一次性通知）
    // ------------------------------------------------------------------

    /**
     * 一次性默认关机：新放置模块首个 tick 关闭物理电源（mWorks=false）。标记 NBT 持久化
     * （"clusterLogiPowerInit"）——区块重载再次触发 onFirstTick 时不重复关停用户已软锤复位的
     * 模块；mWorks 本身由 BaseMetaTileEntity NBT 原生持久化。
     */
    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (aBaseMetaTileEntity == null || !aBaseMetaTileEntity.isServerSide()) return;
        if (!powerOnInitDone) {
            powerOnInitDone = true;
            aBaseMetaTileEntity.disableWorking();
        }
    }

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
     * 低温一次性关机通知（E4 调用）：首次发送
     * {@code GTSRMachineEvent.sendToOwner(ownerUuid, "gtsr.chat.cluster.temperature_low")}
     * 并 {@code disableWorking()}；通知位 NBT 持久化（"clusterLogiLowTemp"），重复调用不刷屏
     * （disableWorking 幂等）。复位经 {@link #onSoftHammerReset()}。
     */
    public void onLowTemperatureShutdown() {
        if (!lowTempNotified) {
            lowTempNotified = true;
            IGregTechTileEntity base = getBaseMetaTileEntity();
            if (base != null) {
                GTSRMachineEvent.sendToOwner(base.getOwnerUuid(), "gtsr.chat.cluster.temperature_low");
            }
        }
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.isAllowedToWork()) base.disableWorking();
    }

    /** 软锤复位回调（E4 调用）：清低温通知位 + 标记链重校验（重校验本身由 E4/主控在下次执行前做）。 */
    public void onSoftHammerReset() {
        lowTempNotified = false;
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
     * 状态细化（自上而下）：
     * <ol>
     * <li>未自成型 → NO_POWER_OR_INVALID；</li>
     * <li>物理电源关闭（!isAllowedToWork()，含默认关机/低温关机/软锤关闭）→ NO_POWER_OR_INVALID
     * （"关机/未通电"，不得显示可工作或待机）；</li>
     * <li>未入集群 / 总控停机 / 链空 → STANDBY；</li>
     * <li>链不可执行（!{@link LogisticsChain#isExecutable}）→ NO_POWER_OR_INVALID；</li>
     * <li>洗矿/化洗批流体不足（!{@link #hasBatchFluids}）→ FLUID_MISSING；</li>
     * <li>其余（就绪或批冷却中）→ WORKING。</li>
     * </ol>
     */
    @Override
    public ClusterUnitStatus getUnitStatus() {
        if (!isUnitStructureFormed()) return ClusterUnitStatus.NO_POWER_OR_INVALID;
        if (!isPowerAllowed()) return ClusterUnitStatus.NO_POWER_OR_INVALID;
        MTESteamMineralLogisticsCluster cluster = getCluster();
        if (cluster == null) return ClusterUnitStatus.STANDBY;
        if (!cluster.isMachineEnabled()) return ClusterUnitStatus.STANDBY;
        if (chain.isEmpty()) return ClusterUnitStatus.STANDBY;
        if (!chain.isExecutable(cluster.getTopology())) return ClusterUnitStatus.NO_POWER_OR_INVALID;
        boolean needWater = chain.countOf(ChainLink.ORE_WASH) > 0;
        boolean needChemBath = chain.countOf(ChainLink.CHEM_BATH) > 0;
        if ((needWater || needChemBath) && !hasBatchFluids(needWater, needChemBath)) {
            return ClusterUnitStatus.FLUID_MISSING;
        }
        return ClusterUnitStatus.WORKING;
    }

    // ------------------------------------------------------------------
    // 双 tank 语义（plan 3.4.5：基类 mFluid 全收口）
    // ------------------------------------------------------------------

    /** 判水系（waterTank 接受面）：普通水或蒸馏水同名变体（GT5U Materials 无 DistilledWater 常量，走名称匹配）。 */
    private static boolean isWaterLike(FluidStack aFluid) {
        if (aFluid == null || aFluid.getFluid() == null) return false;
        Fluid fluid = aFluid.getFluid();
        if (fluid == FluidRegistry.WATER) return true;
        String name = fluid.getName();
        return "water".equalsIgnoreCase(name) || "distilledwater".equalsIgnoreCase(name)
            || "distilled_water".equalsIgnoreCase(name);
    }

    /** 判化浴液（chemBathTank 唯一接受面）：含汞/过硫酸钠（GT5U 化学洗配方流体，Materials.mFluid 直引+名称兜底）。 */
    private static boolean isChemBathFluid(FluidStack aFluid) {
        if (aFluid == null || aFluid.getFluid() == null) return false;
        Fluid fluid = aFluid.getFluid();
        if (fluid == Materials.Mercury.mFluid || fluid == Materials.SodiumPersulfate.mFluid) return true;
        String name = fluid.getName();
        return "mercury".equalsIgnoreCase(name) || "sodiumpersulfate".equalsIgnoreCase(name)
            || "sodium_persulfate".equalsIgnoreCase(name);
    }

    /** 水 tank 补液候选（蒸馏水优先，其次普通水；null 剔重）。 */
    private static List<Fluid> waterCandidates() {
        Set<Fluid> fluids = new LinkedHashSet<>();
        if (FluidRegistry.getFluid("distilledwater") != null) fluids.add(FluidRegistry.getFluid("distilledwater"));
        if (FluidRegistry.getFluid("distilled_water") != null) fluids.add(FluidRegistry.getFluid("distilled_water"));
        if (FluidRegistry.WATER != null) fluids.add(FluidRegistry.WATER);
        return new ArrayList<>(fluids);
    }

    /** 化浴 tank 补液候选（含汞、过硫酸钠；null 剔重）。 */
    private static List<Fluid> chemBathCandidates() {
        Set<Fluid> fluids = new LinkedHashSet<>();
        if (Materials.Mercury.mFluid != null) fluids.add(Materials.Mercury.mFluid);
        if (FluidRegistry.getFluid("mercury") != null) fluids.add(FluidRegistry.getFluid("mercury"));
        if (Materials.SodiumPersulfate.mFluid != null) fluids.add(Materials.SodiumPersulfate.mFluid);
        if (FluidRegistry.getFluid("sodium_persulfate") != null)
            fluids.add(FluidRegistry.getFluid("sodium_persulfate"));
        if (FluidRegistry.getFluid("sodiumpersulfate") != null) fluids.add(FluidRegistry.getFluid("sodiumpersulfate"));
        return new ArrayList<>(fluids);
    }

    public FluidTank getWaterTank() {
        return waterTank;
    }

    public FluidTank getChemBathTank() {
        return chemBathTank;
    }

    /**
     * 填充统一分发点（语义收紧）：水系→waterTank，化浴液→chemBathTank，其余流体一律拒收
     * （返回 0）；成功写入时标记脏块。覆盖基类 mFluid 路径，无旁路。
     */
    @Override
    public int fill(FluidStack aFluid, boolean doFill) {
        if (aFluid == null || aFluid.getFluid() == null || aFluid.amount <= 0 || !canTankBeFilled()) return 0;
        FluidTank target = isWaterLike(aFluid) ? waterTank : isChemBathFluid(aFluid) ? chemBathTank : null;
        if (target == null) return 0;
        int filled = target.fill(aFluid, doFill);
        if (filled > 0 && doFill) markDirty();
        return filled;
    }

    /** 无类型放出：化浴 tank 优先，空则放水 tank（覆盖基类 mFluid 语义）。 */
    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        if (maxDrain <= 0 || !canTankBeEmptied()) return null;
        FluidStack drained = chemBathTank.getFluid() != null ? chemBathTank.drain(maxDrain, doDrain) : null;
        if (drained == null) drained = waterTank.drain(maxDrain, doDrain);
        if (drained != null && doDrain) markDirty();
        return drained;
    }

    /** 类型敏感放出（ME/CD 管道按流体请求）：匹配哪个 tank 就从哪个放，都不匹配返回 null。 */
    @Override
    public FluidStack drain(ForgeDirection side, FluidStack fluidStack, int amount, boolean doDrain) {
        if (fluidStack == null || amount <= 0) return null;
        FluidStack water = waterTank.getFluid();
        if (water != null && water.isFluidEqual(fluidStack)) {
            FluidStack drained = waterTank.drain(amount, doDrain);
            if (drained != null && doDrain) markDirty();
            return drained;
        }
        FluidStack chemBath = chemBathTank.getFluid();
        if (chemBath != null && chemBath.isFluidEqual(fluidStack)) {
            FluidStack drained = chemBathTank.drain(amount, doDrain);
            if (drained != null && doDrain) markDirty();
            return drained;
        }
        return null;
    }

    /** 基类 mFluid 主 tank 全收口（plan 3.4.5）：读写钉 null，一切存储走双 tank，无并行旁路。 */
    @Override
    public FluidStack getFillableStack() {
        return null;
    }

    @Override
    public FluidStack setFillableStack(FluidStack aFluid) {
        return null;
    }

    @Override
    public FluidStack getDrainableStack() {
        return null;
    }

    @Override
    public FluidStack setDrainableStack(FluidStack aFluid) {
        return null;
    }

    /** 兼容读数：化浴优先的非空内容视图。 */
    @Override
    public FluidStack getFluid() {
        if (chemBathTank.getFluid() != null) return chemBathTank.getFluid();
        return waterTank.getFluid();
    }

    /** 兼容读数：双 tank 总存量。 */
    @Override
    public int getFluidAmount() {
        return waterTank.getFluidAmount() + chemBathTank.getFluidAmount();
    }

    /** 填充门（防御纵深）：仅水系与化浴液可入（fill 已路由，此处双保险）。 */
    @Override
    public boolean isFluidInputAllowed(FluidStack aFluid) {
        return isWaterLike(aFluid) || isChemBathFluid(aFluid);
    }

    /** 单 tank 名义容量（管道/ME 显示用；实际双 tank 见 getTankInfo）。 */
    @Override
    public int getCapacity() {
        return ClusterParams.LOGISTICS_TANK_CAPACITY_L;
    }

    /** 双 tank 信息暴露：管道/ME 按侧可见两个独立 tank。 */
    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection side) {
        return new FluidTankInfo[] { waterTank.getInfo(), chemBathTank.getInfo() };
    }

    /**
     * 批流体判定：链含洗矿（needWater）/化洗（needChemBath）时，对应 tank 存量须达到每批用量
     * （{@link ClusterParams#WASH_WATER_PER_BATCH_L} / {@link ClusterParams#CHEM_BATH_FLUID_PER_BATCH_L}，
     * 均 1000L）；两者都不需要时恒 true。
     */
    public boolean hasBatchFluids(boolean needWater, boolean needChemBath) {
        if (!isModuleEnabled()) return false;
        if (needWater && waterTank.getFluidAmount() < ClusterParams.WASH_WATER_PER_BATCH_L) return false;
        return !needChemBath || chemBathTank.getFluidAmount() >= ClusterParams.CHEM_BATH_FLUID_PER_BATCH_L;
    }

    /**
     * 从自身输入仓向双 tank 自动补液（契约方法，onPostTick 每 20t 节流调用；也可由 E4 显式调用）：
     * 逐仓探测候选流体（蒸馏水优先→普通水入 waterTank；含汞/过硫酸钠入 chemBathTank），
     * 按目标 tank 剩余容量为上限，探测→实扣两段式（GTSRHatchFluidAccess 口径，3 参 UNKNOWN
     * 实扣兼容普通仓/ME 仓）。
     */
    public void refillTanksFromHatches() {
        if (!isUnitStructureFormed()) return;
        for (MTEHatch hatch : GTUtility.validMTEList(mInputHatches)) {
            if (hatch == null) continue;
            refillFromHatch(hatch, waterCandidates(), waterTank);
            refillFromHatch(hatch, chemBathCandidates(), chemBathTank);
        }
    }

    /** 单仓补液：候选序探测→按 tank 剩余容量实扣→注入 tank；全失败静默返回。 */
    private static void refillFromHatch(MTEHatch hatch, List<Fluid> candidates, FluidTank tank) {
        int free = tank.getCapacity() - tank.getFluidAmount();
        if (free <= 0) return;
        for (Fluid fluid : candidates) {
            FluidStack probed = GTSRHatchFluidAccess.probeFluidAmount(hatch, fluid, free);
            if (probed == null || probed.amount <= 0) continue;
            FluidStack drained = hatch.drain(ForgeDirection.UNKNOWN, new FluidStack(fluid, probed.amount), true);
            if (drained == null || drained.amount <= 0) continue;
            tank.fill(drained, true);
            return;
        }
    }

    /** 每 20t 节流补液（服务端；结构未成型时 refill 内部自短路）。 */
    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTimer) {
        super.onPostTick(aBaseMetaTileEntity, aTimer);
        if (aBaseMetaTileEntity != null && aBaseMetaTileEntity.isServerSide() && aTimer % 20 == 0) {
            refillTanksFromHatches();
        }
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
     * 右击分流（空手/持任意物品皆同）：服务端打开自身 GUI stub
     * （{@link MTEBasicLogisticsUnitGui.LogisticsUnitGuiFactory#open}），双端均返回 true 消费事件。
     * 不再跳转集群终端链编辑页——链编辑职责只经集群 UI；本 GUI 为最小状态页（批2 E6 全量重写）。
     * 注：潜行右击收不到本事件——GT BaseMetaTileEntity 在潜行时拦截右击（用于贴墙放方块）。
     */
    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        if (aBaseMetaTileEntity.isServerSide()) {
            MTEBasicLogisticsUnitGui.LogisticsUnitGuiFactory.open(aPlayer, this);
        }
        return true;
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
     * 链/双 tank/电源与通知位持久化：链存 "clusterChain" int 数组（ChainLink.ordinal，空链空数组）；
     * 双 tank 存 "clusterWaterTank"/"clusterChemTank"；一次性电源初始化标记存 "clusterLogiPowerInit"
     * （防重载复关）；低温通知位存 "clusterLogiLowTemp"（防重载重发）。super 保留基类 mFluid 语义
     * （本类恒 null，不落 tag，无并行写入旁路）。
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
        aNBT.setTag("clusterWaterTank", waterTank.writeToNBT(new NBTTagCompound()));
        aNBT.setTag("clusterChemTank", chemBathTank.writeToNBT(new NBTTagCompound()));
        aNBT.setBoolean("clusterLogiPowerInit", powerOnInitDone);
        aNBT.setBoolean("clusterLogiLowTemp", lowTempNotified);
    }

    /**
     * 回读对称：按 ordinal 反解链整链重建（越界 ordinal 静默丢弃）；双 tank 经
     * {@link FluidTank#readFromNBT} 恢复；两个标记位回读（缺失时回 false）。tag 缺失天然回退
     * 空链/空 tank/false，不崩。
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
        waterTank.readFromNBT(aNBT.getCompoundTag("clusterWaterTank"));
        chemBathTank.readFromNBT(aNBT.getCompoundTag("clusterChemTank"));
        powerOnInitDone = aNBT.getBoolean("clusterLogiPowerInit");
        lowTempNotified = aNBT.getBoolean("clusterLogiLowTemp");
    }

    // ------------------------------------------------------------------
    // 批冷却（ClusterChainExecutor 节拍）
    // ------------------------------------------------------------------

    /**
     * 批处理冷却剩余 tick：每批执行后由 ClusterChainExecutor 置为 max(20, 本批耗时秒×20)，
     * 总控每 20t 统一 -20，≤0 且全部门控通过才可再执行一批。不持久化——重载/重摆后从零
     * 开始（冷却只是节拍器，非玩家资产）。
     */
    private long chainCooldownTicks;

    /** @return 批处理冷却剩余 tick（0 = 可立即执行下一批）。 */
    public long getChainCooldownTicks() {
        return chainCooldownTicks;
    }

    /** 设置批处理冷却（ClusterChainExecutor 批执行后写入；不持久化，重载后从零开始）。 */
    public void setChainCooldownTicks(long ticks) {
        this.chainCooldownTicks = ticks;
    }
}
