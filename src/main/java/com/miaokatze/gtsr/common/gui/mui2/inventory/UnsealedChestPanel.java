package com.miaokatze.gtsr.common.gui.mui2.inventory;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiPalette;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntityUnsealedChest;
import com.miaokatze.gtsr.common.gui.mui2.TerminalUiStyle;

public final class UnsealedChestPanel {

    private UnsealedChestPanel() {}

    /** Production builder; the caller owns permissions and uses this same live Tile inventory. */
    public static ModularPanel build(PosGuiData data, TileEntityUnsealedChest chest, PanelSyncManager manager,
        UISettings settings) {
        settings.customContainer(() -> new UnsealedChestModularContainer(chest));
        manager.registerSlotGroup(new SlotGroup("unsealed_storage", 9).setAllowSorting(false));
        InvWrapper storage = new InvWrapper(chest);
        ModularPanel panel = ModularPanel.defaultPanel(UnsealedChestFactory.PANEL_NAME, 194, 240)
            .background(TerminalUiStyle.PANEL);
        panel.child(
            IKey.lang("container.gtsr.unsealedChest")
                .asWidget()
                .name("unsealed_title")
                .pos(12, 8)
                .size(170, 14)
                .color(GtsrGuiPalette.TEXT_TITLE));
        for (int n = 0; n < 54; n++) {
            ModularSlot slot = new PureInventorySlot(storage, n).slotGroup("unsealed_storage");
            slot.changeListener((stack, amount, client, init) -> { if (!client && !init) chest.markDirty(); });
            manager.itemSlot("unsealed_chest", n, slot);
            panel.child(
                new ItemSlot().syncHandler("unsealed_chest", n)
                    .name("unsealed_slot_" + n)
                    .pos(16 + n % 9 * 18, 28 + n / 9 * 18));
        }
        InventorySlots.registerPlayer(manager, data.getPlayer());
        panel.child(
            InventorySlots.playerWidgets()
                .name("unsealed_player_inventory")
                .pos(16, 150));
        return panel;
    }
}
