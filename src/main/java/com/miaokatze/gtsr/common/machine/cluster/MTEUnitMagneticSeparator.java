package com.miaokatze.gtsr.common.machine.cluster;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * 工作单元：磁选机（能力闸门 + 自持能源）。
 *
 * <p>
 * 仅声明解锁的 ChainLink（磁选 MAGNETIC_SEPARATOR），自身零配方执行；配方匹配与执行由集群总控侧
 * 完成。集群接线与流体缓冲等公共行为全部继承自 MTEClusterUnitBase；自持能源语义（P 能源位/
 * 成型校验/EU 探测/断电闸门）全部继承自 {@link MTEUnitSelfPoweredProcessingUnit}。
 *
 * <p>
 * overlay：GT5U 电磁选矿机前脸 inactive/active（四态常量存在，绑定前两态，glow 由基类统一）。
 *
 * <p>
 * 结构（r9 权威规格，5×5×5 canonical [Z][Y][X]，控制器 (2,3,0)）：无 'e'（不产粒子候选）；
 * 'b'=金属族（{@code tieredMetalElement}，铁/钢/钕/钐四档）；'P'×1 能源位位于 (2,3,4)——控制器
 * 同列同层最深行；A=外壳族（基类绑定）、B=齿轮箱族、C=管道族、D=框架族、E=玻璃、'-'=严格空气。
 */
public class MTEUnitMagneticSeparator extends MTEUnitSelfPoweredProcessingUnit {

    /** GUI 类型词条 key。 */
    private static final String TYPE_NAME_KEY = "gtsr.gui.cluster.unit_type.magnetic_separator";

    /** 本单元解锁的链路：磁选。 */
    private static final ChainLink[] PROVIDED_LINKS = { ChainLink.MAGNETIC_SEPARATOR };

    /** 注册用构造器。 */
    public MTEUnitMagneticSeparator(int aID, String aName, String aNameRegional) {
        super(
            aID,
            aName,
            aNameRegional,
            TYPE_NAME_KEY,
            Textures.BlockIcons.OVERLAY_FRONT_EMS,
            Textures.BlockIcons.OVERLAY_FRONT_EMS_ACTIVE,
            PROVIDED_LINKS);
    }

    /** 克隆用构造器（newMetaEntity 落点）。 */
    public MTEUnitMagneticSeparator(String aName) {
        super(
            aName,
            TYPE_NAME_KEY,
            Textures.BlockIcons.OVERLAY_FRONT_EMS,
            Textures.BlockIcons.OVERLAY_FRONT_EMS_ACTIVE,
            PROVIDED_LINKS);
    }

    /** 结构矩阵（canonical [Z][Y][X]，z0=正面；'~' 位于 (2,3,0)，'P' 位于 (2,3,4)）。 */
    @Override
    protected String[][] getUnitShape() {
        return new String[][] { { " AAA ", "DAAAD", "DAAAD", "DA~AD", "DAAAD" },
            { "AEEEA", "EbbbE", "EbbbE", "EbbbE", "ABCBA" }, { "AEEEA", "E---E", "E---E", "E---E", "ACCCA" },
            { "AEEEA", "EbbbE", "EbbbE", "EbbbE", "ABCBA" }, { " AAA ", "DAAAD", "DAAAD", "DAPAD", "DAAAD" }, };
    }

    /** 专有结构元素：b=金属族、B=齿轮箱族、C=管道族、D=框架族、E=玻璃、'-'=严格空气（P 由父类注入）。 */
    @Override
    @SuppressWarnings("rawtypes")
    protected void addUnitStructureElements(StructureDefinition.Builder builder) {
        super.addUnitStructureElements(builder);
        builder.addElement('b', tieredMetalElement())
            .addElement('B', tieredGearboxElement())
            .addElement('C', tieredPipeElement())
            .addElement('D', tieredFrameElement())
            .addElement('E', glassElement())
            .addElement('-', airElement());
    }

    @Override
    protected int getStructureOffsetA() {
        return 2;
    }

    @Override
    protected int getStructureOffsetB() {
        return 3;
    }

    @Override
    protected int getStructureOffsetC() {
        return 0;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitMagneticSeparator(mName);
    }

    /** 工序主色（计划 §2.2）：DARK_AQUA。 */
    @Override
    protected EnumChatFormatting getUnitDescColor() {
        return EnumChatFormatting.DARK_AQUA;
    }

    /** 单元描述键（v1.11.15 W1 修正）：磁选模块专属描述行。 */
    @Override
    protected String getUnitDescKey() {
        return "gtsr.tooltip.cluster.unit.magnetic_separator.desc";
    }

    /**
     * 功能群（v1.11.15）：磁选链步「耗时 / 蒸汽消耗」行 + 持续供电行——EU/t 与安培取自
     * {@link ClusterParams#MAGNETIC_EU_PER_TICK}/{@link ClusterParams#MAGNETIC_AMPERAGE}。
     */
    @Override
    protected void addUnitTooltipInfo(MultiblockTooltipBuilder tt) {
        tt.addInfo(
            EnumChatFormatting.YELLOW + String.format(
                StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.magnetic_separator.func"),
                linkSeconds(ChainLink.MAGNETIC_SEPARATOR),
                linkSteam(ChainLink.MAGNETIC_SEPARATOR)))
            .addInfo(
                EnumChatFormatting.YELLOW + String.format(
                    StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.magnetic_separator.power"),
                    gold(powerEuLine(ClusterParams.MAGNETIC_EU_PER_TICK, ClusterParams.MAGNETIC_AMPERAGE))));
    }

    /** 仓室群（v1.11.15）：能源仓行——数量按本单元 shape 的 'P' 位实际统计。 */
    @Override
    protected void addUnitStructureTooltipInfo(MultiblockTooltipBuilder tt) {
        tt.addStructureInfo(
            EnumChatFormatting.YELLOW + String.format(
                StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.hatch.energy"),
                gold(String.valueOf(countUnitShapeChar('P')))));
    }

    /** 持续供电值段：{@code 32 EU/t × 1 A = 32 EU/t}（合计由乘积得出，不另造数）。 */
    private static String powerEuLine(int euPerTick, int amperage) {
        return String.format("%d EU/t × %d A = %d EU/t", euPerTick, amperage, euPerTick * amperage);
    }
}
