package com.miaokatze.gtsr.common.machine.cluster;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;

import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtPlusPlus.xmod.gregtech.common.blocks.textures.TexturesGtBlock;

/**
 * 并行增幅模块：把增益叠加到所接入物流模块的并行数上，可多模块生效、加算。
 * <p>
 * 增益档位（结构 tier）：T1 +4 / T2 +8 / T3 +24 / T4 +48（每批并行物品数）。
 * 生效前提与蒸汽惩罚乘子见 {@link ClusterParams.BoosterType#PARALLEL}。
 * <p>
 * 锁定流体：硝酸（NitricAcid，{@code Materials.NitricAcid}）。tank 缺流体时本模块增益失效
 * （状态=缺增幅流体紫），不影响集群结构成型；类型名 key：gtsr.gui.cluster.unit_type.booster.parallel。
 * <p>
 * 正面 overlay 忠实跨域引用 GT++ 亚马逊包装机资源（miscutils 域 amazonPackager*，不复制 PNG）；
 * 变体选择对齐 MTEAmazonPackagerLegacy：inactive=oMCAAmazonPackager、active=oMCAAmazonPackagerActive。
 */
public class MTEUnitParallelBooster extends MTEBasicAmplifierUnit {

    /** 正面 overlay（inactive）：GT++ {@code TexturesGtBlock.oMCAAmazonPackager}。类加载期绑定，禁止移入方法体。 */
    private static final IIconContainer OVERLAY_INACTIVE = TexturesGtBlock.oMCAAmazonPackager;

    /** 正面 overlay（active）：GT++ {@code TexturesGtBlock.oMCAAmazonPackagerActive}。类加载期绑定。 */
    private static final IIconContainer OVERLAY_ACTIVE = TexturesGtBlock.oMCAAmazonPackagerActive;

    public MTEUnitParallelBooster(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, ClusterParams.BoosterType.PARALLEL);
    }

    public MTEUnitParallelBooster(String aName) {
        super(aName, ClusterParams.BoosterType.PARALLEL);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitParallelBooster(mName);
    }

    /** 锁定流体=硝酸（解析集中在上层 {@link #resolveBoosterFluid}，null 安全）。 */
    @Override
    protected Fluid resolveLockedFluid() {
        return resolveBoosterFluid(ClusterParams.BoosterType.PARALLEL);
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

    /** 单元描述键（v1.11.15 W1 修正）：并行增幅模块专属描述行。 */
    @Override
    protected String getUnitDescKey() {
        return "gtsr.tooltip.cluster.unit.parallel.desc";
    }

    /**
     * 功能群（v1.11.15）：并行四档值行 + 共用「锁定流体 / 按档消耗」行 + 共用「蒸汽惩罚」行——
     * 数值取自 {@link ClusterParams#BOOSTER_PARALLEL_VALUES}（经 getBoosterValue）、
     * {@link ClusterParams#AMPLIFIER_NITRIC_ACID_LPS}（经 amplifierFluidLps）与
     * {@link ClusterParams#BOOSTER_PENALTY_MULT}（经 getPenaltyMultiplier），Java 侧 GOLD/RED 注入。
     */
    @Override
    protected void addUnitTooltipInfo(MultiblockTooltipBuilder tt) {
        ClusterParams.BoosterType type = ClusterParams.BoosterType.PARALLEL;
        tt.addInfo(
            EnumChatFormatting.YELLOW + String.format(
                StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.parallel.value"),
                gold(boosterTierValues(type, ""))))
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
