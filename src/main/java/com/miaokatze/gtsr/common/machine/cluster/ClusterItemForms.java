package com.miaokatze.gtsr.common.machine.cluster;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import gregtech.api.util.GTUtility;

/**
 * 集群物品形态分类器：按矿物词典前缀将物品堆归类为矿石产业链中的九种形态，
 * 供集群状态机与执行器共用。
 * <p>
 * 范式移植自 GT5U {@code MTEIntegratedOreFactory}（矿石工厂哈希集惰性初始化）：
 * 首次调用 {@link #classify(ItemStack)} 时遍历 {@link OreDictionary#getOreNames()}，
 * 按长名优先的 startsWith 顺序分桶，并将每个 OD 名下的物品堆经
 * {@link GTUtility#stackToInt(ItemStack)} 压缩为 int 存入集合。
 * <p>
 * 顺序敏感：{@code crushedPurified}/{@code crushedCentrifuged} 必须先于 {@code crushed}
 * 判定，否则会被短前缀吞掉；{@code dustImpure}/{@code dustPure} 同理必须先于普通
 * {@code dust} 前缀（dust 桶按并集口径收纳 dust/dusts 及 dustTiny/dustSmall 等衍生短前缀，
 * 与 FSM 终态 {@link ClusterChainFSM.Form#DUST} 对应）；{@code ingot}/{@code ingots} 归
 * {@link OreForm#INGOT}（与 {@link ClusterChainFSM.Form#INGOT} 对应）——熔炉链对普通
 * dust/ingot 终态可达（§3.6.6-1/2）。
 * 为避免扩大依赖面，此处使用 {@link HashSet} 而非 fastutil 的 IntOpenHashSet。
 */
public final class ClusterItemForms {

    /** 物品在矿石产业链中的形态。 */
    public enum OreForm {

        /** 原矿（OD 前缀 ore / rawOre）。 */
        ORE,

        /** 破碎矿（OD 前缀 crushed）。 */
        CRUSHED,

        /** 洗净破碎矿（OD 前缀 crushedPurified）。 */
        CRUSHED_PURIFIED,

        /** 离心破碎矿（OD 前缀 crushedCentrifuged）。 */
        CRUSHED_CENTRIFUGED,

        /** 含杂粉尘（OD 前缀 dustImpure）。 */
        DUST_IMPURE,

        /** 纯净粉尘（OD 前缀 dustPure）。 */
        DUST_PURE,

        /** 纯粉尘（OD 前缀 dust，含 dusts 等衍生；FSM 终态）。 */
        DUST,

        /** 锭（OD 前缀 ingot，含 ingots；FSM 终态）。 */
        INGOT,

        /** 不属于上述任何形态的物品。 */
        OTHER
    }

    private static final Set<Integer> ORE_STACKS = new HashSet<>();
    private static final Set<Integer> CRUSHED_STACKS = new HashSet<>();
    private static final Set<Integer> CRUSHED_PURIFIED_STACKS = new HashSet<>();
    private static final Set<Integer> CRUSHED_CENTRIFUGED_STACKS = new HashSet<>();
    private static final Set<Integer> DUST_IMPURE_STACKS = new HashSet<>();
    private static final Set<Integer> DUST_PURE_STACKS = new HashSet<>();
    private static final Set<Integer> DUST_STACKS = new HashSet<>();
    private static final Set<Integer> INGOT_STACKS = new HashSet<>();

    private static boolean initialised = false;

    private ClusterItemForms() {}

    /**
     * 判定物品堆的矿石产业链形态；null 或未命中任何词典前缀桶时返回 {@link OreForm#OTHER}。
     * <p>
     * 首次调用触发词典扫描初始化，之后为纯集合查询，线程模型与矿石工厂一致（主线程调用）。
     *
     * @param stack 待判定的物品堆，允许为 null
     * @return 对应的形态枚举，永不为 null
     */
    public static OreForm classify(ItemStack stack) {
        if (stack == null) return OreForm.OTHER;
        ensureInit();
        int stackId = GTUtility.stackToInt(stack);
        if (CRUSHED_PURIFIED_STACKS.contains(stackId)) return OreForm.CRUSHED_PURIFIED;
        if (CRUSHED_CENTRIFUGED_STACKS.contains(stackId)) return OreForm.CRUSHED_CENTRIFUGED;
        if (CRUSHED_STACKS.contains(stackId)) return OreForm.CRUSHED;
        if (DUST_IMPURE_STACKS.contains(stackId)) return OreForm.DUST_IMPURE;
        if (DUST_PURE_STACKS.contains(stackId)) return OreForm.DUST_PURE;
        if (DUST_STACKS.contains(stackId)) return OreForm.DUST;
        if (ORE_STACKS.contains(stackId)) return OreForm.ORE;
        if (INGOT_STACKS.contains(stackId)) return OreForm.INGOT;
        return OreForm.OTHER;
    }

    /**
     * 惰性初始化：遍历全部矿物词典名，按长名优先的 startsWith 顺序分桶
     * （{@code dustImpure}/{@code dustPure} 必须先于普通 {@code dust}，否则衍生粉尘会被
     * 短前缀吞掉；ingot 与其余前缀无冲突）。
     */
    private static void ensureInit() {
        if (initialised) return;
        initialised = true;
        for (String name : OreDictionary.getOreNames()) {
            if (name == null || name.isEmpty()) continue;

            if (name.startsWith("crushedPurified")) registerStacks(name, CRUSHED_PURIFIED_STACKS);
            else if (name.startsWith("crushedCentrifuged")) registerStacks(name, CRUSHED_CENTRIFUGED_STACKS);
            else if (name.startsWith("crushed")) registerStacks(name, CRUSHED_STACKS);
            else if (name.startsWith("dustImpure")) registerStacks(name, DUST_IMPURE_STACKS);
            else if (name.startsWith("dustPure")) registerStacks(name, DUST_PURE_STACKS);
            else if (name.startsWith("dust")) registerStacks(name, DUST_STACKS);
            else if (name.startsWith("ore") || name.startsWith("rawOre")) registerStacks(name, ORE_STACKS);
            else if (name.startsWith("ingot")) registerStacks(name, INGOT_STACKS);
        }
    }

    /**
     * 将指定矿物词典名下的全部物品堆压缩为 int 标识并加入目标集合。
     */
    private static void registerStacks(String oreDictName, Set<Integer> target) {
        for (ItemStack stack : OreDictionary.getOres(oreDictName)) {
            target.add(GTUtility.stackToInt(stack));
        }
    }
}
