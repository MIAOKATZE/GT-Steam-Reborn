package com.miaokatze.gtsr.common.gui;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiPalette;
import com.miaokatze.gtsr.common.critical.CompoundRecipe;
import com.miaokatze.gtsr.common.critical.CriticalChemicalRecipes;
import com.miaokatze.gtsr.common.critical.CriticalConfiguration;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.critical.CriticalStellarPolicy;
import com.miaokatze.gtsr.common.critical.CriticalTier;
import com.miaokatze.gtsr.common.gui.mui2.Mui2ServerActions;
import com.miaokatze.gtsr.common.gui.mui2.TerminalViewport;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import com.miaokatze.gtsr.common.network.ServerPacketAuthority;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.modularui2.widget.GTProgressWidget;

/** Handheld terminal only; GT's native container lifecycle owns inventory and NEI integration. */
public final class MTECriticalMachineTerminalGui {

    private final MTECriticalMachine multiblock;
    private final CriticalMachineState state;
    private EntityPlayer viewer;
    private final AtomicBoolean actionPending = new AtomicBoolean();
    private BooleanSyncValue permitted;
    private BooleanSyncValue loomController;
    private StringSyncValue actionName, actionDetail;
    private BooleanSyncValue actionAccepted;
    private String lastAction = "", lastActionDetail = "";
    private boolean lastActionAccepted;
    private int selectedPage;
    private String armedAction = "";
    private CriticalConstructionView constructionView;
    private GenericSyncValue<CriticalBusinessSnapshot, ?> business;

    public MTECriticalMachineTerminalGui(MTECriticalMachine multiblock) {
        this.multiblock = multiblock;
        state = multiblock == null ? null : multiblock.getCriticalState();
    }

    public ModularPanel build(PosGuiData data, PanelSyncManager manager, UISettings settings) {
        if (!manager.isClient() && multiblock == null)
            throw new IllegalStateException("Missing server Critical target");
        // Sync-value constructors evaluate their getters before the modular container is bound.
        viewer = data.getPlayer();
        registerSyncValues(manager);
        ModularPanel panel = getBasePanel(data, manager, settings);
        return panel.child(createMainColumn(panel, manager));
    }

    protected ModularPanel getBasePanel(PosGuiData data, PanelSyncManager manager, UISettings settings) {
        // The industrial terminal already has its title inside statusHeader. GT's external
        // title tab extends above the panel and is clipped on a 640 x 360 logical canvas.
        return ModularPanel.defaultPanel("gtsr_critical_terminal", getBasePanelWidth(), getBasePanelHeight())
            .background(CriticalGuiStyle.PANEL);
    }

    protected int getBasePanelWidth() {
        return 475;
    }

    protected int getBasePanelHeight() {
        return 350;
    }

    protected void registerSyncValues(PanelSyncManager manager) {
        // One immutable S2C business sample; all mutations use guarded server-side actions.
        permitted = new BooleanSyncValue(() -> !manager.isClient() && state.canUse(viewer));
        loomController = new BooleanSyncValue(() -> !manager.isClient() && multiblock.isKindSelector());
        actionName = new StringSyncValue(() -> lastAction);
        actionDetail = new StringSyncValue(() -> lastActionDetail);
        actionAccepted = new BooleanSyncValue(() -> lastActionAccepted);
        manager.syncValue("critical.permitted", permitted);
        manager.syncValue("critical.loomController", loomController);
        manager.syncValue("critical.actionName", actionName);
        manager.syncValue("critical.actionDetail", actionDetail);
        manager.syncValue("critical.actionAccepted", actionAccepted);
        business = CriticalBusinessSnapshot.register(manager, state, multiblock);
        constructionView = new CriticalConstructionView(business);
    }

    public Flow createMainColumn(ModularPanel panel, PanelSyncManager manager) {
        // Every page and handler exists on both sides; navigation only changes local presentation.
        PagedWidget<?> pages = new PagedWidget<>().name("critical_pages")
            .size(463, 252)
            .onPageChange(page -> {
                selectedPage = page;
                armedAction = "";
            });
        pages.addPage(
            new ParentWidget<>().name("critical_page_overview")
                .size(463, 252)
                .child(
                    constructionView.createTerminalOverview()
                        .setEnabledIf(w -> loomController.getValue()))
                .child(controlPage(manager).setEnabledIf(w -> !loomController.getValue())));
        pages.addPage(
            new ParentWidget<>().name("critical_page_details")
                .size(463, 252)
                .child(
                    constructionView.createTerminalMaterials()
                        .setEnabledIf(w -> loomController.getValue()))
                .child(runtimeDetailsPage().setEnabledIf(w -> !loomController.getValue())));
        pages.addPage(engineeringPage(manager));
        pages.addPage(inventoryPage(manager));
        ParentWidget<?> tabs = new ParentWidget<>().name("critical_navigation")
            .size(463, 20);
        String[] loomTabs = { "ui.terminal.tab.progress", "ui.terminal.tab.materials", "ui.terminal.tab.engineering",
            "tab.inventory" };
        for (int i = 0; i < loomTabs.length; i++) {
            tabs.child(
                tab(pages, "critical_tab_" + i, loomTabs[i], i, i * 117, 112)
                    .setEnabledIf(w -> loomController.getValue()));
        }
        String[] mainTabs = { "ui.terminal.tab.overview", "ui.terminal.tab.runtime", "tab.inventory" };
        int[] mainPages = { 0, 1, 3 };
        for (int i = 0; i < mainTabs.length; i++) {
            tabs.child(
                tab(pages, "critical_main_tab_" + mainPages[i], mainTabs[i], mainPages[i], i * 156, 151)
                    .setEnabledIf(w -> !loomController.getValue()));
        }
        Flow content = Flow.column()
            .pos(0, 0)
            .size(475, 350)
            .padding(6)
            .child(statusHeader(manager))
            .child(tabs)
            .child(pages)
            .child(actionBar(manager));
        return Flow.column()
            .child(
                TerminalViewport.wrapExact("critical_terminal_viewport", content, 475, 350)
                    .sizeRel(1f));
    }

    private ButtonWidget<?> tab(PagedWidget<?> pages, String name, String label, int page, int x, int width) {
        return new ButtonWidget<>().name(name)
            .pos(x, 0)
            .size(width, 18)
            .background(
                new DynamicDrawable(() -> selectedPage == page ? CriticalGuiStyle.SELECTED_TAB : CriticalGuiStyle.TAB))
            .hoverBackground(CriticalGuiStyle.HOVER)
            .overlay(
                CriticalGuiStyle.fitted(
                    () -> tr(label),
                    () -> selectedPage == page ? CriticalGuiStyle.TEXT_ACCENT : CriticalGuiStyle.TEXT_BODY,
                    Alignment.CENTER))
            .tooltipBuilder(t -> t.add(tr(label)))
            .onMousePressed(button -> {
                if (button != 0) return false;
                pages.setPage(page);
                return true;
            });
    }

    private IWidget statusHeader(PanelSyncManager manager) {
        ParentWidget<?> header = new ParentWidget<>().name("critical_status_header")
            .size(463, 32)
            .background(CriticalGuiStyle.CARD);
        header.child(
            CriticalGuiStyle.label(this::terminalTitle, () -> CriticalGuiStyle.TEXT_TITLE, Alignment.CenterLeft)
                .name("critical_terminal_title")
                .pos(5, 2)
                .size(365, 14)
                .tooltipBuilder(t -> t.add(terminalTitle()))
                .tooltipAutoUpdate(true));
        header.child(
            CriticalGuiStyle.label(this::headerStatus, this::statusColor, Alignment.CenterLeft)
                .name("critical_status_detail")
                .pos(5, 16)
                .size(450, 12)
                .tooltipBuilder(t -> t.add(headerStatus()))
                .tooltipAutoUpdate(true));
        header.child(
            CriticalGuiStyle
                .label(
                    () -> tr(permitted.getValue() ? "ui.terminal.access.edit" : "ui.terminal.access.view"),
                    () -> permitted.getValue() ? CriticalGuiStyle.TEXT_MUTED : CriticalGuiStyle.STATE_OFFLINE,
                    Alignment.CenterRight)
                .name("critical_access_state")
                .pos(371, 3)
                .size(84, 12)
                .tooltipBuilder(
                    t -> t.add(tr(permitted.getValue() ? "ui.terminal.access.edit_hint" : "action.permission")))
                .tooltipAutoUpdate(true));
        return header;
    }

    private boolean powerEnabled() {
        return loomController.getValue() ? business.getValue().construction.enabled()
            : business.getValue()
                .productionEnabled();
    }

    private String powerLabel() {
        return tr(
            (loomController.getValue() ? "ui.terminal.power.construction." : "ui.terminal.power.production.")
                + powerEnabled());
    }

    private String headerStatus() {
        return tr(loomController.getValue() ? "ui.engineering_status" : "ui.running_status") + ": "
            + (loomController.getValue() ? business.getValue()
                .detail()
                : tr(
                    business.getValue()
                        .runtimeStatusKey()));
    }

    private String terminalTitle() {
        // A loom's selected target defaults to SUN; it is not the identity of the opened controller.
        return loomController.getValue()
            ? StatCollector.translateToLocal("gt.blockmachines.gtsr.critical.loom.name") + " · "
                + tr("ui.target")
                + ": "
                + tr("machine." + configuration().kind.key)
            : tr("machine." + configuration().kind.key);
    }

    private int statusColor() {
        if (!loomController.getValue()) {
            String status = business.getValue()
                .runtimeStatusKey();
            if ("runtime.working".equals(status)) return CriticalGuiStyle.STATE_ONLINE;
            if ("runtime.ready".equals(status) || "runtime.power_off".equals(status)) return GtsrGuiPalette.STATE_IDLE;
            return CriticalGuiStyle.TEXT_ACCENT;
        }
        int cause = business.getValue().construction.reason();
        if (cause != 0 && cause != 7) return CriticalGuiStyle.STATE_OFFLINE;
        return business.getValue().construction.status() == 2 ? CriticalGuiStyle.STATE_ONLINE
            : CriticalGuiStyle.TEXT_ACCENT;
    }

    private ParentWidget<?> engineeringPage(PanelSyncManager manager) {
        ParentWidget<?> page = new ParentWidget<>().name("critical_page_engineering")
            .size(463, 252);
        ParentWidget<?> target = new ParentWidget<>().name("critical_engineering_target")
            .size(463, 56)
            .background(CriticalGuiStyle.CARD);
        target.child(
            CriticalGuiStyle.label(() -> tr("ui.target"), () -> CriticalGuiStyle.TEXT_MUTED, Alignment.CenterLeft)
                .pos(5, 5)
                .size(345, 12));
        target.child(
            CriticalGuiStyle
                .label(
                    () -> tr("machine." + configuration().kind.key),
                    () -> CriticalGuiStyle.TEXT_TITLE,
                    Alignment.CenterLeft)
                .name("critical_target_name")
                .pos(5, 22)
                .size(345, 22)
                .tooltipBuilder(t -> t.add(tr("machine." + configuration().kind.key)))
                .tooltipAutoUpdate(true));
        target.child(
            action(manager, "kind", player -> change(player, 1, 1)).pos(355, 21)
                .size(100, 20)
                .tooltipBuilder(t -> t.add(tr("configuration_hint"))));
        page.child(target);
        page.child(
            new ParentWidget<>().name("critical_engineering_recovery_card")
                .pos(0, 62)
                .size(463, 84)
                .background(CriticalGuiStyle.CARD)
                .child(
                    CriticalGuiStyle
                        .label(() -> tr("ui.next_action"), () -> CriticalGuiStyle.TEXT_ACCENT, Alignment.CenterLeft)
                        .pos(5, 4)
                        .size(450, 14))
                .child(
                    textViewport(
                        "critical_engineering_status",
                        () -> business.getValue()
                            .detail(),
                        443,
                        24,
                        this::statusColor).pos(10, 23))
                .child(
                    textViewport(
                        "critical_engineering_recovery",
                        () -> business.getValue().construction.status() == 2 ? tr("ui.engineering.complete")
                            : tr(business.getValue().construction.recoveryKey()),
                        443,
                        26).pos(10, 49)));
        page.child(
            new ParentWidget<>().name("critical_engineering_help_card")
                .pos(0, 152)
                .size(463, 100)
                .background(CriticalGuiStyle.CARD)
                .child(
                    CriticalGuiStyle
                        .label(
                            () -> tr("ui.terminal.engineering.connections"),
                            () -> CriticalGuiStyle.TEXT_MUTED,
                            Alignment.CenterLeft)
                        .pos(5, 4)
                        .size(450, 14))
                .child(
                    textViewport("critical_engineering_guidance", () -> tr("ui.engineering.guide"), 443, 64)
                        .pos(10, 25)));
        return page;
    }

    private ParentWidget<?> controlPage(PanelSyncManager manager) {
        ParentWidget<?> page = new ParentWidget<>().name("critical_page_control")
            .size(463, 252);
        page.child(metric("ui.energy", this::primaryMetricValue, () -> 0, 0, 0));
        page.child(
            metric(
                "ui.secondary",
                this::secondaryMetricValue,
                () -> ratio(
                    business.getValue()
                        .batchTicks(),
                    business.getValue()
                        .batchDuration()),
                235,
                0));
        ParentWidget<?> process = new ParentWidget<>().name("critical_runtime_process_card")
            .pos(0, 58)
            .size(463, 72)
            .background(CriticalGuiStyle.CARD);
        process.child(
            CriticalGuiStyle.label(this::sampleScope, () -> CriticalGuiStyle.TEXT_MUTED, Alignment.CenterLeft)
                .name("critical_runtime_sample_scope")
                .pos(5, 3)
                .size(450, 14)
                .tooltipBuilder(t -> t.add(sampleScope()))
                .tooltipAutoUpdate(true));
        process.child(
            new ParentWidget<>().name("critical_runtime_process")
                .pos(10, 27)
                .size(190, 37)
                .background(new CriticalRuntimeDiagram(business::getValue))
                .tooltipBuilder(this::operationTooltip)
                .tooltipAutoUpdate(true));
        process.child(
            textViewport("critical_chemical_flow", this::chemicalFlowSummary, 164, 37).pos(31, 27)
                .setEnabledIf(w -> configuration().kind == CriticalMachineKind.BATTERY));
        process.child(
            textViewport(
                "critical_runtime_priority",
                () -> configuration().kind == CriticalMachineKind.BATTERY ? chemicalProgressSummary()
                    : tr(
                        business.getValue()
                            .runtimeStatusKey())
                        + "\n"
                        + tr("batch")
                        + ": "
                        + time(
                            business.getValue()
                                .batchTicks(),
                            business.getValue()
                                .batchDuration()),
                218,
                37,
                this::statusColor).pos(210, 27));
        GTProgressWidget batchProgress = new GTProgressWidget() {

            @Override
            public Interactable.Result onMousePressed(int button) {
                gregtech.api.recipe.RecipeMap<?> map = catalogue();
                if (map == null) return Interactable.Result.IGNORE;
                neiTransferRect(map);
                return super.onMousePressed(button);
            }
        };
        process.child(
            batchProgress.texture(GTGuiTextures.PROGRESSBAR_ARROW_STANDARD, 20)
                .progress(
                    () -> ratio(
                        business.getValue()
                            .batchTicks(),
                        business.getValue()
                            .batchDuration()))
                .pos(435, 43)
                .size(20, 16));
        page.child(process);
        page.child(
            new ParentWidget<>().name("critical_runtime_guidance_card")
                .pos(0, 136)
                .size(463, 84)
                .background(CriticalGuiStyle.CARD)
                .child(
                    CriticalGuiStyle
                        .label(() -> tr("ui.next_action"), () -> CriticalGuiStyle.TEXT_ACCENT, Alignment.CenterLeft)
                        .pos(5, 3)
                        .size(450, 14))
                .child(textViewport("critical_runtime_guidance", this::runtimeGuidance, 443, 58).pos(10, 21)));
        page.child(
            CriticalGuiStyle.label(this::chemicalRecipeTitle, () -> CriticalGuiStyle.TEXT_BODY, Alignment.CenterLeft)
                .name("critical_chemical_recipe")
                .pos(5, 225)
                .size(450, 20)
                .setEnabledIf(w -> configuration().kind == CriticalMachineKind.BATTERY)
                .tooltipBuilder(t -> t.add(chemicalRecipeTitle()))
                .tooltipAutoUpdate(true));
        page.child(
            CriticalGuiStyle
                .label(() -> tr("ui.terminal.runtime_hint"), () -> CriticalGuiStyle.TEXT_MUTED, Alignment.CenterLeft)
                .name("critical_runtime_hint")
                .pos(5, 225)
                .size(450, 20)
                .setEnabledIf(w -> configuration().kind != CriticalMachineKind.BATTERY));
        return page;
    }

    private ParentWidget<?> runtimeDetailsPage() {
        ParentWidget<?> page = new ParentWidget<>().name("critical_page_runtime_details")
            .size(463, 252)
            .background(CriticalGuiStyle.CARD);
        page.child(
            CriticalGuiStyle
                .label(() -> tr("ui.details.runtime"), () -> CriticalGuiStyle.TEXT_ACCENT, Alignment.CenterLeft)
                .pos(5, 3)
                .size(450, 16));
        ListWidget<IWidget, ?> rows = new ListWidget<>().name("critical_status_summary")
            .pos(8, 24)
            .size(447, 220)
            .padding(5)
            .scrollDirection(CriticalGuiStyle.scroll())
            .overlay(CriticalGuiStyle.SCROLL_EDGES);
        constructionLine(
            rows,
            () -> configuration().kind.isReleased() ? "" : tr("tooltip.frozen"),
            () -> CriticalGuiStyle.TEXT_ACCENT);
        for (int i = 0; i < 40; i++) {
            final int row = i;
            constructionLine(rows, () -> runtimeLine(row));
        }
        page.child(rows);
        return page;
    }

    private ParentWidget<?> actionBar(PanelSyncManager manager) {
        ParentWidget<?> bar = new ParentWidget<>().name("critical_action_bar")
            .size(463, 34)
            .background(CriticalGuiStyle.CARD);
        bar.child(action(manager, "power", player -> {
            if (multiblock.isKindSelector()) return state.setEnabled(player, !state.isConstructionEnabled());
            return state.setProductionEnabled(player, !state.isProductionEnabled());
        }).overlay(
            CriticalGuiStyle.fitted(
                this::powerLabel,
                () -> actionAvailable("power")
                    ? powerEnabled() ? CriticalGuiStyle.STATE_ONLINE : CriticalGuiStyle.TEXT_BODY
                    : CriticalGuiStyle.TEXT_MUTED,
                Alignment.CENTER))
            .pos(4, 2)
            .size(101, 18)
            .tooltipBuilder(t -> {
                t.add(
                    tr(
                        loomController.getValue() ? "ui.power.construction_hint"
                            : configuration().kind == CriticalMachineKind.SOLAR ? "ui.power.solar_hint"
                                : "ui.power.production_hint"));
                if (!loomController.getValue() && configuration().kind == CriticalMachineKind.SUN)
                    t.add(tr("ui.sun.power_hint"));
            }));
        bar.child(
            action(manager, "build", player -> state.startConstruction(player)).pos(111, 2)
                .size(105, 18)
                .setEnabledIf(w -> loomController.getValue())
                .tooltipBuilder(this::constructionTooltip)
                .tooltipAutoUpdate(true));
        bar.child(
            action(manager, "repair", player -> state.repairStructure(player)).pos(222, 2)
                .size(105, 18)
                .setEnabledIf(w -> loomController.getValue())
                .tooltipBuilder(this::constructionTooltip)
                .tooltipAutoUpdate(true));
        bar.child(
            action(manager, "dismantle", player -> state.startDismantle(player)).pos(333, 2)
                .size(126, 18)
                .setEnabledIf(w -> loomController.getValue()));
        bar.child(
            action(manager, "ui.solar.change_route", player -> state.selectSolarSaltMode(player))
                .overlay(
                    CriticalGuiStyle.fitted(
                        () -> tr("ui.solar.route." + runtimeValue("solar.route")),
                        () -> actionAvailable("ui.solar.change_route") ? CriticalGuiStyle.TEXT_CONTROL
                            : CriticalGuiStyle.TEXT_MUTED,
                        Alignment.CENTER))
                .pos(111, 2)
                .size(137, 18)
                .setEnabledIf(w -> !loomController.getValue() && configuration().kind == CriticalMachineKind.SOLAR)
                .tooltipBuilder(t -> t.add(tr("ui.solar.route_hint"))));
        bar.child(
            action(manager, "ui.change_mode", player -> state.selectSteamMode(player))
                .overlay(
                    CriticalGuiStyle.fitted(
                        () -> tr(
                            "steam_mode." + business.getValue()
                                .steamMode()),
                        () -> actionAvailable("ui.change_mode") ? CriticalGuiStyle.TEXT_CONTROL
                            : CriticalGuiStyle.TEXT_MUTED,
                        Alignment.CENTER))
                .pos(254, 2)
                .size(101, 18)
                .setEnabledIf(w -> !loomController.getValue() && configuration().kind == CriticalMachineKind.SOLAR)
                .tooltipBuilder(
                    t -> t.add(
                        tr(
                            "steam_mode." + business.getValue()
                                .steamMode())))
                .tooltipAutoUpdate(true));
        bar.child(
            action(manager, "ui.sun.production_pause", player -> state.selectStellarProductionPaused(player))
                .overlay(
                    CriticalGuiStyle.fitted(
                        () -> tr("ui.sun.production_pause." + runtimeValue("sun.production_paused")),
                        () -> actionAvailable("ui.sun.production_pause") ? CriticalGuiStyle.TEXT_CONTROL
                            : CriticalGuiStyle.TEXT_MUTED,
                        Alignment.CENTER))
                .pos(111, 2)
                .size(137, 18)
                .setEnabledIf(w -> !loomController.getValue() && configuration().kind == CriticalMachineKind.SUN)
                .tooltipBuilder(t -> t.add(tr("ui.sun.power_hint"))));
        bar.child(
            action(manager, "ui.sun.extinguish", player -> state.shutdownStellar(player)).pos(254, 2)
                .size(101, 18)
                .setEnabledIf(w -> !loomController.getValue() && configuration().kind == CriticalMachineKind.SUN)
                .tooltipBuilder(t -> t.add(tr("ui.sun.extinguish_hint"))));
        bar.child(
            new ButtonWidget<>().name("critical_recipe_preview")
                .pos(361, 2)
                .size(98, 18)
                .background(CriticalGuiStyle.BUTTON)
                .hoverBackground(CriticalGuiStyle.HOVER)
                .overlay(
                    CriticalGuiStyle.fitted(
                        () -> tr("ui.terminal.action.recipe"),
                        () -> CriticalGuiStyle.TEXT_CONTROL,
                        Alignment.CENTER))
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    armedAction = "";
                    openRecipeCatalogue();
                    return true;
                })
                .setEnabledIf(
                    w -> !loomController.getValue() && !business.getValue()
                        .canCancelLegacyStellarBatch())
                .tooltipBuilder(this::operationTooltip)
                .tooltipAutoUpdate(true));
        bar.child(
            action(manager, "ui.sun.cancel_legacy", player -> state.cancelLegacyStellarBatch(player)).pos(361, 2)
                .size(98, 18)
                .setEnabledIf(
                    w -> !loomController.getValue() && business.getValue()
                        .canCancelLegacyStellarBatch())
                .tooltipBuilder(t -> t.add(tr("ui.sun.cancel_legacy_hint"))));
        bar.child(
            CriticalGuiStyle.label(this::actionFeedback, this::feedbackColor, Alignment.CenterLeft)
                .name("critical_action_feedback")
                .pos(4, 21)
                .size(455, 11)
                .tooltipBuilder(t -> {
                    t.add(actionFeedback());
                    if (!actionName.getValue()
                        .isEmpty()) t.add(tr(actionDetail.getValue()));
                })
                .tooltipAutoUpdate(true));
        return bar;
    }

    private String actionFeedback() {
        if (!armedAction.isEmpty()) return tr("ui.terminal.confirm_hint") + " ? " + tr(armedAction);
        if (actionName.getValue()
            .isEmpty()) return tr("ui.terminal.feedback.idle");
        return tr(actionName.getValue()) + " ? " + tr(actionAccepted.getValue() ? "action.accepted" : "action.refused");
    }

    private int feedbackColor() {
        return !armedAction.isEmpty() ? CriticalGuiStyle.STATE_OFFLINE
            : actionName.getValue()
                .isEmpty() ? CriticalGuiStyle.TEXT_MUTED
                    : actionAccepted.getValue() ? CriticalGuiStyle.STATE_ONLINE : CriticalGuiStyle.STATE_OFFLINE;
    }

    private String primaryMetricLabel() {
        return tr(switch (configuration().kind) {
            case BATTERY -> "ui.runtime.chemical.heat";
            case TURBINE -> "ui.runtime.steam.heat";
            case SOLAR -> "ui.runtime.solar.dimension";
            case SUN -> "ui.runtime.sun.reserve";
            default -> "ui.energy";
        });
    }

    private String primaryMetricValue() {
        return switch (configuration().kind) {
            case BATTERY -> runtimeValue("chemical.heat");
            case TURBINE -> runtimeValue("steam.heat");
            case SOLAR -> runtimeValue("solar.dimension");
            case SUN -> runtimeValue("sun.sustain_power") + "\n" + runtimeValue("sun.sustain_fuel") + " He-3";
            default -> business.getValue()
                .energy() + " / "
                + business.getValue()
                    .capacity()
                + " EU";
        };
    }

    private String sampleScope() {
        if (configuration().kind == CriticalMachineKind.BATTERY)
            return tr("ui.chemical.sample_scope." + runtimeValue("sample_scope"));
        return configuration().kind == CriticalMachineKind.SUN ? tr("ui.sun.phase." + runtimeValue("sun.phase"))
            : tr("ui.sample_scope." + runtimeValue("sample_scope"));
    }

    private String secondaryMetricLabel() {
        return tr(switch (configuration().kind) {
            case BATTERY -> "ui.runtime.chemical.efficiency";
            case TURBINE -> "ui.runtime.steam.multiplier";
            case SOLAR -> "ui.runtime.solar.route";
            case SUN -> "ui.runtime.sun.ignition_paid";
            default -> "batch";
        });
    }

    private String secondaryMetricValue() {
        return switch (configuration().kind) {
            case BATTERY -> runtimeValue("chemical.efficiency");
            case TURBINE -> runtimeValue("steam.multiplier");
            case SOLAR -> tr("ui.solar.route." + runtimeValue("solar.route"));
            case SUN -> runtimeValue("sun.ignition_paid").replace(" / ", " EU\n/ ");
            default -> time(
                business.getValue()
                    .batchTicks(),
                business.getValue()
                    .batchDuration());
        };
    }

    private String chemicalProgressSummary() {
        CriticalBusinessSnapshot sample = business.getValue();
        return tr("ui.chemical.state." + runtimeValue(sample, "chemical.state")) + "\n"
            + tr("ui.chemical.sample_scope." + runtimeValue(sample, "sample_scope"))
            + "\n"
            + tr("ui.chemical.progress")
            + ": "
            + time(sample.batchTicks(), sample.batchDuration());
    }

    private String chemicalRecipeTitle() {
        CriticalBusinessSnapshot sample = business.getValue();
        String status = runtimeValue(sample, "chemical.state");
        return "current".equals(status) ? chemicalRecipeName(runtimeValue(sample, "chemical.recipe"))
            : tr("ui.chemical.state." + ("legacy".equals(status) ? "legacy" : "awaiting"));
    }

    private static String chemicalRecipeName(String id) {
        if (id.isEmpty()) return tr("ui.chemical.state.awaiting");
        CompoundRecipe recipe = CriticalChemicalRecipes.byId(id);
        if (recipe == null) return tr("ui.chemical.recipe.unknown");
        java.util.StringJoiner names = new java.util.StringJoiner(" + ");
        for (CompoundRecipe.Component component : recipe.components) names.add(component.fuel.fluid.getLocalizedName());
        return names.toString();
    }

    private String chemicalFlowSummary() {
        CriticalBusinessSnapshot sample = business.getValue();
        return tr("ui.chemical.efficiency_scale") + "\n"
            + tr("ui.runtime.chemical.flow")
            + ": "
            + runtimeValue(sample, "chemical.flow")
            + "\n"
            + tr("ui.runtime.chemical.litres")
            + ": "
            + runtimeValue(sample, "chemical.litres");
    }

    private String runtimeValue(String suffix) {
        return runtimeValue(business.getValue(), suffix);
    }

    private static String runtimeValue(CriticalBusinessSnapshot sample, String suffix) {
        for (int i = 0; i < sample.runtimeCount(); i++)
            if (("ui.runtime." + suffix).equals(sample.runtime(i, 0))) return sample.runtime(i, 1);
        return "\u2014";
    }

    private String runtimeLine(int row) {
        CriticalBusinessSnapshot sample = business.getValue();
        if (row >= sample.runtimeCount()) return "";
        String label = sample.runtime(row, 0), value = sample.runtime(row, 1);
        if (label.equals("ui.runtime.chemical.state")) value = tr("ui.chemical.state." + value);
        else if (label.equals("ui.runtime.chemical.recipe")) value = chemicalRecipeName(value);
        else if (label.equals("ui.runtime.solar.water_status") || label.equals("ui.runtime.solar.salt_status"))
            value = tr("ui.branch." + (value.isEmpty() ? "none" : value));
        else if (label.equals("ui.runtime.solar.night")) value = tr("ui.solar.night." + value);
        else if (label.equals("ui.runtime.sample_scope")) value = tr(
            (sample.configuration().kind == CriticalMachineKind.BATTERY ? "ui.chemical.sample_scope."
                : "ui.sample_scope.") + value);
        else if (label.equals("ui.runtime.sun.phase")) value = tr("ui.sun.phase." + value);
        else if (label.equals("ui.runtime.sun.production_paused")) value = tr("ui.sun.paused." + value);
        else if ((label.equals("ui.runtime.solar.mode") || label.equals("ui.runtime.solar.batch_mode")))
            value = tr("steam_mode." + value);
        else if (label.equals("ui.runtime.solar.route") || label.equals("ui.runtime.solar.batch_route"))
            value = tr("ui.solar.route." + value);
        else if (label.equals("ui.runtime.solar.coolant")) value = value.isEmpty() ? "—" : tr("solar.coolant." + value);
        else if (label.equals("ui.runtime.solar.exciter")) value = value.isEmpty() ? "—" : tr("solar.exciter." + value);
        else if (label.equals("ui.runtime.solar.plasma"))
            value = value.isEmpty() ? "—" : com.miaokatze.gtsr.common.critical.CriticalPlasmaCatalog.displayName(value);
        else if (label.equals("ui.runtime.settlement")) value = tr("ui.settlement." + value);
        if (sample.configuration().kind == CriticalMachineKind.BATTERY) {
            if (label.equals("ui.runtime.batch_wireless")) label = "ui.chemical.receivable";
            else if (label.equals("ui.runtime.last_wireless")) label = "ui.chemical.last_wireless";
        }
        return tr(label) + ": " + value;
    }

    private static double ratio(long value, long maximum) {
        return maximum <= 0 ? 0 : Math.max(0, Math.min(1, (double) value / maximum));
    }

    private static String time(int elapsed, int duration) {
        return String.format(java.util.Locale.ROOT, "%.1f / %.1f s", elapsed / 20D, duration / 20D);
    }

    private ParentWidget<?> metric(String label, java.util.function.Supplier<String> value,
        java.util.function.DoubleSupplier progress, int x, int y) {
        return new ParentWidget<>().name("critical_metric_card_" + label)
            .pos(x, y)
            .size(228, 52)
            .background(CriticalGuiStyle.CARD)
            .child(
                CriticalGuiStyle
                    .label(
                        () -> "ui.energy".equals(label) ? primaryMetricLabel()
                            : "ui.secondary".equals(label) ? secondaryMetricLabel() : tr(label),
                        () -> CriticalGuiStyle.TEXT_MUTED,
                        Alignment.CenterLeft)
                    .pos(4, 3)
                    .size(220, 13))
            .child(
                textViewport("critical_metric_" + label, value, 212, 25, () -> CriticalGuiStyle.TEXT_TITLE).pos(8, 18)
                    .tooltipBuilder(t -> t.add(value.get()))
                    .tooltipAutoUpdate(true))
            .child(
                new ParentWidget<>().background(new CriticalProgressBar(progress))
                    .pos(8, 45)
                    .size(212, 3)
                    .setEnabledIf(
                        w -> !"ui.energy".equals(label)
                            && !(configuration().kind == CriticalMachineKind.BATTERY && "ui.secondary".equals(label))));
    }

    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager manager, ModularPanel parent) {
        ListWidget<IWidget, ?> list = new ListWidget<>();
        list.child(
            IKey.dynamic(
                () -> "EU " + business.getValue()
                    .energy()
                    + " / "
                    + business.getValue()
                        .capacity())
                .color(CriticalGuiStyle.TEXT_BODY)
                .asWidget()
                .fullWidth()
                .marginBottom(3));
        list.child(
            IKey.dynamic(
                () -> tr("construction") + " "
                    + business.getValue().construction.stage()
                    + " · "
                    + business.getValue().construction.placed()
                    + " / "
                    + business.getValue().construction.total())
                .color(CriticalGuiStyle.TEXT_BODY)
                .asWidget()
                .fullWidth()
                .marginBottom(3));
        list.child(
            IKey.dynamic(
                () -> tr("batch") + " "
                    + business.getValue()
                        .batchTicks()
                    + " / "
                    + business.getValue()
                        .batchDuration()
                    + " t")
                .color(CriticalGuiStyle.TEXT_BODY)
                .asWidget()
                .fullWidth()
                .marginBottom(3));
        list.child(
            IKey.dynamic(
                () -> business.getValue()
                    .power() + " EU/t")
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
        return permitted.getValue() && business.getValue().construction.status() == 0;
    }

    private static void constructionLine(ListWidget<IWidget, ?> list, java.util.function.Supplier<String> text) {
        constructionLine(list, text, () -> CriticalGuiStyle.TEXT_BODY);
    }

    private static void constructionLine(ListWidget<IWidget, ?> list, java.util.function.Supplier<String> text,
        java.util.function.IntSupplier color) {
        list.child(
            CriticalGuiStyle.compactText(IKey.dynamic(text))
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
        return new ActionButton(name).name("critical_action_" + name)
            .background(
                new DynamicDrawable(() -> actionAvailable(name) ? CriticalGuiStyle.BUTTON : CriticalGuiStyle.DISABLED))
            .hoverBackground(
                new DynamicDrawable(() -> actionAvailable(name) ? CriticalGuiStyle.HOVER : CriticalGuiStyle.DISABLED))
            .syncHandler(interaction(manager, name, command))
            .overlay(
                CriticalGuiStyle.fitted(
                    () -> actionLabel(name),
                    () -> !actionAvailable(name) ? CriticalGuiStyle.TEXT_MUTED
                        : dangerous(name) ? CriticalGuiStyle.STATE_OFFLINE : CriticalGuiStyle.TEXT_CONTROL,
                    Alignment.CENTER))
            .tooltipBuilder(t -> {
                t.add(tr(name));
                if (!actionAvailable(name)) t.add(actionDisabledReason(name));
                else if (dangerous(name)) t.add(tr("ui.terminal.confirm_hint"));
            })
            .tooltipAutoUpdate(true);
    }

    private String actionLabel(String name) {
        if (name.equals(armedAction)) return tr("ui.terminal.action.confirm");
        return tr(switch (name) {
            case "kind", "build", "repair", "dismantle" -> "ui.terminal.action." + name;
            default -> name;
        });
    }

    private static boolean dangerous(String name) {
        return "dismantle".equals(name) || "ui.sun.extinguish".equals(name) || "ui.sun.cancel_legacy".equals(name);
    }

    /** Keep unavailable controls hoverable so their reason remains discoverable. */
    private final class ActionButton extends ButtonWidget<ActionButton> {

        private final String action;

        private ActionButton(String action) {
            this.action = action;
        }

        @Override
        public Interactable.Result onMousePressed(int button) {
            if (!actionAvailable(action)) return Interactable.Result.STOP;
            if (button == 0) {
                if (dangerous(action) && !action.equals(armedAction)) {
                    armedAction = action;
                    return Interactable.Result.STOP;
                }
                armedAction = "";
            }
            return super.onMousePressed(button);
        }

    }

    private boolean actionAvailable(String name) {
        if (!permitted.getValue()) return false;
        int status = business.getValue().construction.status();
        return switch (name) {
            case "kind" -> status == 0 && loomController.getValue();
            case "power" -> loomController.getValue() || status == 2;
            case "build", "ui.build_context" -> status == 0;
            case "ui.resume_context" -> !business.getValue().construction.enabled();
            case "dismantle" -> status == 1 || status == 2 || status == 4;
            case "repair" -> status != 0;
            default -> true;
        };
    }

    private String actionDisabledReason(String name) {
        if (!permitted.getValue()) return tr("action.permission");
        return tr(
            "power".equals(name) ? "ui.power.unbuilt"
                : "ui.resume_context".equals(name) ? "ui.action.unavailable.running"
                    : "ui.action.unavailable." + ("kind".equals(name) ? "configuration"
                        : "build".equals(name) || "ui.build_context".equals(name) ? "build"
                            : "dismantle".equals(name) ? "dismantle" : "repair"));
    }

    private InteractionSyncHandler interaction(PanelSyncManager manager, String name,
        java.util.function.Predicate<EntityPlayer> command) {
        // The panel is built before its container is bound. Capture the opening connection from the viewer.
        final EntityPlayer player = viewer;
        final NetHandlerPlayServer connection = ServerPacketAuthority.connection(player);
        final AtomicLong openGeneration = new AtomicLong();
        // Reopening retained MUI managers starts a new interval even with the same container.
        manager.addOpenListener(ignored -> openGeneration.incrementAndGet());
        InteractionSyncHandler handler = new InteractionSyncHandler().setOnMousePressed(data -> {
            if (manager.isClient() || data.mouseButton != 0) return;
            final ModularContainer container = manager.getContainer();
            final long generation = openGeneration.get();
            if (!manager.getModularSyncManager()
                .isOpen() || container == null
                || player == null
                || player.openContainer != container
                || !actionPending.compareAndSet(false, true)) return;
            // Only a fixed button command is retained, never MouseData/PacketBuffer or editable widget values.
            Mui2ServerActions.enqueue(() -> {
                try {
                    // MUI deliberately retains closed managers for reopen; their network IDs still route packets.
                    if (!ServerPacketAuthority.isCurrentPlayer(player, connection) || openGeneration.get() != generation
                        || !manager.getModularSyncManager()
                            .isOpen()
                        || manager.getPlayer() != player
                        || manager.getContainer() != container
                        || player.openContainer != container
                        || !container.canInteractWith(player)) return;
                    lastAction = name;
                    if (!state.canUse(player)) {
                        lastActionAccepted = false;
                        lastActionDetail = "action.permission";
                        player.addChatMessage(new ChatComponentTranslation(key("action.permission")));
                        return;
                    }
                    lastActionAccepted = command.test(player);
                    lastActionDetail = state.getDisplayStatusKey();
                    if (!lastActionAccepted) player.addChatMessage(
                        new ChatComponentTranslation(
                            key("action.rejected"),
                            new ChatComponentTranslation(key(lastAction)),
                            new ChatComponentTranslation(key(lastActionDetail))));
                } finally {
                    actionPending.set(false);
                }
            });
        });
        manager.syncValue("critical.action." + name, handler);
        return handler;
    }

    private IWidget inventoryPage(PanelSyncManager manager) {
        ParentWidget<?> page = new ParentWidget<>().name("critical_page_inventory")
            .size(463, 252);
        for (int side = 0; side < 2; side++) {
            final int index = side;
            ParentWidget<?> card = new ParentWidget<>().name("critical_inventory_card_" + side)
                .pos(side * 235, 0)
                .size(228, 142)
                .background(CriticalGuiStyle.CARD);
            card.child(
                CriticalGuiStyle
                    .label(
                        () -> tr(index == 0 ? "input" : "output"),
                        () -> CriticalGuiStyle.TEXT_ACCENT,
                        Alignment.CenterLeft)
                    .pos(4, 3)
                    .size(220, 14));
            ListWidget<IWidget, ?> contents = new ListWidget<>().name("critical_inventory_" + side)
                .pos(7, 23)
                .size(214, 112)
                .padding(3)
                .scrollDirection(CriticalGuiStyle.scroll())
                .overlay(CriticalGuiStyle.SCROLL_EDGES);
            if (index == 0) {
                constructionLine(
                    contents,
                    () -> tr(
                        loomController.getValue() ? "ui.inventory.construction_physical" : "ui.inventory.physical"),
                    () -> CriticalGuiStyle.TEXT_ACCENT);
                constructionLine(
                    contents,
                    () -> inventoryText(
                        business.getValue()
                            .inputs()));
                constructionLine(contents, () -> tr("ui.inventory.network"), () -> CriticalGuiStyle.TEXT_ACCENT);
                constructionLine(
                    contents,
                    () -> inventoryText(
                        business.getValue()
                            .networkInputs()));
            } else {
                constructionLine(
                    contents,
                    () -> inventoryText(
                        business.getValue()
                            .outputs()));
            }
            card.child(contents);
            page.child(card);
        }
        ParentWidget<?> backpack = new ParentWidget<>().name("critical_player_inventory_card")
            .pos(0, 148)
            .size(463, 104)
            .background(CriticalGuiStyle.CARD);
        backpack.child(
            CriticalGuiStyle
                .label(
                    () -> tr("ui.terminal.player_inventory"),
                    () -> CriticalGuiStyle.TEXT_MUTED,
                    Alignment.CenterLeft)
                .pos(4, 3)
                .size(220, 14));
        backpack
            .child(textViewport("critical_inventory_hint", () -> tr("ui.terminal.inventory_hint"), 205, 70).pos(9, 23));
        // A single native player group: changing pages never replaces its slots or sync identity.
        backpack.child(
            SlotGroupWidget.playerInventory(
                (index, slot) -> slot.background(CriticalGuiStyle.SLOT)
                    .hoverBackground(CriticalGuiStyle.SLOT))
                .pos(267, 19));
        page.child(backpack);
        return page;
    }

    private String runtimeGuidance() {
        CriticalBusinessSnapshot sample = business.getValue();
        if (sample.construction.status() != 2) return tr("ui.power.unbuilt");
        String blocker = sample.runtimeBlockerKey();
        String reason = blocker.isEmpty() ? tr(sample.runtimeStatusKey()) : sample.runtimeBlockerDetail();
        String next = blocker.isEmpty() ? "runtime.working".equals(sample.runtimeStatusKey()) ? "working" : "ready"
            : blocker;
        if (configuration().kind == CriticalMachineKind.SUN && "runtime.power_off".equals(blocker)) next = "sun_ignite";
        return tr("ui.running_status") + ": "
            + reason
            + "\n"
            + tr("ui.next_action")
            + ": "
            + tr("ui.runtime.next." + next)
            + (actionName.getValue()
                .isEmpty()
                    ? ""
                    : "\n" + tr(actionName.getValue())
                        + ": "
                        + tr(actionAccepted.getValue() ? "action.accepted" : "action.refused"));
    }

    private static String inventoryText(String value) {
        return value.trim()
            .isEmpty() ? tr("ui.inventory.empty") : value;
    }

    private static ListWidget<IWidget, ?> textViewport(String name, java.util.function.Supplier<String> text, int width,
        int height) {
        return textViewport(name, text, width, height, () -> CriticalGuiStyle.TEXT_BODY);
    }

    private static ListWidget<IWidget, ?> textViewport(String name, java.util.function.Supplier<String> text, int width,
        int height, java.util.function.IntSupplier color) {
        return new ListWidget<>().name(name)
            .size(width, height)
            .scrollDirection(CriticalGuiStyle.scroll())
            .child(
                CriticalGuiStyle.compactText(IKey.dynamic(text))
                    .color(color)
                    .fullWidth());
    }

    private CriticalConfiguration configuration() {
        return business.getValue()
            .configuration();
    }

    private void constructionTooltip(com.cleanroommc.modularui.screen.RichTooltip tooltip) {
        CriticalConfiguration cfg = configuration();
        int currentStage = Math.max(0, Math.min(5, business.getValue().construction.stage()));
        tooltip.add(tr("construction") + " " + currentStage);
        tooltip.add(
            business.getValue()
                .nextItems());
        tooltip.add(
            business.getValue()
                .nextFluids());
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
        if (current == CriticalMachineKind.TURBINE) {
            tooltip.add(tr("operation.warmup"));
            tooltip.add(tr("operation.idle"));
        }
        if (current == CriticalMachineKind.BATTERY) {
            tooltip.add(tr("operation.battery.windows"));
            tooltip.add(tr("operation.battery.paid"));
        }
        if (current == CriticalMachineKind.SUN) {
            tooltip.add(
                StatCollector.translateToLocalFormatted(
                    key("operation.sun.ignition"),
                    CriticalStellarPolicy.IGNITION_EU / CriticalStellarPolicy.DURATION_TICKS,
                    CriticalStellarPolicy.IGNITION_HELIUM3_L / CriticalStellarPolicy.DURATION_TICKS));
            tooltip.add(
                StatCollector.translateToLocalFormatted(
                    key("operation.sun.heat"),
                    CriticalStellarPolicy.SUSTAIN_EUT,
                    CriticalStellarPolicy.SUSTAIN_HELIUM3_L_PER_TICK));
            tooltip.add(tr("operation.sun.pause"));
        }
        if (current == CriticalMachineKind.SOLAR) tooltip.add(tr("operation.solar.night"));
        tooltip.add(tr("functional.decorative"));
    }

    private String operationLine(String section) {
        CriticalMachineKind current = configuration().kind;
        if (!current.isReleased()) return "input".equals(section) ? tr("tooltip.frozen") : "";
        return tr("operation." + current.key + "." + section);
    }

    private gregtech.api.recipe.RecipeMap<?> catalogue() {
        return CriticalRecipeMaps.forKind(loomController.getValue() ? null : configuration().kind);
    }

    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    private void openRecipeCatalogue() {
        gregtech.api.recipe.RecipeMap<?> map = catalogue();
        if (map != null) codechicken.nei.recipe.GuiCraftingRecipe.openRecipeGui(
            map.getFrontend()
                .getUIProperties().neiTransferRectId);
    }

    private static String key(String suffix) {
        return "gtsr.critical." + suffix;
    }

    private static String tr(String suffix) {
        return StatCollector.translateToLocal(key(suffix));
    }
}
