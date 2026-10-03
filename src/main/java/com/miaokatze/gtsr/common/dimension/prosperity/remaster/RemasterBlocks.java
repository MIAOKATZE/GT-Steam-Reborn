package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.miaokatze.gtsr.register.CreativeTabManager;

import cpw.mods.fml.common.registry.GameRegistry;

/** Only materials used by the three current scenes are registered. */
public final class RemasterBlocks {

    private static final Map<String, RemasterBlock> REGISTRY = new LinkedHashMap<>();
    public static final Map<String, RemasterBlock> BLOCKS = Collections.unmodifiableMap(REGISTRY);
    public static int renderId;
    private static InteractionHandler handler;

    private RemasterBlocks() {}

    public static RemasterBlock get(String id) {
        return REGISTRY.get(id);
    }

    public static void setInteractionHandler(InteractionHandler value) {
        handler = value;
    }

    static InteractionHandler interactionHandler() {
        return handler;
    }

    public interface InteractionHandler {

        boolean activate(World world, int x, int y, int z, EntityPlayer player);

        default TileEntity createTileEntity(World world, int meta, String id) {
            return null;
        }

        default float hardness(World world, int x, int y, int z, float fallback) {
            return fallback;
        }
    }

    /** Direction vocabularies differ between authored revisions; preserve inversion and vine attachments. */
    public static int rotateMetadata(String id, int meta, int quarterTurns) {
        int turns = Math.floorMod(quarterTurns, 4);
        if (id.equals("gtsr:draft5_corrosion_flake")) {
            int value = meta & 15;
            while (turns-- > 0) value = ((value << 1) & 15) | (value >>> 3);
            return value;
        }
        if (id.equals("gtsr:draft6_rubble_slab") && meta == 4) return meta;
        if (id.startsWith("gtsr:draft_")) return (meta & ~3) | ((meta + turns) & 3);
        int[] clockwise = { 2, 3, 1, 0 };
        int direction = meta & 3;
        while (turns-- > 0) direction = clockwise[direction];
        return (meta & ~3) | direction;
    }

    public static void registerBlocks() {
        if (!REGISTRY.isEmpty()) return;
        register(block7());
        register(block8());
        register(block11());
        register(block13());
        register(block40());
        register(block70());
        register(block71());
        register(block72());
        register(block74());
        register(block83());
        register(block82());
        register(block80());
        register(block63());
        register(block58());
        register(block54());
        register(block51());
        register(block50());
        register(block29());
        register(block12());
        register(block9());
        register(block3());
        register(block0());
    }

    private static void register(RemasterBlock block) {
        String name = block.id()
            .substring(
                block.id()
                    .indexOf(':') + 1);
        GameRegistry.registerBlock(block, RemasterItem.class, name);
        REGISTRY.put(block.id(), block);
        CreativeTabManager.addItemToTab(new ItemStack(block));
    }

    public static final class RemasterItem extends ItemBlock {

        public RemasterItem(Block block) {
            super(block);
            setHasSubtypes(true);
            setMaxDamage(0);
        }

        @Override
        public int getMetadata(int damage) {
            return damage & 15;
        }
    }

    private static RemasterBlock block7() {
        return new RemasterBlock(
            "gtsr:draft_parts_drawer",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_44b49a3fad54b92a", "gtsr:remaster_44b49a3fad54b92a",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_44b49a3fad54b92a", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_44b49a3fad54b92a" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_44b49a3fad54b92a", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_44b49a3fad54b92a", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_44b49a3fad54b92a", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_44b49a3fad54b92a",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block8() {
        return new RemasterBlock(
            "gtsr:draft_casting_mould",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_2f595d35254e38eb", "gtsr:remaster_2f595d35254e38eb",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_2f595d35254e38eb", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_2f595d35254e38eb" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_2f595d35254e38eb", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_2f595d35254e38eb", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_2f595d35254e38eb", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_2f595d35254e38eb",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block11() {
        return new RemasterBlock(
            "gtsr:draft_cargo_crate",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_6b397a56352c5001", "gtsr:remaster_6b397a56352c5001",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_6b397a56352c5001", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_6b397a56352c5001" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_6b397a56352c5001", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_6b397a56352c5001", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_6b397a56352c5001", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_6b397a56352c5001",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block13() {
        return new RemasterBlock(
            "gtsr:draft_archive_drawer",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_360db043f98417fe", "gtsr:remaster_360db043f98417fe",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_360db043f98417fe", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_360db043f98417fe" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_360db043f98417fe", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_360db043f98417fe", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_360db043f98417fe", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_360db043f98417fe",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block40() {
        return new RemasterBlock(
            "gtsr:draft_pressure_console",
            "operation",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_1a61b0ae0664483a",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_1a61b0ae0664483a" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_1a61b0ae0664483a", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_1a61b0ae0664483a",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block74() {
        return new RemasterBlock(
            "gtsr:draft6_workbench",
            "decoration",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0.0625, 0, 0.0625, 0.1875, 0.75, 0.1875 }, { 0.0625, 0, 0.8125, 0.1875, 0.75, 0.9375 },
                    { 0.8125, 0, 0.0625, 0.9375, 0.75, 0.1875 }, { 0.8125, 0, 0.8125, 0.9375, 0.75, 0.9375 },
                    { 0, 0.75, 0, 1, 0.9375, 1 }, { 0.125, 0.1875, 0.125, 0.875, 0.25, 0.875 },
                    { 0.125, 0.5625, 0.75, 0.875, 0.6875, 0.875 } },
                { { 0.8125, 0, 0.8125, 0.9375, 0.75, 0.9375 }, { 0.8125, 0, 0.0625, 0.9375, 0.75, 0.1875 },
                    { 0.0625, 0, 0.8125, 0.1875, 0.75, 0.9375 }, { 0.0625, 0, 0.0625, 0.1875, 0.75, 0.1875 },
                    { 0, 0.75, 0, 1, 0.9375, 1 }, { 0.125, 0.1875, 0.125, 0.875, 0.25, 0.875 },
                    { 0.125, 0.5625, 0.125, 0.875, 0.6875, 0.25 } },
                { { 0.8125, 0, 0.0625, 0.9375, 0.75, 0.1875 }, { 0.0625, 0, 0.0625, 0.1875, 0.75, 0.1875 },
                    { 0.8125, 0, 0.8125, 0.9375, 0.75, 0.9375 }, { 0.0625, 0, 0.8125, 0.1875, 0.75, 0.9375 },
                    { 0, 0.75, 0, 1, 0.9375, 1 }, { 0.125, 0.1875, 0.125, 0.875, 0.25, 0.875 },
                    { 0.125, 0.5625, 0.125, 0.25, 0.6875, 0.875 } },
                { { 0.0625, 0, 0.8125, 0.1875, 0.75, 0.9375 }, { 0.8125, 0, 0.8125, 0.9375, 0.75, 0.9375 },
                    { 0.0625, 0, 0.0625, 0.1875, 0.75, 0.1875 }, { 0.8125, 0, 0.0625, 0.9375, 0.75, 0.1875 },
                    { 0, 0.75, 0, 1, 0.9375, 1 }, { 0.125, 0.1875, 0.125, 0.875, 0.25, 0.875 },
                    { 0.75, 0.5625, 0.125, 0.875, 0.6875, 0.875 } } },
            new String[][] {
                { "gtsr:remaster_8c5699ab901745c7", "gtsr:remaster_8c5699ab901745c7", "gtsr:remaster_8c5699ab901745c7",
                    "gtsr:remaster_8c5699ab901745c7", "gtsr:remaster_8c5699ab901745c7",
                    "gtsr:remaster_8c5699ab901745c7" },
                { "gtsr:remaster_8c5699ab901745c7", "gtsr:remaster_8c5699ab901745c7", "gtsr:remaster_8c5699ab901745c7",
                    "gtsr:remaster_8c5699ab901745c7", "gtsr:remaster_8c5699ab901745c7",
                    "gtsr:remaster_8c5699ab901745c7" },
                { "gtsr:remaster_8c5699ab901745c7", "gtsr:remaster_8c5699ab901745c7", "gtsr:remaster_8c5699ab901745c7",
                    "gtsr:remaster_8c5699ab901745c7", "gtsr:remaster_8c5699ab901745c7",
                    "gtsr:remaster_8c5699ab901745c7" },
                { "gtsr:remaster_8c5699ab901745c7", "gtsr:remaster_8c5699ab901745c7", "gtsr:remaster_8c5699ab901745c7",
                    "gtsr:remaster_8c5699ab901745c7", "gtsr:remaster_8c5699ab901745c7",
                    "gtsr:remaster_8c5699ab901745c7" } });
    }

    private static RemasterBlock block0() {
        return new RemasterBlock(
            "gtsr:draft_roof_tile",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_fe7e76f56cd26c3e", "gtsr:remaster_fe7e76f56cd26c3e",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_fe7e76f56cd26c3e", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_fe7e76f56cd26c3e" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_fe7e76f56cd26c3e", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_fe7e76f56cd26c3e", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_fe7e76f56cd26c3e", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_fe7e76f56cd26c3e",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block3() {
        return new RemasterBlock(
            "gtsr:draft_vent_louver",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_d0986dd9badbfb6a", "gtsr:remaster_d0986dd9badbfb6a",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_d0986dd9badbfb6a", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_d0986dd9badbfb6a" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_d0986dd9badbfb6a", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_d0986dd9badbfb6a", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_d0986dd9badbfb6a", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_d0986dd9badbfb6a",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block9() {
        return new RemasterBlock(
            "gtsr:draft_coal_bin",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_b9cfe3542aece14c", "gtsr:remaster_b9cfe3542aece14c",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_b9cfe3542aece14c", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_b9cfe3542aece14c" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_b9cfe3542aece14c", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_b9cfe3542aece14c", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_b9cfe3542aece14c", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_b9cfe3542aece14c",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block12() {
        return new RemasterBlock(
            "gtsr:draft_reel",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_aa7d0b96068d1cea", "gtsr:remaster_aa7d0b96068d1cea",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_aa7d0b96068d1cea", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_aa7d0b96068d1cea" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_aa7d0b96068d1cea", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_aa7d0b96068d1cea", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_aa7d0b96068d1cea", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_aa7d0b96068d1cea",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block29() {
        return new RemasterBlock(
            "gtsr:draft_condenser_fin",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_d0e7eba6f07bef52", "gtsr:remaster_d0e7eba6f07bef52",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_d0e7eba6f07bef52", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_d0e7eba6f07bef52" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_d0e7eba6f07bef52", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_d0e7eba6f07bef52", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_d0e7eba6f07bef52", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_d0e7eba6f07bef52",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block50() {
        return new RemasterBlock(
            "gtsr:draft5_pressure_band",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_9ba1eb1797f056ca", "gtsr:remaster_9ba1eb1797f056ca", "gtsr:remaster_9ba1eb1797f056ca",
                    "gtsr:remaster_9ba1eb1797f056ca", "gtsr:remaster_9ba1eb1797f056ca",
                    "gtsr:remaster_9ba1eb1797f056ca" },
                { "gtsr:remaster_9ba1eb1797f056ca", "gtsr:remaster_9ba1eb1797f056ca", "gtsr:remaster_9ba1eb1797f056ca",
                    "gtsr:remaster_9ba1eb1797f056ca", "gtsr:remaster_9ba1eb1797f056ca",
                    "gtsr:remaster_9ba1eb1797f056ca" },
                { "gtsr:remaster_9ba1eb1797f056ca", "gtsr:remaster_9ba1eb1797f056ca", "gtsr:remaster_9ba1eb1797f056ca",
                    "gtsr:remaster_9ba1eb1797f056ca", "gtsr:remaster_9ba1eb1797f056ca",
                    "gtsr:remaster_9ba1eb1797f056ca" },
                { "gtsr:remaster_9ba1eb1797f056ca", "gtsr:remaster_9ba1eb1797f056ca", "gtsr:remaster_9ba1eb1797f056ca",
                    "gtsr:remaster_9ba1eb1797f056ca", "gtsr:remaster_9ba1eb1797f056ca",
                    "gtsr:remaster_9ba1eb1797f056ca" } });
    }

    private static RemasterBlock block51() {
        return new RemasterBlock(
            "gtsr:draft5_tank_access_hatch",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_263bfab37982b3e4", "gtsr:remaster_263bfab37982b3e4", "gtsr:remaster_263bfab37982b3e4",
                    "gtsr:remaster_263bfab37982b3e4", "gtsr:remaster_263bfab37982b3e4",
                    "gtsr:remaster_263bfab37982b3e4" },
                { "gtsr:remaster_263bfab37982b3e4", "gtsr:remaster_263bfab37982b3e4", "gtsr:remaster_263bfab37982b3e4",
                    "gtsr:remaster_263bfab37982b3e4", "gtsr:remaster_263bfab37982b3e4",
                    "gtsr:remaster_263bfab37982b3e4" },
                { "gtsr:remaster_263bfab37982b3e4", "gtsr:remaster_263bfab37982b3e4", "gtsr:remaster_263bfab37982b3e4",
                    "gtsr:remaster_263bfab37982b3e4", "gtsr:remaster_263bfab37982b3e4",
                    "gtsr:remaster_263bfab37982b3e4" },
                { "gtsr:remaster_263bfab37982b3e4", "gtsr:remaster_263bfab37982b3e4", "gtsr:remaster_263bfab37982b3e4",
                    "gtsr:remaster_263bfab37982b3e4", "gtsr:remaster_263bfab37982b3e4",
                    "gtsr:remaster_263bfab37982b3e4" } });
    }

    private static RemasterBlock block54() {
        return new RemasterBlock(
            "gtsr:draft5_casting_press",
            "decoration",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0.08, 0, 0.08, 0.92, 0.2, 0.92 }, { 0.12, 0.2, 0.15, 0.24, 1, 0.85 },
                    { 0.76, 0.2, 0.15, 0.88, 1, 0.85 }, { 0.2, 0.8, 0.15, 0.8, 1, 0.85 },
                    { 0.44, 0.2, 0.3, 0.56, 0.8, 0.7 } },
                { { 0.08, 0, 0.08, 0.92, 0.2, 0.92 }, { 0.12, 0.2, 0.15, 0.24, 1, 0.85 },
                    { 0.76, 0.2, 0.15, 0.88, 1, 0.85 }, { 0.2, 0.8, 0.15, 0.8, 1, 0.85 },
                    { 0.44, 0.2, 0.3, 0.56, 0.8, 0.7 } },
                { { 0.08, 0, 0.08, 0.92, 0.2, 0.92 }, { 0.12, 0.2, 0.15, 0.24, 1, 0.85 },
                    { 0.76, 0.2, 0.15, 0.88, 1, 0.85 }, { 0.2, 0.8, 0.15, 0.8, 1, 0.85 },
                    { 0.44, 0.2, 0.3, 0.56, 0.8, 0.7 } },
                { { 0.08, 0, 0.08, 0.92, 0.2, 0.92 }, { 0.12, 0.2, 0.15, 0.24, 1, 0.85 },
                    { 0.76, 0.2, 0.15, 0.88, 1, 0.85 }, { 0.2, 0.8, 0.15, 0.8, 1, 0.85 },
                    { 0.44, 0.2, 0.3, 0.56, 0.8, 0.7 } } },
            new String[][] {
                { "gtsr:remaster_37f6ce2baf63efce", "gtsr:remaster_37f6ce2baf63efce", "gtsr:remaster_37f6ce2baf63efce",
                    "gtsr:remaster_37f6ce2baf63efce", "gtsr:remaster_37f6ce2baf63efce",
                    "gtsr:remaster_37f6ce2baf63efce" },
                { "gtsr:remaster_37f6ce2baf63efce", "gtsr:remaster_37f6ce2baf63efce", "gtsr:remaster_37f6ce2baf63efce",
                    "gtsr:remaster_37f6ce2baf63efce", "gtsr:remaster_37f6ce2baf63efce",
                    "gtsr:remaster_37f6ce2baf63efce" },
                { "gtsr:remaster_37f6ce2baf63efce", "gtsr:remaster_37f6ce2baf63efce", "gtsr:remaster_37f6ce2baf63efce",
                    "gtsr:remaster_37f6ce2baf63efce", "gtsr:remaster_37f6ce2baf63efce",
                    "gtsr:remaster_37f6ce2baf63efce" },
                { "gtsr:remaster_37f6ce2baf63efce", "gtsr:remaster_37f6ce2baf63efce", "gtsr:remaster_37f6ce2baf63efce",
                    "gtsr:remaster_37f6ce2baf63efce", "gtsr:remaster_37f6ce2baf63efce",
                    "gtsr:remaster_37f6ce2baf63efce" } });
    }

    private static RemasterBlock block58() {
        return new RemasterBlock(
            "gtsr:draft5_catwalk_railing",
            "decoration",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0, 0, 0.43, 0.12, 1, 0.57 }, { 0.88, 0, 0.43, 1, 1, 0.57 }, { 0, 0.42, 0.43, 1, 0.5, 0.57 },
                    { 0, 0.9, 0.43, 1, 0.98, 0.57 } },
                { { 0.43, 0, 0, 0.57, 1, 0.12 }, { 0.43, 0, 0.88, 0.57, 1, 1 }, { 0.43, 0.42, 0, 0.57, 0.5, 1 },
                    { 0.43, 0.9, 0, 0.57, 0.98, 1 } },
                { { 0, 0, 0.43, 0.12, 1, 0.57 }, { 0.88, 0, 0.43, 1, 1, 0.57 }, { 0, 0.42, 0.43, 1, 0.5, 0.57 },
                    { 0, 0.9, 0.43, 1, 0.98, 0.57 } },
                { { 0.43, 0, 0, 0.57, 1, 0.12 }, { 0.43, 0, 0.88, 0.57, 1, 1 }, { 0.43, 0.42, 0, 0.57, 0.5, 1 },
                    { 0.43, 0.9, 0, 0.57, 0.98, 1 } } },
            new String[][] {
                { "gtsr:remaster_4d5dd51ea345de8e", "gtsr:remaster_4d5dd51ea345de8e", "gtsr:remaster_4d5dd51ea345de8e",
                    "gtsr:remaster_4d5dd51ea345de8e", "gtsr:remaster_4d5dd51ea345de8e",
                    "gtsr:remaster_4d5dd51ea345de8e" },
                { "gtsr:remaster_4d5dd51ea345de8e", "gtsr:remaster_4d5dd51ea345de8e", "gtsr:remaster_4d5dd51ea345de8e",
                    "gtsr:remaster_4d5dd51ea345de8e", "gtsr:remaster_4d5dd51ea345de8e",
                    "gtsr:remaster_4d5dd51ea345de8e" },
                { "gtsr:remaster_4d5dd51ea345de8e", "gtsr:remaster_4d5dd51ea345de8e", "gtsr:remaster_4d5dd51ea345de8e",
                    "gtsr:remaster_4d5dd51ea345de8e", "gtsr:remaster_4d5dd51ea345de8e",
                    "gtsr:remaster_4d5dd51ea345de8e" },
                { "gtsr:remaster_4d5dd51ea345de8e", "gtsr:remaster_4d5dd51ea345de8e", "gtsr:remaster_4d5dd51ea345de8e",
                    "gtsr:remaster_4d5dd51ea345de8e", "gtsr:remaster_4d5dd51ea345de8e",
                    "gtsr:remaster_4d5dd51ea345de8e" } });
    }

    private static RemasterBlock block63() {
        return new RemasterBlock(
            "gtsr:draft5_nest_filament",
            "decoration",
            false,
            true,
            "cross",
            new double[][][] { {}, {}, {}, {} },
            new String[][] {
                { "gtsr:remaster_23ae657536306a2d", "gtsr:remaster_23ae657536306a2d", "gtsr:remaster_23ae657536306a2d",
                    "gtsr:remaster_23ae657536306a2d", "gtsr:remaster_23ae657536306a2d",
                    "gtsr:remaster_23ae657536306a2d" },
                { "gtsr:remaster_23ae657536306a2d", "gtsr:remaster_23ae657536306a2d", "gtsr:remaster_23ae657536306a2d",
                    "gtsr:remaster_23ae657536306a2d", "gtsr:remaster_23ae657536306a2d",
                    "gtsr:remaster_23ae657536306a2d" },
                { "gtsr:remaster_23ae657536306a2d", "gtsr:remaster_23ae657536306a2d", "gtsr:remaster_23ae657536306a2d",
                    "gtsr:remaster_23ae657536306a2d", "gtsr:remaster_23ae657536306a2d",
                    "gtsr:remaster_23ae657536306a2d" },
                { "gtsr:remaster_23ae657536306a2d", "gtsr:remaster_23ae657536306a2d", "gtsr:remaster_23ae657536306a2d",
                    "gtsr:remaster_23ae657536306a2d", "gtsr:remaster_23ae657536306a2d",
                    "gtsr:remaster_23ae657536306a2d" } });
    }

    private static RemasterBlock block80() {
        return new RemasterBlock(
            "gtsr:draft6_drain_grate",
            "decoration",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0, 0, 0, 1, 0.125, 0.125 }, { 0, 0, 0.875, 1, 0.125, 1 }, { 0, 0, 0.125, 0.0625, 0.125, 0.875 },
                    { 0.1875, 0, 0.125, 0.25, 0.125, 0.875 }, { 0.375, 0, 0.125, 0.4375, 0.125, 0.875 },
                    { 0.5625, 0, 0.125, 0.625, 0.125, 0.875 }, { 0.75, 0, 0.125, 0.8125, 0.125, 0.875 },
                    { 0.9375, 0, 0.125, 1, 0.125, 0.875 } },
                { { 0, 0, 0.875, 1, 0.125, 1 }, { 0, 0, 0, 1, 0.125, 0.125 }, { 0.9375, 0, 0.125, 1, 0.125, 0.875 },
                    { 0.75, 0, 0.125, 0.8125, 0.125, 0.875 }, { 0.5625, 0, 0.125, 0.625, 0.125, 0.875 },
                    { 0.375, 0, 0.125, 0.4375, 0.125, 0.875 }, { 0.1875, 0, 0.125, 0.25, 0.125, 0.875 },
                    { 0, 0, 0.125, 0.0625, 0.125, 0.875 } },
                { { 0.875, 0, 0, 1, 0.125, 1 }, { 0, 0, 0, 0.125, 0.125, 1 }, { 0.125, 0, 0, 0.875, 0.125, 0.0625 },
                    { 0.125, 0, 0.1875, 0.875, 0.125, 0.25 }, { 0.125, 0, 0.375, 0.875, 0.125, 0.4375 },
                    { 0.125, 0, 0.5625, 0.875, 0.125, 0.625 }, { 0.125, 0, 0.75, 0.875, 0.125, 0.8125 },
                    { 0.125, 0, 0.9375, 0.875, 0.125, 1 } },
                { { 0, 0, 0, 0.125, 0.125, 1 }, { 0.875, 0, 0, 1, 0.125, 1 }, { 0.125, 0, 0.9375, 0.875, 0.125, 1 },
                    { 0.125, 0, 0.75, 0.875, 0.125, 0.8125 }, { 0.125, 0, 0.5625, 0.875, 0.125, 0.625 },
                    { 0.125, 0, 0.375, 0.875, 0.125, 0.4375 }, { 0.125, 0, 0.1875, 0.875, 0.125, 0.25 },
                    { 0.125, 0, 0, 0.875, 0.125, 0.0625 } } },
            new String[][] {
                { "gtsr:remaster_74fb70069258fb55", "gtsr:remaster_74fb70069258fb55", "gtsr:remaster_74fb70069258fb55",
                    "gtsr:remaster_74fb70069258fb55", "gtsr:remaster_74fb70069258fb55",
                    "gtsr:remaster_74fb70069258fb55" },
                { "gtsr:remaster_74fb70069258fb55", "gtsr:remaster_74fb70069258fb55", "gtsr:remaster_74fb70069258fb55",
                    "gtsr:remaster_74fb70069258fb55", "gtsr:remaster_74fb70069258fb55",
                    "gtsr:remaster_74fb70069258fb55" },
                { "gtsr:remaster_74fb70069258fb55", "gtsr:remaster_74fb70069258fb55", "gtsr:remaster_74fb70069258fb55",
                    "gtsr:remaster_74fb70069258fb55", "gtsr:remaster_74fb70069258fb55",
                    "gtsr:remaster_74fb70069258fb55" },
                { "gtsr:remaster_74fb70069258fb55", "gtsr:remaster_74fb70069258fb55", "gtsr:remaster_74fb70069258fb55",
                    "gtsr:remaster_74fb70069258fb55", "gtsr:remaster_74fb70069258fb55",
                    "gtsr:remaster_74fb70069258fb55" } });
    }

    private static RemasterBlock block82() {
        return new RemasterBlock(
            "gtsr:draft6_rubble_slab",
            "decoration",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0, 0, 0.0625, 0.4375, 0.1875, 0.5 }, { 0.5, 0, 0, 0.9375, 0.125, 0.375 },
                    { 0.1875, 0, 0.5625, 0.75, 0.25, 0.9375 }, { 0.75, 0, 0.5, 1, 0.125, 0.8125 },
                    { 0.0625, 0, 0.8125, 0.25, 0.0625, 1 } },
                { { 0.5625, 0, 0.5, 1, 0.1875, 0.9375 }, { 0.0625, 0, 0.625, 0.5, 0.125, 1 },
                    { 0.25, 0, 0.0625, 0.8125, 0.25, 0.4375 }, { 0, 0, 0.1875, 0.25, 0.125, 0.5 },
                    { 0.75, 0, 0, 0.9375, 0.0625, 0.1875 } },
                { { 0.5, 0, 0, 0.9375, 0.1875, 0.4375 }, { 0.625, 0, 0.5, 1, 0.125, 0.9375 },
                    { 0.0625, 0, 0.1875, 0.4375, 0.25, 0.75 }, { 0.1875, 0, 0.75, 0.5, 0.125, 1 },
                    { 0, 0, 0.0625, 0.1875, 0.0625, 0.25 } },
                { { 0.0625, 0, 0.5625, 0.5, 0.1875, 1 }, { 0, 0, 0.0625, 0.375, 0.125, 0.5 },
                    { 0.5625, 0, 0.25, 0.9375, 0.25, 0.8125 }, { 0.5, 0, 0, 0.8125, 0.125, 0.25 },
                    { 0.8125, 0, 0.75, 1, 0.0625, 0.9375 } },
                { { 0, 0, 0, 1, 1, 0.1875 } } },
            new String[][] {
                { "gtsr:remaster_628f9a3214506ed0", "gtsr:remaster_628f9a3214506ed0", "gtsr:remaster_628f9a3214506ed0",
                    "gtsr:remaster_628f9a3214506ed0", "gtsr:remaster_628f9a3214506ed0",
                    "gtsr:remaster_628f9a3214506ed0" },
                { "gtsr:remaster_628f9a3214506ed0", "gtsr:remaster_628f9a3214506ed0", "gtsr:remaster_628f9a3214506ed0",
                    "gtsr:remaster_628f9a3214506ed0", "gtsr:remaster_628f9a3214506ed0",
                    "gtsr:remaster_628f9a3214506ed0" },
                { "gtsr:remaster_628f9a3214506ed0", "gtsr:remaster_628f9a3214506ed0", "gtsr:remaster_628f9a3214506ed0",
                    "gtsr:remaster_628f9a3214506ed0", "gtsr:remaster_628f9a3214506ed0",
                    "gtsr:remaster_628f9a3214506ed0" },
                { "gtsr:remaster_628f9a3214506ed0", "gtsr:remaster_628f9a3214506ed0", "gtsr:remaster_628f9a3214506ed0",
                    "gtsr:remaster_628f9a3214506ed0", "gtsr:remaster_628f9a3214506ed0",
                    "gtsr:remaster_628f9a3214506ed0" },
                { "gtsr:remaster_a3849c3a31eda8b8", "gtsr:remaster_a3849c3a31eda8b8", "gtsr:remaster_a3849c3a31eda8b8",
                    "gtsr:remaster_a3849c3a31eda8b8", "gtsr:remaster_a3849c3a31eda8b8",
                    "gtsr:remaster_a3849c3a31eda8b8" } });
    }

    private static RemasterBlock block83() {
        return new RemasterBlock(
            "gtsr:draft6_industrial_stairs",
            "decoration",
            false,
            false,
            "stairs",
            new double[][][] { { { 0, 0, 0, 1, 0.5, 1 }, { 0.5, 0.5, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 0.5, 1 }, { 0, 0.5, 0, 0.5, 1, 1 } },
                { { 0, 0, 0, 1, 0.5, 1 }, { 0, 0.5, 0.5, 1, 1, 1 } },
                { { 0, 0, 0, 1, 0.5, 1 }, { 0, 0.5, 0, 1, 1, 0.5 } },
                { { 0, 0.5, 0, 1, 1, 1 }, { 0.5, 0, 0, 1, 0.5, 1 } },
                { { 0, 0.5, 0, 1, 1, 1 }, { 0, 0, 0, 0.5, 0.5, 1 } },
                { { 0, 0.5, 0, 1, 1, 1 }, { 0, 0, 0.5, 1, 0.5, 1 } },
                { { 0, 0.5, 0, 1, 1, 1 }, { 0, 0, 0, 1, 0.5, 0.5 } } },
            new String[][] {
                { "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722",
                    "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722",
                    "gtsr:remaster_a726e839311f5722" },
                { "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722",
                    "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722",
                    "gtsr:remaster_a726e839311f5722" },
                { "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722",
                    "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722",
                    "gtsr:remaster_a726e839311f5722" },
                { "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722",
                    "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722",
                    "gtsr:remaster_a726e839311f5722" },
                { "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722",
                    "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722",
                    "gtsr:remaster_a726e839311f5722" },
                { "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722",
                    "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722",
                    "gtsr:remaster_a726e839311f5722" },
                { "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722",
                    "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722",
                    "gtsr:remaster_a726e839311f5722" },
                { "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722",
                    "gtsr:remaster_a726e839311f5722", "gtsr:remaster_a726e839311f5722",
                    "gtsr:remaster_a726e839311f5722" } });
    }

    private static RemasterBlock block70() {
        return new RemasterBlock(
            "gtsr:draft6_spawner_fragile",
            "spawner",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0, 0, 0, 0.125, 1, 0.125 }, { 0, 0, 0.875, 0.125, 1, 1 }, { 0.875, 0, 0, 1, 1, 0.125 },
                    { 0.875, 0, 0.875, 1, 1, 1 }, { 0, 0, 0, 1, 0.125, 0.125 }, { 0, 0, 0.875, 1, 0.125, 1 },
                    { 0, 0, 0.125, 0.125, 0.125, 0.875 }, { 0.875, 0, 0.125, 1, 0.125, 0.875 },
                    { 0, 0.875, 0, 1, 1, 0.125 }, { 0, 0.875, 0.875, 1, 1, 1 }, { 0, 0.875, 0.125, 0.125, 1, 0.875 },
                    { 0.875, 0.875, 0.125, 1, 1, 0.875 }, { 0.3125, 0.125, 0, 0.375, 0.875, 0.0625 },
                    { 0, 0.125, 0.3125, 0.0625, 0.875, 0.375 }, { 0.3125, 0.125, 0.9375, 0.375, 0.875, 1 },
                    { 0.9375, 0.125, 0.3125, 1, 0.875, 0.375 }, { 0.625, 0.125, 0, 0.6875, 0.875, 0.0625 },
                    { 0, 0.125, 0.625, 0.0625, 0.875, 0.6875 }, { 0.625, 0.125, 0.9375, 0.6875, 0.875, 1 },
                    { 0.9375, 0.125, 0.625, 1, 0.875, 0.6875 }, { 0.3125, 0, 0.3125, 0.6875, 0.0625, 0.6875 },
                    { 0.3125, 0.9375, 0.3125, 0.6875, 1, 0.6875 } },
                { { 0.875, 0, 0.875, 1, 1, 1 }, { 0.875, 0, 0, 1, 1, 0.125 }, { 0, 0, 0.875, 0.125, 1, 1 },
                    { 0, 0, 0, 0.125, 1, 0.125 }, { 0, 0, 0.875, 1, 0.125, 1 }, { 0, 0, 0, 1, 0.125, 0.125 },
                    { 0.875, 0, 0.125, 1, 0.125, 0.875 }, { 0, 0, 0.125, 0.125, 0.125, 0.875 },
                    { 0, 0.875, 0.875, 1, 1, 1 }, { 0, 0.875, 0, 1, 1, 0.125 }, { 0.875, 0.875, 0.125, 1, 1, 0.875 },
                    { 0, 0.875, 0.125, 0.125, 1, 0.875 }, { 0.625, 0.125, 0.9375, 0.6875, 0.875, 1 },
                    { 0.9375, 0.125, 0.625, 1, 0.875, 0.6875 }, { 0.625, 0.125, 0, 0.6875, 0.875, 0.0625 },
                    { 0, 0.125, 0.625, 0.0625, 0.875, 0.6875 }, { 0.3125, 0.125, 0.9375, 0.375, 0.875, 1 },
                    { 0.9375, 0.125, 0.3125, 1, 0.875, 0.375 }, { 0.3125, 0.125, 0, 0.375, 0.875, 0.0625 },
                    { 0, 0.125, 0.3125, 0.0625, 0.875, 0.375 }, { 0.3125, 0, 0.3125, 0.6875, 0.0625, 0.6875 },
                    { 0.3125, 0.9375, 0.3125, 0.6875, 1, 0.6875 } },
                { { 0.875, 0, 0, 1, 1, 0.125 }, { 0, 0, 0, 0.125, 1, 0.125 }, { 0.875, 0, 0.875, 1, 1, 1 },
                    { 0, 0, 0.875, 0.125, 1, 1 }, { 0.875, 0, 0, 1, 0.125, 1 }, { 0, 0, 0, 0.125, 0.125, 1 },
                    { 0.125, 0, 0, 0.875, 0.125, 0.125 }, { 0.125, 0, 0.875, 0.875, 0.125, 1 },
                    { 0.875, 0.875, 0, 1, 1, 1 }, { 0, 0.875, 0, 0.125, 1, 1 }, { 0.125, 0.875, 0, 0.875, 1, 0.125 },
                    { 0.125, 0.875, 0.875, 0.875, 1, 1 }, { 0.9375, 0.125, 0.3125, 1, 0.875, 0.375 },
                    { 0.625, 0.125, 0, 0.6875, 0.875, 0.0625 }, { 0, 0.125, 0.3125, 0.0625, 0.875, 0.375 },
                    { 0.625, 0.125, 0.9375, 0.6875, 0.875, 1 }, { 0.9375, 0.125, 0.625, 1, 0.875, 0.6875 },
                    { 0.3125, 0.125, 0, 0.375, 0.875, 0.0625 }, { 0, 0.125, 0.625, 0.0625, 0.875, 0.6875 },
                    { 0.3125, 0.125, 0.9375, 0.375, 0.875, 1 }, { 0.3125, 0, 0.3125, 0.6875, 0.0625, 0.6875 },
                    { 0.3125, 0.9375, 0.3125, 0.6875, 1, 0.6875 } },
                { { 0, 0, 0.875, 0.125, 1, 1 }, { 0.875, 0, 0.875, 1, 1, 1 }, { 0, 0, 0, 0.125, 1, 0.125 },
                    { 0.875, 0, 0, 1, 1, 0.125 }, { 0, 0, 0, 0.125, 0.125, 1 }, { 0.875, 0, 0, 1, 0.125, 1 },
                    { 0.125, 0, 0.875, 0.875, 0.125, 1 }, { 0.125, 0, 0, 0.875, 0.125, 0.125 },
                    { 0, 0.875, 0, 0.125, 1, 1 }, { 0.875, 0.875, 0, 1, 1, 1 }, { 0.125, 0.875, 0.875, 0.875, 1, 1 },
                    { 0.125, 0.875, 0, 0.875, 1, 0.125 }, { 0, 0.125, 0.625, 0.0625, 0.875, 0.6875 },
                    { 0.3125, 0.125, 0.9375, 0.375, 0.875, 1 }, { 0.9375, 0.125, 0.625, 1, 0.875, 0.6875 },
                    { 0.3125, 0.125, 0, 0.375, 0.875, 0.0625 }, { 0, 0.125, 0.3125, 0.0625, 0.875, 0.375 },
                    { 0.625, 0.125, 0.9375, 0.6875, 0.875, 1 }, { 0.9375, 0.125, 0.3125, 1, 0.875, 0.375 },
                    { 0.625, 0.125, 0, 0.6875, 0.875, 0.0625 }, { 0.3125, 0, 0.3125, 0.6875, 0.0625, 0.6875 },
                    { 0.3125, 0.9375, 0.3125, 0.6875, 1, 0.6875 } } },
            new String[][] {
                { "gtsr:remaster_fa2b19cd3445496c", "gtsr:remaster_fa2b19cd3445496c", "gtsr:remaster_fa2b19cd3445496c",
                    "gtsr:remaster_fa2b19cd3445496c", "gtsr:remaster_fa2b19cd3445496c",
                    "gtsr:remaster_3674e63854fe8305" },
                { "gtsr:remaster_fa2b19cd3445496c", "gtsr:remaster_fa2b19cd3445496c", "gtsr:remaster_fa2b19cd3445496c",
                    "gtsr:remaster_fa2b19cd3445496c", "gtsr:remaster_3674e63854fe8305",
                    "gtsr:remaster_fa2b19cd3445496c" },
                { "gtsr:remaster_fa2b19cd3445496c", "gtsr:remaster_fa2b19cd3445496c", "gtsr:remaster_fa2b19cd3445496c",
                    "gtsr:remaster_3674e63854fe8305", "gtsr:remaster_fa2b19cd3445496c",
                    "gtsr:remaster_fa2b19cd3445496c" },
                { "gtsr:remaster_fa2b19cd3445496c", "gtsr:remaster_fa2b19cd3445496c", "gtsr:remaster_3674e63854fe8305",
                    "gtsr:remaster_fa2b19cd3445496c", "gtsr:remaster_fa2b19cd3445496c",
                    "gtsr:remaster_fa2b19cd3445496c" } });
    }

    private static RemasterBlock block71() {
        return new RemasterBlock(
            "gtsr:draft6_spawner_stable",
            "spawner",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0, 0, 0, 0.125, 1, 0.125 }, { 0, 0, 0.875, 0.125, 1, 1 }, { 0.875, 0, 0, 1, 1, 0.125 },
                    { 0.875, 0, 0.875, 1, 1, 1 }, { 0, 0, 0, 1, 0.125, 0.125 }, { 0, 0, 0.875, 1, 0.125, 1 },
                    { 0, 0, 0.125, 0.125, 0.125, 0.875 }, { 0.875, 0, 0.125, 1, 0.125, 0.875 },
                    { 0, 0.875, 0, 1, 1, 0.125 }, { 0, 0.875, 0.875, 1, 1, 1 }, { 0, 0.875, 0.125, 0.125, 1, 0.875 },
                    { 0.875, 0.875, 0.125, 1, 1, 0.875 }, { 0.3125, 0.125, 0, 0.375, 0.875, 0.0625 },
                    { 0, 0.125, 0.3125, 0.0625, 0.875, 0.375 }, { 0.3125, 0.125, 0.9375, 0.375, 0.875, 1 },
                    { 0.9375, 0.125, 0.3125, 1, 0.875, 0.375 }, { 0.625, 0.125, 0, 0.6875, 0.875, 0.0625 },
                    { 0, 0.125, 0.625, 0.0625, 0.875, 0.6875 }, { 0.625, 0.125, 0.9375, 0.6875, 0.875, 1 },
                    { 0.9375, 0.125, 0.625, 1, 0.875, 0.6875 }, { 0.3125, 0, 0.3125, 0.6875, 0.0625, 0.6875 },
                    { 0.3125, 0.9375, 0.3125, 0.6875, 1, 0.6875 }, { 0, 0.4375, 0, 1, 0.5625, 0.0625 },
                    { 0, 0.4375, 0.9375, 1, 0.5625, 1 } },
                { { 0.875, 0, 0.875, 1, 1, 1 }, { 0.875, 0, 0, 1, 1, 0.125 }, { 0, 0, 0.875, 0.125, 1, 1 },
                    { 0, 0, 0, 0.125, 1, 0.125 }, { 0, 0, 0.875, 1, 0.125, 1 }, { 0, 0, 0, 1, 0.125, 0.125 },
                    { 0.875, 0, 0.125, 1, 0.125, 0.875 }, { 0, 0, 0.125, 0.125, 0.125, 0.875 },
                    { 0, 0.875, 0.875, 1, 1, 1 }, { 0, 0.875, 0, 1, 1, 0.125 }, { 0.875, 0.875, 0.125, 1, 1, 0.875 },
                    { 0, 0.875, 0.125, 0.125, 1, 0.875 }, { 0.625, 0.125, 0.9375, 0.6875, 0.875, 1 },
                    { 0.9375, 0.125, 0.625, 1, 0.875, 0.6875 }, { 0.625, 0.125, 0, 0.6875, 0.875, 0.0625 },
                    { 0, 0.125, 0.625, 0.0625, 0.875, 0.6875 }, { 0.3125, 0.125, 0.9375, 0.375, 0.875, 1 },
                    { 0.9375, 0.125, 0.3125, 1, 0.875, 0.375 }, { 0.3125, 0.125, 0, 0.375, 0.875, 0.0625 },
                    { 0, 0.125, 0.3125, 0.0625, 0.875, 0.375 }, { 0.3125, 0, 0.3125, 0.6875, 0.0625, 0.6875 },
                    { 0.3125, 0.9375, 0.3125, 0.6875, 1, 0.6875 }, { 0, 0.4375, 0.9375, 1, 0.5625, 1 },
                    { 0, 0.4375, 0, 1, 0.5625, 0.0625 } },
                { { 0.875, 0, 0, 1, 1, 0.125 }, { 0, 0, 0, 0.125, 1, 0.125 }, { 0.875, 0, 0.875, 1, 1, 1 },
                    { 0, 0, 0.875, 0.125, 1, 1 }, { 0.875, 0, 0, 1, 0.125, 1 }, { 0, 0, 0, 0.125, 0.125, 1 },
                    { 0.125, 0, 0, 0.875, 0.125, 0.125 }, { 0.125, 0, 0.875, 0.875, 0.125, 1 },
                    { 0.875, 0.875, 0, 1, 1, 1 }, { 0, 0.875, 0, 0.125, 1, 1 }, { 0.125, 0.875, 0, 0.875, 1, 0.125 },
                    { 0.125, 0.875, 0.875, 0.875, 1, 1 }, { 0.9375, 0.125, 0.3125, 1, 0.875, 0.375 },
                    { 0.625, 0.125, 0, 0.6875, 0.875, 0.0625 }, { 0, 0.125, 0.3125, 0.0625, 0.875, 0.375 },
                    { 0.625, 0.125, 0.9375, 0.6875, 0.875, 1 }, { 0.9375, 0.125, 0.625, 1, 0.875, 0.6875 },
                    { 0.3125, 0.125, 0, 0.375, 0.875, 0.0625 }, { 0, 0.125, 0.625, 0.0625, 0.875, 0.6875 },
                    { 0.3125, 0.125, 0.9375, 0.375, 0.875, 1 }, { 0.3125, 0, 0.3125, 0.6875, 0.0625, 0.6875 },
                    { 0.3125, 0.9375, 0.3125, 0.6875, 1, 0.6875 }, { 0.9375, 0.4375, 0, 1, 0.5625, 1 },
                    { 0, 0.4375, 0, 0.0625, 0.5625, 1 } },
                { { 0, 0, 0.875, 0.125, 1, 1 }, { 0.875, 0, 0.875, 1, 1, 1 }, { 0, 0, 0, 0.125, 1, 0.125 },
                    { 0.875, 0, 0, 1, 1, 0.125 }, { 0, 0, 0, 0.125, 0.125, 1 }, { 0.875, 0, 0, 1, 0.125, 1 },
                    { 0.125, 0, 0.875, 0.875, 0.125, 1 }, { 0.125, 0, 0, 0.875, 0.125, 0.125 },
                    { 0, 0.875, 0, 0.125, 1, 1 }, { 0.875, 0.875, 0, 1, 1, 1 }, { 0.125, 0.875, 0.875, 0.875, 1, 1 },
                    { 0.125, 0.875, 0, 0.875, 1, 0.125 }, { 0, 0.125, 0.625, 0.0625, 0.875, 0.6875 },
                    { 0.3125, 0.125, 0.9375, 0.375, 0.875, 1 }, { 0.9375, 0.125, 0.625, 1, 0.875, 0.6875 },
                    { 0.3125, 0.125, 0, 0.375, 0.875, 0.0625 }, { 0, 0.125, 0.3125, 0.0625, 0.875, 0.375 },
                    { 0.625, 0.125, 0.9375, 0.6875, 0.875, 1 }, { 0.9375, 0.125, 0.3125, 1, 0.875, 0.375 },
                    { 0.625, 0.125, 0, 0.6875, 0.875, 0.0625 }, { 0.3125, 0, 0.3125, 0.6875, 0.0625, 0.6875 },
                    { 0.3125, 0.9375, 0.3125, 0.6875, 1, 0.6875 }, { 0, 0.4375, 0, 0.0625, 0.5625, 1 },
                    { 0.9375, 0.4375, 0, 1, 0.5625, 1 } } },
            new String[][] {
                { "gtsr:remaster_9ebbc32271bbe8b4", "gtsr:remaster_9ebbc32271bbe8b4", "gtsr:remaster_9ebbc32271bbe8b4",
                    "gtsr:remaster_9ebbc32271bbe8b4", "gtsr:remaster_9ebbc32271bbe8b4",
                    "gtsr:remaster_6207287faec0b540" },
                { "gtsr:remaster_9ebbc32271bbe8b4", "gtsr:remaster_9ebbc32271bbe8b4", "gtsr:remaster_9ebbc32271bbe8b4",
                    "gtsr:remaster_9ebbc32271bbe8b4", "gtsr:remaster_6207287faec0b540",
                    "gtsr:remaster_9ebbc32271bbe8b4" },
                { "gtsr:remaster_9ebbc32271bbe8b4", "gtsr:remaster_9ebbc32271bbe8b4", "gtsr:remaster_9ebbc32271bbe8b4",
                    "gtsr:remaster_6207287faec0b540", "gtsr:remaster_9ebbc32271bbe8b4",
                    "gtsr:remaster_9ebbc32271bbe8b4" },
                { "gtsr:remaster_9ebbc32271bbe8b4", "gtsr:remaster_9ebbc32271bbe8b4", "gtsr:remaster_6207287faec0b540",
                    "gtsr:remaster_9ebbc32271bbe8b4", "gtsr:remaster_9ebbc32271bbe8b4",
                    "gtsr:remaster_9ebbc32271bbe8b4" } });
    }

    private static RemasterBlock block72() {
        return new RemasterBlock(
            "gtsr:draft6_spawner_runaway",
            "spawner",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0, 0, 0, 0.125, 1, 0.125 }, { 0, 0, 0.875, 0.125, 1, 1 }, { 0.875, 0, 0, 1, 1, 0.125 },
                    { 0.875, 0, 0.875, 1, 1, 1 }, { 0, 0, 0, 1, 0.125, 0.125 }, { 0, 0, 0.875, 1, 0.125, 1 },
                    { 0, 0, 0.125, 0.125, 0.125, 0.875 }, { 0.875, 0, 0.125, 1, 0.125, 0.875 },
                    { 0, 0.875, 0, 1, 1, 0.125 }, { 0, 0.875, 0.875, 1, 1, 1 }, { 0, 0.875, 0.125, 0.125, 1, 0.875 },
                    { 0.875, 0.875, 0.125, 1, 1, 0.875 }, { 0.3125, 0.125, 0, 0.375, 0.875, 0.0625 },
                    { 0, 0.125, 0.3125, 0.0625, 0.875, 0.375 }, { 0.3125, 0.125, 0.9375, 0.375, 0.875, 1 },
                    { 0.9375, 0.125, 0.3125, 1, 0.875, 0.375 }, { 0.625, 0.125, 0, 0.6875, 0.875, 0.0625 },
                    { 0, 0.125, 0.625, 0.0625, 0.875, 0.6875 }, { 0.625, 0.125, 0.9375, 0.6875, 0.875, 1 },
                    { 0.9375, 0.125, 0.625, 1, 0.875, 0.6875 }, { 0.3125, 0, 0.3125, 0.6875, 0.0625, 0.6875 },
                    { 0.3125, 0.9375, 0.3125, 0.6875, 1, 0.6875 }, { 0.0625, 0.625, 0.125, 0.25, 0.75, 0.3125 },
                    { 0.6875, 0.1875, 0.75, 0.9375, 0.3125, 0.9375 } },
                { { 0.875, 0, 0.875, 1, 1, 1 }, { 0.875, 0, 0, 1, 1, 0.125 }, { 0, 0, 0.875, 0.125, 1, 1 },
                    { 0, 0, 0, 0.125, 1, 0.125 }, { 0, 0, 0.875, 1, 0.125, 1 }, { 0, 0, 0, 1, 0.125, 0.125 },
                    { 0.875, 0, 0.125, 1, 0.125, 0.875 }, { 0, 0, 0.125, 0.125, 0.125, 0.875 },
                    { 0, 0.875, 0.875, 1, 1, 1 }, { 0, 0.875, 0, 1, 1, 0.125 }, { 0.875, 0.875, 0.125, 1, 1, 0.875 },
                    { 0, 0.875, 0.125, 0.125, 1, 0.875 }, { 0.625, 0.125, 0.9375, 0.6875, 0.875, 1 },
                    { 0.9375, 0.125, 0.625, 1, 0.875, 0.6875 }, { 0.625, 0.125, 0, 0.6875, 0.875, 0.0625 },
                    { 0, 0.125, 0.625, 0.0625, 0.875, 0.6875 }, { 0.3125, 0.125, 0.9375, 0.375, 0.875, 1 },
                    { 0.9375, 0.125, 0.3125, 1, 0.875, 0.375 }, { 0.3125, 0.125, 0, 0.375, 0.875, 0.0625 },
                    { 0, 0.125, 0.3125, 0.0625, 0.875, 0.375 }, { 0.3125, 0, 0.3125, 0.6875, 0.0625, 0.6875 },
                    { 0.3125, 0.9375, 0.3125, 0.6875, 1, 0.6875 }, { 0.75, 0.625, 0.6875, 0.9375, 0.75, 0.875 },
                    { 0.0625, 0.1875, 0.0625, 0.3125, 0.3125, 0.25 } },
                { { 0.875, 0, 0, 1, 1, 0.125 }, { 0, 0, 0, 0.125, 1, 0.125 }, { 0.875, 0, 0.875, 1, 1, 1 },
                    { 0, 0, 0.875, 0.125, 1, 1 }, { 0.875, 0, 0, 1, 0.125, 1 }, { 0, 0, 0, 0.125, 0.125, 1 },
                    { 0.125, 0, 0, 0.875, 0.125, 0.125 }, { 0.125, 0, 0.875, 0.875, 0.125, 1 },
                    { 0.875, 0.875, 0, 1, 1, 1 }, { 0, 0.875, 0, 0.125, 1, 1 }, { 0.125, 0.875, 0, 0.875, 1, 0.125 },
                    { 0.125, 0.875, 0.875, 0.875, 1, 1 }, { 0.9375, 0.125, 0.3125, 1, 0.875, 0.375 },
                    { 0.625, 0.125, 0, 0.6875, 0.875, 0.0625 }, { 0, 0.125, 0.3125, 0.0625, 0.875, 0.375 },
                    { 0.625, 0.125, 0.9375, 0.6875, 0.875, 1 }, { 0.9375, 0.125, 0.625, 1, 0.875, 0.6875 },
                    { 0.3125, 0.125, 0, 0.375, 0.875, 0.0625 }, { 0, 0.125, 0.625, 0.0625, 0.875, 0.6875 },
                    { 0.3125, 0.125, 0.9375, 0.375, 0.875, 1 }, { 0.3125, 0, 0.3125, 0.6875, 0.0625, 0.6875 },
                    { 0.3125, 0.9375, 0.3125, 0.6875, 1, 0.6875 }, { 0.6875, 0.625, 0.0625, 0.875, 0.75, 0.25 },
                    { 0.0625, 0.1875, 0.6875, 0.25, 0.3125, 0.9375 } },
                { { 0, 0, 0.875, 0.125, 1, 1 }, { 0.875, 0, 0.875, 1, 1, 1 }, { 0, 0, 0, 0.125, 1, 0.125 },
                    { 0.875, 0, 0, 1, 1, 0.125 }, { 0, 0, 0, 0.125, 0.125, 1 }, { 0.875, 0, 0, 1, 0.125, 1 },
                    { 0.125, 0, 0.875, 0.875, 0.125, 1 }, { 0.125, 0, 0, 0.875, 0.125, 0.125 },
                    { 0, 0.875, 0, 0.125, 1, 1 }, { 0.875, 0.875, 0, 1, 1, 1 }, { 0.125, 0.875, 0.875, 0.875, 1, 1 },
                    { 0.125, 0.875, 0, 0.875, 1, 0.125 }, { 0, 0.125, 0.625, 0.0625, 0.875, 0.6875 },
                    { 0.3125, 0.125, 0.9375, 0.375, 0.875, 1 }, { 0.9375, 0.125, 0.625, 1, 0.875, 0.6875 },
                    { 0.3125, 0.125, 0, 0.375, 0.875, 0.0625 }, { 0, 0.125, 0.3125, 0.0625, 0.875, 0.375 },
                    { 0.625, 0.125, 0.9375, 0.6875, 0.875, 1 }, { 0.9375, 0.125, 0.3125, 1, 0.875, 0.375 },
                    { 0.625, 0.125, 0, 0.6875, 0.875, 0.0625 }, { 0.3125, 0, 0.3125, 0.6875, 0.0625, 0.6875 },
                    { 0.3125, 0.9375, 0.3125, 0.6875, 1, 0.6875 }, { 0.125, 0.625, 0.75, 0.3125, 0.75, 0.9375 },
                    { 0.75, 0.1875, 0.0625, 0.9375, 0.3125, 0.3125 } } },
            new String[][] {
                { "gtsr:remaster_8c4abb089b47ad2f", "gtsr:remaster_8c4abb089b47ad2f", "gtsr:remaster_8c4abb089b47ad2f",
                    "gtsr:remaster_8c4abb089b47ad2f", "gtsr:remaster_8c4abb089b47ad2f",
                    "gtsr:remaster_1e9b96e8ec263b65" },
                { "gtsr:remaster_8c4abb089b47ad2f", "gtsr:remaster_8c4abb089b47ad2f", "gtsr:remaster_8c4abb089b47ad2f",
                    "gtsr:remaster_8c4abb089b47ad2f", "gtsr:remaster_1e9b96e8ec263b65",
                    "gtsr:remaster_8c4abb089b47ad2f" },
                { "gtsr:remaster_8c4abb089b47ad2f", "gtsr:remaster_8c4abb089b47ad2f", "gtsr:remaster_8c4abb089b47ad2f",
                    "gtsr:remaster_1e9b96e8ec263b65", "gtsr:remaster_8c4abb089b47ad2f",
                    "gtsr:remaster_8c4abb089b47ad2f" },
                { "gtsr:remaster_8c4abb089b47ad2f", "gtsr:remaster_8c4abb089b47ad2f", "gtsr:remaster_1e9b96e8ec263b65",
                    "gtsr:remaster_8c4abb089b47ad2f", "gtsr:remaster_8c4abb089b47ad2f",
                    "gtsr:remaster_8c4abb089b47ad2f" } });
    }

}
