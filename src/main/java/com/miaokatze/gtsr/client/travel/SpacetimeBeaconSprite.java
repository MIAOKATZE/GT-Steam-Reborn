package com.miaokatze.gtsr.client.travel;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Like vanilla's compass, updates a complete item-atlas sprite instead of drawing a second depth layer. */
@SideOnly(Side.CLIENT)
public final class SpacetimeBeaconSprite extends TextureAtlasSprite {

    private static final int FRAME_COUNT = 128;

    public SpacetimeBeaconSprite(String name) {
        super(name);
    }

    @Override
    public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
        return true;
    }

    @Override
    public boolean load(IResourceManager manager, ResourceLocation location) {
        BufferedImage base = read(manager, location, "");
        BufferedImage needle = read(manager, location, "_needle");
        width = base.getWidth();
        height = base.getHeight();
        if (width != height || needle.getWidth() != width || needle.getHeight() != height) {
            throw new IllegalArgumentException("Beacon dial and needle must be matching square textures: " + location);
        }
        int[] dial = base.getRGB(0, 0, width, height, null, 0, width);
        int[] pointer = needle.getRGB(0, 0, width, height, null, 0, width);
        List<int[][]> frames = new ArrayList<>(FRAME_COUNT);
        double center = width / 2D;
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            int[] pixels = dial.clone();
            double radians = frame * Math.PI * 2 / FRAME_COUNT;
            double cos = Math.cos(radians), sin = Math.sin(radians);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    // Inverse nearest-neighbour rotation: PNG Y points down, so positive angles turn clockwise.
                    double dx = x + .5 - center, dy = y + .5 - center;
                    int sx = (int) Math.floor(center + dx * cos + dy * sin);
                    int sy = (int) Math.floor(center - dx * sin + dy * cos);
                    if (sx < 0 || sx >= width || sy < 0 || sy >= height) continue;
                    int foreground = pointer[sy * width + sx];
                    int alpha = foreground >>> 24;
                    if (alpha == 255) pixels[y * width + x] = foreground;
                    else if (alpha != 0) pixels[y * width + x] = over(foreground, pixels[y * width + x]);
                }
            }
            frames.add(new int[][] { pixels });
        }
        setFramesTextureData(frames);
        frameCounter = 0;
        tickCounter = 0;
        // TextureMap's custom-loader contract: false enters the normal stitch/mipmap/upload path.
        return false;
    }

    private static BufferedImage read(IResourceManager manager, ResourceLocation location, String suffix) {
        ResourceLocation resource = new ResourceLocation(
            location.getResourceDomain(),
            "textures/items/" + location.getResourcePath() + suffix + ".png");
        try (InputStream stream = manager.getResource(resource)
            .getInputStream()) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) throw new IOException("Unsupported image");
            return image;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load beacon sprite " + resource, exception);
        }
    }

    private static int over(int foreground, int background) {
        int alpha = foreground >>> 24;
        int inverse = 255 - alpha;
        int backgroundAlpha = background >>> 24;
        int outputAlpha = alpha + backgroundAlpha * inverse / 255;
        int result = outputAlpha << 24;
        for (int shift = 0; shift <= 16; shift += 8) {
            int color = (((foreground >> shift) & 255) * alpha
                + ((background >> shift) & 255) * backgroundAlpha * inverse / 255) / outputAlpha;
            result |= color << shift;
        }
        return result;
    }

    @Override
    public void generateMipmaps(int levels) {
        // TextureUtil indexes every requested level before generating missing data. A custom
        // loader must supply those null slots, just as vanilla's loadSprite image array does.
        List<int[][]> frames = new ArrayList<>(getFrameCount());
        for (int frame = 0; frame < getFrameCount(); frame++) {
            frames.add(Arrays.copyOf(getFrameTextureData(frame), levels + 1));
        }
        setFramesTextureData(frames);
        super.generateMipmaps(levels);
    }

    @Override
    public boolean hasAnimationMetadata() {
        // Keep all frames and join TextureMap's tick list even without an external .mcmeta file.
        return true;
    }

    @Override
    public void updateAnimation() {
        if (getFrameCount() == 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        boolean charging = mc.thePlayer != null
            && SpacetimeBeaconRenderer.charging(mc.thePlayer, mc.thePlayer.getHeldItem());
        float angle = BeaconNavigationClient.angle(charging);
        int next = Math.floorMod(Math.round(angle * FRAME_COUNT / 360F), FRAME_COUNT);
        if (next == frameCounter) return;
        frameCounter = next;
        TextureUtil.uploadTextureMipmap(getFrameTextureData(next), width, height, originX, originY, false, false);
    }
}
