package com.miaokatze.gtsr.client.gui.terminal;

import net.minecraft.util.ResourceLocation;

/**
 * gtsr 终端轨原版风 GUI 贴图资源常量表：15 张贴图的 {@link ResourceLocation} 与真实像素尺寸的唯一权威来源。
 * <p>
 * 尺寸常量（{@code _W}/{@code _H}）与贴图文件一一配对，是 {@link GtsrGuiDrawing} UV 归一化时
 * 唯一允许引用的尺寸来源——绘制代码不得再写死任何纹理分辨率。
 * <p>
 * 契约出处：plan/ui/terminal-native-ui/texture-list.md §3「贴图家族」（15 张，产物直写
 * {@code src/main/resources/assets/gtsr/textures/gui/}，由
 * {@code plan/ui/terminal-native-ui/tools/gen_gui_textures.py} 生成，禁手改）。
 * 文件名、尺寸常量与该表逐项对应（生成器 SIZES dict 与本表互为断言：脚本 IHDR 尺寸自校验
 * 失败即 FAIL 退出），若贴图实际分辨率与常量不符视为贴图侧缺陷，不得修改本表迁就。
 */
public final class GtsrGuiTextures {

    private GtsrGuiTextures() {}

    /** 拼接 gtsr GUI 贴图路径：name → "gtsr:textures/gui/{name}.png" */
    private static ResourceLocation rl(String name) {
        return new ResourceLocation("gtsr", "textures/gui/" + name + ".png");
    }

    // ---------------------------------------------------------------------
    // 整版面板（§2 三块整版，整绘 1:1，不用 9-slice）
    // ---------------------------------------------------------------------

    /** §3 #1 枢纽状态三件套整版面板 320×200（奇点钻井/蒸汽/蓄水共用，N10-N13 接线） */
    public static final ResourceLocation PANEL_HUB_STATUS = rl("panel_hub_status");
    /** #1 宽 320 px */
    public static final int PANEL_HUB_STATUS_W = 320;
    /** #1 高 200 px */
    public static final int PANEL_HUB_STATUS_H = 200;

    /** §3 #2 聚合器配置整版面板 475×350（N14 接线） */
    public static final ResourceLocation PANEL_AGGREGATOR = rl("panel_aggregator");
    /** #2 宽 475 px */
    public static final int PANEL_AGGREGATOR_W = 475;
    /** #2 高 350 px */
    public static final int PANEL_AGGREGATOR_H = 350;

    /** §3 #3 集群终端主壳整版面板 620×340（ClusterParams.GUI_WIDTH/HEIGHT 冻结尺寸，N15 接线） */
    public static final ResourceLocation PANEL_CLUSTER = rl("panel_cluster");
    /** #3 宽 620 px */
    public static final int PANEL_CLUSTER_W = 620;
    /** #3 高 340 px */
    public static final int PANEL_CLUSTER_H = 340;

    // ---------------------------------------------------------------------
    // 按钮（64×20，9-slice 切片 4px）
    // ---------------------------------------------------------------------

    /** §3 #4 按钮常态：凸起 BTN_FACE 面芯 + 亮/暗斜面（GtsrGuiButton normal） */
    public static final ResourceLocation BUTTON_NORMAL = rl("button_normal");
    /** #4 宽 64 px */
    public static final int BUTTON_NORMAL_W = 64;
    /** #4 高 20 px */
    public static final int BUTTON_NORMAL_H = 20;

    /** §3 #5 按钮 hover：BTN_HOVER 面芯 + 顶缘 1px 琥珀（GtsrGuiButton hover） */
    public static final ResourceLocation BUTTON_HOVER = rl("button_hover");
    /** #5 宽 64 px */
    public static final int BUTTON_HOVER_W = 64;
    /** #5 高 20 px */
    public static final int BUTTON_HOVER_H = 20;

    /** §3 #6 按钮禁用：平框 + BTN_DISABLED 面芯（GtsrGuiButton disabled） */
    public static final ResourceLocation BUTTON_DISABLED = rl("button_disabled");
    /** #6 宽 64 px */
    public static final int BUTTON_DISABLED_W = 64;
    /** #6 高 20 px */
    public static final int BUTTON_DISABLED_H = 20;

    // ---------------------------------------------------------------------
    // 标签页（28×28，9-slice 切片 4px）
    // ---------------------------------------------------------------------

    /** §3 #7 标签页选中：凸起 + 顶缘 1px 琥珀（N15 左页签轨 3×28×28 竖排） */
    public static final ResourceLocation TAB_ACTIVE = rl("tab_active");
    /** #7 宽 28 px */
    public static final int TAB_ACTIVE_W = 28;
    /** #7 高 28 px */
    public static final int TAB_ACTIVE_H = 28;

    /** §3 #8 标签页未选中：凹陷 INSET 面芯（N15 左页签轨） */
    public static final ResourceLocation TAB_INACTIVE = rl("tab_inactive");
    /** #8 宽 28 px */
    public static final int TAB_INACTIVE_W = 28;
    /** #8 高 28 px */
    public static final int TAB_INACTIVE_H = 28;

    // ---------------------------------------------------------------------
    // 滚动条（纵向 9-slice 切片 2px）
    // ---------------------------------------------------------------------

    /** §3 #9 滚动条轨道：INSET 凹陷 + 上下端帽（宽 6 不变） */
    public static final ResourceLocation SCROLLBAR_TRACK = rl("scrollbar_track");
    /** #9 宽 6 px */
    public static final int SCROLLBAR_TRACK_W = 6;
    /** #9 高 16 px */
    public static final int SCROLLBAR_TRACK_H = 16;

    /** §3 #10 滚动条滑块：凸起 BTN_FACE */
    public static final ResourceLocation SCROLLBAR_THUMB = rl("scrollbar_thumb");
    /** #10 宽 6 px */
    public static final int SCROLLBAR_THUMB_W = 6;
    /** #10 高 12 px */
    public static final int SCROLLBAR_THUMB_H = 12;

    // ---------------------------------------------------------------------
    // 列表与槽格、小件（9-slice 切片 4px）
    // ---------------------------------------------------------------------

    /** §3 #11 列表行 hover：ROW_HOVER 填充 + 左缘 2px 琥珀暗线（横向 9-slice 切片 4px） */
    public static final ResourceLocation ROW_HOVER = rl("row_hover");
    /** #11 宽 32 px */
    public static final int ROW_HOVER_W = 32;
    /** #11 高 20 px */
    public static final int ROW_HOVER_H = 20;

    /** §3 #12 列表底：INSET 凹陷（9-slice 切片 4px，GtsrGuiList 背景） */
    public static final ResourceLocation LIST_PANEL = rl("list_panel");
    /** #12 宽 32 px */
    public static final int LIST_PANEL_W = 32;
    /** #12 高 32 px */
    public static final int LIST_PANEL_H = 32;

    /** §3 #13 聚合器槽格：凹陷 INSET 18×18（N14 25 槽格，物品 renderItem 叠其上） */
    public static final ResourceLocation SLOT_FRAME = rl("slot_frame");
    /** #13 宽 18 px */
    public static final int SLOT_FRAME_W = 18;
    /** #13 高 18 px */
    public static final int SLOT_FRAME_H = 18;

    /** §3 #14 chip 常态：凹陷 INSET 小件（N15 底栏提示/异常摘要、N17 链路 chip） */
    public static final ResourceLocation CHIP_NORMAL = rl("chip_normal");
    /** #14 宽 32 px */
    public static final int CHIP_NORMAL_W = 32;
    /** #14 高 16 px */
    public static final int CHIP_NORMAL_H = 16;

    /** §3 #15 chip 激活：凸起 BTN_FACE 小件 */
    public static final ResourceLocation CHIP_ACTIVE = rl("chip_active");
    /** #15 宽 32 px */
    public static final int CHIP_ACTIVE_W = 32;
    /** #15 高 16 px */
    public static final int CHIP_ACTIVE_H = 16;
}
