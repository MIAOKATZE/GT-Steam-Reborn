package com.miaokatze.gtsr.common.gui;

import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.utils.serialization.ByteBufAdapters;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.FluidDisplayWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import com.miaokatze.gtsr.common.critical.CriticalConfiguration;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.critical.CriticalTier;
import com.miaokatze.gtsr.common.critical.recipe.CriticalRecipes;
import com.miaokatze.gtsr.common.critical.recipe.RecipeMatch;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.modularui2.widget.GTProgressWidget;

/** Handheld terminal only; GT's native container lifecycle owns inventory and NEI integration. */
public final class MTECriticalMachineTerminalGui extends MTEMultiBlockBaseGui<MTECriticalMachine> {

    private final CriticalMachineState state;
    private EntityPlayer viewer;
    private IntSyncValue tier, kind, parallel, speed, economy, status, reason, stage, job, total, batch, duration;
    private LongSyncValue energy, capacity, euPerTick, constructionElapsed, constructionDuration;
    private IntSyncValue constructionRound, constructionRounds, roundElapsed, roundDuration;
    private GenericListSyncHandler<ItemStack> constructionCosts, nextConstructionCosts, missingConstructionCosts;
    private GenericListSyncHandler<FluidStack> stageConstructionFluids, nextConstructionFluids,
        missingConstructionFluids;
    private BooleanSyncValue enabled, permitted, loomValid, roundFunded;
    private StringSyncValue detail;
    private int recipePreview;
    private int selectedPage;

    public MTECriticalMachineTerminalGui(MTECriticalMachine multiblock) {
        super(multiblock);
        state = multiblock.getCriticalState();
    }

    @Override
    public ModularPanel build(PosGuiData data, PanelSyncManager manager, UISettings settings) {
        // Sync-value constructors evaluate their getters before the modular container is bound.
        viewer = data.getPlayer();
        return super.build(data, manager, settings).background(CriticalGuiStyle.PANEL);
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
        constructionElapsed = new LongSyncValue(state::getConstructionElapsedTicks);
        constructionDuration = new LongSyncValue(state::getConstructionDurationTicks);
        constructionCosts = itemCosts(state::getConstructionCostsForGui);
        nextConstructionCosts = itemCosts(state::getConstructionNextItemCostsForGui);
        missingConstructionCosts = itemCosts(state::getConstructionMissingItemsForGui);
        stageConstructionFluids = fluidCosts(state::getConstructionStageFluidCostsForGui);
        nextConstructionFluids = fluidCosts(state::getConstructionNextFluidCostsForGui);
        missingConstructionFluids = fluidCosts(state::getConstructionMissingFluidsForGui);
        loomValid = new BooleanSyncValue(state::isLoomStructureValid);
        roundFunded = new BooleanSyncValue(state::isConstructionRoundFunded);
        constructionRound = integer(manager, "constructionRound", state::getConstructionRoundIndex);
        constructionRounds = integer(manager, "constructionRounds", state::getConstructionRoundTotal);
        roundElapsed = integer(manager, "roundElapsed", state::getConstructionRoundElapsedTicks);
        roundDuration = integer(manager, "roundDuration", state::getConstructionRoundDurationTicks);
        enabled = new BooleanSyncValue(state::isEnabled);
        permitted = new BooleanSyncValue(() -> state.canUse(viewer));
        detail = new StringSyncValue(state::getStatus);
        manager.syncValue("critical.constructionElapsed", constructionElapsed);
        manager.syncValue("critical.constructionDuration", constructionDuration);
        manager.syncValue("critical.nextConstructionCosts", nextConstructionCosts);
        manager.syncValue("critical.missingConstructionCosts", missingConstructionCosts);
        manager.syncValue("critical.nextConstructionFluids", nextConstructionFluids);
        manager.syncValue("critical.stageConstructionFluids", stageConstructionFluids);
        manager.syncValue("critical.missingConstructionFluids", missingConstructionFluids);
        manager.syncValue("critical.loomValid", loomValid);
        manager.syncValue("critical.roundFunded", roundFunded);
        manager.syncValue("critical.energy", energy);
        manager.syncValue("critical.capacity", capacity);
        manager.syncValue("critical.euPerTick", euPerTick);
        manager.syncValue("critical.constructionCosts", constructionCosts);
        manager.syncValue("critical.enabled", enabled);
        manager.syncValue("critical.permitted", permitted);
        manager.syncValue("critical.detail", detail);
        manager.registerSlotGroup(new SlotGroup("critical.inputs", 9));
        manager.registerSlotGroup(new SlotGroup("critical.outputs", 9));
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

    private GenericListSyncHandler<FluidStack> fluidCosts(java.util.function.Supplier<FluidStack[]> getter) {
        return new GenericListSyncHandler<>(
            () -> Arrays.asList(getter.get()),
            null,
            packet -> ByteBufAdapters.FLUID_STACK.deserialize(packet),
            (packet, stack) -> ByteBufAdapters.FLUID_STACK.serialize(packet, stack),
            FluidStack::isFluidStackIdentical,
            FluidStack::copy);
    }

    private IntSyncValue integer(PanelSyncManager manager, String key, java.util.function.IntSupplier getter) {
        IntSyncValue value = new IntSyncValue(getter);
        manager.syncValue("critical." + key, value);
        return value;
    }

    @Override
    public Flow createMainColumn(ModularPanel panel, PanelSyncManager manager) {
        PagedWidget<?> pages = new PagedWidget<>().size(463, 186);
        pages.addPage(statusPage(panel, manager));
        pages.addPage(configurationPage(manager));
        pages.addPage(inventoryPage(manager));
        pages.addPage(constructionPage());
        Flow tabs = Flow.row()
            .size(463, 24)
            .childPadding(4);
        String[] labels = { "tab.status", "tab.configuration", "tab.inventory", "tab.requirements" };
        for (int i = 0; i < labels.length; i++) {
            final int page = i;
            tabs.child(
                new ButtonWidget<>().size(112, 22)
                    .background(
                        new DynamicDrawable(
                            () -> selectedPage == page ? CriticalGuiStyle.SELECTED_TAB : CriticalGuiStyle.TAB))
                    .hoverBackground(CriticalGuiStyle.SELECTED_TAB)
                    .overlay(
                        IKey.dynamic(
                            () -> (selectedPage == page ? EnumChatFormatting.GOLD : EnumChatFormatting.WHITE)
                                + tr(labels[page])))
                    .onMousePressed(button -> {
                        if (button != 0) return false;
                        pages.setPage(page);
                        selectedPage = page;
                        return true;
                    }));
        }
        return Flow.column()
            .padding(6)
            .child(statusHeader())
            .child(tabs)
            .child(pages)
            .child(
                SlotGroupWidget.playerInventory(false)
                    .marginTop(4)
                    .alignX(0.5f));
    }

    private IWidget statusHeader() {
        ParentWidget<?> header = new ParentWidget<>().size(463, 48);
        header.child(
            IKey.dynamic(
                () -> EnumChatFormatting.GOLD + tr("machine." + configuration().kind.key)
                    + " \u00b7 "
                    + configuration().tier.name())
                .color(0xFFFFFF)
                .asWidget()
                .pos(8, 4)
                .size(447, 14));
        header.child(
            IKey.dynamic(
                () -> (loomValid.getValue() ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
                    + tr(loomValid.getValue() ? "loom.formed" : "loom.unformed"))
                .color(0xFFFFFF)
                .asWidget()
                .background(CriticalGuiStyle.CHIP)
                .pos(4, 23)
                .size(190, 20));
        header.child(
            IKey.dynamic(
                () -> EnumChatFormatting.WHITE + tr("status." + status.getValue())
                    + " \u00b7 "
                    + tr("reason." + reason.getValue()))
                .color(0xFFFFFF)
                .asWidget()
                .background(CriticalGuiStyle.CHIP)
                .pos(200, 23)
                .size(259, 20));
        return header;
    }

    private IWidget statusPage(ModularPanel panel, PanelSyncManager manager) {
        ParentWidget<?> page = new ParentWidget<>().size(463, 186);
        ParentWidget<?> dashboard = new ParentWidget<>().size(307, 180)
            .background(CriticalGuiStyle.CARD);
        dashboard.child(
            meter(
                () -> "EU " + energy.getValue() + " / " + capacity.getValue(),
                () -> ratio(energy.getValue(), capacity.getValue()),
                8,
                8,
                291));
        dashboard.child(
            meter(
                () -> tr(
                    "construction") + " " + stage.getValue() + " \u00b7 " + job.getValue() + " / " + total.getValue(),
                () -> ratio(job.getValue(), total.getValue()),
                8,
                48,
                291));
        dashboard.child(
            meter(
                () -> tr("construction.round_time") + " "
                    + roundElapsed.getValue()
                    + " / "
                    + roundDuration.getValue()
                    + " t",
                () -> ratio(roundElapsed.getValue(), roundDuration.getValue()),
                8,
                88,
                291));
        dashboard.child(
            meter(
                () -> tr("batch") + " "
                    + batch.getValue()
                    + " / "
                    + duration.getValue()
                    + " t \u00b7 "
                    + euPerTick.getValue()
                    + " EU/t",
                () -> ratio(batch.getValue(), duration.getValue()),
                8,
                128,
                291));
        page.child(dashboard);
        ParentWidget<?> controls = new ParentWidget<>().pos(313, 0)
            .size(150, 180)
            .background(CriticalGuiStyle.CARD);
        controls.child(
            IKey.lang(key("ui.operations"))
                .color(0xFFFFFF)
                .asWidget()
                .pos(8, 8)
                .size(134, 14));
        controls.child(
            action(manager, "build", player -> state.startConstruction(player)).pos(8, 30)
                .size(134, 24)
                .setEnabledIf(w -> permitted.getValue() && loomValid.getValue() && status.getValue() == 0)
                .tooltipBuilder(this::constructionTooltip)
                .tooltipAutoUpdate(true));
        controls.child(
            action(manager, "dismantle", player -> state.startDismantle(player)).pos(8, 60)
                .size(134, 24)
                .setEnabledIf(w -> permitted.getValue() && (status.getValue() == 1 || status.getValue() == 2)));
        controls.child(
            action(manager, "repair", player -> state.repairStructure(player)).pos(8, 90)
                .size(134, 24)
                .setEnabledIf(w -> permitted.getValue() && loomValid.getValue() && status.getValue() != 0)
                .tooltipBuilder(this::constructionTooltip)
                .tooltipAutoUpdate(true));
        controls.child(
            action(manager, "power", player -> state.setEnabled(player, !state.isEnabled()))
                .overlay(
                    IKey.dynamic(() -> tr(enabled.getValue() ? "stop" : "start"))
                        .color(0xFFFFFF))
                .pos(8, 120)
                .size(134, 24)
                .setEnabledIf(w -> permitted.getValue() && (enabled.getValue() || loomValid.getValue())));
        controls.child(
            new GTProgressWidget().neiTransferRect(multiblock.getRecipeMap())
                .texture(GTGuiTextures.PROGRESSBAR_ARROW_STANDARD, 20)
                .progress(() -> ratio(batch.getValue(), duration.getValue()))
                .pos(8, 154)
                .size(134, 16)
                .tooltipBuilder(t -> t.add(IKey.lang(key("recipes")))));
        page.child(controls);
        return page;
    }

    private static double ratio(long value, long maximum) {
        return maximum <= 0 ? 0 : Math.max(0, Math.min(1, (double) value / maximum));
    }

    private static IWidget meter(java.util.function.Supplier<String> label, java.util.function.DoubleSupplier progress,
        int x, int y, int width) {
        return new ParentWidget<>().pos(x, y)
            .size(width, 34)
            .child(
                IKey.dynamic(() -> EnumChatFormatting.WHITE + label.get())
                    .color(0xFFFFFF)
                    .asWidget()
                    .size(width, 14))
            .child(
                new GTProgressWidget().texture(GTGuiTextures.PROGRESSBAR_FUEL_STANDARD, 14)
                    .progress(progress)
                    .pos(0, 18)
                    .size(width, 12));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager manager, ModularPanel parent) {
        ListWidget<IWidget, ?> list = new ListWidget<>();
        list.child(
            IKey.dynamic(() -> "EU " + energy.getValue() + " / " + capacity.getValue())
                .color(0xFFFFFF)
                .asWidget()
                .fullWidth()
                .marginBottom(3));
        list.child(
            IKey.dynamic(
                () -> tr("construction") + " " + stage.getValue() + " · " + job.getValue() + " / " + total.getValue())
                .color(0xFFFFFF)
                .asWidget()
                .fullWidth()
                .marginBottom(3));
        list.child(
            IKey.dynamic(() -> tr("batch") + " " + batch.getValue() + " / " + duration.getValue() + " t")
                .color(0xFFFFFF)
                .asWidget()
                .fullWidth()
                .marginBottom(3));
        list.child(
            IKey.dynamic(() -> euPerTick.getValue() + " EU/t")
                .color(0xFFFFFF)
                .asWidget()
                .fullWidth()
                .marginBottom(3));
        list.child(
            IKey.dynamic(() -> tr("construction_power") + " " + configuration().tier.constructionVoltage() + " EU/t")
                .color(0xFFFFFF)
                .asWidget()
                .fullWidth());
        return list;
    }

    private IWidget configurationPage(PanelSyncManager manager) {
        ParentWidget<?> page = new ParentWidget<>().size(463, 186)
            .background(CriticalGuiStyle.CARD);
        page.child(
            IKey.lang(key("configuration.section"))
                .color(0xFFFFFF)
                .asWidget()
                .pos(8, 8)
                .size(447, 14));
        page.child(
            action(manager, "tier", player -> change(player, 0, 1))
                .overlay(
                    IKey.dynamic(() -> configuration().tier.name())
                        .color(0xFFFFFF))
                .pos(8, 30)
                .size(88, 24)
                .setEnabledIf(w -> editable())
                .tooltipBuilder(t -> t.add(IKey.lang(key("configuration_hint")))));
        page.child(
            action(manager, "kind", player -> change(player, 1, 1))
                .overlay(
                    IKey.dynamic(() -> tr("machine." + configuration().kind.key))
                        .color(0xFFFFFF))
                .pos(102, 30)
                .size(353, 24)
                .setEnabledIf(w -> editable() && multiblock.isKindSelector())
                .tooltipBuilder(t -> t.add(IKey.lang(key("configuration_hint")))));
        String[] labels = { "parallel", "speed", "economy" };
        IntSyncValue[] values = { parallel, speed, economy };
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            page.child(
                IKey.dynamic(() -> tr(labels[index]) + " " + values[index].getValue())
                    .color(0xFFFFFF)
                    .asWidget()
                    .background(CriticalGuiStyle.CHIP)
                    .pos(8, 64 + i * 28)
                    .size(325, 24));
            page.child(
                action(manager, labels[i] + ".minus", player -> change(player, index + 2, -1)).overlay(
                    IKey.str("−")
                        .color(0xFFFFFF))
                    .pos(339, 64 + i * 28)
                    .size(55, 24)
                    .setEnabledIf(w -> editable() && values[index].getValue() > 0));
            page.child(
                action(manager, labels[i] + ".plus", player -> change(player, index + 2, 1)).overlay(
                    IKey.str("+")
                        .color(0xFFFFFF))
                    .pos(400, 64 + i * 28)
                    .size(55, 24)
                    .setEnabledIf(w -> editable() && parallel.getValue() + speed.getValue() + economy.getValue() < 8));
        }
        page.child(
            new ButtonWidget<>().background(CriticalGuiStyle.BUTTON)
                .hoverBackground(CriticalGuiStyle.HOVER)
                .overlay(
                    IKey.lang(key("recipes"))
                        .color(0xFFFFFF))
                .pos(8, 152)
                .size(447, 24)
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    recipePreview++;
                    return true;
                })
                .tooltipBuilder(t -> {
                    t.add(IKey.lang(key("configuration_hint")));
                    recipeTooltip(t);
                })
                .tooltipAutoUpdate(true));
        return page;
    }

    private boolean editable() {
        return permitted.getValue() && status.getValue() == 0;
    }

    private IWidget constructionPage() {
        ListWidget<IWidget, ?> list = new ListWidget<>().size(451, 174)
            .pos(6, 6);
        constructionLine(list, () -> tr("machine." + configuration().kind.key) + " · " + configuration().tier.name());
        constructionLine(list, () -> tr(loomValid.getValue() ? "loom.ready" : "loom.incomplete"));
        for (String suffix : new String[] { "loom.dimensions", "loom.hatches", "loom.orientation", "loom.area",
            "loom.supply", "loom.clock" }) constructionLine(list, () -> tr(suffix));
        constructionLine(
            list,
            () -> tr("construction") + " " + stage.getValue() + " · " + job.getValue() + " / " + total.getValue());
        constructionLine(
            list,
            () -> tr("construction.elapsed") + " "
                + constructionElapsed.getValue()
                + " t · "
                + tr("construction.remaining")
                + " "
                + Math.max(0, constructionDuration.getValue() - constructionElapsed.getValue())
                + " t");
        constructionLine(list, () -> tr("construction.duration") + " " + constructionDuration.getValue() + " t");
        constructionLine(
            list,
            () -> tr("construction.round") + " "
                + (constructionRounds.getValue() == 0 ? 0
                    : Math.min(constructionRounds.getValue(), constructionRound.getValue() + 1))
                + " / "
                + constructionRounds.getValue()
                + " · "
                + tr(roundFunded.getValue() ? "construction.funded" : "construction.unfunded"));
        constructionLine(
            list,
            () -> tr(
                "construction.round_time") + " " + roundElapsed.getValue() + " / " + roundDuration.getValue() + " t");
        constructionLine(
            list,
            () -> tr("construction_power") + " " + configuration().tier.constructionVoltage() + " EU/t");
        constructionLine(list, () -> tr("construction.stage_cost"));
        constructionLine(list, () -> itemCostLines(constructionCosts));
        constructionLine(list, () -> tr("construction.stage_fluid_cost"));
        constructionLine(
            list,
            () -> stageConstructionFluids.getValue()
                .isEmpty() ? tr("construction.no_stage_fluid") : fluidCostLine(stageConstructionFluids));
        constructionLine(list, () -> tr("construction.solder_hint"));
        constructionLine(list, () -> tr("construction.next_cost"));
        constructionLine(list, () -> itemCostLines(nextConstructionCosts));
        constructionLine(
            list,
            () -> nextConstructionFluids.getValue()
                .isEmpty() ? tr("construction.no_round_fluid") : fluidCostLine(nextConstructionFluids));
        constructionLine(list, () -> tr("construction.missing"));
        constructionLine(list, () -> itemCostLines(missingConstructionCosts));
        constructionLine(list, () -> fluidCostLine(missingConstructionFluids));
        constructionLine(
            list,
            () -> missingConstructionCosts.getValue()
                .isEmpty()
                && missingConstructionFluids.getValue()
                    .isEmpty() ? tr("construction.supplied") : "");
        return new ParentWidget<>().size(463, 186)
            .background(CriticalGuiStyle.CARD)
            .child(list);
    }

    private static void constructionLine(ListWidget<IWidget, ?> list, java.util.function.Supplier<String> text) {
        list.child(
            IKey.dynamic(() -> EnumChatFormatting.WHITE + text.get())
                .color(0xFFFFFF)
                .asWidget()
                .fullWidth()
                .marginBottom(3)
                .setEnabledIf(
                    w -> !text.get()
                        .isEmpty()));
    }

    private static String itemCostLines(GenericListSyncHandler<ItemStack> value) {
        StringBuilder lines = new StringBuilder();
        for (ItemStack stack : value.getValue()) {
            if (stack == null) continue;
            if (lines.length() > 0) lines.append("\n");
            lines.append(stack.getDisplayName())
                .append(" × ")
                .append(stack.stackSize);
        }
        return lines.toString();
    }

    private static String fluidCostLine(GenericListSyncHandler<FluidStack> value) {
        StringBuilder line = new StringBuilder();
        for (FluidStack stack : value.getValue()) {
            if (stack == null) continue;
            if (line.length() > 0) line.append(" · ");
            line.append(stack.getLocalizedName())
                .append(" × ")
                .append(stack.amount)
                .append(" mB");
        }
        return line.toString();
    }

    private void change(EntityPlayer player, int field, int delta) {
        if (state.getState() != 0 || (field == 1 && !multiblock.isKindSelector())) return;
        CriticalConfiguration cfg = state.getConfiguration();
        CriticalTier nextTier = field == 0 ? CriticalTier.byId((cfg.tier.ordinal() + 1) % CriticalTier.values().length)
            : cfg.tier;
        CriticalMachineKind nextKind = field == 1
            ? CriticalMachineKind.byId((cfg.kind.ordinal() + 1) % CriticalMachineKind.values().length)
            : cfg.kind;
        state.configure(
            player,
            nextTier,
            nextKind,
            cfg.parallel + (field == 2 ? delta : 0),
            cfg.speed + (field == 3 ? delta : 0),
            cfg.economy + (field == 4 ? delta : 0));
    }

    private ButtonWidget<?> action(PanelSyncManager manager, String name,
        java.util.function.Consumer<EntityPlayer> command) {
        InteractionSyncHandler handler = new InteractionSyncHandler().setOnMousePressed(data -> {
            if (!baseMetaTileEntity.isServerSide() || data.mouseButton != 0) return;
            EntityPlayer player = manager.getPlayer();
            if (state.canUse(player)) command.accept(player);
        });
        manager.syncValue("critical.action." + name, handler);
        return new ButtonWidget<>().background(CriticalGuiStyle.BUTTON)
            .hoverBackground(CriticalGuiStyle.HOVER)
            .syncHandler(handler)
            .overlay(
                IKey.lang(key(name))
                    .color(0xFFFFFF));
    }

    private IWidget inventoryPage(PanelSyncManager manager) {
        ParentWidget<?> page = new ParentWidget<>().size(463, 186);
        InvWrapper inventory = new InvWrapper(state) {

            private boolean allowed() {
                return !baseMetaTileEntity.isServerSide() || state.canUse(viewer);
            }

            @Override
            public void setStackInSlot(int slot, ItemStack stack) {
                if (allowed()) super.setStackInSlot(slot, stack);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return allowed() ? super.insertItem(slot, stack, simulate) : stack;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return allowed() ? super.extractItem(slot, amount, simulate) : null;
            }
        };
        for (int side = 0; side < 2; side++) {
            int x = side * 235 + 10;
            page.child(
                new ParentWidget<>().pos(side * 235, 0)
                    .size(228, 154)
                    .background(CriticalGuiStyle.CARD));
            page.child(
                IKey.lang(key(side == 0 ? "input" : "output"))
                    .color(0xFFFFFF)
                    .asWidget()
                    .pos(x, 8)
                    .size(208, 14));
            for (int i = 0; i < 18; i++) {
                ModularSlot slot = new ModularSlot(inventory, side * 18 + i) {

                    @Override
                    public boolean canTakeStack(EntityPlayer player) {
                        return (!baseMetaTileEntity.isServerSide() || state.canUse(player))
                            && super.canTakeStack(player);
                    }
                }.canPut(false)
                    .slotGroup(side == 0 ? "critical.inputs" : "critical.outputs");
                page.child(
                    new ItemSlot().slot(slot)
                        .background(CriticalGuiStyle.SLOT)
                        .pos(x + 23 + (i % 9) * 18, 30 + (i / 9) * 18));
            }
            for (int i = 0; i < 8; i++) {
                final int tank = side * 8 + i;
                // Empty tanks are legitimate null values. The convenience forFluid factory infers
                // its value class from the first getter result, so it cannot initialize an empty tank.
                GenericSyncValue<FluidStack, ?> fluid = GenericSyncValue.builder(FluidStack.class)
                    .getter(() -> state.getTankInfo(ForgeDirection.UNKNOWN)[tank].fluid)
                    .adapter(ByteBufAdapters.FLUID_STACK)
                    .copy(FluidStack::copy)
                    .nullable()
                    .build();
                manager.syncValue("critical.fluid." + tank, fluid);
                page.child(
                    new FluidDisplayWidget().value(fluid)
                        .background(CriticalGuiStyle.SLOT)
                        .capacity(CriticalMachineState.TANK_CAPACITY)
                        .pos(x + 32 + i * 18, 84)
                        .size(18));
            }
        }
        page.child(
            IKey.lang(key("fluid_hint"))
                .color(0xFFFFFF)
                .asWidget()
                .pos(0, 80)
                .size(463, 24));
        return page;
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

    private void recipeTooltip(com.cleanroommc.modularui.screen.RichTooltip tooltip) {
        List<RecipeMatch> recipes = CriticalRecipes.list(configuration());
        if (recipes.isEmpty()) return;
        int index = Math.floorMod(recipePreview, recipes.size());
        RecipeMatch recipe = recipes.get(index);
        tooltip.add(tr("recipes") + " " + (index + 1) + " / " + recipes.size());
        tooltip.add(tr("input"));
        appendRecipeStacks(tooltip, recipe.itemInputs, recipe.fluidInputs);
        tooltip.add(tr("output"));
        appendRecipeStacks(tooltip, recipe.itemOutputs, recipe.fluidOutputs);
        tooltip.add(recipe.euPerTick + " EU/t · " + recipe.durationTicks + " t");
        if (recipe.generatedEu > 0) tooltip.add(tr("generated") + " " + recipe.generatedEu + " EU");
        tooltip.add(tr("recipe_hint"));
    }

    private static void appendRecipeStacks(com.cleanroommc.modularui.screen.RichTooltip tooltip, ItemStack[] items,
        FluidStack[] fluids) {
        for (ItemStack stack : items) tooltip.add(stack.getDisplayName() + " × " + stack.stackSize);
        for (FluidStack fluid : fluids) tooltip.add(fluid.getLocalizedName() + " × " + fluid.amount + " mB");
        if (items.length == 0 && fluids.length == 0) tooltip.add("—");
    }

    private static String key(String suffix) {
        return "gtsr.critical." + suffix;
    }

    private static String tr(String suffix) {
        return StatCollector.translateToLocal(key(suffix));
    }
}
