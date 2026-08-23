package com.miaokatze.gtsr.common.machine.cluster;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 集群链推演并集状态机：GUI 推演器与服务器执行器共用的纯函数形态转换器，零副作用、无任何可变状态。
 * <p>
 * 结构性判定口径 = 配方网并集（GT 原版 + bartworks Werkstoff 双轨）：转换表取两轨配方网中
 * 「该链步对该前驱形态存在任一配方」的并集，只判定链的形状是否能走通到终态；
 * 运行时逐物品透传由执行器负责（查不到配方的物品原样进入下一步，见 IOF {@code processStep}），
 * 本类只做链形状判定，不做配方存在性判定。
 * <p>
 * 终态 = {@link Form#DUST} 或 {@link Form#INGOT}（plan §5.3：有效链 = 推演终态属于二者）。
 */
public final class ClusterChainFSM {

    /**
     * 链推演形态（比 {@link ClusterItemForms.OreForm} 多 DUST / INGOT / UNKNOWN 三个终态与未知态）。
     */
    public enum Form {

        /** 原矿（链起点）。 */
        ORE,

        /** 破碎矿。 */
        CRUSHED,

        /** 洗净破碎矿。 */
        CRUSHED_PURIFIED,

        /** 离心破碎矿。 */
        CRUSHED_CENTRIFUGED,

        /** 含杂粉尘。 */
        DUST_IMPURE,

        /** 纯净粉尘。 */
        DUST_PURE,

        /** 纯粉尘（终态）。 */
        DUST,

        /** 锭（终态）。 */
        INGOT,

        /** 未知 / 不可判定形态（推演中永不产生，仅作为外部输入的防御值；任何链步对其透传）。 */
        UNKNOWN
    }

    /**
     * 并集转换表：外层键为链步，内层键为前驱形态、值为后继形态；内层未登记的组合一律透传。
     * <p>
     * 两层均为不可变包装，类加载期一次构建，之后只读。
     */
    private static final Map<ChainLink, Map<Form, Form>> TRANSITIONS = buildTransitions();

    private ClusterChainFSM() {}

    /**
     * @return 链推演起点形态，恒为 {@link Form#ORE}
     */
    public static Form start() {
        return Form.ORE;
    }

    /**
     * 单步转换：按并集转换表查询「链步 × 前驱形态」的后继形态；无规则时透传（原样返回 current）。
     * <p>
     * {@link ChainLink#FURNACE} 对任意非 {@link Form#UNKNOWN} 形态（含终态自身）映射为
     * {@link Form#INGOT}；{@link Form#UNKNOWN} 作为前驱时无任何规则，恒透传。
     *
     * @param current 前驱形态，不允许为 null
     * @param link    链步，不允许为 null
     * @return 后继形态，永不为 null
     * @throws IllegalArgumentException current 或 link 为 null 时
     */
    public static Form next(Form current, ChainLink link) {
        if (current == null) throw new IllegalArgumentException("current 不允许为 null");
        if (link == null) throw new IllegalArgumentException("link 不允许为 null");
        Map<Form, Form> byForm = TRANSITIONS.get(link);
        if (byForm == null) return current;
        Form next = byForm.get(current);
        return next != null ? next : current;
    }

    /**
     * @param f 待判定的形态，不允许为 null
     * @return 是否为链终态；仅 {@link Form#DUST} 与 {@link Form#INGOT} 为 {@code true}
     * @throws IllegalArgumentException f 为 null 时
     */
    public static boolean isTerminal(Form f) {
        if (f == null) throw new IllegalArgumentException("f 不允许为 null");
        return f == Form.DUST || f == Form.INGOT;
    }

    /**
     * 整链推演：从 {@link #start()} 出发，依次对每个链步应用 {@link #next(Form, ChainLink)}。
     *
     * @param chain 有序链；为 {@code null} 或空链时无步可走，返回 {@link #start()}
     * @return 推演终态形态，永不为 null
     */
    public static Form simulate(List<ChainLink> chain) {
        Form form = start();
        if (chain == null) return form;
        for (ChainLink link : chain) {
            form = next(form, link);
        }
        return form;
    }

    /**
     * @param f 待本地化的形态，不允许为 null
     * @return 本地化键，形如 {@code gtsr.gui.cluster.form.dust}（枚举名转小写，Locale.ROOT）
     * @throws IllegalArgumentException f 为 null 时
     */
    public static String formLangKey(Form f) {
        if (f == null) throw new IllegalArgumentException("f 不允许为 null");
        return "gtsr.gui.cluster.form." + f.name()
            .toLowerCase(Locale.ROOT);
    }

    /**
     * 构建并集转换表（plan §5.1 / HTML v2.5 公式引擎；精确表，勿增删）：
     * <ul>
     * <li>CRUSH、HAMMER：ORE→CRUSHED；CRUSHED→DUST_IMPURE；CRUSHED_PURIFIED→DUST_PURE；CRUSHED_CENTRIFUGED→DUST
     * <li>ORE_WASH、CHEM_BATH：CRUSHED→CRUSHED_PURIFIED
     * <li>SIMPLE_WASH：CRUSHED→CRUSHED_PURIFIED；DUST_IMPURE→DUST；DUST_PURE→DUST（★GT++ 口径，
     * 与执行器 acceptsForm 三形态对齐——SR-终审 C1：GUI 推演与服务器执行共用同一口径）
     * <li>CENTRIFUGE：DUST_IMPURE→DUST；DUST_PURE→DUST
     * <li>THERMOCENTRIFUGE：CRUSHED→CRUSHED_CENTRIFUGED；CRUSHED_PURIFIED→CRUSHED_CENTRIFUGED
     * <li>SIFTER：CRUSHED_PURIFIED→DUST
     * <li>MAGNETIC_SEPARATOR：DUST_PURE→DUST
     * <li>FURNACE：任意非 UNKNOWN 形态→INGOT
     * </ul>
     * 其余「链步 × 形态」组合不登记，由 {@link #next(Form, ChainLink)} 透传。
     */
    private static Map<ChainLink, Map<Form, Form>> buildTransitions() {
        Map<ChainLink, Map<Form, Form>> table = new EnumMap<>(ChainLink.class);
        // 破碎与锤砸同表（均为「锤/磨」类前驱转换），共享同一不可变内层实例。
        Map<Form, Form> crushHammer = rules(
            Form.ORE,
            Form.CRUSHED,
            Form.CRUSHED,
            Form.DUST_IMPURE,
            Form.CRUSHED_PURIFIED,
            Form.DUST_PURE,
            Form.CRUSHED_CENTRIFUGED,
            Form.DUST);
        table.put(ChainLink.CRUSH, crushHammer);
        table.put(ChainLink.HAMMER, crushHammer);
        // 水洗与化洗同表（均只做 CRUSHED→CRUSHED_PURIFIED），共享同一不可变内层实例。
        Map<Form, Form> bath = rules(Form.CRUSHED, Form.CRUSHED_PURIFIED);
        table.put(ChainLink.ORE_WASH, bath);
        table.put(ChainLink.CHEM_BATH, bath);
        table.put(
            ChainLink.SIMPLE_WASH,
            rules(Form.CRUSHED, Form.CRUSHED_PURIFIED, Form.DUST_IMPURE, Form.DUST, Form.DUST_PURE, Form.DUST));
        table.put(ChainLink.CENTRIFUGE, rules(Form.DUST_IMPURE, Form.DUST, Form.DUST_PURE, Form.DUST));
        table.put(
            ChainLink.THERMOCENTRIFUGE,
            rules(Form.CRUSHED, Form.CRUSHED_CENTRIFUGED, Form.CRUSHED_PURIFIED, Form.CRUSHED_CENTRIFUGED));
        table.put(ChainLink.SIFTER, rules(Form.CRUSHED_PURIFIED, Form.DUST));
        table.put(ChainLink.MAGNETIC_SEPARATOR, rules(Form.DUST_PURE, Form.DUST));
        // 熔炉：任意非 UNKNOWN 形态→INGOT（含 INGOT→INGOT；UNKNOWN 不登记故透传）。
        table.put(
            ChainLink.FURNACE,
            rules(
                Form.ORE,
                Form.INGOT,
                Form.CRUSHED,
                Form.INGOT,
                Form.CRUSHED_PURIFIED,
                Form.INGOT,
                Form.CRUSHED_CENTRIFUGED,
                Form.INGOT,
                Form.DUST_IMPURE,
                Form.INGOT,
                Form.DUST_PURE,
                Form.INGOT,
                Form.DUST,
                Form.INGOT,
                Form.INGOT,
                Form.INGOT));
        return Collections.unmodifiableMap(table);
    }

    /**
     * 按「前驱, 后继, 前驱, 后继, …」成对序列构建单链步的不可变形态规则表。
     *
     * @param pairs 偶数个形态参数
     * @return 不可变的形态规则表
     * @throws IllegalArgumentException 参数个数为奇数时
     */
    private static Map<Form, Form> rules(Form... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("规则表必须为偶数个形态参数");
        EnumMap<Form, Form> map = new EnumMap<>(Form.class);
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }
}
