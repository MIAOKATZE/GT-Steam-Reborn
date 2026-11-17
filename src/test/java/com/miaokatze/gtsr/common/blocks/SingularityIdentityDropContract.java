// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.blocks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.Vec3;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.IChunkProvider;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.blocks.TileRunawaySingularity.SingularityType;
import com.miaokatze.gtsr.common.items.SteamEntangledSingularity;
import com.miaokatze.gtsr.common.util.SingularityDropExplosion;

import cpw.mods.fml.common.registry.GameRegistry;

/** Actual MC block/item, NBT, removal and explosion hooks in a bounded fixture; no game, network or disk world. */
public final class SingularityIdentityDropContract {

    private static sun.misc.Unsafe allocator;
    private static int checks;

    public static void main(String[] args) throws Exception {
        Class<?> bootstrap = Class.forName("com.miaokatze.gtsr.compat.gtnl.GtnlNativeBootstrap");
        Method enter = bootstrap.getDeclaredMethod("enter", String.class, String[].class);
        enter.setAccessible(true);
        if (!(Boolean) enter.invoke(null, EventBootstrap.class.getName(), args)) return;
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        allocator = (sun.misc.Unsafe) field.get(null);
        BlockRunawaySingularity block = new BlockRunawaySingularity();
        BlocksGTSR.runawaySingularity = block;
        GameRegistry.registerBlock(block, ItemBlockRunawaySingularity.class, "contract_singularity");
        GameRegistry.registerTileEntity(TileRunawaySingularity.class, "contract_singularity_tile");
        ItemBlockRunawaySingularity item = (ItemBlockRunawaySingularity) Item.getItemFromBlock(block);
        Item steam = new SteamEntangledSingularity();
        GameRegistry.registerItem(steam, "contract_steam_singularity");
        GTSRItemList.SteamEntangledSingularity.set(steam);
        identities(block, item);
        spawnAndNbt(block);
        harvesting(block);
        knockback();
        System.out
            .println("PASS singularity identity/drop contract: " + checks + " checks; isolated-patched-MC-no-game");
    }

    private static void identities(BlockRunawaySingularity block, ItemBlockRunawaySingularity item) throws Exception {
        for (int invalid : new int[] { 0, 4, 15 }) {
            ItemStack retired = new ItemStack(item, 1, invalid);
            require(ItemBlockRunawaySingularity.variantIndex(retired) == 0, "invalid metadata is not a natural alias");
            require(
                !item.placeBlockAt(retired, null, null, 0, 64, 0, 1, 0, 0, 0, invalid),
                "invalid placement rejected");
            require(
                !item.onItemUse(retired, null, null, 0, 64, 0, 1, 0, 0, 0),
                "invalid use rejected before world access");
        }
        List<ItemStack> variants = new ArrayList<>();
        block.getSubBlocks(item, null, variants);
        require(variants.size() == 3, "only three catalogue variants");
        for (int meta = 1; meta <= 3; meta++) {
            FixtureWorld world = world();
            ItemStack stack = new ItemStack(item, 1, meta);
            NBTTagCompound malicious = new NBTTagCompound();
            malicious.setString("type", "NATURAL");
            malicious.setInteger("attribute", TileRunawaySingularity.ATTRIBUTE_NATURE);
            malicious.setDouble("speed", 100);
            malicious.setInteger("x", 999);
            stack.setTagCompound(new NBTTagCompound());
            stack.getTagCompound()
                .setTag("BlockEntityTag", malicious);
            stack.getTagCompound()
                .setTag("gtsrSingularity", malicious.copy());
            require(item.placeBlockAt(stack, null, world, 0, 64, 0, 1, 0, 0, 0, 1), "current item placement");
            SingularityType type = SingularityType.values()[meta - 1];
            require(world.node.getType() == type && world.meta == meta, "declared metadata owns placement identity");
            require(
                world.node.getAttributeId() == TileRunawaySingularity.ATTRIBUTE_NULL && world.node.getSpeed() == 0
                    && world.node.getDamage() == 0
                    && world.node.getDuration() == 600,
                "hand placement retains original safe decorative parameters");
            require(world.node.xCoord == 0 && world.node.yCoord == 64, "item NBT cannot move tile coordinates");
            require(ItemBlockRunawaySingularity.variantIndex(stack) == meta, "current metadata preserved");
            require(
                item.getUnlocalizedName(stack)
                    .endsWith(new String[] { "natural", "stable", "runaway" }[meta - 1]),
                "name and classification agree");
            require(
                variants.get(meta - 1)
                    .getItemDamage() == meta,
                "catalogue keeps current metadata numbering");
            world.meta = 0; // Existing world metadata alone never determines the live Tile identity.
            require(
                block.getPickBlock(null, world, 0, 64, 0)
                    .getItemDamage() == meta,
                "Waila five-argument pick");
            ItemStack pick = block.getPickBlock(null, world, 0, 64, 0, null);
            require(
                pick.getItemDamage() == meta && !pick.hasTagCompound(),
                "Forge pick is classified safe default only");
        }
        BlockSpacetimeSingularity spacetime = new BlockSpacetimeSingularity();
        GameRegistry.registerBlock(spacetime, ItemBlockSpacetimeSingularity.class, "contract_spacetime");
        ItemBlockSpacetimeSingularity spacetimeItem = (ItemBlockSpacetimeSingularity) Item.getItemFromBlock(spacetime);
        FixtureWorld world = world();
        require(
            spacetimeItem.placeBlockAt(new ItemStack(spacetimeItem), null, world, 0, 64, 0, 1, 0, 0, 0, 0),
            "unrelated spacetime metadata zero remains placeable");
        require(
            spacetime.getPickBlock(null, world, 0, 64, 0)
                .getItemDamage() == 0,
            "spacetime subclass retains its own metadata");
    }

    private static void spawnAndNbt(BlockRunawaySingularity block) throws Exception {
        for (SingularityType type : SingularityType.values()) {
            FixtureWorld world = world();
            TileRunawaySingularity.spawnSingularity(world, 0, 64, 0, 13, 2, 3, 333, 7, "red", 17, false, type);
            require(
                world.current == block && world.meta == ItemBlockRunawaySingularity.metadataForType(type),
                "direct spawn carries canonical metadata");
            NBTTagCompound saved = new NBTTagCompound();
            world.node.writeToNBT(saved);
            TileRunawaySingularity restored = new TileRunawaySingularity();
            restored.readFromNBT(saved);
            require(
                restored.getType() == type && restored.getRange() == 13
                    && restored.getSpeed() == 2
                    && restored.getDamage() == 3
                    && restored.getDuration() == 333
                    && restored.getAttributeId() == 7
                    && restored.getColor()
                        .equals("red")
                    && restored.getFxRadius() == 17,
                "explicit business parameters and type survive NBT round-trip");
            NBTTagCompound savedAgain = new NBTTagCompound();
            restored.writeToNBT(savedAgain);
            require(
                !savedAgain.getCompoundTag("gtsrSingularity")
                    .getBoolean("destroyBlocks"),
                "terrain-destruction setting survives NBT round-trip");
        }
    }

    private static void harvesting(BlockRunawaySingularity block) throws Exception {
        FixturePlayer player = player();
        boolean sawOne = false, sawTwo = false;
        for (int seed = 0; seed < 32; seed++) {
            FixtureWorld world = world();
            set(World.class, world, "rand", new Random(seed * 4_096L));
            world.setBlock(0, 64, 0, block, 1, 3);
            require(block.getBlockHardness(world, 0, 64, 0) == 4, "natural type is mineable even with safe attrNULL");
            require(block.removedByPlayer(world, player, 0, 64, 0, true), "natural removal succeeds");
            int count = world.drops.stream()
                .mapToInt(e -> e.getEntityItem().stackSize)
                .sum();
            require(count >= 1 && count <= 2, "one natural node gives one or two ordinary singularities");
            sawOne |= count == 1;
            sawTwo |= count == 2;
            for (EntityItem drop : world.drops) {
                int delay = drop.getEntityData()
                    .getInteger("gtsrDropDelay");
                require(delay >= 200 && delay <= 400, "original 10-20 second pickup window retained");
                require(
                    drop.getEntityItem()
                        .getItem() == GTSRItemList.SteamEntangledSingularity.getItem() && !drop.isDead
                        && !drop.getEntityData()
                            .getBoolean("gtsrExploded"),
                    "ordinary, live timed drop");
            }
            int spawned = world.drops.size();
            require(!block.removedByPlayer(world, player, 0, 64, 0, true), "repeated removal cannot drop twice");
            require(
                block.getDrops(world, 0, 64, 0, 1, 100)
                    .isEmpty() && !block.canSilkHarvest(),
                "normal harvest, fortune, Silk Touch and explosion drop lookup cannot add a second reward");
            require(world.drops.size() == spawned, "ordinary drop lookup did not spawn entities");
            require(!world.spawnedBeforeRemoval, "reward appears only after the node was removed");
        }
        require(sawOne && sawTwo, "both inclusive count endpoints exercised");
        FixtureWorld failed = world();
        failed.setBlock(0, 64, 0, block, 1, 3);
        failed.rejectRemoval = true;
        require(
            !block.removedByPlayer(failed, player, 0, 64, 0, true) && failed.drops.isEmpty(),
            "failed removal gives no reward");
        for (int meta : new int[] { 2, 3 }) {
            FixtureWorld protectedWorld = world();
            protectedWorld.setBlock(0, 64, 0, block, meta, 3);
            protectedWorld.node.setParams(10, 1, 0, -1, TileRunawaySingularity.ATTRIBUTE_NATURE, "white", 10);
            require(
                block.getBlockHardness(protectedWorld, 0, 64, 0) < 0,
                "nature attribute cannot demote stable/runaway identity");
            require(
                !block.removedByPlayer(protectedWorld, player, 0, 64, 0, true) && protectedWorld.drops.isEmpty(),
                "stable/runaway cannot be harvested for natural rewards");
        }
        FixtureWorld creative = world();
        creative.setBlock(0, 64, 0, block, 1, 3);
        player.capabilities.isCreativeMode = true;
        require(
            block.removedByPlayer(creative, player, 0, 64, 0, false) && creative.drops.isEmpty(),
            "creative removal gives no survival reward");
        FixtureWorld externalBlast = world();
        externalBlast.setBlock(0, 64, 0, block, 1, 3);
        Explosion explosion = new Explosion(externalBlast, null, .5, 64.5, .5, 4);
        require(
            block.getDrops(externalBlast, 0, 64, 0, 1, 0)
                .isEmpty(),
            "native explosion drop lookup adds no reward");
        block.onBlockExploded(externalBlast, 0, 64, 0, explosion);
        require(
            externalBlast.current == Blocks.air && externalBlast.drops.isEmpty(),
            "native explosion removal cannot re-enter player harvest rewards");
    }

    private static void knockback() throws Exception {
        FixtureWorld world = world();
        FixturePlayer player = player();
        player.setPosition(2.5, 64.5, .5);
        world.nearby = new ArrayList<>(Arrays.asList(player, player));
        Explosion visual = new Explosion(world, null, .5, 64.5, .5, 4);
        Method knockback = SingularityDropExplosion.class.getDeclaredMethod(
            "knockbackEntities",
            World.class,
            Explosion.class,
            double.class,
            double.class,
            double.class,
            float.class);
        knockback.setAccessible(true);
        knockback.invoke(null, world, visual, .5, 64.5, .5, 4F);
        Vec3 impulse = (Vec3) visual.func_77277_b()
            .get(player);
        require(
            Math.abs(player.motionX - .75) < .000001 && player.motionY == 0 && player.motionZ == 0,
            "nature applies one server impulse even if an event repeats an entity");
        require(
            impulse != null && Math.abs(impulse.xCoord - .75) < .000001,
            "S27 player impulse map populated before visual broadcast");
        require(player.attacks == 0, "nature knockback never applies HP damage");
    }

    private static FixtureWorld world() throws Exception {
        FixtureWorld world = (FixtureWorld) allocator.allocateInstance(FixtureWorld.class);
        set(World.class, world, "rand", new Random(0));
        set(World.class, world, "provider", new WorldProviderSurface());
        set(World.class, world, "playerEntities", new ArrayList<>());
        world.drops = new ArrayList<>();
        world.nearby = new ArrayList<>();
        world.current = Blocks.air;
        return world;
    }

    private static FixturePlayer player() throws Exception {
        FixturePlayer player = (FixturePlayer) allocator.allocateInstance(FixturePlayer.class);
        player.capabilities = new PlayerCapabilities();
        player.inventory = new InventoryPlayer(player);
        set(Entity.class, player, "boundingBox", AxisAlignedBB.getBoundingBox(0, 0, 0, 1, 1, 1));
        return player;
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static final class EventBootstrap {

        public static void main(String[] args) throws Exception {
            Launch.classLoader.registerTransformer(ForgeEventTransformer.class.getName());
            SingularityIdentityDropContract.main(args);
        }
    }

    /** Forge's old ASM transformer is needed only for native events, not modern GT265 dependency classes. */
    public static final class ForgeEventTransformer implements net.minecraft.launchwrapper.IClassTransformer {

        private final cpw.mods.fml.common.asm.transformers.EventSubscriptionTransformer events = new cpw.mods.fml.common.asm.transformers.EventSubscriptionTransformer();

        @Override
        public byte[] transform(String name, String transformedName, byte[] basicClass) {
            return transformedName.startsWith("net.minecraftforge.event.")
                ? events.transform(name, transformedName, basicClass)
                : basicClass;
        }
    }

    private static final class FixtureWorld extends World {

        private Block current;
        private TileRunawaySingularity node;
        private int meta;
        private boolean rejectRemoval, spawnedBeforeRemoval;
        private List<EntityItem> drops;
        private List<Entity> nearby;

        private FixtureWorld() {
            super(null, "", (net.minecraft.world.WorldProvider) null, null, null);
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return null;
        }

        @Override
        protected int func_152379_p() {
            return 0;
        }

        @Override
        public Entity getEntityByID(int id) {
            return null;
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            return x == 0 && y == 64 && z == 0 ? current : Blocks.air;
        }

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            return meta;
        }

        @Override
        public TileEntity getTileEntity(int x, int y, int z) {
            return node;
        }

        @Override
        public boolean setBlock(int x, int y, int z, Block block, int metadata, int flags) {
            if (block == Blocks.air && (rejectRemoval || current == Blocks.air)) return false;
            current = block;
            meta = metadata;
            node = block instanceof BlockRunawaySingularity
                ? (TileRunawaySingularity) block.createTileEntity(this, metadata)
                : null;
            if (node != null) {
                node.setWorldObj(this);
                node.xCoord = x;
                node.yCoord = y;
                node.zCoord = z;
            }
            return true;
        }

        @Override
        public void markBlockForUpdate(int x, int y, int z) {}

        @Override
        public void markTileEntityChunkModified(int x, int y, int z, TileEntity tile) {}

        @Override
        public boolean spawnEntityInWorld(Entity entity) {
            spawnedBeforeRemoval |= current != Blocks.air;
            drops.add((EntityItem) entity);
            return true;
        }

        @Override
        public <T> List<T> getEntitiesWithinAABB(Class<T> type, AxisAlignedBB box) {
            List<T> result = new ArrayList<>();
            for (Entity entity : nearby) if (type.isInstance(entity)) result.add(type.cast(entity));
            return result;
        }

        @Override
        public List<Entity> getEntitiesWithinAABBExcludingEntity(Entity entity, AxisAlignedBB box) {
            throw new AssertionError("Nature harvesting must never execute real doExplosionA");
        }

        @Override
        public float getBlockDensity(Vec3 center, AxisAlignedBB box) {
            return 1;
        }
    }

    private static final class FixturePlayer extends EntityPlayer {

        private int attacks;

        private FixturePlayer() {
            super(null, null);
        }

        @Override
        public float getEyeHeight() {
            return 0;
        }

        @Override
        public boolean attackEntityFrom(net.minecraft.util.DamageSource source, float damage) {
            attacks++;
            return true;
        }

        @Override
        public void addChatMessage(IChatComponent message) {}

        @Override
        public boolean canCommandSenderUseCommand(int level, String command) {
            return false;
        }

        @Override
        public ChunkCoordinates getPlayerCoordinates() {
            return new ChunkCoordinates(0, 64, 0);
        }
    }
}
