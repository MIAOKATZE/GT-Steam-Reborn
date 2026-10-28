package com.miaokatze.gtsr.common.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
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
import com.miaokatze.gtsr.common.gui.mui2.TerminalViewport;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.modularui2.widget.GTProgressWidget;

/** Handheld terminal only; GT's native container lifecycle owns inventory and NEI integration. */
public final class MTECriticalMachineTerminalGui {

    private final MTECriticalMachine multiblock;
    private final CriticalMachineState state;
    private EntityPlayer viewer;
    private BooleanSyncValue permitted;
    private BooleanSyncValue loomController;
    private StringSyncValue actionName, actionDetail;
    private BooleanSyncValue actionAccepted;
    private String lastAction = "", lastActionDetail = "";
    private boolean lastActionAccepted;
    private int selectedPage;
    private boolean runtimeExpanded;
    private boolean initialPageSelected;
    private boolean pageChosen;
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
        PagedWidget<?> pages = new PagedWidget<>().name("critical_pages")
            .size(463, 202);
        ParentWidget<?> construction = constructionView.create();
        construction.child(
            action(manager, "ui.build_context", player -> state.startConstruction(player)).pos(8, 185)
                .size(130, 16)
                .tooltipBuilder(this::constructionTooltip)
                .tooltipAutoUpdate(true));
        construction.child(
            action(manager, "ui.resume_context", player -> state.setEnabled(player, true)).pos(146, 185)
                .size(130, 16));
        pages.addPage(construction);
        pages.addPage(controlPage(manager));
        pages.addPage(inventoryPage(manager));
        pages.onUpdateListener(widget -> {
            // Wait for the first authoritative descriptor; a remote client may not have the target TE.
            if (!initialPageSelected && business.getValue()
                .hasSample() && pages.isValid()) {
                initialPageSelected = true;
                if (!pageChosen && business.getValue().construction.status() == 2) {
                    selectedPage = 1;
                    pages.setPage(1);
                }
            }
        });
        Flow tabs = Flow.row()
            .size(463, 20)
            .childPadding(4);
        String[] labels = { "tab.construction", "ui.tab.runtime", "tab.inventory" };
        for (int i = 0; i < labels.length; i++) {
            final int page = i;
            tabs.child(
                new ButtonWidget<>().name("critical_tab_" + page)
                    .size(151, 18)
                    .background(
                        new DynamicDrawable(
                            () -> selectedPage == page ? CriticalGuiStyle.SELECTED_TAB : CriticalGuiStyle.TAB))
                    .hoverBackground(CriticalGuiStyle.HOVER)
                    .tooltipBuilder(t -> t.add(IKey.lang(key(labels[page]))))
                    .overlay(
                        IKey.dynamic(() -> (selectedPage == page ? "• " : "") + tr(labels[page]))
                            .scale(0.9f)
                            .color(
                                () -> selectedPage == page ? CriticalGuiStyle.TEXT_ACCENT : CriticalGuiStyle.TEXT_BODY))
                    .onMousePressed(button -> {
                        if (button != 0) return false;
                        pages.setPage(page);
                        selectedPage = page;
                        pageChosen = true;
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
            textViewport("critical_terminal_title", this::terminalTitle, 447, 12, () -> CriticalGuiStyle.TEXT_TITLE)
                .pos(8, 4)
                .tooltipBuilder(t -> t.add(terminalTitle()))
                .tooltipAutoUpdate(true));
        header.child(
            IKey.dynamic(
                () -> tr(
                    business.getValue()
                        .loomValid() ? "ui.loom_valid" : "ui.loom_invalid"))
                .color(
                    () -> business.getValue()
                        .loomValid() ? CriticalGuiStyle.STATE_ONLINE : CriticalGuiStyle.STATE_OFFLINE)
                .asWidget()
                .pos(8, 19)
                .size(136, 10));
        header.child(
            textViewport(
                "critical_status_detail",
                () -> business.getValue()
                    .detail(),
                307,
                10,
                this::statusColor).pos(148, 19)
                    .tooltipBuilder(
                        t -> t.add(
                            business.getValue()
                                .detail()))
                    .tooltipAutoUpdate(true));
        return header;
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
        int cause = business.getValue().construction.reason();
        if (cause != 0 && cause != 7) return CriticalGuiStyle.STATE_OFFLINE;
        if (cause == 7 || business.getValue().construction.status() == 0) return GtsrGuiPalette.STATE_IDLE;
        return business.getValue().construction.status() == 2 ? CriticalGuiStyle.STATE_ONLINE
            : CriticalGuiStyle.TEXT_ACCENT;
    }

    private IWidget controlPage(PanelSyncManager manager) {
        ParentWidget<?> page = new ParentWidget<>().name("critical_page_control")
            .size(463, 202);
        page.child(
            action(manager, "kind", player -> change(player, 1, 1))
                .overlay(
                    IKey.dynamic(() -> tr("machine." + configuration().kind.key))
                        .color(
                            () -> actionAvailable("kind") ? CriticalGuiStyle.TEXT_CONTROL : CriticalGuiStyle.TEXT_MUTED)
                        .scale(0.85f))
                .pos(0, 0)
                .size(210, 20)
                .setEnabledIf(w -> loomController.getValue())
                .tooltipBuilder(t -> t.add(IKey.lang(key("configuration_hint")))));
        page.child(
            action(manager, "ui.solar.change_route", player -> state.selectSolarSaltMode(player))
                .overlay(
                    IKey.dynamic(() -> tr("ui.solar.route." + runtimeValue("solar.route")))
                        .color(CriticalGuiStyle.TEXT_CONTROL))
                .pos(0, 0)
                .size(210, 20)
                .setEnabledIf(w -> configuration().kind == CriticalMachineKind.SOLAR && !loomController.getValue())
                .tooltipBuilder(t -> t.add(IKey.lang(key("ui.solar.route_hint")))));
        page.child(
            action(manager, "ui.change_mode", player -> state.selectSteamMode(player))
                .overlay(
                    IKey.dynamic(
                        () -> tr(
                            "steam_mode." + business.getValue()
                                .steamMode()))
                        .color(CriticalGuiStyle.TEXT_CONTROL))
                .pos(215, 0)
                .size(160, 20)
                .setEnabledIf(w -> configuration().kind == CriticalMachineKind.SOLAR));
        page.child(
            textViewport(
                "critical_chemical_recipe",
                this::chemicalRecipeTitle,
                160,
                20,
                () -> CriticalGuiStyle.TEXT_ACCENT).pos(215, 0)
                    .setEnabledIf(w -> configuration().kind == CriticalMachineKind.BATTERY)
                    .tooltipBuilder(t -> t.add(chemicalRecipeTitle()))
                    .tooltipAutoUpdate(true));
        page.child(
            action(manager, "ui.sun.production_pause", player -> state.selectStellarProductionPaused(player))
                .overlay(
                    IKey.dynamic(() -> tr("ui.sun.production_pause." + runtimeValue("sun.production_paused")))
                        .color(CriticalGuiStyle.TEXT_CONTROL))
                .pos(215, 0)
                .size(160, 20)
                .setEnabledIf(w -> configuration().kind == CriticalMachineKind.SUN));
        page.child(
            new ButtonWidget<>().name("critical_recipe_preview")
                .background(CriticalGuiStyle.BUTTON)
                .hoverBackground(CriticalGuiStyle.HOVER)
                .overlay(
                    IKey.lang(key("operation.details"))
                        .color(CriticalGuiStyle.TEXT_CONTROL))
                .pos(380, 0)
                .size(83, 20)
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    openRecipeCatalogue();
                    return true;
                })
                .setEnabledIf(
                    w -> !business.getValue()
                        .canCancelLegacyStellarBatch())
                .tooltipBuilder(this::operationTooltip)
                .tooltipAutoUpdate(true));
        page.child(
            action(manager, "ui.sun.cancel_legacy", player -> state.cancelLegacyStellarBatch(player)).pos(380, 0)
                .size(83, 20)
                .setEnabledIf(
                    w -> business.getValue()
                        .canCancelLegacyStellarBatch())
                .tooltipBuilder(t -> t.add(IKey.lang(key("ui.sun.cancel_legacy_hint")))));
        page.child(metric("ui.energy", this::primaryMetricValue, () -> 0, 0, 26).setEnabledIf(w -> !runtimeExpanded));
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
                26).setEnabledIf(w -> !runtimeExpanded));
        ParentWidget<?> summary = new ParentWidget<>().pos(0, 74)
            .size(463, 70)
            .background(CriticalGuiStyle.CARD)
            .setEnabledIf(w -> !runtimeExpanded);
        summary.child(runtimeDisclosure("critical_runtime_disclosure"));
        ParentWidget<?> details = new ParentWidget<>().name("critical_runtime_details")
            .pos(0, 26)
            .size(463, 118)
            .background(CriticalGuiStyle.CARD)
            .setEnabledIf(w -> runtimeExpanded);
        details.child(runtimeDisclosure("critical_runtime_collapse"));
        ListWidget<IWidget, ?> summaryText = new ListWidget<>().name("critical_status_summary")
            .pos(8, 26)
            .size(447, 85)
            .scrollDirection(CriticalGuiStyle.scroll());
        for (int i = 0; i < 40; i++) {
            final int row = i;
            constructionLine(summaryText, () -> runtimeLine(row));
        }
        details.child(summaryText);
        page.child(details);
        summary.child(
            new ParentWidget<>().name("critical_runtime_process")
                .pos(8, 26)
                .size(190, 37)
                .background(new CriticalRuntimeDiagram(business::getValue))
                .tooltipBuilder(this::operationTooltip)
                .tooltipAutoUpdate(true)
                .setEnabledIf(w -> !runtimeExpanded));
        summary.child(
            textViewport("critical_chemical_flow", this::chemicalFlowSummary, 164, 37, () -> CriticalGuiStyle.TEXT_BODY)
                .pos(29, 26)
                .setEnabledIf(w -> !runtimeExpanded && configuration().kind == CriticalMachineKind.BATTERY));
        summary.child(
            textViewport(
                "critical_runtime_priority",
                () -> configuration().kind == CriticalMachineKind.BATTERY ? chemicalProgressSummary()
                    : business.getValue()
                        .detail() + "\n"
                        + sampleScope()
                        + "\n"
                        + tr("batch")
                        + ": "
                        + time(
                            business.getValue()
                                .batchTicks(),
                            business.getValue()
                                .batchDuration()),
                222,
                37,
                this::statusColor).pos(205, 26)
                    .setEnabledIf(w -> !runtimeExpanded));
        GTProgressWidget batchProgress = new GTProgressWidget() {

            @Override
            public Interactable.Result onMousePressed(int button) {
                // Resolve from the received descriptor at interaction time, including remote clients without a tile.
                gregtech.api.recipe.RecipeMap<?> map = catalogue();
                if (map == null) return Interactable.Result.IGNORE;
                neiTransferRect(map);
                return super.onMousePressed(button);
            }
        };
        summary.child(
            batchProgress.texture(GTGuiTextures.PROGRESSBAR_ARROW_STANDARD, 20)
                .progress(
                    () -> ratio(
                        business.getValue()
                            .batchTicks(),
                        business.getValue()
                            .batchDuration()))
                .pos(435, 48)
                .size(20, 16));
        page.child(summary);
        page.child(
            action(manager, "build", player -> state.startConstruction(player)).pos(0, 150)
                .size(112, 20)
                .tooltipBuilder(this::constructionTooltip)
                .tooltipAutoUpdate(true));
        page.child(
            action(manager, "dismantle", player -> state.startDismantle(player)).pos(117, 150)
                .size(112, 20));
        page.child(
            action(manager, "repair", player -> state.repairStructure(player)).pos(234, 150)
                .size(112, 20)
                .tooltipBuilder(this::constructionTooltip)
                .tooltipAutoUpdate(true));
        page.child(action(manager, "power", player -> {
            if (state.getKind() == CriticalMachineKind.SUN) {
                if (state.isEnabled() || state.getStellarRuntimeData()
                    .getBoolean("hot")) return state.shutdownStellar(player);
                return state.setEnabled(player, true);
            }
            return state.setEnabled(player, !state.isEnabled());
        }).overlay(
            IKey.dynamic(
                () -> tr(
                    configuration().kind == CriticalMachineKind.SUN
                        ? business.getValue().construction.enabled() || "3".equals(runtimeValue("sun.phase"))
                            || "4".equals(runtimeValue("sun.phase")) ? "ui.sun.extinguish" : "ui.sun.ignite"
                        : business.getValue().construction.enabled() ? "stop" : "start"))
                .color(CriticalGuiStyle.TEXT_CONTROL))
            .pos(351, 150)
            .size(112, 20)
            .tooltipBuilder(t -> t.add(IKey.lang(key("ui.sun.power_hint")))));
        ListWidget<IWidget, ?> feedback = new ListWidget<>().name("critical_action_feedback")
            .pos(0, 176)
            .size(463, 26)
            .padding(4)
            .background(CriticalGuiStyle.CARD)
            .scrollDirection(CriticalGuiStyle.scroll());
        feedback.child(
            CriticalGuiStyle.text(
                IKey.dynamic(
                    () -> tr("ui.inventory.blocker") + ": "
                        + business.getValue()
                            .detail()
                        + "\n"
                        + tr("ui.next_action")
                        + ": "
                        + tr(business.getValue().construction.recoveryKey())
                        + (actionName.getValue()
                            .isEmpty()
                                ? ""
                                : "\n" + tr(actionName.getValue())
                                    + " | "
                                    + tr(actionAccepted.getValue() ? "action.accepted" : "action.refused"))))
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
                            .isEmpty() ? "" : tr(actionDetail.getValue())))
                .tooltipAutoUpdate(true));
        page.child(feedback);
        return page;
    }

    private ButtonWidget<?> runtimeDisclosure(String name) {
        return new ButtonWidget<>().name(name)
            .pos(8, 5)
            .size(447, 17)
            .background(CriticalGuiStyle.BUTTON)
            .hoverBackground(CriticalGuiStyle.HOVER)
            .overlay(
                IKey.dynamic(() -> (runtimeExpanded ? "− " : "+ ") + tr("ui.details.runtime"))
                    .color(CriticalGuiStyle.TEXT_ACCENT))
            .onMousePressed(button -> {
                if (button != 0) return false;
                runtimeExpanded = !runtimeExpanded;
                return true;
            });
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
        return new ParentWidget<>().pos(x, y)
            .size(228, 44)
            .background(CriticalGuiStyle.CARD)
            .child(
                IKey.dynamic(
                    () -> "ui.energy".equals(label) ? primaryMetricLabel()
                        : "ui.secondary".equals(label) ? secondaryMetricLabel() : tr(label))
                    .color(CriticalGuiStyle.TEXT_MUTED)
                    .asWidget()
                    .pos(8, 5)
                    .size(212, 10))
            .child(
                new ListWidget<>().name("critical_metric_" + label)
                    .size(212, 19)
                    .scrollDirection(CriticalGuiStyle.scroll())
                    .child(
                        CriticalGuiStyle.text(IKey.dynamic(value))
                            .color(CriticalGuiStyle.TEXT_TITLE)
                            .fullWidth())
                    .pos(8, 15)
                    .tooltipBuilder(t -> t.add(value.get()))
                    .tooltipAutoUpdate(true))
            .child(
                new ParentWidget<>().background(new CriticalProgressBar(progress))
                    .pos(8, 35)
                    .size(212, 4)
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
        return new ActionButton(name).name("critical_action_" + name)
            .background(
                new DynamicDrawable(() -> actionAvailable(name) ? CriticalGuiStyle.BUTTON : CriticalGuiStyle.DISABLED))
            .hoverBackground(
                new DynamicDrawable(() -> actionAvailable(name) ? CriticalGuiStyle.HOVER : CriticalGuiStyle.DISABLED))
            .syncHandler(interaction(manager, name, command))
            .overlay(
                IKey.lang(key(name))
                    .color(() -> actionAvailable(name) ? CriticalGuiStyle.TEXT_CONTROL : CriticalGuiStyle.TEXT_MUTED))
            .tooltipBuilder(t -> { if (!actionAvailable(name)) t.add(actionDisabledReason(name)); })
            .tooltipAutoUpdate(true);
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
            return super.onMousePressed(button);
        }

    }

    private boolean actionAvailable(String name) {
        if (!permitted.getValue()) return false;
        int status = business.getValue().construction.status();
        return switch (name) {
            case "kind" -> status == 0 && loomController.getValue();
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
            "ui.resume_context".equals(name) ? "ui.action.unavailable.running"
                : "ui.action.unavailable." + ("kind".equals(name) ? "configuration"
                    : "build".equals(name) || "ui.build_context".equals(name) ? "build"
                        : "dismantle".equals(name) ? "dismantle" : "repair"));
    }

    private InteractionSyncHandler interaction(PanelSyncManager manager, String name,
        java.util.function.Predicate<EntityPlayer> command) {
        InteractionSyncHandler handler = new InteractionSyncHandler().setOnMousePressed(data -> {
            if (manager.isClient() || data.mouseButton != 0) return;
            EntityPlayer player = manager.getPlayer();
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
        });
        manager.syncValue("critical.action." + name, handler);
        return handler;
    }

    private IWidget inventoryPage(PanelSyncManager manager) {
        ParentWidget<?> page = new ParentWidget<>().name("critical_page_inventory")
            .size(463, 202);

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
                .scrollDirection(CriticalGuiStyle.scroll());
            if (index == 0) {
                constructionLine(contents, () -> tr("ui.inventory.physical"), () -> CriticalGuiStyle.TEXT_ACCENT);
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
                constructionLine(contents, () -> tr("ui.economy.network_unpaid"), () -> CriticalGuiStyle.TEXT_MUTED);
            } else {
                constructionLine(
                    contents,
                    () -> inventoryText(
                        business.getValue()
                            .outputs()));
                constructionLine(
                    contents,
                    () -> tr("ui.inventory.blocker") + ": "
                        + business.getValue()
                            .detail(),
                    () -> CriticalGuiStyle.TEXT_ACCENT);
            }
            card.child(contents);
            page.child(card);
        }
        page.child(
            textViewport(
                "critical_inventory_hint",
                () -> tr("ui.inventory.blocker") + ": "
                    + business.getValue()
                        .detail()
                    + "\n"
                    + tr("ui.inventory.hint"),
                447,
                24).background(CriticalGuiStyle.CARD)
                    .pos(8, 172));
        return page;
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
                CriticalGuiStyle.text(IKey.dynamic(text))
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
