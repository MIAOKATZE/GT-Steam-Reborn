package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import cpw.mods.fml.common.eventhandler.Event.Result;

/** Real production interaction listener and GT ItemMachines; no game placement/network emulation. */
public final class CriticalPortPlacementContract {
    private static final CriticalRuntime.StructureChangeListener LISTENER = new CriticalRuntime.StructureChangeListener();

    private static final class Player extends EntityPlayerMP {
        ItemStack held;
        private Player() { super(null, null, null, null); }
        @Override public ItemStack getHeldItem() { return held; }
    }

    // An untransformed JVM does not install Forge's generated isCancelable override.
    private static final class Interaction extends PlayerInteractEvent {
        Interaction(Player player, Action action, int x, int y, int z, int face, SparseWorld world) {
            super(player, action, x, y, z, face, world);
        }
        @Override public boolean isCancelable() { return true; }
    }

    private static Interaction interact(Player player, SparseWorld world, int x, int y, int z, int face,
        PlayerInteractEvent.Action action) {
        Interaction event = new Interaction(player, action, x, y, z, face, world);
        LISTENER.onPlayerInteract(event);
        return event;
    }

    private static void run() throws Exception {
        Player player = (Player) unsafe.allocateInstance(Player.class);
        ItemStack chamber = new ItemStack(new gregtech.common.blocks.ItemMachines(AIR), 1, 30002);
        check(chamber.getItem() instanceof ItemBlock, "GT205 ItemMachines uses normal ItemBlock placement");
        check(chamber.getItem().getClass().getMethod("onItemUseFirst", ItemStack.class,
            net.minecraft.entity.player.EntityPlayer.class, net.minecraft.world.World.class,
            int.class, int.class, int.class, int.class, float.class, float.class, float.class)
            .getDeclaringClass() == Item.class, "GT205 ItemMachines inherits vanilla onItemUseFirst");
        for (ForgeDirection facing : new ForgeDirection[] {
            ForgeDirection.NORTH, ForgeDirection.EAST, ForgeDirection.SOUTH, ForgeDirection.WEST }) {
            SparseWorld world = world();
            CriticalMachineState state = state(world, 31, facing);
            List<CriticalGeometry.Voxel> cells = geometry(state);
            List<CriticalGeometry.Voxel> ports = CriticalGeometry.optionalWorkPorts(state);
            check(ports.size() == 13, "thirteen optional work ports " + facing);
            for (CriticalGeometry.Voxel port : ports) {
                CriticalGeometry.Voxel paid = cells.stream()
                    .filter(v -> v.x == port.x && v.y == port.y - 1 && v.z == port.z)
                    .findFirst().orElseThrow();
                put(state, paid, paid.block);
                ((BitSet) field(state, "placed")).set(cells.indexOf(paid));
            }
            CriticalWorldData data = CriticalWorldData.get(world);
            check(data.acquire(state.getStructureId(), "owner", state.getOriginX(), state.getOriginY(),
                state.getOriginZ(), state.getRegionSize(), state.getRegionHeight(), state.xCoord, state.yCoord,
                state.zCoord), "claim acquired " + facing);
            check(data.bindTask(state.getStructureId(), "owner", state.getJobId(), state.getOriginX(),
                state.getOriginY(), state.getOriginZ()), "paid task bound " + facing);
            data.updateProtection(state);
            for (CriticalGeometry.Voxel port : ports) {
                int x = state.blockWorldX(port), y = state.blockWorldY(port.y), z = state.blockWorldZ(port);
                check(world.isAirBlock(x, y, z), "optional port starts as air " + facing + " x=" + port.x);
                check(CriticalBlockProtection.protects(world, x, y - 1, z), "paid support is protected");
                player.held = chamber;
                check(!chamber.getItem().onItemUseFirst(chamber, player, world, x, y - 1, z, 1, .5F, 1F, .5F),
                    "GT ItemMachines does not intercept item-use-first before placement");
                Interaction event = interact(player, world, x, y - 1, z, 1, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK);
                check(!event.isCanceled(), "GT chamber placement on paid shell must not cancel " + facing + " x=" + port.x);
                check(event.useBlock == Result.DENY && event.useItem == Result.ALLOW,
                    "Forge server reaches ItemBlock placement even without sneaking");
                check(CriticalBlockProtection.protects(world, x, y - 1, z), "placing permission retains paid support");
                player.held = null;
                check(interact(player, world, x, y - 1, z, 1, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK).isCanceled(),
                    "empty hand cannot interact with paid material");
                player.held = new ItemStack(new Item());
                check(interact(player, world, x, y - 1, z, 1, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK).isCanceled(),
                    "non-block tools remain blocked");
                player.held = chamber;
                check(interact(player, world, x, y - 1, z, 1, PlayerInteractEvent.Action.LEFT_CLICK_BLOCK).isCanceled(),
                    "holding chamber does not allow paid shell breaking");
                check(interact(player, world, x, y - 1, z, -1, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK).isCanceled(),
                    "invalid placement face stays blocked");
                world.blocks.put(key(x, y, z), world.getBlock(x, y - 1, z));
                check(interact(player, world, x, y - 1, z, 1, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK).isCanceled(),
                    "occupied neighboring cell cannot be replaced");
                world.blocks.remove(key(x, y, z));
                Interaction denied = new Interaction(player, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, x, y - 1, z, 1, world);
                denied.useItem = Result.DENY;
                LISTENER.onPlayerInteract(denied);
                check(denied.useItem == Result.DENY, "other item-use denial is preserved");
                Interaction canceled = new Interaction(player, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, x, y - 1, z, 1, world);
                canceled.setCanceled(true);
                LISTENER.onPlayerInteract(canceled);
                check(canceled.isCanceled() && canceled.useItem == Result.DENY,
                    "previous cancellation cannot be reopened");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        JsonObject report = new JsonObject();
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (sun.misc.Unsafe) field.get(null);
            materials();
            run();
            report.addProperty("status", "PASS");
        } catch (Throwable failure) {
            report.addProperty("status", "FAIL");
            report.addProperty("failure", failure.toString());
            throw failure;
        } finally {
            report.addProperty("checks", checks);
            report.addProperty("runtimeCodeSource", CriticalRuntime.class.getProtectionDomain().getCodeSource().getLocation().toString());
            report.addProperty("gtItemCodeSource", gregtech.common.blocks.ItemMachines.class.getProtectionDomain().getCodeSource().getLocation().toString());
            report.addProperty("boundary", "Production Forge listener with real GT ItemMachines and protected ledger in sparse World; no actual ItemStack placement, game server, client prediction, or packets");
            Files.writeString(Path.of(args[0]), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }
    }
}
