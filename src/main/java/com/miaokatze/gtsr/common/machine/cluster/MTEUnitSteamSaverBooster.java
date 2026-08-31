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
 * 蒸汽效率增幅模块：按比例节约集群运行蒸汽消耗；可多模块生效、加算，全集群总节约上限 48%。
 * <p>
 * 增益档位（结构 tier）：T1 -2% / T2 -4% / T3 -8% / T4 -12%（运行蒸汽）。
 * 总蒸汽公式中作为 (1 − min(48%, Σ节汽%)) 乘算层；惩罚乘子见 {@link ClusterParams.BoosterType#STEAM_SAVER}。
 * <p>
 * 锁定流体：冷却液——优先取 {@code FluidRegistry.getFluid(ClusterParams.BOOSTER_COOLANT_FLUID)} 注册名，
 * 缺失时回退超冷却液（SuperCoolant）；两者都不可用时本模块增幅禁用（isFluidAvailable 恒 false，不崩）。
 * 类型名 key：gtsr.gui.cluster.unit_type.booster.steam_saver。
 * <p>
 * 正面 overlay 引用离心机（centrifuge）原机资源（gregtech 域，无 BlockIcons 常量，走 customOptional；
 * 该目录无 GLOW 变体，仅 inactive/active 两组）；类加载期（贴图缝合前）以 static final 字段绑定，禁止移入方法体。
 */
public class MTEUnitSteamSaverBooster extends MTEBasicAmplifierUnit {

    /** 正面 overlay（inactive）：{@code basicmachines/centrifuge/OVERLAY_FRONT}。 */
    private static final IIconContainer OVERLAY_INACTIVE = Textures.BlockIcons
        .customOptional("basicmachines/centrifuge/OVERLAY_FRONT");

    /** 正面 overlay（active）：{@code basicmachines/centrifuge/OVERLAY_FRONT_ACTIVE}。 */
    private static final IIconContainer OVERLAY_ACTIVE = Textures.BlockIcons
        .customOptional("basicmachines/centrifuge/OVERLAY_FRONT_ACTIVE");

    public MTEUnitSteamSaverBooster(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, ClusterParams.BoosterType.STEAM_SAVER);
    }

    public MTEUnitSteamSaverBooster(String aName) {
        super(aName, ClusterParams.BoosterType.STEAM_SAVER);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitSteamSaverBooster(mName);
    }

    /** 锁定流体=冷却液（注册名可配，回退 SuperCoolant；解析集中在上层 {@link #resolveBoosterFluid}）。 */
    @Override
    protected Fluid resolveLockedFluid() {
        return resolveBoosterFluid(ClusterParams.BoosterType.STEAM_SAVER);
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

    /** 单元描述键（v1.11.15 W1 修正）：节汽增幅模块专属描述行。 */
    @Override
    protected String getUnitDescKey() {
        return "gtsr.tooltip.cluster.unit.steam_saver.desc";
    }

    /**
     * 功能群（v1.11.15）：节汽四档值行（含全集群总节约上限）+ 共用「锁定流体 / 按档消耗」行 +
     * 共用「蒸汽惩罚」行——数值取自 {@link ClusterParams#BOOSTER_SAVER_PCT}（经 getBoosterValue）、
     * {@link ClusterParams#STEAM_SAVER_CAP}、{@link ClusterParams#AMPLIFIER_SUPER_COOLANT_LPS}
     * （经 amplifierFluidLps）与 {@link ClusterParams#BOOSTER_PENALTY_MULT}（经 getPenaltyMultiplier）。
     */
    @Override
    protected void addUnitTooltipInfo(MultiblockTooltipBuilder tt) {
        ClusterParams.BoosterType type = ClusterParams.BoosterType.STEAM_SAVER;
        tt.addInfo(
            EnumChatFormatting.YELLOW + String.format(
                StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.steam_saver.value"),
                gold(boosterTierValues(type, "%")),
                gold(String.format("%.0f %%", ClusterParams.STEAM_SAVER_CAP * 100))))
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
