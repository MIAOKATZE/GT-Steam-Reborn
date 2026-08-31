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
 * 速度增幅模块：配方增速（百分比，不减小实际秒数），可多模块生效、加算后作为独立乘算层。
 * <p>
 * 增益档位（结构 tier）：T1 +5% / T2 +10% / T3 +30% / T4 +40%。
 * 与 tier 时间因子（减小实际秒数）相乘缩短总耗时；蒸汽惩罚乘子见 {@link ClusterParams.BoosterType#SPEED}。
 * <p>
 * 锁定流体：盐酸（HydrochloricAcid，{@code Materials.HydrochloricAcid}）。缺流体时本模块增益失效
 * （状态=缺增幅流体紫）；类型名 key：gtsr.gui.cluster.unit_type.booster.speed。
 * <p>
 * 正面 overlay 引用装配机（assembler）原机资源（gregtech 域，无 BlockIcons 常量，走 customOptional）；
 * 类加载期（贴图缝合前）以 static final 字段绑定，禁止移入方法体。
 */
public class MTEUnitSpeedBooster extends MTEBasicAmplifierUnit {

    /** 正面 overlay（inactive）：{@code basicmachines/assembler/OVERLAY_FRONT}。 */
    private static final IIconContainer OVERLAY_INACTIVE = Textures.BlockIcons
        .customOptional("basicmachines/assembler/OVERLAY_FRONT");

    /** 正面 overlay（active）：{@code basicmachines/assembler/OVERLAY_FRONT_ACTIVE}。 */
    private static final IIconContainer OVERLAY_ACTIVE = Textures.BlockIcons
        .customOptional("basicmachines/assembler/OVERLAY_FRONT_ACTIVE");

    public MTEUnitSpeedBooster(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, ClusterParams.BoosterType.SPEED);
    }

    public MTEUnitSpeedBooster(String aName) {
        super(aName, ClusterParams.BoosterType.SPEED);
    }

    @Override
    protected String[][] getUnitShape() {
        return new String[][] {
            { "     ", "C   C", "C   C", "CC CC", "CC CC", "CC CC", " C C ", " C C ", "     ", "     " },
            { "  C  ", " CBA ", " ADA ", "CADAC", "CBABC", "BB~BB", "BBABB", "BADAB", " ADA ", " AAA " },
            { " CCC ", " BAB ", " D-D ", " D-D ", " D-D ", " D-D ", " D-D ", " D-D ", " DeD ", " BBB " },
            { "  C  ", " CAC ", " ADA ", "CADAC", "CBDBC", "BBDBB", "BBDBB", "BADAB", " ADA ", " AAA " },
            { "     ", "C   C", "C   C", "CC CC", "CC CC", "CC CC", " C C ", " C C ", "     ", "     " }, };
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
        return new MTEUnitSpeedBooster(mName);
    }

    /** 锁定流体=盐酸（解析集中在上层 {@link #resolveBoosterFluid}，null 安全）。 */
    @Override
    protected Fluid resolveLockedFluid() {
        return resolveBoosterFluid(ClusterParams.BoosterType.SPEED);
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

    /** 单元描述键（v1.11.15 W1 修正）：速度增幅模块专属描述行。 */
    @Override
    protected String getUnitDescKey() {
        return "gtsr.tooltip.cluster.unit.speed.desc";
    }

    /**
     * 功能群（v1.11.15）：速度四档值行 + 共用「锁定流体 / 按档消耗」行 + 共用「蒸汽惩罚」行——
     * 数值取自 {@link ClusterParams#BOOSTER_SPEED_PCT}（经 getBoosterValue）、
     * {@link ClusterParams#AMPLIFIER_HYDROCHLORIC_ACID_LPS}（经 amplifierFluidLps）与
     * {@link ClusterParams#BOOSTER_STRUCTURE_PENALTY_MULT}，按结构档位逐台连乘，Java 侧 GOLD/RED 注入。
     */
    @Override
    protected void addUnitTooltipInfo(MultiblockTooltipBuilder tt) {
        ClusterParams.BoosterType type = ClusterParams.BoosterType.SPEED;
        tt.addInfo(
            EnumChatFormatting.YELLOW + String.format(
                StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.speed.value"),
                gold(boosterTierValues(type, "%"))))
            .addInfo(
                EnumChatFormatting.YELLOW + String.format(
                    StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.booster.fluid_cost"),
                    EnumChatFormatting.WHITE + StatCollector.translateToLocal(type.getFluidLangKey()),
                    red(boosterTierLps(type))))
            .addInfo(
                EnumChatFormatting.YELLOW + String.format(
                    StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.booster.penalty"),
                    gold(boosterPenaltyTierValues())))
            .addInfo(
                EnumChatFormatting.YELLOW + String.format(
                    StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.speed.link_apply"),
                    gold(joinSurchargeTierValues())));
    }

    /** 仓室群（v1.11.15）：增幅输入仓行（锁定增幅流体自输入仓读取，≥1 由结构校验强制）。 */
    @Override
    protected void addUnitStructureTooltipInfo(MultiblockTooltipBuilder tt) {
        tt.addStructureInfo(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.hatch.input"));
    }
}
