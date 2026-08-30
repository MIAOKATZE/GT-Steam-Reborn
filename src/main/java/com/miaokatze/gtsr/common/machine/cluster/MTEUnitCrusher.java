package com.miaokatze.gtsr.common.machine.cluster;

import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 工作单元：粉碎机（能力闸门）。
 *
 * <p>
 * 仅声明解锁的 ChainLink（矿石粉碎 CRUSH / 锤击粉碎 HAMMER），自身零配方执行；配方匹配与
 * 执行由集群总控侧完成。集群接线与流体缓冲等公共行为全部继承自
 * MTEBasicProcessingUnit/MTEClusterUnitBase。
 *
 * <p>
 * 贴图：GT5U 蒸汽粉碎机前脸 overlay（OVERLAY_FRONT_STEAM_MACERATOR / _ACTIVE，无 glow）经
 * 基类构造器注入的常量对接入 {@code unitOverlayInactive}/{@code unitOverlayActive} 钩子，
 * 渲染面由基类 {@code getTexture} 的 side==facing 判定；底材走基类 tier 联动。
 *
 * <p>
 * 结构（r9 权威规格，5×6×5 canonical [Z][Y][X]，控制器 (0,4,2)）：'e'×4 粒子候选空气位
 * （x∈{1,3}, y0, z∈{1,3}）；A=外壳族（基类绑定）、B=齿轮箱族、C=管道族、D=框架族、E=玻璃、
 * '-'/'e'=严格空气。
 */
public class MTEUnitCrusher extends MTEBasicProcessingUnit {

    /** GUI 类型词条 key。 */
    private static final String TYPE_NAME_KEY = "gtsr.gui.cluster.unit_type.crusher";

    /** 本单元解锁的链路：矿石粉碎与打粉锤击两条前置破碎链。 */
    private static final ChainLink[] PROVIDED_LINKS = { ChainLink.CRUSH, ChainLink.HAMMER };

    /** 注册用构造器。 */
    public MTEUnitCrusher(int aID, String aName, String aNameRegional) {
        super(
            aID,
            aName,
            aNameRegional,
            TYPE_NAME_KEY,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_MACERATOR,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_MACERATOR_ACTIVE,
            PROVIDED_LINKS);
    }

    /** 克隆用构造器（newMetaEntity 落点）。 */
    public MTEUnitCrusher(String aName) {
        super(
            aName,
            TYPE_NAME_KEY,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_MACERATOR,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_MACERATOR_ACTIVE,
            PROVIDED_LINKS);
    }

    /** 结构矩阵（canonical [Z][Y][X]，z0=正面；'~' 位于 (0,4,2)）。 */
    @Override
    protected String[][] getUnitShape() {
        return new String[][] { { " DDD ", " AAA ", "DEEED", "DEEED", "DEEED", "DAAAD" },
            { "DeAeD", "ACCCA", "A-B-A", "A---A", "A-B-A", "ABCBA" },
            { "DAAAD", "ACCCA", "ABBBA", "A---A", "~BBBA", "ACCCA" },
            { "DeAeD", "ACCCA", "A-B-A", "A---A", "A-B-A", "ABCBA" },
            { " DDD ", " AAA ", "DEEED", "DEEED", "DEEED", "DAAAD" }, };
    }

    /** 专有结构元素：B=齿轮箱族、C=管道族、D=框架族、E=玻璃、'-'/'e'=严格空气。 */
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
        return 0;
    }

    @Override
    protected int getStructureOffsetB() {
        return 4;
    }

    @Override
    protected int getStructureOffsetC() {
        return 2;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitCrusher(mName);
    }
}
