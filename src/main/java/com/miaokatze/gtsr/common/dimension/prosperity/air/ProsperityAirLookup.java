package com.miaokatze.gtsr.common.dimension.prosperity.air;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeBrassWastes;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeFumaroleSwamp;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeGearworkForest;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeRustedSteppe;

import gregtech.api.enums.Materials;

/**
 * dim78 群系 → 繁荣空气材料查表（dim1 S3，plan §5 产出群系映射）。
 * <p>
 * 映射（用户指定配对，唯一权威如需调整只改本表）：
 * <ul>
 * <li>锈蚀草原 rustedSteppe（180）→ WastesSigh 荒原叹息</li>
 * <li>齿轮森林 gearworkForest（181）→ ThickGrease 浓稠油污</li>
 * <li>黄铜荒漠 brassWastes（182）→ MetalGrit 金属风沙</li>
 * <li>起雾沼泽 fumaroleSwamp（183）→ UmbralMire 至暗泥泞</li>
 * </ul>
 * 匹配用实例类型而非 biomeID 数值：群系槽被占时 ProsperityBiomes 跳过构造（plan R3 降级），
 * 同 id 若被其他 mod 群系占用则不会误判；命中失败返回 null 由调用方回落（压缩机回落 Air）。
 */
public final class ProsperityAirLookup {

    private ProsperityAirLookup() {}

    /**
     * 查询 (world,x,z) 处群系对应的繁荣空气材料。
     *
     * @return 命中四群系时返回对应材料（注册失败降级时可能为 null），未命中返回 null
     */
    public static Materials of(World world, int x, int z) {
        if (world == null) {
            return null;
        }
        final BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
        if (biome instanceof BiomeRustedSteppe) {
            return GTSRProsperityAirMaterials.WastesSigh;
        }
        if (biome instanceof BiomeGearworkForest) {
            return GTSRProsperityAirMaterials.ThickGrease;
        }
        if (biome instanceof BiomeBrassWastes) {
            return GTSRProsperityAirMaterials.MetalGrit;
        }
        if (biome instanceof BiomeFumaroleSwamp) {
            return GTSRProsperityAirMaterials.UmbralMire;
        }
        return null;
    }
}
