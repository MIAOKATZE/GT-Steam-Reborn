package com.miaokatze.gtsr.client.machinevisual;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;

import javax.imageio.ImageIO;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The native steel gearbox recess inside a robust tungstensteel casing, composed once per atlas load. */
@SideOnly(Side.CLIENT)
public final class TungstenSteelGearboxSprite extends TextureAtlasSprite {

    private static final String ICON_NAME = "gtsr:machine_casing_gearbox_tungstensteel";

    private TungstenSteelGearboxSprite() {
        super(ICON_NAME);
    }

    public static IIcon register(IIconRegister register) {
        if (!(register instanceof TextureMap atlas)) {
            throw new IllegalArgumentException("Tungstensteel gearbox requires the block texture atlas");
        }
        TungstenSteelGearboxSprite sprite = new TungstenSteelGearboxSprite();
        if (!atlas.setTextureEntry(ICON_NAME, sprite)) return atlas.getTextureExtry(ICON_NAME);
        return sprite;
    }

    @Override
    public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
        return true;
    }

    @Override
    public boolean load(IResourceManager manager, ResourceLocation location) {
        BufferedImage steelGear = read(manager, "MACHINE_CASING_GEARBOX_STEEL");
        BufferedImage casing = read(manager, "MACHINE_CASING_ROBUST_TUNGSTENSTEEL");
        BufferedImage image = compose(steelGear, casing);
        width = image.getWidth();
        height = image.getHeight();
        setFramesTextureData(
            Collections.singletonList(new int[][] { image.getRGB(0, 0, width, height, null, 0, width) }));
        // Forge's custom loader returns false to continue normal atlas stitching and upload.
        return false;
    }

    static BufferedImage compose(BufferedImage steelGear, BufferedImage casing) {
        if (steelGear.getWidth() != steelGear.getHeight() || casing.getWidth() != casing.getHeight()) {
            throw new IllegalArgumentException("Gearbox source textures must be square");
        }
        int size = Math.max(steelGear.getWidth(), casing.getWidth());
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                // GT's 16-pixel layout reserves [3,13) for the complete recess and steel gear.
                boolean recess = x * 16 >= size * 3 && x * 16 < size * 13 && y * 16 >= size * 3 && y * 16 < size * 13;
                BufferedImage source = recess ? steelGear : casing;
                image.setRGB(x, y, source.getRGB(x * source.getWidth() / size, y * source.getHeight() / size));
            }
        }
        return image;
    }

    private static BufferedImage read(IResourceManager manager, String name) {
        ResourceLocation source = new ResourceLocation("gregtech", "textures/blocks/iconsets/" + name + ".png");
        try (InputStream stream = manager.getResource(source)
            .getInputStream()) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) throw new IOException("Unsupported image");
            return image;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load gearbox texture " + source, exception);
        }
    }

    @Override
    public void generateMipmaps(int levels) {
        // Vanilla indexes every mip level before generating it; custom loaders supply the null slots.
        setFramesTextureData(Collections.singletonList(Arrays.copyOf(getFrameTextureData(0), levels + 1)));
        super.generateMipmaps(levels);
    }
}
