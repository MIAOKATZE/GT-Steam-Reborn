package com.miaokatze.gtsr.common.gui.cluster;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.miaokatze.gtsr.common.gui.AbstractGTSRPosUiFactory;
import com.miaokatze.gtsr.common.machine.cluster.ChainLink;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams;
import com.miaokatze.gtsr.common.machine.cluster.LogisticsChain;
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicLogisticsUnit;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.modularui2.GTGuiTextures;

/**
 * 物流模块自身 GUI（批2 E6 全量重写；右击物流模块只打开本 GUI——链编辑在集群终端）。
 *
 * <p>
 * 内容（plan §7.5「物流模块 GUI」逐条）：段/垫/tier；已连接 + 自身成型；链摘要/冷却/链不可执行原因；
 * 水/化浴存量 + 每批真实需求（{@link ClusterParams#WASH_WATER_PER_BATCH_L}/
 * {@link ClusterParams#CHEM_BATH_FLUID_PER_BATCH_L}，仅链含对应链步时需求成立）；开关状态 +
 * 软锤复位指引。
 *
 * <p>
 * 同步（全部 S2C 单向，无 C2S——电源切换走软锤）：段/垫/tier/连接/成型/电源为 10t 级标量
 * （Int/BooleanSyncValue 变化即发）；链快照/可执行两级判定/失败步为服务端真值；冷却按 20t 采样
 * （秒粒度显示，避免每 tick 推送）。文案键族 {@code gtsr.cluster.gui.logistics.*}（E7 落盘）。
 */
public class MTEBasicLogisticsUnitGui implements IGuiHolder<PosGuiData> {

    /** 面板尺寸（信息页：宽 220，高按行数）。 */
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 196;
    private static final int CONTENT_X = 10;
    private static final int CONTENT_Y = 18;
    /** 行高与行距。 */
    private static final int ROW_H = 12;
    /** 冷却缓存 20t 采样状态。 */
    private long cooldownLastTick = Long.MIN_VALUE;
    private int cooldownCacheSec;

    private final MTEBasicLogisticsUnit machine;

    public MTEBasicLogisticsUnitGui(MTEBasicLogisticsUnit machine) {
        this.machine = machine;
    }

    public MTEBasicLogisticsUnit getMachine() {
        return machine;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModularScreen createScreen(PosGuiData data, ModularPanel mainPanel) {
        return new ModularScreen(GTSteamReborn.MODID, mainPanel);
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        // —— 身份行 ——
        IntSyncValue segment = new IntSyncValue(machine::getSegmentIndex);
        IntSyncValue pad = new IntSyncValue(machine::getPadId);
        IntSyncValue tier = new IntSyncValue(machine::getLogisticsStructureTier);
        BooleanSyncValue connected = new BooleanSyncValue(machine::isClusterConnected);
        BooleanSyncValue formed = new BooleanSyncValue(machine::isUnitStructureFormed);
        BooleanSyncValue powered = new BooleanSyncValue(machine::isPowerAllowed);
        syncManager.syncValue("gtsr.cluster.logi.seg", segment);
        syncManager.syncValue("gtsr.cluster.logi.pad", pad);
        syncManager.syncValue("gtsr.cluster.logi.tier", tier);
        syncManager.syncValue("gtsr.cluster.logi.conn", connected);
        syncManager.syncValue("gtsr.cluster.logi.formed", formed);
        syncManager.syncValue("gtsr.cluster.logi.power", powered);

        // —— 链与执行（服务端真值；链编辑只在集群终端）——
        StringSyncValue chain = new StringSyncValue(() -> {
            LogisticsChain c = machine.getChain();
            if (c == null) return "";
            int[] ordinals = c.toOrdinalArray();
            StringBuilder sb = new StringBuilder(ordinals.length * 3);
            for (int i = 0; i < ordinals.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(ordinals[i]);
            }
            return sb.toString();
        });
        IntSyncValue execLevel = new IntSyncValue(() -> {
            LogisticsChain c = machine.getChain();
            if (c == null || c.isEmpty()) return 0;
            if (!c.isValidStructure()) return 0;
            return machine.isChainExecutableNow() ? 2 : 1;
        });
        IntSyncValue failLink = new IntSyncValue(() -> {
            LogisticsChain c = machine.getChain();
            if (c == null || c.isEmpty() || !c.isValidStructure()) return 0;
            MTESteamMineralLogisticsCluster cluster = machine.getCluster();
            if (cluster == null) return 0;
            for (ChainLink link : c.getLinks()) {
                if (LogisticsChain.getLinkLockReasonKey(link, cluster.getTopology()) != null) return link.ordinal() + 1;
            }
            return 0;
        });
        // 冷却：秒粒度 20t 采样（避免逐 tick 推送）
        IntSyncValue cooldownSec = new IntSyncValue(() -> sampledCooldownSec());
        syncManager.syncValue("gtsr.cluster.logi.chain", chain);
        syncManager.syncValue("gtsr.cluster.logi.exec", execLevel);
        syncManager.syncValue("gtsr.cluster.logi.fail", failLink);
        syncManager.syncValue("gtsr.cluster.logi.cooldown", cooldownSec);

        // —— 双 tank 存量与每批需求（只读）——
        IntSyncValue water = new IntSyncValue(
            () -> machine.getWaterTank()
                .getFluidAmount());
        IntSyncValue chem = new IntSyncValue(
            () -> machine.getChemBathTank()
                .getFluidAmount());
        BooleanSyncValue needWater = new BooleanSyncValue(
            () -> machine.getChain() != null && machine.getChain()
                .countOf(ChainLink.ORE_WASH) > 0);
        BooleanSyncValue needChem = new BooleanSyncValue(
            () -> machine.getChain() != null && machine.getChain()
                .countOf(ChainLink.CHEM_BATH) > 0);
        syncManager.syncValue("gtsr.cluster.logi.water", water);
        syncManager.syncValue("gtsr.cluster.logi.chem", chem);
        syncManager.syncValue("gtsr.cluster.logi.needWater", needWater);
        syncManager.syncValue("gtsr.cluster.logi.needChem", needChem);

        // —— 布局（绝对定位行：标签灰 + 值白/状态色）——
        Flow column = Flow.column()
            .child(row("gtsr.cluster.gui.logistics.seg", () -> String.valueOf(segment.getIntValue())))
            .child(
                row(
                    "gtsr.cluster.gui.logistics.pad",
                    () -> pad.getIntValue() >= 0 ? String.valueOf(pad.getIntValue()) : "--"))
            .child(
                row(
                    "gtsr.cluster.gui.logistics.tier",
                    () -> tier.getIntValue() >= 0 ? "T" + (tier.getIntValue() + 1) : "--"))
            .child(boolRow("gtsr.cluster.gui.logistics.connected", connected))
            .child(boolRow("gtsr.cluster.gui.logistics.formed", formed))
            .child(boolRow("gtsr.cluster.gui.logistics.power", powered))
            .child(row("gtsr.cluster.gui.logistics.chain", () -> chainSummary(chain)))
            .child(
                row(
                    "gtsr.cluster.gui.logistics.cooldown",
                    () -> cooldownSec.getIntValue() > 0 ? cooldownSec.getIntValue() + " s" : "--"))
            .child(execLine(execLevel, failLink, connected, powered, chain))
            .child(tankLine("gtsr.cluster.gui.logistics.water", water, needWater, ClusterParams.WASH_WATER_PER_BATCH_L))
            .child(
                tankLine("gtsr.cluster.gui.logistics.chem", chem, needChem, ClusterParams.CHEM_BATH_FLUID_PER_BATCH_L))
            .coverChildren()
            .childPadding(3);

        return ModularPanel.defaultPanel("gtsr_logistics_unit", PANEL_WIDTH, PANEL_HEIGHT)
            .background(GTGuiTextures.BACKGROUND_STANDARD)
            .child(ButtonWidget.panelCloseButton())
            .child(
                IKey.lang("gtsr.gui.cluster.unit_type.logistics")
                    .asWidget()
                    .pos(CONTENT_X, 5))
            // Flow 不得作为面板直接 child（pos 失效被居中）：绝对定位 ParentWidget 承载，Flow 仅内部嵌套
            .child(
                new com.cleanroommc.modularui.widget.ParentWidget<>().pos(CONTENT_X, CONTENT_Y)
                    .size(PANEL_WIDTH - CONTENT_X * 2, PANEL_HEIGHT - CONTENT_Y - 28)
                    .child(column))
            // 软锤复位指引（底部灰字两行）
            .child(
                IKey.str(
                    EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.cluster.gui.logistics.soft_hammer"))
                    .asWidget()
                    .pos(CONTENT_X, PANEL_HEIGHT - 24)
                    .scale(0.65f)
                    .width(PANEL_WIDTH - CONTENT_X * 2));
    }

    /** 冷却秒数 20t 采样（基 TE 失联时每次重算的防御口径）。 */
    private int sampledCooldownSec() {
        IGregTechTileEntity base = machine.getBaseMetaTileEntity();
        long now = base != null ? base.getTimer() : -1L;
        if (now < 0 || now - cooldownLastTick >= 20) {
            cooldownCacheSec = (int) (machine.getChainCooldownTicks() / 20L);
            cooldownLastTick = now;
        }
        return cooldownCacheSec;
    }

    /** 链摘要：N 步：粉碎→锻造锤→…（超过 5 步截断 + …）。 */
    private static String chainSummary(StringSyncValue chain) {
        List<Integer> ordinals = ClusterGuiSync
            .parseIntList(chain.getValue() == null ? "" : chain.getValue(), ChainLink.values().length);
        if (ordinals.isEmpty()) return "--";
        ChainLink[] links = ChainLink.values();
        StringBuilder sb = new StringBuilder(ordinals.size() + " · ");
        int limit = Math.min(5, ordinals.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append("→");
            sb.append(StatCollector.translateToLocal(links[ordinals.get(i)].getLangKey()));
        }
        if (ordinals.size() > limit) sb.append("→…");
        return sb.toString();
    }

    /** 可执行性行：绿 可执行 / 灰 冷却中 / 红 具体原因（未关联/电源关/空链/非终态/缺模块）。 */
    private static com.cleanroommc.modularui.api.widget.IWidget execLine(IntSyncValue execLevel, IntSyncValue failLink,
        BooleanSyncValue connected, BooleanSyncValue powered, StringSyncValue chain) {
        return rowDynamic("gtsr.cluster.gui.logistics.exec", () -> {
            boolean conn = connected.getValue() != null && connected.getValue();
            boolean power = powered.getValue() != null && powered.getValue();
            String chainStr = chain.getValue() == null ? "" : chain.getValue();
            if (!conn) return red(tr("gtsr.cluster.gui.logistics.reason.unlinked"));
            if (!power) return red(tr("gtsr.cluster.gui.logistics.reason.power_off"));
            if (chainStr.isEmpty()) return red(tr("gtsr.cluster.gui.logistics.reason.empty"));
            int exec = execLevel.getIntValue();
            if (exec == 2) return green("✔ " + tr("gtsr.cluster.gui.logistics.reason.ready"));
            if (exec == 0) return red(tr("gtsr.cluster.gui.logistics.reason.not_terminal"));
            int fail = failLink.getIntValue();
            if (fail > 0 && fail <= ChainLink.values().length) {
                return red(
                    String.format(
                        tr("gtsr.cluster.gui.logistics.reason.need_module"),
                        tr(ChainLink.values()[fail - 1].getLangKey())));
            }
            return gray(tr("gtsr.cluster.gui.logistics.reason.other"));
        });
    }

    /** tank 行：存量 L +（需要时）每批真实需求；不足红字。 */
    private static com.cleanroommc.modularui.api.widget.IWidget tankLine(String labelKey, IntSyncValue amount,
        BooleanSyncValue needed, int batchNeedL) {
        return rowDynamic(labelKey, () -> {
            boolean need = needed.getValue() != null && needed.getValue();
            int current = amount.getIntValue();
            String value = current + " L";
            if (!need) return gray(value + " " + tr("gtsr.cluster.gui.logistics.need.none"));
            String demand = " " + String.format(tr("gtsr.cluster.gui.logistics.need.batch"), batchNeedL);
            return current >= batchNeedL ? green(value + demand) : red(value + demand);
        });
    }

    /** 静态标签 + 动态值行（灰标签/白值）。 */
    private static com.cleanroommc.modularui.api.widget.IWidget row(String labelKey,
        java.util.function.Supplier<String> value) {
        return Flow.row()
            .child(
                IKey.lang(labelKey)
                    .style(EnumChatFormatting.GRAY)
                    .asWidget()
                    .width(60))
            .child(
                IKey.dynamic(value::get)
                    .style(EnumChatFormatting.WHITE)
                    .asWidget())
            .coverChildren()
            .childPadding(4);
    }

    /** 静态标签 + 状态色动态值行（可执行性/tank 用）。 */
    private static com.cleanroommc.modularui.api.widget.IWidget rowDynamic(String labelKey,
        java.util.function.Supplier<String> value) {
        return Flow.row()
            .child(
                IKey.lang(labelKey)
                    .style(EnumChatFormatting.GRAY)
                    .asWidget()
                    .width(60))
            .child(
                IKey.dynamic(value::get)
                    .asWidget())
            .coverChildren()
            .childPadding(4);
    }

    /** 布尔行：绿 是/开 或 红 否/关。 */
    private static com.cleanroommc.modularui.api.widget.IWidget boolRow(String labelKey, BooleanSyncValue value) {
        return rowDynamic(
            labelKey,
            () -> (value.getValue() != null && value.getValue()) ? green(tr("gtsr.cluster.gui.logistics.on"))
                : red(tr("gtsr.cluster.gui.logistics.off")));
    }

    private static String green(String text) {
        return EnumChatFormatting.GREEN + text;
    }

    private static String red(String text) {
        return EnumChatFormatting.RED + text;
    }

    private static String gray(String text) {
        return EnumChatFormatting.GRAY + text;
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }

    /**
     * 物流模块状态页 MUI2 工厂（嵌套自包含：INSTANCE + open()；CommonProxy 双端注册，open 内
     * hasFactory 守卫惰性自注册兜底专用服联机前的单人场景，与 registerFactory 幂等不冲突）。
     */
    public static final class LogisticsUnitGuiFactory extends AbstractGTSRPosUiFactory<MTEBasicLogisticsUnit> {

        /** 工厂名（≤32 字符，网络包按名解析，双端一致）。 */
        public static final String GUI_ID = "gtsr.logistics_unit";

        public static final LogisticsUnitGuiFactory INSTANCE = new LogisticsUnitGuiFactory();

        private LogisticsUnitGuiFactory() {
            super(GUI_ID, MTEBasicLogisticsUnit.class, MTEBasicLogisticsUnitGui::new, "Cluster Logistics Unit");
        }

        /** 服务端调用：为玩家打开物流模块状态页（右击物流模块唯一 GUI 入口）。 */
        public static void open(EntityPlayer player, MTEBasicLogisticsUnit unit) {
            if (!GuiManager.hasFactory(GUI_ID)) GuiManager.registerFactory(INSTANCE);
            INSTANCE.openGui(player, unit);
        }
    }
}
