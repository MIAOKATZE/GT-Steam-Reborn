package com.miaokatze.gtsr.common.machine.cluster;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * 副产物增幅模块：按概率额外产出副产物；可多模块生效、加算。
 * <p>
 * 增益档位（结构 tier）：T1 +1% / T2 +2% / T3 +4% / T4 +5%（+副产物概率）。
 * links 串联可能指数叠加，数值刻意压低；蒸汽惩罚乘子见 {@link ClusterParams.BoosterType#SECONDARY_OUTPUT}。
 * <p>
 * 锁定流体：氯化铵（AmmoniumChloride，Werkstoff 材料，{@code WerkstoffLoader.AmmoniumChloride}）。缺流体时本模块增益失效
 * （状态=缺增幅流体紫）；类型名 key：gtsr.gui.cluster.unit_type.booster.secondary。
 * <p>
 * 正面 overlay 引用流体加热器（fluid_heater）原机资源（gregtech 域，无 BlockIcons 常量，走 customOptional；
 * 该目录无 GLOW 变体，仅 inactive/active 两组）；类加载期（贴图缝合前）以 static final 字段绑定，禁止移入方法体。
 */
public class MTEUnitSecondaryBooster extends MTEBasicAmplifierUnit {

    /** 正面 overlay（inactive）：{@code basicmachines/fluid_heater/OVERLAY_FRONT}。 */
    private static final IIconContainer OVERLAY_INACTIVE = Textures.BlockIcons
        .customOptional("basicmachines/fluid_heater/OVERLAY_FRONT");

    /** 正面 overlay（active）：{@code basicmachines/fluid_heater/OVERLAY_FRONT_ACTIVE}。 */
    private static final IIconContainer OVERLAY_ACTIVE = Textures.BlockIcons
        .customOptional("basicmachines/fluid_heater/OVERLAY_FRONT_ACTIVE");

    public MTEUnitSecondaryBooster(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, ClusterParams.BoosterType.SECONDARY_OUTPUT);
    }

    public MTEUnitSecondaryBooster(String aName) {
        super(aName, ClusterParams.BoosterType.SECONDARY_OUTPUT);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitSecondaryBooster(mName);
    }

    /** 锁定流体=氯化铵（解析集中在上层 {@link #resolveBoosterFluid}，null 安全）。 */
    @Override
    protected Fluid resolveLockedFluid() {
        return resolveBoosterFluid(ClusterParams.BoosterType.SECONDARY_OUTPUT);
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

    /** 单元描述键（v1.11.15 W1 修正）：副产物增幅模块专属描述行。 */
    @Override
    protected String getUnitDescKey() {
        return "gtsr.tooltip.cluster.unit.secondary.desc";
    }

    /**
     * 功能群（v1.11.15）：副产物四档值行 + 共用「锁定流体 / 按档消耗」行 + 共用「蒸汽惩罚」行——
     * 数值取自 {@link ClusterParams#BOOSTER_SECONDARY_PCT}（经 getBoosterValue）、
     * {@link ClusterParams#AMPLIFIER_AMMONIUM_CHLORIDE_LPS}（经 amplifierFluidLps；代码语义
     * resolveBoosterFluid(SECONDARY_OUTPUT)=氯化铵，lang fluid.secondary 值已同步校正）与
     * {@link ClusterParams#BOOSTER_PENALTY_MULT}（经 getPenaltyMultiplier）。
     */
    @Override
    protected void addUnitTooltipInfo(MultiblockTooltipBuilder tt) {
        ClusterParams.BoosterType type = ClusterParams.BoosterType.SECONDARY_OUTPUT;
        tt.addInfo(
            EnumChatFormatting.YELLOW + String.format(
                StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.secondary.value"),
                gold(boosterTierValues(type, "%"))))
            .addInfo(
                EnumChatFormatting.YELLOW + String.format(
                    StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.booster.fluid_cost"),
                    EnumChatFormatting.WHITE + StatCollector.translateToLocal(type.getFluidLangKey()),
                    red(boosterTierLps(type))))
            .addInfo(
                EnumChatFormatting.YELLOW + String.format(
                    StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.booster.penalty"),
                    gold(String.format("%.1f", type.getPenaltyMultiplier()))));
    }

    /** 仓室群（v1.11.15）：增幅输入仓行（锁定增幅流体自输入仓读取，≥1 由结构校验强制）。 */
    @Override
    protected void addUnitStructureTooltipInfo(MultiblockTooltipBuilder tt) {
        tt.addStructureInfo(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.hatch.input"));
    }
}
