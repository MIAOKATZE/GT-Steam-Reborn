package com.miaokatze.gtsr.client.gui.terminal;

/**
 * gtsr 终端轨 GUI 文字与语义色板（深色工业琥珀主题，代码绘制专用）。
 * <p>
 * 文字/状态色均为不透明 ARGB（0xFF 前缀），可直接传给
 * {@code FontRenderer.drawString / drawStringWithShadow}；分隔线 {@link #DIVIDER} 与
 * 面板 alpha 档保留含 alpha 形式供 {@code Gui.drawRect} 使用。
 * <p>
 * 数值首发逐字取 GTSWN 同款（裁决：PLAN §4.6 / §8.4-L2 默认照搬，契约
 * plan/ui/terminal-native-ui/texture-list.md §1/§4/§5 定稿；例外：TEXT_LABEL/TEXT_MUTED 经
 * 400×240 终端枢纽切片 A 上调可读性，旧值→新值记档于字段注释与契约 §5）；
 * 颜色一律不烘进 PNG，后续整装换皮只改本类与契约 §1，绘制逻辑零改动。
 * <p>
 * 旧 MUI2/EnumChatFormatting 着色语义 → token 映射表见契约 §5；lang 键自带 § 色码
 * 保持原样（行为不回归红线），仅代码新绘制文字走本类 token。
 * <p>
 * 最小用法（宿主 GuiScreen 自绘行内）：
 *
 * <pre>
 * font.drawString("ONLINE", x, y, GtsrGuiPalette.STATE_ONLINE);
 * GtsrGuiPalette.DIVIDER 用 Gui.drawRect 画分隔线（含 alpha）
 * </pre>
 */
public final class GtsrGuiPalette {

    private GtsrGuiPalette() {}

    // ---------------------------------------------------------------------
    // 文字色（不透明 ARGB，供 drawString 系调用）
    // ---------------------------------------------------------------------

    /** 面板标题字（GTSWN 原值；替代旧 MUI2 标题用法） */
    public static final int TEXT_TITLE = 0xFFF0E8D8;

    /** 正文/列头字（GTSWN 原值） */
    public static final int TEXT_BODY = 0xFFD8D4C8;

    /**
     * 标签字（400×240 切片 A 可读性上调：GTSWN 原值 0xB0AA9A → 0xC8C2B2，
     * 契约 texture-list.md §5 记档；列头标签/次要标签用）
     */
    public static final int TEXT_LABEL = 0xFFC8C2B2;

    /**
     * 提示/占位/灰字（400×240 切片 A 可读性上调：GTSWN 原值 0x9AA0A8 → 0xB8BEC8，
     * 契约 texture-list.md §5 记档；底栏运行提示/异常摘要用）
     */
    public static final int TEXT_MUTED = 0xFFB8BEC8;

    /** 强调字：警示强调、选中页签字（GTSWN 原值，与 HAZARD_AMBER #F0A028 同源） */
    public static final int TEXT_ACCENT = 0xFFF0A028;

    /** 纯白字（与旧 0xFFFFFF 一致，不变） */
    public static final int TEXT_WHITE = 0xFFFFFFFF;

    // ---------------------------------------------------------------------
    // 语义状态色（数据状态文字用，GTSWN 原值）
    // ---------------------------------------------------------------------

    /** 在线/正常（替代旧 §a GREEN 语义） */
    public static final int STATE_ONLINE = 0xFF4CE08A;

    /** 离线/断开（替代旧 §c RED 语义） */
    public static final int STATE_OFFLINE = 0xFFF05A5A;

    /** 空闲/待机（替代旧 §e YELLOW 语义） */
    public static final int STATE_IDLE = 0xFFF0B03C;

    /** 过载/异常（替代旧 §4 DARK_RED 语义） */
    public static final int STATE_OVERLOAD = 0xFFFF4040;

    // 缺失/缺流（旧 §5 DARK_PURPLE 语义，PLAN §4.6）：STATE_MISSING 为 S5 集群增幅页
    // 尾追 token（枚举/常量纪律：尾追不改既有值），S1 风格基座不建，契约 §5 记档。

    // ---------------------------------------------------------------------
    // 线条/叠层色（含 alpha 形式，供 drawRect）
    // ---------------------------------------------------------------------

    /** 分隔线琥珀暗线（GTSWN 原值，drawRect 用含 alpha 形式） */
    public static final int DIVIDER = 0xFF7A5A1E;

    /**
     * 模态遮罩/下拉浮层压暗底（GTSR 首发值，契约 §5 注：GTSWN 无对应 token）。
     * 色相取预览板底 #101216 同族（HAZARD_DARK 近亲）。
     */
    public static final int PANEL_SCRIM = 0x80101216;

    /**
     * 轻压暗叠层（GTSR 首发值，契约 §5 注）：弱分隔/弱高亮档，同色相 25% 不透明。
     */
    public static final int PANEL_GLASS = 0x40101216;
}
