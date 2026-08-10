package com.miaokatze.gtsr.api.util;

import gregtech.api.GregTechAPI;
import gregtech.api.util.GTUtility;

/** 12 级 tier→casing 纹理索引映射（动力加工阵列/巨型蒸汽轮机机组共用，消除两处逐行重复）。 */
public final class CasingTierTextureHelper {

    private CasingTierTextureHelper() {}

    /** 按 tier 返回 casing 纹理索引；tier 越界时返回 fallback。 */
    public static int getTextureIndex(int tier, int fallback) {
        switch (tier) {
            case 1:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
            case 2:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 2);
            case 3:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 1);
            case 4:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 2);
            case 5:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 0);
            case 6:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 6);
            case 7:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 7);
            case 8:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 14);
            case 9:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockReinforced, 11);
            case 10:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockReinforced, 10);
            case 11:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 3);
            case 12:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 10);
            default:
                return fallback;
        }
    }
}
