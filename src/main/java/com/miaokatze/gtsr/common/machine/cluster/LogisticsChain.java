package com.miaokatze.gtsr.common.machine.cluster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.common.collect.ImmutableList;

/**
 * 物流链最小骨架：一个物流模块（{@link MTEBasicLogisticsUnit}）持有的可变有序 ChainLink 表，
 * 同一 link 允许重复出现（UI 显示「在链×N」）。
 * <p>
 * 本切片只保证可变有序表语义；M4 批在本骨架上扩展：链有效性验证（能否产出最终纯净粉/有效终端）、
 * 预设载入（{@link #setLinks}）与 NBT 持久化。
 * <p>
 * 线约定：仅主线程（游戏逻辑 tick）读写；{@link #getLinks()} 返回 live 视图，调用方遍历期间的
 * 结构变更责任在调用方。
 */
public final class LogisticsChain {

    /** 有序可重复链表（live 视图直接暴露）。 */
    private final List<ChainLink> links = new ArrayList<>();

    /** live 视图：外部只读遍历/按索引访问，勿缓存引用后假设其不可变。 */
    public List<ChainLink> getLinks() {
        return links;
    }

    public boolean isEmpty() {
        return links.isEmpty();
    }

    public int length() {
        return links.size();
    }

    /** 追加链尾；null link 无意义，静默忽略。 */
    public void append(ChainLink link) {
        if (link != null) links.add(link);
    }

    /** 按索引删除；越界安全（不抛不删）。 */
    public void removeAt(int index) {
        if (index >= 0 && index < links.size()) links.remove(index);
    }

    /** 位置调整：direction=-1 左移 / +1 右移；索引越界或移出边界时安全忽略（不抛不动）。 */
    public void move(int index, int direction) {
        if (index < 0 || index >= links.size()) return;
        int target = index + direction;
        if (target < 0 || target >= links.size()) return;
        Collections.swap(links, index, target);
    }

    public void clear() {
        links.clear();
    }

    /** 整表替换（预设载入用）：只拷贝元素、不持外部列表引用；null 等价 clear。 */
    public void setLinks(List<ChainLink> newLinks) {
        links.clear();
        if (newLinks != null) links.addAll(newLinks);
    }

    /** 「在链×N」计数：按 equals 匹配（null 入参计链中 null 元素个数，正常链不含 null）。 */
    public int countOf(ChainLink link) {
        int count = 0;
        for (ChainLink l : links) {
            if (Objects.equals(l, link)) count++;
        }
        return count;
    }

    // ---- 预设（固定 6 条，顺序即 GUI 显示序；来源 plan-prompt §5.2） ----

    /** 预设条数（固定 6，GUI 预设栏容量与 {@link #getPresetLinks} 合法下标上限）。 */
    public static final int PRESET_COUNT = 6;

    /**
     * 预设链表（不可变，双 层）：外层下标 0..5 即 GUI 显示序，内层为该预设的有序链步。
     * 本工程经 Jabel 编译到 J8 字节码（语法现代化、stdlib 仍 J8，无 {@code java.util.List.of}），
     * 故不可变集合用 Guava {@link ImmutableList}（同包 {@link ClusterStructureDef} 同款用法）。
     */
    private static final List<List<ChainLink>> PRESETS = ImmutableList.of(
        // 1 极速成粉：粉碎→锻造锤→简易洗（依赖 GT++ 简易洗口径：dustImpure→dust 终态）
        ImmutableList.of(ChainLink.CRUSH, ChainLink.HAMMER, ChainLink.SIMPLE_WASH),
        // 2 快速成锭：+熔炉（任意形态→ingot 终态）
        ImmutableList.of(ChainLink.CRUSH, ChainLink.HAMMER, ChainLink.SIMPLE_WASH, ChainLink.FURNACE),
        // 3 离心副产：粉碎→粉碎→离心（crushed→dustImpure→离心→dust 终态）
        ImmutableList.of(ChainLink.CRUSH, ChainLink.CRUSH, ChainLink.CENTRIFUGE),
        // 4 洗矿副产：粉碎→洗矿→粉碎→离心（crushedPurified→磨→dustPure→离心→dust 终态）
        ImmutableList.of(ChainLink.CRUSH, ChainLink.ORE_WASH, ChainLink.CRUSH, ChainLink.CENTRIFUGE),
        // 5 热离副产：粉碎→洗矿→热力离心→粉碎（crushedCentrifuged→磨→dust 终态）
        ImmutableList.of(ChainLink.CRUSH, ChainLink.ORE_WASH, ChainLink.THERMOCENTRIFUGE, ChainLink.CRUSH),
        // 6 典型筛选：粉碎→洗矿→筛选（crushedPurified→筛选→dust+宝石 终态，仅含 gem 材料）
        ImmutableList.of(ChainLink.CRUSH, ChainLink.ORE_WASH, ChainLink.SIFTER));

    /**
     * 取预设链（预设载入用：返回值经 {@link #setLinks} 注入物流链，本表不可变故可安全共享）。
     *
     * @param index 预设下标 [0, {@link #PRESET_COUNT})
     * @return 该预设的不可变有序链步表（元素引用级共享，调用方不得修改）
     * @throws IllegalArgumentException 下标越界
     */
    public static List<ChainLink> getPresetLinks(int index) {
        checkPresetIndex(index);
        return PRESETS.get(index);
    }

    /**
     * 取预设名的本地化 key，形如 {@code gtsr.gui.cluster.preset.1} .. {@code gtsr.gui.cluster.preset.6}。
     *
     * @param index 预设下标 [0, {@link #PRESET_COUNT})
     * @return 本地化 key
     * @throws IllegalArgumentException 下标越界
     */
    public static String getPresetNameKey(int index) {
        checkPresetIndex(index);
        return "gtsr.gui.cluster.preset." + (index + 1);
    }

    private static void checkPresetIndex(int index) {
        if (index < 0 || index >= PRESET_COUNT) {
            throw new IllegalArgumentException("preset index out of range [0," + PRESET_COUNT + "): " + index);
        }
    }

    // ---- 有效性（结构性：FSM 终态判定） ----

    /**
     * 结构性有效性：以并集状态机（{@link ClusterChainFSM}）从原矿推演本链，
     * 终态 ∈ {dust, ingot} 即有效；与 GUI 推演器/服务器执行器共用同一状态机。
     */
    public boolean isValidStructure() {
        return ClusterChainFSM.isTerminal(ClusterChainFSM.simulate(links));
    }

    /** @return 有效返回 {@code null}；否则返回 {@code "gtsr.gui.cluster.chain.invalid_not_terminal"}。 */
    public String getInvalidReasonKey() {
        return isValidStructure() ? null : "gtsr.gui.cluster.chain.invalid_not_terminal";
    }

    // ---- link 可用性（在场工作模块 + 磁选/热离通电 + GT++ 简易洗） ----

    /**
     * link 可用性（编辑器灰化口径）：
     * <ol>
     * <li>SIMPLE_WASH 且 GT++ 简易洗配方图缺失 → 不可用；</li>
     * <li>拓扑中无该链步所需工作单元（{@link ChainLink#getRequiredUnitClass()} 计数为 0）→ 不可用；</li>
     * <li>所需工作单元全部在场但均未成型（{@code !isModuleEnabled()}，含自身结构未成型/断电）→
     * 不可用：需持续供电链步（磁选/热离）报 locked_power，其余报 locked_unformed；</li>
     * <li>其余可用。link 或 topology 为 null 视为无在场模块 → 不可用。</li>
     * </ol>
     */
    public static boolean isLinkAvailable(ChainLink link, ClusterTopology topology) {
        return getLinkLockReasonKey(link, topology) == null;
    }

    /**
     * link 锁定原因的本地化 key（判定顺序同 {@link #isLinkAvailable}）。
     *
     * @return 可用返回 {@code null}；否则按序返回
     *         {@code gtsr.gui.cluster.link.locked_simple_wash} /
     *         {@code gtsr.gui.cluster.link.locked_module} /
     *         {@code gtsr.gui.cluster.link.locked_unformed} /
     *         {@code gtsr.gui.cluster.link.locked_power}
     */
    public static String getLinkLockReasonKey(ChainLink link, ClusterTopology topology) {
        if (link == null || topology == null) return "gtsr.gui.cluster.link.locked_module";
        if (link == ChainLink.SIMPLE_WASH && !ChainLink.isSimpleWashAvailable()) {
            return "gtsr.gui.cluster.link.locked_simple_wash";
        }
        if (topology.countUnits(link.getRequiredUnitClass()) <= 0) {
            return "gtsr.gui.cluster.link.locked_module";
        }
        if (!hasEnabledUnit(link.getRequiredUnitClass(), topology)) {
            return link.requiresContinuousPower() ? "gtsr.gui.cluster.link.locked_power"
                : "gtsr.gui.cluster.link.locked_unformed";
        }
        return null;
    }

    /** 遍历拓扑单元，判定是否存在「所需类型且 {@code isModuleEnabled()}」的单元（通电判定）。 */
    private static boolean hasEnabledUnit(Class<? extends MTEClusterUnitBase> requiredClass, ClusterTopology topology) {
        for (MTEClusterUnitBase unit : topology.getUnits()) {
            if (requiredClass.isInstance(unit) && unit.isModuleEnabled()) return true;
        }
        return false;
    }

    // ---- 可执行判定与 NBT 序列化 ----

    /**
     * 可执行判定：非空 && {@link #isValidStructure()} && 链上全部 link 可用
     * （{@link #isLinkAvailable}；topology 为 null 时按全 link 不可用处理，返回 false）。
     */
    public boolean isExecutable(ClusterTopology topology) {
        if (isEmpty() || !isValidStructure()) return false;
        for (ChainLink link : links) {
            if (!isLinkAvailable(link, topology)) return false;
        }
        return true;
    }

    /**
     * NBT 序列化：按链序输出各 link 的 ordinal 数组（空链返回长度 0 数组）。
     */
    public int[] toOrdinalArray() {
        int[] ordinals = new int[links.size()];
        for (int i = 0; i < links.size(); i++) {
            ordinals[i] = links.get(i)
                .ordinal();
        }
        return ordinals;
    }

    /**
     * NBT 反序列化：按序把 ordinal 还原为 link 并追加到新链；越界（含负数）ordinal 静默丢弃，
     * {@code null} 数组返回空链。枚举增删后旧存档仍可安全载入（缺失项丢弃）。
     */
    public static LogisticsChain fromOrdinalArray(int[] ordinals) {
        LogisticsChain chain = new LogisticsChain();
        if (ordinals == null) return chain;
        ChainLink[] values = ChainLink.values();
        for (int ordinal : ordinals) {
            if (ordinal >= 0 && ordinal < values.length) chain.append(values[ordinal]);
        }
        return chain;
    }
}
