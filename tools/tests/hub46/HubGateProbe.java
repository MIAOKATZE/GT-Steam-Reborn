package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.ProbeSupport.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.entity.Entity;
import net.minecraft.block.Block;

import com.google.gson.GsonBuilder;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.common.machine.*;
import com.miaokatze.gtsr.common.machine.base.*;

import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.HatchElementBuilder;

/** Native C/A structure checks and real admission/candidate predicates; no controller mocks. */
public final class HubGateProbe {
    private static final List<String> failures = new ArrayList<>();
    private static int assertions;
    private static void verify(boolean pass, String name) {
        assertions++;
        System.out.println((pass ? "PASS " : "FAIL ") + name);
        if (!pass) failures.add(name);
    }
    private static Object host(Class<?> type) throws Exception {
        Object host = U.allocateInstance(type);
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && List.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    f.set(host, f.getType().isInterface() ? new ArrayList<>() : f.getType().getDeclaredConstructor().newInstance());
                }
            }
        }
        set(host, "mInventory", new ItemStack[2]);
        return host;
    }
    private static Method method(Class<?> type, String name, Class<?>... params) throws Exception {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try { Method m = c.getDeclaredMethod(name, params); m.setAccessible(true); return m; }
            catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(name);
    }
    private static long capacity(Object host) throws Exception {
        return (long) method(host.getClass(), "getTotalCapacity").invoke(host);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void family(Class<?> type, String prefix, Class<?> input, Class<?> output,
                               Class<?> singularityInput, Class<?> singularityOutput,
                               String unitField, long expectedCapacity) throws Exception {
        Object host = host(type);
        StructureDefinition definition = (StructureDefinition) method(type, "getStructureDefinition").invoke(host);
        IStructureElement c = (IStructureElement) definition.getElements().get('C');
        IStructureElement a = (IStructureElement) definition.getElements().get('A');
        Class<?> hatchEnum = Class.forName(type.getName() + "$" + prefix + "HubHatchElement");
        IHatchElement[] elements = (IHatchElement[]) hatchEnum.getEnumConstants();
        HatchElementBuilder builder = HatchElementBuilder.builder().atLeast(elements);
        // Register real MTE item prototypes to exercise the exact builder filter used by construction.
        gregtech.common.blocks.ItemMachines machineItem = new gregtech.common.blocks.ItemMachines(net.minecraft.init.Blocks.stone);
        int id = type == MTESteamHubArray.class ? 29000 : 29020;
        Class<?>[] prohibited = {MTESingularitySteamCompartment.class, MTESingularitySteamOutputCompartment.class,
                                MTESingularityFluidInputCompartment.class, MTESingularityFluidOutputCompartment.class};
        set(host, unitField, 1);
        verify(capacity(host) == expectedCapacity, prefix + " dedicated storage unit baseline capacity");
        long before = capacity(host);
        for (Class<?> hatchType : prohibited) {
            IMetaTileEntity hatch = (IMetaTileEntity) U.allocateInstance(hatchType);
            IGregTechTileEntity tile = attach((MetaTileEntity) hatch);
            TileWorld world = (TileWorld) U.allocateInstance(TileWorld.class);
            world.tile = (TileEntity) tile;
            verify(!c.check(host, world, 0, 0, 0), prefix + " structure C rejects " + hatchType.getSimpleName());
            verify(!a.check(host, world, 0, 0, 0), prefix + " storage A rejects " + hatchType.getSimpleName());
            for (String direction : new String[]{"Input", "Output"}) {
                boolean admitted = (boolean) method(type, "add" + prefix + direction + "ToMachineList",
                    IGregTechTileEntity.class, int.class).invoke(host, tile, 0);
                verify(!admitted, prefix + " direct " + direction + " adder rejects " + hatchType.getSimpleName());
            }
            gregtech.api.GregTechAPI.METATILEENTITIES[id] = hatch;
            ItemStack item = new ItemStack(machineItem, 1, id++);
            set(item, "itemDamage", id - 1);
            verify(gregtech.common.blocks.ItemMachines.getMetaTileEntity(item) == hatch,
                prefix + " construction item resolves " + hatchType.getSimpleName());
            verify(!((java.util.function.Predicate<ItemStack>) builder.getHatchItemFilter().apply(host(type), null)).test(item),
                prefix + " construction filter rejects " + hatchType.getSimpleName());
        }
        verify(capacity(host) == before, prefix + " rejected singularity compartments never increase capacity");
        for (Class<?> hatchType : new Class<?>[]{input, output}) {
            IMetaTileEntity hatch = (IMetaTileEntity) U.allocateInstance(hatchType);
            IGregTechTileEntity tile = attach((MetaTileEntity) hatch);
            TileWorld world = (TileWorld) U.allocateInstance(TileWorld.class); world.tile = (TileEntity) tile;
            gregtech.api.GregTechAPI.METATILEENTITIES[id] = hatch;
            ItemStack item = new ItemStack(machineItem, 1, id++);
            set(item, "itemDamage", id - 1);
            verify(gregtech.common.blocks.ItemMachines.getMetaTileEntity(item) == hatch,
                prefix + " construction item resolves " + hatchType.getSimpleName());
            verify(((java.util.function.Predicate<ItemStack>) builder.getHatchItemFilter().apply(host(type), null)).test(item),
                prefix + " construction filter accepts " + hatchType.getSimpleName());
            verify(c.check(host, world, 0, 0, 0), prefix + " structure C accepts " + hatchType.getSimpleName());
            verify(get(hatch, "mController") == host, prefix + " dedicated hatch controller attached");
        }
        verify(capacity(host) == before, prefix + " dedicated I/O does not add storage capacity");
        MetaTileEntity unit = (MetaTileEntity) U.allocateInstance(MTEHubStorageUnit.class);
        TileWorld unitWorld = (TileWorld) U.allocateInstance(TileWorld.class);
        unitWorld.tile = attach(unit);
        verify(a.check(host, unitWorld, 0, 0, 0), prefix + " storage A still accepts dedicated storage unit");
        verify(capacity(host) == expectedCapacity * 2, prefix + " dedicated storage unit still increases capacity");
        for (Class<?> hatchType : new Class<?>[]{singularityInput, singularityOutput}) {
            IMetaTileEntity hatch = (IMetaTileEntity) U.allocateInstance(hatchType);
            String bindType = (String) method(type, "resolveNodeType", IHubCacheNode.class).invoke(host, hatch);
            verify(bindType != null && bindType.startsWith("singularity_"), prefix + " remote binding type preserved " + hatchType.getSimpleName());
        }
    }
    public static void main(String[] args) throws Exception {
        bootstrap();
        gregtech.api.GregTechAPI.sBlockCasings1 = (gregtech.common.blocks.BlockCasings1)
            U.allocateInstance(gregtech.common.blocks.BlockCasings1.class);
        gregtech.api.GregTechAPI.sBlockCasings2 = (gregtech.common.blocks.BlockCasings2)
            U.allocateInstance(gregtech.common.blocks.BlockCasings2.class);
        family(MTESteamHubArray.class, "Steam", MTESteamHubInputHatch.class, MTESteamHubOutputHatch.class,
            MTESingularitySteamCompartment.class, MTESingularitySteamOutputCompartment.class,
            "mPressureUnitCount", 320_000_000L);
        family(MTEWaterHubArray.class, "Water", MTEWaterHubInputHatch.class, MTEWaterHubOutputHatch.class,
            MTESingularityFluidInputCompartment.class, MTESingularityFluidOutputCompartment.class,
            "mHubUnitCount", 1_280_000L);
        java.util.Map<String, Object> report = new java.util.LinkedHashMap<>();
        report.put("status", failures.isEmpty() ? "PASS" : "FAIL"); report.put("assertions", assertions);
        report.put("failures", failures); report.put("steamSource", source(MTESteamHubArray.class));
        report.put("waterSource", source(MTEWaterHubArray.class));
        Files.writeString(Path.of(args[0]), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        if (!failures.isEmpty()) throw new AssertionError(failures.toString());
    }
    public static final class TileWorld extends World {
        TileEntity tile;
        TileWorld() { super(null, "unused", (WorldProvider) null, null, null); }
        @Override public TileEntity getTileEntity(int x, int y, int z) { return tile; }
        @Override public Block getBlock(int x, int y, int z) { return net.minecraft.init.Blocks.air; }
        @Override public int getBlockMetadata(int x, int y, int z) { return 0; }
        @Override protected IChunkProvider createChunkProvider() { throw new UnsupportedOperationException(); }
        @Override protected int func_152379_p() { throw new UnsupportedOperationException(); }
        @Override public Entity getEntityByID(int id) { throw new UnsupportedOperationException(); }
    }
}
