package com.miaokatze.gtsr.common.machine.cluster;

import static gregtech.api.util.GTStructureUtility.fillStructureWithWater;

import java.util.List;

import net.minecraft.item.ItemStack;

import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTStructureUtility;

/**
 * 工作单元：洗矿机（能力闸门）。
 *
 * <p>
 * 仅声明解锁的 ChainLink（矿石清洗 ORE_WASH / 化学浴 CHEM_BATH / 简单清洗 SIMPLE_WASH），
 * 自身零配方执行；配方匹配与执行由集群总控侧完成。纹理、集群接线与流体缓冲等公共行为全部
 * 继承自 MTEBasicProcessingUnit/MTEClusterUnitBase；overlay 取 GT5U 蒸汽洗矿机前脸
 * inactive/active（常量直引）。
 *
 * <p>
 * 结构（r9 权威规格，7×6×5 canonical [Z][Y][X]，控制器 (3,4,0)）：无 'e'（不产粒子候选）；
 * 'f'×40 内腔 = ofChain(ofAnyWater(false), isAir())（水或空气皆通过，太阳能阵列式口径）；
 * A=外壳族（基类绑定）、B=齿轮箱族、C=管道族、D=框架族、E=玻璃。
 *
 * <p>
 * 真实注水（r9，太阳能阵列式）：成型边沿置 {@link #needsWaterFill}，onPostTick 服务端每秒
 * {@code fillStructureWithWater} 将全部 'f' 位实际置换为水（getUnitShape 已 canonical 直传，
 * 勿再 transpose）；成功后清标记，结构重检（checkMachine 成功）再置位——结构破坏后重建自动回填。
 */
public class MTEUnitOreWasher extends MTEBasicProcessingUnit {

    /** GUI 类型词条 key。 */
    private static final String TYPE_NAME_KEY = "gtsr.gui.cluster.unit_type.ore_washer";

    /** 本单元解锁的链路：矿石清洗、化学浴与简单清洗三条湿法链。 */
    private static final ChainLink[] PROVIDED_LINKS = { ChainLink.ORE_WASH, ChainLink.CHEM_BATH,
        ChainLink.SIMPLE_WASH };

    /** 真实注水待执行标记（服务端瞬态）：成型边沿置 true，注水成功清 false。 */
    private boolean needsWaterFill = false;

    /** 注册用构造器。 */
    public MTEUnitOreWasher(int aID, String aName, String aNameRegional) {
        super(
            aID,
            aName,
            aNameRegional,
            TYPE_NAME_KEY,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_WASHER,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_WASHER_ACTIVE,
            PROVIDED_LINKS);
    }

    /** 克隆用构造器（newMetaEntity 落点）。 */
    public MTEUnitOreWasher(String aName) {
        super(
            aName,
            TYPE_NAME_KEY,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_WASHER,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_WASHER_ACTIVE,
            PROVIDED_LINKS);
    }

    /** 结构矩阵（canonical [Z][Y][X]，z0=正面；'~' 位于 (3,4,0)；无 'e'，'f'=水/空气两态内腔）。 */
    @Override
    protected String[][] getUnitShape() {
        return new String[][] { { " DDDDD ", " AAAAA ", "DAAAAAD", "DAAAAAD", "DAA~AAD", " DAAAD " },
            { "D     D", "AEEEEEA", "EfffffE", "EfffffE", "EffBffE", " ABCBA " },
            { "D     D", "AEEEEEA", "EfffffE", "EfffffE", "EfBBBfE", " ACCCA " },
            { "D     D", "AEEEEEA", "EfffffE", "EfffffE", "EffBffE", " ABCBA " },
            { " DDDDD ", " AAAAA ", "DAAAAAD", "DAAAAAD", "DAAAAAD", " DAAAD " }, };
    }

    /** 专有结构元素：B=齿轮箱族、C=管道族、D=框架族、E=玻璃、'f'=anyWater 链严格空气（无 'e'）。 */
    @Override
    @SuppressWarnings("rawtypes")
    protected void addUnitStructureElements(StructureDefinition.Builder builder) {
        builder.addElement('B', tieredGearboxElement())
            .addElement('C', tieredPipeElement())
            .addElement('D', tieredFrameElement())
            .addElement('E', glassElement())
            .addElement('f', StructureUtility.ofChain(GTStructureUtility.ofAnyWater(false), airElement()));
    }

    @Override
    protected int getStructureOffsetA() {
        return 3;
    }

    @Override
    protected int getStructureOffsetB() {
        return 4;
    }

    @Override
    protected int getStructureOffsetC() {
        return 0;
    }

    /**
     * 结构校验追加：super（分族 tier + 同级校验）通过即成型边沿——置真实注水待执行标记（下个
     * 整秒由 onPostTick 回填 'f' 位）。
     */
    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        super.checkMachine(aBaseMetaTileEntity, aStack, errors);
        if (errors.isEmpty()) needsWaterFill = true;
    }

    /**
     * 服务端真实注水（r9）：成型且待注水时每秒（aTick%20==0）执行一次
     * {@code fillStructureWithWater}——把矩阵内全部 'f' 位实际置为水（canonical 矩阵直传，勿再
     * transpose）；成功清标记。非服务端/未成型/无需注水直接透传基类（客户端 'e' 候选注册在基类，
     * 本模块无 'e' 即无候选）。
     */
    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide() && needsWaterFill && mMachine && aTick % 20 == 0) {
            if (fillStructureWithWater(getBaseMetaTileEntity(), getExtendedFacing(), getUnitShape(), 3, 4, 0, 'f')) {
                needsWaterFill = false;
            }
        }
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitOreWasher(mName);
    }
}
