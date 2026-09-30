package com.miaokatze.gtsr.client.encounter;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntityResidualOathguard;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntitySilentKing;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntitySealedChest;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Local HUD/music owner. Never sets vanilla BossStatus or cancels another mod's overlay. */
public final class EncounterClient extends Gui implements IResourceManagerReloadListener {

    private static final ResourceLocation FRAME = new ResourceLocation("gtsr", "textures/gui/silent_king_frame.png");
    private static final int[] COLORS = { 0xCF554D, 0xD88B42, 0xDFC34F, 0x9AB45B, 0x56B889, 0x55B7C4, 0x618CDD,
        0x936ADB, 0xBE69BF, 0xDE849E };
    private EntitySilentKing target;
    private ThroneMusic music;

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
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntitySealedChest.class, new SealedChestRenderer());
        MinecraftForge.EVENT_BUS.register(owner);
        FMLCommonHandler.instance()
            .bus()
            .register(owner);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
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
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || target == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        float health = Math.max(0, Math.min(1000, target.getHealth()));
        int layers = (int) Math.ceil(health / 100.0F);
        float segment = layers == 0 ? 0 : (health - (layers - 1) * 100) / 100;
        int x = (event.resolution.getScaledWidth() - 256) / 2;
        int y = BossStatus.statusBarTime > 0 ? 42 : 17;
        try (GlScope scope = new GlScope()) {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            drawRect(x + 32, y + 13, x + 224, y + 23, 0xFF151918);
            int color = COLORS[Math.max(0, layers - 1) % COLORS.length];
            drawRect(x + 33, y + 14, x + 33 + Math.round(190 * segment), y + 22, 0xFF000000 | color);
            drawRect(x + 33, y + 14, x + 33 + Math.round(190 * segment), y + 16, 0x66000000 | 0xFFFFFF);
            GL11.glColor4f(1, 1, 1, 1);
            mc.getTextureManager()
                .bindTexture(FRAME);
            func_152125_a(x, y, 0, 0, 256, 40, 256, 40, 256, 40);
            String name = "（旧日虚影）缄王";
            String count = "×" + layers;
            int countWidth = mc.fontRenderer.getStringWidth(count);
            int countX = Math.max(4, Math.min(x + 260, event.resolution.getScaledWidth() - countWidth - 4));
            drawRect(countX - 2, y + 12, countX + countWidth + 2, y + 24, 0xCC151918);
            mc.fontRenderer.drawStringWithShadow(count, countX, y + 14, 0xE9D7AD);
            mc.fontRenderer.drawStringWithShadow(
                name,
                (event.resolution.getScaledWidth() - mc.fontRenderer.getStringWidth(name)) / 2,
                y - 9,
                0xE9D7AD);
            if (target.getEncounterState() == 1) {
                String caption = "王座正在苏醒";
                mc.fontRenderer.drawStringWithShadow(
                    caption,
                    (event.resolution.getScaledWidth() - mc.fontRenderer.getStringWidth(caption)) / 2,
                    y + 31,
                    0xCCA977);
            }
        }
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
