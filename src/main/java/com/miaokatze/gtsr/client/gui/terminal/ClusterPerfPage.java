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
 * 集群终端·第五页「性能」（terminal-native-ui S3 新独立页：集群级极详细只读页，六分组三栏排版；
 * v1.20.14 T3 四项改造：运行汇总分模块列 / 详情词条占比 / 词条展开计算路径 / BOOST 实际流体名）。
 *
 * <p>
 * 版面：页标题 + 三栏各 191px（栏距 4），每栏一个 {@link GtsrGuiList}（行高 11，惰性重建范式
 * 照链路页 perfList）；组标题行=金粗 0.7f（{@code gtsr.terminal.perf.group.*}），数据行 0.7f，
 * 长行 ellipsis。三栏六分组：
 * <ul>
 * <li>栏 1【运行汇总】：总计行（耗时 KEY_F_TIME ×100 定点→%.2f 秒 / 有效并行 KEY_F_PAR raw /
 * 预测吞吐 KEY_F_THRU ×100→%.2f / 实际加权公式 KEY_F_FORMULA 标签+长串两行）+ <b>每物流模块一行</b>
 * （v1.20.14 (a)：cl.f.detail 新增 FMOD 令牌——选择无关、服务端逐物流单元摘要
 * {@code idx:seg:timeX100:par:thruX100:steamX100}，#序号·段号 前缀；旧服务端缺令牌整块省略）
 * + 热量 / 润滑 / 真实吞吐 / 累计处理（后四行与主壳顶卡同口径，数值 grouped）；</li>
 * <li>栏 2【蒸汽口径 + 集群详情】：总蒸汽耗与链蒸汽耗（KEY_F_TOTAL/KEY_F_STEAM 均 ×100 定点，
 * <b>÷100.0D 解码修复</b>）+ 结算蒸汽（KEY_STEAM 原值，行尾注口径来源）+ 集群详情词条——
 * FLUID/LUBE/BOOST 令牌行升级为<b>可展开词条</b>（v1.20.14 (b)(c)(d)：行首 ▶/▼ 箭头、
 * 互斥展开计算路径小字行、正文追加 FTOT 服务端合计真值占比、BOOST 行首词改实际增幅流体名），
 * 链蒸汽耗行同样可展开（计算路径 = LINK 逐步 T_i/C_i 加权式）；</li>
 * <li>栏 3【链路概览 + 增幅汇总 + 增幅实耗】：KEY_LE_CHAINS len 派生四行（链数/总链步/最长链/
 * 平均链长，len 首整数 token 防御解析）+ KEY_BO_SUM 八字段（增幅页汇总卡同式解码，
 * pctText/saverText/multText）+ KEY_BO_COST 逐增幅器实耗行（tooltip 完整公式，
 * costFormulaText 同式、NumberFormatUtil 换 {@link GtsrNumFormat#grouped}）。</li>
 * </ul>
 *
 * <p>
 * <b>live 每帧重读纪律</b>：三列行集全部由 draw 每帧重建（零构造期快照）；列表实例仅随几何
 * 惰性重建。交互：只读页——第二列词条行点击经 {@link GtsrGuiList} 行回调切换互斥展开态
 * （滚动条命中由列表内滚动条分支先行、滚轮走独立 handleWheel，互不冲突）；其余内容区点击
 * 一律消费防穿透；滚轮分栏转发鼠标所落栏的 {@code list.handleWheel}；拖拽/释放转发各栏列表
 * （滚动条）。cl.f.detail 未知令牌（含旧客户端视角的 FMOD/FTOT/FWIP）前向兼容丢弃。
 */
@SideOnly(Side.CLIENT)
final class ClusterPerfPage implements ClusterPage {

    /** 三等栏宽与栏距（582 = 191×3 + 4×2）。 */
    private static final int COL_W = (GuiClusterTerminalScreen.CONTENT_W - 2 * 4) / 3;
    private static final int COL_GAP = 4;
    /** 列表起始偏移（页标题行之下）与高度。 */
    private static final int LIST_DY = 14;
    private static final int LIST_H = GuiClusterTerminalScreen.CONTENT_H - LIST_DY;
    /** 数据行高（组标题行同行高，金粗区分）。 */
    private static final int ROW_H = 11;

    private final GuiClusterTerminalScreen host;
    /** 三栏滚动列表（惰性重建范式）。 */
    private final GtsrGuiList[] colLists = new GtsrGuiList[3];
    /** 各栏列表左缘几何快照（top/width/height 三栏共享）。 */
    private final int[] colListLefts = new int[3];
    private int colListTop = -1;
    private int colListWidth = -1;
    private int colListHeight = -1;
    /** 本帧三列行集（draw 每帧重建；live 每帧重读纪律）。 */
    private final List<List<String>> colFrameLines;
    /** 本帧增幅实耗公式串（与第三列 boostcost 行下标一一对应；tooltip 用）。 */
    private final List<String> boostFormulas = new ArrayList<>();
    /** 本帧 boostcost 首行在第三列行集中的下标（未含 boostcost 组时置 Integer.MAX_VALUE）。 */
    private int boostCostStart = Integer.MAX_VALUE;
    /** 本帧第二列词条行键（与第二列行集同下标同步重建；null=非词条行，点击不切换展开）。 */
    private final List<String> col2RowKeys = new ArrayList<>();
    /** 本帧第二列小字行标记（true=展开计算路径行：0.6f 缩进绘制；与第二列行集同下标）。 */
    private final List<Boolean> col2RowSmall = new ArrayList<>();
    /** 互斥展开态：当前展开词条键（"STEAM_CHAIN"/"FLUID:i"/"LUBE:*"/"BOOST:ordinal"；null=全折叠）。 */
    private String openEntryKey;
    /** 展开态身份哨兵：选中物流单元下标；变化即清 openEntryKey，防止展开状态跨选择泄漏。 */
    private int openEntrySel = -1;

    ClusterPerfPage(GuiClusterTerminalScreen host) {
        this.host = host;
        this.colFrameLines = new ArrayList<List<String>>();
        for (int i = 0; i < 3; i++) {
            this.colFrameLines.add(Collections.<String>emptyList());
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
        // 三列行集每帧重建（draw 每帧重读缓存；boostFormulas/boostCostStart 随第三列同步刷新）
        this.colFrameLines.set(0, buildSummaryLines());
        this.colFrameLines.set(1, buildSteamDetailLines());
        this.colFrameLines.set(2, buildChainsBoostLines());
        for (int col = 0; col < 3; col++) {
            ensureColList(col, ox + col * (COL_W + COL_GAP), oy + LIST_DY, COL_W, LIST_H);
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
            () -> this.colFrameLines.get(c)
                .size());
        list.setRowPainter((index, x, y, mouseX, mouseY) -> paintPerfRow(c, index, x, y, mouseX, mouseY));
        if (c == 1) {
            // (c)：第二列词条行点击 = 互斥切换展开态；滚动条命中由列表滚动条分支先行处理，不冲突
            list.setRowListener((index, mouseX, mouseY, button) -> {
                if (index < 0 || index >= this.col2RowKeys.size()) return;
                String key = this.col2RowKeys.get(index);
                if (key == null) return; // 非词条行（组标题/小字行/蒸汽口径普通行）
                this.openEntryKey = key.equals(this.openEntryKey) ? null : key;
            });
        }
        this.colLists[col] = list;
    }

    /**
     * 单行绘制：数据行 0.7f 长行 ellipsis（组标题行经 § GOLD/BOLD 前缀同款渲染）；第二列小字行
     * （展开计算路径）0.6f 缩进暗色；boostcost 行命中出公式 tooltip。
     */
    private void paintPerfRow(int col, int index, int x, int y, int mx, int my) {
        List<String> lines = this.colFrameLines.get(col);
        if (index < 0 || index >= lines.size()) return;
        boolean small = col == 1 && index < this.col2RowSmall.size() && this.col2RowSmall.get(index);
        String text = lines.get(index);
        if (small) {
            text = GtsrGuiList.ellipsis(font(), text, (int) ((this.colListWidth - 18) / 0.6f));
            GuiClusterTerminalScreen.drawScaledText(font(), text, x + 12, y + 2, 0.6f, GtsrGuiPalette.TEXT_MUTED);
        } else {
            text = GtsrGuiList.ellipsis(font(), text, (int) ((this.colListWidth - 6) / 0.7f));
            GuiClusterTerminalScreen.drawScaledText(font(), text, x + 3, y + 2, 0.7f, GtsrGuiPalette.TEXT_BODY);
        }
        if (col == 2 && mx >= x && mx < x + this.colListWidth && my >= y && my < y + ROW_H) {
            int booster = index - this.boostCostStart;
            if (booster >= 0 && booster < this.boostFormulas.size()) {
                this.host.requestTooltip(
                    "perf.bo" + booster,
                    GuiClusterTerminalScreen.lines(this.boostFormulas.get(booster)));
            }
        }
    }

    // ==================== 栏 1【运行汇总】 ====================

    /** 运行汇总（KEY_F_* ×100 定点解码 + 总计行下每物流模块一行（FMOD 令牌）+ 顶卡口径四行；每帧重读）。 */
    private List<String> buildSummaryLines() {
        List<String> out = new ArrayList<>();
        out.add(groupHeader("gtsr.terminal.perf.group.summary"));
        int timeRaw = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_F_TIME, 0);
        int parRaw = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_F_PAR, 0);
        int thruRaw = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_F_THRU, 0);
        out.add(kvLine("gtsr.cluster.gui.link.perf.time", String.format("%.2f", timeRaw / 100.0D) + " s"));
        out.add(kvLine("gtsr.cluster.gui.link.perf.parallel", String.valueOf(parRaw)));
        out.add(
            kvLine(
                "gtsr.cluster.gui.link.perf.thru",
                String.format("%.2f", thruRaw / 100.0D) + " " + tr("gtsr.cluster.gui.card.thru.unit")));
        // 实际加权公式：标签 + 长串两行（ellipsis 兜底）
        out.add(EnumChatFormatting.YELLOW + tr("gtsr.gui.cluster.link.perf.formula") + " =");
        out.add(
            EnumChatFormatting.GREEN + ClusterTerminalClientCache.getStr(ClusterTerminalData.KEY_F_FORMULA, "0 L/s"));
        // (a) 总计行下每物流模块一行：cl.f.detail 的 FMOD 令牌（选择无关，服务端逐物流单元摘要）；
        // 旧服务端缺令牌 → 整块省略（降级不显示）
        DetailFrame frame = parseDetail(ClusterTerminalClientCache.getFDetail(""));
        for (int[] fmod : frame.fmods) {
            String seg = fmod[1] < 0 ? "--" : String.valueOf(fmod[1]);
            out.add(
                EnumChatFormatting.WHITE + String.format(
                    tr("gtsr.terminal.perf.fmod"),
                    String.valueOf(fmod[0] + 1),
                    seg,
                    String.format("%.2f", fmod[2] / 100.0D) + "s",
                    String.valueOf(fmod[3]),
                    String.format("%.2f", fmod[4] / 100.0D),
                    GtsrNumFormat.grouped(Math.round(fmod[5] / 100.0D))));
        }
        out.add(
            kvLine(
                "gtsr.cluster.gui.card.heat",
                ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_HEAT, 0) + "%"));
        out.add(
            kvLine(
                "gtsr.cluster.gui.card.lube",
                GtsrNumFormat.grouped(ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_LUBE, 0)) + " L/s"));
        out.add(
            kvLine(
                "gtsr.cluster.gui.card.thru",
                GtsrNumFormat.grouped(ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_THRU, 0)) + " "
                    + tr("gtsr.cluster.gui.card.thru.unit")));
        out.add(
            EnumChatFormatting.YELLOW + String.format(
                tr("gtsr.cluster.gui.card.thru.total"),
                GtsrNumFormat.grouped(ClusterTerminalClientCache.getLong(ClusterTerminalData.KEY_TOTAL, 0L))));
        return out;
    }

    // ==================== 栏 2【蒸汽口径 + 集群详情（可展开词条）】 ====================

    /**
     * 蒸汽口径三行 + 集群详情词条：总蒸汽耗/链蒸汽耗为 ×100 定点（<b>÷100.0D 解码修复</b>），
     * 结算蒸汽为 KEY_STEAM 原值（行尾注口径来源）；链蒸汽耗行与 FLUID/LUBE/BOOST 词条行首带
     * ▶/▼ 箭头、点击互斥展开计算路径小字行（v1.20.14 (b)(c)(d)）。
     */
    private List<String> buildSteamDetailLines() {
        int selNow = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_SEL_LOGI, -1);
        if (selNow != this.openEntrySel) {
            this.openEntrySel = selNow;
            this.openEntryKey = null;
        }
        List<String> out = new ArrayList<>();
        this.col2RowKeys.clear();
        this.col2RowSmall.clear();
        out.add(groupHeader("gtsr.terminal.perf.group.steam"));
        int totalRaw = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_F_TOTAL, 0);
        int chainRaw = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_F_STEAM, 0);
        out.add(
            kvLine(
                "gtsr.cluster.gui.link.perf.steam_total",
                GtsrNumFormat.grouped(Math.round(totalRaw / 100.0D)) + " L/s"));
        DetailFrame frame = parseDetail(ClusterTerminalClientCache.getFDetail(""));
        // 链蒸汽耗行 = 可展开词条（计算路径 = cl.f.detail LINK 逐步 T_i/C_i 加权式；LINK 由链路页与本页共享）
        appendEntry(out, buildChainSteamEntry(frame, chainRaw));
        out.add(
            kvLine(
                "gtsr.cluster.gui.card.steam",
                GtsrNumFormat.grouped(ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_STEAM, 0)) + " L/s")
                + " "
                + EnumChatFormatting.GRAY
                + "· "
                + tr("gtsr.terminal.perf.caliber.settle"));
        out.add(groupHeader("gtsr.terminal.perf.group.detail"));
        for (EntryRow entry : buildDetailEntries(frame)) {
            appendEntry(out, entry);
        }
        return out;
    }

    /** 词条行落列：按展开态加行首箭头（▶ 折叠 / ▼ 展开，MC 字体安全字符——链路页 chip ◀▶ 同族），展开时尾随计算路径小字行。 */
    private void appendEntry(List<String> out, EntryRow entry) {
        boolean open = entry.key.equals(this.openEntryKey);
        out.add(EnumChatFormatting.WHITE + (open ? "▼ " : "▶ ") + entry.text);
        this.col2RowKeys.add(entry.key);
        this.col2RowSmall.add(Boolean.FALSE);
        if (open) {
            for (String line : entry.expansion) {
                out.add(EnumChatFormatting.GRAY + line);
                this.col2RowKeys.add(null);
                this.col2RowSmall.add(Boolean.TRUE);
            }
        }
    }

    /** 链蒸汽耗词条：正文沿用既有 kv 行 + 口径注；展开 = LINK 逐步（T_i 秒 / C_i 蒸汽）与加权式说明。 */
    private static EntryRow buildChainSteamEntry(DetailFrame frame, int chainRawX100) {
        EntryRow entry = new EntryRow(KEY_STEAM_CHAIN);
        entry.text = kvLine(
            "gtsr.cluster.gui.link.perf.steam",
            GtsrNumFormat.grouped(Math.round(chainRawX100 / 100.0D)) + " L/s") + " "
            + EnumChatFormatting.GRAY
            + "· "
            + tr("gtsr.terminal.perf.caliber.formula");
        entry.expansion.add(tr("gtsr.terminal.perf.exp.steam.head"));
        if (frame.linkSteps.isEmpty()) {
            entry.expansion.add("--"); // 无链步：未选中单元/空链/旧服务端——降级占位
        } else {
            for (int[] step : frame.linkSteps) {
                String name = step[0] >= 0 && step[0] < CHAIN_LINKS.length ? tr(CHAIN_LINKS[step[0]].getLangKey())
                    : "#" + step[0];
                entry.expansion
                    .add(String.format(tr("gtsr.terminal.perf.exp.steam.step"), name, x100(step[1]), x100(step[2])));
            }
        }
        return entry;
    }

    /**
     * 集群详情词条集（v1.20.14 (b)(c)(d)）：FLUID/LUBE/BOOST 升级为可展开词条——正文追加服务端
     * 合计真值占比（FTOT 缺失=旧服务端→沿用无占比旧格式；合计 ≤0=零分母→占比位 "--"）；
     * BOOST 正文首词改实际增幅流体名（{@code BoosterType.getFluidLangKey()}）+ 括号保留增幅
     * 类型名（如「硫酸（主产物增幅）：X L/s」）；展开 = 计算路径（FLUID=批记账合计/项数、
     * LUBE=集群+物流=合计分量式、BOOST=KEY_BO_COST 该型逐台分量 + FWIP wip 倍率）。
     */
    private static List<EntryRow> buildDetailEntries(DetailFrame frame) {
        List<EntryRow> out = new ArrayList<>();
        // FLUID：充流体（选中单元最近成功批 charged；占比分母 = FTOT 流体合计真值）
        for (int i = 0; i < frame.fluidNames.size(); i++) {
            String name = frame.fluidNames.get(i);
            long liters = frame.fluidLiters.get(i)[0];
            EntryRow entry = new EntryRow("FLUID:" + i);
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
        // LUBE：集群/物流两词条（展开共用同一条分量路径：集群 + 物流 = 合计）
        appendLubeEntry(out, frame, "cluster", "gtsr.terminal.f.detail.lube.cluster", frame.lubeCluster);
        appendLubeEntry(out, frame, "logi", "gtsr.terminal.f.detail.lube.logi", frame.lubeLogi);
        // BOOST：5 型逐型（占比分母 = FTOT 增幅合计真值；展开 = 该型逐台分量 + wip 倍率）
        List<int[]> costs = costRows();
        for (int[] boost : frame.boosts) {
            ClusterParams.BoosterType type = boosterType(boost[0]);
            EntryRow entry = new EntryRow("BOOST:" + boost[0]);
            String fluid = tr(type.getFluidLangKey());
            String typeLabel = boosterLabel(boost[0]);
            String pct = shareText(boost[1], frame.ftotBoost);
            entry.text = pct == null
                ? EnumChatFormatting.GREEN
                    + String.format(tr("gtsr.terminal.f.detail.boost"), fluid, typeLabel, x100(boost[1]))
                : EnumChatFormatting.GREEN
                    + String.format(tr("gtsr.terminal.f.detail.boost.pct"), fluid, typeLabel, x100(boost[1]), pct);
            int unitNo = 1;
            for (int[] cost : costs) {
                if (cost.length < 5 || cost[4] != boost[0]) continue;
                entry.expansion.add(
                    String.format(
                        tr("gtsr.terminal.perf.exp.boost.unit"),
                        unitNo++,
                        GtsrNumFormat.grouped(cost[1]),
                        rateText(cost[0]),
                        tierLabel(cost[3])));
            }
            if (frame.fwip >= 0) {
                entry.expansion.add(String.format(tr("gtsr.terminal.perf.exp.boost.wip"), x100(frame.fwip)));
            }
            out.add(entry);
        }
        return out;
    }

    /**
     * LUBE 词条构建：正文按有无占比真值选新旧格式（缺行——如物流行需选中单元——整词条不渲染）；
     * 展开 = 润滑分量路径 {@code 集群 + 物流 = 合计}（分量/合计缺服务端真值以 "--" 占位）。
     */
    private static void appendLubeEntry(List<EntryRow> out, DetailFrame frame, String kind, String langKey,
        int valueX100) {
        if (valueX100 == Integer.MIN_VALUE) return;
        EntryRow entry = new EntryRow("LUBE:" + kind);
        String pct = shareText(valueX100, frame.ftotLube);
        entry.text = pct == null ? EnumChatFormatting.GREEN + String.format(tr(langKey), x100(valueX100))
            : EnumChatFormatting.GREEN + String.format(tr(langKey + ".pct"), x100(valueX100), pct);
        String clusterTxt = frame.lubeCluster == Integer.MIN_VALUE ? "--" : x100(frame.lubeCluster);
        String logiTxt = frame.lubeLogi == Integer.MIN_VALUE ? "--" : x100(frame.lubeLogi);
        String totalTxt = frame.ftotLube < 0 ? "--" : x100(frame.ftotLube);
        entry.expansion.add(String.format(tr("gtsr.terminal.perf.exp.lube"), clusterTxt, logiTxt, totalTxt));
        out.add(entry);
    }

    /**
     * 占比文本（v1.20.14 (b)）：{@code part/total×100}（同 ×100 定点比值与量纲无关）——整值省
     * 小数、非整留一位（增幅页 pctText 同式去 "+"）；{@code total<0}（服务端未下发合计真值）回
     * {@code null}（调用方走无占比旧格式），{@code total≤0}（零分母）回 "--"（现有 UI 无数据哨兵）。
     */
    private static String shareText(double part, long total) {
        if (total < 0) return null;
        if (total <= 0) return "--";
        double pct = part / total * 100.0D;
        return Math.abs(pct - Math.rint(pct)) < 1e-6 ? String.valueOf((long) Math.rint(pct)) + "%"
            : String.format("%.1f", pct) + "%";
    }

    /** ×100 定点 int → 两位小数文本（int 已解析无畸形；旧 x100Text(String) 的 int 版）。 */
    private static String x100(int rawX100) {
        return String.format("%.2f", rawX100 / 100.0D);
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

    // ==================== cl.f.detail 单帧解析（(a)(b)(c) 数据基座） ====================

    /** 词条键：链蒸汽耗行（蒸汽口径组内；展开 = LINK 逐步 T_i/C_i 计算路径）。 */
    private static final String KEY_STEAM_CHAIN = "STEAM_CHAIN";

    /** 链步枚举缓存（LINK 行 ordinal → 本地名；链路页 LINKS 同款）。 */
    private static final ChainLink[] CHAIN_LINKS = ChainLink.values();

    /** 可展开词条（正文 + 计算路径小字行；draw 每帧重建的临时结构）。 */
    private static final class EntryRow {

        /** 互斥展开键（词条唯一；点击同键再点折叠）。 */
        final String key;
        /** 词条正文（不含行首箭头——绘制时按展开态加 ▶/▼ 前缀）。 */
        String text;
        /** 计算路径小字行（展开时缩进 0.6f 渲染；服务端缺真值段以 "--" 占位）。 */
        final List<String> expansion = new ArrayList<>();

        EntryRow(String key) {
            this.key = key;
        }
    }

    /**
     * cl.f.detail 单帧解析结果（draw 每帧重建；畸形行/段丢弃；LOGI/PEAK 本页不消费直接跳过）。
     * 数值哨兵：合计/wip 字段 {@code -1} = 服务端未下发（旧服务端降级），LUBE 分量
     * {@code Integer.MIN_VALUE} = 缺行。
     */
    private static final class DetailFrame {

        /** FMOD 行：[unitIdx, seg, timeX100, par, thruX100, steamX100]（token 序）。 */
        final List<int[]> fmods = new ArrayList<>();
        /** LINK 行：[ordinal, timeX100, steamX100]（链序；链蒸汽耗展开用）。 */
        final List<int[]> linkSteps = new ArrayList<>();
        /** FLUID 行：[liters]（与 {@link #fluidNames} 同下标）。 */
        final List<long[]> fluidLiters = new ArrayList<>();
        /** FLUID 行：流体注册名（可含 ':'，取首尾定界之间）。 */
        final List<String> fluidNames = new ArrayList<>();
        /** LUBE:cluster 值（×100 定点；MIN_VALUE=缺行）。 */
        int lubeCluster = Integer.MIN_VALUE;
        /** LUBE:logi 值（×100 定点；MIN_VALUE=缺行——未选中单元/旧服务端）。 */
        int lubeLogi = Integer.MIN_VALUE;
        /** BOOST 行：[typeOrdinal, lpsX100]（恒 5 行；token 序）。 */
        final List<int[]> boosts = new ArrayList<>();
        /** FTOT 流体合计（L；-1=缺 token）。 */
        long ftotFluid = -1L;
        /** FTOT 润滑合计（×100 定点；-1=缺 token）。 */
        int ftotLube = -1;
        /** FTOT 增幅合计（×100 定点；-1=缺 token）。 */
        int ftotBoost = -1;
        /** FWIP wip 流体倍率（×100 定点；-1=缺 token）。 */
        int fwip = -1;
    }

    /**
     * cl.f.detail 单帧解析（{@code |} 分行、行首令牌分发；{@code :} 分段，畸形行丢弃）：
     * FMOD/FTOT/FWIP 为 v1.20.14 (a)(b) 新增令牌；LINK 供链蒸汽耗展开 (c)；FLUID/LUBE/BOOST
     * 供集群详情词条 (b)(c)(d)。未知令牌（LOGI/PEAK 及未来扩展）静默跳过——前向兼容。
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
            } else if (row.startsWith("FWIP:")) {
                int[] v = parseIntValues(row, 1);
                if (v != null) f.fwip = v[0];
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

    /** 链路概览四行 + 增幅汇总七行 + 增幅实耗逐增幅器行（公式串随行缓存供 tooltip）。 */
    private List<String> buildChainsBoostLines() {
        List<String> out = new ArrayList<>();
        this.boostFormulas.clear();
        out.add(groupHeader("gtsr.terminal.perf.group.chains"));
        int count = 0, sum = 0, longest = 0;
        for (int len : leChainLens()) {
            count++;
            sum += len;
            if (len > longest) longest = len;
        }
        if (count == 0) {
            out.add(mutedLine("gtsr.terminal.perf.chains.count", "--"));
            out.add(mutedLine("gtsr.terminal.perf.chains.steps", "--"));
            out.add(mutedLine("gtsr.terminal.perf.chains.longest", "--"));
            out.add(mutedLine("gtsr.terminal.perf.chains.avg", "--"));
        } else {
            out.add(kvLine("gtsr.terminal.perf.chains.count", String.valueOf(count)));
            out.add(kvLine("gtsr.terminal.perf.chains.steps", String.valueOf(sum)));
            out.add(kvLine("gtsr.terminal.perf.chains.longest", String.valueOf(longest)));
            out.add(kvLine("gtsr.terminal.perf.chains.avg", String.format("%.1f", (double) sum / count)));
        }
        out.add(groupHeader("gtsr.terminal.perf.group.boost"));
        int[] sum8 = summary();
        out.add(kvLine("gtsr.cluster.gui.boost.sum.speed", pctText(sum8[0])));
        out.add(kvLine("gtsr.cluster.gui.boost.sum.parallel", "+" + sum8[1]));
        out.add(kvLine("gtsr.cluster.gui.boost.sum.primary", pctText(sum8[2])));
        out.add(kvLine("gtsr.cluster.gui.boost.sum.secondary", pctText(sum8[3])));
        out.add(kvLine("gtsr.cluster.gui.boost.sum.saver", saverText(sum8[4])));
        out.add(kvLine("gtsr.cluster.gui.boost.sum.mult", multText(sum8[5])));
        out.add(EnumChatFormatting.YELLOW + String.format(tr("gtsr.cluster.gui.boost.sum.count"), sum8[6], sum8[7]));
        out.add(groupHeader("gtsr.terminal.perf.group.boostcost"));
        this.boostCostStart = out.size();
        List<int[]> costs = costRows();
        for (int i = 0; i < costs.size(); i++) {
            int[] cost = costs.get(i);
            this.boostFormulas.add(costFormulaText(cost));
            out.add(EnumChatFormatting.WHITE + "#" + (i + 1) + " " + rateText(cost[0]) + " L/s");
        }
        return out;
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

    /** KEY_BO_COST 全条目解析（{@code lpsX10:base[:pct:tier:type]...}；畸形条目丢弃，增幅页 costRow 同防御）。 */
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

    /** 秒耗文本：×10 定点 → 整数值省小数、非整数保留一位小数（如 575→"57.5"、400→"40"；增幅页同款迁入）。 */
    private static String rateText(int lpsX10) {
        if (lpsX10 % 10 == 0) return String.valueOf(lpsX10 / 10);
        return String.format("%.1f", lpsX10 / 10.0D);
    }

    /**
     * S7 公式串（tooltip，代入实值）：{@code 基础 50 × (1 + 10%[速度 钢] + 5%[并行 青铜]) = 57.5 L/s}。
     * 增幅页 costFormulaText 语义移植（参数化 cost 数组；NumberFormatUtil 调用点换
     * {@link GtsrNumFormat#grouped}）；无联动加成时显示 {@code 基础 N × (1) = N L/s}；
     * 无实耗数据（基础值 ≤ 0）返回空串不出 tooltip。
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
            .append(" L/s");
        return sb.toString();
    }

    /** 施加方类型短标签（SPEED/PARALLEL；越界回退并行；增幅页同款迁入）。 */
    private static String sourceLabel(int typeOrdinal) {
        if (typeOrdinal == ClusterParams.BoosterType.SPEED.ordinal()) {
            return tr("gtsr.cluster.gui.boost.cost.src.speed");
        }
        return tr("gtsr.cluster.gui.boost.cost.src.parallel");
    }

    /** 施加方 tier 标签（复用集群层级 lang key；越界回 "--"；增幅页同款迁入）。 */
    private static String tierLabel(int tier) {
        if (tier < 0 || tier >= ClusterParams.TIER_COUNT) return "--";
        return tr(
            ClusterParams.ClusterTier.get(tier)
                .getLangKey());
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
     * 内容区内点击：三栏列表先行接管（滚动条拖拽/第二列词条行展开切换/区内其余点击消费防穿透，
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
