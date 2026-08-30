package com.miaokatze.gtsr.common.machine.cluster;

import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gtPlusPlus.xmod.gregtech.common.blocks.textures.TexturesGtBlock;

/**
 * 工作单元：筛选机（能力闸门）。
 *
 * <p>
 * 仅声明解锁的 ChainLink（筛分 SIFTER），自身零配方执行；配方匹配与执行由集群总控侧完成。
 * 纹理、集群接线与流体缓冲等公共行为全部继承自 MTEBasicProcessingUnit/MTEClusterUnitBase；
 * overlay 取 GT++ 工业筛选机前脸 inactive/active（常量直引，忠实引用原资源域，不复制 PNG）。
 *
 * <p>
 * 结构（r9 权威规格，5×5×5 canonical [Z][Y][X]，控制器 (2,3,0)）：'e'×9 粒子候选空气位
 * （x1-3, y0, z1-3）；A=外壳族（基类绑定）、B=齿轮箱族、C=管道族、D=框架族、E=玻璃、
 * '-'/'e'=严格空气。
 */
public class MTEUnitSifter extends MTEBasicProcessingUnit {

    /** GUI 类型词条 key。 */
    private static final String TYPE_NAME_KEY = "gtsr.gui.cluster.unit_type.sifter";

    /** 本单元解锁的链路：筛分。 */
    private static final ChainLink[] PROVIDED_LINKS = { ChainLink.SIFTER };

    /** 注册用构造器。 */
    public MTEUnitSifter(int aID, String aName, String aNameRegional) {
        super(
            aID,
            aName,
            aNameRegional,
            TYPE_NAME_KEY,
            TexturesGtBlock.oMCDIndustrialSifter,
            TexturesGtBlock.oMCDIndustrialSifterActive,
            PROVIDED_LINKS);
    }

    /** 克隆用构造器（newMetaEntity 落点）。 */
    public MTEUnitSifter(String aName) {
        super(
            aName,
            TYPE_NAME_KEY,
            TexturesGtBlock.oMCDIndustrialSifter,
            TexturesGtBlock.oMCDIndustrialSifterActive,
            PROVIDED_LINKS);
    }

    /** 结构矩阵（canonical [Z][Y][X]，z0=正面；'~' 位于 (2,3,0)）。 */
    @Override
    protected String[][] getUnitShape() {
        return new String[][] { { " DDD ", "DAAAD", "DEEED", "DA~AD", "DAAAD" },
            { "DeeeD", "EDDDE", "E---E", "EDDDE", "ABCBA" }, { "DeeeD", "EDDDE", "E---E", "EDDDE", "ACCCA" },
            { "DeeeD", "EDDDE", "E---E", "EDDDE", "ABCBA" }, { " DDD ", "DAAAD", "DEEED", "DAAAD", "DAAAD" }, };
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
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitSifter(mName);
    }
}
