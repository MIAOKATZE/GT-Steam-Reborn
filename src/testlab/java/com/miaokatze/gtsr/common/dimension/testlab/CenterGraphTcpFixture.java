// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.dimension.testlab;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.machine.orecenter.CenterGraph;
import com.miaokatze.gtsr.common.machine.orecenter.CenterFixtureGeometry;
import com.miaokatze.gtsr.common.machine.orecenter.CenterProcess;
import com.miaokatze.gtsr.common.machine.orecenter.MTEMineralIntegrationCenter;
import com.miaokatze.gtsr.common.items.CenterModuleItems;
import com.miaokatze.gtsr.common.machine.orecenter.CenterModule;
import com.miaokatze.gtsr.main.GTSteamReborn;
import com.miaokatze.miaokatzecore.api.testlab.ArtifactIdentity;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTOreDictUnificator;
import net.minecraftforge.fluids.FluidRegistry;

/** Dedicated server fixture. All edits after the two starting graphs must arrive via the production UI. */
public final class CenterGraphTcpFixture {

    static final String CASE_ID = "ore_integration_center.graph_tcp";
    static final String PREFIX = "gtsr.graphTcp.";
    static final String ORE_RULE = "^(?:rawOre|ore)(?!Tin$)(?:Copper|Iron)$";
    static final String FILTER_RULE = "^crushed(?:Copper|Iron)$";
    static final long FIRST = 101L, SECOND = 102L;
    static final int X = 4, Y = 100, Z = 5;

    final String runId = UUID.fromString(System.getProperty(PREFIX + "run"))
        .toString();
    final UUID actorId = UUID.fromString(System.getProperty(PREFIX + "actor"));
    final Path receiptDir = Paths.get(System.getProperty(PREFIX + "receipts"));
    private final Path ownedWorld = Paths.get(System.getProperty(PREFIX + "world"));
    private final List<Map<String, Object>> changes = new ArrayList<>();
    private MTEMineralIntegrationCenter center;
    private boolean ready, done, disabled;
    private boolean reviewBuilt, loadedModule;
    private long revision;
    private boolean fullCenterVerified;
    private final boolean fullCenter = Boolean.getBoolean(PREFIX + "fullCenter");
    private EntityPlayerMP businessActor;

    static boolean requested() {
        return System.getProperty(PREFIX + "run") != null;
    }

    static void registerServer(MinecraftServer server) {
        if (requested() && server.isDedicatedServer()) {
            FMLCommonHandler.instance()
                .bus()
                .register(new CenterGraphTcpFixture());
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        try {
            MinecraftServer server = MinecraftServer.getServer();
            WorldServer world = server.worldServerForDimension(0);
            if (!ready) {
                verifyWorld(world);
                write(receiptDir, "server-ready.json", Collections.singletonMap("runId", runId));
                ready = true;
            }
            if (center == null) {
                for (Object candidate : server.getConfigurationManager().playerEntityList) {
                    EntityPlayerMP actor = (EntityPlayerMP) candidate;
                    if (actorId.equals(actor.getUniqueID()) && actor.worldObj == world) {
                        businessActor = actor;
                        place(world, actor);
                        break;
                    }
                }
                return;
            }
            if (!reviewBuilt && (fullCenter || Files.isRegularFile(receiptDir.resolve("native-projector.png")))) {
                buildReviewWing(world);
                reviewBuilt = true;
            }
            if (reviewBuilt && !loadedModule && center.installModuleFromBus(1)) {
                require(center.installModuleFromBus(3), "Cannot install the review logistics module");
                if (fullCenter) {
                    require(center.installModuleFromBus(4) && center.installModuleFromBus(5),
                        "Cannot install two layout test logistics packs");
                }
                loadedModule = true;
            }
            if (fullCenter && reviewBuilt && !fullCenterVerified) {
                NBTTagCompound state = center.terminalState();
                if (world.getTotalWorldTime() % 80 == 0) {
                    Map<String, Object> status = new LinkedHashMap<>();
                    status.put("tier", state.getInteger("Tier"));
                    status.put("sectors", state.getInteger("Sectors"));
                    status.put("formed", state.getBoolean("Formed"));
                    status.put("moduleInstalled", loadedModule);
                    status.put("power", state.getLong("Power"));
                    write(receiptDir, "server-formation.json", status);
                }
                if (!loadedModule) return;
                if (state.getInteger("Sectors") != 63) return;
                require(state.getBoolean("Formed") && state.getLong("Power") > 0,
                    "Tower casings did not bind the four required I/O ports and the energy hatch");
                require(state.getIntArray("Grades")[1] > 0, "Installed module is missing from the six-sector snapshot");
                fullCenterVerified = true;
                businessActor.playerNetServerHandler.setPlayerLocation(X + .5, Y, Z - 2.5, 0, 0);
                write(receiptDir, "server-full-ready.json", Collections.singletonMap("sectors", 63));
            }
            long current = center.graphRevision();
            if (current == revision) return;
            revision = current;
            List<CenterGraph> graphs = center.graphSnapshot();
            require(graphs.size() == 2, "Expected two live graphs");
            CenterGraph edited = graphs.get(0).id == FIRST ? graphs.get(0) : graphs.get(1);
            require(edited.id == FIRST, "Edited graph lost its identity");
            List<CenterGraph.OutletFilter> filters = edited.nodes.get(0)
                .filters();
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("revision", revision);
            change.put("first", graphs.get(0).id);
            change.put("enabled", edited.enabled);
            change.put("rules", edited.rules());
            change.put(
                "filters",
                filters.stream()
                    .map(filter -> filter.rules())
                    .collect(java.util.stream.Collectors.toList()));
            changes.add(change);
            write(receiptDir, "server-progress.json", change);
            if (!edited.enabled) disabled = true;
            if (!disabled || !edited.enabled
                || graphs.get(0).id != SECOND
                || !edited.rules()
                    .equals(Collections.singletonList(ORE_RULE)))
                return;
            require(
                filters.size() == 1 && filters.get(0).slot == 0
                    && filters.get(0).allow
                    && filters.get(0)
                        .rules()
                        .equals(Collections.singletonList(FILTER_RULE)),
                "Saved outlet filter differs");
            NBTTagCompound checkpoint = new NBTTagCompound();
            center.saveNBTData(checkpoint);
            NBTTagList saved = checkpoint.getTagList("CenterGraphs", 10);
            require(
                saved.tagCount() == 2 && CenterGraph.load(saved.getCompoundTagAt(0)).id == SECOND
                    && CenterGraph.load(saved.getCompoundTagAt(1))
                        .save()
                        .equals(edited.save()),
                "Graph order or edits absent from NBT");
            Map<String, Object> status = report(runId, "PASS");
            status.put("actorUuid", actorId.toString());
            status.put("actorOp", false);
            status.put("savedGraphOrder", Arrays.asList(SECOND, FIRST));
            status.put("changes", changes);
            write(receiptDir, "server-checks.json", status);
            done = true;
        } catch (Exception failure) {
            done = true;
            GTSteamReborn.LOG.error("Ore center dedicated graph fixture failed", failure);
            Map<String, Object> status = report(runId, "FAIL");
            status.put("error", failure.toString());
            status.put("changes", changes);
            write(receiptDir, "server-checks.json", status);
        }
    }

    private void verifyWorld(WorldServer world) throws IOException {
        Path actual = world.getSaveHandler()
            .getWorldDirectory()
            .toPath()
            .toRealPath();
        Path expected = ownedWorld.toRealPath();
        require(
            actual.equals(expected) && expected.getFileName()
                .toString()
                .equals("MiaoHost-" + runId),
            "Wrong server save");
        String marker = new String(
            Files.readAllBytes(actual.resolve(".miaotest-host-owner.json")),
            StandardCharsets.UTF_8);
        require(
            runId.equals(
                new JsonParser().parse(marker)
                    .getAsJsonObject()
                    .get("runId")
                    .getAsString()),
            "Wrong fixture marker");
    }

    private void place(WorldServer world, EntityPlayerMP actor) {
        require(!actor.canCommandSenderUseCommand(2, "gtsrtest"), "Business actor must not be OP2");
        world.theChunkProviderServer.loadChunk(X >> 4, Z >> 4);
        for (int x = X - 2; x <= X + 2; x++) for (int z = Z - 3; z <= Z + 2; z++) {
            world.setBlock(x, Y - 1, z, Blocks.stone);
        }
        int id = GTSRItemList.OreIntegrationCenter.get(1)
            .getItemDamage();
        int baseType = GregTechAPI.METATILEENTITIES[id].getTileEntityBaseType();
        world.setBlock(X, Y, Z, GregTechAPI.sBlockMachines, baseType, 3);
        TileEntity tile = world.getTileEntity(X, Y, Z);
        require(tile instanceof IGregTechTileEntity, "GT tile placement failed");
        IGregTechTileEntity gt = (IGregTechTileEntity) tile;
        gt.setInitialValuesAsNBT(null, (short) id);
        gt.setOwnerName(actor.getCommandSenderName());
        gt.setOwnerUuid(actorId);
        gt.setFrontFacing(ForgeDirection.NORTH);
        gt.disableWorking();
        center = (MTEMineralIntegrationCenter) gt.getMetaTileEntity();
        actor.inventory.setInventorySlotContents(actor.inventory.currentItem,
            GTSRItemList.HubTerminal.get(1));
        actor.inventory.setInventorySlotContents(9, GTOreDictUnificator.get(OrePrefixes.rawOre, Materials.Copper, 24));
        actor.playerNetServerHandler.setPlayerLocation(X + .5, Y, Z - 2.5, 0, 0);
        List<CenterGraph.Node> nodes = Arrays.asList(
            new CenterGraph.Node(CenterProcess.CRUSHER, new int[] { 1, -1, -1 }).withPosition(174, 91),
            new CenterGraph.Node(CenterProcess.WASHER, new int[] { 2, -1, -1 }).withPosition(282, 91),
            new CenterGraph.Node(CenterProcess.CRUSHER, new int[] { 3, -1, -1 }).withPosition(106, 192),
            new CenterGraph.Node(CenterProcess.CENTRIFUGE, new int[] { 4, 5 }).withPosition(214, 192),
            new CenterGraph.Node(CenterProcess.SMELTING, new int[] { -1 }).withPosition(322, 192),
            new CenterGraph.Node(CenterProcess.SMELTING, new int[] { -1 }).withPosition(214, 278));
        require(
            center.setGraphs(
                Arrays.asList(
                    new CenterGraph(FIRST, "铜矿加工审阅", 4, true, Collections.singletonList("rawOreCopper"), nodes),
                    new CenterGraph(
                        SECOND,
                        "备用图谱",
                        4,
                        true,
                        Collections.singletonList("rawOreCopper"),
                        Collections.singletonList(new CenterGraph.Node(CenterProcess.CRUSHER, new int[] { -1, -1, -1 }))))),
            "Unable to seed graphs");
        revision = center.graphRevision();
        world.markBlockForUpdate(X, Y, Z);
        write(receiptDir, "server-progress.json", Collections.singletonMap("revision", revision));
    }

    private void buildReviewWing(WorldServer world) {
        boolean full = fullCenter;
        Set<Long> chunks = new HashSet<>();
        for (int[] block : full ? CenterFixtureGeometry.fullCenter() : CenterFixtureGeometry.oneSector()) {
            int x = (X + block[0]) >> 4, z = (Z + block[2]) >> 4;
            if (chunks.add(((long) x << 32) | (z & 0xffffffffL))) world.theChunkProviderServer.loadChunk(x, z);
        }
        ItemStack trigger = new ItemStack(com.gtnewhorizon.structurelib.StructureLibAPI.getDefaultHologramItem(), 2);
        if (full) com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor
            .setChannelData(trigger, "ore_center_sectors", 6);
        center.construct(trigger, false);
        // Exercise ordinary casing on the controller level, the beam above it, and the rear of the wall.
        IGregTechTileEntity input = placeReviewPort(world, -1, 0, 0, ItemList.Hatch_Input_Bus_LV);
        placeReviewPort(world, 1, 0, 0, ItemList.Hatch_Output_Bus_LV);
        IGregTechTileEntity fluid = placeReviewPort(world, 0, 1, 0, ItemList.Hatch_Input_IV);
        placeReviewPort(world, 0, 0, 1, ItemList.Hatch_Output_LV);
        placeReviewPort(world, 5, 0, 0, ItemList.Hatch_Energy_LV);
        input.setInventorySlotContents(0, CenterModuleItems.stack(CenterModule.CRUSHER, 1));
        input.setInventorySlotContents(1, CenterModuleItems.stack(CenterModule.WASHER, 1));
        input.setInventorySlotContents(2, CenterModuleItems.stack(CenterModule.SPEED, 1));
        ItemStack logistics = CenterModuleItems.stack(CenterModule.LOGISTICS, 1);
        logistics.stackSize = fullCenter ? 3 : 1;
        input.setInventorySlotContents(3, logistics);
        ((gregtech.api.metatileentity.implementations.MTEHatchInput) fluid.getMetaTileEntity())
            .fill(ForgeDirection.UNKNOWN, FluidRegistry.getFluidStack("water", 8000), true);
    }

    private static IGregTechTileEntity placeReviewPort(WorldServer world, int x, int y, int z, ItemList type) {
        int id = type.get(1).getItemDamage();
        require(world.getBlock(X + x, Y + y, Z + z) == (x == 5 ? GregTechAPI.sBlockCasings3 : GregTechAPI.sBlockCasings2)
            && world.getBlockMetadata(X + x, Y + y, Z + z) == (x == 5 ? 10 : 0),
            "Fixture must replace an actual authored casing");
        world.setBlock(X + x, Y + y, Z + z, GregTechAPI.sBlockMachines,
            GregTechAPI.METATILEENTITIES[id].getTileEntityBaseType(), 3);
        IGregTechTileEntity tile = (IGregTechTileEntity) world.getTileEntity(X + x, Y + y, Z + z);
        tile.setInitialValuesAsNBT(null, (short) id);
        tile.setFrontFacing(ForgeDirection.NORTH);
        return tile;
    }

    static void require(boolean condition, String failure) {
        if (!condition) throw new IllegalStateException(failure);
    }

    static Map<String, Object> report(String runId, String status) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("runId", runId);
        report.put("status", status);
        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put(
            "gtsr",
            ArtifactIdentity.capture(CenterGraphTcpFixture.class)
                .asMap());
        artifacts.put(
            "miaokatzecore",
            ArtifactIdentity.capture(ArtifactIdentity.class)
                .asMap());
        report.put("artifacts", artifacts);
        return report;
    }

    static void write(Path dir, String name, Map<String, ?> data) {
        try {
            Path temp = Files.createTempFile(dir, name, ".tmp");
            Files.write(
                temp,
                new Gson().toJson(data)
                    .getBytes(StandardCharsets.UTF_8));
            Files.move(temp, dir.resolve(name), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to write " + name, failure);
        }
    }
}
