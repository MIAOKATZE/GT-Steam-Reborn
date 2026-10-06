package com.miaokatze.gtsr.client.encounter;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntityResidualOathguard;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntitySilentKing;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntitySealedChest;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntityUnsealedChest;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Local HUD/music owner. Never sets vanilla BossStatus or cancels another mod's overlay. */
public final class EncounterClient extends Gui implements IResourceManagerReloadListener {

    private static final int[] COLORS = { 0xCF554D, 0xD26746, 0xD67D42, 0xDB9548, 0xE0AE50, 0xE5C360, 0xCFCF65,
        0xB3C16C, 0x95B774, 0x76AC7C, 0x58A586, 0x439C93, 0x399BA3, 0x3E9EB5, 0x4E9BC8, 0x5C94D5, 0x6E86DB, 0x8178D6,
        0x966CCB, 0xAB66BF, 0xBC69AF, 0xCD72A2, 0xD67E99, 0xE1919E, 0xE6A2AA, 0xDAB0AA, 0xC3BDA0, 0xA8C3AB, 0x8CCBBB,
        0x6ECCCE };

    private EntitySilentKing target;
    private ThroneMusic music;
    private final EchoBossOverlay echoOverlay = new EchoBossOverlay();

    public static void register() {
        Minecraft mc = Minecraft.getMinecraft();
        RigRepository repository = new RigRepository("gtsr");
        EncounterClient owner = new EncounterClient();
        IReloadableResourceManager resources = (IReloadableResourceManager) mc.getResourceManager();
        resources.registerReloadListener(repository);
        resources.registerReloadListener(owner);
        RenderingRegistry
            .registerEntityRenderingHandler(EntitySilentKing.class, new EncounterEntityRenderer(repository, "dc-10"));
        RenderingRegistry.registerEntityRenderingHandler(
            EntityResidualOathguard.class,
            new EncounterEntityRenderer(repository, "dr-09"));
        RenderingRegistry.registerEntityRenderingHandler(
            com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho.class,
            new EchoEntityRenderer(repository));
        RenderingRegistry.registerEntityRenderingHandler(
            com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoCombatProjectile.class,
            new EchoProjectileRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntitySealedChest.class, new SealedChestRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityUnsealedChest.class, new UnsealedChestRenderer());
        CubeRuneParticle cubes = new CubeRuneParticle();
        MinecraftForge.EVENT_BUS.register(cubes);
        FMLCommonHandler.instance()
            .bus()
            .register(cubes);
        MinecraftForge.EVENT_BUS.register(owner);
        FMLCommonHandler.instance()
            .bus()
            .register(owner);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        EncounterSignals.tick();
        BossSkillVisuals.tick();
        target = null;
        if (mc.theWorld != null && mc.thePlayer != null
            && mc.theWorld.provider instanceof WorldProviderProsperityRuins) {
            double distance = 55 * 55;
            List<?> entities = mc.theWorld.loadedEntityList;
            for (Object object : entities) {
                if (!(object instanceof EntitySilentKing)) continue;
                EntitySilentKing king = (EntitySilentKing) object;
                int state = king.getEncounterState();
                double d = mc.thePlayer.getDistanceSqToEntity(king);
                if (!king.isDead && state != 0 && state != 4 && d < distance) {
                    target = king;
                    distance = d;
                }
            }
        }
        boolean play = target != null && (target.getEncounterState() == 1 || target.getEncounterState() == 2);
        if (music != null && (mc.theWorld == null || mc.thePlayer == null
            || !(mc.theWorld.provider instanceof WorldProviderProsperityRuins))) {
            music.stop();
            mc.getSoundHandler()
                .stopSound(music);
            music = null;
        }
        if (music != null && music.isDonePlaying()) music = null;
        if (play && music == null) {
            music = new ThroneMusic();
            mc.getSoundHandler()
                .playSound(music);
        }
        if (music != null) music.wanted = play;
    }

    @SubscribeEvent
    public void overlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        com.miaokatze.gtsr.common.dimension.prosperity.encounter.SceneBossSignal scene = EncounterSignals.scene();
        if (scene != null) {
            echoOverlay.drawScene(event, scene);
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null
            || !(mc.theWorld.provider instanceof WorldProviderProsperityRuins)) return;
        if (target != null && !target.isDead && target.worldObj == mc.theWorld) {
            echoOverlay.drawKing(event, target);
            return;
        }
        com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho legacy = EchoBossOverlay.nearest(mc);
        if (legacy != null) echoOverlay.draw(event, legacy);
    }

    static int kingLayerColor(int layers) {
        return COLORS[Math.max(0, layers - 1) % COLORS.length];
    }

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        if (music != null) {
            music.stop();
            Minecraft.getMinecraft()
                .getSoundHandler()
                .stopSound(music);
            music = null;
        }
        target = null;
        BossSkillVisuals.clear();
    }

    private static final class ThroneMusic extends MovingSound {

        boolean wanted = true;

        ThroneMusic() {
            super(new ResourceLocation("gtsr", "silent_king.battle"));
            repeat = true;
            field_147665_h = 0;
            field_147666_i = ISound.AttenuationType.NONE;
            volume = 0.01F; // SoundManager refuses zero-volume starts.
        }

        @Override
        public void update() {
            volume = wanted ? Math.min(0.55F, volume + 0.009F) : Math.max(0, volume - 0.012F);
            if (!wanted && volume <= 0) donePlaying = true;
        }

        void stop() {
            wanted = false;
            donePlaying = true;
        }
    }
}
