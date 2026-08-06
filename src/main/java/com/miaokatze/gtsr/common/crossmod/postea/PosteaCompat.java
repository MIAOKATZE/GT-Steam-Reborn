package com.miaokatze.gtsr.common.crossmod.postea;

import com.gtnewhorizons.postea.api.ItemStackReplacementManager;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;

import cpw.mods.fml.common.Loader;
import gregtech.api.enums.Mods;

/**
 * Postea 兼容层：旧 ID 机器物品（ItemMachines damage=旧ID）→ 新 ID 映射（V2 meta 迁移）。
 * <p>
 * 类加载安全：init() 仅引用字符串/枚举常量；{@link #registerLegacyMachineItemMapping()} 方法签名
 * 不含 Postea 类型（无参），方法体在调用时才解析——Postea 缺失时类可正常加载，注册逻辑不执行。
 * 临时机制：下一大版本移除旧 ID 注册后，本类一并删除（依赖旧 ID 物品仍注册，Postea 才能找到并转换）。
 * 官方先例：GT5U PosteaTransformers.java:91-110（gt.blockmachines framebox 按 meta 改 Damage）。
 */
public class PosteaCompat {

    public static void init() {
        if (Loader.isModLoaded(Mods.Postea.ID)) {
            registerLegacyMachineItemMapping();
        }
    }

    private static void registerLegacyMachineItemMapping() {
        ItemStackReplacementManager.addTransformationHandler("gregtech:gt.blockmachines", (name, tag) -> {
            int damage = tag.getInteger("Damage");
            int newId = MetaTileEntityID.getMappedId(damage);
            if (newId < 0) return false;
            tag.setInteger("Damage", newId);
            return true;
        });
    }
}
