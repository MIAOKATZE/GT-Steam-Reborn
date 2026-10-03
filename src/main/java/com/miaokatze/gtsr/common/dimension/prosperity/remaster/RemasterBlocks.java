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
        if (id.equals("gtsr:draft5_nest_vein") || id.equals("gtsr:draft5_corrosion_flake")) {
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
        register(block6());
        register(block7());
        register(block8());
        register(block10());
        register(block11());
        register(block13());
        register(block26());
        register(block31());
        register(block40());
        register(block70());
        register(block74());
        register(block75());
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

    private static RemasterBlock block6() {
        return new RemasterBlock(
            "gtsr:draft_tool_rack",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_29fb0c1d18c22cad", "gtsr:remaster_29fb0c1d18c22cad",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_29fb0c1d18c22cad", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_29fb0c1d18c22cad" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_29fb0c1d18c22cad", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_29fb0c1d18c22cad", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_29fb0c1d18c22cad", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_29fb0c1d18c22cad",
                    "gtsr:remaster_843c2f1bfeea551d" } });
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

    private static RemasterBlock block10() {
        return new RemasterBlock(
            "gtsr:draft_oil_drum",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_f677eda74168b146", "gtsr:remaster_f677eda74168b146",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_f677eda74168b146", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_f677eda74168b146" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_f677eda74168b146", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_f677eda74168b146", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_f677eda74168b146", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_f677eda74168b146",
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

    private static RemasterBlock block26() {
        return new RemasterBlock(
            "gtsr:draft_filter_bed",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_f596257b153e5f74", "gtsr:remaster_f596257b153e5f74",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_f596257b153e5f74", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_f596257b153e5f74" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_f596257b153e5f74", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_f596257b153e5f74", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_f596257b153e5f74", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_f596257b153e5f74",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block31() {
        return new RemasterBlock(
            "gtsr:draft_feed_hopper",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_c094ffd9a1ece269", "gtsr:remaster_c094ffd9a1ece269",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_c094ffd9a1ece269", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_c094ffd9a1ece269" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_c094ffd9a1ece269", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_c094ffd9a1ece269", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_c094ffd9a1ece269", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_c094ffd9a1ece269",
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

    private static RemasterBlock block75() {
        return new RemasterBlock(
            "gtsr:draft6_chair",
            "decoration",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0.0625, 0, 0.0625, 0.1875, 0.375, 0.1875 }, { 0.0625, 0, 0.8125, 0.1875, 0.375, 0.9375 },
                    { 0.8125, 0, 0.0625, 0.9375, 0.375, 0.1875 }, { 0.8125, 0, 0.8125, 0.9375, 0.375, 0.9375 },
                    { 0.0625, 0.375, 0.0625, 0.9375, 0.5, 0.9375 }, { 0.0625, 0.5, 0.0625, 0.1875, 1, 0.1875 },
                    { 0.0625, 0.5, 0.8125, 0.1875, 1, 0.9375 }, { 0.0625, 0.75, 0.1875, 0.1875, 0.9375, 0.8125 } },
                { { 0.8125, 0, 0.8125, 0.9375, 0.375, 0.9375 }, { 0.8125, 0, 0.0625, 0.9375, 0.375, 0.1875 },
                    { 0.0625, 0, 0.8125, 0.1875, 0.375, 0.9375 }, { 0.0625, 0, 0.0625, 0.1875, 0.375, 0.1875 },
                    { 0.0625, 0.375, 0.0625, 0.9375, 0.5, 0.9375 }, { 0.8125, 0.5, 0.8125, 0.9375, 1, 0.9375 },
                    { 0.8125, 0.5, 0.0625, 0.9375, 1, 0.1875 }, { 0.8125, 0.75, 0.1875, 0.9375, 0.9375, 0.8125 } },
                { { 0.8125, 0, 0.0625, 0.9375, 0.375, 0.1875 }, { 0.0625, 0, 0.0625, 0.1875, 0.375, 0.1875 },
                    { 0.8125, 0, 0.8125, 0.9375, 0.375, 0.9375 }, { 0.0625, 0, 0.8125, 0.1875, 0.375, 0.9375 },
                    { 0.0625, 0.375, 0.0625, 0.9375, 0.5, 0.9375 }, { 0.8125, 0.5, 0.0625, 0.9375, 1, 0.1875 },
                    { 0.0625, 0.5, 0.0625, 0.1875, 1, 0.1875 }, { 0.1875, 0.75, 0.0625, 0.8125, 0.9375, 0.1875 } },
                { { 0.0625, 0, 0.8125, 0.1875, 0.375, 0.9375 }, { 0.8125, 0, 0.8125, 0.9375, 0.375, 0.9375 },
                    { 0.0625, 0, 0.0625, 0.1875, 0.375, 0.1875 }, { 0.8125, 0, 0.0625, 0.9375, 0.375, 0.1875 },
                    { 0.0625, 0.375, 0.0625, 0.9375, 0.5, 0.9375 }, { 0.0625, 0.5, 0.8125, 0.1875, 1, 0.9375 },
                    { 0.8125, 0.5, 0.8125, 0.9375, 1, 0.9375 }, { 0.1875, 0.75, 0.8125, 0.8125, 0.9375, 0.9375 } } },
            new String[][] {
                { "gtsr:remaster_20f7606288ad8ff4", "gtsr:remaster_20f7606288ad8ff4", "gtsr:remaster_20f7606288ad8ff4",
                    "gtsr:remaster_20f7606288ad8ff4", "gtsr:remaster_20f7606288ad8ff4",
                    "gtsr:remaster_20f7606288ad8ff4" },
                { "gtsr:remaster_20f7606288ad8ff4", "gtsr:remaster_20f7606288ad8ff4", "gtsr:remaster_20f7606288ad8ff4",
                    "gtsr:remaster_20f7606288ad8ff4", "gtsr:remaster_20f7606288ad8ff4",
                    "gtsr:remaster_20f7606288ad8ff4" },
                { "gtsr:remaster_20f7606288ad8ff4", "gtsr:remaster_20f7606288ad8ff4", "gtsr:remaster_20f7606288ad8ff4",
                    "gtsr:remaster_20f7606288ad8ff4", "gtsr:remaster_20f7606288ad8ff4",
                    "gtsr:remaster_20f7606288ad8ff4" },
                { "gtsr:remaster_20f7606288ad8ff4", "gtsr:remaster_20f7606288ad8ff4", "gtsr:remaster_20f7606288ad8ff4",
                    "gtsr:remaster_20f7606288ad8ff4", "gtsr:remaster_20f7606288ad8ff4",
                    "gtsr:remaster_20f7606288ad8ff4" } });
    }

}
