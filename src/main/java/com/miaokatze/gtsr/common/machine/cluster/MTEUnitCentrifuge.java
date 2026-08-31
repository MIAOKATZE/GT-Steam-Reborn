package com.miaokatze.gtsr.common.machine.cluster;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * 工作单元：离心机（能力闸门）。
 *
 * <p>
 * 仅声明解锁的 ChainLink（离心分离 CENTRIFUGE），自身零配方执行；配方匹配与执行由集群总控侧
 * 完成。纹理、集群接线与流体缓冲等公共行为全部继承自 MTEBasicProcessingUnit/MTEClusterUnitBase；
 * overlay 取 GT5U 蒸汽离心机前脸 inactive/active（仅 base+ACTIVE 有 PNG，禁 glow 变体）。
 *
 * <p>
 * 结构（r9 权威规格，7×7×5 canonical [Z][Y][X]，控制器 (3,5,0)）：'e'×9 粒子候选空气位
 * （x2-4, y1, z1-3）；A=外壳族（基类绑定）、B=齿轮箱族、C=管道族、D=框架族、'-'/'e'=严格空气。
 */
public class MTEUnitCentrifuge extends MTEBasicProcessingUnit {

    /** GUI 类型词条 key。 */
    private static final String TYPE_NAME_KEY = "gtsr.gui.cluster.unit_type.centrifuge";

    /** 本单元解锁的链路：离心分离。 */
    private static final ChainLink[] PROVIDED_LINKS = { ChainLink.CENTRIFUGE };

    /** 注册用构造器。 */
    public MTEUnitCentrifuge(int aID, String aName, String aNameRegional) {
        super(
            aID,
            aName,
            aNameRegional,
            TYPE_NAME_KEY,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_CENTRIFUGE,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_CENTRIFUGE_ACTIVE,
            PROVIDED_LINKS);
    }

    /** 克隆用构造器（newMetaEntity 落点）。 */
    public MTEUnitCentrifuge(String aName) {
        super(
            aName,
            TYPE_NAME_KEY,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_CENTRIFUGE,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_CENTRIFUGE_ACTIVE,
            PROVIDED_LINKS);
    }

    /** 结构矩阵（canonical [Z][Y][X]，z0=正面；'~' 位于 (3,5,0)；z4=同z0 但 '~'→'A'（唯一控制器）。 */
    @Override
    protected String[][] getUnitShape() {
        return new String[][] { { "  AAA  ", " DAAAD ", " DAAAD ", " DAAAD ", " DAAAD ", " DA~AD ", " DAAAD " },
            { " A   A ", " AeeeA ", "DACCCAD", "DBCBCBD", "DCBCBCD", "DBCBCBD", " ABCBA " },
            { " A   A ", " AeeeA ", "DACCCAD", "DCBBBCD", "DBCCCBD", "DCBBBCD", " ACCCA " },
            { " A   A ", " AeeeA ", "DACCCAD", "DBCBCBD", "DCBCBCD", "DBCBCBD", " ABCBA " },
            { "  AAA  ", " DAAAD ", " DAAAD ", " DAAAD ", " DAAAD ", " DAAAD ", " DAAAD " }, };
    }

    /** 专有结构元素：B=齿轮箱族、C=管道族、D=框架族、'-'/'e'=严格空气。 */
    @Override
    @SuppressWarnings("rawtypes")
    protected void addUnitStructureElements(StructureDefinition.Builder builder) {
        builder.addElement('B', tieredGearboxElement())
            .addElement('C', tieredPipeElement())
            .addElement('D', tieredFrameElement())
            .addElement('-', airElement())
            .addElement('e', airElement());
    }

    @Override
    protected int getStructureOffsetA() {
        return 3;
    }

    @Override
    protected int getStructureOffsetB() {
        return 5;
    }

    @Override
    protected int getStructureOffsetC() {
        return 0;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitCentrifuge(mName);
    }

    /** 工序主色（计划 §2.2）：AQUA。 */
    @Override
    protected EnumChatFormatting getUnitDescColor() {
        return EnumChatFormatting.AQUA;
    }

    /** 单元描述键（v1.11.15 W1 修正）：离心模块专属描述行。 */
    @Override
    protected String getUnitDescKey() {
        return "gtsr.tooltip.cluster.unit.centrifuge.desc";
    }

    /** 功能群（v1.11.15）：离心链步「耗时 / 蒸汽消耗」行（数据源 {@link ChainLink#CENTRIFUGE}）。 */
    @Override
    protected void addUnitTooltipInfo(MultiblockTooltipBuilder tt) {
        tt.addInfo(
            EnumChatFormatting.YELLOW + String.format(
                StatCollector.translateToLocal("gtsr.tooltip.cluster.unit.centrifuge.func"),
                linkSeconds(ChainLink.CENTRIFUGE),
                linkSteam(ChainLink.CENTRIFUGE)));
    }
}
