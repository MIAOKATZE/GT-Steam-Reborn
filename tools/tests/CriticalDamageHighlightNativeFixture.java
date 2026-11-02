package com.miaokatze.gtsr.common.gui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.critical.CriticalConstructionEconomy;
import com.miaokatze.gtsr.common.critical.CriticalDamageHighlights;
import com.miaokatze.gtsr.common.critical.CriticalGeometry;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.critical.CriticalStructures;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import com.miaokatze.miaokatzecore.client.render.ScreenLineStream;

/** External QA JAR only. Frozen-host cache/visual fixtures never claim natural damage scans or paid construction. */
public final class CriticalDamageHighlightNativeFixture {

    private static CriticalMachineState state;
    private static World fixtureWorld;
    private static World initialClientWorld;
    private static final List<CriticalGeometry.Voxel> SELECTED = new ArrayList<>();
    private static NBTTagCompound capabilities;
    private static long firstSession, secondSession;
    private static int firstRemaining, checks;

    private CriticalDamageHighlightNativeFixture() {}

    public static String seed(MTECriticalMachine machine, EntityPlayerMP player) throws Exception {
        state = machine.getCriticalState();
        fixtureWorld = player.worldObj;
        check(machine.isKindSelector() && state.worldObj == fixtureWorld && state.canUse(player),
            "owned loom retains ordinary server access");
        check(!fixtureWorld.loadedTileEntityList.contains(machine.getBaseMetaTileEntity())
            && !((List<?>) field(fixtureWorld, "addedTileEntityList|field_147484_a"))
                .contains(machine.getBaseMetaTileEntity()),
            "galleryLoomSeed froze only the owned host in both tile tick lists");
        capabilities = new NBTTagCompound();
        player.capabilities.writeCapabilitiesToNBT(capabilities);
        player.capabilities.isCreativeMode = true;
        player.capabilities.allowFlying = true;
        player.capabilities.isFlying = true;
        player.capabilities.disableDamage = true;
        player.sendPlayerAbilities();

        Object ledger = field(state, "constructionLedger");
        CriticalConstructionEconomy economy = (CriticalConstructionEconomy) call(ledger, "physicalJob");
        check(economy != null, "modern gallery ledger exposes the actual frozen construction layout");
        @SuppressWarnings("unchecked")
        List<CriticalGeometry.Voxel> geometry = (List<CriticalGeometry.Voxel>) call(state, "allVoxels");
        List<CriticalGeometry.Voxel> candidates = new ArrayList<>();
        for (CriticalGeometry.Voxel voxel : geometry) {
            int y = state.blockWorldY(voxel.y);
            if (economy.stage(voxel) > 0 && !CriticalStructures.isOptionalWorkPort(voxel)
                && !voxel.block.hasTileEntity(voxel.metadata) && y > 1 && y < 254) candidates.add(voxel);
        }
        final int hx = machine.getBaseMetaTileEntity().getXCoord();
        final int hy = machine.getBaseMetaTileEntity().getYCoord();
        final int hz = machine.getBaseMetaTileEntity().getZCoord();
        candidates.sort(Comparator.comparingDouble(v -> distance(v, hx, hy, hz)));
        check(!candidates.isEmpty(), "actual geometry contains maintainable main-structure material");
        CriticalGeometry.Voxel anchor = candidates.get(0);
        int ax = state.blockWorldX(anchor), ay = state.blockWorldY(anchor.y), az = state.blockWorldZ(anchor);
        candidates.sort(Comparator.comparingDouble(v -> distance(v, ax, ay, az)));
        SELECTED.clear();
        for (int i = 0; i < Math.min(32, candidates.size()) && SELECTED.size() < 3; i++) {
            CriticalGeometry.Voxel voxel = candidates.get(i);
            int x = state.blockWorldX(voxel), y = state.blockWorldY(voxel.y), z = state.blockWorldZ(voxel);
            fixtureWorld.getChunkProvider().loadChunk(x >> 4, z >> 4);
            if (fixtureWorld.isAirBlock(x, y, z)) SELECTED.add(voxel);
        }
        check(SELECTED.size() == 3, "three nearby actual geometry positions were air before fixture placement");
        CriticalGeometry.Voxel occupied = SELECTED.get(0);
        check(fixtureWorld.setBlock(state.blockWorldX(occupied), state.blockWorldY(occupied.y),
            state.blockWorldZ(occupied), occupied.block, occupied.metadata, 3),
            "one cached damaged coordinate receives an explicit same-model nonempty manual occupant");
        set(state, "state", 2);
        set(state, "stage", 9);
        set(state, "repairScanPending", false);
        set(state, "completionValidationPending", false);
        set(state, "dismantleRequested", false);
        set(state, "structureValid", false);
        set(state, "damageHighlightScanPending", true);
        call(field(state, "structureMonitor"), "invalidate");
        Map<Long, CriticalGeometry.Voxel> known = known();
        known.clear();
        known.put(SELECTED.get(0).positionKey(), SELECTED.get(0));
        known.put(SELECTED.get(1).positionKey(), SELECTED.get(1));
        double px = hx + .5, py = hy + 2D, pz = hz + 4.5;
        double tx = 0, ty = 0, tz = 0;
        for (CriticalGeometry.Voxel voxel : SELECTED) {
            tx += state.blockWorldX(voxel) + .5;
            ty += state.blockWorldY(voxel.y) + .5;
            tz += state.blockWorldZ(voxel) + .5;
        }
        double dx = tx / 3 - px, dy = ty / 3 - py - player.getEyeHeight(), dz = tz / 3 - pz;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.motionX = player.motionY = player.motionZ = 0;
        player.fallDistance = 0;
        player.playerNetServerHandler.setPlayerLocation(px, py, pz, yaw, pitch);
        check(state.canUse(player), "normal server access still passes at the flying camera position");
        CriticalMachineState.DamageHighlightSnapshot snapshot = state.getDamageHighlightSnapshot();
        check(snapshot.knownTotal == 2 && snapshot.positions.length == 6 && snapshot.scanPending,
            "server cache fixture starts with two points and an explicitly in-flight diagnostic scan");
        JsonObject report = new JsonObject();
        report.addProperty("scope", "Frozen owned host, actual geometry and seeded server cache; no natural scan, formed structure, repair or payment claim");
        report.addProperty("initialPositions", Arrays.toString(snapshot.positions));
        report.addProperty("occupiedSameModel", true);
        report.addProperty("originalCapabilities", capabilities.toString());
        report.addProperty("viewer", px + "," + py + "," + pz + " yaw=" + yaw + " pitch=" + pitch);
        report.addProperty("checks", checks);
        return report.toString();
    }

    public static String completeCachedScan() throws Exception {
        known().put(SELECTED.get(2).positionKey(), SELECTED.get(2));
        set(state, "damageHighlightScanPending", false);
        return "Controlled server cache now contains three actual geometry positions and scanPending=false; no natural scan claim";
    }

    public static String firstClientResult() throws Exception {
        Object overlay = overlay();
        checkClient(overlay, 2, true);
        firstSession = (Long) field(overlay, "latestSession");
        firstRemaining = (Integer) field(overlay, "remaining");
        initialClientWorld = Minecraft.getMinecraft().theWorld;
        check(firstSession > 0 && firstRemaining > 0 && firstRemaining <= 600, "real S2C starts one bounded client lifetime");
        check(Minecraft.getMinecraft().currentScreen == null, "world markers survive closing the real terminal");
        ScreenLineStream stream = (ScreenLineStream) field(overlay, "lines");
        boolean core = stream != null && stream.acceptedPasses() > 0;
        check(core || field(overlay, "fallback") != null, "normal world rendering entered Core or the complete native fallback");
        JsonObject report = clientReport(overlay);
        report.addProperty("coreAcceptedPasses", stream == null ? 0 : stream.acceptedPasses());
        report.addProperty("coreSubmittedVertices", stream == null ? 0 : stream.submittedVertices());
        report.addProperty("nativeFallbackUsed", field(overlay, "fallback") != null);
        return report.toString();
    }

    public static String updatedClientResult() throws Exception {
        Object overlay = overlay();
        checkClient(overlay, 3, false);
        check((Long) field(overlay, "latestSession") == firstSession,
            "subsequent cached evidence used the same production S2C session nonce");
        check((Integer) field(overlay, "remaining") < firstRemaining,
            "updating the point set and scan state does not renew the original 600 ticks");
        return clientReport(overlay).toString();
    }

    public static String expiredClientResult() throws Exception {
        Object overlay = overlay();
        check((Integer) field(overlay, "remaining") == 0 && ((int[]) field(overlay, "positions")).length == 0,
            "normal client ticks naturally expired and removed all markers");
        check(field(overlay, "world") == null && field(overlay, "lines") == null,
            "expiry cleared the world and closed Core GL resources on the actual client thread");
        return clientReport(overlay).toString();
    }

    public static String secondClientResult() throws Exception {
        Object overlay = overlay();
        checkClient(overlay, 3, true);
        secondSession = (Long) field(overlay, "latestSession");
        check(secondSession > firstSession && (Integer) field(overlay, "remaining") > 0,
            "a second actual GUI click starts a new finite request, not a stale first-session update");
        return clientReport(overlay).toString();
    }

    public static String dimensionClearedClientResult() throws Exception {
        check(Minecraft.getMinecraft().theWorld != initialClientWorld
            && Minecraft.getMinecraft().theWorld.provider.dimensionId != initialClientWorld.provider.dimensionId,
            "actual client World identity and dimension changed after the second diagnostic request");
        Object overlay = overlay();
        check((Long) field(overlay, "latestSession") == secondSession,
            "world cleanup preserves the retired session nonce against delayed updates");
        return expiredClientResult();
    }

    public static String restore(EntityPlayerMP player) throws Exception {
        try {
            @SuppressWarnings("unchecked")
            Map<EntityPlayerMP, ?> sessions = (Map<EntityPlayerMP, ?>) field(CriticalDamageHighlights.class, "SESSIONS");
            check(!sessions.containsKey(player), "server discarded this player's diagnostic after the actual dimension change");
            for (CriticalGeometry.Voxel voxel : SELECTED) {
                int x = state.blockWorldX(voxel), y = state.blockWorldY(voxel.y), z = state.blockWorldZ(voxel);
                if (!fixtureWorld.isAirBlock(x, y, z)) {
                    check(fixtureWorld.getBlock(x, y, z) == voxel.block
                        && fixtureWorld.getBlockMetadata(x, y, z) == voxel.metadata,
                        "cleanup still owns the explicit same-model fixture occupant");
                    fixtureWorld.setBlockToAir(x, y, z);
                }
            }
            known().clear();
            set(state, "damageHighlightScanPending", false);
        } finally {
            if (capabilities != null) {
                player.capabilities.readCapabilitiesFromNBT(capabilities);
                player.sendPlayerAbilities();
                String original = capabilities.toString();
                NBTTagCompound restored = new NBTTagCompound();
                player.capabilities.writeCapabilitiesToNBT(restored);
                check(original.equals(restored.toString()), "owned player's complete original abilities restored and synchronized");
            }
            SELECTED.clear();
            state = null;
            fixtureWorld = null;
            initialClientWorld = null;
            capabilities = null;
        }
        return "PASS CriticalDamageHighlightNativeFixture checks=" + checks + "; actual GUI/S2C/GL lifecycle only; controlled cache fixture";
    }

    private static void checkClient(Object overlay, int count, boolean pending) throws Exception {
        check((Integer) field(overlay, "knownTotal") == count && ((int[]) field(overlay, "positions")).length == count * 3,
            "real client S2C cache contains every supplied point and exact known count");
        check((Boolean) field(overlay, "scanPending") == pending, "client received the diagnostic scan flag");
        int[] expected = new int[count * 3];
        for (int i = 0; i < count; i++) {
            CriticalGeometry.Voxel voxel = SELECTED.get(i);
            expected[i * 3] = state.blockWorldX(voxel);
            expected[i * 3 + 1] = state.blockWorldY(voxel.y);
            expected[i * 3 + 2] = state.blockWorldZ(voxel);
        }
        check(Arrays.equals(expected, (int[]) field(overlay, "positions")),
            "including the occupied same-model coordinate, S2C coordinates match actual server geometry");
    }

    private static JsonObject clientReport(Object overlay) throws Exception {
        JsonObject report = new JsonObject();
        for (String name : new String[] { "latestSession", "remaining", "knownTotal", "scanPending" })
            report.addProperty(name, String.valueOf(field(overlay, name)));
        report.addProperty("positions", Arrays.toString((int[]) field(overlay, "positions")));
        report.addProperty("checks", checks);
        return report;
    }

    private static double distance(CriticalGeometry.Voxel voxel, int x, int y, int z) {
        double dx = state.blockWorldX(voxel) - x, dy = state.blockWorldY(voxel.y) - y,
            dz = state.blockWorldZ(voxel) - z;
        return dx * dx + dy * dy + dz * dz;
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, CriticalGeometry.Voxel> known() throws Exception {
        return (Map<Long, CriticalGeometry.Voxel>) field(state, "knownDamagedPositions");
    }

    private static Object overlay() throws Exception {
        return field(Class.forName("com.miaokatze.gtsr.client.critical.CriticalDamageHighlightClient"), "INSTANCE");
    }

    private static Object field(Object target, String name) throws Exception {
        for (String candidate : name.split("\\|"))
            for (Class<?> type = target instanceof Class ? (Class<?>) target : target.getClass(); type != null;
                type = type.getSuperclass()) {
                try {
                    Field field = type.getDeclaredField(candidate);
                    field.setAccessible(true);
                    return field.get(target instanceof Class ? null : target);
                } catch (NoSuchFieldException inherited) {}
            }
        throw new NoSuchFieldException(name);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object call(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
