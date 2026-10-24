package com.miaokatze.gtsr.common.terminal;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Hashtable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.miaokatze.gtsr.common.gui.mui2.Mui2ServerActions;
import com.miaokatze.gtsr.common.items.HubTerminal;

import cpw.mods.fml.common.gameevent.TickEvent;

/** Runs the real C08 handler, including its post-use stack copy; no GL or network session fixture. */
public final class MachineHistoryItemUseRegression {
    private static sun.misc.Unsafe unsafe;
    private static MachineHistoryFactory.Data opened;
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void field(Object object, Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    public static void main(String[] args) throws Exception {
        Thread.currentThread().setName("Server thread");
        Path candidate = Path.of(args[0]).toRealPath();
        Path actual = Path.of(MachineHistoryFactory.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!Files.isSameFile(candidate, actual)) throw new AssertionError("Unexpected factory CodeSource " + actual);
        Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); access.setAccessible(true);
        unsafe = (sun.misc.Unsafe) access.get(null);
        field(null, cpw.mods.fml.common.Loader.class, "instance", unsafe.allocateInstance(cpw.mods.fml.common.Loader.class));
        Object side = Proxy.newProxyInstance(cpw.mods.fml.common.IFMLSidedHandler.class.getClassLoader(),
            new Class<?>[] { cpw.mods.fml.common.IFMLSidedHandler.class }, (proxy, method, values) ->
                method.getName().equals("getSide") ? cpw.mods.fml.relauncher.Side.SERVER : null);
        field(cpw.mods.fml.common.FMLCommonHandler.instance(), cpw.mods.fml.common.FMLCommonHandler.class, "sidedDelegate", side);
        Player player = (Player) unsafe.allocateInstance(Player.class);
        WorldServer world = (WorldServer) unsafe.allocateInstance(WorldServer.class);
        player.worldObj = world; player.dimension = -704209; player.inventory = new InventoryPlayer(player);
        Item item = (Item) unsafe.allocateInstance(HubTerminal.class);
        Object delegate = Proxy.newProxyInstance(cpw.mods.fml.common.registry.RegistryDelegate.class.getClassLoader(),
            new Class<?>[] { cpw.mods.fml.common.registry.RegistryDelegate.class }, (proxy, method, values) ->
                method.getName().equals("get") ? item : null);
        field(item, Item.class, "delegate", delegate);
        ItemStack terminal = new ItemStack(item);
        player.inventory.mainInventory[0] = terminal;
        Slots inventory = new Slots(player);
        player.openContainer = inventory;
        Use manager = new Use(world);
        field(player, EntityPlayerMP.class, "theItemInWorldManager", manager);
        Handler handler = (Handler) unsafe.allocateInstance(Handler.class);
        handler.playerEntity = player; player.playerNetServerHandler = handler;
        field(handler, NetHandlerPlayServer.class, "serverController", unsafe.allocateInstance(IntegratedServer.class));
        Field worlds = DimensionManager.class.getDeclaredField("worlds"); worlds.setAccessible(true);
        @SuppressWarnings("unchecked") Hashtable<Integer, WorldServer> registry = (Hashtable<Integer, WorldServer>) worlds.get(null);
        registry.put(player.dimension, world);
        try {
            for (int round = 0; round < 10; round++) {
                opened = null;
                ItemStack before = player.getHeldItem();
                handler.processPlayerBlockPlacement(new C08PacketPlayerBlockPlacement(0, 0, 0, 255, before, 0, 0, 0));
                check(player.getHeldItem() != before, "Real C08 must replace the held ItemStack after right-click");
                new Mui2ServerActions.Drain().tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
                check(opened != null && opened.valid(player), "History session must remain valid after real C08 post-use stack replacement");
                for (int repeat = 0; repeat < 600; repeat++) check(opened.valid(player), "Stable session validity must not decay");
                player.inventory.currentItem = 1;
                check(!opened.valid(player), "Switching slot invalidates the issued session");
                player.inventory.currentItem = 0;
                ItemStack issuedStack = player.getHeldItem();
                player.inventory.mainInventory[0] = issuedStack.copy();
                check(!opened.valid(player), "A same-NBT replacement after opening must not inherit the session");
                player.inventory.mainInventory[0] = issuedStack;
                issuedStack.getTagCompound().setString("gtsr.terminalId", "changed");
                check(!opened.valid(player), "Changing terminal identity invalidates the session");
                player.inventory.mainInventory[0] = new ItemStack(item);
            }
            opened = null;
            MachineHistoryFactory.open(player, data -> opened = data);
            player.inventory.currentItem = 1;
            new Mui2ServerActions.Drain().tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
            check(opened == null, "A pending open must not follow a player to another slot");
            player.inventory.currentItem = 0;
            MachineHistoryFactory.open(player, data -> opened = data);
            player.openContainer = new Slots(player);
            new Mui2ServerActions.Drain().tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
            check(opened == null, "A pending open must not replace a newer container");
        } finally {
            registry.remove(player.dimension);
            Mui2ServerActions.clear();
        }
        System.out.println("PASS MachineHistoryItemUseRegression checks=" + checks + " real C08 x10, 600 validity checks/open, slot/stack/identity invalidation");
    }

    private static final class Player extends EntityPlayerMP {
        Player() { super(null, null, null, null); }
        @Override public NBTTagCompound getEntityData() {
            if (data == null) data = new NBTTagCompound();
            return data;
        }
        private NBTTagCompound data;
        @Override public void func_143004_u() {}
    }

    private static final class Handler extends NetHandlerPlayServer {
        Handler() { super(null, null, null); }
        @Override public void sendPacket(Packet packet) {}
    }

    private static final class Use extends ItemInWorldManager {
        Use(World world) { super(world); }
        @Override public boolean tryUseItem(EntityPlayer player, World world, ItemStack stack) {
            MachineHistoryFactory.open(player, data -> opened = data);
            return false;
        }
    }

    private static final class Slots extends Container {
        Slots(EntityPlayer player) { addSlotToContainer(new Slot(player.inventory, 0, 0, 0)); }
        @Override public boolean canInteractWith(EntityPlayer player) { return true; }
        @Override public void detectAndSendChanges() {}
    }
}
