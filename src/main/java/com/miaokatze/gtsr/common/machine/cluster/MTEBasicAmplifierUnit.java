package com.miaokatze.gtsr.common.machine.cluster;

import net.minecraft.init.Blocks;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 增幅模块基类：集群五类增幅（并行/速度/主产物/副产物/蒸汽效率）的公共骨架。
 * <p>
 * 每个增幅模块自带一个只接受「锁定流体」的内置 tank（容量 {@link ClusterParams#BOOSTER_TANK_CAPACITY_L}），
 * 由玩家自接供给：锁定流体充足时按结构 tier 贡献增益，缺流体时该模块增益失效（不崩、不影响结构成型）。
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
        return new String[][] { { "AAA", "ACA", "ACA", "BAB", "B~B", "BAB", "ACA", "ACA", "AAA" },
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
            .addElement('-', com.gtnewhorizon.structurelib.structure.StructureUtility.isAir());
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

    @Override
    public abstract IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity);

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
