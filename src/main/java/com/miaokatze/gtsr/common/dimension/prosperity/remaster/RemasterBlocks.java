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

/** Ninety-four authored materials, imported exactly from revision 4 through 7 snapshots. */
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
        register(block0());
        register(block1());
        register(block2());
        register(block3());
        register(block4());
        register(block5());
        register(block6());
        register(block7());
        register(block8());
        register(block9());
        register(block10());
        register(block11());
        register(block12());
        register(block13());
        register(block14());
        register(block15());
        register(block16());
        register(block17());
        register(block18());
        register(block19());
        register(block20());
        register(block21());
        register(block22());
        register(block23());
        register(block24());
        register(block25());
        register(block26());
        register(block27());
        register(block28());
        register(block29());
        register(block30());
        register(block31());
        register(block32());
        register(block33());
        register(block34());
        register(block35());
        register(block36());
        register(block37());
        register(block38());
        register(block39());
        register(block40());
        register(block41());
        register(block42());
        register(block43());
        register(block44());
        register(block45());
        register(block46());
        register(block47());
        register(block48());
        register(block49());
        register(block50());
        register(block51());
        register(block52());
        register(block53());
        register(block54());
        register(block55());
        register(block56());
        register(block57());
        register(block58());
        register(block59());
        register(block60());
        register(block61());
        register(block62());
        register(block63());
        register(block64());
        register(block65());
        register(block66());
        register(block67());
        register(block68());
        register(block69());
        register(block70());
        register(block71());
        register(block72());
        register(block73());
        register(block74());
        register(block75());
        register(block76());
        register(block77());
        register(block78());
        register(block79());
        register(block80());
        register(block81());
        register(block82());
        register(block83());
        register(block84());
        register(block85());
        register(block86());
        register(block87());
        register(block88());
        register(block89());
        register(block90());
        register(block91());
        register(block92());
        register(block93());
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

    private static RemasterBlock block1() {
        return new RemasterBlock(
            "gtsr:draft_beam_socket",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_ac2a5674bc15c45d", "gtsr:remaster_ac2a5674bc15c45d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_ac2a5674bc15c45d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_ac2a5674bc15c45d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_ac2a5674bc15c45d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_ac2a5674bc15c45d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_ac2a5674bc15c45d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_ac2a5674bc15c45d",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block2() {
        return new RemasterBlock(
            "gtsr:draft_braced_panel",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_cc389090e0943b93", "gtsr:remaster_cc389090e0943b93",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_cc389090e0943b93", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_cc389090e0943b93" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_cc389090e0943b93", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_cc389090e0943b93", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_cc389090e0943b93", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_cc389090e0943b93",
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

    private static RemasterBlock block4() {
        return new RemasterBlock(
            "gtsr:draft_drain_channel",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_b746477f7c776957", "gtsr:remaster_b746477f7c776957",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_b746477f7c776957", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_b746477f7c776957" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_b746477f7c776957", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_b746477f7c776957", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_b746477f7c776957", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_b746477f7c776957",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block5() {
        return new RemasterBlock(
            "gtsr:draft_locker",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_7722a85fddc96bd6", "gtsr:remaster_7722a85fddc96bd6",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_7722a85fddc96bd6", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_7722a85fddc96bd6" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_7722a85fddc96bd6", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_7722a85fddc96bd6", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_7722a85fddc96bd6", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_7722a85fddc96bd6",
                    "gtsr:remaster_843c2f1bfeea551d" } });
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

    private static RemasterBlock block14() {
        return new RemasterBlock(
            "gtsr:draft_notice_board",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_799e18410f4a1915", "gtsr:remaster_799e18410f4a1915",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_799e18410f4a1915", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_799e18410f4a1915" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_799e18410f4a1915", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_799e18410f4a1915", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_799e18410f4a1915", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_799e18410f4a1915",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block15() {
        return new RemasterBlock(
            "gtsr:draft_canteen_table",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_c540bf87a20a5e46", "gtsr:remaster_c540bf87a20a5e46",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_c540bf87a20a5e46", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_c540bf87a20a5e46" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_c540bf87a20a5e46", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_c540bf87a20a5e46", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_c540bf87a20a5e46", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_c540bf87a20a5e46",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block16() {
        return new RemasterBlock(
            "gtsr:draft_bedroll",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_d3c189cc6ea3e13e", "gtsr:remaster_d3c189cc6ea3e13e",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_d3c189cc6ea3e13e", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_d3c189cc6ea3e13e" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_d3c189cc6ea3e13e", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_d3c189cc6ea3e13e", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_d3c189cc6ea3e13e", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_d3c189cc6ea3e13e",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block17() {
        return new RemasterBlock(
            "gtsr:draft_wash_basin",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_8376124ed5624645", "gtsr:remaster_8376124ed5624645",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_8376124ed5624645", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_8376124ed5624645" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_8376124ed5624645", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_8376124ed5624645", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_8376124ed5624645", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_8376124ed5624645",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block18() {
        return new RemasterBlock(
            "gtsr:draft_ceramic_insulator",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_2e3946f56e8eb267", "gtsr:remaster_2e3946f56e8eb267",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_2e3946f56e8eb267", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_2e3946f56e8eb267" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_2e3946f56e8eb267", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_2e3946f56e8eb267", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_2e3946f56e8eb267", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_2e3946f56e8eb267",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block19() {
        return new RemasterBlock(
            "gtsr:draft_track_buffer",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_786f0c99ad9190be", "gtsr:remaster_786f0c99ad9190be",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_786f0c99ad9190be", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_786f0c99ad9190be" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_786f0c99ad9190be", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_786f0c99ad9190be", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_786f0c99ad9190be", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_786f0c99ad9190be",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block20() {
        return new RemasterBlock(
            "gtsr:draft_scaffold_joint",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_b5f06af01f3d9192", "gtsr:remaster_b5f06af01f3d9192",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_b5f06af01f3d9192", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_b5f06af01f3d9192" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_b5f06af01f3d9192", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_b5f06af01f3d9192", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_b5f06af01f3d9192", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_b5f06af01f3d9192",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block21() {
        return new RemasterBlock(
            "gtsr:draft_brick_pallet",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_620983aa40fb4222", "gtsr:remaster_620983aa40fb4222",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_620983aa40fb4222", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_620983aa40fb4222" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_620983aa40fb4222", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_620983aa40fb4222", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_620983aa40fb4222", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_620983aa40fb4222",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block22() {
        return new RemasterBlock(
            "gtsr:draft_cement_sack",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_7d13ca0f8ca7cfe1", "gtsr:remaster_7d13ca0f8ca7cfe1",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_7d13ca0f8ca7cfe1", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_7d13ca0f8ca7cfe1" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_7d13ca0f8ca7cfe1", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_7d13ca0f8ca7cfe1", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_7d13ca0f8ca7cfe1", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_7d13ca0f8ca7cfe1",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block23() {
        return new RemasterBlock(
            "gtsr:draft_safety_stripe",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_1a84e2562fe9f0d0", "gtsr:remaster_1a84e2562fe9f0d0",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_1a84e2562fe9f0d0", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_1a84e2562fe9f0d0" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_1a84e2562fe9f0d0", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_1a84e2562fe9f0d0", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_1a84e2562fe9f0d0", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_1a84e2562fe9f0d0",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block24() {
        return new RemasterBlock(
            "gtsr:draft_sewer_lining",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_fe74bd4f52935f50", "gtsr:remaster_fe74bd4f52935f50",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_fe74bd4f52935f50", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_fe74bd4f52935f50" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_fe74bd4f52935f50", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_fe74bd4f52935f50", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_fe74bd4f52935f50", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_fe74bd4f52935f50",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block25() {
        return new RemasterBlock(
            "gtsr:draft_inspection_cover",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_f2e69fe48d89ef04", "gtsr:remaster_f2e69fe48d89ef04",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_f2e69fe48d89ef04", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_f2e69fe48d89ef04" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_f2e69fe48d89ef04", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_f2e69fe48d89ef04", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_f2e69fe48d89ef04", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_f2e69fe48d89ef04",
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

    private static RemasterBlock block27() {
        return new RemasterBlock(
            "gtsr:draft_sluice_marker",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_1f89d7ba0d165bdc", "gtsr:remaster_1f89d7ba0d165bdc",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_1f89d7ba0d165bdc", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_1f89d7ba0d165bdc" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_1f89d7ba0d165bdc", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_1f89d7ba0d165bdc", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_1f89d7ba0d165bdc", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_1f89d7ba0d165bdc",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block28() {
        return new RemasterBlock(
            "gtsr:draft_distill_tray",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_6289629400ca928a", "gtsr:remaster_6289629400ca928a",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_6289629400ca928a", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_6289629400ca928a" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_6289629400ca928a", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_6289629400ca928a", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_6289629400ca928a", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_6289629400ca928a",
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

    private static RemasterBlock block30() {
        return new RemasterBlock(
            "gtsr:draft_sample_bottle",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_1ca14a4d890e32cc", "gtsr:remaster_1ca14a4d890e32cc",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_1ca14a4d890e32cc", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_1ca14a4d890e32cc" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_1ca14a4d890e32cc", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_1ca14a4d890e32cc", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_1ca14a4d890e32cc", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_1ca14a4d890e32cc",
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

    private static RemasterBlock block32() {
        return new RemasterBlock(
            "gtsr:draft_slag_cart",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_b8430818c38ac2e0", "gtsr:remaster_b8430818c38ac2e0",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_b8430818c38ac2e0", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_b8430818c38ac2e0" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_b8430818c38ac2e0", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_b8430818c38ac2e0", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_b8430818c38ac2e0", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_b8430818c38ac2e0",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block33() {
        return new RemasterBlock(
            "gtsr:draft_kiln_gauge_mark",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_c853e5e0f79c9019", "gtsr:remaster_c853e5e0f79c9019",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_c853e5e0f79c9019", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_c853e5e0f79c9019" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_c853e5e0f79c9019", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_c853e5e0f79c9019", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_c853e5e0f79c9019", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_c853e5e0f79c9019",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block34() {
        return new RemasterBlock(
            "gtsr:draft_street_bench",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_fcf6fcecbe5714a2", "gtsr:remaster_fcf6fcecbe5714a2",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_fcf6fcecbe5714a2", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_fcf6fcecbe5714a2" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_fcf6fcecbe5714a2", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_fcf6fcecbe5714a2", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_fcf6fcecbe5714a2", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_fcf6fcecbe5714a2",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block35() {
        return new RemasterBlock(
            "gtsr:draft_lamp_pedestal",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_a9ee057a8ed3e1da", "gtsr:remaster_a9ee057a8ed3e1da",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_a9ee057a8ed3e1da", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_a9ee057a8ed3e1da" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_a9ee057a8ed3e1da", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_a9ee057a8ed3e1da", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_a9ee057a8ed3e1da", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_a9ee057a8ed3e1da",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block36() {
        return new RemasterBlock(
            "gtsr:draft_market_canopy",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_92fc04ea7cd372de", "gtsr:remaster_92fc04ea7cd372de",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_92fc04ea7cd372de", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_92fc04ea7cd372de" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_92fc04ea7cd372de", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_92fc04ea7cd372de", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_92fc04ea7cd372de", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_92fc04ea7cd372de",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block37() {
        return new RemasterBlock(
            "gtsr:draft_medicine_cabinet",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_8af70bceafed7bcf", "gtsr:remaster_8af70bceafed7bcf",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_8af70bceafed7bcf", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_8af70bceafed7bcf" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_8af70bceafed7bcf", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_8af70bceafed7bcf", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_8af70bceafed7bcf", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_8af70bceafed7bcf",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block38() {
        return new RemasterBlock(
            "gtsr:draft_mess_oven",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_0e30468f7c7e0203", "gtsr:remaster_0e30468f7c7e0203",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_0e30468f7c7e0203", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_0e30468f7c7e0203" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_0e30468f7c7e0203", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_0e30468f7c7e0203", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_0e30468f7c7e0203", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_0e30468f7c7e0203",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block39() {
        return new RemasterBlock(
            "gtsr:draft_dispatch_ledger",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_193d9f55e4cdf82b", "gtsr:remaster_193d9f55e4cdf82b",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_193d9f55e4cdf82b", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_193d9f55e4cdf82b" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_193d9f55e4cdf82b", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_193d9f55e4cdf82b", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_193d9f55e4cdf82b", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_193d9f55e4cdf82b",
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

    private static RemasterBlock block41() {
        return new RemasterBlock(
            "gtsr:draft_signal_switch",
            "operation",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_0e5a5cd40f63d0bc",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_0e5a5cd40f63d0bc" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_0e5a5cd40f63d0bc", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_0e5a5cd40f63d0bc",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block42() {
        return new RemasterBlock(
            "gtsr:draft_counterweight_lock",
            "operation",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_70ef259e6aff5e04",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_70ef259e6aff5e04" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_70ef259e6aff5e04", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_70ef259e6aff5e04",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block43() {
        return new RemasterBlock(
            "gtsr:draft_flow_regulator",
            "operation",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_5314d79e42c304c3",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_5314d79e42c304c3" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_5314d79e42c304c3", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_5314d79e42c304c3",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block44() {
        return new RemasterBlock(
            "gtsr:draft_resonance_tuner",
            "operation",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_0508ec8f1b229737",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_0508ec8f1b229737" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_0508ec8f1b229737", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_0508ec8f1b229737",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block45() {
        return new RemasterBlock(
            "gtsr:draft_distillation_control",
            "operation",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_04631346d95618cf",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_04631346d95618cf" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_04631346d95618cf", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_04631346d95618cf",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block46() {
        return new RemasterBlock(
            "gtsr:draft_rail_dispatch",
            "operation",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_29810a2a9bc684aa",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_29810a2a9bc684aa" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_29810a2a9bc684aa", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_29810a2a9bc684aa",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block47() {
        return new RemasterBlock(
            "gtsr:draft_drain_lock",
            "operation",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_18772098fdca12d4",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_18772098fdca12d4" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_18772098fdca12d4", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_18772098fdca12d4",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block48() {
        return new RemasterBlock(
            "gtsr:draft_construction_jack",
            "operation",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_f31d2839fe4a4ce8",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_f31d2839fe4a4ce8" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_f31d2839fe4a4ce8", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_f31d2839fe4a4ce8",
                    "gtsr:remaster_843c2f1bfeea551d" } });
    }

    private static RemasterBlock block49() {
        return new RemasterBlock(
            "gtsr:draft_archive_reader",
            "operation",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_76f90a31f608c9e9",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_76f90a31f608c9e9" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_76f90a31f608c9e9", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d" },
                { "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_843c2f1bfeea551d",
                    "gtsr:remaster_843c2f1bfeea551d", "gtsr:remaster_76f90a31f608c9e9",
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

    private static RemasterBlock block52() {
        return new RemasterBlock(
            "gtsr:draft5_boiler_safety_seal",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_fe30884504e60d86", "gtsr:remaster_fe30884504e60d86", "gtsr:remaster_fe30884504e60d86",
                    "gtsr:remaster_fe30884504e60d86", "gtsr:remaster_fe30884504e60d86",
                    "gtsr:remaster_fe30884504e60d86" },
                { "gtsr:remaster_fe30884504e60d86", "gtsr:remaster_fe30884504e60d86", "gtsr:remaster_fe30884504e60d86",
                    "gtsr:remaster_fe30884504e60d86", "gtsr:remaster_fe30884504e60d86",
                    "gtsr:remaster_fe30884504e60d86" },
                { "gtsr:remaster_fe30884504e60d86", "gtsr:remaster_fe30884504e60d86", "gtsr:remaster_fe30884504e60d86",
                    "gtsr:remaster_fe30884504e60d86", "gtsr:remaster_fe30884504e60d86",
                    "gtsr:remaster_fe30884504e60d86" },
                { "gtsr:remaster_fe30884504e60d86", "gtsr:remaster_fe30884504e60d86", "gtsr:remaster_fe30884504e60d86",
                    "gtsr:remaster_fe30884504e60d86", "gtsr:remaster_fe30884504e60d86",
                    "gtsr:remaster_fe30884504e60d86" } });
    }

    private static RemasterBlock block53() {
        return new RemasterBlock(
            "gtsr:draft5_gantry_socket",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_183f80e84de06c75", "gtsr:remaster_183f80e84de06c75", "gtsr:remaster_183f80e84de06c75",
                    "gtsr:remaster_183f80e84de06c75", "gtsr:remaster_183f80e84de06c75",
                    "gtsr:remaster_183f80e84de06c75" },
                { "gtsr:remaster_183f80e84de06c75", "gtsr:remaster_183f80e84de06c75", "gtsr:remaster_183f80e84de06c75",
                    "gtsr:remaster_183f80e84de06c75", "gtsr:remaster_183f80e84de06c75",
                    "gtsr:remaster_183f80e84de06c75" },
                { "gtsr:remaster_183f80e84de06c75", "gtsr:remaster_183f80e84de06c75", "gtsr:remaster_183f80e84de06c75",
                    "gtsr:remaster_183f80e84de06c75", "gtsr:remaster_183f80e84de06c75",
                    "gtsr:remaster_183f80e84de06c75" },
                { "gtsr:remaster_183f80e84de06c75", "gtsr:remaster_183f80e84de06c75", "gtsr:remaster_183f80e84de06c75",
                    "gtsr:remaster_183f80e84de06c75", "gtsr:remaster_183f80e84de06c75",
                    "gtsr:remaster_183f80e84de06c75" } });
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

    private static RemasterBlock block55() {
        return new RemasterBlock(
            "gtsr:draft5_mould_shelf",
            "decoration",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0, 0, 0, 0.12, 1, 1 }, { 0.88, 0, 0, 1, 1, 1 }, { 0, 0.1, 0, 1, 0.18, 1 },
                    { 0, 0.45, 0, 1, 0.53, 1 }, { 0, 0.85, 0, 1, 0.9299999999999999, 1 } },
                { { 0, 0, 0, 0.12, 1, 1 }, { 0.88, 0, 0, 1, 1, 1 }, { 0, 0.1, 0, 1, 0.18, 1 },
                    { 0, 0.45, 0, 1, 0.53, 1 }, { 0, 0.85, 0, 1, 0.9299999999999999, 1 } },
                { { 0, 0, 0, 0.12, 1, 1 }, { 0.88, 0, 0, 1, 1, 1 }, { 0, 0.1, 0, 1, 0.18, 1 },
                    { 0, 0.45, 0, 1, 0.53, 1 }, { 0, 0.85, 0, 1, 0.9299999999999999, 1 } },
                { { 0, 0, 0, 0.12, 1, 1 }, { 0.88, 0, 0, 1, 1, 1 }, { 0, 0.1, 0, 1, 0.18, 1 },
                    { 0, 0.45, 0, 1, 0.53, 1 }, { 0, 0.85, 0, 1, 0.9299999999999999, 1 } } },
            new String[][] {
                { "gtsr:remaster_5afda9ac23720a0a", "gtsr:remaster_5afda9ac23720a0a", "gtsr:remaster_5afda9ac23720a0a",
                    "gtsr:remaster_5afda9ac23720a0a", "gtsr:remaster_5afda9ac23720a0a",
                    "gtsr:remaster_5afda9ac23720a0a" },
                { "gtsr:remaster_5afda9ac23720a0a", "gtsr:remaster_5afda9ac23720a0a", "gtsr:remaster_5afda9ac23720a0a",
                    "gtsr:remaster_5afda9ac23720a0a", "gtsr:remaster_5afda9ac23720a0a",
                    "gtsr:remaster_5afda9ac23720a0a" },
                { "gtsr:remaster_5afda9ac23720a0a", "gtsr:remaster_5afda9ac23720a0a", "gtsr:remaster_5afda9ac23720a0a",
                    "gtsr:remaster_5afda9ac23720a0a", "gtsr:remaster_5afda9ac23720a0a",
                    "gtsr:remaster_5afda9ac23720a0a" },
                { "gtsr:remaster_5afda9ac23720a0a", "gtsr:remaster_5afda9ac23720a0a", "gtsr:remaster_5afda9ac23720a0a",
                    "gtsr:remaster_5afda9ac23720a0a", "gtsr:remaster_5afda9ac23720a0a",
                    "gtsr:remaster_5afda9ac23720a0a" } });
    }

    private static RemasterBlock block56() {
        return new RemasterBlock(
            "gtsr:draft5_vault_rib",
            "decoration",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0.08, 0, 0.08, 0.23, 1, 0.92 }, { 0.23, 0.75, 0.1, 0.92, 0.92, 0.9 },
                    { 0.1, 0, 0.1, 0.7, 0.12, 0.9 } },
                { { 0.08, 0, 0.08, 0.23, 1, 0.92 }, { 0.23, 0.75, 0.1, 0.92, 0.92, 0.9 },
                    { 0.1, 0, 0.1, 0.7, 0.12, 0.9 } },
                { { 0.08, 0, 0.08, 0.23, 1, 0.92 }, { 0.23, 0.75, 0.1, 0.92, 0.92, 0.9 },
                    { 0.1, 0, 0.1, 0.7, 0.12, 0.9 } },
                { { 0.08, 0, 0.08, 0.23, 1, 0.92 }, { 0.23, 0.75, 0.1, 0.92, 0.92, 0.9 },
                    { 0.1, 0, 0.1, 0.7, 0.12, 0.9 } } },
            new String[][] {
                { "gtsr:remaster_ebe5666c61617d57", "gtsr:remaster_ebe5666c61617d57", "gtsr:remaster_ebe5666c61617d57",
                    "gtsr:remaster_ebe5666c61617d57", "gtsr:remaster_ebe5666c61617d57",
                    "gtsr:remaster_ebe5666c61617d57" },
                { "gtsr:remaster_ebe5666c61617d57", "gtsr:remaster_ebe5666c61617d57", "gtsr:remaster_ebe5666c61617d57",
                    "gtsr:remaster_ebe5666c61617d57", "gtsr:remaster_ebe5666c61617d57",
                    "gtsr:remaster_ebe5666c61617d57" },
                { "gtsr:remaster_ebe5666c61617d57", "gtsr:remaster_ebe5666c61617d57", "gtsr:remaster_ebe5666c61617d57",
                    "gtsr:remaster_ebe5666c61617d57", "gtsr:remaster_ebe5666c61617d57",
                    "gtsr:remaster_ebe5666c61617d57" },
                { "gtsr:remaster_ebe5666c61617d57", "gtsr:remaster_ebe5666c61617d57", "gtsr:remaster_ebe5666c61617d57",
                    "gtsr:remaster_ebe5666c61617d57", "gtsr:remaster_ebe5666c61617d57",
                    "gtsr:remaster_ebe5666c61617d57" } });
    }

    private static RemasterBlock block57() {
        return new RemasterBlock(
            "gtsr:draft5_cable_tray",
            "decoration",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0.08, 0, 0.08, 0.23, 1, 0.92 }, { 0.23, 0.75, 0.1, 0.92, 0.92, 0.9 },
                    { 0.1, 0, 0.1, 0.7, 0.12, 0.9 } },
                { { 0.08, 0, 0.08, 0.23, 1, 0.92 }, { 0.23, 0.75, 0.1, 0.92, 0.92, 0.9 },
                    { 0.1, 0, 0.1, 0.7, 0.12, 0.9 } },
                { { 0.08, 0, 0.08, 0.23, 1, 0.92 }, { 0.23, 0.75, 0.1, 0.92, 0.92, 0.9 },
                    { 0.1, 0, 0.1, 0.7, 0.12, 0.9 } },
                { { 0.08, 0, 0.08, 0.23, 1, 0.92 }, { 0.23, 0.75, 0.1, 0.92, 0.92, 0.9 },
                    { 0.1, 0, 0.1, 0.7, 0.12, 0.9 } } },
            new String[][] {
                { "gtsr:remaster_0f75debed88ddcec", "gtsr:remaster_0f75debed88ddcec", "gtsr:remaster_0f75debed88ddcec",
                    "gtsr:remaster_0f75debed88ddcec", "gtsr:remaster_0f75debed88ddcec",
                    "gtsr:remaster_0f75debed88ddcec" },
                { "gtsr:remaster_0f75debed88ddcec", "gtsr:remaster_0f75debed88ddcec", "gtsr:remaster_0f75debed88ddcec",
                    "gtsr:remaster_0f75debed88ddcec", "gtsr:remaster_0f75debed88ddcec",
                    "gtsr:remaster_0f75debed88ddcec" },
                { "gtsr:remaster_0f75debed88ddcec", "gtsr:remaster_0f75debed88ddcec", "gtsr:remaster_0f75debed88ddcec",
                    "gtsr:remaster_0f75debed88ddcec", "gtsr:remaster_0f75debed88ddcec",
                    "gtsr:remaster_0f75debed88ddcec" },
                { "gtsr:remaster_0f75debed88ddcec", "gtsr:remaster_0f75debed88ddcec", "gtsr:remaster_0f75debed88ddcec",
                    "gtsr:remaster_0f75debed88ddcec", "gtsr:remaster_0f75debed88ddcec",
                    "gtsr:remaster_0f75debed88ddcec" } });
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

    private static RemasterBlock block59() {
        return new RemasterBlock(
            "gtsr:draft5_stair_baluster",
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
                { "gtsr:remaster_38d6a7e04c77f857", "gtsr:remaster_38d6a7e04c77f857", "gtsr:remaster_38d6a7e04c77f857",
                    "gtsr:remaster_38d6a7e04c77f857", "gtsr:remaster_38d6a7e04c77f857",
                    "gtsr:remaster_38d6a7e04c77f857" },
                { "gtsr:remaster_38d6a7e04c77f857", "gtsr:remaster_38d6a7e04c77f857", "gtsr:remaster_38d6a7e04c77f857",
                    "gtsr:remaster_38d6a7e04c77f857", "gtsr:remaster_38d6a7e04c77f857",
                    "gtsr:remaster_38d6a7e04c77f857" },
                { "gtsr:remaster_38d6a7e04c77f857", "gtsr:remaster_38d6a7e04c77f857", "gtsr:remaster_38d6a7e04c77f857",
                    "gtsr:remaster_38d6a7e04c77f857", "gtsr:remaster_38d6a7e04c77f857",
                    "gtsr:remaster_38d6a7e04c77f857" },
                { "gtsr:remaster_38d6a7e04c77f857", "gtsr:remaster_38d6a7e04c77f857", "gtsr:remaster_38d6a7e04c77f857",
                    "gtsr:remaster_38d6a7e04c77f857", "gtsr:remaster_38d6a7e04c77f857",
                    "gtsr:remaster_38d6a7e04c77f857" } });
    }

    private static RemasterBlock block60() {
        return new RemasterBlock(
            "gtsr:draft5_shaft_rim",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_c6807f9288b98ebb", "gtsr:remaster_c6807f9288b98ebb", "gtsr:remaster_c6807f9288b98ebb",
                    "gtsr:remaster_c6807f9288b98ebb", "gtsr:remaster_c6807f9288b98ebb",
                    "gtsr:remaster_c6807f9288b98ebb" },
                { "gtsr:remaster_c6807f9288b98ebb", "gtsr:remaster_c6807f9288b98ebb", "gtsr:remaster_c6807f9288b98ebb",
                    "gtsr:remaster_c6807f9288b98ebb", "gtsr:remaster_c6807f9288b98ebb",
                    "gtsr:remaster_c6807f9288b98ebb" },
                { "gtsr:remaster_c6807f9288b98ebb", "gtsr:remaster_c6807f9288b98ebb", "gtsr:remaster_c6807f9288b98ebb",
                    "gtsr:remaster_c6807f9288b98ebb", "gtsr:remaster_c6807f9288b98ebb",
                    "gtsr:remaster_c6807f9288b98ebb" },
                { "gtsr:remaster_c6807f9288b98ebb", "gtsr:remaster_c6807f9288b98ebb", "gtsr:remaster_c6807f9288b98ebb",
                    "gtsr:remaster_c6807f9288b98ebb", "gtsr:remaster_c6807f9288b98ebb",
                    "gtsr:remaster_c6807f9288b98ebb" } });
    }

    private static RemasterBlock block61() {
        return new RemasterBlock(
            "gtsr:draft5_resin_casing",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_25cd8e987703af1b", "gtsr:remaster_25cd8e987703af1b", "gtsr:remaster_25cd8e987703af1b",
                    "gtsr:remaster_25cd8e987703af1b", "gtsr:remaster_25cd8e987703af1b",
                    "gtsr:remaster_25cd8e987703af1b" },
                { "gtsr:remaster_25cd8e987703af1b", "gtsr:remaster_25cd8e987703af1b", "gtsr:remaster_25cd8e987703af1b",
                    "gtsr:remaster_25cd8e987703af1b", "gtsr:remaster_25cd8e987703af1b",
                    "gtsr:remaster_25cd8e987703af1b" },
                { "gtsr:remaster_25cd8e987703af1b", "gtsr:remaster_25cd8e987703af1b", "gtsr:remaster_25cd8e987703af1b",
                    "gtsr:remaster_25cd8e987703af1b", "gtsr:remaster_25cd8e987703af1b",
                    "gtsr:remaster_25cd8e987703af1b" },
                { "gtsr:remaster_25cd8e987703af1b", "gtsr:remaster_25cd8e987703af1b", "gtsr:remaster_25cd8e987703af1b",
                    "gtsr:remaster_25cd8e987703af1b", "gtsr:remaster_25cd8e987703af1b",
                    "gtsr:remaster_25cd8e987703af1b" } });
    }

    private static RemasterBlock block62() {
        return new RemasterBlock(
            "gtsr:draft5_nest_vein",
            "decoration",
            false,
            true,
            "vine",
            new double[][][] { {}, { { 0, 0, 0.975, 1, 1, 0.99 } }, { { 0.01, 0, 0, 0.025, 1, 1 } },
                { { 0, 0, 0.975, 1, 1, 0.99 }, { 0.01, 0, 0, 0.025, 1, 1 } }, { { 0, 0, 0.01, 1, 1, 0.025 } },
                { { 0, 0, 0.01, 1, 1, 0.025 }, { 0, 0, 0.975, 1, 1, 0.99 } },
                { { 0, 0, 0.01, 1, 1, 0.025 }, { 0.01, 0, 0, 0.025, 1, 1 } },
                { { 0, 0, 0.01, 1, 1, 0.025 }, { 0, 0, 0.975, 1, 1, 0.99 }, { 0.01, 0, 0, 0.025, 1, 1 } },
                { { 0.975, 0, 0, 0.99, 1, 1 } }, { { 0, 0, 0.975, 1, 1, 0.99 }, { 0.975, 0, 0, 0.99, 1, 1 } },
                { { 0.01, 0, 0, 0.025, 1, 1 }, { 0.975, 0, 0, 0.99, 1, 1 } },
                { { 0, 0, 0.975, 1, 1, 0.99 }, { 0.01, 0, 0, 0.025, 1, 1 }, { 0.975, 0, 0, 0.99, 1, 1 } },
                { { 0, 0, 0.01, 1, 1, 0.025 }, { 0.975, 0, 0, 0.99, 1, 1 } },
                { { 0, 0, 0.01, 1, 1, 0.025 }, { 0, 0, 0.975, 1, 1, 0.99 }, { 0.975, 0, 0, 0.99, 1, 1 } },
                { { 0, 0, 0.01, 1, 1, 0.025 }, { 0.01, 0, 0, 0.025, 1, 1 }, { 0.975, 0, 0, 0.99, 1, 1 } },
                { { 0, 0, 0.01, 1, 1, 0.025 }, { 0, 0, 0.975, 1, 1, 0.99 }, { 0.01, 0, 0, 0.025, 1, 1 },
                    { 0.975, 0, 0, 0.99, 1, 1 } } },
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
                    "gtsr:remaster_23ae657536306a2d" },
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
                    "gtsr:remaster_23ae657536306a2d" },
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
                    "gtsr:remaster_23ae657536306a2d" },
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

    private static RemasterBlock block64() {
        return new RemasterBlock(
            "gtsr:draft5_salt_bloom",
            "decoration",
            false,
            true,
            "cross",
            new double[][][] { {}, {}, {}, {} },
            new String[][] {
                { "gtsr:remaster_de4bb82e85b37fd3", "gtsr:remaster_de4bb82e85b37fd3", "gtsr:remaster_de4bb82e85b37fd3",
                    "gtsr:remaster_de4bb82e85b37fd3", "gtsr:remaster_de4bb82e85b37fd3",
                    "gtsr:remaster_de4bb82e85b37fd3" },
                { "gtsr:remaster_de4bb82e85b37fd3", "gtsr:remaster_de4bb82e85b37fd3", "gtsr:remaster_de4bb82e85b37fd3",
                    "gtsr:remaster_de4bb82e85b37fd3", "gtsr:remaster_de4bb82e85b37fd3",
                    "gtsr:remaster_de4bb82e85b37fd3" },
                { "gtsr:remaster_de4bb82e85b37fd3", "gtsr:remaster_de4bb82e85b37fd3", "gtsr:remaster_de4bb82e85b37fd3",
                    "gtsr:remaster_de4bb82e85b37fd3", "gtsr:remaster_de4bb82e85b37fd3",
                    "gtsr:remaster_de4bb82e85b37fd3" },
                { "gtsr:remaster_de4bb82e85b37fd3", "gtsr:remaster_de4bb82e85b37fd3", "gtsr:remaster_de4bb82e85b37fd3",
                    "gtsr:remaster_de4bb82e85b37fd3", "gtsr:remaster_de4bb82e85b37fd3",
                    "gtsr:remaster_de4bb82e85b37fd3" } });
    }

    private static RemasterBlock block65() {
        return new RemasterBlock(
            "gtsr:draft5_corrosion_flake",
            "decoration",
            false,
            true,
            "vine",
            new double[][][] { {}, { { 0, 0, 0.975, 1, 1, 0.99 } }, { { 0.01, 0, 0, 0.025, 1, 1 } },
                { { 0, 0, 0.975, 1, 1, 0.99 }, { 0.01, 0, 0, 0.025, 1, 1 } }, { { 0, 0, 0.01, 1, 1, 0.025 } },
                { { 0, 0, 0.01, 1, 1, 0.025 }, { 0, 0, 0.975, 1, 1, 0.99 } },
                { { 0, 0, 0.01, 1, 1, 0.025 }, { 0.01, 0, 0, 0.025, 1, 1 } },
                { { 0, 0, 0.01, 1, 1, 0.025 }, { 0, 0, 0.975, 1, 1, 0.99 }, { 0.01, 0, 0, 0.025, 1, 1 } },
                { { 0.975, 0, 0, 0.99, 1, 1 } }, { { 0, 0, 0.975, 1, 1, 0.99 }, { 0.975, 0, 0, 0.99, 1, 1 } },
                { { 0.01, 0, 0, 0.025, 1, 1 }, { 0.975, 0, 0, 0.99, 1, 1 } },
                { { 0, 0, 0.975, 1, 1, 0.99 }, { 0.01, 0, 0, 0.025, 1, 1 }, { 0.975, 0, 0, 0.99, 1, 1 } },
                { { 0, 0, 0.01, 1, 1, 0.025 }, { 0.975, 0, 0, 0.99, 1, 1 } },
                { { 0, 0, 0.01, 1, 1, 0.025 }, { 0, 0, 0.975, 1, 1, 0.99 }, { 0.975, 0, 0, 0.99, 1, 1 } },
                { { 0, 0, 0.01, 1, 1, 0.025 }, { 0.01, 0, 0, 0.025, 1, 1 }, { 0.975, 0, 0, 0.99, 1, 1 } },
                { { 0, 0, 0.01, 1, 1, 0.025 }, { 0, 0, 0.975, 1, 1, 0.99 }, { 0.01, 0, 0, 0.025, 1, 1 },
                    { 0.975, 0, 0, 0.99, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e" },
                { "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e" },
                { "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e" },
                { "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e" },
                { "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e" },
                { "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e" },
                { "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e" },
                { "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e" },
                { "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e" },
                { "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e" },
                { "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e" },
                { "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e" },
                { "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e" },
                { "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e" },
                { "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e" },
                { "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e", "gtsr:remaster_4a5cf5733a37301e",
                    "gtsr:remaster_4a5cf5733a37301e" } });
    }

    private static RemasterBlock block66() {
        return new RemasterBlock(
            "gtsr:draft5_fracture_wedge",
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
                { "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b",
                    "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b",
                    "gtsr:remaster_423c83c879586c2b" },
                { "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b",
                    "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b",
                    "gtsr:remaster_423c83c879586c2b" },
                { "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b",
                    "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b",
                    "gtsr:remaster_423c83c879586c2b" },
                { "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b",
                    "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b",
                    "gtsr:remaster_423c83c879586c2b" },
                { "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b",
                    "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b",
                    "gtsr:remaster_423c83c879586c2b" },
                { "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b",
                    "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b",
                    "gtsr:remaster_423c83c879586c2b" },
                { "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b",
                    "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b",
                    "gtsr:remaster_423c83c879586c2b" },
                { "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b",
                    "gtsr:remaster_423c83c879586c2b", "gtsr:remaster_423c83c879586c2b",
                    "gtsr:remaster_423c83c879586c2b" } });
    }

    private static RemasterBlock block67() {
        return new RemasterBlock(
            "gtsr:draft5_collapsed_bracket",
            "decoration",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0.08, 0, 0.08, 0.23, 1, 0.92 }, { 0.23, 0.75, 0.1, 0.92, 0.92, 0.9 },
                    { 0.1, 0, 0.1, 0.7, 0.12, 0.9 } },
                { { 0.08, 0, 0.08, 0.23, 1, 0.92 }, { 0.23, 0.75, 0.1, 0.92, 0.92, 0.9 },
                    { 0.1, 0, 0.1, 0.7, 0.12, 0.9 } },
                { { 0.08, 0, 0.08, 0.23, 1, 0.92 }, { 0.23, 0.75, 0.1, 0.92, 0.92, 0.9 },
                    { 0.1, 0, 0.1, 0.7, 0.12, 0.9 } },
                { { 0.08, 0, 0.08, 0.23, 1, 0.92 }, { 0.23, 0.75, 0.1, 0.92, 0.92, 0.9 },
                    { 0.1, 0, 0.1, 0.7, 0.12, 0.9 } } },
            new String[][] {
                { "gtsr:remaster_1ebcc228341f2a48", "gtsr:remaster_1ebcc228341f2a48", "gtsr:remaster_1ebcc228341f2a48",
                    "gtsr:remaster_1ebcc228341f2a48", "gtsr:remaster_1ebcc228341f2a48",
                    "gtsr:remaster_1ebcc228341f2a48" },
                { "gtsr:remaster_1ebcc228341f2a48", "gtsr:remaster_1ebcc228341f2a48", "gtsr:remaster_1ebcc228341f2a48",
                    "gtsr:remaster_1ebcc228341f2a48", "gtsr:remaster_1ebcc228341f2a48",
                    "gtsr:remaster_1ebcc228341f2a48" },
                { "gtsr:remaster_1ebcc228341f2a48", "gtsr:remaster_1ebcc228341f2a48", "gtsr:remaster_1ebcc228341f2a48",
                    "gtsr:remaster_1ebcc228341f2a48", "gtsr:remaster_1ebcc228341f2a48",
                    "gtsr:remaster_1ebcc228341f2a48" },
                { "gtsr:remaster_1ebcc228341f2a48", "gtsr:remaster_1ebcc228341f2a48", "gtsr:remaster_1ebcc228341f2a48",
                    "gtsr:remaster_1ebcc228341f2a48", "gtsr:remaster_1ebcc228341f2a48",
                    "gtsr:remaster_1ebcc228341f2a48" } });
    }

    private static RemasterBlock block68() {
        return new RemasterBlock(
            "gtsr:draft5_oath_stanchion",
            "decoration",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0.08, 0, 0.08, 0.23, 1, 0.92 }, { 0.23, 0.75, 0.1, 0.92, 0.92, 0.9 },
                    { 0.1, 0, 0.1, 0.7, 0.12, 0.9 } },
                { { 0.08, 0, 0.08, 0.23, 1, 0.92 }, { 0.23, 0.75, 0.1, 0.92, 0.92, 0.9 },
                    { 0.1, 0, 0.1, 0.7, 0.12, 0.9 } },
                { { 0.08, 0, 0.08, 0.23, 1, 0.92 }, { 0.23, 0.75, 0.1, 0.92, 0.92, 0.9 },
                    { 0.1, 0, 0.1, 0.7, 0.12, 0.9 } },
                { { 0.08, 0, 0.08, 0.23, 1, 0.92 }, { 0.23, 0.75, 0.1, 0.92, 0.92, 0.9 },
                    { 0.1, 0, 0.1, 0.7, 0.12, 0.9 } } },
            new String[][] {
                { "gtsr:remaster_191ef49515f7c294", "gtsr:remaster_191ef49515f7c294", "gtsr:remaster_191ef49515f7c294",
                    "gtsr:remaster_191ef49515f7c294", "gtsr:remaster_191ef49515f7c294",
                    "gtsr:remaster_191ef49515f7c294" },
                { "gtsr:remaster_191ef49515f7c294", "gtsr:remaster_191ef49515f7c294", "gtsr:remaster_191ef49515f7c294",
                    "gtsr:remaster_191ef49515f7c294", "gtsr:remaster_191ef49515f7c294",
                    "gtsr:remaster_191ef49515f7c294" },
                { "gtsr:remaster_191ef49515f7c294", "gtsr:remaster_191ef49515f7c294", "gtsr:remaster_191ef49515f7c294",
                    "gtsr:remaster_191ef49515f7c294", "gtsr:remaster_191ef49515f7c294",
                    "gtsr:remaster_191ef49515f7c294" },
                { "gtsr:remaster_191ef49515f7c294", "gtsr:remaster_191ef49515f7c294", "gtsr:remaster_191ef49515f7c294",
                    "gtsr:remaster_191ef49515f7c294", "gtsr:remaster_191ef49515f7c294",
                    "gtsr:remaster_191ef49515f7c294" } });
    }

    private static RemasterBlock block69() {
        return new RemasterBlock(
            "gtsr:draft5_hush_inscription",
            "decoration",
            true,
            false,
            "cube",
            new double[][][] { { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } }, { { 0, 0, 0, 1, 1, 1 } },
                { { 0, 0, 0, 1, 1, 1 } } },
            new String[][] {
                { "gtsr:remaster_51c1b774398a07f2", "gtsr:remaster_51c1b774398a07f2", "gtsr:remaster_51c1b774398a07f2",
                    "gtsr:remaster_51c1b774398a07f2", "gtsr:remaster_51c1b774398a07f2",
                    "gtsr:remaster_51c1b774398a07f2" },
                { "gtsr:remaster_51c1b774398a07f2", "gtsr:remaster_51c1b774398a07f2", "gtsr:remaster_51c1b774398a07f2",
                    "gtsr:remaster_51c1b774398a07f2", "gtsr:remaster_51c1b774398a07f2",
                    "gtsr:remaster_51c1b774398a07f2" },
                { "gtsr:remaster_51c1b774398a07f2", "gtsr:remaster_51c1b774398a07f2", "gtsr:remaster_51c1b774398a07f2",
                    "gtsr:remaster_51c1b774398a07f2", "gtsr:remaster_51c1b774398a07f2",
                    "gtsr:remaster_51c1b774398a07f2" },
                { "gtsr:remaster_51c1b774398a07f2", "gtsr:remaster_51c1b774398a07f2", "gtsr:remaster_51c1b774398a07f2",
                    "gtsr:remaster_51c1b774398a07f2", "gtsr:remaster_51c1b774398a07f2",
                    "gtsr:remaster_51c1b774398a07f2" } });
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

    private static RemasterBlock block73() {
        return new RemasterBlock(
            "gtsr:draft6_bed_frame",
            "decoration",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0.0625, 0, 0.0625, 0.1875, 0.3125, 0.1875 }, { 0.0625, 0, 0.8125, 0.1875, 0.3125, 0.9375 },
                    { 0.8125, 0, 0.0625, 0.9375, 0.3125, 0.1875 }, { 0.8125, 0, 0.8125, 0.9375, 0.3125, 0.9375 },
                    { 0, 0.3125, 0, 1, 0.4375, 1 }, { 0, 0.4375, 0.0625, 0.125, 0.875, 0.9375 },
                    { 0.875, 0.4375, 0.0625, 1, 0.625, 0.9375 } },
                { { 0.8125, 0, 0.8125, 0.9375, 0.3125, 0.9375 }, { 0.8125, 0, 0.0625, 0.9375, 0.3125, 0.1875 },
                    { 0.0625, 0, 0.8125, 0.1875, 0.3125, 0.9375 }, { 0.0625, 0, 0.0625, 0.1875, 0.3125, 0.1875 },
                    { 0, 0.3125, 0, 1, 0.4375, 1 }, { 0.875, 0.4375, 0.0625, 1, 0.875, 0.9375 },
                    { 0, 0.4375, 0.0625, 0.125, 0.625, 0.9375 } },
                { { 0.8125, 0, 0.0625, 0.9375, 0.3125, 0.1875 }, { 0.0625, 0, 0.0625, 0.1875, 0.3125, 0.1875 },
                    { 0.8125, 0, 0.8125, 0.9375, 0.3125, 0.9375 }, { 0.0625, 0, 0.8125, 0.1875, 0.3125, 0.9375 },
                    { 0, 0.3125, 0, 1, 0.4375, 1 }, { 0.0625, 0.4375, 0, 0.9375, 0.875, 0.125 },
                    { 0.0625, 0.4375, 0.875, 0.9375, 0.625, 1 } },
                { { 0.0625, 0, 0.8125, 0.1875, 0.3125, 0.9375 }, { 0.8125, 0, 0.8125, 0.9375, 0.3125, 0.9375 },
                    { 0.0625, 0, 0.0625, 0.1875, 0.3125, 0.1875 }, { 0.8125, 0, 0.0625, 0.9375, 0.3125, 0.1875 },
                    { 0, 0.3125, 0, 1, 0.4375, 1 }, { 0.0625, 0.4375, 0.875, 0.9375, 0.875, 1 },
                    { 0.0625, 0.4375, 0, 0.9375, 0.625, 0.125 } } },
            new String[][] {
                { "gtsr:remaster_f8c8f2868bfa0aa6", "gtsr:remaster_f8c8f2868bfa0aa6", "gtsr:remaster_f8c8f2868bfa0aa6",
                    "gtsr:remaster_f8c8f2868bfa0aa6", "gtsr:remaster_f8c8f2868bfa0aa6",
                    "gtsr:remaster_f8c8f2868bfa0aa6" },
                { "gtsr:remaster_f8c8f2868bfa0aa6", "gtsr:remaster_f8c8f2868bfa0aa6", "gtsr:remaster_f8c8f2868bfa0aa6",
                    "gtsr:remaster_f8c8f2868bfa0aa6", "gtsr:remaster_f8c8f2868bfa0aa6",
                    "gtsr:remaster_f8c8f2868bfa0aa6" },
                { "gtsr:remaster_f8c8f2868bfa0aa6", "gtsr:remaster_f8c8f2868bfa0aa6", "gtsr:remaster_f8c8f2868bfa0aa6",
                    "gtsr:remaster_f8c8f2868bfa0aa6", "gtsr:remaster_f8c8f2868bfa0aa6",
                    "gtsr:remaster_f8c8f2868bfa0aa6" },
                { "gtsr:remaster_f8c8f2868bfa0aa6", "gtsr:remaster_f8c8f2868bfa0aa6", "gtsr:remaster_f8c8f2868bfa0aa6",
                    "gtsr:remaster_f8c8f2868bfa0aa6", "gtsr:remaster_f8c8f2868bfa0aa6",
                    "gtsr:remaster_f8c8f2868bfa0aa6" } });
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

    private static RemasterBlock block76() {
        return new RemasterBlock(
            "gtsr:draft6_window_shutter",
            "decoration",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0, 0.0625, 0, 0.125, 0.9375, 0.125 }, { 0, 0.0625, 0.875, 0.125, 0.9375, 1 },
                    { 0, 0.125, 0.125, 0.125, 0.1875, 0.875 }, { 0, 0.3125, 0.125, 0.125, 0.375, 0.875 },
                    { 0, 0.5, 0.125, 0.125, 0.5625, 0.875 }, { 0, 0.6875, 0.125, 0.125, 0.75, 0.875 },
                    { 0, 0.875, 0.125, 0.125, 0.9375, 0.875 } },
                { { 0.875, 0.0625, 0.875, 1, 0.9375, 1 }, { 0.875, 0.0625, 0, 1, 0.9375, 0.125 },
                    { 0.875, 0.125, 0.125, 1, 0.1875, 0.875 }, { 0.875, 0.3125, 0.125, 1, 0.375, 0.875 },
                    { 0.875, 0.5, 0.125, 1, 0.5625, 0.875 }, { 0.875, 0.6875, 0.125, 1, 0.75, 0.875 },
                    { 0.875, 0.875, 0.125, 1, 0.9375, 0.875 } },
                { { 0.875, 0.0625, 0, 1, 0.9375, 0.125 }, { 0, 0.0625, 0, 0.125, 0.9375, 0.125 },
                    { 0.125, 0.125, 0, 0.875, 0.1875, 0.125 }, { 0.125, 0.3125, 0, 0.875, 0.375, 0.125 },
                    { 0.125, 0.5, 0, 0.875, 0.5625, 0.125 }, { 0.125, 0.6875, 0, 0.875, 0.75, 0.125 },
                    { 0.125, 0.875, 0, 0.875, 0.9375, 0.125 } },
                { { 0, 0.0625, 0.875, 0.125, 0.9375, 1 }, { 0.875, 0.0625, 0.875, 1, 0.9375, 1 },
                    { 0.125, 0.125, 0.875, 0.875, 0.1875, 1 }, { 0.125, 0.3125, 0.875, 0.875, 0.375, 1 },
                    { 0.125, 0.5, 0.875, 0.875, 0.5625, 1 }, { 0.125, 0.6875, 0.875, 0.875, 0.75, 1 },
                    { 0.125, 0.875, 0.875, 0.875, 0.9375, 1 } } },
            new String[][] {
                { "gtsr:remaster_649b5e0951ca1d79", "gtsr:remaster_649b5e0951ca1d79", "gtsr:remaster_649b5e0951ca1d79",
                    "gtsr:remaster_649b5e0951ca1d79", "gtsr:remaster_649b5e0951ca1d79",
                    "gtsr:remaster_649b5e0951ca1d79" },
                { "gtsr:remaster_649b5e0951ca1d79", "gtsr:remaster_649b5e0951ca1d79", "gtsr:remaster_649b5e0951ca1d79",
                    "gtsr:remaster_649b5e0951ca1d79", "gtsr:remaster_649b5e0951ca1d79",
                    "gtsr:remaster_649b5e0951ca1d79" },
                { "gtsr:remaster_649b5e0951ca1d79", "gtsr:remaster_649b5e0951ca1d79", "gtsr:remaster_649b5e0951ca1d79",
                    "gtsr:remaster_649b5e0951ca1d79", "gtsr:remaster_649b5e0951ca1d79",
                    "gtsr:remaster_649b5e0951ca1d79" },
                { "gtsr:remaster_649b5e0951ca1d79", "gtsr:remaster_649b5e0951ca1d79", "gtsr:remaster_649b5e0951ca1d79",
                    "gtsr:remaster_649b5e0951ca1d79", "gtsr:remaster_649b5e0951ca1d79",
                    "gtsr:remaster_649b5e0951ca1d79" } });
    }

    private static RemasterBlock block77() {
        return new RemasterBlock(
            "gtsr:draft6_window_sill",
            "decoration",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0, 0.25, 0, 0.625, 0.375, 1 }, { 0, 0, 0.125, 0.125, 0.25, 0.25 },
                    { 0, 0, 0.75, 0.125, 0.25, 0.875 }, { 0.125, 0.125, 0.125, 0.375, 0.25, 0.25 },
                    { 0.125, 0.125, 0.75, 0.375, 0.25, 0.875 } },
                { { 0.375, 0.25, 0, 1, 0.375, 1 }, { 0.875, 0, 0.75, 1, 0.25, 0.875 },
                    { 0.875, 0, 0.125, 1, 0.25, 0.25 }, { 0.625, 0.125, 0.75, 0.875, 0.25, 0.875 },
                    { 0.625, 0.125, 0.125, 0.875, 0.25, 0.25 } },
                { { 0, 0.25, 0, 1, 0.375, 0.625 }, { 0.75, 0, 0, 0.875, 0.25, 0.125 },
                    { 0.125, 0, 0, 0.25, 0.25, 0.125 }, { 0.75, 0.125, 0.125, 0.875, 0.25, 0.375 },
                    { 0.125, 0.125, 0.125, 0.25, 0.25, 0.375 } },
                { { 0, 0.25, 0.375, 1, 0.375, 1 }, { 0.125, 0, 0.875, 0.25, 0.25, 1 },
                    { 0.75, 0, 0.875, 0.875, 0.25, 1 }, { 0.125, 0.125, 0.625, 0.25, 0.25, 0.875 },
                    { 0.75, 0.125, 0.625, 0.875, 0.25, 0.875 } } },
            new String[][] {
                { "gtsr:remaster_d478682c1c004439", "gtsr:remaster_d478682c1c004439", "gtsr:remaster_d478682c1c004439",
                    "gtsr:remaster_d478682c1c004439", "gtsr:remaster_d478682c1c004439",
                    "gtsr:remaster_d478682c1c004439" },
                { "gtsr:remaster_d478682c1c004439", "gtsr:remaster_d478682c1c004439", "gtsr:remaster_d478682c1c004439",
                    "gtsr:remaster_d478682c1c004439", "gtsr:remaster_d478682c1c004439",
                    "gtsr:remaster_d478682c1c004439" },
                { "gtsr:remaster_d478682c1c004439", "gtsr:remaster_d478682c1c004439", "gtsr:remaster_d478682c1c004439",
                    "gtsr:remaster_d478682c1c004439", "gtsr:remaster_d478682c1c004439",
                    "gtsr:remaster_d478682c1c004439" },
                { "gtsr:remaster_d478682c1c004439", "gtsr:remaster_d478682c1c004439", "gtsr:remaster_d478682c1c004439",
                    "gtsr:remaster_d478682c1c004439", "gtsr:remaster_d478682c1c004439",
                    "gtsr:remaster_d478682c1c004439" } });
    }

    private static RemasterBlock block78() {
        return new RemasterBlock(
            "gtsr:draft6_pipe_elbow",
            "decoration",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0.3125, 0, 0.3125, 0.6875, 0.625, 0.6875 }, { 0.3125, 0.3125, 0.3125, 1, 0.6875, 0.6875 },
                    { 0.25, 0, 0.25, 0.75, 0.125, 0.75 }, { 0.875, 0.25, 0.25, 1, 0.75, 0.75 } },
                { { 0.3125, 0, 0.3125, 0.6875, 0.625, 0.6875 }, { 0, 0.3125, 0.3125, 0.6875, 0.6875, 0.6875 },
                    { 0.25, 0, 0.25, 0.75, 0.125, 0.75 }, { 0, 0.25, 0.25, 0.125, 0.75, 0.75 } },
                { { 0.3125, 0, 0.3125, 0.6875, 0.625, 0.6875 }, { 0.3125, 0.3125, 0.3125, 0.6875, 0.6875, 1 },
                    { 0.25, 0, 0.25, 0.75, 0.125, 0.75 }, { 0.25, 0.25, 0.875, 0.75, 0.75, 1 } },
                { { 0.3125, 0, 0.3125, 0.6875, 0.625, 0.6875 }, { 0.3125, 0.3125, 0, 0.6875, 0.6875, 0.6875 },
                    { 0.25, 0, 0.25, 0.75, 0.125, 0.75 }, { 0.25, 0.25, 0, 0.75, 0.75, 0.125 } } },
            new String[][] {
                { "gtsr:remaster_32fa268f7286e476", "gtsr:remaster_32fa268f7286e476", "gtsr:remaster_32fa268f7286e476",
                    "gtsr:remaster_32fa268f7286e476", "gtsr:remaster_32fa268f7286e476",
                    "gtsr:remaster_32fa268f7286e476" },
                { "gtsr:remaster_32fa268f7286e476", "gtsr:remaster_32fa268f7286e476", "gtsr:remaster_32fa268f7286e476",
                    "gtsr:remaster_32fa268f7286e476", "gtsr:remaster_32fa268f7286e476",
                    "gtsr:remaster_32fa268f7286e476" },
                { "gtsr:remaster_32fa268f7286e476", "gtsr:remaster_32fa268f7286e476", "gtsr:remaster_32fa268f7286e476",
                    "gtsr:remaster_32fa268f7286e476", "gtsr:remaster_32fa268f7286e476",
                    "gtsr:remaster_32fa268f7286e476" },
                { "gtsr:remaster_32fa268f7286e476", "gtsr:remaster_32fa268f7286e476", "gtsr:remaster_32fa268f7286e476",
                    "gtsr:remaster_32fa268f7286e476", "gtsr:remaster_32fa268f7286e476",
                    "gtsr:remaster_32fa268f7286e476" } });
    }

    private static RemasterBlock block79() {
        return new RemasterBlock(
            "gtsr:draft6_pipe_flange",
            "decoration",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0, 0.3125, 0.3125, 1, 0.6875, 0.6875 }, { 0.4375, 0.125, 0.125, 0.5625, 0.3125, 0.875 },
                    { 0.4375, 0.6875, 0.125, 0.5625, 0.875, 0.875 }, { 0.4375, 0.3125, 0.125, 0.5625, 0.6875, 0.3125 },
                    { 0.4375, 0.3125, 0.6875, 0.5625, 0.6875, 0.875 } },
                { { 0, 0.3125, 0.3125, 1, 0.6875, 0.6875 }, { 0.4375, 0.125, 0.125, 0.5625, 0.3125, 0.875 },
                    { 0.4375, 0.6875, 0.125, 0.5625, 0.875, 0.875 }, { 0.4375, 0.3125, 0.6875, 0.5625, 0.6875, 0.875 },
                    { 0.4375, 0.3125, 0.125, 0.5625, 0.6875, 0.3125 } },
                { { 0.3125, 0.3125, 0, 0.6875, 0.6875, 1 }, { 0.125, 0.125, 0.4375, 0.875, 0.3125, 0.5625 },
                    { 0.125, 0.6875, 0.4375, 0.875, 0.875, 0.5625 }, { 0.6875, 0.3125, 0.4375, 0.875, 0.6875, 0.5625 },
                    { 0.125, 0.3125, 0.4375, 0.3125, 0.6875, 0.5625 } },
                { { 0.3125, 0.3125, 0, 0.6875, 0.6875, 1 }, { 0.125, 0.125, 0.4375, 0.875, 0.3125, 0.5625 },
                    { 0.125, 0.6875, 0.4375, 0.875, 0.875, 0.5625 }, { 0.125, 0.3125, 0.4375, 0.3125, 0.6875, 0.5625 },
                    { 0.6875, 0.3125, 0.4375, 0.875, 0.6875, 0.5625 } } },
            new String[][] {
                { "gtsr:remaster_88c29fa9657ec668", "gtsr:remaster_88c29fa9657ec668", "gtsr:remaster_88c29fa9657ec668",
                    "gtsr:remaster_88c29fa9657ec668", "gtsr:remaster_88c29fa9657ec668",
                    "gtsr:remaster_88c29fa9657ec668" },
                { "gtsr:remaster_88c29fa9657ec668", "gtsr:remaster_88c29fa9657ec668", "gtsr:remaster_88c29fa9657ec668",
                    "gtsr:remaster_88c29fa9657ec668", "gtsr:remaster_88c29fa9657ec668",
                    "gtsr:remaster_88c29fa9657ec668" },
                { "gtsr:remaster_88c29fa9657ec668", "gtsr:remaster_88c29fa9657ec668", "gtsr:remaster_88c29fa9657ec668",
                    "gtsr:remaster_88c29fa9657ec668", "gtsr:remaster_88c29fa9657ec668",
                    "gtsr:remaster_88c29fa9657ec668" },
                { "gtsr:remaster_88c29fa9657ec668", "gtsr:remaster_88c29fa9657ec668", "gtsr:remaster_88c29fa9657ec668",
                    "gtsr:remaster_88c29fa9657ec668", "gtsr:remaster_88c29fa9657ec668",
                    "gtsr:remaster_88c29fa9657ec668" } });
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

    private static RemasterBlock block81() {
        return new RemasterBlock(
            "gtsr:draft6_wall_bracket",
            "decoration",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0, 0, 0.125, 0.125, 1, 0.875 }, { 0.125, 0.75, 0.1875, 0.875, 0.875, 0.8125 },
                    { 0.125, 0.1875, 0.3125, 0.3125, 0.4375, 0.6875 }, { 0.3125, 0.375, 0.3125, 0.5, 0.625, 0.6875 },
                    { 0.5, 0.5625, 0.3125, 0.6875, 0.75, 0.6875 } },
                { { 0.875, 0, 0.125, 1, 1, 0.875 }, { 0.125, 0.75, 0.1875, 0.875, 0.875, 0.8125 },
                    { 0.6875, 0.1875, 0.3125, 0.875, 0.4375, 0.6875 }, { 0.5, 0.375, 0.3125, 0.6875, 0.625, 0.6875 },
                    { 0.3125, 0.5625, 0.3125, 0.5, 0.75, 0.6875 } },
                { { 0.125, 0, 0, 0.875, 1, 0.125 }, { 0.1875, 0.75, 0.125, 0.8125, 0.875, 0.875 },
                    { 0.3125, 0.1875, 0.125, 0.6875, 0.4375, 0.3125 }, { 0.3125, 0.375, 0.3125, 0.6875, 0.625, 0.5 },
                    { 0.3125, 0.5625, 0.5, 0.6875, 0.75, 0.6875 } },
                { { 0.125, 0, 0.875, 0.875, 1, 1 }, { 0.1875, 0.75, 0.125, 0.8125, 0.875, 0.875 },
                    { 0.3125, 0.1875, 0.6875, 0.6875, 0.4375, 0.875 }, { 0.3125, 0.375, 0.5, 0.6875, 0.625, 0.6875 },
                    { 0.3125, 0.5625, 0.3125, 0.6875, 0.75, 0.5 } } },
            new String[][] {
                { "gtsr:remaster_74fe3a757efd9cb2", "gtsr:remaster_74fe3a757efd9cb2", "gtsr:remaster_74fe3a757efd9cb2",
                    "gtsr:remaster_74fe3a757efd9cb2", "gtsr:remaster_74fe3a757efd9cb2",
                    "gtsr:remaster_74fe3a757efd9cb2" },
                { "gtsr:remaster_74fe3a757efd9cb2", "gtsr:remaster_74fe3a757efd9cb2", "gtsr:remaster_74fe3a757efd9cb2",
                    "gtsr:remaster_74fe3a757efd9cb2", "gtsr:remaster_74fe3a757efd9cb2",
                    "gtsr:remaster_74fe3a757efd9cb2" },
                { "gtsr:remaster_74fe3a757efd9cb2", "gtsr:remaster_74fe3a757efd9cb2", "gtsr:remaster_74fe3a757efd9cb2",
                    "gtsr:remaster_74fe3a757efd9cb2", "gtsr:remaster_74fe3a757efd9cb2",
                    "gtsr:remaster_74fe3a757efd9cb2" },
                { "gtsr:remaster_74fe3a757efd9cb2", "gtsr:remaster_74fe3a757efd9cb2", "gtsr:remaster_74fe3a757efd9cb2",
                    "gtsr:remaster_74fe3a757efd9cb2", "gtsr:remaster_74fe3a757efd9cb2",
                    "gtsr:remaster_74fe3a757efd9cb2" } });
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

    private static RemasterBlock block84() {
        return new RemasterBlock(
            "gtsr:draft6_pollution_membrane",
            "decoration",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0, 0.125, 0.125, 0.0625, 0.4375, 0.4375 }, { 0, 0.375, 0.25, 0.0625, 0.8125, 0.6875 },
                    { 0, 0.5625, 0.5625, 0.0625, 0.9375, 0.875 }, { 0, 0.0625, 0.4375, 0.0625, 0.3125, 0.625 },
                    { 0, 0.75, 0.125, 0.0625, 0.9375, 0.3125 } },
                { { 0.9375, 0.125, 0.5625, 1, 0.4375, 0.875 }, { 0.9375, 0.375, 0.3125, 1, 0.8125, 0.75 },
                    { 0.9375, 0.5625, 0.125, 1, 0.9375, 0.4375 }, { 0.9375, 0.0625, 0.375, 1, 0.3125, 0.5625 },
                    { 0.9375, 0.75, 0.6875, 1, 0.9375, 0.875 } },
                { { 0.5625, 0.125, 0, 0.875, 0.4375, 0.0625 }, { 0.3125, 0.375, 0, 0.75, 0.8125, 0.0625 },
                    { 0.125, 0.5625, 0, 0.4375, 0.9375, 0.0625 }, { 0.375, 0.0625, 0, 0.5625, 0.3125, 0.0625 },
                    { 0.6875, 0.75, 0, 0.875, 0.9375, 0.0625 } },
                { { 0.125, 0.125, 0.9375, 0.4375, 0.4375, 1 }, { 0.25, 0.375, 0.9375, 0.6875, 0.8125, 1 },
                    { 0.5625, 0.5625, 0.9375, 0.875, 0.9375, 1 }, { 0.4375, 0.0625, 0.9375, 0.625, 0.3125, 1 },
                    { 0.125, 0.75, 0.9375, 0.3125, 0.9375, 1 } } },
            new String[][] {
                { "gtsr:remaster_bba5580baba0ba3f", "gtsr:remaster_bba5580baba0ba3f", "gtsr:remaster_bba5580baba0ba3f",
                    "gtsr:remaster_bba5580baba0ba3f", "gtsr:remaster_bba5580baba0ba3f",
                    "gtsr:remaster_bba5580baba0ba3f" },
                { "gtsr:remaster_bba5580baba0ba3f", "gtsr:remaster_bba5580baba0ba3f", "gtsr:remaster_bba5580baba0ba3f",
                    "gtsr:remaster_bba5580baba0ba3f", "gtsr:remaster_bba5580baba0ba3f",
                    "gtsr:remaster_bba5580baba0ba3f" },
                { "gtsr:remaster_bba5580baba0ba3f", "gtsr:remaster_bba5580baba0ba3f", "gtsr:remaster_bba5580baba0ba3f",
                    "gtsr:remaster_bba5580baba0ba3f", "gtsr:remaster_bba5580baba0ba3f",
                    "gtsr:remaster_bba5580baba0ba3f" },
                { "gtsr:remaster_bba5580baba0ba3f", "gtsr:remaster_bba5580baba0ba3f", "gtsr:remaster_bba5580baba0ba3f",
                    "gtsr:remaster_bba5580baba0ba3f", "gtsr:remaster_bba5580baba0ba3f",
                    "gtsr:remaster_bba5580baba0ba3f" } });
    }

    private static RemasterBlock block85() {
        return new RemasterBlock(
            "gtsr:draft6_pollution_tendril",
            "decoration",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0, 0.0625, 0.4375, 0.25, 0.25, 0.625 }, { 0.1875, 0.1875, 0.4375, 0.375, 0.4375, 0.625 },
                    { 0.3125, 0.375, 0.5, 0.5, 0.625, 0.6875 }, { 0.4375, 0.5625, 0.375, 0.625, 0.75, 0.5625 },
                    { 0.5625, 0.625, 0.25, 0.75, 0.8125, 0.4375 }, { 0.625, 0.4375, 0.1875, 0.8125, 0.625, 0.375 },
                    { 0.3125, 0.375, 0.6875, 0.4375, 0.5, 0.9375 }, { 0.375, 0.5, 0.8125, 0.5, 0.6875, 0.9375 } },
                { { 0.75, 0.0625, 0.375, 1, 0.25, 0.5625 }, { 0.625, 0.1875, 0.375, 0.8125, 0.4375, 0.5625 },
                    { 0.5, 0.375, 0.3125, 0.6875, 0.625, 0.5 }, { 0.375, 0.5625, 0.4375, 0.5625, 0.75, 0.625 },
                    { 0.25, 0.625, 0.5625, 0.4375, 0.8125, 0.75 }, { 0.1875, 0.4375, 0.625, 0.375, 0.625, 0.8125 },
                    { 0.5625, 0.375, 0.0625, 0.6875, 0.5, 0.3125 }, { 0.5, 0.5, 0.0625, 0.625, 0.6875, 0.1875 } },
                { { 0.375, 0.0625, 0, 0.5625, 0.25, 0.25 }, { 0.375, 0.1875, 0.1875, 0.5625, 0.4375, 0.375 },
                    { 0.3125, 0.375, 0.3125, 0.5, 0.625, 0.5 }, { 0.4375, 0.5625, 0.4375, 0.625, 0.75, 0.625 },
                    { 0.5625, 0.625, 0.5625, 0.75, 0.8125, 0.75 }, { 0.625, 0.4375, 0.625, 0.8125, 0.625, 0.8125 },
                    { 0.0625, 0.375, 0.3125, 0.3125, 0.5, 0.4375 }, { 0.0625, 0.5, 0.375, 0.1875, 0.6875, 0.5 } },
                { { 0.4375, 0.0625, 0.75, 0.625, 0.25, 1 }, { 0.4375, 0.1875, 0.625, 0.625, 0.4375, 0.8125 },
                    { 0.5, 0.375, 0.5, 0.6875, 0.625, 0.6875 }, { 0.375, 0.5625, 0.375, 0.5625, 0.75, 0.5625 },
                    { 0.25, 0.625, 0.25, 0.4375, 0.8125, 0.4375 }, { 0.1875, 0.4375, 0.1875, 0.375, 0.625, 0.375 },
                    { 0.6875, 0.375, 0.5625, 0.9375, 0.5, 0.6875 }, { 0.8125, 0.5, 0.5, 0.9375, 0.6875, 0.625 } } },
            new String[][] {
                { "gtsr:remaster_b0d25971c8390324", "gtsr:remaster_b0d25971c8390324", "gtsr:remaster_b0d25971c8390324",
                    "gtsr:remaster_b0d25971c8390324", "gtsr:remaster_b0d25971c8390324",
                    "gtsr:remaster_b0d25971c8390324" },
                { "gtsr:remaster_b0d25971c8390324", "gtsr:remaster_b0d25971c8390324", "gtsr:remaster_b0d25971c8390324",
                    "gtsr:remaster_b0d25971c8390324", "gtsr:remaster_b0d25971c8390324",
                    "gtsr:remaster_b0d25971c8390324" },
                { "gtsr:remaster_b0d25971c8390324", "gtsr:remaster_b0d25971c8390324", "gtsr:remaster_b0d25971c8390324",
                    "gtsr:remaster_b0d25971c8390324", "gtsr:remaster_b0d25971c8390324",
                    "gtsr:remaster_b0d25971c8390324" },
                { "gtsr:remaster_b0d25971c8390324", "gtsr:remaster_b0d25971c8390324", "gtsr:remaster_b0d25971c8390324",
                    "gtsr:remaster_b0d25971c8390324", "gtsr:remaster_b0d25971c8390324",
                    "gtsr:remaster_b0d25971c8390324" } });
    }

    private static RemasterBlock block86() {
        return new RemasterBlock(
            "gtsr:draft6_pollution_crust",
            "decoration",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0, 0, 0.125, 0.1875, 0.1875, 0.875 }, { 0.0625, 0.125, 0.1875, 0.25, 0.4375, 0.4375 },
                    { 0.125, 0.375, 0.125, 0.25, 0.625, 0.3125 }, { 0.0625, 0.1875, 0.5, 0.3125, 0.375, 0.6875 },
                    { 0.25, 0.3125, 0.5625, 0.4375, 0.5, 0.75 }, { 0.0625, 0.125, 0.75, 0.25, 0.3125, 0.9375 },
                    { 0.1875, 0.25, 0.8125, 0.375, 0.5, 1 } },
                { { 0.8125, 0, 0.125, 1, 0.1875, 0.875 }, { 0.75, 0.125, 0.5625, 0.9375, 0.4375, 0.8125 },
                    { 0.75, 0.375, 0.6875, 0.875, 0.625, 0.875 }, { 0.6875, 0.1875, 0.3125, 0.9375, 0.375, 0.5 },
                    { 0.5625, 0.3125, 0.25, 0.75, 0.5, 0.4375 }, { 0.75, 0.125, 0.0625, 0.9375, 0.3125, 0.25 },
                    { 0.625, 0.25, 0, 0.8125, 0.5, 0.1875 } },
                { { 0.125, 0, 0, 0.875, 0.1875, 0.1875 }, { 0.5625, 0.125, 0.0625, 0.8125, 0.4375, 0.25 },
                    { 0.6875, 0.375, 0.125, 0.875, 0.625, 0.25 }, { 0.3125, 0.1875, 0.0625, 0.5, 0.375, 0.3125 },
                    { 0.25, 0.3125, 0.25, 0.4375, 0.5, 0.4375 }, { 0.0625, 0.125, 0.0625, 0.25, 0.3125, 0.25 },
                    { 0, 0.25, 0.1875, 0.1875, 0.5, 0.375 } },
                { { 0.125, 0, 0.8125, 0.875, 0.1875, 1 }, { 0.1875, 0.125, 0.75, 0.4375, 0.4375, 0.9375 },
                    { 0.125, 0.375, 0.75, 0.3125, 0.625, 0.875 }, { 0.5, 0.1875, 0.6875, 0.6875, 0.375, 0.9375 },
                    { 0.5625, 0.3125, 0.5625, 0.75, 0.5, 0.75 }, { 0.75, 0.125, 0.75, 0.9375, 0.3125, 0.9375 },
                    { 0.8125, 0.25, 0.625, 1, 0.5, 0.8125 } } },
            new String[][] {
                { "gtsr:remaster_ee0614c03ec14b3e", "gtsr:remaster_ee0614c03ec14b3e", "gtsr:remaster_ee0614c03ec14b3e",
                    "gtsr:remaster_ee0614c03ec14b3e", "gtsr:remaster_ee0614c03ec14b3e",
                    "gtsr:remaster_ee0614c03ec14b3e" },
                { "gtsr:remaster_ee0614c03ec14b3e", "gtsr:remaster_ee0614c03ec14b3e", "gtsr:remaster_ee0614c03ec14b3e",
                    "gtsr:remaster_ee0614c03ec14b3e", "gtsr:remaster_ee0614c03ec14b3e",
                    "gtsr:remaster_ee0614c03ec14b3e" },
                { "gtsr:remaster_ee0614c03ec14b3e", "gtsr:remaster_ee0614c03ec14b3e", "gtsr:remaster_ee0614c03ec14b3e",
                    "gtsr:remaster_ee0614c03ec14b3e", "gtsr:remaster_ee0614c03ec14b3e",
                    "gtsr:remaster_ee0614c03ec14b3e" },
                { "gtsr:remaster_ee0614c03ec14b3e", "gtsr:remaster_ee0614c03ec14b3e", "gtsr:remaster_ee0614c03ec14b3e",
                    "gtsr:remaster_ee0614c03ec14b3e", "gtsr:remaster_ee0614c03ec14b3e",
                    "gtsr:remaster_ee0614c03ec14b3e" } });
    }

    private static RemasterBlock block87() {
        return new RemasterBlock(
            "gtsr:draft6_sluice_actuator",
            "operation",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0.0625, 0, 0.125, 0.9375, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5, 0.6875 },
                    { 0.4375, 0.5, 0.4375, 0.5625, 1, 0.5625 }, { 0.625, 0.375, 0.4375, 1, 0.5, 0.5625 },
                    { 0.875, 0.5, 0.4375, 1, 0.75, 0.5625 } },
                { { 0.0625, 0, 0.125, 0.9375, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5, 0.6875 },
                    { 0.4375, 0.5, 0.4375, 0.5625, 1, 0.5625 }, { 0, 0.375, 0.4375, 0.375, 0.5, 0.5625 },
                    { 0, 0.5, 0.4375, 0.125, 0.75, 0.5625 } },
                { { 0.125, 0, 0.0625, 0.875, 0.1875, 0.9375 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5, 0.6875 },
                    { 0.4375, 0.5, 0.4375, 0.5625, 1, 0.5625 }, { 0.4375, 0.375, 0.625, 0.5625, 0.5, 1 },
                    { 0.4375, 0.5, 0.875, 0.5625, 0.75, 1 } },
                { { 0.125, 0, 0.0625, 0.875, 0.1875, 0.9375 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5, 0.6875 },
                    { 0.4375, 0.5, 0.4375, 0.5625, 1, 0.5625 }, { 0.4375, 0.375, 0, 0.5625, 0.5, 0.375 },
                    { 0.4375, 0.5, 0, 0.5625, 0.75, 0.125 } } },
            new String[][] {
                { "gtsr:remaster_fc68c0bf302e516e", "gtsr:remaster_fc68c0bf302e516e", "gtsr:remaster_fc68c0bf302e516e",
                    "gtsr:remaster_fc68c0bf302e516e", "gtsr:remaster_fc68c0bf302e516e",
                    "gtsr:remaster_fc68c0bf302e516e" },
                { "gtsr:remaster_fc68c0bf302e516e", "gtsr:remaster_fc68c0bf302e516e", "gtsr:remaster_fc68c0bf302e516e",
                    "gtsr:remaster_fc68c0bf302e516e", "gtsr:remaster_fc68c0bf302e516e",
                    "gtsr:remaster_fc68c0bf302e516e" },
                { "gtsr:remaster_fc68c0bf302e516e", "gtsr:remaster_fc68c0bf302e516e", "gtsr:remaster_fc68c0bf302e516e",
                    "gtsr:remaster_fc68c0bf302e516e", "gtsr:remaster_fc68c0bf302e516e",
                    "gtsr:remaster_fc68c0bf302e516e" },
                { "gtsr:remaster_fc68c0bf302e516e", "gtsr:remaster_fc68c0bf302e516e", "gtsr:remaster_fc68c0bf302e516e",
                    "gtsr:remaster_fc68c0bf302e516e", "gtsr:remaster_fc68c0bf302e516e",
                    "gtsr:remaster_fc68c0bf302e516e" } });
    }

    private static RemasterBlock block88() {
        return new RemasterBlock(
            "gtsr:draft6_lift_winching",
            "operation",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0.0625, 0, 0.0625, 0.9375, 0.125, 0.9375 }, { 0.125, 0.125, 0.25, 0.25, 0.8125, 0.75 },
                    { 0.75, 0.125, 0.25, 0.875, 0.8125, 0.75 }, { 0.25, 0.375, 0.3125, 0.75, 0.75, 0.6875 },
                    { 0.875, 0.5, 0.4375, 1, 0.625, 0.5625 }, { 0.875, 0.625, 0.4375, 1, 0.875, 0.5625 } },
                { { 0.0625, 0, 0.0625, 0.9375, 0.125, 0.9375 }, { 0.75, 0.125, 0.25, 0.875, 0.8125, 0.75 },
                    { 0.125, 0.125, 0.25, 0.25, 0.8125, 0.75 }, { 0.25, 0.375, 0.3125, 0.75, 0.75, 0.6875 },
                    { 0, 0.5, 0.4375, 0.125, 0.625, 0.5625 }, { 0, 0.625, 0.4375, 0.125, 0.875, 0.5625 } },
                { { 0.0625, 0, 0.0625, 0.9375, 0.125, 0.9375 }, { 0.25, 0.125, 0.125, 0.75, 0.8125, 0.25 },
                    { 0.25, 0.125, 0.75, 0.75, 0.8125, 0.875 }, { 0.3125, 0.375, 0.25, 0.6875, 0.75, 0.75 },
                    { 0.4375, 0.5, 0.875, 0.5625, 0.625, 1 }, { 0.4375, 0.625, 0.875, 0.5625, 0.875, 1 } },
                { { 0.0625, 0, 0.0625, 0.9375, 0.125, 0.9375 }, { 0.25, 0.125, 0.75, 0.75, 0.8125, 0.875 },
                    { 0.25, 0.125, 0.125, 0.75, 0.8125, 0.25 }, { 0.3125, 0.375, 0.25, 0.6875, 0.75, 0.75 },
                    { 0.4375, 0.5, 0, 0.5625, 0.625, 0.125 }, { 0.4375, 0.625, 0, 0.5625, 0.875, 0.125 } } },
            new String[][] {
                { "gtsr:remaster_4b55df6695aba50e", "gtsr:remaster_4b55df6695aba50e", "gtsr:remaster_4b55df6695aba50e",
                    "gtsr:remaster_4b55df6695aba50e", "gtsr:remaster_4b55df6695aba50e",
                    "gtsr:remaster_4b55df6695aba50e" },
                { "gtsr:remaster_4b55df6695aba50e", "gtsr:remaster_4b55df6695aba50e", "gtsr:remaster_4b55df6695aba50e",
                    "gtsr:remaster_4b55df6695aba50e", "gtsr:remaster_4b55df6695aba50e",
                    "gtsr:remaster_4b55df6695aba50e" },
                { "gtsr:remaster_4b55df6695aba50e", "gtsr:remaster_4b55df6695aba50e", "gtsr:remaster_4b55df6695aba50e",
                    "gtsr:remaster_4b55df6695aba50e", "gtsr:remaster_4b55df6695aba50e",
                    "gtsr:remaster_4b55df6695aba50e" },
                { "gtsr:remaster_4b55df6695aba50e", "gtsr:remaster_4b55df6695aba50e", "gtsr:remaster_4b55df6695aba50e",
                    "gtsr:remaster_4b55df6695aba50e", "gtsr:remaster_4b55df6695aba50e",
                    "gtsr:remaster_4b55df6695aba50e" } });
    }

    private static RemasterBlock block89() {
        return new RemasterBlock(
            "gtsr:draft6_iris_controller",
            "operation",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0.1875, 0, 0.1875, 0.8125, 0.125, 0.8125 }, { 0.375, 0.125, 0.375, 0.625, 0.5625, 0.625 },
                    { 0.1875, 0.5625, 0.1875, 0.8125, 0.875, 0.8125 }, { 0.75, 0.625, 0.3125, 0.875, 0.75, 0.6875 },
                    { 0.8125, 0.75, 0.3125, 0.9375, 0.875, 0.4375 } },
                { { 0.1875, 0, 0.1875, 0.8125, 0.125, 0.8125 }, { 0.375, 0.125, 0.375, 0.625, 0.5625, 0.625 },
                    { 0.1875, 0.5625, 0.1875, 0.8125, 0.875, 0.8125 }, { 0.125, 0.625, 0.3125, 0.25, 0.75, 0.6875 },
                    { 0.0625, 0.75, 0.5625, 0.1875, 0.875, 0.6875 } },
                { { 0.1875, 0, 0.1875, 0.8125, 0.125, 0.8125 }, { 0.375, 0.125, 0.375, 0.625, 0.5625, 0.625 },
                    { 0.1875, 0.5625, 0.1875, 0.8125, 0.875, 0.8125 }, { 0.3125, 0.625, 0.75, 0.6875, 0.75, 0.875 },
                    { 0.5625, 0.75, 0.8125, 0.6875, 0.875, 0.9375 } },
                { { 0.1875, 0, 0.1875, 0.8125, 0.125, 0.8125 }, { 0.375, 0.125, 0.375, 0.625, 0.5625, 0.625 },
                    { 0.1875, 0.5625, 0.1875, 0.8125, 0.875, 0.8125 }, { 0.3125, 0.625, 0.125, 0.6875, 0.75, 0.25 },
                    { 0.3125, 0.75, 0.0625, 0.4375, 0.875, 0.1875 } } },
            new String[][] {
                { "gtsr:remaster_74f384da8135229c", "gtsr:remaster_74f384da8135229c", "gtsr:remaster_74f384da8135229c",
                    "gtsr:remaster_74f384da8135229c", "gtsr:remaster_74f384da8135229c",
                    "gtsr:remaster_74f384da8135229c" },
                { "gtsr:remaster_74f384da8135229c", "gtsr:remaster_74f384da8135229c", "gtsr:remaster_74f384da8135229c",
                    "gtsr:remaster_74f384da8135229c", "gtsr:remaster_74f384da8135229c",
                    "gtsr:remaster_74f384da8135229c" },
                { "gtsr:remaster_74f384da8135229c", "gtsr:remaster_74f384da8135229c", "gtsr:remaster_74f384da8135229c",
                    "gtsr:remaster_74f384da8135229c", "gtsr:remaster_74f384da8135229c",
                    "gtsr:remaster_74f384da8135229c" },
                { "gtsr:remaster_74f384da8135229c", "gtsr:remaster_74f384da8135229c", "gtsr:remaster_74f384da8135229c",
                    "gtsr:remaster_74f384da8135229c", "gtsr:remaster_74f384da8135229c",
                    "gtsr:remaster_74f384da8135229c" } });
    }

    private static RemasterBlock block90() {
        return new RemasterBlock(
            "gtsr:draft7_seal_chest",
            "chest",
            false,
            false,
            "chest",
            new double[][][] { { { 0.0625, 0, 0.0625, 0.9375, 0.875, 0.9375 } },
                { { 0.0625, 0, 0.0625, 0.9375, 0.875, 0.9375 } }, { { 0.0625, 0, 0.0625, 0.9375, 0.875, 0.9375 } },
                { { 0.0625, 0, 0.0625, 0.9375, 0.875, 0.9375 } }, { { 0.0625, 0, 0.0625, 0.9375, 0.875, 0.9375 } },
                { { 0.0625, 0, 0.0625, 0.9375, 0.875, 0.9375 } }, { { 0.0625, 0, 0.0625, 0.9375, 0.875, 0.9375 } },
                { { 0.0625, 0, 0.0625, 0.9375, 0.875, 0.9375 } } },
            new String[][] {
                { "gtsr:remaster_4225e06cb74b90d9", "gtsr:remaster_4225e06cb74b90d9", "gtsr:remaster_4225e06cb74b90d9",
                    "gtsr:remaster_4225e06cb74b90d9", "gtsr:remaster_4225e06cb74b90d9",
                    "gtsr:remaster_4225e06cb74b90d9" },
                { "gtsr:remaster_4225e06cb74b90d9", "gtsr:remaster_4225e06cb74b90d9", "gtsr:remaster_4225e06cb74b90d9",
                    "gtsr:remaster_4225e06cb74b90d9", "gtsr:remaster_4225e06cb74b90d9",
                    "gtsr:remaster_4225e06cb74b90d9" },
                { "gtsr:remaster_4225e06cb74b90d9", "gtsr:remaster_4225e06cb74b90d9", "gtsr:remaster_4225e06cb74b90d9",
                    "gtsr:remaster_4225e06cb74b90d9", "gtsr:remaster_4225e06cb74b90d9",
                    "gtsr:remaster_4225e06cb74b90d9" },
                { "gtsr:remaster_4225e06cb74b90d9", "gtsr:remaster_4225e06cb74b90d9", "gtsr:remaster_4225e06cb74b90d9",
                    "gtsr:remaster_4225e06cb74b90d9", "gtsr:remaster_4225e06cb74b90d9",
                    "gtsr:remaster_4225e06cb74b90d9" },
                { "gtsr:remaster_7cef17c0c6ca5cee", "gtsr:remaster_7cef17c0c6ca5cee", "gtsr:remaster_7cef17c0c6ca5cee",
                    "gtsr:remaster_7cef17c0c6ca5cee", "gtsr:remaster_7cef17c0c6ca5cee",
                    "gtsr:remaster_7cef17c0c6ca5cee" },
                { "gtsr:remaster_7cef17c0c6ca5cee", "gtsr:remaster_7cef17c0c6ca5cee", "gtsr:remaster_7cef17c0c6ca5cee",
                    "gtsr:remaster_7cef17c0c6ca5cee", "gtsr:remaster_7cef17c0c6ca5cee",
                    "gtsr:remaster_7cef17c0c6ca5cee" },
                { "gtsr:remaster_7cef17c0c6ca5cee", "gtsr:remaster_7cef17c0c6ca5cee", "gtsr:remaster_7cef17c0c6ca5cee",
                    "gtsr:remaster_7cef17c0c6ca5cee", "gtsr:remaster_7cef17c0c6ca5cee",
                    "gtsr:remaster_7cef17c0c6ca5cee" },
                { "gtsr:remaster_7cef17c0c6ca5cee", "gtsr:remaster_7cef17c0c6ca5cee", "gtsr:remaster_7cef17c0c6ca5cee",
                    "gtsr:remaster_7cef17c0c6ca5cee", "gtsr:remaster_7cef17c0c6ca5cee",
                    "gtsr:remaster_7cef17c0c6ca5cee" } });
    }

    private static RemasterBlock block91() {
        return new RemasterBlock(
            "gtsr:draft7_index_frame",
            "operation",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } },
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } },
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } },
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } },
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } },
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } },
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } },
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } } },
            new String[][] {
                { "gtsr:remaster_40dd36b034e3e8de", "gtsr:remaster_40dd36b034e3e8de", "gtsr:remaster_40dd36b034e3e8de",
                    "gtsr:remaster_40dd36b034e3e8de", "gtsr:remaster_40dd36b034e3e8de",
                    "gtsr:remaster_40dd36b034e3e8de" },
                { "gtsr:remaster_40dd36b034e3e8de", "gtsr:remaster_40dd36b034e3e8de", "gtsr:remaster_40dd36b034e3e8de",
                    "gtsr:remaster_40dd36b034e3e8de", "gtsr:remaster_40dd36b034e3e8de",
                    "gtsr:remaster_40dd36b034e3e8de" },
                { "gtsr:remaster_40dd36b034e3e8de", "gtsr:remaster_40dd36b034e3e8de", "gtsr:remaster_40dd36b034e3e8de",
                    "gtsr:remaster_40dd36b034e3e8de", "gtsr:remaster_40dd36b034e3e8de",
                    "gtsr:remaster_40dd36b034e3e8de" },
                { "gtsr:remaster_40dd36b034e3e8de", "gtsr:remaster_40dd36b034e3e8de", "gtsr:remaster_40dd36b034e3e8de",
                    "gtsr:remaster_40dd36b034e3e8de", "gtsr:remaster_40dd36b034e3e8de",
                    "gtsr:remaster_40dd36b034e3e8de" },
                { "gtsr:remaster_1e31dcd80a4e5028", "gtsr:remaster_1e31dcd80a4e5028", "gtsr:remaster_1e31dcd80a4e5028",
                    "gtsr:remaster_1e31dcd80a4e5028", "gtsr:remaster_1e31dcd80a4e5028",
                    "gtsr:remaster_1e31dcd80a4e5028" },
                { "gtsr:remaster_1e31dcd80a4e5028", "gtsr:remaster_1e31dcd80a4e5028", "gtsr:remaster_1e31dcd80a4e5028",
                    "gtsr:remaster_1e31dcd80a4e5028", "gtsr:remaster_1e31dcd80a4e5028",
                    "gtsr:remaster_1e31dcd80a4e5028" },
                { "gtsr:remaster_1e31dcd80a4e5028", "gtsr:remaster_1e31dcd80a4e5028", "gtsr:remaster_1e31dcd80a4e5028",
                    "gtsr:remaster_1e31dcd80a4e5028", "gtsr:remaster_1e31dcd80a4e5028",
                    "gtsr:remaster_1e31dcd80a4e5028" },
                { "gtsr:remaster_1e31dcd80a4e5028", "gtsr:remaster_1e31dcd80a4e5028", "gtsr:remaster_1e31dcd80a4e5028",
                    "gtsr:remaster_1e31dcd80a4e5028", "gtsr:remaster_1e31dcd80a4e5028",
                    "gtsr:remaster_1e31dcd80a4e5028" } });
    }

    private static RemasterBlock block92() {
        return new RemasterBlock(
            "gtsr:draft7_flow_window",
            "operation",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } },
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } },
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } },
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } },
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } },
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } },
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } },
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } } },
            new String[][] {
                { "gtsr:remaster_6bc6c108ac24ba1b", "gtsr:remaster_6bc6c108ac24ba1b", "gtsr:remaster_6bc6c108ac24ba1b",
                    "gtsr:remaster_6bc6c108ac24ba1b", "gtsr:remaster_6bc6c108ac24ba1b",
                    "gtsr:remaster_6bc6c108ac24ba1b" },
                { "gtsr:remaster_6bc6c108ac24ba1b", "gtsr:remaster_6bc6c108ac24ba1b", "gtsr:remaster_6bc6c108ac24ba1b",
                    "gtsr:remaster_6bc6c108ac24ba1b", "gtsr:remaster_6bc6c108ac24ba1b",
                    "gtsr:remaster_6bc6c108ac24ba1b" },
                { "gtsr:remaster_6bc6c108ac24ba1b", "gtsr:remaster_6bc6c108ac24ba1b", "gtsr:remaster_6bc6c108ac24ba1b",
                    "gtsr:remaster_6bc6c108ac24ba1b", "gtsr:remaster_6bc6c108ac24ba1b",
                    "gtsr:remaster_6bc6c108ac24ba1b" },
                { "gtsr:remaster_6bc6c108ac24ba1b", "gtsr:remaster_6bc6c108ac24ba1b", "gtsr:remaster_6bc6c108ac24ba1b",
                    "gtsr:remaster_6bc6c108ac24ba1b", "gtsr:remaster_6bc6c108ac24ba1b",
                    "gtsr:remaster_6bc6c108ac24ba1b" },
                { "gtsr:remaster_151a0926a548331e", "gtsr:remaster_151a0926a548331e", "gtsr:remaster_151a0926a548331e",
                    "gtsr:remaster_151a0926a548331e", "gtsr:remaster_151a0926a548331e",
                    "gtsr:remaster_151a0926a548331e" },
                { "gtsr:remaster_151a0926a548331e", "gtsr:remaster_151a0926a548331e", "gtsr:remaster_151a0926a548331e",
                    "gtsr:remaster_151a0926a548331e", "gtsr:remaster_151a0926a548331e",
                    "gtsr:remaster_151a0926a548331e" },
                { "gtsr:remaster_151a0926a548331e", "gtsr:remaster_151a0926a548331e", "gtsr:remaster_151a0926a548331e",
                    "gtsr:remaster_151a0926a548331e", "gtsr:remaster_151a0926a548331e",
                    "gtsr:remaster_151a0926a548331e" },
                { "gtsr:remaster_151a0926a548331e", "gtsr:remaster_151a0926a548331e", "gtsr:remaster_151a0926a548331e",
                    "gtsr:remaster_151a0926a548331e", "gtsr:remaster_151a0926a548331e",
                    "gtsr:remaster_151a0926a548331e" } });
    }

    private static RemasterBlock block93() {
        return new RemasterBlock(
            "gtsr:draft7_phase_lens",
            "operation",
            false,
            false,
            "boxes",
            new double[][][] {
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } },
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } },
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } },
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } },
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } },
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } },
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } },
                { { 0.125, 0, 0.125, 0.875, 0.1875, 0.875 }, { 0.3125, 0.1875, 0.3125, 0.6875, 0.5625, 0.6875 },
                    { 0.0625, 0.5625, 0.125, 0.9375, 0.9375, 0.875 } } },
            new String[][] {
                { "gtsr:remaster_cd9dc9b8c7da955a", "gtsr:remaster_cd9dc9b8c7da955a", "gtsr:remaster_cd9dc9b8c7da955a",
                    "gtsr:remaster_cd9dc9b8c7da955a", "gtsr:remaster_cd9dc9b8c7da955a",
                    "gtsr:remaster_cd9dc9b8c7da955a" },
                { "gtsr:remaster_cd9dc9b8c7da955a", "gtsr:remaster_cd9dc9b8c7da955a", "gtsr:remaster_cd9dc9b8c7da955a",
                    "gtsr:remaster_cd9dc9b8c7da955a", "gtsr:remaster_cd9dc9b8c7da955a",
                    "gtsr:remaster_cd9dc9b8c7da955a" },
                { "gtsr:remaster_cd9dc9b8c7da955a", "gtsr:remaster_cd9dc9b8c7da955a", "gtsr:remaster_cd9dc9b8c7da955a",
                    "gtsr:remaster_cd9dc9b8c7da955a", "gtsr:remaster_cd9dc9b8c7da955a",
                    "gtsr:remaster_cd9dc9b8c7da955a" },
                { "gtsr:remaster_cd9dc9b8c7da955a", "gtsr:remaster_cd9dc9b8c7da955a", "gtsr:remaster_cd9dc9b8c7da955a",
                    "gtsr:remaster_cd9dc9b8c7da955a", "gtsr:remaster_cd9dc9b8c7da955a",
                    "gtsr:remaster_cd9dc9b8c7da955a" },
                { "gtsr:remaster_18b6245112e0f06a", "gtsr:remaster_18b6245112e0f06a", "gtsr:remaster_18b6245112e0f06a",
                    "gtsr:remaster_18b6245112e0f06a", "gtsr:remaster_18b6245112e0f06a",
                    "gtsr:remaster_18b6245112e0f06a" },
                { "gtsr:remaster_18b6245112e0f06a", "gtsr:remaster_18b6245112e0f06a", "gtsr:remaster_18b6245112e0f06a",
                    "gtsr:remaster_18b6245112e0f06a", "gtsr:remaster_18b6245112e0f06a",
                    "gtsr:remaster_18b6245112e0f06a" },
                { "gtsr:remaster_18b6245112e0f06a", "gtsr:remaster_18b6245112e0f06a", "gtsr:remaster_18b6245112e0f06a",
                    "gtsr:remaster_18b6245112e0f06a", "gtsr:remaster_18b6245112e0f06a",
                    "gtsr:remaster_18b6245112e0f06a" },
                { "gtsr:remaster_18b6245112e0f06a", "gtsr:remaster_18b6245112e0f06a", "gtsr:remaster_18b6245112e0f06a",
                    "gtsr:remaster_18b6245112e0f06a", "gtsr:remaster_18b6245112e0f06a",
                    "gtsr:remaster_18b6245112e0f06a" } });
    }
}
