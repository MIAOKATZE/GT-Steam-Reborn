package com.miaokatze.gtsgu.loader;

import com.miaokatze.gtsgu.register.ItemRegistrar;

/**
 * 物品加载器
 * 负责在模组初始化的正确阶段触发物品注册流程
 */
public class ItemLoader {

    /**
     * 初始化物品
     * 建议在 PreInit 阶段调用
     */
    public static void initItems() {
        // [GTSGU-DEV] 临时禁用物品加载器，为后续开发清理环境。源码完整保留。
        // ItemRegistrar.init();
    }
}
