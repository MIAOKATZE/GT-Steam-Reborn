package com.miaokatze.gtsr.client.gui.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;

import com.miaokatze.gtsr.client.gui.terminal.GuiClusterTerminalScreen.ClusterPage;
import com.miaokatze.gtsr.client.terminal.ClusterTerminalClientCache;
import com.miaokatze.gtsr.common.machine.cluster.ChainLink;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams;
import com.miaokatze.gtsr.common.terminal.ClusterTerminalData;
import com.miaokatze.gtsr.common.util.GtsrNumFormat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 集群终端·第五页「性能」（v1.20.16 切片 2 全面改造：行集对齐模拟稿三列版式 + ▶ 数值推导块 +
 * 参数级悬浮 tooltip + 折行与行模型映射 + 增幅液 L/矿·L/批 新口径 + 断供失效显示接线）。
 *
 * <p>
 * 版面（UI 唯一权威 = plan/ui/cluster-perf-detail-v1.20.16/mockup.html）：页标题 + 三等栏
 * （栏距 4）各一个 {@link GtsrGuiList}（行高 11），栏内组标题行=金粗（
 * {@code gtsr.terminal.perf.group.*}），数据行 0.7f，推导小字行 0.6f 缩进；超宽行经
 * {@link GtsrGuiList#wrapLine} 按宽折行（ellipsis 末位兜底）。三列分组：
 * <ul>
 * <li>栏 1【运行汇总】：单批耗时/并行数/吞吐（均 ▶ 推导）+ 实际加权公式（KEY_F_FORMULA 折行直显）
 * + 逐物流模块摘要（FMOD 令牌，旧服务端缺令牌整块省略）+ 热量 + 润滑油 ▶ + 实时吞吐/累计处理；</li>
 * <li>栏 2【蒸汽口径 + 集群详情】：总蒸汽耗（加权全链口径）+ 链蒸汽耗 ▶（LINK 逐步时间加权推导）
 * + 结算蒸汽 ▶ + 集群详情：润滑两段 ▶（集群+物流分量占比）、最近成功批记账流体（FLUID:i）、
 * 五增幅液行 ▶（单价 L/矿 · 本批 L/批 · 生效/失效/未安装徽章；已安装=单价+本批双段推导，
 * 未安装=四档单价表）；</li>
 * <li>栏 3【链路概览 + 增幅汇总 + 增幅实耗】：KEY_LE_CHAINS 派生四行 + 增幅汇总
 * （状态行 生效 x · 失效 y 数据源 KEY_BO_SUM；速度/并行/主/副/节汽合计；蒸汽乘子 ▶ 逐台连乘推导）
 * + 增幅实耗 #n = 单价×本批矿数 L/批 ▶（KEY_BO_COST 首字段=单价×10 定点）。</li>
 * </ul>
 *
 * <p>
 * <b>行模型（v1.20.16 行映射重构，R5 防错位）</b>：旧「逻辑行集 + 四组平行数组」改为
 * {@link PerfRow} 物理行记录——构建期先产出逻辑行（正文 + 词条键 + 小字标记 + tooltip 键），
 * 再经 {@link GtsrGuiList#wrapLine} 展开为物理行集，续行继承所属逻辑行的词条键/小字标记/tooltip，
 * 即全部行级元数据与物理行同下标对齐；点击展开与 tooltip 命中直接按物理行下标读取，
 * 折行后命中与逻辑行必然一致。仍保持 draw 每帧重建纪律（零构造期快照）。
 *
 * <p>
 * <b>▶ 推导块</b>：沿用 openEntryKey 全页互斥展开（选择变化即清空）；每块四段式
 * 通式（金 §e）→ 代入（白 §f）→ 分步（灰 §7）→ 结果（绿 §a，= 界面显示值），可附注（暗灰 §8）、
 * 块头（青 §b）；词条键命名 TIME/PAR/THRU/LUBE/CHAIN/SETTLE/LUB2/FLUID:i/BOOSTTYPE:o/MULT/COST:i。
 * 推导数据源白名单：KEY_F_TIME/KEY_F_PAR/KEY_F_THRU/KEY_F_DETAIL/KEY_F_FORMULA/KEY_LE_CHAINS/
 * KEY_BO_COST/KEY_BO_SUM/KEY_BO_LIVE/KEY_HEAT/KEY_LUBE/KEY_THRU + 客户端同 jar 公共常量
 * {@link ClusterParams}/{@link ChainLink}；服务端不下发推导文本，禁止引用协议外状态。
 *
 * <p>
 * <b>悬浮 tooltip</b>：行命中由 {@link GtsrGuiList} 行级 hover 计时维护（hoveredIndex），
 * 页绘制期对命中行登记 pendingTip，宿主 drawScreen 出剪刀后统一经 500ms 门槛绘制
 * （移开即消、无短时闪烁）；键规范 {@code gtsr.terminal.perf.tip.*}（文案由 lang 切片按
 * 模拟稿 data-tip 逐条落地）。
 *
 * <p>
 * <b>交互</b>：三列词条行点击互斥切换展开态（滚动条命中由列表内滚动条分支先行、滚轮走独立
 * handleWheel，互不冲突）；其余内容区点击一律消费防穿透；滚轮分栏转发鼠标所落栏；
 * 拖拽/释放转发各栏列表。cl.f.detail 未知令牌前向兼容丢弃。
 */
@SideOnly(Side.CLIENT)
final class ClusterPerfPage implements ClusterPage {

    // ==================== 几何与绘制常量 ====================

    /** 三等栏宽与栏距（582 = 191×3 + 4×2）。 */
    private static final int COL_W = (GuiClusterTerminalScreen.CONTENT_W - 2 * 4) / 3;
    private static final int COL_GAP = 4;
    /** 列表起始偏移（页标题行之下）与高度。 */
    private static final int LIST_DY = 14;
    private static final int LIST_H = GuiClusterTerminalScreen.CONTENT_H - LIST_DY;
    /** 数据行高（组标题行同行高，金粗区分）。 */
    private static final int ROW_H = 11;
    /** 数据行缩放（正文 0.7f / 推导小字 0.6f；折行宽度预算按此换算）。 */
    private static final float NORMAL_SCALE = 0.7f;
    private static final float SMALL_SCALE = 0.6f;
    /** 推导小字行左缩进（px；折行宽度预算同步扣除）。 */
    private static final int SMALL_INDENT = 12;

    // ==================== ▶ 词条互斥键（TIME/PAR/THRU/LUBE/CHAIN/SETTLE/LUB2/FLUID:i/BOOSTTYPE:o/MULT/COST:i）
    // ====================

    private static final String KEY_TIME = "TIME";
    private static final String KEY_PAR = "PAR";
    private static final String KEY_THRU = "THRU";
    private static final String KEY_LUBE = "LUBE";
    private static final String KEY_CHAIN = "CHAIN";
    private static final String KEY_SETTLE = "SETTLE";
    private static final String KEY_LUB2 = "LUB2";
    private static final String KEY_FLUID_PREFIX = "FLUID:";
    private static final String KEY_BOOST_PREFIX = "BOOSTTYPE:";
    private static final String KEY_MULT = "MULT";
    private static final String KEY_COST_PREFIX = "COST:";

    // ==================== 状态 ====================

    private final GuiClusterTerminalScreen host;
    /** 三栏滚动列表（惰性重建范式）。 */
    private final GtsrGuiList[] colLists = new GtsrGuiList[3];
    /** 各栏列表左缘几何快照（top/width/height 三栏共享）。 */
    private final int[] colListLefts = new int[3];
    private int colListTop = -1;
    private int colListWidth = -1;
    private int colListHeight = -1;
    /** 本帧三列物理行集（逻辑行经 wrapLine 折行展开；draw 每帧重建；live 每帧重读纪律）。 */
    private final List<List<PerfRow>> colFrameRows;
    /** 互斥展开态：当前展开词条键（见 KEY_* 常量；null=全折叠）。 */
    private String openEntryKey;
    /** 展开态身份哨兵：选中物流单元下标；变化即清 openEntryKey，防止展开状态跨选择泄漏。 */
    private int openEntrySel = -1;

    ClusterPerfPage(GuiClusterTerminalScreen host) {
        this.host = host;
        this.colFrameRows = new ArrayList<List<PerfRow>>();
        for (int i = 0; i < 3; i++) {
            this.colFrameRows.add(Collections.<PerfRow>emptyList());
        }
    }

    // ==================== 绘制 ====================

    @Override
    public void draw(int ox, int oy, int mx, int my, float z) {
        GuiClusterTerminalScreen.drawScaledText(
            font(),
            EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + tr("gtsr.terminal.perf.title"),
            ox,
            oy,
            0.7f,
            GtsrGuiPalette.TEXT_ACCENT);
        // 列表先行惰性重建（折行宽度预算依赖 rowTextWidthCap），再三列行集每帧重建
        for (int col = 0; col < 3; col++) {
            ensureColList(col, ox + col * (COL_W + COL_GAP), oy + LIST_DY, COL_W, LIST_H);
        }
        DetailFrame frame = parseDetail(ClusterTerminalClientCache.getFDetail(""));
        this.colFrameRows.set(0, wrapRows(0, buildSummaryLines(frame)));
        this.colFrameRows.set(1, wrapRows(1, buildSteamDetailLines(frame)));
        this.colFrameRows.set(2, wrapRows(2, buildChainsBoostLines()));
        for (int col = 0; col < 3; col++) {
            this.colLists[col].draw(mx, my, z);
        }
    }

    /** 三栏列表惰性重建（几何快照比对，链路页 ensurePerfList 同范式）。 */
    private void ensureColList(int col, int left, int top, int width, int height) {
        if (this.colLists[col] != null && this.colListLefts[col] == left
            && this.colListTop == top
            && this.colListWidth == width
            && this.colListHeight == height) {
            return;
        }
        this.colListLefts[col] = left;
        this.colListTop = top;
        this.colListWidth = width;
        this.colListHeight = height;
        GtsrGuiList list = new GtsrGuiList(this.host, left, top, width, height, ROW_H);
        final int c = col;
        list.setRowSource(
            () -> this.colFrameRows.get(c)
                .size());
        list.setRowPainter((index, x, y, mouseX, mouseY) -> paintPerfRow(c, index, x, y, mouseX, mouseY));
        // 三列词条行点击 = 互斥切换展开态（滚动条命中由列表滚动条分支先行处理，不冲突）
        list.setRowListener((index, mouseX, mouseY, button) -> {
            List<PerfRow> rows = this.colFrameRows.get(c);
            if (index < 0 || index >= rows.size()) return;
            String key = rows.get(index).entryKey;
            if (key == null) return; // 非词条行（组标题/推导小字/普通数据行）
            this.openEntryKey = key.equals(this.openEntryKey) ? null : key;
        });
        this.colLists[col] = list;
    }

    /**
     * 逻辑行 → 物理行展开（行模型映射核心）：每条逻辑行经 {@link GtsrGuiList#wrapLine} 按宿主
     * 缩放换算宽度折行，产出的全部物理行继承所属逻辑行的词条键/小字标记/tooltip 键——
     * 元数据与物理行同下标，折行后点击/tooltip 命中不漂移（R5）。
     */
    private List<PerfRow> wrapRows(int col, List<PerfRow> logical) {
        GtsrGuiList list = this.colLists[col];
        int cap = list.rowTextWidthCap();
        int capNormal = (int) (cap / NORMAL_SCALE);
        int capSmall = (int) ((cap - SMALL_INDENT) / SMALL_SCALE);
        List<PerfRow> out = new ArrayList<>();
        for (PerfRow row : logical) {
            List<String> lines = GtsrGuiList.wrapLine(font(), row.text, row.small ? capSmall : capNormal);
            for (String line : lines) {
                out.add(new PerfRow(line, row.entryKey, row.small, row.tipKey, row.tipExtra));
            }
        }
        return out;
    }

    /**
     * 单行绘制：数据行 0.7f（左缘 = {@link GtsrGuiList#ROW_PAD_LEFT}，宽度预算 =
     * {@link GtsrGuiList#rowTextWidthCap()} 按缩放换算）；推导小字行 0.6f 缩进暗色；
     * ellipsis 仅兜底单个不可断超宽单元；行命中（列表 hover 计时）登记参数级 tooltip。
     */
    private void paintPerfRow(int col, int index, int x, int y, int mx, int my) {
        List<PerfRow> rows = this.colFrameRows.get(col);
        if (index < 0 || index >= rows.size()) return;
        PerfRow row = rows.get(index);
        GtsrGuiList list = this.colLists[col];
        if (row.small) {
            int cap = (int) ((list.rowTextWidthCap() - SMALL_INDENT) / SMALL_SCALE);
            String text = GtsrGuiList.ellipsis(font(), row.text, cap);
            GuiClusterTerminalScreen
                .drawScaledText(font(), text, x + SMALL_INDENT, y + 2, SMALL_SCALE, GtsrGuiPalette.TEXT_MUTED);
        } else {
            int cap = (int) (list.rowTextWidthCap() / NORMAL_SCALE);
            String text = GtsrGuiList.ellipsis(font(), row.text, cap);
            GuiClusterTerminalScreen.drawScaledText(
                font(),
                text,
                x + GtsrGuiList.ROW_PAD_LEFT,
                y + 2,
                NORMAL_SCALE,
                GtsrGuiPalette.TEXT_BODY);
        }
        // 参数级 tooltip：仅命中行登记（键跨帧稳定），宿主出剪刀后统一按 500ms 门槛绘制
        if (row.tipKey != null && list.hoveredIndex() == index) {
            this.host.requestTooltip(col + ":" + index + ":" + row.tipKey, tipLines(row));
        }
    }

    /** tooltip 行集：lang 值按 \n 分行 + 构建期附加行（如增幅实耗公式串）。 */
    private static List<String> tipLines(PerfRow row) {
        List<String> out = new ArrayList<>();
        Collections.addAll(out, tr(row.tipKey).split("\\n"));
        if (row.tipExtra != null) out.addAll(row.tipExtra);
        return out;
    }

    // ==================== 行模型 ====================

    /**
     * 单条物理行（折行后与列表行同下标）：正文 + 所属词条键（null=普通行，点击不切换展开）
     * + 小字标记（0.6f 缩进）+ tooltip 键（{@code gtsr.terminal.perf.tip.*}）+ tooltip 附加行。
     * 续行继承所属逻辑行全部元数据（折行映射不漂移的关键不变量）。
     */
    private static final class PerfRow {

        /** 行正文（含 § 色码；已折行）。 */
        final String text;
        /** 所属词条互斥键（null=普通行）。 */
        final String entryKey;
        /** true=推导小字行（0.6f 缩进暗色）。 */
        final boolean small;
        /** 参数级 tooltip 键（null=无）。 */
        final String tipKey;
        /** tooltip 附加行（构建期定值，如实耗公式串；null=无）。 */
        final List<String> tipExtra;

        PerfRow(String text, String entryKey, boolean small, String tipKey, List<String> tipExtra) {
            this.text = text;
            this.entryKey = entryKey;
            this.small = small;
            this.tipKey = tipKey;
            this.tipExtra = tipExtra;
        }
    }

    /** 普通数据行。 */
    private static PerfRow plain(String text) {
        return new PerfRow(text, null, false, null, null);
    }

    /** 普通数据行（带参数级 tooltip）。 */
    private static PerfRow plainTip(String text, String tipKey) {
        return new PerfRow(text, null, false, tipKey, null);
    }

    /** 推导小字行（展开块内容行）。 */
    private static PerfRow small(String text) {
        return new PerfRow(text, null, true, null, null);
    }

    /** 可展开词条（正文 + 推导块行；draw 每帧重建的临时结构）。 */
    private static final class EntryRow {

        /** 互斥展开键（词条唯一；点击同键再点折叠）。 */
        final String key;
        /** 词条正文（不含行首箭头——落列时按展开态加 ▶/▼ 前缀）。 */
        String text;
        /** 推导块行（展开时缩进 0.6f 渲染；缺服务端真值段以 "--" 占位）。 */
        final List<String> expansion = new ArrayList<>();

        EntryRow(String key) {
            this.key = key;
        }
    }

    /** 词条落列：行首箭头（▶ 折叠 / ▼ 展开）+ 展开块小字行；词条行/续行均可带 tooltip。 */
    private void appendEntry(List<PerfRow> out, EntryRow entry, String tipKey, String tipExtraLine) {
        boolean open = entry.key.equals(this.openEntryKey);
        List<String> extra = tipExtraLine == null ? null : Collections.singletonList(tipExtraLine);
        out.add(
            new PerfRow(EnumChatFormatting.WHITE + (open ? "▼ " : "▶ ") + entry.text, entry.key, false, tipKey, extra));
        if (open) {
            for (String line : entry.expansion) {
                out.add(small(line));
            }
        }
    }

    /** 词条落列（无 tooltip 简版）。 */
    private void appendEntry(List<PerfRow> out, EntryRow entry) {
        appendEntry(out, entry, null, null);
    }

    // ==================== 栏 1【运行汇总】 ====================

    /**
     * 运行汇总（每帧重读）：单批耗时/并行数/吞吐 ▶ 推导词条 + 实际加权公式折行直显 +
     * 逐物流模块摘要（FMOD；旧服务端缺令牌整块省略）+ 热量 + 润滑油 ▶ + 实时吞吐/累计处理。
     */
    private List<PerfRow> buildSummaryLines(DetailFrame frame) {
        List<PerfRow> out = new ArrayList<>();
        out.add(plain(groupHeader("gtsr.terminal.perf.group.summary")));
        int timeRaw = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_F_TIME, 0);
        int parRaw = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_F_PAR, 0);
        int thruRaw = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_F_THRU, 0);
        int[] sum8 = summary();
        appendEntry(out, buildTimeEntry(frame, timeRaw, sum8), "gtsr.terminal.perf.tip.time", null);
        appendEntry(out, buildParEntry(parRaw, sum8), "gtsr.terminal.perf.tip.parallel", null);
        appendEntry(out, buildThruEntry(timeRaw, parRaw, thruRaw), "gtsr.terminal.perf.tip.thru", null);
        // 实际加权公式：标签 + 长串折行直显（ellipsis 兜底）
        out.add(
            plainTip(
                EnumChatFormatting.YELLOW + tr("gtsr.gui.cluster.link.perf.formula") + " =",
                "gtsr.terminal.perf.tip.formula"));
        out.add(
            plain(
                EnumChatFormatting.GREEN
                    + ClusterTerminalClientCache.getStr(ClusterTerminalData.KEY_F_FORMULA, "0 L/秒")));
        // 逐物流模块一行：cl.f.detail 的 FMOD 令牌（选择无关，服务端逐物流单元摘要）；缺令牌整块省略
        for (int[] fmod : frame.fmods) {
            String seg = fmod[1] < 0 ? "--" : String.valueOf(fmod[1]);
            out.add(
                plainTip(
                    EnumChatFormatting.WHITE + String.format(
                        tr("gtsr.terminal.perf.fmod"),
                        String.valueOf(fmod[0] + 1),
                        seg,
                        String.format("%.2f", fmod[2] / 100.0D),
                        String.valueOf(fmod[3]),
                        String.format("%.2f", fmod[4] / 100.0D),
                        GtsrNumFormat.grouped(Math.round(fmod[5] / 100.0D))),
                    "gtsr.terminal.perf.tip.fmod"));
        }
        out.add(
            plainTip(
                kvLine(
                    "gtsr.cluster.gui.card.heat",
                    ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_HEAT, 0) + "%"),
                "gtsr.terminal.perf.tip.heat"));
        appendEntry(out, buildLubeEntry(frame), "gtsr.terminal.perf.tip.lube", null);
        out.add(
            plainTip(
                kvLine(
                    "gtsr.cluster.gui.card.thru",
                    GtsrNumFormat.grouped(ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_THRU, 0)) + " 矿/秒"),
                "gtsr.terminal.perf.tip.thru_real"));
        out.add(
            plainTip(
                EnumChatFormatting.YELLOW + String.format(
                    tr("gtsr.cluster.gui.card.thru.total"),
                    GtsrNumFormat.grouped(ClusterTerminalClientCache.getLong(ClusterTerminalData.KEY_TOTAL, 0L))
                        + " 矿"),
                "gtsr.terminal.perf.tip.total"));
        return out;
    }

    /** 单批耗时词条（TIME）：通式 → 链步代入（LINK 服务端同源）→ 工作段/物流段 → 结果 = 显示值。 */
    private EntryRow buildTimeEntry(DetailFrame frame, int timeRaw, int[] sum8) {
        EntryRow entry = new EntryRow(KEY_TIME);
        entry.text = kvLine("gtsr.terminal.perf.time", f2(timeRaw / 100.0D) + " 秒");
        entry.expansion.add(derivHead(tr("gtsr.terminal.perf.time")));
        entry.expansion.add(EnumChatFormatting.YELLOW + "t = 工作段 ÷ (1+速度增幅) + 物流段时间");
        double workDivided = -1.0D;
        String logiText = null;
        if (!frame.linkSteps.isEmpty()) {
            entry.expansion.add(EnumChatFormatting.GRAY + "工作段各链步耗时（服务端同源，含档位与同类模块修正）：");
            double workSec = 0.0D;
            StringBuilder sumExpr = new StringBuilder();
            for (int i = 0; i < frame.linkSteps.size(); i++) {
                int[] step = frame.linkSteps.get(i);
                double sec = step[1] / 100.0D;
                workSec += sec;
                if (i > 0) sumExpr.append('+');
                sumExpr.append(f2(sec));
                entry.expansion
                    .add(EnumChatFormatting.WHITE + "T" + (i + 1) + " " + stepName(step[0]) + " = " + f2(sec) + " 秒");
            }
            workDivided = workSec / (1 + sum8[0] / 100.0D);
            entry.expansion.add(
                EnumChatFormatting.WHITE + "工作段 = ("
                    + sumExpr
                    + ") ÷ (1+"
                    + sum8[0]
                    + "%) = "
                    + f2(workDivided)
                    + " 秒");
        }
        if (frame.logiTimeX100 >= 0) {
            double logi = frame.logiTimeX100 / 100.0D;
            logiText = f2(logi);
            entry.expansion
                .add(EnumChatFormatting.WHITE + "物流段 = " + logiText + " 秒" + logiTierSuffix(frame.logiTimeX100));
        }
        String result;
        if (workDivided >= 0 && logiText != null) {
            result = "t = " + f2(workDivided) + " + " + logiText + " = " + f2(timeRaw / 100.0D) + " 秒";
        } else {
            result = "t = " + f2(timeRaw / 100.0D) + " 秒";
        }
        entry.expansion.add(EnumChatFormatting.GREEN + result);
        return entry;
    }

    /** 并行数词条（PAR）：物流基数[集群档] + Σ并行增幅（基数 = KEY_F_PAR − KEY_BO_SUM 并行位）。 */
    private EntryRow buildParEntry(int parRaw, int[] sum8) {
        EntryRow entry = new EntryRow(KEY_PAR);
        entry.text = kvLine("gtsr.cluster.gui.link.perf.parallel", String.valueOf(parRaw) + " 矿/批");
        entry.expansion.add(derivHead(tr("gtsr.cluster.gui.link.perf.parallel")));
        entry.expansion.add(EnumChatFormatting.YELLOW + "并行 = 物流基数[集群档] + Σ并行增幅");
        int bonus = sum8[1];
        int base = Math.max(0, parRaw - bonus);
        int baseIdx = matchIdx(ClusterParams.LOGISTICS_BASE_PARALLEL, base);
        String baseTxt = String.valueOf(base) + (baseIdx >= 0 ? "（" + tierNameByIdx(baseIdx) + "档）" : "");
        entry.expansion.add(EnumChatFormatting.WHITE + "= " + baseTxt + " + " + bonus + "（并行增幅）");
        entry.expansion.add(EnumChatFormatting.GREEN + "= " + parRaw + " 矿/批");
        entry.expansion
            .add(EnumChatFormatting.DARK_GRAY + "物流基数四档：" + joinTable(ClusterParams.LOGISTICS_BASE_PARALLEL));
        return entry;
    }

    /** 吞吐词条（THRU）：并行数 ÷ 单批耗时（结果 = KEY_F_THRU 显示值）。 */
    private EntryRow buildThruEntry(int timeRaw, int parRaw, int thruRaw) {
        EntryRow entry = new EntryRow(KEY_THRU);
        entry.text = kvLine("gtsr.cluster.gui.link.perf.thru", f2(thruRaw / 100.0D) + " 矿/秒");
        entry.expansion.add(derivHead(tr("gtsr.cluster.gui.link.perf.thru")));
        entry.expansion.add(EnumChatFormatting.YELLOW + "吞吐 = 并行数 ÷ 单批耗时");
        entry.expansion.add(EnumChatFormatting.WHITE + "= " + parRaw + " ÷ " + f2(timeRaw / 100.0D));
        entry.expansion.add(EnumChatFormatting.GREEN + "= " + f2(thruRaw / 100.0D) + " 矿/秒");
        return entry;
    }

    /** 润滑油词条（LUBE）：集群恒定段 + 物流工作段 = 合计（KEY_LUBE 显示值；两段不动，本次改造不改机制）。 */
    private EntryRow buildLubeEntry(DetailFrame frame) {
        EntryRow entry = new EntryRow(KEY_LUBE);
        int total = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_LUBE, 0);
        entry.text = kvLine("gtsr.cluster.gui.card.lube", GtsrNumFormat.grouped(total) + " L/秒");
        entry.expansion.add(derivHead(tr("gtsr.cluster.gui.card.lube") + "消耗"));
        entry.expansion.add(EnumChatFormatting.YELLOW + "润滑 = 集群恒定段[集群档] + 物流工作段");
        if (frame.lubeCluster != Integer.MIN_VALUE) {
            double cluster = frame.lubeCluster / 100.0D;
            double logi = frame.lubeLogi != Integer.MIN_VALUE ? frame.lubeLogi / 100.0D
                : Math.max(0.0D, total - cluster);
            entry.expansion
                .add(EnumChatFormatting.WHITE + "集群段 = " + fmtNum(cluster) + " L/秒" + clusterTierSuffix(cluster));
            entry.expansion.add(EnumChatFormatting.WHITE + "物流段 = " + fmtNum(logi) + " L/秒");
            entry.expansion.add(
                EnumChatFormatting.GREEN + "= "
                    + fmtNum(cluster)
                    + " + "
                    + fmtNum(logi)
                    + " = "
                    + GtsrNumFormat.grouped(total)
                    + " L/秒");
        } else {
            entry.expansion.add(EnumChatFormatting.WHITE + "合计（服务端值）= " + GtsrNumFormat.grouped(total) + " L/秒");
            entry.expansion.add(EnumChatFormatting.GREEN + "= " + GtsrNumFormat.grouped(total) + " L/秒");
        }
        entry.expansion.add(
            EnumChatFormatting.DARK_GRAY + tr("gtsr.terminal.perf.deriv.table")
                + "集群 "
                + joinTable(ClusterParams.CLUSTER_LUBRICANT_LPS)
                + " · 物流 "
                + joinTable(ClusterParams.LOGISTICS_UNIT_LUBRICANT_LPS)
                + "（L/秒）");
        return entry;
    }

    // ==================== 栏 2【蒸汽口径 + 集群详情（可展开词条）】 ====================

    /**
     * 蒸汽口径三行（总蒸汽耗=加权全链口径 / 链蒸汽耗 ▶ / 结算蒸汽 ▶）+ 集群详情词条：
     * 润滑两段 ▶、最近成功批记账流体（FLUID:i）、五增幅液行 ▶（单价 L/矿 · 本批 L/批 · 徽章）。
     */
    private List<PerfRow> buildSteamDetailLines(DetailFrame frame) {
        int selNow = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_SEL_LOGI, -1);
        if (selNow != this.openEntrySel) {
            this.openEntrySel = selNow;
            this.openEntryKey = null;
        }
        List<PerfRow> out = new ArrayList<>();
        out.add(plain(groupHeader("gtsr.terminal.perf.group.steam")));
        int totalRaw = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_F_TOTAL, 0);
        int chainRaw = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_F_STEAM, 0);
        out.add(
            plainTip(
                kvLine(
                    "gtsr.cluster.gui.link.perf.steam_total",
                    GtsrNumFormat.grouped(Math.round(totalRaw / 100.0D)) + " L/秒") + " "
                    + EnumChatFormatting.GRAY
                    + "· "
                    + tr("gtsr.terminal.perf.caliber.weighted"),
                "gtsr.terminal.perf.tip.steam_total"));
        appendEntry(out, buildChainEntry(frame, chainRaw), "gtsr.terminal.perf.tip.steam_chain", null);
        appendEntry(out, buildSettleEntry(totalRaw), "gtsr.terminal.perf.tip.steam_settle", null);
        out.add(plain(groupHeader("gtsr.terminal.perf.group.detail")));
        appendEntry(out, buildLub2Entry(frame), "gtsr.terminal.perf.tip.lube_split", null);
        for (EntryRow entry : buildFluidEntries(frame)) {
            appendEntry(out, entry);
        }
        List<int[]> costs = costRows();
        for (int o = 0; o < ClusterParams.BoosterType.values().length; o++) {
            appendEntry(out, buildBoosterTypeEntry(frame, o, costs), "gtsr.terminal.perf.tip.boost." + o, null);
        }
        return out;
    }

    /** 链蒸汽耗词条（CHAIN）：时间加权平均——Σ(Ci×Ti) ÷ Σ Ti，逐步值取 cl.f.detail LINK。 */
    private EntryRow buildChainEntry(DetailFrame frame, int chainRawX100) {
        EntryRow entry = new EntryRow(KEY_CHAIN);
        entry.text = kvLine(
            "gtsr.cluster.gui.link.perf.steam",
            GtsrNumFormat.grouped(Math.round(chainRawX100 / 100.0D)) + " L/秒") + " "
            + EnumChatFormatting.GRAY
            + "· "
            + tr("gtsr.terminal.perf.caliber.formula");
        entry.expansion.add(derivHead(tr("gtsr.cluster.gui.link.perf.steam") + "（时间加权平均）"));
        entry.expansion.add(EnumChatFormatting.YELLOW + "C = Σ(Ci×Ti) ÷ Σ Ti");
        if (frame.linkSteps.isEmpty()) {
            entry.expansion.add(EnumChatFormatting.GRAY + "--（未选中物流单元或空链）");
        } else {
            StringBuilder numExpr = new StringBuilder();
            StringBuilder denExpr = new StringBuilder();
            double num = 0.0D;
            double den = 0.0D;
            for (int i = 0; i < frame.linkSteps.size(); i++) {
                int[] step = frame.linkSteps.get(i);
                double c = step[2] / 100.0D;
                double t = step[1] / 100.0D;
                num += c * t;
                den += t;
                if (i > 0) {
                    numExpr.append(" + ");
                    denExpr.append(" + ");
                }
                numExpr.append(fmtNum(c))
                    .append('×')
                    .append(fmtNum(t));
                denExpr.append(fmtNum(t));
            }
            entry.expansion.add(EnumChatFormatting.WHITE + "= (" + numExpr + ") ÷ (" + denExpr + ")");
            entry.expansion.add(EnumChatFormatting.GRAY + "= " + fmtNum(num) + " ÷ " + fmtNum(den));
            entry.expansion.add(
                EnumChatFormatting.GREEN + "= "
                    + f2(den == 0.0D ? 0.0D : num / den)
                    + " ≈ "
                    + GtsrNumFormat.grouped(Math.round(chainRawX100 / 100.0D))
                    + " L/秒");
        }
        entry.expansion.add(EnumChatFormatting.DARK_GRAY + "Ci/Ti 为服务端同源逐步值（含单元档倍率与同类模块数修正）");
        return entry;
    }

    /** 结算蒸汽词条（SETTLE）：固定项 + 加权全链×蒸汽乘子×(1-节汽)（实际扣量 = KEY_STEAM 显示值）。 */
    private EntryRow buildSettleEntry(int totalRawX100) {
        EntryRow entry = new EntryRow(KEY_SETTLE);
        int steam = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_STEAM, 0);
        entry.text = kvLine("gtsr.cluster.gui.card.steam", GtsrNumFormat.grouped(steam) + " L/秒") + " "
            + EnumChatFormatting.GRAY
            + "· "
            + tr("gtsr.terminal.perf.caliber.settle");
        int[] sum8 = summary();
        entry.expansion.add(derivHead("结算蒸汽"));
        entry.expansion.add(EnumChatFormatting.YELLOW + "结算 = 固定项 + 加权全链 × 蒸汽乘子 × (1-节汽)（向上取整）");
        entry.expansion.add(
            EnumChatFormatting.WHITE + "加权全链（公式口径）= "
                + GtsrNumFormat.grouped(Math.round(totalRawX100 / 100.0D))
                + " L/秒");
        entry.expansion
            .add(EnumChatFormatting.WHITE + "蒸汽乘子 = ×" + f2(sum8[5] / 100.0D) + " · 节汽率 = -" + sum8[4] + "%");
        entry.expansion.add(EnumChatFormatting.GRAY + "固定项按集群档与延伸段结算（服务端结算口径）");
        entry.expansion.add(EnumChatFormatting.GREEN + "= " + GtsrNumFormat.grouped(steam) + " L/秒");
        entry.expansion.add(EnumChatFormatting.DARK_GRAY + "按实际蒸汽种类折算：过热÷2 · 超临界÷4 · 致密÷1000 起 · 乘子只作用于加权段");
        return entry;
    }

    /** 润滑两段词条（LUB2）：集群 + 物流分量与占比（物流段需选中物流单元，缺行 "--" 降级）。 */
    private EntryRow buildLub2Entry(DetailFrame frame) {
        EntryRow entry = new EntryRow(KEY_LUB2);
        boolean hasCluster = frame.lubeCluster != Integer.MIN_VALUE;
        boolean hasLogi = frame.lubeLogi != Integer.MIN_VALUE;
        String clusterTxt = hasCluster ? fmtX100(frame.lubeCluster) : "--";
        String logiTxt = hasLogi ? fmtX100(frame.lubeLogi) : "--";
        String clusterPct = hasCluster ? shareText(frame.lubeCluster, frame.ftotLube) : null;
        String logiPct = hasLogi ? shareText(frame.lubeLogi, frame.ftotLube) : null;
        entry.text = EnumChatFormatting.YELLOW + tr("gtsr.terminal.perf.lube.dual")
            + "："
            + EnumChatFormatting.GREEN
            + clusterTxt
            + " + "
            + logiTxt
            + " L/秒"
            + (clusterPct != null && logiPct != null
                ? EnumChatFormatting.GRAY + "（各占 " + clusterPct + "/" + logiPct + "）"
                : "");
        entry.expansion.add(EnumChatFormatting.AQUA + "分量：润滑两段");
        if (!hasCluster && !hasLogi) {
            entry.expansion.add(EnumChatFormatting.GRAY + "--（无服务端润滑分量数据）");
            return entry;
        }
        if (hasCluster) {
            entry.expansion.add(
                EnumChatFormatting.WHITE + "集群恒定段 = "
                    + clusterTxt
                    + " L/秒"
                    + clusterTierSuffix(frame.lubeCluster / 100.0D)
                    + (clusterPct != null ? " · 占 " + clusterPct : ""));
        }
        if (hasLogi) {
            entry.expansion.add(
                EnumChatFormatting.WHITE + "物流工作段 = " + logiTxt + " L/秒" + (logiPct != null ? " · 占 " + logiPct : ""));
        }
        String totalTxt = frame.ftotLube >= 0 ? fmtX100(frame.ftotLube)
            : (hasCluster && hasLogi ? fmtX100(frame.lubeCluster + frame.lubeLogi) : "--");
        entry.expansion.add(EnumChatFormatting.GREEN + "合计 " + totalTxt + " L/秒");
        entry.expansion.add(EnumChatFormatting.DARK_GRAY + "占比 = 分段 ÷ 两段合计 · 物流段需选中物流单元");
        return entry;
    }

    /** 最近成功批记账流体词条集（FLUID:i；正文占比分母 = FTOT 流体合计真值）。 */
    private static List<EntryRow> buildFluidEntries(DetailFrame frame) {
        List<EntryRow> out = new ArrayList<>();
        for (int i = 0; i < frame.fluidNames.size(); i++) {
            String name = frame.fluidNames.get(i);
            long liters = frame.fluidLiters.get(i)[0];
            EntryRow entry = new EntryRow(KEY_FLUID_PREFIX + i);
            String pct = shareText(liters, frame.ftotFluid);
            entry.text = pct == null
                ? EnumChatFormatting.GREEN
                    + String.format(tr("gtsr.terminal.f.detail.fluid"), name, GtsrNumFormat.grouped(liters))
                : EnumChatFormatting.GREEN
                    + String.format(tr("gtsr.terminal.f.detail.fluid.pct"), name, GtsrNumFormat.grouped(liters), pct);
            entry.expansion.add(tr("gtsr.terminal.perf.exp.fluid.head"));
            if (frame.ftotFluid >= 0) {
                entry.expansion.add(
                    String.format(
                        tr("gtsr.terminal.perf.exp.fluid.total"),
                        GtsrNumFormat.grouped(frame.ftotFluid),
                        frame.fluidNames.size()));
            }
            out.add(entry);
        }
        return out;
    }

    /**
     * 五增幅液词条（BOOSTTYPE:o，v1.20.16 L/矿 新口径）：正文 = 单价 L/矿 · 本批 L/批 +
     * 徽章（生效绿 / 失效红〔KEY_BO_LIVE 可用位〕/ 未安装灰）；已安装展开 = 单价推导
     * （基础表×施压×协同÷10）+ 本批推导（单价×KEY_F_PAR）；未安装展开 = 四档单价表。
     */
    private EntryRow buildBoosterTypeEntry(DetailFrame frame, int typeOrdinal, List<int[]> costs) {
        ClusterParams.BoosterType type = boosterType(typeOrdinal);
        String fluid = tr(type.getFluidLangKey());
        String typeLabel = boosterLabel(typeOrdinal);
        EntryRow entry = new EntryRow(KEY_BOOST_PREFIX + typeOrdinal);
        int typedX100 = 0;
        for (int[] boost : frame.boosts) {
            if (boost[0] == typeOrdinal) typedX100 = boost[1];
        }
        List<int[]> typeCosts = new ArrayList<>();
        for (int[] cost : costs) {
            if (cost.length >= 5 && cost[4] == typeOrdinal) typeCosts.add(cost);
        }
        boolean installed = typedX100 > 0 || !typeCosts.isEmpty();
        int par = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_F_PAR, 0);
        int synergy = synergyPct(type);
        if (!installed) {
            entry.text = EnumChatFormatting.YELLOW + fluid
                + "（"
                + typeLabel
                + "）："
                + EnumChatFormatting.RED
                + "-- "
                + EnumChatFormatting.GRAY
                + tr("gtsr.terminal.perf.boost.na")
                + " ["
                + tr("gtsr.terminal.perf.boost.na")
                + "]";
            entry.expansion.add(EnumChatFormatting.AQUA + "四档单价表（基础表 ÷ 10）");
            for (int tier = 0; tier < ClusterParams.TIER_COUNT; tier++) {
                int price = ClusterParams.amplifierFluidLps(type, tier);
                entry.expansion.add(
                    EnumChatFormatting.WHITE + tierNameByIdx(
                        tier) + "：基础 " + GtsrNumFormat.grouped(price * 10L) + " L/秒 → 单价 " + price + " L/矿");
            }
            entry.expansion.add(EnumChatFormatting.DARK_GRAY + "施压/协同：×(1+Σ施压%)×(1+(同种台数-1)×" + synergy + "%)");
            entry.expansion.add(
                EnumChatFormatting.DARK_GRAY
                    + (type == ClusterParams.BoosterType.SPEED || type == ClusterParams.BoosterType.PARALLEL
                        ? "蒸汽乘子：按自身档 ×1.2/1.4/1.8/2.0 逐台连乘（照旧）"
                        : "不产生蒸汽乘子"));
            return entry;
        }
        // 失效徽章（显示接线）：KEY_BO_LIVE 可用位逐单元，任一同型单元可用位=0 → 本型失效
        List<int[]> live = parseLive();
        boolean failing = false;
        for (int i = 0; i < costs.size(); i++) {
            int[] cost = costs.get(i);
            if (cost.length < 5 || cost[4] != typeOrdinal) continue;
            if (i < live.size() && live.get(i)[1] == 0) failing = true;
        }
        String priceTxt = typedX100 > 0 ? fmtX100(typedX100)
            : (typeCosts.isEmpty() ? "--" : rateText(sumPriceX10(typeCosts)));
        String batchTxt = typedX100 > 0 ? fmtBatchX100(typedX100, par)
            : (typeCosts.isEmpty() ? "--" : fmtBatchX10(sumPriceX10(typeCosts), par));
        entry.text = EnumChatFormatting.YELLOW + fluid
            + "（"
            + typeLabel
            + "）："
            + EnumChatFormatting.GREEN
            + priceTxt
            + " L/矿 · 本批 "
            + batchTxt
            + " L"
            + (failing ? EnumChatFormatting.RED + " [" + tr("gtsr.terminal.perf.boost.off") + "]"
                : EnumChatFormatting.GREEN + " [" + tr("gtsr.cluster.gui.boost.active") + "]");
        // 推导：单价（体积/单位）
        entry.expansion.add(derivHead("单价（体积/单位）"));
        entry.expansion.add(EnumChatFormatting.YELLOW + "单价 = 基础表值[档] × (1+Σ施压%) × (1+(同种台数-1)×协同率) ÷ 10");
        if (typeCosts.size() == 1) {
            int[] cost = typeCosts.get(0);
            int surcharge = surchargeSum(cost);
            int formedCount = 0;
            for (int[] c : typeCosts) {
                if (c[1] > 0) {
                    formedCount++;
                }
            }
            entry.expansion.add(
                EnumChatFormatting.WHITE + "= "
                    + cost[1]
                    + "（"
                    + tierLabel(cost[3])
                    + "档）× (1+"
                    + surcharge
                    + "%) × (1+("
                    + formedCount
                    + "-1)×"
                    + synergy
                    + "%) ÷ 10");
            entry.expansion.add(
                EnumChatFormatting.GRAY + "= "
                    + cost[1]
                    + " × "
                    + f2(1 + surcharge / 100.0D)
                    + " × "
                    + f2(1 + (formedCount - 1) * synergy / 100.0D)
                    + " ÷ 10");
            entry.expansion.add(EnumChatFormatting.GREEN + "= " + rateText(cost[0]) + " L/矿");
        } else {
            int unitNo = 1;
            for (int[] cost : typeCosts) {
                entry.expansion.add(
                    EnumChatFormatting.WHITE + "#"
                        + (unitNo++)
                        + " "
                        + rateText(cost[0])
                        + " L/矿（"
                        + tierLabel(cost[3])
                        + "档 · 施压 +"
                        + surchargeSum(cost)
                        + "%）");
            }
        }
        // 推导：本批消耗
        entry.expansion.add(derivHead("本批消耗"));
        entry.expansion.add(EnumChatFormatting.YELLOW + "本批 = 单价 × 本批实际矿数（开批时一次扣除）");
        entry.expansion.add(EnumChatFormatting.WHITE + "= " + priceTxt + " L/矿 × " + par + " 矿");
        entry.expansion.add(EnumChatFormatting.GREEN + "= " + batchTxt + " L/批");
        entry.expansion.add(
            EnumChatFormatting.DARK_GRAY + "施压：速度/并行模块按档 +5/10/30/40% · 协同：同种第 2 台起 +"
                + synergy
                + "%/台 · 液量不足整批 → 本批失效 + 一次性播报");
        return entry;
    }

    // ==================== cl.f.detail 单帧解析（数据基座） ====================

    /** 链步枚举缓存（LINK 行 ordinal → 本地名；链路页 LINKS 同款）。 */
    private static final ChainLink[] CHAIN_LINKS = ChainLink.values();

    /**
     * cl.f.detail 单帧解析结果（draw 每帧重建；畸形行/段丢弃；PEAK 本页不消费直接跳过）。
     * 数值哨兵：合计字段 {@code -1} = 服务端未下发（旧服务端降级），LUBE 分量与物流段时间
     * {@code Integer.MIN_VALUE} / {@code -1} = 缺行。
     */
    private static final class DetailFrame {

        /** FMOD 行：[unitIdx, seg, timeX100, par, thruX100, steamX100]（token 序）。 */
        final List<int[]> fmods = new ArrayList<>();
        /** LINK 行：[ordinal, timeX100, steamX100]（链序；链蒸汽耗/单批耗时推导用）。 */
        final List<int[]> linkSteps = new ArrayList<>();
        /** LOGI 行：物流段耗时（×100 定点；-1=缺行——未选中单元/旧服务端）。 */
        int logiTimeX100 = -1;
        /** FLUID 行：[liters]（与 {@link #fluidNames} 同下标）。 */
        final List<long[]> fluidLiters = new ArrayList<>();
        /** FLUID 行：流体注册名（可含 ':'，取首尾定界之间）。 */
        final List<String> fluidNames = new ArrayList<>();
        /** LUBE:cluster 值（×100 定点；MIN_VALUE=缺行）。 */
        int lubeCluster = Integer.MIN_VALUE;
        /** LUBE:logi 值（×100 定点；MIN_VALUE=缺行——未选中单元/旧服务端）。 */
        int lubeLogi = Integer.MIN_VALUE;
        /** BOOST 行：[typeOrdinal, typedX100]（恒 5 行；v1.20.16 起为五型单价合计 ×100 定点）。 */
        final List<int[]> boosts = new ArrayList<>();
        /** FTOT 流体合计（L；-1=缺 token）。 */
        long ftotFluid = -1L;
        /** FTOT 润滑合计（×100 定点；-1=缺 token）。 */
        int ftotLube = -1;
        /** FTOT 增幅合计（×100 定点；-1=缺 token）。 */
        int ftotBoost = -1;
    }

    /**
     * cl.f.detail 单帧解析（{@code |} 分行、行首令牌分发；{@code :} 分段，畸形行丢弃）：
     * FMOD/LINK/LOGI/LUBE/BOOST/FTOT/FLUID 供三列行集与推导块；未知令牌（PEAK 及未来扩展）
     * 静默跳过——前向兼容。
     */
    private static DetailFrame parseDetail(String detail) {
        DetailFrame f = new DetailFrame();
        if (detail == null || detail.isEmpty()) return f;
        for (String row : detail.split("\\|", -1)) {
            if (row.isEmpty()) continue;
            if (row.startsWith("FMOD:")) {
                int[] v = parseIntValues(row, 6);
                if (v != null) f.fmods.add(v);
            } else if (row.startsWith("LINK:")) {
                int[] v = parseIntValues(row, 3);
                if (v != null) f.linkSteps.add(v);
            } else if (row.startsWith("LOGI:")) {
                int[] v = parseIntValues(row, 2);
                if (v != null) f.logiTimeX100 = v[1];
            } else if (row.startsWith("FLUID:")) {
                int first = row.indexOf(':');
                int last = row.lastIndexOf(':');
                if (last <= first) continue;
                try {
                    f.fluidLiters.add(
                        new long[] { Long.parseLong(
                            row.substring(last + 1)
                                .trim()) });
                    f.fluidNames.add(row.substring(first + 1, last));
                } catch (NumberFormatException ignored) {
                    // 畸形数量段整行丢弃（名称段不入列，防下标错位）
                }
            } else if (row.startsWith("LUBE:")) {
                int second = row.indexOf(':', 5);
                if (second < 0) continue;
                String kind = row.substring(5, second);
                try {
                    int value = Integer.parseInt(
                        row.substring(second + 1)
                            .trim());
                    if ("cluster".equals(kind)) f.lubeCluster = value;
                    else if ("logi".equals(kind)) f.lubeLogi = value;
                } catch (NumberFormatException ignored) {
                    // 畸形行丢弃
                }
            } else if (row.startsWith("BOOST:")) {
                int[] v = parseIntValues(row, 2);
                if (v != null) f.boosts.add(v);
            } else if (row.startsWith("FTOT:")) {
                int[] v = parseIntValues(row, 3);
                if (v != null) {
                    // 服务端 clamp ≥0；负值（伪造/畸形）自然落入 -1 哨兵语义=缺真值
                    f.ftotFluid = v[0];
                    f.ftotLube = v[1];
                    f.ftotBoost = v[2];
                }
            }
        }
        return f;
    }

    /** 冒号分段整数解析（token 首段后的 value 段，期望 count 段全可解析；畸形回 null 整行丢弃）。 */
    private static int[] parseIntValues(String row, int count) {
        String[] parts = row.split(":", -1);
        if (parts.length < count + 1) return null;
        int[] out = new int[count];
        try {
            for (int i = 0; i < count; i++) {
                out[i] = Integer.parseInt(parts[i + 1].trim());
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return out;
    }

    // ==================== 栏 3【链路概览 + 增幅汇总 + 增幅实耗】 ====================

    /**
     * 链路概览四行（条/步单位对齐模拟稿）+ 增幅汇总（状态行 生效 x · 失效 y 数据源 KEY_BO_SUM
     * 首行置顶 + 五合计行 + 蒸汽乘子 ▶）+ 增幅实耗逐增幅器行 #n = 单价×本批矿数 L/批 ▶。
     */
    private List<PerfRow> buildChainsBoostLines() {
        List<PerfRow> out = new ArrayList<>();
        out.add(plain(groupHeader("gtsr.terminal.perf.group.chains")));
        int count = 0, sum = 0, longest = 0;
        for (int len : leChainLens()) {
            count++;
            sum += len;
            if (len > longest) longest = len;
        }
        if (count == 0) {
            out.add(
                plainTip(mutedLine("gtsr.terminal.perf.chains.count", "--"), "gtsr.terminal.perf.tip.chains_count"));
            out.add(
                plainTip(mutedLine("gtsr.terminal.perf.chains.steps", "--"), "gtsr.terminal.perf.tip.chains_steps"));
            out.add(plain(mutedLine("gtsr.terminal.perf.chains.longest", "--")));
            out.add(plain(mutedLine("gtsr.terminal.perf.chains.avg", "--")));
        } else {
            out.add(
                plainTip(
                    kvLine("gtsr.terminal.perf.chains.count", count + " 条"),
                    "gtsr.terminal.perf.tip.chains_count"));
            out.add(
                plainTip(kvLine("gtsr.terminal.perf.chains.steps", sum + " 步"), "gtsr.terminal.perf.tip.chains_steps"));
            out.add(plain(kvLine("gtsr.terminal.perf.chains.longest", longest + " 步")));
            out.add(plain(kvLine("gtsr.terminal.perf.chains.avg", String.format("%.1f", (double) sum / count) + " 步")));
        }
        out.add(plain(groupHeader("gtsr.terminal.perf.group.boost")));
        int[] sum8 = summary();
        out.add(
            plainTip(
                EnumChatFormatting.YELLOW + tr("gtsr.cluster.gui.boost.col.status")
                    + " = "
                    + EnumChatFormatting.GREEN
                    + tr("gtsr.cluster.gui.boost.active")
                    + " "
                    + sum8[6]
                    + EnumChatFormatting.GRAY
                    + " · "
                    + EnumChatFormatting.RED
                    + tr("gtsr.terminal.perf.boost.off")
                    + " "
                    + sum8[7],
                "gtsr.terminal.perf.tip.boost_state"));
        out.add(
            plainTip(kvLine("gtsr.cluster.gui.boost.sum.speed", pctText(sum8[0])), "gtsr.terminal.perf.tip.sum_speed"));
        out.add(
            plainTip(
                kvLine("gtsr.cluster.gui.boost.sum.parallel", "+" + sum8[1]),
                "gtsr.terminal.perf.tip.sum_parallel"));
        out.add(
            plainTip(
                kvLine("gtsr.cluster.gui.boost.sum.primary", pctText(sum8[2])),
                "gtsr.terminal.perf.tip.sum_primary"));
        out.add(
            plainTip(
                kvLine("gtsr.cluster.gui.boost.sum.secondary", pctText(sum8[3])),
                "gtsr.terminal.perf.tip.sum_secondary"));
        out.add(
            plainTip(
                kvLine("gtsr.cluster.gui.boost.sum.saver", saverText(sum8[4])),
                "gtsr.terminal.perf.tip.sum_saver"));
        appendEntry(out, buildMultEntry(sum8), "gtsr.terminal.perf.tip.sum_mult", null);
        out.add(plain(groupHeader("gtsr.terminal.perf.group.boostcost")));
        List<int[]> costs = costRows();
        int par = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_F_PAR, 0);
        for (int i = 0; i < costs.size(); i++) {
            int[] cost = costs.get(i);
            String name = cost.length >= 5 ? tr(boosterType(cost[4]).getFluidLangKey()) + "·" + shortTypeLabel(cost[4])
                : "--";
            String batchTxt = fmtBatchX10(Math.max(0, cost[0]), par);
            EntryRow entry = new EntryRow(KEY_COST_PREFIX + i);
            entry.text = EnumChatFormatting.WHITE + "#"
                + (i + 1)
                + " "
                + name
                + EnumChatFormatting.YELLOW
                + " = "
                + EnumChatFormatting.GREEN
                + batchTxt
                + " L/批";
            entry.expansion.add(derivHead("#" + (i + 1) + " " + name + " 实耗"));
            entry.expansion.add(EnumChatFormatting.YELLOW + "实耗 = 单价 × 本批实际矿数");
            entry.expansion
                .add(EnumChatFormatting.WHITE + "= " + rateText(Math.max(0, cost[0])) + " L/矿 × " + par + " 矿");
            entry.expansion.add(EnumChatFormatting.GREEN + "= " + batchTxt + " L/批");
            entry.expansion.add(EnumChatFormatting.DARK_GRAY + "开批一次扣除 · 批越小扣越少（按秒口径退役）");
            String formula = costFormulaText(cost);
            appendEntry(out, entry, "gtsr.terminal.perf.tip.cost", formula.isEmpty() ? null : formula);
        }
        return out;
    }

    /** 蒸汽乘子词条（MULT）：Π(每台速度/并行模块的档位系数)，贡献方取 KEY_BO_COST 施加方三元组去重。 */
    private EntryRow buildMultEntry(int[] sum8) {
        EntryRow entry = new EntryRow(KEY_MULT);
        entry.text = kvLine("gtsr.cluster.gui.boost.sum.mult", multText(sum8[5]));
        entry.expansion.add(derivHead(tr("gtsr.cluster.gui.boost.sum.mult")));
        entry.expansion.add(EnumChatFormatting.YELLOW + "乘子 = Π(每台速度/并行模块的档位系数)");
        List<String> seen = new ArrayList<>();
        for (int[] cost : costRows()) {
            for (int i = 2; i + 2 < cost.length; i += 3) {
                int srcTier = cost[i + 1];
                int srcType = cost[i + 2];
                if (srcType != ClusterParams.BoosterType.SPEED.ordinal()
                    && srcType != ClusterParams.BoosterType.PARALLEL.ordinal()) {
                    continue;
                }
                String sig = srcType + ":" + srcTier;
                if (seen.contains(sig)) continue;
                seen.add(sig);
                float factor = srcTier >= 0 && srcTier < ClusterParams.TIER_COUNT
                    ? ClusterParams.BOOSTER_STRUCTURE_PENALTY_MULT[srcTier]
                    : 1.0F;
                entry.expansion.add(
                    EnumChatFormatting.WHITE + "× "
                        + factor
                        + "（"
                        + sourceLabel(srcType)
                        + " · "
                        + tierLabel(srcTier)
                        + "档）");
            }
        }
        if (seen.isEmpty()) {
            entry.expansion.add(EnumChatFormatting.WHITE + "= 1.00（无速度/并行增幅）");
        }
        entry.expansion.add(EnumChatFormatting.GREEN + "= ×" + f2(sum8[5] / 100.0D));
        entry.expansion
            .add(EnumChatFormatting.DARK_GRAY + "档位系数：青铜 1.2 / 钢 1.4 / 钛 1.8 / 钨钢 2.0 · 主产物/副产物/节汽模块不计入 · 只作用于加权链路蒸汽");
        return entry;
    }

    /**
     * KEY_LE_CHAINS 各条目 len 整数首 token（S2b 同款防御：peak/ordinal 尾巴 {@code "3.5.3"}
     * 不进 len；S2b 的 parseLeadingInt 为链路页 private，本页自持等价最小解析）。
     * 仅统计 len ≥ 1 的有效链（空链 len=0 条目不计入链数/均值）。
     */
    private static List<Integer> leChainLens() {
        List<Integer> out = new ArrayList<>();
        String encoded = ClusterTerminalClientCache.getLeChains("");
        if (encoded.isEmpty()) return out;
        for (String entry : encoded.split(",", -1)) {
            String[] fields = entry.split(":", -1);
            if (fields.length < 2) continue;
            int len = parseLeadingInt(fields[1].trim());
            if (len > 0) out.add(len);
        }
        return out;
    }

    /** 首个非数字字符前的整数前缀（兼容 {@code "1.5.3"} 型 ordinal 尾巴；无有效数字前缀回 -1）。 */
    private static int parseLeadingInt(String raw) {
        if (raw == null || raw.isEmpty()) return -1;
        int start = raw.charAt(0) == '-' ? 1 : 0;
        int end = start;
        while (end < raw.length() && raw.charAt(end) >= '0' && raw.charAt(end) <= '9') end++;
        if (end == start) return -1;
        try {
            return Integer.parseInt(raw.substring(0, end));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /** 解析 KEY_BO_SUM 八字段 CSV（畸形回退全 0 数组；增幅页 summary 同款迁入）。 */
    private static int[] summary() {
        return parseIntCsv(ClusterTerminalClientCache.getStr(ClusterTerminalData.KEY_BO_SUM, ""), 8);
    }

    /**
     * 解析 KEY_BO_LIVE 逐单元 {@code amount:available} CSV（畸形条目丢弃；条目序与 KEY_BO_COST
     * 同为结构扫描序——失效徽章按下标对齐消费可用位，不新增协议键）。
     */
    private static List<int[]> parseLive() {
        List<int[]> out = new ArrayList<>();
        String encoded = ClusterTerminalClientCache.getStr(ClusterTerminalData.KEY_BO_LIVE, "");
        if (encoded.isEmpty()) return out;
        for (String entry : encoded.split(",", -1)) {
            String[] parts = entry.split(":", -1);
            if (parts.length < 2) continue;
            try {
                out.add(new int[] { Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()) });
            } catch (NumberFormatException ignored) {
                // 畸形条目丢弃
            }
        }
        return out;
    }

    /** 客户端解析定长 int CSV（畸形项回 0；增幅页 parseIntCsv 同款迁入）。 */
    private static int[] parseIntCsv(String csv, int length) {
        int[] out = new int[length];
        if (csv == null || csv.isEmpty()) return out;
        String[] parts = csv.split(",", -1);
        int n = Math.min(length, parts.length);
        for (int i = 0; i < n; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException ignored) {
                out[i] = 0;
            }
        }
        return out;
    }

    /** 加成百分比文本（×100 定点 → +N% / +N.N%；增幅页 pctText 逐字迁入）。 */
    private static String pctText(int x100) {
        double pct = x100 / 100.0D * 100.0D;
        return "+" + (Math.abs(pct - Math.rint(pct)) < 1e-6 ? String.valueOf((long) Math.rint(pct))
            : String.format("%.1f", pct)) + "%";
    }

    /** 节汽率文本（×100 定点：截断展示 min(raw, cap)，超限红粗；增幅页 saverText 同式迁参版）。 */
    private static String saverText(int raw) {
        int capped = (int) Math.min(raw, ClusterParams.STEAM_SAVER_CAP * 100);
        boolean truncated = raw > ClusterParams.STEAM_SAVER_CAP * 100 + 1;
        String text = "-" + capped + "%";
        return truncated ? EnumChatFormatting.RED.toString() + EnumChatFormatting.BOLD + text
            : EnumChatFormatting.WHITE.toString() + EnumChatFormatting.BOLD + text;
    }

    /** 蒸汽乘子文本（×100 定点 → ×N.NN 白粗；增幅页 multText 逐字迁入）。 */
    private static String multText(int x100) {
        return EnumChatFormatting.WHITE.toString() + EnumChatFormatting.BOLD
            + "×"
            + String.format("%.2f", x100 / 100.0D);
    }

    /** KEY_BO_COST 全条目解析（{@code priceX10:base[:pct:tier:type]...}；畸形条目丢弃，增幅页 costRow 同防御）。 */
    private static List<int[]> costRows() {
        List<int[]> out = new ArrayList<>();
        String encoded = ClusterTerminalClientCache.getStr(ClusterTerminalData.KEY_BO_COST, "");
        if (encoded.isEmpty()) return out;
        for (String entry : encoded.split(",", -1)) {
            String[] fields = entry.split(":", -1);
            if (fields.length < 2 || (fields.length - 2) % 3 != 0) continue;
            try {
                int[] arr = new int[fields.length];
                for (int i = 0; i < fields.length; i++) {
                    arr[i] = Integer.parseInt(fields[i].trim());
                }
                out.add(arr);
            } catch (NumberFormatException ignored) {
                // 畸形条目丢弃
            }
        }
        return out;
    }

    /** 单价文本：×10 定点 → 整数值省小数、非整数保留一位小数（如 575→"57.5"、50→"5"；v1.20.16 起首字段 = 单价×10）。 */
    private static String rateText(int priceX10) {
        if (priceX10 % 10 == 0) return String.valueOf(priceX10 / 10);
        return String.format("%.1f", priceX10 / 10.0D);
    }

    /** 本批实耗文本：单价×10 × 矿数 → L/批（÷10 无损则整数，否则一位小数）。 */
    private static String fmtBatchX10(int priceX10, int par) {
        long v = (long) priceX10 * par;
        return v % 10 == 0 ? GtsrNumFormat.grouped(v / 10) : String.format("%.1f", v / 10.0D);
    }

    /** 本批实耗文本（×100 定点单价合计版）：typedX100 × 矿数 → L/批。 */
    private static String fmtBatchX100(int priceX100, int par) {
        long v = (long) priceX100 * par;
        return v % 100 == 0 ? GtsrNumFormat.grouped(v / 100) : f2(v / 100.0D);
    }

    /**
     * 实耗公式串（tooltip 附加行，代入实值，v1.20.16 L/矿 口径）：
     * {@code 基础 50 × (1 + 10%[速度 钢] + 5%[并行 青铜]) = 57.5 L/矿}；无联动加成时显示
     * {@code 基础 N × (1) = N L/矿}；无实耗数据（基础值 ≤ 0）返回空串不出 tooltip。
     */
    private static String costFormulaText(int[] cost) {
        if (cost == null || cost.length < 2 || cost[1] <= 0) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(EnumChatFormatting.WHITE)
            .append(tr("gtsr.cluster.gui.boost.cost.base"))
            .append(' ')
            .append(GtsrNumFormat.grouped(cost[1]))
            .append(" × (1");
        for (int i = 2; i + 2 < cost.length; i += 3) {
            int pct = cost[i], tier = cost[i + 1], typeOrdinal = cost[i + 2];
            sb.append(" + ")
                .append(pct)
                .append("%[")
                .append(sourceLabel(typeOrdinal))
                .append(' ')
                .append(tierLabel(tier))
                .append(']');
        }
        sb.append(") = ")
            .append(rateText(cost[0]))
            .append(" L/矿");
        return sb.toString();
    }

    /** 施加方类型短标签（SPEED/PARALLEL；越界回退并行；增幅页同款迁入）。 */
    private static String sourceLabel(int typeOrdinal) {
        if (typeOrdinal == ClusterParams.BoosterType.SPEED.ordinal()) {
            return tr("gtsr.cluster.gui.boost.cost.src.speed");
        }
        return tr("gtsr.cluster.gui.boost.cost.src.parallel");
    }

    /** 增幅类型短标签（实耗行 #n 流体·类型 用）：速度/并行用既有短键，其余复用增幅汇总标签。 */
    private static String shortTypeLabel(int typeOrdinal) {
        switch (boosterType(typeOrdinal)) {
            case SPEED:
                return tr("gtsr.cluster.gui.boost.cost.src.speed");
            case PRIMARY_OUTPUT:
                return tr("gtsr.cluster.gui.boost.sum.primary");
            case SECONDARY_OUTPUT:
                return tr("gtsr.cluster.gui.boost.sum.secondary");
            case STEAM_SAVER:
                return tr("gtsr.cluster.gui.boost.sum.saver");
            case PARALLEL:
            default:
                return tr("gtsr.cluster.gui.boost.cost.src.parallel");
        }
    }

    /** 施加方 tier 标签（复用集群层级 lang key；越界回 "--"；增幅页同款迁入）。 */
    private static String tierLabel(int tier) {
        if (tier < 0 || tier >= ClusterParams.TIER_COUNT) return "--";
        return tr(
            ClusterParams.ClusterTier.get(tier)
                .getLangKey());
    }

    // ==================== 推导块与数值工具 ====================

    /** 推导块头（青 §b + 通用前缀键 + 主题）。 */
    private static String derivHead(String subject) {
        return EnumChatFormatting.AQUA + tr("gtsr.terminal.perf.deriv.head") + subject;
    }

    /** 链步本地名（LINK ordinal → lang key；越界回 "#ordinal"）。 */
    private static String stepName(int ordinal) {
        return ordinal >= 0 && ordinal < CHAIN_LINKS.length ? tr(CHAIN_LINKS[ordinal].getLangKey()) : "#" + ordinal;
    }

    /** 层级下标 → 本地名（越界回 "--"）。 */
    private static String tierNameByIdx(int idx) {
        if (idx < 0 || idx >= ClusterParams.TIER_COUNT) return "--";
        return tr(
            ClusterParams.ClusterTier.get(idx)
                .getLangKey());
    }

    /** 增幅类型序号 → 枚举（越界回退并行型，与 boosterType 防御口径一致）。 */
    private static ClusterParams.BoosterType boosterType(int typeOrdinal) {
        ClusterParams.BoosterType[] values = ClusterParams.BoosterType.values();
        return typeOrdinal >= 0 && typeOrdinal < values.length ? values[typeOrdinal]
            : ClusterParams.BoosterType.PARALLEL;
    }

    /** 增幅类型序号 → 本地名（越界回退并行型；d6acf00 基线语义保留）。 */
    private static String boosterLabel(int typeOrdinal) {
        return tr(boosterType(typeOrdinal).getLangKey());
    }

    /** 同种协同率（%/台）：速度/并行 10%，主/副产物与节汽 50%（ClusterParams 常量换算）。 */
    private static int synergyPct(ClusterParams.BoosterType type) {
        boolean speedParallel = type == ClusterParams.BoosterType.SPEED || type == ClusterParams.BoosterType.PARALLEL;
        double rate = speedParallel ? ClusterParams.SPEED_PARALLEL_SYNERGY_RATE : ClusterParams.OUTPUT_SYNERGY_RATE;
        return (int) Math.round(rate * 100.0D);
    }

    /** KEY_BO_COST 条目的 Σ施压%（施加方三元组 pct 求和）。 */
    private static int surchargeSum(int[] cost) {
        int sum = 0;
        for (int i = 2; i + 2 < cost.length; i += 3) {
            sum += cost[i];
        }
        return sum;
    }

    /** 同型条目 Σ单价（×10 定点；无条目回 0）。 */
    private static int sumPriceX10(List<int[]> typeCosts) {
        int sum = 0;
        for (int[] cost : typeCosts) {
            sum += cost[0];
        }
        return sum;
    }

    /** 整数表值匹配下标（反向查档名用；无匹配回 -1）。 */
    private static int matchIdx(int[] table, int value) {
        for (int i = 0; i < table.length; i++) {
            if (table[i] == value) return i;
        }
        return -1;
    }

    /** 整数表连接（" / " 分隔；档位表注行用）。 */
    private static String joinTable(int[] table) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < table.length; i++) {
            if (i > 0) sb.append(" / ");
            sb.append(table[i]);
        }
        return sb.toString();
    }

    /** 集群润滑档位后缀（值反查 CLUSTER_LUBRICANT_LPS；无匹配为空串）。 */
    private static String clusterTierSuffix(double litersPerSec) {
        int idx = matchIdx(ClusterParams.CLUSTER_LUBRICANT_LPS, (int) Math.round(litersPerSec));
        return idx >= 0 ? "（" + tierNameByIdx(idx) + "档）" : "";
    }

    /** 物流段时间档位后缀（×100 定点反查 LOGISTICS_TIME_SEC；无匹配为空串）。 */
    private static String logiTierSuffix(int logiTimeX100) {
        int idx = matchIdx(ClusterParams.LOGISTICS_TIME_SEC, logiTimeX100 / 100);
        return idx >= 0 ? "（" + tierNameByIdx(idx) + "档）" : "";
    }

    /**
     * 占比文本：{@code part/total×100}（×100 定点比值与量纲无关）——整值省小数、非整留一位
     * （增幅页 pctText 同式去 "+"）；{@code total<0}（服务端未下发合计真值）回 {@code null}
     * （调用方走无占比格式），{@code total≤0}（零分母）回 "--"（现有 UI 无数据哨兵）。
     */
    private static String shareText(double part, long total) {
        if (total < 0) return null;
        if (total <= 0) return "--";
        double pct = part / total * 100.0D;
        return Math.abs(pct - Math.rint(pct)) < 1e-6 ? String.valueOf((long) Math.rint(pct)) + "%"
            : String.format("%.1f", pct) + "%";
    }

    /** ×100 定点 int → 文本（整值省小数千位分组、非整留两位）。 */
    private static String fmtX100(int rawX100) {
        double v = rawX100 / 100.0D;
        return v == Math.rint(v) ? GtsrNumFormat.grouped(Math.round(v)) : f2(v);
    }

    /** double → 文本（整值省小数千位分组、非整留两位）。 */
    private static String fmtNum(double v) {
        return v == Math.rint(v) ? GtsrNumFormat.grouped(Math.round(v)) : f2(v);
    }

    /** 两位小数文本。 */
    private static String f2(double v) {
        return String.format("%.2f", v);
    }

    // ==================== 行文本工具 ====================

    /** 组标题行（金粗 0.7f）。 */
    private static String groupHeader(String key) {
        return EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + tr(key);
    }

    /** 数据行（黄标签 = 绿值，链路页 perfLines 同式）。 */
    private static String kvLine(String labelKey, String value) {
        return EnumChatFormatting.YELLOW + tr(labelKey) + " = " + EnumChatFormatting.GREEN + value;
    }

    /** 数据行（黄标签 = 灰值，无数据哨兵 "--" 用）。 */
    private static String mutedLine(String labelKey, String value) {
        return EnumChatFormatting.YELLOW + tr(labelKey) + " = " + EnumChatFormatting.GRAY + value;
    }

    // ==================== 输入 ====================

    /**
     * 内容区内点击：三栏列表先行接管（滚动条拖拽/词条行展开切换/区内其余点击消费防穿透，
     * GtsrGuiList.mouseClicked 滚动条分支先行于行命中，滚轮走独立 handleWheel——互不冲突）；
     * 列表外残余内容区点击仍一律消费。
     */
    @Override
    public boolean mouseClicked(int ox, int oy, int mx, int my, int button) {
        boolean inPage = mx >= ox && mx < ox + GuiClusterTerminalScreen.CONTENT_W
            && my >= oy
            && my < oy + GuiClusterTerminalScreen.CONTENT_H;
        if (!inPage) return false;
        for (GtsrGuiList list : this.colLists) {
            if (list != null && list.mouseClicked(mx, my, button)) return true;
        }
        return true;
    }

    @Override
    public void wheel(int ox, int oy, int mx, int my, int dir) {
        // 滚轮路由 = 鼠标所落栏的 list.handleWheel（列表自判区内命中并消费；链路页现行转发范式）
        for (GtsrGuiList list : this.colLists) {
            if (list != null && list.handleWheel(mx, my, dir)) return;
        }
    }

    @Override
    public void mouseClickMove(int mouseX, int mouseY, int button) {
        for (GtsrGuiList list : this.colLists) {
            if (list != null) list.mouseClickMove(mouseX, mouseY, button);
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        for (GtsrGuiList list : this.colLists) {
            if (list != null) list.mouseReleased(mouseX, mouseY, button);
        }
    }

    // ==================== 共用工具 ====================

    private net.minecraft.client.gui.FontRenderer font() {
        return this.host.font();
    }

    private static String tr(String key) {
        return GuiClusterTerminalScreen.tr(key);
    }
}
