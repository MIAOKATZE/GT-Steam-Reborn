package com.miaokatze.gtsr.common.gui;

import java.util.Arrays;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiPalette;
import com.miaokatze.gtsr.common.critical.CriticalConfiguration;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.critical.CriticalTier;
import com.miaokatze.gtsr.common.gui.mui2.TerminalViewport;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.modularui2.factory.GTBaseGuiBuilder;
import gregtech.common.modularui2.widget.GTProgressWidget;

/** Handheld terminal only; GT's native container lifecycle owns inventory and NEI integration. */
public final class MTECriticalMachineTerminalGui extends MTEMultiBlockBaseGui<MTECriticalMachine> {

    private final CriticalMachineState state;
    private EntityPlayer viewer;
    private IntSyncValue tier, kind, parallel, speed, economy, status, reason, stage, job, total, batch, duration;
    private LongSyncValue energy, capacity, euPerTick;
    private IntSyncValue constructionRound, constructionRounds, roundElapsed, roundDuration;
    private GenericListSyncHandler<ItemStack> constructionCosts;
    private BooleanSyncValue enabled, permitted, loomValid, roundFunded;
    private IntSyncValue steamMode;
    private StringSyncValue detail, inputOverview, outputOverview, actionName, actionDetail, jobProgressText;
    private BooleanSyncValue actionAccepted;
    private String lastAction = "", lastActionDetail = "";
    private boolean lastActionAccepted;
    private int selectedPage;
    private CriticalConstructionView constructionView;

    public MTECriticalMachineTerminalGui(MTECriticalMachine multiblock) {
        super(multiblock);
        state = multiblock.getCriticalState();
    }

    @Override
    public ModularPanel build(PosGuiData data, PanelSyncManager manager, UISettings settings) {
        // Sync-value constructors evaluate their getters before the modular container is bound.
        viewer = data.getPlayer();
        return super.build(data, manager, settings);
    }

    @Override
    protected ModularPanel getBasePanel(PosGuiData data, PanelSyncManager manager, UISettings settings) {
        // The industrial terminal already has its title inside statusHeader. GT's external
        // title tab extends above the panel and is clipped on a 640 x 360 logical canvas.
        return new GTBaseGuiBuilder(multiblock, data, manager, settings).setWidth(getBasePanelWidth())
            .setHeight(getBasePanelHeight())
            .doesAddTitle(false)
            .doesAddGregTechLogo(false)
            .doesBindPlayerInventory(false)
            .build()
            .background(CriticalGuiStyle.PANEL);
    }

    @Override
    protected int getBasePanelWidth() {
        return 475;
    }

    @Override
    protected int getBasePanelHeight() {
        return 350;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager manager) {
        // Only server-owned telemetry and guarded interaction handlers are exposed. In particular,
        // inherited feature setters are not registered as unrestricted C2S command endpoints.
        tier = integer(
            manager,
            "tier",
            () -> state.getTier()
                .ordinal());
        kind = integer(
            manager,
            "kind",
            () -> state.getKind()
                .ordinal());
        parallel = integer(manager, "parallel", state::getParallelPlugins);
        speed = integer(manager, "speed", state::getSpeedPlugins);
        economy = integer(manager, "economy", state::getEfficiencyPlugins);
        status = integer(manager, "status", state::getStatusCode);
        reason = integer(manager, "reason", state::getReasonCode);
        stage = integer(manager, "stage", state::getStage);
        job = integer(manager, "job", state::getJobProgress);
        total = integer(manager, "total", state::getJobTotal);
        batch = integer(manager, "batch", state::getBatchProgress);
        duration = integer(manager, "duration", state::getBatchDuration);
        energy = new LongSyncValue(state::getEnergyStored);
        capacity = new LongSyncValue(state::getEnergyCapacity);
        euPerTick = new LongSyncValue(state::getBatchEuPerTick);
        constructionCosts = itemCosts(state::getConstructionCostsForGui);
        loomValid = new BooleanSyncValue(state::isLoomStructureValid);
        roundFunded = new BooleanSyncValue(state::isConstructionRoundFunded);
        constructionRound = integer(manager, "constructionRound", state::getConstructionRoundIndex);
        constructionRounds = integer(manager, "constructionRounds", state::getConstructionRoundTotal);
        roundElapsed = integer(manager, "roundElapsed", state::getConstructionRoundElapsedTicks);
        roundDuration = integer(manager, "roundDuration", state::getConstructionRoundDurationTicks);
        enabled = new BooleanSyncValue(state::isEnabled);
        permitted = new BooleanSyncValue(() -> state.canUse(viewer));
        detail = new StringSyncValue(state::getStatus);
        steamMode = integer(manager, "steamMode", state::getSteamMode);
        manager.syncValue("critical.loomValid", loomValid);
        manager.syncValue("critical.roundFunded", roundFunded);
        manager.syncValue("critical.energy", energy);
        manager.syncValue("critical.capacity", capacity);
        manager.syncValue("critical.euPerTick", euPerTick);
        manager.syncValue("critical.constructionCosts", constructionCosts);
        manager.syncValue("critical.enabled", enabled);
        manager.syncValue("critical.permitted", permitted);
        manager.syncValue("critical.detail", detail);
        inputOverview = new StringSyncValue(multiblock::getCriticalInputOverviewForGui);
        outputOverview = new StringSyncValue(multiblock::getCriticalOutputOverviewForGui);
        actionName = new StringSyncValue(() -> lastAction);
        actionDetail = new StringSyncValue(() -> lastActionDetail);
        actionAccepted = new BooleanSyncValue(() -> lastActionAccepted);
        manager.syncValue("critical.inputOverview", inputOverview);
        manager.syncValue("critical.outputOverview", outputOverview);
        manager.syncValue("critical.actionName", actionName);
        manager.syncValue("critical.actionDetail", actionDetail);
        manager.syncValue("critical.actionAccepted", actionAccepted);
        // Send the stage cursor and total together so a stage transition cannot display mismatched counts.
        jobProgressText = new StringSyncValue(() -> state.getJobProgress() + " / " + state.getJobTotal());
        manager.syncValue("critical.jobProgressText", jobProgressText);
        constructionView = new CriticalConstructionView(state, manager);
    }

    private GenericListSyncHandler<ItemStack> itemCosts(java.util.function.Supplier<ItemStack[]> getter) {
        return new GenericListSyncHandler<>(() -> Arrays.asList(getter.get()), null, packet -> {
            ItemStack stack = NetworkUtils.readItemStack(packet);
            int count = packet.readInt();
            if (stack != null) stack.stackSize = count;
            return stack;
        }, (packet, stack) -> {
            NetworkUtils.writeItemStack(packet, stack);
            packet.writeInt(stack == null ? 0 : stack.stackSize);
        }, ItemStack::areItemStacksEqual, stack -> stack == null ? null : stack.copy());
    }

    private IntSyncValue integer(PanelSyncManager manager, String key, java.util.function.IntSupplier getter) {
        IntSyncValue value = new IntSyncValue(getter);
        manager.syncValue("critical." + key, value);
        return value;
    }

    @Override
    public Flow createMainColumn(ModularPanel panel, PanelSyncManager manager) {
        PagedWidget<?> pages = new PagedWidget<>().name("critical_pages")
            .size(463, 202);
        pages.addPage(constructionView.create());
        pages.addPage(controlPage(manager));
        pages.addPage(inventoryPage(manager));
        Flow tabs = Flow.row()
            .size(463, 20)
            .childPadding(4);
        String[] labels = { "tab.construction", "tab.configuration", "tab.inventory" };
        for (int i = 0; i < labels.length; i++) {
            final int page = i;
            tabs.child(
                new ButtonWidget<>().name("critical_tab_" + page)
                    .size(151, 18)
                    .background(
                        new DynamicDrawable(
                            () -> selectedPage == page ? CriticalGuiStyle.SELECTED_TAB : CriticalGuiStyle.TAB))
                    .hoverBackground(CriticalGuiStyle.SELECTED_TAB)
                    .tooltipBuilder(t -> t.add(IKey.lang(key(labels[page]))))
                    .overlay(
                        IKey.dynamic(() -> tr(labels[page]))
                            .color(
                                () -> selectedPage == page ? CriticalGuiStyle.TEXT_ACCENT : CriticalGuiStyle.TEXT_BODY))
                    .onMousePressed(button -> {
                        if (button != 0) return false;
                        pages.setPage(page);
                        selectedPage = page;
                        return true;
                    }));
        }
        Flow content = Flow.column()
            .pos(0, 0)
            .size(475, 350)
            .padding(6)
            .child(statusHeader())
            .child(tabs)
            .child(pages)
            .child(
                SlotGroupWidget.playerInventory(
                    (index, slot) -> slot.background(CriticalGuiStyle.SLOT)
                        .hoverBackground(CriticalGuiStyle.SLOT))
                    .marginTop(4)
                    .alignX(0.5f));
        return Flow.column()
            .child(
                TerminalViewport.wrapExact("critical_terminal_viewport", content, 475, 350)
                    .sizeRel(1f));
    }

    private IWidget statusHeader() {
        ParentWidget<?> header = new ParentWidget<>().name("critical_status_header")
            .size(463, 32)
            .background(CriticalGuiStyle.CARD);
        header.child(
            IKey.dynamic(this::terminalTitle)
                .color(CriticalGuiStyle.TEXT_TITLE)
                .asWidget()
                .pos(8, 4)
                .size(389, 12)
                .tooltipBuilder(t -> t.add(terminalTitle()))
                .tooltipAutoUpdate(true));
        header.child(
            IKey.dynamic(() -> configuration().tier.name())
                .color(CriticalGuiStyle.TEXT_ACCENT)
                .asWidget()
                .background(CriticalGuiStyle.CHIP)
                .pos(405, 3)
                .size(50, 14));
        header.child(
            IKey.dynamic(() -> tr(loomValid.getValue() ? "ui.loom_valid" : "ui.loom_invalid"))
                .color(() -> loomValid.getValue() ? CriticalGuiStyle.STATE_ONLINE : CriticalGuiStyle.STATE_OFFLINE)
                .asWidget()
                .pos(8, 19)
                .size(136, 10));
        header.child(
            textViewport(
                "critical_status_detail",
                () -> detail.getValue()
                    .equals("\u5f85\u914d\u7f6e") ? tr("status.0") : detail.getValue(),
                307,
                10,
                this::statusColor).pos(148, 19)
                    .tooltipBuilder(t -> t.add(detail.getValue()))
                    .tooltipAutoUpdate(true));
        return header;
    }

    private String terminalTitle() {
        // A loom's selected target defaults to SUN; it is not the identity of the opened controller.
        return multiblock.isKindSelector() ? StatCollector.translateToLocal("gt.blockmachines.gtsr.critical.loom.name")
            : tr("machine." + multiblock.getPresetKind().key);
    }

    private int statusColor() {
        int cause = reason.getValue();
        if (cause != 0 && cause != 7) return CriticalGuiStyle.STATE_OFFLINE;
        if (cause == 7 || status.getValue() == 0) return GtsrGuiPalette.STATE_IDLE;
        return status.getValue() == 2 ? CriticalGuiStyle.STATE_ONLINE : CriticalGuiStyle.TEXT_ACCENT;
    }

    private IWidget controlPage(PanelSyncManager manager) {
        ParentWidget<?> page = new ParentWidget<>().name("critical_page_control")
            .size(463, 202);
        page.child(
            action(manager, "kind", player -> change(player, 1, 1))
                .overlay(
                    IKey.dynamic(() -> tr("machine." + configuration().kind.key))
                        .color(CriticalGuiStyle.TEXT_CONTROL))
                .pos(0, 0)
                .size(210, 20)
                .setEnabledIf(w -> editable() && multiblock.isKindSelector())
                .tooltipBuilder(t -> t.add(IKey.lang(key("configuration_hint")))));
        page.child(
            action(manager, "steam_mode", state::selectSteamMode)
                .overlay(
                    IKey.dynamic(() -> tr("steam_mode." + steamMode.getValue()))
                        .color(CriticalGuiStyle.TEXT_CONTROL))
                .pos(215, 0)
                .size(160, 20)
                .setEnabledIf(w -> permitted.getValue() && configuration().kind == CriticalMachineKind.SOLAR));
        page.child(
            new ButtonWidget<>().name("critical_recipe_preview")
                .background(CriticalGuiStyle.BUTTON)
                .hoverBackground(CriticalGuiStyle.HOVER)
                .overlay(
                    IKey.lang(key("operation.details"))
                        .color(CriticalGuiStyle.TEXT_CONTROL))
                .pos(380, 0)
                .size(83, 20)
                .tooltipBuilder(this::operationTooltip)
                .tooltipAutoUpdate(true));
        page.child(
            metric(
                "ui.energy",
                () -> energy.getValue() + " / " + capacity.getValue() + " EU",
                () -> ratio(energy.getValue(), capacity.getValue()),
                0,
                26));
        page.child(
            metric(
                "batch",
                () -> time(batch.getValue(), duration.getValue()),
                () -> ratio(batch.getValue(), duration.getValue()),
                235,
                26));
        ParentWidget<?> summary = new ParentWidget<>().pos(0, 74)
            .size(463, 70)
            .background(CriticalGuiStyle.CARD);
        ListWidget<IWidget, ?> summaryText = new ListWidget<>().name("critical_status_summary")
            .pos(8, 5)
            .size(419, 60)
            .scrollDirection(new VerticalScrollData().cancelScrollEdge(false));
        constructionLine(
            summaryText,
            () -> tr("construction") + " "
                + Math.min(5, stage.getValue() + 1)
                + " / 5 | "
                + tr("construction.round")
                + " "
                + (constructionRounds.getValue() == 0 ? 0
                    : Math.min(constructionRounds.getValue(), constructionRound.getValue() + 1))
                + " / "
                + constructionRounds.getValue()
                + " | "
                + tr(roundFunded.getValue() ? "construction.funded" : "construction.unfunded"),
            () -> roundFunded.getValue() ? CriticalGuiStyle.TEXT_BODY : CriticalGuiStyle.TEXT_ACCENT);
        constructionLine(summaryText, () -> tr("ui.construction_progress") + " " + jobProgressText.getValue());
        constructionLine(
            summaryText,
            () -> tr("ui.construction_round") + " " + time(roundElapsed.getValue(), roundDuration.getValue()));
        constructionLine(
            summaryText,
            () -> tr("construction_power") + " "
                + configuration().tier.constructionVoltage()
                + " EU/t | "
                + tr("ui.batch_power")
                + " "
                + euPerTick.getValue()
                + " EU/t",
            () -> GtsrGuiPalette.TEXT_LABEL);
        summary.child(summaryText);
        GTProgressWidget batchProgress = new GTProgressWidget();
        if (multiblock.getRecipeMap() != null) batchProgress.neiTransferRect(multiblock.getRecipeMap());
        summary.child(
            batchProgress.texture(GTGuiTextures.PROGRESSBAR_ARROW_STANDARD, 20)
                .progress(() -> ratio(batch.getValue(), duration.getValue()))
                .pos(435, 48)
                .size(20, 16));
        page.child(summary);
        page.child(
            action(manager, "build", player -> state.startConstruction(player)).pos(0, 150)
                .size(112, 20)
                .setEnabledIf(w -> permitted.getValue() && loomValid.getValue() && status.getValue() == 0)
                .tooltipBuilder(this::constructionTooltip)
                .tooltipAutoUpdate(true));
        page.child(
            action(manager, "dismantle", player -> state.startDismantle(player)).pos(117, 150)
                .size(112, 20)
                .setEnabledIf(w -> permitted.getValue() && (status.getValue() == 1 || status.getValue() == 2)));
        page.child(
            action(manager, "repair", player -> state.repairStructure(player)).pos(234, 150)
                .size(112, 20)
                .setEnabledIf(w -> permitted.getValue() && loomValid.getValue() && status.getValue() != 0)
                .tooltipBuilder(this::constructionTooltip)
                .tooltipAutoUpdate(true));
        page.child(
            action(manager, "power", player -> state.setEnabled(player, !state.isEnabled()))
                .overlay(
                    IKey.dynamic(() -> tr(enabled.getValue() ? "stop" : "start"))
                        .color(CriticalGuiStyle.TEXT_CONTROL))
                .pos(351, 150)
                .size(112, 20)
                .setEnabledIf(w -> permitted.getValue() && (enabled.getValue() || loomValid.getValue())));
        ListWidget<IWidget, ?> feedback = new ListWidget<>().name("critical_action_feedback")
            .pos(0, 176)
            .size(463, 26)
            .padding(4)
            .background(CriticalGuiStyle.CARD)
            .scrollDirection(new VerticalScrollData().cancelScrollEdge(false));
        feedback.child(
            CriticalGuiStyle.text(
                IKey.dynamic(
                    () -> actionName.getValue()
                        .isEmpty()
                            ? tr("ui.supply_short")
                            : tr(actionName.getValue()) + " | "
                                + tr(actionAccepted.getValue() ? "action.accepted" : "action.refused")))
                .color(
                    () -> actionName.getValue()
                        .isEmpty()
                            ? CriticalGuiStyle.TEXT_MUTED
                            : actionAccepted.getValue() ? CriticalGuiStyle.STATE_ONLINE
                                : CriticalGuiStyle.STATE_OFFLINE)
                .fullWidth()
                .tooltipBuilder(
                    t -> t.add(
                        actionDetail.getValue()
                            .equals(key("action.permission")) ? tr("action.permission") : actionDetail.getValue()))
                .tooltipAutoUpdate(true));
        page.child(feedback);
        return page;
    }

    private static double ratio(long value, long maximum) {
        return maximum <= 0 ? 0 : Math.max(0, Math.min(1, (double) value / maximum));
    }

    private static String time(int elapsed, int duration) {
        return String.format(java.util.Locale.ROOT, "%.1f / %.1f s", elapsed / 20D, duration / 20D);
    }

    private static IWidget metric(String label, java.util.function.Supplier<String> value,
        java.util.function.DoubleSupplier progress, int x, int y) {
        return new ParentWidget<>().pos(x, y)
            .size(228, 44)
            .background(CriticalGuiStyle.CARD)
            .child(
                IKey.lang(key(label))
                    .color(CriticalGuiStyle.TEXT_MUTED)
                    .asWidget()
                    .pos(8, 5)
                    .size(212, 10))
            .child(
                textViewport("critical_metric_" + label, value, 212, 14).pos(8, 16)
                    .tooltipBuilder(t -> t.add(value.get()))
                    .tooltipAutoUpdate(true))
            .child(
                new ParentWidget<>().background(new CriticalProgressBar(progress))
                    .pos(8, 32)
                    .size(212, 7));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager manager, ModularPanel parent) {
        ListWidget<IWidget, ?> list = new ListWidget<>();
        list.child(
            IKey.dynamic(() -> "EU " + energy.getValue() + " / " + capacity.getValue())
                .color(CriticalGuiStyle.TEXT_BODY)
                .asWidget()
                .fullWidth()
                .marginBottom(3));
        list.child(
            IKey.dynamic(
                () -> tr("construction") + " " + stage.getValue() + " · " + job.getValue() + " / " + total.getValue())
                .color(CriticalGuiStyle.TEXT_BODY)
                .asWidget()
                .fullWidth()
                .marginBottom(3));
        list.child(
            IKey.dynamic(() -> tr("batch") + " " + batch.getValue() + " / " + duration.getValue() + " t")
                .color(CriticalGuiStyle.TEXT_BODY)
                .asWidget()
                .fullWidth()
                .marginBottom(3));
        list.child(
            IKey.dynamic(() -> euPerTick.getValue() + " EU/t")
                .color(CriticalGuiStyle.TEXT_BODY)
                .asWidget()
                .fullWidth()
                .marginBottom(3));
        list.child(
            IKey.dynamic(() -> tr("construction_power") + " " + configuration().tier.constructionVoltage() + " EU/t")
                .color(CriticalGuiStyle.TEXT_BODY)
                .asWidget()
                .fullWidth());
        return list;
    }

    private boolean editable() {
        return permitted.getValue() && status.getValue() == 0;
    }

    private static void constructionLine(ListWidget<IWidget, ?> list, java.util.function.Supplier<String> text) {
        constructionLine(list, text, () -> CriticalGuiStyle.TEXT_BODY);
    }

    private static void constructionLine(ListWidget<IWidget, ?> list, java.util.function.Supplier<String> text,
        java.util.function.IntSupplier color) {
        list.child(
            CriticalGuiStyle.text(IKey.dynamic(text))
                .color(color)
                .fullWidth()
                .marginBottom(3)
                .setEnabledIf(
                    w -> !text.get()
                        .isEmpty()));
    }

    private boolean change(EntityPlayer player, int field, int delta) {
        if (state.getState() != 0 || (field == 1 && !multiblock.isKindSelector())) return false;
        CriticalConfiguration cfg = state.getConfiguration();
        CriticalTier nextTier = field == 0 ? CriticalTier.byId((cfg.tier.ordinal() + 1) % CriticalTier.values().length)
            : cfg.tier;
        CriticalMachineKind nextKind = field == 1 ? cfg.kind.nextReleased() : cfg.kind;
        return state.configure(
            player,
            nextTier,
            nextKind,
            cfg.parallel + (field == 2 ? delta : 0),
            cfg.speed + (field == 3 ? delta : 0),
            cfg.economy + (field == 4 ? delta : 0));
    }

    private ButtonWidget<?> action(PanelSyncManager manager, String name,
        java.util.function.Predicate<EntityPlayer> command) {
        return new ButtonWidget<>().name("critical_action_" + name)
            .background(CriticalGuiStyle.BUTTON)
            .hoverBackground(CriticalGuiStyle.HOVER)
            .syncHandler(interaction(manager, name, command))
            .overlay(
                IKey.lang(key(name))
                    .color(CriticalGuiStyle.TEXT_CONTROL));
    }

    private InteractionSyncHandler interaction(PanelSyncManager manager, String name,
        java.util.function.Predicate<EntityPlayer> command) {
        InteractionSyncHandler handler = new InteractionSyncHandler().setOnMousePressed(data -> {
            if (!baseMetaTileEntity.isServerSide() || data.mouseButton != 0) return;
            EntityPlayer player = manager.getPlayer();
            int separator = name.indexOf('.');
            lastAction = separator < 0 ? name : name.substring(0, separator);
            if (!state.canUse(player)) {
                lastActionAccepted = false;
                lastActionDetail = "gtsr.critical.action.permission";
                player.addChatMessage(new ChatComponentTranslation(key("action.permission")));
                return;
            }
            lastActionAccepted = command.test(player);
            lastActionDetail = state.getStatus();
            if (!lastActionAccepted) player.addChatMessage(
                new ChatComponentTranslation(
                    key("action.rejected"),
                    new ChatComponentTranslation(key(lastAction)),
                    lastActionDetail));
        });
        manager.syncValue("critical.action." + name, handler);
        return handler;
    }

    private IWidget inventoryPage(PanelSyncManager manager) {
        ParentWidget<?> page = new ParentWidget<>().name("critical_page_inventory")
            .size(463, 202);
        StringSyncValue[] summaries = { inputOverview, outputOverview };
        for (int side = 0; side < 2; side++) {
            final int index = side;
            ParentWidget<?> card = new ParentWidget<>().pos(side * 235, 0)
                .size(228, 166)
                .background(CriticalGuiStyle.CARD);
            card.child(
                IKey.lang(key(side == 0 ? "input" : "output"))
                    .color(CriticalGuiStyle.TEXT_ACCENT)
                    .asWidget()
                    .pos(8, 8)
                    .size(212, 14));
            ListWidget<IWidget, ?> contents = new ListWidget<>().name("critical_inventory_" + side)
                .pos(8, 28)
                .size(212, 130)
                .scrollDirection(new VerticalScrollData().cancelScrollEdge(false));
            constructionLine(contents, () -> summaries[index].getValue());
            card.child(contents);
            page.child(card);
        }
        page.child(
            textViewport("critical_inventory_hint", () -> tr("hatch_overview_hint"), 447, 24)
                .background(CriticalGuiStyle.CARD)
                .pos(8, 172));
        return page;
    }

    private static ListWidget<IWidget, ?> textViewport(String name, java.util.function.Supplier<String> text, int width,
        int height) {
        return textViewport(name, text, width, height, () -> CriticalGuiStyle.TEXT_BODY);
    }

    private static ListWidget<IWidget, ?> textViewport(String name, java.util.function.Supplier<String> text, int width,
        int height, java.util.function.IntSupplier color) {
        return new ListWidget<>().name(name)
            .size(width, height)
            .scrollDirection(new VerticalScrollData().cancelScrollEdge(false))
            .child(
                CriticalGuiStyle.text(IKey.dynamic(text))
                    .color(color)
                    .fullWidth());
    }

    private CriticalConfiguration configuration() {
        // Independent S2C values may arrive in different ticks; clamp the display snapshot.
        int a = Math.max(0, Math.min(8, parallel.getValue()));
        int b = Math.max(0, Math.min(8 - a, speed.getValue()));
        int c = Math.max(0, Math.min(8 - a - b, economy.getValue()));
        return new CriticalConfiguration(
            CriticalTier.byId(tier.getValue()),
            CriticalMachineKind.byId(kind.getValue()),
            a,
            b,
            c);
    }

    private void constructionTooltip(com.cleanroommc.modularui.screen.RichTooltip tooltip) {
        CriticalConfiguration cfg = configuration();
        int currentStage = Math.max(0, Math.min(4, stage.getValue()));
        tooltip.add(tr("construction") + " " + currentStage);
        for (ItemStack stack : constructionCosts.getValue()) {
            tooltip.add(stack.getDisplayName() + " × " + stack.stackSize);
        }
        tooltip.add(tr("construction_power") + " " + cfg.tier.constructionVoltage() + " EU/t");
        tooltip.add(tr("cost_hint"));
    }

    private void operationTooltip(com.cleanroommc.modularui.screen.RichTooltip tooltip) {
        CriticalMachineKind current = configuration().kind;
        if (!current.isReleased()) {
            tooltip.add(tr("tooltip.frozen"));
            return;
        }
        for (String section : new String[] { "input", "cycle", "parallel", "energy", "efficiency", "output" }) {
            tooltip.add(operationLine(section));
        }
        if (current == CriticalMachineKind.BATTERY || current == CriticalMachineKind.TURBINE) {
            tooltip.add(tr("operation.trend"));
            tooltip.add(tr("operation.idle"));
        }
        if (current == CriticalMachineKind.SOLAR) tooltip.add(tr("operation.solar.night"));
        tooltip.add(tr("functional.decorative"));
    }

    private String operationLine(String section) {
        CriticalMachineKind current = configuration().kind;
        if (!current.isReleased()) return "input".equals(section) ? tr("tooltip.frozen") : "";
        return tr("operation." + current.key + "." + section);
    }

    private static String key(String suffix) {
        return "gtsr.critical." + suffix;
    }

    private static String tr(String suffix) {
        return StatCollector.translateToLocal(key(suffix));
    }
}
