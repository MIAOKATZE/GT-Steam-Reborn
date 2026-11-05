import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.*;
import com.miaokatze.gtsr.common.api.enums.GTSRHatchElement;

import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.*;
import gregtech.api.metatileentity.implementations.*;
import gregtech.common.misc.GTStructureChannels;

/** Real production construction and GT/SL placement filters; sparse world and empty item source, no game. */
public final class MotionHatchPlacementRegression {
    static sun.misc.Unsafe unsafe;
    static int nextId = 30000;
    static boolean green;

    public static void main(String[] args) throws Exception {
        System.setProperty("motion.audit.green", args[0]);
        Issue37RegistrationRegression.launch("MotionHatchPlacementRegression", "5.09.54.265");
    }

    static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(target, value); return; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static BaseMetaTileEntity attach(MetaTileEntity meta, SparseWorld world, int x, int y, int z) throws Exception {
        BaseMetaTileEntity tile = new BaseMetaTileEntity();
        tile.setWorldObj(world); tile.xCoord = x; tile.yCoord = y; tile.zCoord = z;
        set(meta, "mBaseMetaTileEntity", tile); tile.setMetaTileEntity(meta);
        set(tile, "mID", (short) nextId); GregTechAPI.METATILEENTITIES[nextId++] = meta;
        world.tiles.put(key(x,y,z), tile);
        return tile;
    }

    static String key(int x, int y, int z) { return x + ":" + y + ":" + z; }

    static final class SparseWorld extends World {
        Map<String, TileEntity> tiles;
        Map<String, ItemStack> blocks;
        private SparseWorld() { super(null, "unused", (WorldProvider) null, null, null); }
        @Override public TileEntity getTileEntity(int x, int y, int z) { return tiles.get(key(x,y,z)); }
        @Override public Block getBlock(int x, int y, int z) {
            if (tiles.containsKey(key(x,y,z))) return GregTechAPI.sBlockMachines;
            ItemStack stack = blocks.get(key(x,y,z));
            return stack == null ? net.minecraft.init.Blocks.air : Block.getBlockFromItem(stack.getItem());
        }
        @Override public int getBlockMetadata(int x, int y, int z) {
            ItemStack stack = blocks.get(key(x,y,z)); return stack == null ? 0 : stack.getItemDamage();
        }
        @Override public boolean blockExists(int x, int y, int z) { return true; }
        @Override public void markBlockForUpdate(int x, int y, int z) {}
        @Override public void markTileEntityChunkModified(int x, int y, int z, TileEntity tile) {}
        @Override public long getTotalWorldTime() { return 1; }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected int func_152379_p() { return 0; }
        @Override public Entity getEntityByID(int id) { return null; }
    }

    static final class Query extends RuntimeException {
        final boolean inputAccepted;
        Query(boolean inputAccepted) { this.inputAccepted = inputAccepted; }
    }

    @SuppressWarnings("unchecked")
    static void scenario(String name, int tier, int installed) throws Exception {
        SparseWorld world = (SparseWorld) unsafe.allocateInstance(SparseWorld.class);
        world.tiles = new HashMap<>(); world.blocks = new HashMap<>();
        world.playerEntities = new ArrayList<>();
        var machine = (MTEEnhancedMultiBlockBase<?>) unsafe.allocateInstance(
            Class.forName("com.miaokatze.gtsr.common.machine." + name));
        for (Class<?> type = machine.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                if (field.getType() == ArrayList.class || field.getType() == List.class) field.set(machine, new ArrayList<>());
                if (field.getType() == HashSet.class || field.getType() == Set.class) field.set(machine, new HashSet<>());
            }
        }
        set(machine, "mInventory", new ItemStack[2]);
        set(machine, "mCoils", new it.unimi.dsi.fastutil.longs.LongArrayList());
        set(machine, "mExtendedFacing", ExtendedFacing.DEFAULT);
        attach(machine, world, 0, 64, 0);
        ItemStack trigger = new ItemStack(net.minecraft.init.Items.stick, tier);
        com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor.setChannelData(trigger, GTStructureChannels.HATCH.get(), 1);
        var definition = (StructureDefinition<Object>) (IStructureDefinition<?>) machine.getStructureDefinition();
        var shell = definition.getElements().get('B');
        List<Object[]> positions = new ArrayList<>();
        int[] offsets = name.equals("MTEAirCompressor") ? new int[] {1,2,0} : new int[] {4,3,0};
        definition.iterate("motion", world, ExtendedFacing.DEFAULT, 0,64,0, offsets[0],offsets[1],0,
            (e,w,x,y,z,a,b,c) -> { positions.add(new Object[] {e,x,y,z}); return true; });
        int hatches = 0;
        for (Object[] position : positions) {
            var element = (IStructureElement<Object>) position[0];
            int x = (int) position[1], y = (int) position[2], z = (int) position[3];
            if (element == shell) {
                if (hatches == installed) break;
                var input = new MTEHatchInput("motion_input_" + nextId, 1, new String[0], new gregtech.api.interfaces.ITexture[0][][]);
                attach(input, world, x,y,z); hatches++;
            } else {
                var candidates = element.getBlocksToPlace(machine, world,x,y,z,trigger,null);
                if (candidates != null && candidates.getStacks() != null)
                    for (ItemStack stack : candidates.getStacks()) { world.blocks.put(key(x,y,z),stack); break; }
            }
        }
        require(hatches == installed, "not enough shell slots");
        machine.clearHatches();
        machine.checkMachine(machine.getBaseMetaTileEntity(), null, new ArrayList<>());
        long before = GTSRHatchElement.PressureSteamInputHatch.count(machine);
        var candidate = new MTEHatchInput("motion_candidate_" + nextId, 1, new String[0], new gregtech.api.interfaces.ITexture[0][][]);
        attach(candidate, world, 999,64,999);
        ItemStack candidateStack = candidate.getStackForm(1);
        EntityPlayerMP actor = (EntityPlayerMP) unsafe.allocateInstance(EntityPlayerMP.class);
        actor.worldObj = world;
        IItemSource source = (predicate, simulate, count) -> { throw new Query(predicate.test(candidateStack)); };
        boolean accepted;
        try {
            ((com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable) machine)
                .survivalConstruct(trigger, 1, ISurvivalBuildEnvironment.create(source, actor));
            throw new AssertionError("native source query did not execute");
        } catch (Query query) { accepted = query.inputAccepted; }
        require(accepted == (!green && name.equals("MTEAtmosphericCentrifuge")), name + " unexpected extra input acceptance=" + accepted);
        require(before == (name.equals("MTEAtmosphericCentrifuge") ? 0 : installed), "unexpected failed-shape counter");
        System.out.println("CASE " + name + " tier=" + tier + " installed=" + installed
            + " countAfterFailedCheck=" + before + " extraInputAccepted=" + accepted);
    }

    public static void run(String version) throws Exception {
        green = Boolean.getBoolean("motion.audit.green");
        unsafe = Issue37Runtime.bootstrap(version);
        set(gregtech.api.enums.Materials.Bronze, "mMetaItemSubID", (short) 310);
        set(gregtech.api.enums.Materials.Steel, "mMetaItemSubID", (short) 305);
        GregTechAPI.sBlockFrames = net.minecraft.init.Blocks.stone;
        for (Field field : GregTechAPI.class.getFields()) {
            if (!field.getName().startsWith("sBlockCasings")) continue;
            Object block = field.get(null);
            if (!(block instanceof Block casing)) continue;
            Method rawBlock = cpw.mods.fml.common.registry.GameData.getBlockRegistry().getClass()
                .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
            rawBlock.setAccessible(true);
            rawBlock.invoke(cpw.mods.fml.common.registry.GameData.getBlockRegistry(), nextId, "fixture:" + field.getName(), casing);
            Method raw = cpw.mods.fml.common.registry.GameData.getItemRegistry().getClass()
                .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
            raw.setAccessible(true);
            raw.invoke(cpw.mods.fml.common.registry.GameData.getItemRegistry(), nextId++, "fixture:" + field.getName(), new ItemBlock(casing));
        }
        GTSRHatchElement.registerMachineHatchClasses(
            com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch.class,
            com.miaokatze.gtsr.common.machine.base.MTEPressureSteamCoolingHatch.class,
            com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput.class);
        for (String name : List.of("MTEAtmosphericCentrifuge", "MTEAirCompressor"))
            for (int tier : new int[] {1,2}) for (int installed : new int[] {1,2}) scenario(name,tier,installed);
        System.out.println("RESULT " + (green ? "green" : "red") + " PASS cases=8");
    }
}
