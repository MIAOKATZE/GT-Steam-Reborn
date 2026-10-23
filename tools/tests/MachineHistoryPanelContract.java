package com.miaokatze.gtsr.common.terminal;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;

/** Builds the real client tree, without rendering or dispatching any remote request. */
public final class MachineHistoryPanelContract {
    public static void main(String[] args) throws Exception {
        Path candidate = Path.of(args[0]).toRealPath();
        Path actual = Path.of(MachineHistoryFactory.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!Files.isSameFile(candidate, actual)) throw new AssertionError("Unexpected history factory CodeSource: " + actual);
        Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); access.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) access.get(null);
        EntityPlayerMP player = (EntityPlayerMP) unsafe.allocateInstance(EntityPlayerMP.class);
        World world = (World) unsafe.allocateInstance(WorldServer.class);
        Field remote = World.class.getDeclaredField("isRemote"); remote.setAccessible(true); remote.setBoolean(world, true);
        player.worldObj = world; player.inventory = new InventoryPlayer(player);
        MachineTerminalHistory.Entry entry = new MachineTerminalHistory.Entry(1, 2, 3, 4, 5,
            MachineTerminalHistory.Kind.CRITICAL, "", "", "gt.blockmachines.gtsr.critical.sun.name");
        MachineHistoryFactory.Data data = new MachineHistoryFactory.Data(player, Collections.singletonList(entry), false);
        Field singleton = MachineHistoryFactory.class.getDeclaredField("INSTANCE"); singleton.setAccessible(true);
        MachineHistoryFactory factory = (MachineHistoryFactory) singleton.get(null);
        ModularPanel panel = factory.createPanel(data, new PanelSyncManager(new ModularSyncManager(true), true), new UISettings());
        IWidget row = find(panel, "machine_history_row_0");
        if (row == null || row.getChildren().size() != 1 || !(row.getChildren().get(0) instanceof ParentWidget<?> cells)
            || cells.getChildren().size() != 2)
            throw new AssertionError("History button must retain both name and coordinates as children of its sole content parent");
        System.out.println("PASS MachineHistoryPanelContract real client tree retains both row text lines; no rendering or request dispatch");
    }

    private static IWidget find(IWidget widget, String name) {
        if (name.equals(widget.getName())) return widget;
        for (IWidget child : widget.getChildren()) {
            IWidget found = find(child, name);
            if (found != null) return found;
        }
        return null;
    }
}
