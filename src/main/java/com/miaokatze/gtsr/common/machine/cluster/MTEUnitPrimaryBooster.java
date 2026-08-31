package com.miaokatze.gtsr.common.machine.cluster;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;

import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * 主产物增幅模块：按概率令单物品额外 +1 主产物；同类多模块时只生效 1 个（取其一，不叠加）。
 * <p>
 * 增益档位（结构 tier）：T1 +5% / T2 +10% / T3 +15% / T4 +20%（+1 主产物概率）。
 * 工作模块复数只减 link 时间，不参与主产物增幅；蒸汽惩罚乘子见 {@link ClusterParams.BoosterType#PRIMARY_OUTPUT}。
 * <p>
 * 锁定流体：硫酸（SulfuricAcid，{@code Materials.SulfuricAcid}）。缺流体时本模块增益失效
 * （状态=缺增幅流体紫）；类型名 key：gtsr.gui.cluster.unit_type.booster.primary。
 * <p>
 * 正面 overlay 引用蒸馏塔（distillery）原机资源（gregtech 域，无 BlockIcons 常量，走 customOptional）；
 * 类加载期（贴图缝合前）以 static final 字段绑定，禁止移入方法体。
 */
public class MTEUnitPrimaryBooster extends MTEBasicAmplifierUnit {

    /** 正面 overlay（inactive）：{@code basicmachines/distillery/OVERLAY_FRONT}。 */
    private static final IIconContainer OVERLAY_INACTIVE = Textures.BlockIcons
        .customOptional("basicmachines/distillery/OVERLAY_FRONT");

    /** 正面 overlay（active）：{@code basicmachines/distillery/OVERLAY_FRONT_ACTIVE}。 */
    private static final IIconContainer OVERLAY_ACTIVE = Textures.BlockIcons
        .customOptional("basicmachines/distillery/OVERLAY_FRONT_ACTIVE");

    public MTEUnitPrimaryBooster(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, ClusterParams.BoosterType.PRIMARY_OUTPUT);
    }

    public MTEUnitPrimaryBooster(String aName) {
        super(aName, ClusterParams.BoosterType.PRIMARY_OUTPUT);
    }

    @Override
    protected String[][] getUnitShape() {
        return new String[][] {
            { "     ", "D   D", "D   D", "DD DD", "DD DD", "DD DD", " D D ", "     ", "     ", "     " },
            { " DDD ", " BCB ", " BEB ", "DBEBD", "DBABD", "DB~BD", "DBABD", " AEA ", " AEA ", " AAA " },
            { " DDD ", " CBC ", " E-E ", " E-E ", " E-E ", " E-E ", " E-E ", " E-E ", " EeE ", " CCC " },
            { " DDD ", " BCB ", " BEB ", "DBEBD", "DBEBD", "DBEBD", "DBEBD", " AEA ", " AEA ", " AAA " },
            { "     ", "D   D", "D   D", "DD DD", "DD DD", "DD DD", " D D ", "     ", "     ", "     " }, };
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected void addUnitStructureElements(StructureDefinition.Builder builder) {
        builder.addElement('B', tieredGearboxElement())
            .addElement('C', tieredPipeElement())
            .addElement('D', tieredFrameElement())
            .addElement('E', glassElement())
            .addElement('-', airElement())
            .addElement('e', airElement());
    }

    @Override
    protected int getStructureOffsetA() {
        return 2;
    }

    @Override
    protected int getStructureOffsetB() {
        return 5;
    }

    @Override
    protected int getStructureOffsetC() {
        return 1;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitPrimaryBooster(mName);
    }

    /** 锁定流体=硫酸（解析集中在上层 {@link #resolveBoosterFluid}，null 安全）。 */
    @Override
    protected Fluid resolveLockedFluid() {
        return resolveBoosterFluid(ClusterParams.BoosterType.PRIMARY_OUTPUT);
    }

    @Override
    protected IIconContainer unitOverlayInactive() {
        return OVERLAY_INACTIVE;
    }

    @Override
    protected IIconContainer unitOverlayActive() {
        return OVERLAY_ACTIVE;
    }

    /** 工序主色（计划 §2.2）：GREEN 系。 */
    @Override
    protected EnumChatFormatting getUnitDescColor() {
        return EnumChatFormatting.GREEN;
    }

    /** 单元描述键（v1.11.15 W1 修正）：主产物增幅模块专属描述行。 */
    @Override
    protected String getUnitDescKey() {
        return "gtsr.tooltip.cluster.unit.primary.desc";
    }

    /**
     * 功能群（v1.11.15）：主产物四档值行 + 共用「锁定流体 / 按档消耗」行 + 共用「蒸汽惩罚」行——
     * 数值取自 {@link ClusterParams#BOOSTER_PRIMARY_PCT}（经 getBoosterValue）、
     * {@link ClusterParams#AMPLIFIER_SULFURIC_ACID_LPS}（经 amplifierFluidLps；代码语义
     * resolveBoosterFluid(PRIMARY_OUTPUT)=硫酸，lang fluid.primary 值已同步校正）与
     * 无蒸汽惩罚。
     */
    @Override
    protected void addUnitTooltipInfo(MultiblockTooltipBuilder tt) {
        ClusterParams.BoosterType type = ClusterParams.BoosterType.PRIMARY_OUTPUT;
        tt.addInfo(
            EnumChatFormatting.YELLOW + String.format(
                StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.primary.value"),
                gold(boosterTierValues(type, "%"))))
            .addInfo(
                EnumChatFormatting.YELLOW + String.format(
                    StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.booster.fluid_cost"),
                    EnumChatFormatting.WHITE + StatCollector.translateToLocal(type.getFluidLangKey()),
                    red(boosterTierLps(type))))
            .addInfo(
                EnumChatFormatting.YELLOW
                    + StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.booster.penalty.none"))
            .addInfo(
                EnumChatFormatting.YELLOW + String.format(
                    StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.primary.link_receive"),
                    gold(joinSurchargeTierValues())));
    }

    /** 仓室群（v1.11.15）：增幅输入仓行（锁定增幅流体自输入仓读取，≥1 由结构校验强制）。 */
    @Override
    protected void addUnitStructureTooltipInfo(MultiblockTooltipBuilder tt) {
        tt.addStructureInfo(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.hatch.input"));
    }
}
