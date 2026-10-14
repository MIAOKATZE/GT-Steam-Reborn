package com.miaokatze.gtsr.common.critical;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

import com.google.common.collect.HashBiMap;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ModMetadata;
import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/** Real Forge issuance via minimal real FML container maps; no full lifecycle or WorldServer IO. */
public final class CriticalV31FreshTickets {

    static final JsonArray checks = new JsonArray();

    static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    static void check(String name, boolean pass) {
        JsonObject row = new JsonObject();
        row.addProperty("name", name);
        row.addProperty("pass", pass);
        checks.add(row);
        if (!pass) throw new AssertionError(name);
    }

    static void run() throws Exception {
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null);
        CriticalChunkLoadingV31Probe.unsafe = unsafe;
        Object mod = new Object();
        ModMetadata metadata = new ModMetadata();
        metadata.modId = "gtsr";
        metadata.name = "GTSR minimal container fixture";
        metadata.version = "fixture";
        DummyModContainer container = new DummyModContainer(metadata);
        Loader loader = (Loader) unsafe.allocateInstance(Loader.class);
        LoadController controller = (LoadController) unsafe.allocateInstance(LoadController.class);
        HashBiMap<ModContainer, Object> objects = HashBiMap.create();
        objects.put(container, mod);
        field(LoadController.class, "modObjectList").set(controller, objects);
        field(Loader.class, "modController").set(loader, controller);
        Map<String, ModContainer> namedMods = new HashMap<>();
        namedMods.put("gtsr", container);
        field(Loader.class, "namedMods").set(loader, namedMods);
        field(Loader.class, "instance").set(null, loader);
        field(CriticalChunkTicketManager.class, "mod").set(null, mod);
        ForgeChunkManager.setForcedChunkLoadingCallback(
            mod,
            (ForgeChunkManager.LoadingCallback) field(CriticalChunkTicketManager.class, "INSTANCE").get(null));
        check("real FML Loader resolves minimal registered container", Loader.instance().getModObjectList().inverse().get(mod) == container);

        GTValues.enableChunkloaders = true;
        field(ForgeChunkManager.class, "overridesEnabled").setBoolean(null, false);
        field(ForgeChunkManager.class, "defaultMaxChunks").setInt(null, 64);
        field(ForgeChunkManager.class, "defaultMaxCount").setInt(null, 1);
        CriticalChunkLoadingV31Probe.SparseWorld world = CriticalChunkLoadingV31Probe.world();
        BaseMetaTileEntity host = CriticalChunkLoadingV31Probe.host(world);
        Ticket issued = CriticalChunkTicketManager.ensureTicket(host);
        check("production manager issues a fresh actual Forge ticket", issued != null && "gtsr".equals(issued.getModId()) && issued.getChunkListDepth() == 64);
        check("fresh ticket carries exact owner coordinates", issued.getModData().getInteger("OwnerX") == host.xCoord
            && issued.getModData().getInteger("OwnerY") == host.yCoord && issued.getModData().getInteger("OwnerZ") == host.zCoord);
        check("same owner reuses single freshly issued ticket", CriticalChunkTicketManager.ensureTicket(host) == issued);
        ChunkCoordIntPair chunk = new ChunkCoordIntPair(0, 0);
        check("fresh native ticket really forces requested chunk", CriticalChunkTicketManager.requestChunkLoad(host, chunk)
            && ForgeChunkManager.getPersistentChunksFor(world).get(chunk).contains(issued));
        BaseMetaTileEntity second = (BaseMetaTileEntity) unsafe.allocateInstance(BaseMetaTileEntity.class);
        second.setWorldObj(world);
        second.xCoord = 17;
        second.yCoord = 64;
        second.zCoord = 1;
        check("real Forge ticket count quota rejects second owner", CriticalChunkTicketManager.ensureTicket(second) == null);
        CriticalChunkTicketManager.releaseTicket(host);
        check("release clears real force and owner registration", issued.getChunkList().isEmpty()
            && CriticalChunkTicketManager.getTicket(host) == null && !ForgeChunkManager.getPersistentChunksFor(world).containsValue(issued));
        Ticket recovered = CriticalChunkTicketManager.ensureTicket(second);
        check("released quota permits new actual issuance", recovered != null && recovered != issued);
        CriticalChunkTicketManager.releaseTicket(second);
    }

    public static void main(String[] args) throws Exception {
        JsonObject report = new JsonObject();
        try {
            field(cpw.mods.fml.relauncher.FMLRelaunchLog.class, "side")
                .set(null, cpw.mods.fml.relauncher.Side.SERVER);
            field(cpw.mods.fml.relauncher.FMLRelaunchLog.class, "minecraftHome")
                .set(null, Path.of(args[0]).toAbsolutePath().getParent().toFile());
            run();
            report.addProperty("status", "PASS");
        } catch (Throwable failure) {
            report.addProperty("status", "FAIL");
            report.addProperty("failure", failure.toString());
            throw failure;
        } finally {
            report.add("checks", checks);
            report.addProperty("scope", "Real Forge.requestTicket through production GTSR manager with real minimal FML Loader/LoadController/DummyModContainer maps; sparse World; no complete FML lifecycle, disk restart or WorldServer asynchronous IO");
            Files.writeString(Path.of(args[0]), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }
    }
}
