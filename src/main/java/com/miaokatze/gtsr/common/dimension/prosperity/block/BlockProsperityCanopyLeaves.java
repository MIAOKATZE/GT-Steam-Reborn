package com.miaokatze.gtsr.common.dimension.prosperity.block;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 繁荣维度树冠叶方块 · side/top 双图标档（P17-SB1，plan §0-Q3「新增自有 log/leaf 族 3 档」）。
 * <p>
 * 与 {@link BlockProsperityRustLeaves}（单图标、rust 档既有）的关系：本类只把材质面从单图标扩为
 * side/top 双图标（D 片「每档 2 方块 + 4 贴图」定价的形态学兑现——树冠顶视读作更密的簇缝），
 * 冠层乘色 tint、物品占位色与专用服字段剥离纪律全部继承 {@code BlockProsperityRustLeaves}，
 * 不重抄第二份（避免第二真值源）。普通立方块、不接原版树叶图形档/衰减逻辑的口径同父类：
 * 冠层由 S-B2（P17 植被趟）确定性拼装后永久留存。
 * <p>
 * 消费方：{@code BlocksGTSR.prosperityCopperLeaves / prosperityBrassLeaves / prosperityMarshLeaves}
 * 三身份档实例（BlockLoader 注册，meta 恒 0）；世界生成侧本片零接线。
 */
public class BlockProsperityCanopyLeaves extends BlockProsperityRustLeaves {

    @SideOnly(Side.CLIENT)
    private IIcon sideIcon;
    @SideOnly(Side.CLIENT)
    private IIcon topIcon;

    /** 贴图名（gtsr 域内；注册名不变换贴图，双端安全 String 字段，同父类纪律）。 */
    private final String textureSide;
    private final String textureTop;

    public BlockProsperityCanopyLeaves(String blockName, String textureSide, String textureTop) {
        // 父类 texture 槽传 side 名（父类 registerBlockIcons 被本类整体覆写，该槽仅占位不注册）。
        super(blockName, textureSide);
        this.textureSide = textureSide;
        this.textureTop = textureTop;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.sideIcon = register.registerIcon(this.textureSide);
        this.topIcon = register.registerIcon(this.textureTop);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return side == 1 || side == 0 ? this.topIcon : this.sideIcon;
    }
}
