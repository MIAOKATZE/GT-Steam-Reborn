package com.miaokatze.gtsr.common.structure;

import java.util.List;

import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;

/**
 * checkMachine 结构校验样板助手（O2-07 一阶段纯去重，零行为变化）。
 * 75 处「if (失败条件) { errors.add(UNKNOWN_STRUCTURE_ERROR); 复位; return; }」样板中
 * 「条件 + add」两行收敛为单点 require；复位动作（复位字段/issueTileUpdate/无复位三样）
 * 与提前 return 留在调用处，助手不吞控制流（深查-checkMachine样板收敛 §2.1 定稿设计）。
 * 仅限结构校验语境使用；玩家可读的具体错误提示属二阶段 requireLang，不在本类。
 */
public final class GTSRStructureChecks {

    private GTSRStructureChecks() {}

    /**
     * cond 为 false 时向 errors 追加 UNKNOWN_STRUCTURE_ERROR 并返回 false；条件成立返回 true。
     * 调用侧惯用形如 if (!require(mStackCount > 0, errors)) { getBaseMetaTileEntity().issueTileUpdate(); return; }
     */
    public static boolean require(boolean cond, List<StructureError> errors) {
        if (!cond) errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
        return cond;
    }
}
