package com.miaokatze.gtsr.common.gui.mui2.worker;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiPalette;
import com.miaokatze.gtsr.common.gui.mui2.TerminalUiStyle;
import com.miaokatze.gtsr.common.gui.mui2.TerminalViewport;
import com.miaokatze.gtsr.common.gui.mui2.hub.HubClientText;
import com.miaokatze.gtsr.common.gui.mui2.inventory.InventorySlots;
import com.miaokatze.gtsr.common.gui.mui2.inventory.PureInventorySlot;
import com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode;

/** Three existing machine slots plus the real player inventory. There is no temporary UI-owned storage. */
public final class RemoteWorkerNodePanel {

    public static final String PANEL_NAME = "remote_worker_node";

    private RemoteWorkerNodePanel() {}

    public static ModularPanel build(MTERemoteWorkerNode machine, PosGuiData data, PanelSyncManager manager,
        UISettings settings) {
        WorkerUiSession session = new WorkerUiSession(data, machine);
        manager.syncValue(WorkerUiSession.SYNC_KEY, session);
        settings.canInteractWith(player -> session.canUse());
        com.cleanroommc.modularui.value.sync.GenericSyncValue<ItemStack, ?> upgradeItem = com.cleanroommc.modularui.value.sync.GenericSyncValue
            .forItem(
                () -> manager.isClient() ? null : machine.getWorkerUpgradeItem(machine.getWorkerUiSnapshot().tier + 1),
                null);
        com.cleanroommc.modularui.value.sync.IntSyncValue upgradeCost = new com.cleanroommc.modularui.value.sync.IntSyncValue(
            () -> manager.isClient() ? 0 : machine.getWorkerUpgradeCost(machine.getWorkerUiSnapshot().tier + 1));
        manager.syncValue("worker_upgrade_item", upgradeItem);
        manager.syncValue("worker_upgrade_cost", upgradeCost);
        com.cleanroommc.modularui.utils.item.IItemHandler inventory = machine == null
            ? new com.cleanroommc.modularui.utils.item.ItemStackHandler(3)
            : machine.inventoryHandler;
        manager.registerSlotGroup("worker_pipes", 3);
        for (int i = 0; i < 3; i++) {
            ModularSlot slot = new PureInventorySlot(inventory, i).slotGroup("worker_pipes");
            manager.itemSlot("worker_pipe", i, slot);
        }
        // Explicit registration precedes widget collection on both sides, so indices are always 0..2, then main/hotbar.
        InventorySlots.registerPlayer(manager, data.getPlayer());
        Flow top = Flow.col()
            .name("worker_content")
            .pos(8, 8)
            .size(224, 176)
            .childPadding(3);
        top.child(
            IKey.dynamic(
                () -> session.snapshot
                    == null
                        ? machine == null ? "" : machine.getLocalName()
                        : session.snapshot.title.isEmpty()
                            ? IKey
                                .lang(
                                    session.snapshot.miner ? "gtsr.drilling.node_miner" : "gtsr.drilling.node_driller")
                                .getFormatted()
                            : session.snapshot.title)
                .asWidget()
                .color(GtsrGuiPalette.TEXT_BODY)
                .name("worker_title")
                .color(GtsrGuiPalette.TEXT_TITLE)
                .size(224, 18));
        top.child(
            Flow.row()
                .size(224, 20)
                .childPadding(10)
                .child(
                    SlotGroupWidget.builder()
                        .matrix("III")
                        .key(
                            'I',
                            index -> new ItemSlot().syncHandler("worker_pipe", index)
                                .name("worker_pipe_" + index))
                        .slotGroup("worker_pipes")
                        .build())
                .child(
                    TerminalUiStyle.button("gtsr.mui2.hub.upgrade")
                        .name("worker_upgrade")
                        .size(100, 18)
                        .setEnabledIf(w -> !session.awaiting)
                        .onMousePressed(button -> {
                            if (button == 0) session.upgrade();
                            return true;
                        })
                        .tooltipBuilder(t -> {
                            WorkerUiSnapshot snapshot = session.snapshot;
                            t.addLine(IKey.lang("gtsr.gui.node_upgrade.tooltip.title"));
                            int tier = snapshot == null ? 0 : snapshot.tier;
                            ItemStack item = upgradeItem.getValue();
                            if (item == null) t.addLine(IKey.lang("gtsr.gui.node_upgrade.tooltip.max"));
                            else {
                                t.addLine(
                                    IKey.str(
                                        IKey.lang("gtsr.gui.node_upgrade.tooltip.next")
                                            .getFormatted() + " "
                                            + (tier + 1)));
                                t.addLine(IKey.str(item.getDisplayName()));
                                t.addLine(
                                    IKey.str(
                                        IKey.lang("gtsr.gui.node_upgrade.tooltip.singularity")
                                            .getFormatted() + " "
                                            + upgradeCost.getIntValue()));
                            }
                        })
                        .tooltipAutoUpdate(true)));
        top.child(
            IKey.dynamicKey(
                () -> IKey.lang(session.snapshot == null ? "gtsr.node.status.unknown" : session.snapshot.statusKey))
                .asWidget()
                .color(GtsrGuiPalette.TEXT_BODY)
                .name("worker_status")
                .size(224, 16));
        top.child(
            IKey.dynamic(
                () -> IKey.lang("gtsr.node.depth")
                    .getFormatted() + ": "
                    + (session.snapshot == null ? "—" : session.snapshot.depth))
                .asWidget()
                .color(GtsrGuiPalette.TEXT_BODY)
                .name("worker_depth")
                .size(224, 14));
        top.child(IKey.dynamic(() -> {
            WorkerUiSnapshot snapshot = session.snapshot;
            return IKey
                .lang(snapshot != null && snapshot.miner ? "gtsr.node.pipes_retracted" : "gtsr.node.driller_bedrock")
                .getFormatted() + ": "
                + IKey.lang(snapshot != null && snapshot.flag ? "gtsr.node.yes" : "gtsr.node.no")
                    .getFormatted();
        })
            .asWidget()
            .color(GtsrGuiPalette.TEXT_BODY)
            .name("worker_flag")
            .size(224, 14));
        top.child(IKey.dynamic(() -> {
            WorkerUiSnapshot snapshot = session.snapshot;
            if (snapshot == null) return "—";
            String prefix = snapshot.miner ? "gtsr.gui.miner_node." : "gtsr.gui.drilling_node.";
            return IKey.lang(prefix + "tier")
                .getFormatted() + " "
                + IKey.lang(prefix + (snapshot.tier == 0 ? "base" : "enhanced"))
                    .getFormatted()
                + (snapshot.tier == 0 ? "" : " " + roman(snapshot.tier));
        })
            .asWidget()
            .color(GtsrGuiPalette.TEXT_BODY)
            .name("worker_tier")
            .size(224, 14));
        top.child(IKey.dynamic(() -> {
            WorkerUiSnapshot snapshot = session.snapshot;
            if (snapshot == null) return "—";
            if (snapshot.miner) return IKey.lang("gtsr.gui.miner_node.crushed_mode")
                .getFormatted() + ": "
                + IKey.lang(
                    snapshot.crushed ? "gtsr.gui.miner_node.crushed_mode.on" : "gtsr.gui.miner_node.crushed_mode.off")
                    .getFormatted();
            return HubClientText.clip(
                snapshot.fluidName + " "
                    + snapshot.displayRate
                    + " "
                    + IKey.lang("gtsr.gui.drilling_node.output_per_8s")
                        .getFormatted(),
                220);
        })
            .asWidget()
            .color(GtsrGuiPalette.TEXT_BODY)
            .name("worker_extra")
            .size(224, 14));
        top.child(
            IKey.lang("gtsr.gui.node.hub_status_hint")
                .asWidget()
                .color(GtsrGuiPalette.TEXT_BODY)
                .name("worker_hub_hint")
                .size(224, 22)
                .scale(0.8f));
        top.child(
            IKey.dynamicKey(() -> IKey.lang(session.receiptKey))
                .asWidget()
                .color(GtsrGuiPalette.TEXT_BODY)
                .name("worker_receipt")
                .color(GtsrGuiPalette.TEXT_ACCENT)
                .size(224, 14));
        ParentWidget<?> body = new ParentWidget<>().name("worker_body")
            .pos(0, 0)
            .size(240, 274)
            .child(top)
            .child(
                InventorySlots.playerWidgets()
                    .pos(39, 190));
        return ModularPanel.defaultPanel(PANEL_NAME, 240, 274)
            .background(TerminalUiStyle.PANEL)
            .child(
                TerminalViewport.wrap("worker_viewport", body, 240, 274)
                    .sizeRel(1f))
            .onUpdateListener(panel -> session.consume());
    }

    private static String roman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> Integer.toString(tier);
        };
    }
}
