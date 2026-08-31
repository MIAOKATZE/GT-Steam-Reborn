package com.miaokatze.gtsr.common.machine.cluster;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * 工作单元：熔炉（能力闸门）。
 *
 * <p>
 * 仅声明解锁的 ChainLink（熔炼 FURNACE），自身零配方执行；配方匹配与执行由集群总控侧完成。
 * 纹理、集群接线与流体缓冲等公共行为全部继承自 MTEBasicProcessingUnit/MTEClusterUnitBase；
 * overlay 取 GT5U 蒸汽熔炉前脸 inactive/active（同 GTSR 大型蒸汽熔炉 MTELargeSteamFurnace 绑定）。
 *
 * <p>
 * 结构（r9 权威规格，5×6×5 canonical [Z][Y][X]，控制器 (2,4,0)）：'e'×9 粒子候选空气位
 * （x1-3, y0, z1-3）；A=外壳族（基类绑定）、C=管道族、D=燃烧室族（与集群总控同族，
 * {@code tieredFireboxElement}）、E=框架族、F=玻璃、'e'=严格空气。D/E/F 三族经基类
 * resolveUnitStructureTier 与其他参与族同级强校验。
 */
public class MTEUnitFurnace extends MTEBasicProcessingUnit {

    /** GUI 类型词条 key。 */
    private static final String TYPE_NAME_KEY = "gtsr.gui.cluster.unit_type.furnace";

    /** 本单元解锁的链路：熔炼。 */
    private static final ChainLink[] PROVIDED_LINKS = { ChainLink.FURNACE };

    /** 注册用构造器。 */
    public MTEUnitFurnace(int aID, String aName, String aNameRegional) {
        super(
            aID,
            aName,
            aNameRegional,
            TYPE_NAME_KEY,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE_ACTIVE,
            PROVIDED_LINKS);
    }

    /** 克隆用构造器（newMetaEntity 落点）。 */
    public MTEUnitFurnace(String aName) {
        super(
            aName,
            TYPE_NAME_KEY,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE_ACTIVE,
            PROVIDED_LINKS);
    }

    /** 结构矩阵（canonical [Z][Y][X]，z0=正面；'~' 位于 (2,4,0)）。 */
    @Override
    protected String[][] getUnitShape() {
        return new String[][] { { " EEE ", " AAA ", "EAAAE", "EAAAE", "EA~AE", "EAAAE" },
            { "EeeeE", "ACCCA", "FFFFF", "FFFFF", "DDDDD", "ABCBA" },
            { "EeeeE", "ACCCA", "F---F", "F---F", "DDDDD", "ACCCA" },
            { "EeeeE", "ACCCA", "FFFFF", "FFFFF", "DDDDD", "ABCBA" },
            { " EEE ", " AAA ", "EAAAE", "EAAAE", "EAAAE", "EAAAE" }, };
    }

    /** 专有结构元素：B=齿轮箱族、C=管道族、D=燃烧室族、E=框架族、F=玻璃、'-'与'e'=严格空气。 */
    @Override
    @SuppressWarnings("rawtypes")
    protected void addUnitStructureElements(StructureDefinition.Builder builder) {
        builder.addElement('B', tieredGearboxElement())
            .addElement('C', tieredPipeElement())
            .addElement('D', tieredFireboxElement())
            .addElement('E', tieredFrameElement())
            .addElement('F', glassElement())
            .addElement('-', airElement())
            .addElement('e', airElement());
    }

    @Override
    protected int getStructureOffsetA() {
        return 2;
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
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitFurnace(mName);
    }

    /** 工序主色（计划 §2.2）：YELLOW。 */
    @Override
    protected EnumChatFormatting getUnitDescColor() {
        return EnumChatFormatting.YELLOW;
    }

    /** 单元描述键（v1.11.15 W1 修正）：熔炼模块专属描述行。 */
    @Override
    protected String getUnitDescKey() {
        return "gtsr.tooltip.cluster.unit.furnace.desc";
    }

    /** 功能群（v1.11.15）：熔炼链步「耗时 / 蒸汽消耗」行（数据源 {@link ChainLink#FURNACE}）。 */
    @Override
    protected void addUnitTooltipInfo(MultiblockTooltipBuilder tt) {
        super.addUnitTooltipInfo(tt);
        tt.addInfo(
            EnumChatFormatting.YELLOW + String.format(
                StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.furnace.func"),
                linkSeconds(ChainLink.FURNACE),
                linkSteam(ChainLink.FURNACE)));
    }
}
