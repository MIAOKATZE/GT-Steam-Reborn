package com.miaokatze.gtsr.common.gui;

import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
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
import gregtech.api.modularui2.GTWidgetThemes;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.modularui2.widget.GTProgressWidget;

/** Handheld terminal only; GT's native container lifecycle owns inventory and NEI integration. */
public final class MTECriticalMachineTerminalGui extends MTEMultiBlockBaseGui<MTECriticalMachine> {

    private final CriticalMachineState state;
    private EntityPlayer viewer;
    private IntSyncValue tier, kind, parallel, speed, economy, status, stage, job, total, batch, duration;
    private LongSyncValue energy, capacity, euPerTick, constructionElapsed, constructionDuration;
    private IntSyncValue constructionRound, constructionRounds, roundElapsed, roundDuration;
    private GenericListSyncHandler<ItemStack> constructionCosts, nextConstructionCosts, missingConstructionCosts;
    private GenericListSyncHandler<FluidStack> stageConstructionFluids, nextConstructionFluids,
        missingConstructionFluids;
    private BooleanSyncValue enabled, permitted, loomValid, roundFunded;
    private StringSyncValue detail;
    private int recipePreview;

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
    protected int getBasePanelWidth() {
        return 350;
    }

    @Override
    protected int getBasePanelHeight() {
        return 212;
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
        PagedWidget<?> pages = new PagedWidget<>().size(338, 100);
        pages.addPage(statusPage(panel, manager));
        pages.addPage(configurationPage(manager));
        pages.addPage(inventoryPage(manager));
        pages.addPage(constructionPage());
        Flow tabs = Flow.row()
            .size(338, 20)
            .childPadding(4);
        String[] labels = { "tab.status", "tab.configuration", "tab.inventory", "tab.requirements" };
        for (int i = 0; i < labels.length; i++) {
            final int page = i;
            tabs.child(
                new ButtonWidget<>().size(81, 18)
                    .overlay(IKey.lang(key(labels[i])))
                    .onMousePressed(button -> {
                        if (button != 0) return false;
                        pages.setPage(page);
                        return true;
                    }));
        }
        return Flow.column()
            .padding(6)
            .child(tabs)
            .child(pages)
            .child(
                SlotGroupWidget.playerInventory(false)
                    .marginTop(4)
                    .alignX(0.5f));
    }

    private IWidget statusPage(ModularPanel panel, PanelSyncManager manager) {
        ParentWidget<?> page = new ParentWidget<>().size(338, 100);
        page.child(
            new ParentWidget<>().pos(0, 0)
                .size(338, 56)
                .padding(4)
                .widgetTheme(GTWidgetThemes.BACKGROUND_TERMINAL)
                .child(createTerminalTextWidget(manager, panel).size(330, 48)));
        page.child(
            action(manager, "build", player -> state.startConstruction(player)).pos(0, 60)
                .size(80, 18)
                .setEnabledIf(w -> permitted.getValue() && loomValid.getValue() && status.getValue() == 0)
                .tooltipBuilder(this::constructionTooltip)
                .tooltipAutoUpdate(true));
        page.child(
            action(manager, "dismantle", player -> state.startDismantle(player)).pos(86, 60)
                .size(80, 18)
                .setEnabledIf(w -> permitted.getValue() && (status.getValue() == 1 || status.getValue() == 2)));
        page.child(
            action(manager, "repair", player -> state.repairStructure(player)).pos(172, 60)
                .size(80, 18)
                .setEnabledIf(w -> permitted.getValue() && loomValid.getValue() && status.getValue() != 0)
                .tooltipBuilder(this::constructionTooltip)
                .tooltipAutoUpdate(true));
        page.child(
            action(manager, "power", player -> state.setEnabled(player, !state.isEnabled()))
                .overlay(IKey.dynamic(() -> tr(enabled.getValue() ? "stop" : "start")))
                .pos(258, 60)
                .size(80, 18)
                .setEnabledIf(w -> permitted.getValue() && (enabled.getValue() || loomValid.getValue())));
        page.child(
            IKey.lang(key("recipes"))
                .asWidget()
                .pos(0, 84)
                .size(80, 16));
        page.child(
            new GTProgressWidget().neiTransferRect(multiblock.getRecipeMap())
                .texture(GTGuiTextures.PROGRESSBAR_ARROW_STANDARD, 20)
                .progress(() -> duration.getValue() <= 0 ? 0 : (double) batch.getValue() / duration.getValue())
                .pos(86, 84)
                .size(252, 16));
        return page;
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager manager, ModularPanel parent) {
        ListWidget<IWidget, ?> list = new ListWidget<>();
        list.child(
            IKey.dynamic(() -> tr("machine." + configuration().kind.key) + " · " + configuration().tier.name())
                .asWidget()
                .fullWidth()
                .marginBottom(3));
        list.child(
            IKey.dynamic(() -> tr("status." + status.getValue()) + " · " + detail.getValue())
                .asWidget()
                .fullWidth()
                .marginBottom(3));
        list.child(
            IKey.dynamic(() -> "EU " + energy.getValue() + " / " + capacity.getValue())
                .asWidget()
                .fullWidth()
                .marginBottom(3));
        list.child(
            IKey.dynamic(
                () -> tr("construction") + " " + stage.getValue() + " · " + job.getValue() + " / " + total.getValue())
                .asWidget()
                .fullWidth()
                .marginBottom(3));
        list.child(
            IKey.dynamic(() -> tr("batch") + " " + batch.getValue() + " / " + duration.getValue() + " t")
                .asWidget()
                .fullWidth()
                .marginBottom(3));
        list.child(
            IKey.dynamic(() -> euPerTick.getValue() + " EU/t")
                .asWidget()
                .fullWidth()
                .marginBottom(3));
        list.child(
            IKey.dynamic(() -> tr("construction_power") + " " + configuration().tier.constructionVoltage() + " EU/t")
                .asWidget()
                .fullWidth());
        return list;
    }

    private IWidget configurationPage(PanelSyncManager manager) {
        ParentWidget<?> page = new ParentWidget<>().size(338, 100);
        page.child(
            action(manager, "tier", player -> change(player, 0, 1))
                .overlay(IKey.dynamic(() -> configuration().tier.name()))
                .pos(0, 0)
                .size(70, 18)
                .setEnabledIf(w -> editable())
                .tooltipBuilder(t -> t.add(IKey.lang(key("configuration_hint")))));
        page.child(
            action(manager, "kind", player -> change(player, 1, 1))
                .overlay(IKey.dynamic(() -> tr("machine." + configuration().kind.key)))
                .pos(76, 0)
                .size(262, 18)
                .setEnabledIf(w -> editable() && multiblock.isKindSelector())
                .tooltipBuilder(t -> t.add(IKey.lang(key("configuration_hint")))));
        String[] labels = { "parallel", "speed", "economy" };
        IntSyncValue[] values = { parallel, speed, economy };
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            page.child(
                IKey.dynamic(() -> tr(labels[index]) + " " + values[index].getValue())
                    .asWidget()
                    .pos(0, 22 + i * 20)
                    .size(224, 18));
            page.child(
                action(manager, labels[i] + ".minus", player -> change(player, index + 2, -1)).overlay(IKey.str("−"))
                    .pos(230, 22 + i * 20)
                    .size(50, 18)
                    .setEnabledIf(w -> editable() && values[index].getValue() > 0));
            page.child(
                action(manager, labels[i] + ".plus", player -> change(player, index + 2, 1)).overlay(IKey.str("+"))
                    .pos(286, 22 + i * 20)
                    .size(52, 18)
                    .setEnabledIf(w -> editable() && parallel.getValue() + speed.getValue() + economy.getValue() < 8));
        }
        page.child(
            new ButtonWidget<>().overlay(IKey.lang(key("recipes")))
                .pos(0, 82)
                .size(110, 18)
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
        ListWidget<IWidget, ?> list = new ListWidget<>().size(338, 100);
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
        return list;
    }

    private static void constructionLine(ListWidget<IWidget, ?> list, java.util.function.Supplier<String> text) {
        list.child(
            IKey.dynamic(text)
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
        return new ButtonWidget<>().syncHandler(handler)
            .overlay(IKey.lang(key(name)));
    }

    private IWidget inventoryPage(PanelSyncManager manager) {
        ParentWidget<?> page = new ParentWidget<>().size(338, 100);
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
            int x = side * 176;
            page.child(
                IKey.lang(key(side == 0 ? "input" : "output"))
                    .asWidget()
                    .pos(x, 0)
                    .size(162, 14));
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
                        .pos(x + (i % 9) * 18, 16 + (i / 9) * 18));
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
                        .capacity(CriticalMachineState.TANK_CAPACITY)
                        .pos(x + i * 18, 58)
                        .size(18));
            }
        }
        page.child(
            IKey.lang(key("fluid_hint"))
                .asWidget()
                .pos(0, 80)
                .size(338, 20));
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
