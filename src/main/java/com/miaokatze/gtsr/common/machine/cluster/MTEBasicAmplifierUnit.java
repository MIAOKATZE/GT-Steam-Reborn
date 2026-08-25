package com.miaokatze.gtsr.common.machine.cluster;

import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.List;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;

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
 * 每个增幅模块自带一个只接受「锁定流体」的内置 tank（容量 {@link ClusterParams#BOOSTER_TANK_CAPACITY_L}），
 * 是增幅液缓冲（基类 tank 在增幅族豁免收紧）。正面 {@code (0,3,0)} 为 H 输入仓（标准
 * {@code InputHatch}，至少一个否则不成型），服务端成型后每 tick 自动把仓内锁定流体补入 mFluid
 * （tank 满时 O(1) 早退）；E4 经济结算经 {@link #tryConsumeAmplifierFluid} 按秒预检实扣，
 * 预检失败零扣、该模块当秒无增益无惩罚。
 * <p>
 * 锁定流体的接受点为 {@link #isFluidInputAllowed}——MTEBasicTank.fill 入口即调用该钩子，
 * 管道/ME/倒容器（onPreTick）全部填充路径统一经它放行；drain 走基类 mFluid 语义正常放出，无需覆写。
 * 手工容器交互（桶/胶囊）沿承 {@link MTEClusterUnitBase} 家族语义（关闭），流体出入一律走管道/ME。
 * <p>
 * 锁定流体解析表（集中在本类 {@link #resolveBoosterFluid}，null 安全）：
 * <ul>
 * <li>PARALLEL（并行增幅）→ 硝酸 {@code Materials.NitricAcid.getFluid(1).getFluid()}
 * <li>SPEED（速度增幅）→ 盐酸 {@code Materials.HydrochloricAcid.getFluid(1).getFluid()}
 * <li>PRIMARY_OUTPUT（主产物增幅）→ 氨气（气态）{@code Materials.Ammonia.getGas(1).getFluid()}
 * <li>SECONDARY_OUTPUT（副产物增幅）→ 硫酸 {@code Materials.SulfuricAcid.getFluid(1).getFluid()}
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
        enableInternalFluidTank();
    }

    protected MTEBasicAmplifierUnit(String aName, ClusterParams.BoosterType type) {
        super(aName);
        this.boosterType = type;
        enableInternalFluidTank();
    }

    @Override
    protected String[][] getUnitShape() {
        // canonical [Z][Y][X]，与草稿「基本增幅单元-修.java」（层在前 + transpose）零差异；
        // 唯一改绑：正面 (0,3,0)（z=0, y=3, x=0，控制器 (1,4,0) 左下一格）'B' → 'H' 输入仓
        return new String[][] { { "AAA", "ACA", "ACA", "HAB", "B~B", "BAB", "ACA", "ACA", "AAA" },
            { "AAA", "C-C", "C-C", "C-C", "C-C", "C-C", "C-C", "CDC", "BBB" },
            { "AAA", "ACA", "ACA", "BCB", "BCB", "BCB", "ACA", "ACA", "AAA" }, };
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected void addUnitStructureElements(StructureDefinition.Builder builder) {
        addPipeElement(builder);
        builder
            .addElement(
                'C',
                com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock(GregTechAPI.sBlockGlass1, 10))
            .addElement('D', com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock(Blocks.stone, 0))
            .addElement('-', com.gtnewhorizon.structurelib.structure.StructureUtility.isAir())
            // H=增幅液输入仓：标准 InputHatch hatchAdder（不与 tiered casing 混挂），
            // 收集进本模块 mInputHatches；hint 底材沿用青铜外壳索引（与集群现有挂点口径一致）
            .addElement(
                'H',
                buildHatchAdder(MTEBasicAmplifierUnit.class).atLeast(InputHatch)
                    .casingIndex(HATCH_HINT_CASING_INDEX)
                    .hint(1)
                    .build());
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

    /**
     * 服务端成型后自动补液：从 H 输入仓抽取本模块锁定流体填入内置 mFluid 缓冲。tank 满时 O(1) 早退
     * （稳态零探测开销）；探测/实扣统一走 {@link GTSRHatchFluidAccess} 模拟→实扣两段式，兼容 ME 输入仓。
     */
    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide() && mMachine) pullBoosterFluidFromInputHatches();
    }

    /** 输入仓 → mFluid 补液：只取锁定流体，量 = tank 剩余容量；fill 侧再经 isFluidInputAllowed 双保险。 */
    private void pullBoosterFluidFromInputHatches() {
        if (mInputHatches.isEmpty()) return;
        int room = getCapacity() - (mFluid == null ? 0 : mFluid.amount);
        if (room <= 0) return;
        Fluid locked = getLockedFluidOrNull();
        if (locked == null || (mFluid != null && mFluid.getFluid() != locked)) return;
        int drained = GTSRHatchFluidAccess.depleteFluidAcross(mInputHatches, new FluidStack(locked, room));
        if (drained > 0) fill(new FluidStack(locked, drained), true);
    }

    @Override
    public abstract IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity);

    /**
     * E4 结算冻结接口：本模块每秒增幅液消耗（L/s），经
     * {@code ClusterParams.AMPLIFIER_FLUID_PER_SEC[单元已验证结构 tier]} 取值
     * （T1/T2/T3/T4 = 4/8/12/16 L/s，五类增幅共用；tier 无效——未成型/越界——返回 0）。
     */
    public int amplifierFluidPerSec() {
        int tier = getUnitStructureTier();
        if (tier < 0 || tier >= ClusterParams.TIER_COUNT) return 0;
        return ClusterParams.AMPLIFIER_FLUID_PER_SEC[tier];
    }

    /**
     * E4 结算冻结接口：预检 mFluid 足量才实扣；不足（或参数非正）返回 false 且零扣——
     * 该模块当秒应从 BoosterState 剔除（无增益、无蒸汽惩罚乘子），剔除动作由 E4 结算侧完成。
     */
    public boolean tryConsumeAmplifierFluid(int liters) {
        if (liters <= 0 || !hasBoosterFluid() || mFluid.amount < liters) return false;
        mFluid.amount -= liters;
        if (mFluid.amount <= 0) mFluid = null;
        markDirty();
        return true;
    }

    /**
     * 便捷判据：mFluid 含锁定流体且 ≥ 1 mB。增幅「在供流体」的具体识别由主控拓扑层判，
     * 本方法只提供 tank 事实快照。
     */
    public boolean hasBoosterFluid() {
        Fluid locked = getLockedFluidOrNull();
        return locked != null && mFluid != null && mFluid.getFluid() == locked && mFluid.amount >= 1;
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

    /** tank 非空、单元结构已成型且流体==锁定流体时视为增幅流体可用。 */
    public boolean isFluidAvailable() {
        if (!isModuleEnabled() || mFluid == null || mFluid.amount <= 0) return false;
        Fluid locked = getLockedFluidOrNull();
        return locked != null && mFluid.getFluid() == locked;
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

    /** tank 当前内容（live 视图，可为 null）。 */
    public FluidStack getTankContent() {
        return mFluid;
    }

    /** 增幅锁定流体 tank 容量（结构 tier 不改变容量）。 */
    @Override
    public int getCapacity() {
        return ClusterParams.BOOSTER_TANK_CAPACITY_L;
    }

    /** 锁定流体闸门：fill/倒容器全路径统一入口；解析失败（null）时恒 false → 增幅禁用但不崩。 */
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
     * 锁定流体集中解析（null 安全）：Materials 取流体经 {@code getFluid(1).getFluid()}（氨气为气态走
     * {@code getGas(1).getFluid()}），任一环节未注册返回 null；蒸汽效率增幅先查配置注册名冷却液，缺失回退超冷却液。
     */
    protected static Fluid resolveBoosterFluid(ClusterParams.BoosterType type) {
        if (type == null) return null;
        if (type == ClusterParams.BoosterType.PARALLEL) return toFluid(Materials.NitricAcid.getFluid(1));
        if (type == ClusterParams.BoosterType.SPEED) return toFluid(Materials.HydrochloricAcid.getFluid(1));
        if (type == ClusterParams.BoosterType.PRIMARY_OUTPUT) return toFluid(Materials.Ammonia.getGas(1));
        if (type == ClusterParams.BoosterType.SECONDARY_OUTPUT) return toFluid(Materials.SulfuricAcid.getFluid(1));
        if (type == ClusterParams.BoosterType.STEAM_SAVER) {
            Fluid coolant = FluidRegistry.getFluid(ClusterParams.BOOSTER_COOLANT_FLUID);
            return coolant != null ? coolant : toFluid(Materials.SuperCoolant.getFluid(1));
        }
        return null;
    }

    private static Fluid toFluid(FluidStack aStack) {
        return aStack != null ? aStack.getFluid() : null;
    }
}
