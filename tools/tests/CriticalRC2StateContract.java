package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTOreDictUnificator;

/** Candidate-jar State integration. Sparse world, real mutable GT input bus, real public commands/ticks and NBT. */
public final class CriticalRC2StateContract {
    private static final Item COMPONENT = new Item();
    private static final int MAIN_META = 31050;
    private static int registryId = 30000;
    private static int blockRegistryId = 1000;

    private static Field member(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); return f;
    }

    private static void registerItem(String name, Item item) throws Exception {
        java.lang.reflect.Method method = Item.itemRegistry.getClass().getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        method.setAccessible(true); method.invoke(Item.itemRegistry, registryId++, name, item);
    }

    private static void registerBlock(String name, Block block) throws Exception {
        java.lang.reflect.Method method = Block.blockRegistry.getClass().getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        int id = name.equals("minecraft:water") ? 9 : name.equals("minecraft:lava") ? 11 : blockRegistryId++;
        method.setAccessible(true); method.invoke(Block.blockRegistry, id, name, block);
        ItemBlock item = new ItemBlock(block);
        java.lang.reflect.Method items = Item.itemRegistry.getClass().getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        items.setAccessible(true); items.invoke(Item.itemRegistry, id, name, item);
    }

    /** Registry identities only, deliberately not an FML/recipe-registration or GT material-generation test. */
    @SuppressWarnings("unchecked")
    static void seedCatalog() throws Exception {
        materials(); registerItem("gtsr:rc2_components", COMPONENT);
        Class.forName("net.minecraft.init.Items");
        Field feather = net.minecraft.init.Items.class.getDeclaredField("feather");
        unsafe.putObject(unsafe.staticFieldBase(feather), unsafe.staticFieldOffset(feather), COMPONENT);
        Map<String, Block> blocks = (Map<String, Block>) member(CriticalMaterials.class, "MATERIALS").get(null);
        for (Map.Entry<String, Block> entry : blocks.entrySet()) registerBlock("gtsr:critical_" + entry.getKey(), entry.getValue());
        registerBlock("minecraft:water", new Block(Material.water) {});
        registerBlock("minecraft:lava", new Block(Material.lava) {});
        registerBlock("gtsr:rc2_anchor", CriticalRenderAnchors.BLOCK);
        GregTechAPI.sBlockMachines = new Block(Material.iron) {};
        registerBlock("gregtech:rc2_machine", GregTechAPI.sBlockMachines);
        int meta = 1;
        for (GTSRItemList controller : new GTSRItemList[] { GTSRItemList.CriticalLoom, GTSRItemList.CriticalSolar,
            GTSRItemList.CriticalTurbine, GTSRItemList.CriticalBattery, GTSRItemList.CriticalSun })
            controller.set(new ItemStack(COMPONENT, 1, controller == GTSRItemList.CriticalTurbine ? MAIN_META : MAIN_META + meta++));
        for (ItemList part : new ItemList[] { ItemList.Electric_Motor_UHV, ItemList.Electric_Pump_UHV,
            ItemList.Electric_Piston_UHV, ItemList.Field_Generator_UHV, ItemList.Emitter_UHV, ItemList.Sensor_UHV,
            ItemList.Gravistar }) part.set(new ItemStack(COMPONENT, 1, meta++));
        Map<String, ItemStack> ores = (Map<String, ItemStack>) member(GTOreDictUnificator.class, "sName2StackMap").get(null);
        OrePrefixes[] prefixes = { OrePrefixes.circuit, OrePrefixes.pipeMedium, OrePrefixes.gearGt, OrePrefixes.stickLong,
            OrePrefixes.rotor, OrePrefixes.plate, OrePrefixes.wireFine };
        Materials[] materials = { Materials.UHV, Materials.Neutronium, Materials.NaquadahAlloy,
            Materials.Europium, Materials.Platinum, Materials.Iridium, Materials.Osmium, Materials.CosmicNeutronium };
        for (OrePrefixes prefix : prefixes) for (Materials material : materials)
            ores.put(prefix.get(material).toString(), new ItemStack(COMPONENT, 1, meta++));
        GregTechAPI.METATILEENTITIES[MAIN_META] = new MTECriticalMachine("rc2_main_prototype", CriticalMachineKind.TURBINE);
    }

    private static final class Bus extends MTEHatchInputBus {
        ItemStack[] items;
        private Bus() { super("rc2_bus", 9, 64, new String[0], new gregtech.api.interfaces.ITexture[0][][]); }
        @Override public int getSizeInventory() { return items.length; }
        @Override public int getCircuitSlot() { return -1; }
        @Override public ItemStack getStackInSlot(int slot) { return items[slot]; }
        @Override public void updateSlots() {
            for (int i = 0; i < items.length; i++) if (items[i] != null && items[i].stackSize <= 0) items[i] = null;
        }
        @Override public ItemStack decrStackSize(int slot, int count) {
            ItemStack result = items[slot].copy(); result.stackSize = count;
            items[slot].stackSize -= count; if (items[slot].stackSize == 0) items[slot] = null; return result;
        }
    }

    private static final class Host extends BaseMetaTileEntity {
        @Override public void markDirty() {}
        @Override public void setFrontFacing(ForgeDirection facing) {
            try { member(BaseMetaTileEntity.class, "mFacing").set(this, facing); }
            catch (Exception failure) { throw new RuntimeException(failure); }
        }
        @Override public void setInitialValuesAsNBT(NBTTagCompound n, short meta) {
            try {
                MTECriticalMachine main = new MTECriticalMachine("rc2_actual_main", CriticalMachineKind.TURBINE);
                member(MetaTileEntity.class, "mBaseMetaTileEntity").set(main, this); setMetaTileEntity(main);
            } catch (Exception failure) { throw new RuntimeException(failure); }
        }
    }

    private static final class Player extends EntityPlayerMP {
        private Player() { super(null, null, null, null); }
        @Override public boolean canCommandSenderUseCommand(int level, String name) { return true; }
        @Override public java.util.UUID getUniqueID() { return new java.util.UUID(0, 7); }
    }

    private static CriticalConstructionLedger ledger(CriticalMachineState state) throws Exception {
        return (CriticalConstructionLedger) field(state, "constructionLedger");
    }

    private static Host host(SparseWorld world) throws Exception {
        Host host = (Host) unsafe.allocateInstance(Host.class);
        member(gregtech.api.metatileentity.BaseTileEntity.class, "mReturnedCoordinates")
            .set(host, new net.minecraft.util.ChunkCoordinates());
        host.setWorldObj(world);
        return host;
    }

    private static void refreshPorts(MTECriticalMachine machine) throws Exception {
        // The native host calls this before State.updateEntity on every server tick. Keep that real discovery
        // boundary when driving State directly, without booting the unrelated GT parent processing lifecycle.
        java.lang.reflect.Method discover = MTECriticalMachine.class.getDeclaredMethod("discoverHatches", boolean.class);
        discover.setAccessible(true); discover.invoke(machine, false);
    }

    private static NBTTagCompound save(CriticalMachineState state) {
        NBTTagCompound n = new NBTTagCompound(); state.writeToNBT(n); return n;
    }

    private static String identity(ItemStack stack) { return Item.itemRegistry.getNameForObject(stack.getItem()) + ":" + stack.getItemDamage(); }

    private static Map<String, Long> amounts(ItemStack[] items) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (ItemStack stack : items) if (stack != null) result.merge(identity(stack), (long) stack.stackSize, Long::sum);
        return result;
    }

    private static Map<String, Long> balances(CriticalMachineState state, String field) {
        Map<String, Long> result = new LinkedHashMap<>();
        NBTTagList rows = save(state).getTagList("constructionPhysicalBalances", 10);
        for (int i = 0; i < rows.tagCount(); i++) {
            NBTTagCompound row = rows.getCompoundTagAt(i);
            result.put(identity(CriticalResourceNbt.readItems(row.getTagList("item", 10), 1)[0]), row.getLong(field));
        }
        return result;
    }

    private static void configureRepresentativeStages(CriticalMachineState state, CriticalGeometry.Voxel main,
        List<CriticalGeometry.Voxel> body) throws Exception {
        List<List<CriticalGeometry.Voxel>> stages = new ArrayList<>();
        stages.add(Collections.emptyList()); stages.add(Collections.singletonList(main));
        stages.add(Collections.emptyList()); stages.add(Collections.emptyList()); stages.add(body);
        set(state, "stageCache", stages);
        ((Map<?, ?>) field(state, "paidStageCache")).clear();
    }

    public static void main(String[] args) throws Exception {
        Thread.currentThread().setName("Server thread");
        for (Class<?> type : new Class<?>[] { CriticalMachineState.class, CriticalConstructionLedger.class,
            CriticalConstructionEconomy.class, CriticalHatchFunding.class, CriticalNativeMEFunding.class,
            MTECriticalMachine.class }) CriticalFoundationContract.source(type, args);
        unsafe = (sun.misc.Unsafe) member(sun.misc.Unsafe.class, "theUnsafe").get(null);
        member(cpw.mods.fml.common.Loader.class, "instance").set(null, unsafe.allocateInstance(cpw.mods.fml.common.Loader.class));
        member(cpw.mods.fml.relauncher.FMLRelaunchLog.class, "side").set(null, cpw.mods.fml.relauncher.Side.SERVER);
        seedCatalog();
        SparseWorld world = world(); CriticalMachineState state = state(world, 31, ForgeDirection.NORTH);
        set(state, "config", new CriticalConfiguration(CriticalTier.T3, CriticalMachineKind.TURBINE, 0, 0, 0));
        set(state, "geometryCache", null); geometry(state);
        set(state, "owner", ""); set(state, "state", 0); set(state, "structureId", ""); set(state, "jobId", "");
        Host ownerHost = host(world);
        ownerHost.setWorldObj(world); ownerHost.xCoord = state.xCoord; ownerHost.yCoord = state.yCoord; ownerHost.zCoord = state.zCoord;
        state.bindHost(ownerHost); CriticalConstructionOwnershipContract.coverage(world, state);
        MTECriticalMachine machine = new MTECriticalMachine("rc2_state_contract", null);
        member(MTECriticalMachine.class, "state").set(machine, state);
        member(MetaTileEntity.class, "mBaseMetaTileEntity").set(machine, ownerHost); ownerHost.setMetaTileEntity(machine);
        ownerHost.setFrontFacing(ForgeDirection.NORTH);
        Player player = (Player) unsafe.allocateInstance(Player.class); player.capabilities = new PlayerCapabilities();
        player.posX = state.xCoord + .5; player.posY = state.yCoord + .5; player.posZ = state.zCoord + .5;
        for (CriticalGeometry.Voxel voxel : geometry(state)) if (CriticalLoomStructure.isLoomVoxel(voxel)
            && !CriticalStructures.isController(voxel)) put(state, voxel, CriticalLoomStructure.shell(voxel));
        set(state, "debugIgnoreEnergy", true); set(state, "debugSpeed", 3); // Items remain mandatory.
        boolean started = false;
        for (int i = 0; i < 3000 && !started; i++) started = state.startConstruction(player);
        check(started && state.getState() == 1 && ledger(state).isPhysicalJob(), "Public new start freezes the real full-job physical economy: " + state.getStatus());
        NBTTagCompound frozen = save(state).getCompoundTag("constructionPhysicalJob");
        set(state, "foundationRequired", false); // This contract starts after the separately tested foundation domain.
        List<CriticalGeometry.Voxel> full = geometry(state);
        CriticalGeometry.Voxel main = full.stream().filter(v -> CriticalStructures.isController(v) && !CriticalLoomStructure.isLoomVoxel(v)).findFirst().orElseThrow();
        Map<String, Long> carriers = CriticalConstructionEconomy.carrierPositions(CriticalMachineKind.TURBINE, full);
        CriticalGeometry.Voxel carrier = full.stream().filter(v -> v.positionKey() == carriers.get("rotor_0")).findFirst().orElseThrow();
        List<CriticalGeometry.Voxel> body = new ArrayList<>(); body.add(carrier);
        for (CriticalGeometry.Voxel voxel : full) if (body.size() < 1000 && voxel.stage == 4
            && !carriers.containsValue(voxel.positionKey()) && voxel.block.getLightValue() == 0) body.add(voxel);
        configureRepresentativeStages(state, main, body);
        Host mainHost = host(world);
        mainHost.setWorldObj(world); mainHost.xCoord = state.blockWorldX(main); mainHost.yCoord = state.blockWorldY(main.y); mainHost.zCoord = state.blockWorldZ(main);
        world.tiles.put(key(mainHost.xCoord, mainHost.yCoord, mainHost.zCoord), mainHost);
        Bus bus = new Bus();
        ItemStack[] bank = ledger(state).physicalJob().demand();
        for (ItemStack stack : bank) stack.stackSize = 1_000_000;
        bus.items = bank;
        Host busHost = host(world);
        member(MetaTileEntity.class, "mBaseMetaTileEntity").set(bus, busHost); busHost.setMetaTileEntity(bus);
        CriticalGeometry.Voxel busPort = state.getHatchPorts().stream().filter(CriticalLoomStructure::isLoomVoxel).findFirst().orElseThrow();
        busHost.xCoord = state.blockWorldX(busPort); busHost.yCoord = state.blockWorldY(busPort.y); busHost.zCoord = state.blockWorldZ(busPort);
        world.tiles.put(key(busHost.xCoord, busHost.yCoord, busHost.zCoord), busHost);
        member(MTEMultiBlockBase.class, "mInputBusses").set(machine, new ArrayList<>(Collections.singletonList(bus)));
        refreshPorts(machine);
        check(((List<?>) member(MTEMultiBlockBase.class, "mInputBusses").get(machine)).contains(bus),
            "Native host discovery admits the real mutable bus in the manual loom");
        Map<String, Long> before = amounts(bus.items);
        boolean partial = false;
        for (int ticks = 0; ticks < 100 && !partial; ticks++) {
            state.updateEntity();
            if (state.getStage() == 4) {
                int round = state.getConstructionRoundIndex(), totalRounds = state.getConstructionRoundTotal();
                int start = body.size() * round / totalRounds, end = body.size() * (round + 1) / totalRounds;
                partial = state.isConstructionRoundFunded() && state.getCursor() > start && state.getCursor() < end;
            }
        }
        check(partial, "Real updateEntity stops with a paid, partly placed multi-voxel round: stage=" + state.getStage()
            + " cursor=" + state.getCursor() + " round=" + state.getConstructionRoundIndex() + " funded="
            + state.isConstructionRoundFunded() + " placed=" + ((java.util.BitSet) field(state, "placed")).cardinality()
            + " status=" + state.getStatus());
        NBTTagCompound checkpoint = save(state);
        int heldRound = state.getConstructionRoundIndex();
        state.readFromNBT(checkpoint); geometry(state); configureRepresentativeStages(state, main, body);
        check(state.isConstructionRoundFunded() && state.getConstructionRoundIndex() == heldRound,
            "World journal reload retains the exact paid partial placement boundary");
        for (int tick = 0; tick < 300 && state.getStage() < 5; tick++) state.updateEntity();
        check(state.getStage() >= 5 && ((java.util.BitSet) field(state, "placed")).cardinality() == 1001,
            "Real controller and 1000 physical shells are actually placed and individually journaled: " + state.getStatus());
        Map<String, Long> paid = balances(state, "paid"), after = amounts(bus.items);
        List<CriticalGeometry.Voxel> targets = new ArrayList<>(body); targets.add(main);
        Map<String, Long> exactBill = amounts(ledger(state).physicalJob().costs(targets, 0, targets.size()));
        paid.entrySet().removeIf(entry -> entry.getValue() == 0);
        check(paid.equals(exactBill), "Partial placement reload bills exactly the real selected identities once, including one native main");
        for (Map.Entry<String, Long> entry : paid.entrySet())
            check(before.get(entry.getKey()) - after.getOrDefault(entry.getKey(), 0L) == entry.getValue(),
                "Real mutable GT bus debit equals the persisted paid identity: " + entry.getKey());
        check(save(state).getCompoundTag("constructionPhysicalJob").equals(frozen), "Real phase advancement and reload never replace the full frozen job price basis");
        // Complete the rest of the sparse world as independently pre-existing blocks; only the 1001 journaled
        // placements belong to this bill/removal. This avoids building 118k unrelated cells in an integration probe.
        fill(state); world.tiles.put(key(ownerHost.xCoord, ownerHost.yCoord, ownerHost.zCoord), ownerHost);
        world.tiles.put(key(mainHost.xCoord, mainHost.yCoord, mainHost.zCoord), mainHost);
        put(state, main, GregTechAPI.sBlockMachines);
        set(state, "state", 2); set(state, "structureValid", true); set(state, "completionValidationPending", false);
        monitor(state).primeConstruction(full.size() + CriticalGeometry.optionalWorkPorts(state).size());
        CriticalGeometry.Voxel panel = body.get(1);
        world.setBlockToAir(state.blockWorldX(carrier), state.blockWorldY(carrier.y), state.blockWorldZ(carrier));
        world.setBlockToAir(state.blockWorldX(panel), state.blockWorldY(panel.y), state.blockWorldZ(panel));
        Map<String, Long> beforeRepair = balances(state, "paid");
        check(state.repairStructure(player), "Public repair accepts the two truly damaged economic positions");
        refreshPorts(machine);
        for (int tick = 0; tick < 2000 && state.getState() == 4; tick++) state.updateEntity();
        check(state.getState() == 2, "Real repair scan and placement finish: " + state.getStatus() + " stage=" + state.getStage()
            + " cursor=" + state.getCursor() + " scan=" + field(state, "repairScanCursor")
            + " missing=" + ((java.util.BitSet) field(state, "repairTargets")).cardinality()
            + " busses=" + ((List<?>) member(MTEMultiBlockBase.class, "mInputBusses").get(machine)).size()
            + " wanted=" + amounts(state.getConstructionNextItemCostsForGui())
            + " absent=" + amounts(state.getConstructionMissingItemsForGui()));
        Map<String, Long> repairBill = amounts(ledger(state).physicalJob().costs(Arrays.asList(carrier, panel), 0, 2));
        Map<String, Long> afterRepair = balances(state, "paid");
        for (String id : afterRepair.keySet()) check(afterRepair.get(id) - beforeRepair.getOrDefault(id, 0L) == repairBill.getOrDefault(id, 0L),
            "Repair pays only one damaged carrier and one adjacent panel: " + id);
        Map<String, Long> expectedRefund = new LinkedHashMap<>();
        Map<String, Long> installed = balances(state, "installed"), escrow = balances(state, "escrow");
        String mainIdentity = identity(GTSRItemList.CriticalTurbine.get(1));
        for (String id : installed.keySet()) expectedRefund.put(id, escrow.getOrDefault(id, 0L)
            + (id.equals(mainIdentity) ? Math.min(1, installed.get(id)) : installed.get(id) / 10));
        check(state.startDismantle(player), "Public dismantle accepts the repaired paid construction");
        for (int tick = 0; tick < 1000 && state.getState() != 0; tick++) state.updateEntity();
        check(state.getState() == 0, "Actual placed-only removal settles its real output inventory: " + state.getStatus());
        Map<String, Long> actualRefund = amounts((ItemStack[]) field(state, "inventory"));
        expectedRefund.entrySet().removeIf(entry -> entry.getValue() == 0);
        check(actualRefund.equals(expectedRefund), "Dismantle returns exactly uninstalled escrow, one main and aggregate 10% surviving hardware");
        check(actualRefund.get(mainIdentity) == 1 && !ledger(state).isPhysicalJob(), "Controller returns once and successful dismantle releases the frozen job");
        System.out.println("PASS CriticalRC2StateContract checks=" + checks);
    }
}
