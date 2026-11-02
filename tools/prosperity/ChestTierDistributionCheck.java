import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.ISaveHandler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntitySealedChest;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntityUnsealedChest;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.ChestTier;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterCatalog;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterSite;

/** Actual tier resolver, authored rewards and native sealed-TileEntity initialization/NBT, without a game boot. */
public final class ChestTierDistributionCheck {

    private static int checks;
    private static final int RARE_PERCENT = Integer.getInteger("gtsr.test.rarePercent", 10);

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static JsonObject node(String text) {
        return new JsonParser().parse(text).getAsJsonObject();
    }

    private static void rareFrequency(int count, int rolls, String message) {
        double expected = rolls * RARE_PERCENT / 100.0;
        check(count > expected * .8 && count < expected * 1.2, message + ": " + count + "/" + rolls);
    }

    private static RemasterSite site(String prefab, int seed) {
        return new RemasterSite(prefab, 0, seed, seed * 16, 64, -seed * 16);
    }

    private static void categoryBounds() {
        for (String mode : new String[] { "direct", "story", "puzzle", "combat" }) {
            JsonObject node = node("{\"id\":\"legacy-high\",\"tier\":5,\"rareTier5\":true}");
            node.addProperty("unlockMode", mode);
            int maximum = mode.equals("direct") ? 3 : 4;
            int fours = 0;
            for (int seed = 0; seed < 10000; seed++) {
                int tier = ChestTier.resolveTier(site("fallen_foundry", seed), node);
                check(tier >= 1 && tier <= maximum, mode + " cannot award tier " + tier + " at seed " + seed);
                if (tier == 4) fours++;
            }
            if (!mode.equals("direct")) rareFrequency(fours, 10000, "conditional tier IV frequency");
            JsonObject low = node("{\"id\":\"authored-low\",\"tier\":2}");
            low.addProperty("unlockMode", mode);
            for (int seed = 0; seed < 1000; seed++)
                check(ChestTier.resolveTier(site("fallen_foundry", seed), low) == 2,
                    "existing fixed low rewards must not gain a high-tier promotion");
        }
    }

    private static void authoredBosses() {
        int[] counts = new int[6];
        int nodes = 0;
        for (String id : RemasterCatalog.ids()) {
            for (JsonElement element : site(id, 0).plan().metadata.getAsJsonArray("lootPlan7")) {
                JsonObject node = element.getAsJsonObject();
                if (!ChestTier.bossReward(node)) continue;
                nodes++;
                for (int seed = 0; seed < 1000; seed++) {
                    int tier = ChestTier.resolveTier(site(id, seed), node);
                    check(tier == ChestTier.resolveTier(site(id, seed), node), "tier must be stable across retries");
                    counts[tier]++;
                }
            }
        }
        check(nodes > 0 && counts[5] > 0, "authored boss rewards must retain a chance of tier V");
        check(counts[3] > 0 && counts[5] < nodes * 1000, "boss rewards must not be fixed tier V");
        for (int tier : new int[] { 4, 5 })
            rareFrequency(counts[tier], nodes * 1000, "authored boss rare tier frequency");
        System.out.println("authoredBosses=" + nodes + ";tierCounts=" + Arrays.toString(counts));
    }

    public static final class TestChest extends TileEntitySealedChest {
        @Override
        public void markDirty() {}
    }

    /** Only the world seed and update notification are used by these production initialization paths. */
    public static final class TestWorld extends World {
        private TestWorld() {
            super((ISaveHandler) null, (String) null, (WorldProvider) null, (WorldSettings) null, (Profiler) null);
        }
        @Override
        public long getSeed() { return 218; }
        @Override
        public void markBlockForUpdate(int x, int y, int z) {}
        @Override
        public net.minecraft.entity.Entity getEntityByID(int id) { throw new AssertionError("no entity access"); }
        @Override
        protected IChunkProvider createChunkProvider() { throw new AssertionError("no chunk access"); }
        @Override
        protected int func_152379_p() { return 0; }
    }

    private static TestChest chest(World world, int x) {
        TestChest chest = new TestChest();
        chest.setWorldObj(world);
        chest.xCoord = x;
        chest.yCoord = 64;
        chest.zCoord = -x;
        return chest;
    }

    private static void nativeSources() throws Exception {
        TestWorld world = world();
        int[] bossCounts = new int[6];
        for (int x = 0; x < 10000; x++) {
            TestChest direct = chest(world, x);
            direct.initializeClickUnlock(5, "echo:legacy-click");
            check(direct.getTier() <= 3, "legacy click-only reward is not a conditional reward");
            TestChest boss = chest(world, x);
            boss.initialize(3, "echo:legacy-boss", -1);
            bossCounts[boss.getTier()]++;
            TestChest platform = chest(world, x);
            platform.initialize(2, "forgotten-lake", 0);
            check(platform.getTier() == 2, "native platform keeps its authored tier II");
            TestChest altar = chest(world, x);
            altar.initializeAltar("altar:legacy");
            check(altar.getTier() == 1, "altar keeps its authored tier I");
        }
        check(bossCounts[3] > 0 && bossCounts[5] > 0, "native boss rewards must roll instead of force V");
        for (int tier : new int[] { 4, 5 })
            rareFrequency(bossCounts[tier], 10000, "native boss rare tier frequency");
        System.out.println("nativeBossTierCounts=" + Arrays.toString(bossCounts));
    }

    private static TestWorld world() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        return (TestWorld) unsafe.allocateInstance(TestWorld.class);
    }

    @SuppressWarnings("unchecked")
    private static void savedSeals() throws Exception {
        Field mapping = TileEntity.class.getDeclaredField("classToNameMap");
        mapping.setAccessible(true);
        ((Map<Class<?>, String>) mapping.get(null)).put(TestChest.class, "gtsr_test:sealed");
        NBTTagCompound saved = new NBTTagCompound();
        saved.setInteger("tier", 5);
        saved.setInteger("lootContract", 1);
        saved.setString("encounter", "cache:legacy");
        saved.setBoolean("clickUnlock", true);
        saved.setInteger("opening", -1);
        TestChest restored = new TestChest();
        restored.readFromNBT(saved);
        NBTTagCompound written = new NBTTagCompound();
        restored.writeToNBT(written);
        check(written.getInteger("tier") == 5 && written.getInteger("lootContract") == 1,
            "NBT read/write must preserve old seal data before authorized migration");
        ((Map<Class<?>, String>) mapping.get(null)).put(TileEntityUnsealedChest.class, "gtsr_test:opened");
        Item item = new Item();
        Method register = Item.itemRegistry.getClass().getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        register.setAccessible(true);
        register.invoke(Item.itemRegistry, 32000, "gtsr_test:preserved", item);
        TileEntityUnsealedChest opened = new TileEntityUnsealedChest();
        check(opened.beginReward(5), "prepare old settled tier V reward");
        ItemStack stack = new ItemStack(item, 12, 7);
        NBTTagCompound proof = new NBTTagCompound();
        proof.setString("proof", "existing-player-items");
        stack.setTagCompound(proof);
        opened.setInventorySlotContents(53, stack);
        NBTTagCompound openedSaved = new NBTTagCompound();
        opened.writeToNBT(openedSaved);
        TileEntityUnsealedChest openedRestored = new TileEntityUnsealedChest();
        openedRestored.readFromNBT(openedSaved);
        ItemStack retained = openedRestored.getStackInSlot(53);
        check(openedRestored.getRewardTier() == 5 && !openedRestored.beginReward(3)
            && retained.getItem() == item && retained.stackSize == 12 && retained.getItemDamage() == 7
            && retained.getTagCompound().getString("proof").equals("existing-player-items"),
            "settled tier V reward and existing item quantity, damage and NBT must survive unchanged");
    }

    @SuppressWarnings("unchecked")
    private static void migration() throws Exception {
        Field mapping = TileEntity.class.getDeclaredField("classToNameMap");
        mapping.setAccessible(true);
        ((Map<Class<?>, String>) mapping.get(null)).put(TestChest.class, "gtsr_test:sealed");
        Method migrate = TileEntitySealedChest.class.getDeclaredMethod("migrateLootContract");
        migrate.setAccessible(true);
        TestWorld world = world();
        for (int contract : new int[] { 0, 1 }) for (boolean boss : new boolean[] { false, true }) {
            NBTTagCompound saved = new NBTTagCompound();
            saved.setInteger("tier", 5);
            saved.setInteger("lootContract", contract);
            saved.setString("encounter", boss ? "echo:legacy-boss" : "cache:legacy-click");
            saved.setInteger("platform", -1);
            saved.setBoolean("clickUnlock", !boss);
            saved.setInteger("opening", -1);
            saved.setInteger("x", 128);
            saved.setInteger("y", 64);
            saved.setInteger("z", -128);
            TestChest restored = chest(world, 128);
            restored.readFromNBT(saved);
            migrate.invoke(restored);
            NBTTagCompound migrated = new NBTTagCompound();
            restored.writeToNBT(migrated);
            check(migrated.getInteger("lootContract") == 2, "old sealed contract " + contract + " migrates once to 2");
            if (!boss) check(restored.getTier() == 3, "legacy unearned high reward is reduced to tier III");
            TestChest fresh = chest(world, 128);
            if (boss) fresh.initialize(3, "echo:legacy-boss", -1);
            else fresh.initializeClickUnlock(5, "cache:legacy-click");
            check(fresh.getTier() == restored.getTier(), "migration and new generation use the same stable roll");
            TestChest reloaded = chest(world, 128);
            reloaded.readFromNBT(migrated);
            for (int retry = 0; retry < 10; retry++) migrate.invoke(reloaded);
            NBTTagCompound repeated = new NBTTagCompound();
            reloaded.writeToNBT(repeated);
            check(migrated.equals(repeated), "saving, reloading and retries must not reroll or alter any seal data");
        }
    }

    public static void main(String[] args) throws Exception {
        String scenario = args.length == 0 ? "all" : args[0];
        if (scenario.equals("all") || scenario.equals("categories")) categoryBounds();
        if (scenario.equals("all") || scenario.equals("bosses")) authoredBosses();
        if (scenario.equals("all") || scenario.equals("native")) nativeSources();
        if (scenario.equals("all") || scenario.equals("saved")) savedSeals();
        if (scenario.equals("all") || scenario.equals("migration")) migration();
        System.out.println("PASS " + scenario + ": " + checks + " checks");
    }
}
