package com.miaokatze.gtsr.common.machine.cluster;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 集群拓扑：段/垫/槽模型簿记 + GUI 快照数据源。
 *
 * <p>
 * 模型：集群由「主段 + 0..19 个延伸段」纵向组成（T10：含基础共 20 段；segment=0 主段；延伸段
 * k=0..18 对应 segment=k+1，即 1..19），每段恰 3 个垫槽（{@link #PAD_WORKING} / {@link #PAD_BOOSTER} /
 * {@link #PAD_LOGISTICS}），槽位总量上限 {@link #SLOT_COUNT}=60。段锚/延伸偏移（局部深度主段
 * {@code [-7,+12]}=段 0、延伸段
 * {@code [13+8k,20+8k]}=段 k+1、extOffsetC(k)=-13-8k）的几何推导归
 * {@code ClusterStructureDef}（E1a），本类只管段/槽数据簿记。本类持有两份互补数据：
 * <ul>
 * <li>单元清单：结构扫描顺序收集的全部已 connect 单元（同一 TE 实例引用级去重）；</li>
 * <li>槽位登记表 [20][3]：segment×pad 坐标到单元的映射，空槽不登记，快照时按需产出。</li>
 * </ul>
 *
 * <p>
 * 生命周期与线程语义：仅服务器主线程访问，无需并发控制；<b>结构期重建、运行期只读</b>——结构成型时由
 * 总控先 {@link #clear()}，再逐单元 {@link #addUnit}/{@link #putSlot}，末尾
 * {@link #setSegmentCount} 落定段数；成型后（含 GUI 每 tick 快照）只读，直到下次结构重检整体重建。
 *
 * <p>
 * 本类为纯数据容器：不反向依赖总控，也不做连接关系推导（单元链/链长语义归总控侧 rebuild 流程），
 * 仅提供坐标簿记与按类型查询。
 */
public final class ClusterTopology {

    /** 工作单元（Processing）垫位 ID。 */
    public static final int PAD_WORKING = 0;

    /** 增幅单元（Amplifier/Booster）垫位 ID。 */
    public static final int PAD_BOOSTER = 1;

    /** 物流单元（Logistics）垫位 ID。 */
    public static final int PAD_LOGISTICS = 2;

    /** 每段垫槽数（恒为 3，对应三个 PAD_* 常量）。 */
    private static final int PAD_COUNT = 3;

    /** 延伸段数上限（延伸段 k=0..18，共 19 段）。 */
    public static final int MAX_EXTENSION_SEGMENTS = 19;

    /** 段数上限（主段 + 延伸段总数 ≤ 20，即主段 0 + 延伸段 1..19）。 */
    public static final int MAX_SEGMENTS = 20;

    /** 槽位总量上限（段数上限 × 每段垫槽数 = 10 × 3 = 30，含空槽）。 */
    public static final int SLOT_COUNT = MAX_SEGMENTS * PAD_COUNT;

    /**
     * 槽位快照（不可变）：GUI 快照行的最小数据载体，{@link #unit} 为 null 即空槽。
     */
    public static final class SlotSnapshot {

        /** 所属段下标：0=主段，k+1=第 k+1 延伸段。 */
        public final int segment;

        /** 垫位 ID（{@link #PAD_WORKING}/{@link #PAD_BOOSTER}/{@link #PAD_LOGISTICS}）。 */
        public final int pad;

        /** 登记在该槽的单元；null = 空槽。 */
        public final MTEClusterUnitBase unit;

        /** 全参构造器。 */
        public SlotSnapshot(int segment, int pad, MTEClusterUnitBase unit) {
            this.segment = segment;
            this.pad = pad;
            this.unit = unit;
        }
    }

    /** 已收集的集群单元（保序：按结构扫描顺序即垫位出现顺序存放）。 */
    private final List<MTEClusterUnitBase> units = new ArrayList<>();

    /** 槽位登记表：[segment][pad]，null = 未登记/空槽。 */
    private final SlotSnapshot[][] slots = new SlotSnapshot[MAX_SEGMENTS][PAD_COUNT];

    /** 当前段数（主段 + 延伸段）：clear 后为 1（仅主段），由总控在 checkMachine 末尾写入。 */
    private int segmentCount = 1;

    /**
     * 延伸断裂位置（段下标）：-1 = 无断裂；≥ 1 = 延伸链第一个被判定为断裂的延伸段下标（1..9）。
     * 错误详情（识别到的后续结构、lang 键）由 E1a 的 {@code ClusterStructureError} 承载，本字段只
     * 保存断裂段位置供 GUI 展示；断裂后该段及之后的段不收集（不进单元清单/槽表）。
     */
    private int brokenExtensionSegment = -1;

    /** 纯数据容器，可直接实例化。 */
    public ClusterTopology() {}

    /** @return 空拓扑实例（总控字段初始化与结构重检复位共用）。 */
    public static ClusterTopology empty() {
        return new ClusterTopology();
    }

    /** 清空单元清单与全部槽位登记，段数复位为 1、断裂记录复位为无（结构破坏/重检时由总控调用；单元侧 disconnect 通知归总控 rebuild 流程）。 */
    public void clear() {
        units.clear();
        for (SlotSnapshot[] row : slots) {
            Arrays.fill(row, null);
        }
        segmentCount = 1;
        brokenExtensionSegment = -1;
    }

    /**
     * 收集一个单元（同一 TE 实例引用级去重；只入清单，不动槽位）。
     *
     * @param unit 待收集单元
     * @return true = 新增成功；false = 已存在被跳过
     */
    public boolean addUnit(MTEClusterUnitBase unit) {
        for (MTEClusterUnitBase existing : units) {
            if (existing == unit) return false;
        }
        return units.add(unit);
    }

    /**
     * 登记槽位（unit 可 null——传 null 即显式清空该槽）。同一 TE 实例只保留最新槽位：若该单元此前
     * 登记在别的槽，旧槽自动置空。允许登记在 segment ≥ 当前段数的段（段数随后由
     * {@link #setSegmentCount} 在 checkMachine 末尾统一落定，未落定前不出现在快照中）。
     *
     * @param segment 段下标 [0, 10)（0=主段，1..9=延伸段 k+1）
     * @param pad     垫位 ID [0, 3)
     * @param unit    登记单元，null = 空槽
     */
    public void putSlot(int segment, int pad, MTEClusterUnitBase unit) {
        checkSegment(segment);
        checkPad(pad);
        if (unit != null) {
            for (int s = 0; s < MAX_SEGMENTS; s++) {
                for (int p = 0; p < PAD_COUNT; p++) {
                    if (slots[s][p] != null && slots[s][p].unit == unit) slots[s][p] = null;
                }
            }
        }
        slots[segment][pad] = unit == null ? null : new SlotSnapshot(segment, pad, unit);
    }

    /**
     * 落定段数（主段 + 延伸段数），总控在 checkMachine 末尾写入；快照
     * {@link #getSlots()} 以此为准只产出 segment &lt; 段数的段。
     *
     * @param segmentCount 段数 [1, 10]（1=仅主段，10=主段+9 延伸段满配）
     */
    public void setSegmentCount(int segmentCount) {
        if (segmentCount < 1 || segmentCount > MAX_SEGMENTS) {
            throw new IllegalArgumentException("segmentCount out of range [1," + MAX_SEGMENTS + "]: " + segmentCount);
        }
        this.segmentCount = segmentCount;
    }

    /** @return 当前段数（主段 + 延伸段，恒 ≥ 1）。 */
    public int getSegmentCount() {
        return segmentCount;
    }

    /** @return 延伸段数 = 段数 - 1（最小 0，最大 {@link #MAX_EXTENSION_SEGMENTS}；无延伸段时恰为 0）。 */
    public int getExtensionCount() {
        return Math.max(0, segmentCount - 1);
    }

    /**
     * 记录延伸断裂位置（总控 checkMachine 延伸链检查时写入）：第一个缺失/不完整且其后仍有可识别
     * 延伸结构的段。错误详情由 E1a 的 {@code ClusterStructureError} 承载，本类只保存断裂段下标供
     * GUI 展示。每次结构重检 {@link #clear()} 复位为 -1，之后按最新扫描结果重写。
     *
     * @param segment 断裂的延伸段下标 [1, 9]（主段不成型不产生断裂记录），-1 = 清除断裂记录
     */
    public void setBrokenExtensionSegment(int segment) {
        if (segment == -1) {
            this.brokenExtensionSegment = -1;
            return;
        }
        if (segment < 1 || segment >= MAX_SEGMENTS) {
            throw new IllegalArgumentException(
                "broken extension segment out of range [1," + MAX_EXTENSION_SEGMENTS + "]: " + segment);
        }
        this.brokenExtensionSegment = segment;
    }

    /** @return 延伸断裂段下标（-1 = 无断裂；≥ 1 = 断裂发生在该延伸段，其后段未收集）。 */
    public int getBrokenExtensionSegment() {
        return brokenExtensionSegment;
    }

    /** @return 单元列表的 live 视图（已 connect 的全部单元；调用方不得缓存引用，遍历期间禁止增删）。 */
    public List<MTEClusterUnitBase> getUnits() {
        return units;
    }

    /**
     * GUI 快照数据源：按 segment 升序、pad 升序产出全部槽位，每段恰 3 槽（含空槽），
     * 因此快照行数恒 = 段数 × 3 ≤ {@link #SLOT_COUNT}（满配 20 段恰 60 槽，全部可编码；
     * 注意终端解码端 ClusterTerminalData.SLOT_COUNT 仍为冻结值 30，T10 扩段同步归主代理合并——
     * 见切片 B manifest 报备）。
     * 每次调用新建列表副本，元素本身不可变，可安全跨 tick 持有；完整快照 DTO 归 GUI 批（E4/E6）。
     *
     * @return 槽位快照副本（只产出 segment &lt; {@link #getSegmentCount()} 的段）
     */
    public List<SlotSnapshot> getSlots() {
        List<SlotSnapshot> result = new ArrayList<>(segmentCount * PAD_COUNT);
        for (int s = 0; s < segmentCount; s++) {
            for (int p = 0; p < PAD_COUNT; p++) {
                SlotSnapshot slot = slots[s][p];
                result.add(slot != null ? slot : new SlotSnapshot(s, p, null));
            }
        }
        return result;
    }

    /**
     * 查询单元槽。
     *
     * @param segment 段下标
     * @param pad     垫位 ID
     * @return 该槽单元；空槽或下标越界返回 null
     */
    public MTEClusterUnitBase getUnitAt(int segment, int pad) {
        if (segment < 0 || segment >= MAX_SEGMENTS || pad < 0 || pad >= PAD_COUNT) return null;
        SlotSnapshot slot = slots[segment][pad];
        return slot == null ? null : slot.unit;
    }

    /**
     * 按类型计数（instanceof 语义，含子类）。
     *
     * @param type 单元类型（{@link MTEClusterUnitBase} 的任意子类）
     * @return 该类型单元的数量
     */
    public int countUnits(Class<? extends MTEClusterUnitBase> type) {
        int count = 0;
        for (MTEClusterUnitBase unit : units) {
            if (type.isInstance(unit)) count++;
        }
        return count;
    }

    /** @return 全部物流单元（每次调用新建列表，非 live 视图）。 */
    public List<MTEBasicLogisticsUnit> getLogisticsUnits() {
        List<MTEBasicLogisticsUnit> result = new ArrayList<>();
        for (MTEClusterUnitBase unit : units) {
            if (unit instanceof MTEBasicLogisticsUnit logistics) result.add(logistics);
        }
        return result;
    }

    /** @return 全部增幅单元（每次调用新建列表，非 live 视图）。 */
    public List<MTEBasicAmplifierUnit> getBoosterUnits() {
        List<MTEBasicAmplifierUnit> result = new ArrayList<>();
        for (MTEClusterUnitBase unit : units) {
            if (unit instanceof MTEBasicAmplifierUnit amplifier) result.add(amplifier);
        }
        return result;
    }

    /**
     * 移除一个单元：从清单剔除（引用级匹配）并把它登记的所有槽位置空；单元不在清单时仍会清理其槽位并返回 false。
     *
     * @param unit 待移除单元
     * @return true = 清单中确有该单元并被移除；false = 本就不在清单
     */
    public boolean removeUnit(MTEClusterUnitBase unit) {
        boolean removed = units.removeIf(existing -> existing == unit);
        for (int s = 0; s < MAX_SEGMENTS; s++) {
            for (int p = 0; p < PAD_COUNT; p++) {
                if (slots[s][p] != null && slots[s][p].unit == unit) slots[s][p] = null;
            }
        }
        return removed;
    }

    /**
     * 查询单元登记的垫位。
     *
     * @param unit 目标单元
     * @return 垫位 ID（PAD_*）；未登记任何槽返回 -1
     */
    public int getUnitPad(MTEClusterUnitBase unit) {
        for (int s = 0; s < MAX_SEGMENTS; s++) {
            for (int p = 0; p < PAD_COUNT; p++) {
                if (slots[s][p] != null && slots[s][p].unit == unit) return p;
            }
        }
        return -1;
    }

    /**
     * 查询单元登记的段。
     *
     * @param unit 目标单元
     * @return 段下标（0=主段，k+1=第 k+1 延伸段）；未登记任何槽返回 -1
     */
    public int getUnitSegment(MTEClusterUnitBase unit) {
        for (int s = 0; s < MAX_SEGMENTS; s++) {
            for (int p = 0; p < PAD_COUNT; p++) {
                if (slots[s][p] != null && slots[s][p].unit == unit) return s;
            }
        }
        return -1;
    }

    private static void checkSegment(int segment) {
        if (segment < 0 || segment >= MAX_SEGMENTS) {
            throw new IllegalArgumentException("segment out of range [0," + MAX_SEGMENTS + "): " + segment);
        }
    }

    private static void checkPad(int pad) {
        if (pad < 0 || pad >= PAD_COUNT) {
            throw new IllegalArgumentException("pad out of range [0," + PAD_COUNT + "): " + pad);
        }
    }
}
