package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;

import com.cleanroommc.modularui.api.RecipeViewerSettings;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntityUnsealedChest;
import com.miaokatze.gtsr.common.gui.mui2.inventory.UnsealedChestFactory;
import com.miaokatze.gtsr.common.gui.mui2.inventory.UnsealedChestPanel;
import com.mojang.authlib.GameProfile;

/** Unplaced real chest inventory: no loot, viewers, open route, ticking tile or copied storage. */
final class TestLabUiChestTask implements TestLabTask {

    private TestLabReport.Result result;

    public String id() {
        return "ui.terminal.unsealed_chest";
    }

    public String displayName() {
        return "Unsealed chest production inventory panel";
    }

    public int timeoutTicks() {
        return 100;
    }

    public boolean requiresFixtureChunks() {
        return false;
    }

    public void start(WorldServer world, int x, int y, int z) {
        if (!(world.provider instanceof TestLabWorldProvider))
            throw new IllegalArgumentException("Not a test lab world");
        Map<String, Object> details = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        details.put("probeCategory", "UI_SERVER");
        details.put("productionFactory", UnsealedChestFactory.FACTORY_NAME);
        details.put("fixture", "UNPLACED_REAL_TILE_INVENTORY_WITHOUT_REWARD_OR_VIEWERS");
        details.put(
            "actualOpenEvidence",
            "NOT_RUN: production builder only; never invokes factory.open or bypasses permissions");
        details.put(
            "slotTransactionEvidence",
            "NOT_RUN: real connected player/container, shift/numeric/drag/cursor transactions");
        details.put("lifecycleBoundary", "NOT_RUN: lid/viewer, disconnect, range, destruction and legacy NBT loading");
        details.put("renderEvidence", "NOT_RUN: real Minecraft GL, font, input and full client layout");
        try {
            EntityPlayer player = FakePlayerFactory.get(world, new GameProfile(TestLabFixtures.OWNER, "GTSR UI Probe"));
            TileEntityUnsealedChest chest = new TileEntityUnsealedChest();
            chest.setWorldObj(world);
            chest.xCoord = x;
            chest.yCoord = y;
            chest.zCoord = z;
            NBTTagCompound before = new NBTTagCompound();
            chest.writeToNBT(before);
            TestLabUiSnapshot.Manager manager = new TestLabUiSnapshot.Manager(player);
            UISettings settings = new UISettings(RecipeViewerSettings.DUMMY);
            long timing = TestLabPerformance.begin();
            ModularPanel panel;
            try {
                panel = UnsealedChestPanel.build(new PosGuiData(player, x, y, z), chest, manager, settings);
            } finally {
                TestLabPerformance.end("ui.mui2ProductionBuilder", timing);
            }
            details.put(
                "productionBuilderEvidence",
                "PASS: actual production UnsealedChestPanel.build returned real panel");
            details.put("actualPanelName", panel.getName());
            if (!UnsealedChestFactory.PANEL_NAME.equals(panel.getName())) failures.add("Chest panel name mismatch");
            TestLabUiSnapshot.inspect(panel, manager, null, details, failures);
            long storage = manager.handlers.keySet()
                .stream()
                .filter(key -> key.startsWith("unsealed_chest:"))
                .count();
            long inventory = manager.handlers.keySet()
                .stream()
                .filter(key -> key.startsWith("player:"))
                .count();
            details.put("registeredStorageSlots", storage);
            details.put("registeredPlayerSlots", inventory);
            if (storage != 54 || inventory != 36)
                failures.add("Chest production slots expected 54+36, got " + storage + "+" + inventory);
            NBTTagCompound after = new NBTTagCompound();
            chest.writeToNBT(after);
            details.put("fixtureNbtUnchanged", before.equals(after));
            if (!before.equals(after)) failures.add("Chest builder probe mutated persistent inventory/reward state");
            details.put("failures", failures);
            result = new TestLabReport.Result(
                failures.isEmpty() ? TestLabReport.Status.PASS : TestLabReport.Status.FAIL,
                failures.isEmpty()
                    ? "Production chest tree/sync/slot registration passed; transactions and client rendering NOT_RUN"
                    : String.join("; ", failures),
                details);
        } catch (LinkageError error) {
            TestLabUiTask.recordError(details, "linkageError", error);
            result = new TestLabReport.Result(
                TestLabReport.Status.UNSUPPORTED,
                "Production chest panel cannot link on dedicated server; actual open NOT_RUN",
                details);
        } catch (Exception | AssertionError error) {
            TestLabUiTask.recordError(details, "probeError", error);
            result = new TestLabReport.Result(
                TestLabReport.Status.FAIL,
                "Production chest panel probe failed: " + error,
                details);
        }
    }

    public TestLabReport.Result tick(WorldServer world, int elapsedTicks) {
        return result;
    }

    public void stop(WorldServer world) {}
}
