package com.miaokatze.gtsr.common.weapons;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;

/** Complete production controller, native NBT/ItemStack/InventoryPlayer; controlled drop admission only. */
public final class WeaponRefundConservationCheck {
    private static int checks;
    private static Class<?> sessionType;
    private static Method refund;

    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        PortableWeapons.AmmoItem normal = allocate(PortableWeapons.AmmoItem.class);
        field(PortableWeapons.AmmoItem.class, "kind").set(normal, WeaponKind.QLZ04);
        field(PortableWeapons.AmmoItem.class, "mimic").set(normal, false);
        normal.setMaxStackSize(1).setMaxDurability(WeaponKind.QLZ04.capacity);
        PortableWeapons.ammo[WeaponKind.QLZ04.id] = normal;
        PortableWeapons.AmmoItem mimic = allocate(PortableWeapons.AmmoItem.class);
        field(PortableWeapons.AmmoItem.class, "kind").set(mimic, WeaponKind.QLZ04);
        field(PortableWeapons.AmmoItem.class, "mimic").set(mimic, true);
        mimic.setMaxStackSize(1).setMaxDurability(WeaponKind.QLZ04.capacity);
        PortableWeapons.mimicAmmo[WeaponKind.QLZ04.id] = mimic;
        sessionType = Class.forName(WeaponController.class.getName() + "$Session");
        refund = method("refundMagazine", sessionType, WeaponKind.class, int.class);
        String scenario = args.length == 0 ? "overflow" : args[0];
        if (scenario.equals("overflow")) {
            run(12, 12, 1, true, false, 12, "11 paid from exhausted pack A plus 1 from fresh pack B");
            run(12, 12, 1, true, true, 12, "full inventory drops exact 11-round remainder");
            run(12, 3, 1, true, false, 3, "economy refunds paid rounds only");
            run(12, 12, 0, true, false, 12, "full latest ordinary pack cannot swallow prior payments");
        } else if (scenario.equals("missing-mimic")) {
            Player player = player(false); Object session = session(player, 12, 12);
            refund(session, 1);
            check(countPacks(player, true) == 0, "missing mimic must never mint a regenerating vessel");
            check(data(session).getInteger("magazine") == 12 && data(session).getInteger("paidMagazine") == 12,
                "unreturnable mimic receipt stays loaded and paid");
            ItemStack original = pack(true, 12, "latest-pack", "original"); player.inventory.mainInventory[0] = original;
            refund(session, 1);
            check(countPacks(player, true) == 1 && original.getMetadata() == 0 && data(session).getInteger("paidMagazine") == 0,
                "retrieving original mimic settles pending receipt without creating another vessel");
        } else if (scenario.equals("rejected-drop")) {
            Player player = player(true); player.reject = true;
            Object session = session(player, 7, 7); refund(session, 0);
            check(data(session).getInteger("magazine") == 7 && data(session).getInteger("paidMagazine") == 7,
                "non-null entity returned by rejected world admission cannot retire refund receipt");
            check(rounds(player) == 0, "rejected drop delivers nothing");
            player.reject = false; refund(session, 0);
            check(rounds(player) == 7 && data(session).getInteger("paidMagazine") == 0, "successful retry pays exact pending rounds");
            refund(session, 0); check(rounds(player) == 7, "retry cannot pay twice");
        } else if (scenario.equals("malformed")) {
            malformedReceipts();
        } else if (scenario.equals("transactions")) {
            reloadAndShoot(false); reloadAndShoot(true); freeLegacyTopup();
        } else if (scenario.equals("receipts")) {
            sourceReceipts(); partialReceipt(); capturedDrop(); transitionFailure(); boundedReceipts();
        } else {
            check(!normal.hasContainerItem(), "ordinary ammo defines no independent empty container");
            run(7, 7, 30, true, false, 7, "legacy single source restoration remains in place");
            run(7, 7, 0, false, false, 7, "legacy missing ordinary pack returns paid contents");
            run(7, 0, 1, true, false, 0, "free magazine does not refund ammo");
            Player player = player(false); ItemStack pack = pack(true, 0, "latest-pack", "mimic");
            player.inventory.mainInventory[0] = pack; refund(session(player, 12, 12), 1);
            check(countPacks(player, true) == 1 && player.drops.isEmpty() && pack.getMetadata() == 0,
                "already regenerated present mimic absorbs refund without creating another vessel");
        }
        System.out.println("PASS: " + checks + " native weapon refund " + scenario + " checks");
    }

    private static void run(int magazine, int paid, int damage, boolean present, boolean full, int expected,
        String reason) throws Exception {
        Player player = player(full); ItemStack pack = pack(false, damage, "latest-pack", "original");
        if (present) player.inventory.mainInventory[0] = pack;
        NBTTagCompound originalTag = (NBTTagCompound) pack.getTagCompound().copy();
        int before = rounds(player); Object session = session(player, magazine, paid); refund(session, 0);
        check(rounds(player) - before == expected, reason);
        check(data(session).getInteger("magazine") == 0 && data(session).getInteger("paidMagazine") == 0, "refund retires magazine receipt");
        int after = rounds(player); refund(session, 0); check(rounds(player) == after, "repeated switch cannot refund twice");
        if (present) check(originalTag.equals(pack.getTagCompound()), "in-place refund preserves all original NBT");
        if (present && expected > damage) {
            check(pack.getMetadata() == 0, "existing latest pack fills first");
            for (ItemStack item : player.inventory.mainInventory) if (item != null && item != pack && item.getItem() == pack.getItem())
                check(!WeaponController.data(item).hasKey("id"), "detached overflow does not clone routing identity");
        }
        if (full && expected > damage) check(player.drops.size() == 1, "full inventory uses exact explicit remainder drop");
    }

    private static void sourceReceipts() throws Exception {
        Player player = player(false); Object session = session(player, 0, 0); NBTTagCompound n = data(session);
        ItemStack a = pack(false, 60, "pack-a", "alpha"), b = pack(false, 1, "pack-b", "beta");
        remember(n, a, 11); n.setInteger("magazine", 11); n.setInteger("paidMagazine", 11);
        remember(n, b, 1); n.setInteger("magazine", 12); n.setInteger("paidMagazine", 12);
        player.inventory.mainInventory[0] = b;
        for (int i = 0; i < 2; i++) {
            n.setInteger("magazine", n.getInteger("magazine") - 1);
            method("spendPayment", NBTTagCompound.class).invoke(null, n);
            n.setInteger("paidMagazine", n.getInteger("paidMagazine") - 1);
        }
        persistGun(session); // Native ItemStack write/read after paid shots.
        int before = rounds(player); refund(session, 0);
        check(rounds(player) - before == 10, "two fired paid rounds consume exactly two source receipts across save/read");
        ItemStack returned = player.inventory.mainInventory[1];
        check(returned != null && WeaponController.remaining(returned) == 9, "original exhausted A returns only its nine unpaid rounds");
        check(returned.getTagCompound().getString("custom").equals("alpha"), "A metadata survives B top-up and save/read");
        check(returned.getTagCompound().getCompoundTag("nested").getInteger("value") == 29, "nested custom NBT preserved");
        check(!WeaponController.data(returned).hasKey("id"), "detached source does not duplicate original pack identity");
        check(b.getMetadata() == 0 && b.getTagCompound().getString("custom").equals("beta"), "B restored in place with its own metadata");
        returned.getTagCompound().getCompoundTag("nested").setInteger("value", 99);
        check(a.getTagCompound().getCompoundTag("nested").getInteger("value") == 29, "receipt and returned metadata are detached deep copies");
        check(!data(session).hasKey("magazinePayments"), "settled receipt list removed");
    }

    private static void partialReceipt() throws Exception {
        Player player = player(true); player.reject = true;
        ItemStack b = pack(false, 1, "latest-pack", "beta"); player.inventory.mainInventory[0] = b;
        Object session = session(player, 12, 12); int before = rounds(player); refund(session, 0);
        check(rounds(player) - before == 1 && b.getMetadata() == 0, "partial refund delivers one actual spent-capacity round");
        check(data(session).getInteger("magazine") == 11 && data(session).getInteger("paidMagazine") == 11,
            "rejected remainder retains exactly eleven paid loaded rounds");
        persistGun(session);
        refund(session, 0); check(rounds(player) - before == 1, "rejected retry after native persistence cannot refill B twice");
        player.reject = false; refund(session, 0);
        check(rounds(player) - before == 12 && data(session).getInteger("paidMagazine") == 0, "retry after persistence delivers only remaining eleven");
        check(player.drops.size() == 1, "only successful remainder enters world");
    }

    private static void capturedDrop() throws Exception {
        Player player = player(true); player.captureDrops = true; Object session = session(player, 5, 5); refund(session, 0);
        check(player.capturedDrops.size() == 1 && player.worldObj.loadedEntityList.isEmpty(), "Forge capture ownership accepted without premature world admission");
        check(data(session).getInteger("paidMagazine") == 0, "captured drop retires receipt exactly once");
    }

    private static void transitionFailure() throws Exception {
        Player player = player(true); player.reject = true; Object session = session(player, 12, 12);
        data(session).setInteger("mode", 0); data(session).setInteger("ammoType", 0);
        ItemStack alternative = pack(true, 0, "other", "mimic"); player.inventory.mainInventory[0] = alternative;
        method("switchAmmo", sessionType, WeaponKind.class).invoke(new WeaponController(), session, WeaponKind.QLZ04);
        check(data(session).getInteger("ammoType") == 0 && data(session).getInteger("magazine") == 12,
            "failed ammo refund does not relabel loaded rounds");
        method("switchMode", sessionType, WeaponKind.class).invoke(new WeaponController(), session, WeaponKind.QLZ04);
        check(data(session).getInteger("mode") == 0 && data(session).getInteger("magazine") == 12,
            "failed mode refund does not shrink or discard loaded magazine");
    }

    private static void boundedReceipts() throws Exception {
        Player player = player(false); Object session = session(player, 0, 0); NBTTagCompound n = data(session);
        Method spend = method("spendPayment", NBTTagCompound.class);
        for (int i = 0; i < 12; i++) {
            remember(n, pack(false, 1, "initial-" + i, "tag"), 1);
            n.setInteger("magazine", i + 1); n.setInteger("paidMagazine", i + 1);
        }
        for (int i = 0; i < 1000; i++) {
            n.setInteger("magazine", 11); spend.invoke(null, n); n.setInteger("paidMagazine", 11);
            remember(n, pack(false, 1, "replacement-" + i, "tag"), 1);
            n.setInteger("magazine", 12); n.setInteger("paidMagazine", 12);
            check(n.getTagList("magazinePayments", 10).tagCount() == 12 && receiptTotal(n) == 12,
                "paid-source storage remains bounded by twelve outstanding rounds, not historical reloads");
        }
    }

    private static void reloadAndShoot(boolean legacy) throws Exception {
        Player player = player(false); Object session = session(player, 0, 0);
        PortableWeapons.WeaponItem weapon = allocate(PortableWeapons.WeaponItem.class);
        field(PortableWeapons.WeaponItem.class, "kind").set(weapon, WeaponKind.QLZ04); weapon.setMaxStackSize(1);
        net.minecraft.item.Item.itemRegistry.addObject(31000 + checks, "gtsr:audit_weapon_" + checks, weapon);
        ItemStack gun = new ItemStack(weapon); field(sessionType, "gun").set(session, gun);
        player.inventory.mainInventory[0] = gun; player.inventory.currentItem = 0;
        player.inventoryContainer = new EmptyContainer();
        NBTTagCompound n = WeaponController.data(gun); n.setInteger("ammoType", 0);
        ItemStack a = pack(false, 49, "pack-a", "alpha"), b = pack(false, 0, "pack-b", "beta");
        player.inventory.mainInventory[1] = a; player.inventory.mainInventory[2] = b;
        WeaponController controller = new WeaponController();
        if (legacy) {
            player.inventory.mainInventory[1] = null; b.setMetadata(12);
            n.setInteger("magazine", 12); n.setInteger("paidMagazine", 12); n.setString("returnPack", "pack-b");
        } else {
            Method reload = method("finishReload", sessionType, WeaponKind.class);
            field(sessionType, "reloadPack").set(session, a); reload.invoke(controller, session, WeaponKind.QLZ04);
            check(n.getInteger("magazine") == 11 && n.getInteger("paidMagazine") == 11 && player.inventory.mainInventory[1] == null,
                "actual reload debits and removes exhausted A after recording eleven paid rounds");
            field(sessionType, "reloadPack").set(session, b); reload.invoke(controller, session, WeaponKind.QLZ04);
            check(n.getInteger("magazine") == 12 && n.getInteger("paidMagazine") == 12 && b.getMetadata() == 1,
                "actual top-up debits only one round from B");
            java.util.Random expected = new java.util.Random(47); for (int i = 0; i < 12; i++) expected.nextFloat();
            check(player.random.nextLong() == expected.nextLong(), "reload receipt snapshots consume no additional economy RNG draws");
        }
        field(sessionType, "firing").setBoolean(session, true); TestWorld world = (TestWorld) player.worldObj;
        Method update = method("update", sessionType); world.rejectProjectile = true; update.invoke(controller, session);
        check(n.getInteger("magazine") == 12 && n.getInteger("paidMagazine") == 12 && receiptTotal(n) == 12,
            "rejected projectile admission preserves magazine and every paid receipt, including legacy migration");
        world.rejectProjectile = false; update.invoke(controller, session);
        check(n.getInteger("magazine") == 11 && n.getInteger("paidMagazine") == 11 && receiptTotal(n) == 11,
            "actual accepted shot spends exactly one paid source receipt");
        world.time += WeaponKind.QLZ04.interval; update.invoke(controller, session);
        check(n.getInteger("magazine") == 10 && n.getInteger("paidMagazine") == 10 && receiptTotal(n) == 10,
            "actual second shot spends one more receipt without repeating the first");
        persistGun(session);
        int before = rounds(player); refund(session, 0);
        check(rounds(player) - before == 10 && data(session).getInteger("magazine") == 0,
            "actual reload/shots/native save-copy/refund sequence returns only ten remaining paid rounds");
        if (!legacy) check(player.inventory.mainInventory[1].getTagCompound().getString("custom").equals("alpha"),
            "actual reload path snapshots original A tags before removal");
    }

    private static int receiptTotal(NBTTagCompound n) {
        net.minecraft.nbt.NBTTagList list = n.getTagList("magazinePayments", 10); int total = 0;
        for (int i = 0; i < list.tagCount(); i++) total += list.getCompoundTagAt(i).getInteger("paid");
        return total;
    }

    private static void malformedReceipts() throws Exception {
        for (int kind = 0; kind < 5; kind++) {
            Player player = player(false); Object session = session(player, 0, 0);
            PortableWeapons.WeaponItem weapon = allocate(PortableWeapons.WeaponItem.class);
            field(PortableWeapons.WeaponItem.class, "kind").set(weapon, WeaponKind.QLZ04); weapon.setMaxStackSize(1);
            ItemStack gun = new ItemStack(weapon); field(sessionType, "gun").set(session, gun);
            player.inventory.mainInventory[0] = gun; player.inventoryContainer = new EmptyContainer();
            NBTTagCompound n = WeaponController.data(gun); n.setInteger("ammoType", 0);
            net.minecraft.nbt.NBTBase opaque;
            if (kind == 0) opaque = new net.minecraft.nbt.NBTTagString("opaque-outer");
            else {
                net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
                if (kind < 3) {
                    list.appendTag(new net.minecraft.nbt.NBTTagString("opaque-member"));
                    if (kind == 2) list.removeTag(0); // Native retains string subtype even though count is now zero.
                } else if (kind == 4) { list.appendTag(new NBTTagCompound()); list.removeTag(0); }
                opaque = list; // kind 3 is a legitimate fresh, untyped empty list; kind 4 is empty compound-typed.

            }
            n.setTag("magazinePayments", opaque); net.minecraft.nbt.NBTBase before = opaque.copy();
            ItemStack a = pack(false, 59, "pack-a", "alpha"); player.inventory.mainInventory[1] = a;
            field(sessionType, "reloadPack").set(session, a);
            method("finishReload", sessionType, WeaponKind.class).invoke(new WeaponController(), session, WeaponKind.QLZ04);
            if (kind >= 3) {
                check(a.getMetadata() == 60 && player.inventory.mainInventory[1] == null
                    && n.getInteger("magazine") == 1 && n.getInteger("paidMagazine") == 1 && receiptTotal(n) == 1,
                    "legitimate END/COMPOUND empty receipt list accepts actual reload and persists payment");
                refund(session, 0);
                check(rounds(player) == 1 && n.getInteger("paidMagazine") == 0, "valid empty receipt list remains refundable");
                continue;
            }
            check(a.getMetadata() == 59 && player.inventory.mainInventory[1] == a
                && n.getInteger("magazine") == 0 && n.getInteger("paidMagazine") == 0,
                "unreadable outer/inner receipt schema must reject reload before any debit");
            check(before.equals(n.getTag("magazinePayments")), "rejected reload preserves opaque receipt bytes/type");
            check(player.random.nextLong() == new java.util.Random(47).nextLong(), "rejected schema consumes no economy RNG");
            n.setInteger("magazine", 1); n.setInteger("paidMagazine", 1); refund(session, 0);
            check(n.getInteger("magazine") == 1 && n.getInteger("paidMagazine") == 1 && before.equals(n.getTag("magazinePayments")),
                "refund cannot normalize away unknown receipt schema");
            field(sessionType, "firing").setBoolean(session, true);
            method("update", sessionType).invoke(new WeaponController(), session);
            check(n.getInteger("magazine") == 1 && n.getInteger("paidMagazine") == 1 && before.equals(n.getTag("magazinePayments")),
                "firing migration cannot rewrite an unreadable receipt payload");
        }
    }

    private static void freeLegacyTopup() throws Exception {
        Player player = player(false); Object session = session(player, 0, 0);
        PortableWeapons.WeaponItem weapon = allocate(PortableWeapons.WeaponItem.class);
        field(PortableWeapons.WeaponItem.class, "kind").set(weapon, WeaponKind.QLZ04); weapon.setMaxStackSize(1);
        ItemStack gun = new ItemStack(weapon); field(sessionType, "gun").set(session, gun);
        player.inventory.mainInventory[0] = gun; player.inventoryContainer = new EmptyContainer();
        NBTTagCompound n = WeaponController.data(gun);
        n.setInteger("ammoType", 1); n.setInteger("loadedType", 1); n.setInteger("magazine", 11);
        n.setInteger("paidMagazine", 11); n.setString("returnPack", "legacy-a");
        ItemStack b = pack(true, 0, "fresh-b", "beta"); player.inventory.mainInventory[1] = b;
        WeaponEnchantments economy = allocate(WeaponEnchantments.class);
        field(WeaponEnchantments.class, "category").setInt(economy, 3);
        field(net.minecraft.enchantment.Enchantment.class, "effectId").setInt(economy, 200);
        WeaponEnchantments.economy = economy; gun.addEnchantment(economy, 5);
        player.random = new java.util.Random() { @Override public float nextFloat() { return 0; } };
        field(sessionType, "reloadPack").set(session, b);
        try {
            method("finishReload", sessionType, WeaponKind.class).invoke(new WeaponController(), session, WeaponKind.QLZ04);
        } finally { WeaponEnchantments.economy = null; }
        check(n.getInteger("magazine") == 12 && n.getInteger("paidMagazine") == 11 && b.getMetadata() == 0,
            "actual economy top-up adds one free round without debiting B");
        net.minecraft.nbt.NBTTagList payments = n.getTagList("magazinePayments", 10);
        check(payments.tagCount() == 1 && payments.getCompoundTagAt(0).getInteger("paid") == 11
            && payments.getCompoundTagAt(0).getString("id").equals("legacy-a"),
            "all-free B top-up migrates existing paid A identity before overwriting returnPack; no zero-paid receipt");
        refund(session, 1);
        check(n.getInteger("magazine") == 12 && n.getInteger("paidMagazine") == 11 && b.getMetadata() == 0,
            "missing legacy A cannot settle against fully regenerated mimic B");
    }

    private static void persistGun(Object session) throws Exception {
        ItemStack original = (ItemStack) field(sessionType, "gun").get(session);
        NBTTagCompound saved = new NBTTagCompound(); original.writeToNBT(saved);
        ItemStack loaded = ItemStack.loadItemStackFromNBT((NBTTagCompound) saved.copy());
        check(loaded != null && loaded.getItem() == original.getItem()
            && loaded.getTagCompound().equals(original.getTagCompound()), "native ItemStack save/read preserves complete receipt NBT");
        field(sessionType, "gun").set(session, loaded);
        Player player = (Player) field(sessionType, "player").get(session);
        if (player.inventory.mainInventory[0] == original) player.inventory.mainInventory[0] = loaded;
    }

    private static void remember(NBTTagCompound n, ItemStack source, int paid) throws Exception {
        method("rememberPayment", NBTTagCompound.class, ItemStack.class, WeaponKind.class, int.class)
            .invoke(null, n, source, WeaponKind.QLZ04, paid);
    }
    private static void refund(Object session, int type) throws Exception { refund.invoke(new WeaponController(), session, WeaponKind.QLZ04, type); }
    private static NBTTagCompound data(Object session) throws Exception { return WeaponController.data((ItemStack) field(sessionType, "gun").get(session)); }
    private static Object session(Player player, int magazine, int paid) throws Exception {
        Constructor<?> ctor = sessionType.getDeclaredConstructor(); ctor.setAccessible(true); Object session = ctor.newInstance();
        field(sessionType, "player").set(session, player); field(sessionType, "gun").set(session, new ItemStack(Items.stick));
        NBTTagCompound n = data(session); n.setInteger("magazine", magazine); n.setInteger("paidMagazine", paid); n.setString("returnPack", "latest-pack");
        return session;
    }
    private static ItemStack pack(boolean mimic, int damage, String id, String custom) {
        ItemStack result = new ItemStack((mimic ? PortableWeapons.mimicAmmo : PortableWeapons.ammo)[WeaponKind.QLZ04.id]);
        result.setMetadata(damage); WeaponController.data(result).setString("id", id);
        result.getTagCompound().setString("custom", custom); NBTTagCompound nested = new NBTTagCompound(); nested.setInteger("value", 29);
        result.getTagCompound().setTag("nested", nested); return result;
    }
    private static Player player(boolean full) throws Exception {
        Player p = allocate(Player.class); p.capabilities = new PlayerCapabilities(); p.inventory = new InventoryPlayer(p);
        p.entityData = new NBTTagCompound(); p.random = new java.util.Random(47); p.drops = new ArrayList<>(); p.capturedDrops = new ArrayList<>(); p.worldObj = allocate(TestWorld.class);
        p.worldObj.loadedEntityList = new ArrayList<>(); field(net.minecraft.world.World.class, "provider").set(p.worldObj, new net.minecraft.world.WorldProviderSurface());
        if (full) for (int i = 0; i < p.inventory.mainInventory.length; i++) p.inventory.mainInventory[i] = new ItemStack(Items.stick, 64);
        return p;
    }
    private static int countPacks(Player p, boolean mimic) {
        int count = 0; Object item = (mimic ? PortableWeapons.mimicAmmo : PortableWeapons.ammo)[WeaponKind.QLZ04.id];
        for (ItemStack s : p.inventory.mainInventory) if (s != null && s.getItem() == item) count += s.stackSize;
        for (ItemStack s : p.drops) if (s.getItem() == item) count += s.stackSize;
        return count;
    }
    private static int rounds(Player p) {
        int sum = 0;
        for (ItemStack s : p.inventory.mainInventory) if (s != null && s.getItem() == PortableWeapons.ammo[WeaponKind.QLZ04.id]) sum += WeaponController.remaining(s);
        for (ItemStack s : p.drops) sum += WeaponController.remaining(s);
        return sum;
    }
    public static final class Player extends EntityPlayerMP {
        List<ItemStack> drops; boolean reject; NBTTagCompound entityData; java.util.Random random;
        @Override public NBTTagCompound getEntityData() { return entityData; }
        @Override public java.util.Random getRNG() { return random; }
        private Player() { super(null, null, null, null); }
        @Override public EntityItem entityDropItem(ItemStack item, float y) {
            EntityItem entity = new EntityItem(worldObj, 0, 0, 0, item);
            if (captureDrops) capturedDrops.add(entity);
            else if (!reject) { worldObj.loadedEntityList.add(entity); drops.add(item.copy()); }
            return entity; // Native returns a non-null entity even if spawnEntityInWorld rejects it.
        }
        @Override public void addChatMessage(net.minecraft.util.IChatComponent chat) {}
    }
    public static final class EmptyContainer extends net.minecraft.inventory.Container {
        public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer p) { return true; }
    }
    public static final class TestWorld extends WorldServer {
        long time; boolean rejectProjectile;
        @Override public long getTotalWorldTime() { return time; }
        @Override public boolean spawnEntityInWorld(Entity e) { if (rejectProjectile) return false; loadedEntityList.add(e); return true; }
        private TestWorld() { super(null, null, "fixture", 0, null, null); }
    }
    private static Method method(String name, Class<?>... types) throws Exception { Method m = WeaponController.class.getDeclaredMethod(name, types); m.setAccessible(true); return m; }
    private static Field field(Class<?> type, String name) throws Exception { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
    @SuppressWarnings("unchecked") private static <T> T allocate(Class<T> type) throws Exception { return (T) ((sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null)).allocateInstance(type); }
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); checks++; }
}
