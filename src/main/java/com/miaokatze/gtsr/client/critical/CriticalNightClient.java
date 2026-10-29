package com.miaokatze.gtsr.client.critical;

import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;

import com.miaokatze.gtsr.common.critical.CriticalNightClock;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class CriticalNightClient {

    public static final long FADE_NANOS = 10_000_000_000L;
    private static final Transition TRANSITION = new Transition();
    private static final NaturalClock NATURAL = new NaturalClock();
    private static final WorldProviderSurface VANILLA_SKY = new WorldProviderSurface();
    private static World sampleWorld;
    private static boolean sampleMidnight;

    private CriticalNightClient() {}

    public static void drain(ConcurrentLinkedQueue<CriticalNightClock.TimeMessage> queue) {
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) return;
        CriticalNightClock.TimeMessage message;
        CriticalNightClock.TimeMessage latest = null;
        while ((message = queue.poll()) != null)
            if (message.dimension == world.provider.dimensionId && message.clientConnection == Minecraft.getMinecraft()
                .getNetHandler()) latest = message;
        if (latest != null) {
            CriticalNightClock.receiveClient(world, latest);
            world.skylightSubtracted = world.calculateSkylightSubtracted(1.0F);
        }
    }

    public static float strength(World world) {
        if (world == null || isSampling(world)) return 0F;
        Minecraft mc = Minecraft.getMinecraft();
        if (world != mc.theWorld) return 0F;
        Object connection = mc.getNetHandler();
        boolean night = CriticalNightClock.isNight(world);
        NATURAL.update(
            world,
            connection,
            night,
            CriticalNightClock.hasPendingNight(world, connection),
            world.getWorldInfo()
                .getWorldTime(),
            world.getTotalWorldTime(),
            world.getGameRules()
                .getGameRuleBooleanValue("doDaylightCycle"));
        return TRANSITION.update(world, connection, night, System.nanoTime());
    }

    public static void clear(World world) {
        if (world == null || TRANSITION.world == world) {
            TRANSITION.reset();
            NATURAL.reset();
        }
    }

    /** Only render/lightmap calls enter this scope; gameplay observes the authoritative night immediately. */
    public static void beginSample(World world, boolean midnight) {
        sampleWorld = world;
        sampleMidnight = midnight;
    }

    public static void endSample() {
        sampleWorld = null;
        sampleMidnight = false;
    }

    public static boolean isSampling(World world) {
        return world != null && sampleWorld == world;
    }

    public static boolean isSamplingMidnight(World world) {
        return isSampling(world) && sampleMidnight;
    }

    public static long sampledTime(World world) {
        long time = NATURAL.sample(world.getTotalWorldTime());
        return sampleMidnight ? Math.floorDiv(time, 24000L) * 24000L + 18000L : time;
    }

    public static Vec3 skyColor(World world, Entity camera, float partialTicks, float strength) {
        Vec3 natural;
        beginSample(world, false);
        try {
            natural = world.getSkyColor(camera, partialTicks);
        } finally {
            endSample();
        }
        Vec3 night;
        beginSample(world, true);
        try {
            night = world.getSkyColorBody(camera, partialTicks);
        } finally {
            endSample();
        }
        return blend(natural, night, strength);
    }

    public static Vec3 fogColor(World world, float partialTicks, float strength) {
        Vec3 natural;
        beginSample(world, false);
        try {
            natural = world.getFogColor(partialTicks);
        } finally {
            endSample();
        }
        return blend(natural, VANILLA_SKY.getFogColor(.5F, partialTicks), strength);
    }

    private static Vec3 blend(Vec3 natural, Vec3 midnight, float strength) {
        // One modest step below ordinary midnight; retain the normal blue night hue.
        return Vec3.createVectorHelper(
            natural.xCoord + (midnight.xCoord * .85 - natural.xCoord) * strength,
            natural.yCoord + (midnight.yCoord * .85 - natural.yCoord) * strength,
            natural.zCoord + (midnight.zCoord * .85 - natural.zCoord) * strength);
    }

    /** The supplied palette is normal midnight, including custom lightmaps; move sky down one row only. */
    public static void blendNightLight(int[] colors, int[] natural, float strength) {
        for (int i = 255; i >= 0; i--) {
            int night = colors[Math.max(0, i / 16 - 1) * 16 + i % 16];
            colors[i] = natural == null ? night : blendColor(natural[i], night, strength);
        }
    }

    public static int blendColor(int from, int to, float strength) {
        int red = Math.round((from >> 16 & 255) + ((to >> 16 & 255) - (from >> 16 & 255)) * strength);
        int green = Math.round((from >> 8 & 255) + ((to >> 8 & 255) - (from >> 8 & 255)) * strength);
        int blue = Math.round((from & 255) + ((to & 255) - (from & 255)) * strength);
        return 0xff000000 | red << 16 | green << 8 | blue;
    }

    /** Legacy v37 probe helper; live rendering uses the ordinary-midnight palette above. */
    public static void darkenSkyLight(int[] colors) {
        for (int sky = 1; sky < 16; sky++) System.arraycopy(colors, 0, colors, sky * 16, 16);
    }

    public static final class Transition {

        private Object world, connection;
        private boolean active;
        private float from;
        private long started;

        public float update(Object world, Object connection, boolean active, long now) {
            if (this.world != world || this.connection != connection) {
                reset();
                this.world = world;
                this.connection = connection;
            }
            float value = sample(now);
            if (this.active != active) {
                from = value;
                started = now;
                this.active = active;
            }
            return value;
        }

        public float sample(long now) {
            float t = Math.max(0F, Math.min(1F, (float) (now - started) / FADE_NANOS));
            float eased = t * t * (3F - 2F * t);
            return from + ((active ? 1F : 0F) - from) * eased;
        }

        public void reset() {
            world = connection = null;
            active = false;
            from = 0F;
            started = 0L;
        }
    }

    /** Vanilla S03 packets carry our locked 18000, so preserve the pre-lock natural clock locally. */
    public static final class NaturalClock {

        private Object world, connection;
        private boolean locked, waitingForNatural, advancing;
        private long time, total;

        public void update(Object world, Object connection, boolean locked, boolean pending, long rawTime, long total,
            boolean advancing) {
            if (this.world != world || this.connection != connection) {
                reset();
                this.world = world;
                this.connection = connection;
                this.time = rawTime;
                this.total = total;
            }
            if (this.advancing != advancing) {
                time = sample(total);
                this.total = total;
                this.advancing = advancing;
            }
            if (locked || pending) this.locked = true;
            else {
                if (this.locked) {
                    this.locked = false;
                    waitingForNatural = true;
                }
                if (!waitingForNatural || Math.floorMod(rawTime, 24000L) != 18000L) {
                    time = rawTime;
                    this.total = total;
                    waitingForNatural = false;
                }
            }
        }

        public long sample(long total) {
            return time + (advancing ? Math.max(0L, total - this.total) : 0L);
        }

        public void reset() {
            world = connection = null;
            locked = waitingForNatural = advancing = false;
            time = total = 0L;
        }
    }
}
