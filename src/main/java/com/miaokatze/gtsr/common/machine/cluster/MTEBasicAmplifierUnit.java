package com.miaokatze.gtsr.common.machine.cluster;

import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;

import bartworks.system.material.Werkstoff;
import bartworks.system.material.WerkstoffLoader;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTUtility;

/**
 * 增幅模块基类：集群五类增幅（并行/速度/主产物/副产物/蒸汽效率）的公共骨架。
 * <p>
 * 正面 {@code (0,3,0)} 为输入仓位（r9 起 'A' 元素 = ofChain(tiered 外壳, InputHatch)——输入仓可置
 * 任意 A 位，至少一个否则不成型），E4 经济结算经 {@link #tryConsumeAmplifierFluid} 按秒预检实扣，
 * 预检失败零扣、该模块当秒无增益无惩罚。
 * <p>
 * 锁定流体直接从 H 输入仓读取并按秒扣除；不再启用内部流体槽，管道直灌面随之关闭。
 * 手工容器交互（桶/胶囊）沿承 {@link MTEClusterUnitBase} 家族语义（关闭），流体出入一律走管道/ME。
 * <p>
 * 锁定流体解析表（集中在本类 {@link #resolveBoosterFluid}，null 安全）：
 * <ul>
 * <li>PARALLEL（并行增幅）→ 硝酸 {@code Materials.NitricAcid.getFluid(1).getFluid()}
 * <li>SPEED（速度增幅）→ 盐酸 {@code Materials.HydrochloricAcid.getFluid(1).getFluid()}
 * <li>PRIMARY_OUTPUT（主产物增幅）→ 硫酸 {@code Materials.SulfuricAcid.getFluid(1).getFluid()}
 * <li>SECONDARY_OUTPUT（副产物增幅）→ 氯化铵（BW Werkstoff）
 * {@code WerkstoffLoader.AmmoniumChloride.getFluidOrGas(1)}
 * <li>STEAM_SAVER（蒸汽效率增幅）→ 冷却液 {@code FluidRegistry.getFluid(ClusterParams.BOOSTER_COOLANT_FLUID)}，
 * 注册缺失时回退 {@code Materials.SuperCoolant}；仍解析失败则该增幅禁用（isFluidAvailable 恒 false，不崩）
 * </ul>
 */
public abstract class MTEBasicAmplifierUnit extends MTEClusterUnitBase<MTEBasicAmplifierUnit> {

    /** 本模块增幅类型（构造期定死，拷贝构造同型透传）。 */
    private final ClusterParams.BoosterType boosterType;

    /** H 输入仓 hint 底材索引（青铜外壳，与 ClusterStructureDef 主控挂点同口径；纯 int，无贴图分配）。 */
    private static final int HATCH_HINT_CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10);

    /** 锁定流体（懒解析缓存；null=尚未解析成功或解析不出 → 增幅禁用）。 */
    private Fluid lockedFluid;

    protected MTEBasicAmplifierUnit(int aID, String aName, String aNameRegional, ClusterParams.BoosterType type) {
        super(aID, aName, aNameRegional);
        this.boosterType = type;
    }

    protected MTEBasicAmplifierUnit(String aName, ClusterParams.BoosterType type) {
        super(aName);
        this.boosterType = type;
    }

    @Override
    protected String[][] getUnitShape() {
        // canonical [Z][Y][X]，r9 权威规格 3×9×3（控制器 (1,4,0)）：旧 'H' 输入仓位 (0,3,0) 并入
        // 'A'（输入仓任意 A 位混挂），旧 'D' 石头位 (1,7,1) 改 'e'（粒子候选空气位）；字符 diff 仅 2 格
        return new String[][] { { "AAA", "ACA", "ACA", "BAB", "B~B", "BAB", "ACA", "ACA", "AAA" },
            { "AAA", "C-C", "C-C", "C-C", "C-C", "C-C", "C-C", "CeC", "BBB" },
            { "AAA", "ACA", "ACA", "BCB", "BCB", "BCB", "ACA", "ACA", "AAA" }, };
    }

    /**
     * 'A' 元素覆写（r9，范式同物流四 I/O 与 ClusterStructureDef A 总控仓室元素）：tiered 外壳
     * （默认形态，四族 casing 之一）或 anyOf(标准输入仓)——输入仓可置于矩阵任意 A 位；数量校验在
     * {@link #checkMachine}（mInputHatches ≥ 1）。禁用 atLeast（GT5U atLeast 是「各元素至少一个」
     * 语义，此处不适用）；casingIndex+hint 齐备（静态青铜 hint 口径保留）。
     */
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected IStructureElement tieredCasingElement() {
        return StructureUtility.ofChain(
            super.tieredCasingElement(),
            buildHatchAdder(MTEBasicAmplifierUnit.class).anyOf(InputHatch)
                .casingIndex(HATCH_HINT_CASING_INDEX)
                .hint(1)
                .build());
    }

    /**
     * 专有结构元素（r9 权威绑定）：B=管道族（沿用旧绑定）、C=玻璃、'-'/'e'=严格空气；
     * 原 'D'（Blocks.stone）与 'H'（专用输入仓字符）绑定删除——输入仓改经 'A' 元素链混挂。
     */
    @Override
    @SuppressWarnings("rawtypes")
    protected void addUnitStructureElements(StructureDefinition.Builder builder) {
        builder.addElement('B', tieredPipeElement())
            .addElement('C', glassElement())
            .addElement('-', airElement())
            .addElement('e', airElement());
    }

    @Override
    protected int getStructureOffsetA() {
        return 1;
    }

    @Override
    protected int getStructureOffsetB() {
        return 4;
    }

    @Override
    protected int getStructureOffsetC() {
        return 0;
    }

    /**
     * 成型校验追加：正面 H 输入仓至少一个（{@code mInputHatches} 非空），缺失即整体不成型
     * （errors 非空 → checkStructure 判 mMachine=false；checkStructure 在 checkMachine 前已
     * clearHatches，本处无需自清）。lang key 缺键时显示原键，由 lang 并行切片补齐。
     * 成型成功末尾按 unitStructureTier 刷新 H 输入仓贴图（切片 2 统一入口）。
     */
    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        super.checkMachine(aBaseMetaTileEntity, aStack, errors);
        if (errors.isEmpty() && mInputHatches.isEmpty()) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.booster_missing_input_hatch"));
            return;
        }
        if (errors.isEmpty()) {
            refreshHatchTextures(mInputHatches);
        }
    }

    @Override
    public boolean showRecipeTextInGUI() {
        return false;
    }

    @Override
    public abstract IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity);

    /**
     * E4/S7 结算冻结接口：本模块每秒增幅液<b>实际</b>消耗（L/s，联动加成后向上取整）——
     * {@link #amplifierFluidPerSecExact()} 取整口径；tier 无效（未成型/越界）返回 0。
     * 预检（{@code BoosterState.aggregate} 支付判定）与实扣（主控 {@code tryConsumeAmplifierFluid}）
     * 统一走本值，保证「预检 = 实扣」同一口径。
     */
    public int amplifierFluidPerSec() {
        double exact = amplifierFluidPerSecExact();
        return exact <= 0 ? 0 : (int) Math.ceil(exact - 1e-9);
    }

    /**
     * S7 联动加成后的实际每秒消耗精确值（L/s，可为小数，显示与公式串共用）：
     *
     * <pre>
     * 实耗 = amplifierFluidLps(type, 单元已验证结构 tier) × (1 + Σ 施加方 BOOSTER_SURCHARGE_PCT[tier] / 100)
     * </pre>
     *
     * 加成明细见 {@link #amplifierSurchargeSources()}；无施加方时退化为基础表值。
     */
    public double amplifierFluidPerSecExact() {
        int tier = getUnitStructureTier();
        if (tier < 0 || tier >= ClusterParams.TIER_COUNT) return 0;
        double base = ClusterParams.amplifierFluidLps(boosterType, tier);
        int pctSum = 0;
        for (int[] source : amplifierSurchargeSources()) {
            pctSum += source[0];
        }
        return base * (1D + pctSum / 100D);
    }

    /**
     * S7 联动加成明细：每个元素为 {@code [施加方 pct%, 施加方结构 tier, 施加方类型 ordinal]}。
     * 施加方 = 集群内
     * 所有<b>其他</b>速度/并行型增幅模块（不含自身、不含加工/物流模块；速度↔并行互相影响），
     * 按施加方自身结构 tier 查 {@link ClusterParams#BOOSTER_SURCHARGE_PCT} 得 +5%/10%/30%/40%，
     * 加算叠加。施加方资格与 {@code BoosterState} 双重豁免同口径——已接入集群 && 自身成型 &&
     * 连接 tier 有效 && 锁定流体可用（缺流体模块不施压他人）；未接入集群返回空列表。
     * 类型 ordinal 供 GUI 公式串区分施加方种类（速度/并行加成表同值，数值上不可反推）；
     * GUI 编码（ClusterGuiSync KEY_BO_COST）与本计算共用本实现，保证显示 = 实扣。
     */
    public java.util.List<int[]> amplifierSurchargeSources() {
        java.util.List<int[]> sources = new java.util.ArrayList<>();
        if (cluster == null || boosterType == null) return sources;
        for (MTEBasicAmplifierUnit other : cluster.getTopology()
            .getBoosterUnits()) {
            if (other == null || other == this) continue;
            if (other.boosterType != ClusterParams.BoosterType.PARALLEL
                && other.boosterType != ClusterParams.BoosterType.SPEED) continue;
            if (!other.isTierValidForConnection() || !other.isFluidAvailable()) continue;
            int srcTier = other.getUnitStructureTier();
            int idx = Math.max(0, Math.min(srcTier, ClusterParams.TIER_COUNT - 1));
            sources.add(new int[] { ClusterParams.BOOSTER_SURCHARGE_PCT[idx], srcTier, other.boosterType.ordinal() });
        }
        return sources;
    }

    /**
     * E4 结算冻结接口：先跨输入仓合计预检足额、足额才整笔实扣（不足零扣原子语义——
     * {@code depleteFluidAcross} 允许部分提取，直接调用会破坏「不足零扣」）；不足（或参数非正）
     * 返回 false 且零扣——该模块当秒应从 BoosterState 剔除（无增益、无蒸汽惩罚乘子），
     * 剔除动作由 E4 结算侧完成。
     */
    public boolean tryConsumeAmplifierFluid(int liters) {
        Fluid locked = getLockedFluidOrNull();
        if (liters <= 0 || locked == null || mInputHatches.isEmpty()) return false;
        if (!GTSRHatchFluidAccess.hasEnoughAcross(mInputHatches, new FluidStack(locked, liters))) return false;
        int drained = GTSRHatchFluidAccess.depleteFluidAcross(mInputHatches, new FluidStack(locked, liters));
        return drained >= liters;
    }

    /**
     * 便捷判据：结构输入仓合计含锁定流体且 ≥ 1 mB。增幅「在供流体」的具体识别由主控拓扑层判，
     * 本方法只提供输入仓事实快照。
     */
    public boolean hasBoosterFluid() {
        Fluid locked = getLockedFluidOrNull();
        return locked != null
            && GTSRHatchFluidAccess.probeFluidAmountAcross(mInputHatches, new FluidStack(locked, 1)) >= 1;
    }

    /**
     * 增幅模块独立运行信号：自身成型 && 已连接集群 && 单元已验证 tier 有效 && 电气允许工作
     * （增幅「运行」= 模块在供流体运行；供流与否另见 {@link #hasBoosterFluid()}）。
     */
    @Override
    public boolean isUnitRunning() {
        return mMachine && cluster != null
            && getUnitStructureTier() >= 0
            && getBaseMetaTileEntity() != null
            && getBaseMetaTileEntity().isAllowedToWork();
    }

    /**
     * E2a 基类贴图钩子（增幅族正面 overlay，inactive）：由五个具体增幅类以 static final 常量绑定，
     * 禁止方法体内分配（NEI 安全：贴图缝合前完成解析）。
     */
    @Override
    protected abstract IIconContainer unitOverlayInactive();

    /** E2a 基类贴图钩子（增幅族正面 overlay，active）：绑定约束同 {@link #unitOverlayInactive()}。 */
    @Override
    protected abstract IIconContainer unitOverlayActive();

    public ClusterParams.BoosterType getBoosterType() {
        return boosterType;
    }

    /** 子类返回锁定流体；实现统一委托 {@link #resolveBoosterFluid}，返回 null 表示该增幅流体不可用。 */
    protected abstract Fluid resolveLockedFluid();

    /** 锁定流体懒解析：流体注册晚于本类实例化时后续调用仍可自愈；解析失败不缓存负面结果。 */
    private Fluid getLockedFluidOrNull() {
        if (lockedFluid == null) lockedFluid = resolveLockedFluid();
        return lockedFluid;
    }

    /** 输入仓存在锁定流体时视为增幅流体可用。 */
    public boolean isFluidAvailable() {
        if (!isModuleEnabled()) return false;
        Fluid locked = getLockedFluidOrNull();
        return locked != null
            && GTSRHatchFluidAccess.probeFluidAmountAcross(mInputHatches, new FluidStack(locked, 1)) > 0;
    }

    /**
     * 单元状态：未接入集群→STANDBY；锁定流体可用→WORKING；已接入但缺锁定流体→BOOSTER_FLUID_MISSING
     * （对应 GUI「缺增幅流体紫」状态色，增益失效）。
     */
    @Override
    public ClusterUnitStatus getUnitStatus() {
        if (!isUnitStructureFormed()) return ClusterUnitStatus.NO_POWER_OR_INVALID;
        if (getCluster() == null) return ClusterUnitStatus.STANDBY;
        return isFluidAvailable() ? ClusterUnitStatus.WORKING : ClusterUnitStatus.BOOSTER_FLUID_MISSING;
    }

    /** 当前结构等级下本模块贡献的增益值（如并行 +N、速度 +N%）。 */
    public int getBoosterValueForStructureTier() {
        return isModuleEnabled() && getStructureTier() >= 0 ? getBoosterType().getBoosterValue(getStructureTier()) : 0;
    }

    /** 当前锁定流体，供同包支付快照与 GUI 访问。 */
    public Fluid getBoosterFluidForAccess() {
        return getLockedFluidOrNull();
    }

    /** 当前增幅输入仓列表，供同包支付快照与 GUI 访问。 */
    public List<gregtech.api.metatileentity.implementations.MTEHatchInput> getInputHatchesForAccess() {
        return mInputHatches;
    }

    /** tank 当前内容（已移除内置缓存，始终为空）。 */
    public FluidStack getTankContent() {
        return null;
    }

    /** 内部 tank 已移除，增幅模块不暴露可灌装容量。 */
    @Override
    public int getCapacity() {
        return 0;
    }

    /** 锁定流体闸门：内部 tank 关闭后所有直灌路径均拒绝。 */
    @Override
    public boolean isFluidInputAllowed(FluidStack aFluid) {
        if (aFluid == null || aFluid.getFluid() == null) return false;
        Fluid locked = getLockedFluidOrNull();
        return locked != null && aFluid.getFluid() == locked;
    }

    /**
     * 类型名 key：unit_type 命名空间（如 gtsr.gui.cluster.unit_type.booster.parallel），
     * 与 {@link ClusterParams.BoosterType#getLangKey()} 的 booster.* 显示名 key 是两个命名空间。
     */
    @Override
    public String getUnitTypeNameKey() {
        return unitTypeKey(boosterType);
    }

    /** 增幅类型 → unit_type.lang key 映射（未知类型回退 parallel，防御不可达分支）。 */
    private static String unitTypeKey(ClusterParams.BoosterType type) {
        if (type == ClusterParams.BoosterType.SPEED) return "gtsr.gui.cluster.unit_type.booster.speed";
        if (type == ClusterParams.BoosterType.PRIMARY_OUTPUT) return "gtsr.gui.cluster.unit_type.booster.primary";
        if (type == ClusterParams.BoosterType.SECONDARY_OUTPUT) return "gtsr.gui.cluster.unit_type.booster.secondary";
        if (type == ClusterParams.BoosterType.STEAM_SAVER) {
            return "gtsr.gui.cluster.unit_type.booster.steam_saver";
        }
        return "gtsr.gui.cluster.unit_type.booster.parallel";
    }

    /**
     * 锁定流体集中解析（null 安全）：Materials 取流体经 {@code getFluid(1).getFluid()}；氯化铵为
     * BW Werkstoff（{@code WerkstoffLoader.AmmoniumChloride}），经 {@code getFluidOrGas(1)} 解析并
     * 捕获未注册异常回退 null；任一环节未注册返回 null；蒸汽效率增幅先查配置注册名冷却液，缺失
     * 回退超冷却液。
     */
    protected static Fluid resolveBoosterFluid(ClusterParams.BoosterType type) {
        if (type == null) return null;
        if (type == ClusterParams.BoosterType.PARALLEL) return toFluid(Materials.NitricAcid.getFluid(1));
        if (type == ClusterParams.BoosterType.SPEED) return toFluid(Materials.HydrochloricAcid.getFluid(1));
        if (type == ClusterParams.BoosterType.PRIMARY_OUTPUT) return toFluid(Materials.SulfuricAcid.getFluid(1));
        if (type == ClusterParams.BoosterType.SECONDARY_OUTPUT)
            return toWerkstoffFluid(WerkstoffLoader.AmmoniumChloride);
        if (type == ClusterParams.BoosterType.STEAM_SAVER) {
            Fluid coolant = FluidRegistry.getFluid(ClusterParams.BOOSTER_COOLANT_FLUID);
            return coolant != null ? coolant : toFluid(Materials.SuperCoolant.getFluid(1));
        }
        return null;
    }

    /** BW Werkstoff → 流体（null 安全）：流体未注册（BW 未加载完成等）时返回 null，不崩。 */
    private static Fluid toWerkstoffFluid(Werkstoff werkstoff) {
        try {
            FluidStack stack = werkstoff != null ? werkstoff.getFluidOrGas(1) : null;
            return stack != null ? stack.getFluid() : null;
        } catch (Throwable ignored) {
            // 氯化铵流体尚未注册：解析失败 → 副产物增幅禁用（isFluidAvailable 恒 false），不崩。
            return null;
        }
    }

    private static Fluid toFluid(FluidStack aStack) {
        return aStack != null ? aStack.getFluid() : null;
    }
}
