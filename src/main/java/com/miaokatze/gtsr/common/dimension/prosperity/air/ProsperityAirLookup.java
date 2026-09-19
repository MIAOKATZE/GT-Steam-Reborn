package com.miaokatze.gtsr.common.dimension.prosperity.air;

import net.minecraft.world.World;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.BiomeId;

import gregtech.api.enums.Materials;

/**
 * dim78 群系 → 繁荣空气材料查表（dim1 S3，plan §5 产出群系映射）。
 * <p>
 * 映射（用户指定配对，唯一权威如需调整只改本表）：
 * <ul>
 * <li>锈蚀草原 rustedSteppe（{@code BiomeId.RUSTED_STEPPE}）→ WastesSigh 荒原叹息</li>
 * <li>齿轮森林 gearworkForest → ThickGrease 浓稠油污</li>
 * <li>黄铜荒漠 brassWastes → MetalGrit 金属风沙</li>
 * <li>起雾沼泽 fumaroleSwamp → UmbralMire 至暗泥泞</li>
 * </ul>
 * <b>P2b 身份读面收口 L1</b>（plan §2.1 L1 禁止项，GTSRBiomeAuthority 类注释登记的
 * "P2/后续"遗留处）：改造前经 {@code world.getBiomeGenForCoords}（读 Chunk 保存的 byte
 * biome 平面）+ {@code instanceof BiomeXxx} 四分支链判定；现走
 * {@link GTSRBiomeAuthority#ordinalAt(int, int)}——解析链是"绑定采样源（chunk manager 的
 * 确定性纯函数，与表层生成同表同种子）→ 实例账本"，不读 byte 平面、不做 {@code id - idStart}
 * 减法、不留 instanceof 链。降级保护语义不变：群系未注册（账本点名不到）返回 null，由
 * 调用方回落（压缩机回落 Air）；非 dim78 维度经 {@link GTSRBiomeAuthority#forDimension(int)}
 * 的未绑定空权威同样解析为 null。四对配对关系零改动（只改"怎么判定是哪个群系"）。
 */
public final class ProsperityAirLookup {

    private ProsperityAirLookup() {}

    /**
     * 查询 (world,x,z) 处群系对应的繁荣空气材料（块坐标，L1 内部按 chunk 粒度解析）。
     *
     * @return 命中四群系时返回对应材料（注册失败降级时可能为 null），未命中返回 null
     */
    public static Materials of(World world, int x, int z) {
        if (world == null || world.provider == null) {
            return null;
        }
        final BiomeId key = GTSRBiomeAuthority.forDimension(world.provider.dimensionId)
            .ordinalAt(x, z).biomeId;
        if (key == null) {
            return null;
        }
        switch (key) {
            case RUSTED_STEPPE: {
                return GTSRProsperityAirMaterials.WastesSigh;
            }
            case GEARWORK_FOREST: {
                return GTSRProsperityAirMaterials.ThickGrease;
            }
            case BRASS_WASTES: {
                return GTSRProsperityAirMaterials.MetalGrit;
            }
            case FUMAROLE_SWAMP: {
                return GTSRProsperityAirMaterials.UmbralMire;
            }
            default: {
                return null; // dim79 名册成员或未知身份不得配出繁荣气
            }
        }
    }
}
