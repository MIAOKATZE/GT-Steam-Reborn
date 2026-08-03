package com.miaokatze.gtsr.common.util;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 客户端程序化纹理管线：基于 SteamEntangledSingularity.png (16x144, 9 帧垂直动画)
 * 生成「临界蒸汽纠缠奇点」图标并注入物品图集。
 * <p>
 * 处理流程：逐帧灰度化 (0.299R+0.587G+0.114B) 并整体压暗 (x0.8) → 9 帧扩展为 27 帧
 * (每 3 帧一组: 原帧 + 2 个 1-2 像素随机偏移副本, 抖动效果) → 经 Forge 自定义
 * 纹理加载钩子 (hasCustomLoader/load) 在缝合前填入帧数据。
 * <p>
 * 注入时机: 物品图集 TextureMap.registerIcons() 阶段 (Item.registerIcons 委托调用
 * {@link #registerIcon(IIconRegister)}), 随后 loadTextureAtlas 的缝合循环检测到
 * hasCustomLoader 后调用 {@link #load(IResourceManager, ResourceLocation)}。
 */
@SideOnly(Side.CLIENT)
public class CriticalSingularityTexture extends TextureAtlasSprite {

    /** 注入到图集 (mapRegisteredSprites) 与物品 setTextureName 使用的图标名 */
    public static final String ICON_NAME = "gtsr:CriticalSteamEntangledSingularity";

    /** 源贴图: 蒸汽纠缠奇点 16x144, 9 帧垂直排列 */
    private static final ResourceLocation SOURCE_TEXTURE = new ResourceLocation(
        "gtsr",
        "textures/items/SteamEntangledSingularity.png");

    private static final int FRAME_SIZE = 16;
    private static final int SOURCE_FRAME_COUNT = 9;
    private static final int TOTAL_FRAME_COUNT = 27;

    /**
     * 27 帧动画时长表 (单位: tick)。原动画 9 帧 x frametime 5 = 45 tick,
     * 新动画总时长 = 27 x 1 + 3 x 2 = 30 tick, 快约 50%。每组的原帧 (i % 9 == 0)
     * 停留 2 tick, 抖动副本各 1 tick。
     */
    private static final int[] FRAME_TIMES = buildFrameTimes();

    /** 抖动偏移 (固定种子, 每次图集重载保持一致): [源帧][副本序号 0/1][x, y], 幅度 1-2 像素 */
    private static final int[][][] JITTER_OFFSETS = buildJitterOffsets();

    private static int[] buildFrameTimes() {
        int[] times = new int[TOTAL_FRAME_COUNT];
        for (int i = 0; i < times.length; i++) {
            times[i] = (i % SOURCE_FRAME_COUNT == 0) ? 2 : 1;
        }
        return times;
    }

    private static int[][][] buildJitterOffsets() {
        Random random = new Random(0x5EEDC0DEL);
        int[][][] offsets = new int[SOURCE_FRAME_COUNT][2][2];
        for (int i = 0; i < SOURCE_FRAME_COUNT; i++) {
            for (int j = 0; j < 2; j++) {
                int dx = random.nextBoolean() ? 1 + random.nextInt(2) : -(1 + random.nextInt(2));
                int dy = random.nextBoolean() ? 1 + random.nextInt(2) : -(1 + random.nextInt(2));
                offsets[i][j][0] = dx;
                offsets[i][j][1] = dy;
            }
        }
        return offsets;
    }

    private CriticalSingularityTexture(String iconName) {
        super(iconName);
    }

    /**
     * 由物品的 registerIcons 调用: 创建自定义 sprite 并预置到物品图集,
     * 保证其参与本次缝合。返回用于 itemIcon 的 IIcon。
     */
    public static IIcon registerIcon(IIconRegister iconRegister) {
        if (!(iconRegister instanceof TextureMap)) {
            return iconRegister.registerIcon(ICON_NAME);
        }
        TextureMap textureMap = (TextureMap) iconRegister;
        CriticalSingularityTexture sprite = new CriticalSingularityTexture(ICON_NAME);
        if (!textureMap.setTextureEntry(ICON_NAME, sprite)) {
            // 已有同名条目 (极少数冲突), 回退到图集中已有的 sprite
            return textureMap.getTextureExtry(ICON_NAME);
        }
        return sprite;
    }

    @Override
    public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
        return true;
    }

    /**
     * Forge 自定义加载钩子: 程序生成 27 帧像素数据, 不读取磁盘 PNG。
     * 返回 false 表示「数据已就绪, 无需默认 PNG 加载」。
     */
    @Override
    public boolean load(IResourceManager manager, ResourceLocation location) {
        List<int[][]> frames = new ArrayList<>(TOTAL_FRAME_COUNT);
        int[] srcPixels = null;
        try {
            IResource resource = manager.getResource(SOURCE_TEXTURE);
            InputStream in = resource.getInputStream();
            try {
                BufferedImage image = ImageIO.read(in);
                if (image != null && image.getWidth() >= FRAME_SIZE
                    && image.getHeight() >= FRAME_SIZE * SOURCE_FRAME_COUNT) {
                    srcPixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
                }
            } finally {
                in.close();
            }
        } catch (IOException ignored) {
            // 源贴图缺失时回退为深色占位帧, 避免图集加载崩溃
        }
        for (int i = 0; i < TOTAL_FRAME_COUNT; i++) {
            int[] pixels;
            if (srcPixels == null) {
                pixels = fallbackFrame();
            } else {
                int sourceFrame = i / 3;
                int[] base = grayscaleFrame(srcPixels, sourceFrame);
                int copy = i % 3;
                pixels = (copy == 0) ? base
                    : shiftedCopy(
                        base,
                        JITTER_OFFSETS[sourceFrame][copy - 1][0],
                        JITTER_OFFSETS[sourceFrame][copy - 1][1]);
            }
            frames.add(new int[][] { pixels });
        }
        setFramesTextureData(frames);
        setIconWidth(FRAME_SIZE);
        setIconHeight(FRAME_SIZE);
        frameCounter = 0;
        tickCounter = 0;
        return false;
    }

    /** 与内置动画 sprite 相同: 常驻图集动画列表, 每 tick 触发 updateAnimation */
    @Override
    public boolean hasAnimationMetadata() {
        return true;
    }

    /**
     * 逐 tick 推进 27 帧动画。1.7.10 内置管线会把 mcmeta 的显式逐帧时长丢弃,
     * 因此这里自行按 FRAME_TIMES 控制播放速度。
     */
    @Override
    public void updateAnimation() {
        if (framesTextureData == null || framesTextureData.isEmpty()) {
            return;
        }
        int frameTime = FRAME_TIMES[frameCounter % FRAME_TIMES.length];
        if (++tickCounter < frameTime) {
            return;
        }
        tickCounter = 0;
        frameCounter = (frameCounter + 1) % framesTextureData.size();
        TextureUtil
            .uploadTextureMipmap(framesTextureData.get(frameCounter), width, height, originX, originY, false, false);
    }

    /** 灰度化 + 压暗, 保留 alpha */
    private static int[] grayscaleFrame(int[] srcPixels, int frameIndex) {
        int[] out = new int[FRAME_SIZE * FRAME_SIZE];
        int rowOffset = frameIndex * FRAME_SIZE;
        for (int y = 0; y < FRAME_SIZE; y++) {
            for (int x = 0; x < FRAME_SIZE; x++) {
                int argb = srcPixels[(rowOffset + y) * FRAME_SIZE + x];
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                int gray = (int) ((0.299D * r + 0.587D * g + 0.114D * b) * 0.8D);
                if (gray > 255) {
                    gray = 255;
                }
                out[y * FRAME_SIZE + x] = (a << 24) | (gray << 16) | (gray << 8) | gray;
            }
        }
        return out;
    }

    /** 按固定偏移复制帧像素 (越界区域保持透明), 形成颤动效果 */
    private static int[] shiftedCopy(int[] base, int dx, int dy) {
        int[] out = new int[FRAME_SIZE * FRAME_SIZE];
        for (int y = 0; y < FRAME_SIZE; y++) {
            int sy = y - dy;
            if (sy < 0 || sy >= FRAME_SIZE) {
                continue;
            }
            for (int x = 0; x < FRAME_SIZE; x++) {
                int sx = x - dx;
                if (sx < 0 || sx >= FRAME_SIZE) {
                    continue;
                }
                out[y * FRAME_SIZE + x] = base[sy * FRAME_SIZE + sx];
            }
        }
        return out;
    }

    private static int[] fallbackFrame() {
        int[] out = new int[FRAME_SIZE * FRAME_SIZE];
        for (int i = 0; i < out.length; i++) {
            out[i] = 0xFF303030;
        }
        return out;
    }
}
