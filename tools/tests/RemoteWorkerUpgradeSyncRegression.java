import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.GenericSyncValue;

import io.netty.buffer.Unpooled;

/** Native sync-value regression. Machine and side fixtures only; no game, UI or network startup. */
public final class RemoteWorkerUpgradeSyncRegression {
    private static int checks;

    public static final class Manager {
        private final boolean client;

        Manager(boolean client) {
            this.client = client;
        }

        public boolean isClient() {
            return client;
        }
    }

    public static final class Snapshot {
        public int tier;
    }

    public static final class Machine {
        final Snapshot snapshot = new Snapshot();
        ItemStack nextItem;
        boolean forbidden;
        int snapshotReads, itemReads, requestedTier;

        public Snapshot getWorkerUiSnapshot() {
            if (forbidden) throw new AssertionError("Client accessed server snapshot");
            snapshotReads++;
            return snapshot;
        }

        public ItemStack getWorkerUpgradeItem(int tier) {
            if (forbidden) throw new AssertionError("Client accessed server upgrade item");
            itemReads++;
            requestedTier = tier;
            return nextItem;
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void transfer(GenericSyncValue<ItemStack, ?> server, GenericSyncValue<ItemStack, ?> client)
        throws Exception {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            server.write(packet);
            client.read(packet);
            check(packet.readableBytes() == 0, "Native serializer/deserializer consume the exact payload");
        } finally {
            packet.release();
        }
    }

    public static void main(String[] args) throws Exception {
        Machine clientMachine = new Machine();
        clientMachine.forbidden = true;
        GenericSyncValue<ItemStack, ?> client = UpgradeItemSyncCallsite.create(clientMachine, new Manager(true));
        check(client.getValue() == null, "Client can construct with null before the first server packet");
        check(client.getValueType() == ItemStack.class, "Null initial value retains explicit ItemStack type");
        check(!client.updateCacheFromSource(false), "Unchanged client null compares equal");
        check(clientMachine.snapshotReads == 0 && clientMachine.itemReads == 0, "Client never reads server getters");
        check(UpgradeItemSyncCallsite.create(null, new Manager(true)).getValue() == null,
            "Client construction does not require a server machine");

        Machine drillerMaximum = new Machine();
        drillerMaximum.snapshot.tier = 3;
        GenericSyncValue<ItemStack, ?> driller = UpgradeItemSyncCallsite.create(drillerMaximum, new Manager(false));
        check(driller.getValue() == null && drillerMaximum.requestedTier == 4,
            "Driller maximum tier preserves null and requests tier four");

        Machine machine = new Machine();
        machine.snapshot.tier = 4;
        GenericSyncValue<ItemStack, ?> server = UpgradeItemSyncCallsite.create(machine, new Manager(false));
        check(server.getValue() == null && machine.requestedTier == 5, "Server maximum-tier null constructs safely");
        check(server.updateCacheFromSource(true), "First null sync is emitted");
        transfer(server, client);
        check(client.getValue() == null, "Initial null packet round-trips");
        check(!server.updateCacheFromSource(false), "Unchanged server null is not a dirty value");

        Item fixtureItem = new Item();
        // Give the native packet codec a registry identity without bootstrapping FML or Minecraft.
        java.lang.reflect.Method register = Item.itemRegistry.getClass()
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        register.setAccessible(true);
        register.invoke(Item.itemRegistry, 32000, "gtsr_test:upgrade_sync", fixtureItem);
        machine.snapshot.tier = 0;
        machine.nextItem = new ItemStack(fixtureItem, 3, 0);
        machine.nextItem.setTagCompound(new NBTTagCompound());
        machine.nextItem.getTagCompound().setString("upgrade", "native-sync-regression");
        check(server.updateCacheFromSource(false), "Null to non-null source change is detected");
        check(machine.requestedTier == 1, "Production getter requests snapshot tier plus one");
        check(server.getValue() != machine.nextItem, "Native cache update deep-copies source stack");
        check(server.getValue().getTagCompound() != machine.nextItem.getTagCompound(), "Native copy isolates NBT");
        transfer(server, client);
        check(ItemStack.areItemStacksEqual(client.getValue(), machine.nextItem), "Item/count/damage/NBT round-trip");
        check(client.getValue() != server.getValue(), "Client packet value is a separate stack");
        check(!server.updateCacheFromSource(false), "Equivalent source stack is not sent every tick");
        machine.nextItem.stackSize = 4;
        check(server.getValue().stackSize == 3, "Source mutation cannot mutate cached stack");
        check(server.updateCacheFromSource(false), "Native equality detects source count change");
        transfer(server, client);
        check(client.getValue().stackSize == 4, "Count mutation synchronizes");

        machine.nextItem = null;
        machine.snapshot.tier = 4;
        check(server.updateCacheFromSource(false), "Non-null to maximum-tier null is detected");
        transfer(server, client);
        check(client.getValue() == null, "Later null packet clears former upgrade item");
        check(!server.updateCacheFromSource(false), "Subsequent maximum-tier null remains unchanged");
        check(clientMachine.snapshotReads == 0 && clientMachine.itemReads == 0, "Packet reads never call server getters");
        System.out.println("PASS RemoteWorkerUpgradeSyncRegression: " + checks + " checks");
    }
}
