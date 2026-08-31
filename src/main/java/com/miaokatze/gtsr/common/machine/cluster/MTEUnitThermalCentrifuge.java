package com.miaokatze.gtsr.common.machine.cluster;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtPlusPlus.xmod.gregtech.common.blocks.textures.TexturesGtBlock;

/**
 * 工作单元：热力离心机（能力闸门 + 自持能源）。
 *
 * <p>
 * 仅声明解锁的 ChainLink（热离心 THERMOCENTRIFUGE），自身零配方执行；配方匹配与执行由集群总控侧
 * 完成。集群接线与流体缓冲等公共行为全部继承自 MTEClusterUnitBase；自持能源语义（P 能源位/
 * 成型校验/EU 探测/断电闸门）全部继承自 {@link MTEUnitSelfPoweredProcessingUnit}。
 *
 * <p>
 * overlay：GT++ 工业热力离心机前脸（忠实引用原资源域，不复制 PNG）。
 *
 * <p>
 * 结构（r9 权威规格，7×5×5 canonical [Z][Y][X]，控制器 (3,3,0)）：无 'e'（不产粒子候选）；
 * 'a'=线圈族（{@code tieredCoilElement}，白铜/坎塔尔/钛铂钒/HSS-G 四档，⚠ 第四档 meta 4 为
 * GT5U lang 证据的授权偏离）；'P'×1 能源位位于 (3,3,4)——控制器同列同层最深行；
 * A=外壳族（基类绑定）、B=齿轮箱族、C=管道族、D=框架族、'-'=严格空气。
 */
public class MTEUnitThermalCentrifuge extends MTEUnitSelfPoweredProcessingUnit {

    /** GUI 类型词条 key。 */
    private static final String TYPE_NAME_KEY = "gtsr.gui.cluster.unit_type.thermal_centrifuge";

    /** 本单元解锁的链路：热离心。 */
    private static final ChainLink[] PROVIDED_LINKS = { ChainLink.THERMOCENTRIFUGE };

    /** 注册用构造器。 */
    public MTEUnitThermalCentrifuge(int aID, String aName, String aNameRegional) {
        super(
            aID,
            aName,
            aNameRegional,
            TYPE_NAME_KEY,
            TexturesGtBlock.oMCDIndustrialThermalCentrifuge,
            TexturesGtBlock.oMCDIndustrialThermalCentrifugeActive,
            PROVIDED_LINKS);
    }

    /** 克隆用构造器（newMetaEntity 落点）。 */
    public MTEUnitThermalCentrifuge(String aName) {
        super(
            aName,
            TYPE_NAME_KEY,
            TexturesGtBlock.oMCDIndustrialThermalCentrifuge,
            TexturesGtBlock.oMCDIndustrialThermalCentrifugeActive,
            PROVIDED_LINKS);
    }

    /** 结构矩阵（canonical [Z][Y][X]，z0=正面；'~' 位于 (3,3,0)，'P' 位于 (3,3,4)）。 */
    @Override
    protected String[][] getUnitShape() {
        return new String[][] { { " D   D ", " DAAAD ", " DaaaD ", " DA~AD ", " DAAAD " },
            { "  DDD  ", "DACBCAD", "Da---aD", "DACBCAD", " ABCBA " },
            { "  DDD  ", "DABBBAD", "Da---aD", "DABBBAD", " ACCCA " },
            { "  DDD  ", "DACBCAD", "Da---aD", "DACBCAD", " ABCBA " },
            { " D   D ", " DAAAD ", " DaaaD ", " DAPAD ", " DAAAD " }, };
    }

    /** 专有结构元素：a=线圈族、B=齿轮箱族、C=管道族、D=框架族、'-'=严格空气（P 由父类注入）。 */
    @Override
    @SuppressWarnings("rawtypes")
    protected void addUnitStructureElements(StructureDefinition.Builder builder) {
        super.addUnitStructureElements(builder);
        builder.addElement('a', tieredCoilElement())
            .addElement('B', tieredGearboxElement())
            .addElement('C', tieredPipeElement())
            .addElement('D', tieredFrameElement())
            .addElement('-', airElement());
    }

    @Override
    protected int getStructureOffsetA() {
        return 3;
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
        return new MTEUnitThermalCentrifuge(mName);
    }

    /** 工序主色（计划 §2.2）：LIGHT_PURPLE。 */
    @Override
    protected EnumChatFormatting getUnitDescColor() {
        return EnumChatFormatting.LIGHT_PURPLE;
    }

    /** 单元描述键（v1.11.15 W1 修正）：热力离心模块专属描述行。 */
    @Override
    protected String getUnitDescKey() {
        return "gtsr.tooltip.cluster.unit.thermal_centrifuge.desc";
    }

    /**
     * 功能群（v1.11.15）：热离链步「耗时 / 蒸汽消耗」行 + 持续供电行——EU/t 与安培取自
     * {@link ClusterParams#THERMOCENTRIFUGE_EU_PER_TICK}/{@link ClusterParams#THERMOCENTRIFUGE_AMPERAGE}
     * （合计 = 乘积，Java 侧 GOLD 注入）。
     */
    @Override
    protected void addUnitTooltipInfo(MultiblockTooltipBuilder tt) {
        tt.addInfo(
            EnumChatFormatting.YELLOW + String.format(
                StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.thermal_centrifuge.func"),
                linkSeconds(ChainLink.THERMOCENTRIFUGE),
                linkSteam(ChainLink.THERMOCENTRIFUGE)))
            .addInfo(
                EnumChatFormatting.YELLOW + String.format(
                    StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.thermal_centrifuge.power"),
                    gold(
                        powerEuLine(
                            ClusterParams.THERMOCENTRIFUGE_EU_PER_TICK,
                            ClusterParams.THERMOCENTRIFUGE_AMPERAGE))));
    }

    /** 仓室群（v1.11.15）：能源仓行——数量按本单元 shape 的 'P' 位实际统计。 */
    @Override
    protected void addUnitStructureTooltipInfo(MultiblockTooltipBuilder tt) {
        tt.addStructureInfo(
            EnumChatFormatting.YELLOW + String.format(
                StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.hatch.energy"),
                gold(String.valueOf(countUnitShapeChar('P')))));
    }

    /** 持续供电值段：{@code 32 EU/t × 3 A = 96 EU/t}（合计由乘积得出，不另造数）。 */
    private static String powerEuLine(int euPerTick, int amperage) {
        return String.format("%d EU/t × %d A = %d EU/t", euPerTick, amperage, euPerTick * amperage);
    }
}
