package com.miaokatze.gtsr.client.critical;

import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.critical.CriticalGeometry.Voxel;

import net.minecraft.util.IIcon;

/** Real production list compilation and real PNG rasterization; no Minecraft client or FML startup. */
public final class Emission39Probe {
    private static final int SIZE = 32;
    private static final Method COMPILE;
    private static final Constructor<?> FACE, VOXEL;

    static {
        try {
            COMPILE = CriticalMeshCache.class.getDeclaredMethod("compileList", List.class, boolean.class);
            COMPILE.setAccessible(true);
            FACE = Arrays.stream(Class.forName(CriticalMeshCache.class.getName() + "$Face").getDeclaredConstructors())
                .filter(constructor -> constructor.getParameterCount() == 5).findFirst().orElseThrow();
            FACE.setAccessible(true);
            VOXEL = Voxel.class.getDeclaredConstructor(int.class, int.class, int.class,
                Class.forName("net.minecraft.block.Block"), int.class, String.class, String.class, String.class);
            VOXEL.setAccessible(true);
        } catch (ReflectiveOperationException problem) {
            throw new ExceptionInInitializerError(problem);
        }
    }

    private static IIcon icon(boolean mask) {
        return (IIcon) Proxy.newProxyInstance(IIcon.class.getClassLoader(), new Class<?>[] { IIcon.class },
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getInterpolatedU" -> (float) ((mask ? .5 : 0) + (double) arguments[0] / 32);
                case "getInterpolatedV" -> (float) ((double) arguments[0] / 16);
                case "getIconName" -> mask ? "actual-mask" : "actual-albedo";
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static int texture(BufferedImage image) {
        ByteBuffer pixels = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 4);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int pixel = image.getRGB(x, y);
            pixels.put((byte) (pixel >> 16)).put((byte) (pixel >> 8)).put((byte) pixel).put((byte) (pixel >> 24));
        }
        pixels.flip();
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, image.getWidth(), image.getHeight(), 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        return texture;
    }

    private static void light(int value) {
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        BufferedImage pixel = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        pixel.setRGB(0, 0, 0xff000000 | value << 16 | value << 8 | value);
        int texture = texture(pixel);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        // Texture 1 has a single texel; no coordinate interpolation or world sampling is simulated.
        GL11.glCallList(value == 255 ? emissionList : albedoList);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL11.glDeleteTextures(texture);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private static int albedoList, emissionList;

    private static JsonObject render(Path assets, Path out, String name, String albedo, String mask, float strength,
        int worldLight) throws Exception {
        BufferedImage atlas = new BufferedImage(SIZE * 2, SIZE, BufferedImage.TYPE_INT_ARGB);
        BufferedImage body = ImageIO.read(assets.resolve(albedo + ".png").toFile());
        BufferedImage glow = mask == null ? null : ImageIO.read(assets.resolve(mask + ".png").toFile());
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            atlas.setRGB(x, y, body.getRGB(x, y));
            if (glow != null) atlas.setRGB(x + SIZE, y, glow.getRGB(x, y));
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int atlasTexture = texture(atlas);
        Object voxel = VOXEL.newInstance(0, 0, 0, null, 0, "fixture", "fixture", "fixture");
        Object classified = FACE.getParameterTypes()[4] == boolean.class ? strength > 0 : Float.valueOf(strength);
        Object face = FACE.newInstance(voxel, 1, icon(false), mask == null ? null : icon(true), classified);
        albedoList = (int) COMPILE.invoke(null, List.of(face), false);
        emissionList = (int) COMPILE.invoke(null, List.of(face), true);
        GL11.glViewport(0, 0, SIZE, SIZE);
        GL11.glClearColor(0, 0, 0, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, 1, -1, 0, -10, 10);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glRotated(90, 1, 0, 0);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, .01F);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        light(worldLight);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(-1, -1);
        light(255);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glDepthMask(true);
        GL11.glFinish();
        ByteBuffer pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        GL11.glReadPixels(0, 0, SIZE, SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        int nearWhite = 0, maxError = 0, allPixelMaxError = 0, unmasked = 0, minChannel = 255;
        long rgbSum = 0;
        // Top-face UV basis gives one-to-one source texels at these pixel centers.
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int index = (y * SIZE + x) * 4;
            int r = pixels.get(index) & 255, g = pixels.get(index + 1) & 255, b = pixels.get(index + 2) & 255;
            image.setRGB(x, y, 0xff000000 | r << 16 | g << 8 | b);
            rgbSum += r + g + b;
            minChannel = Math.min(minChannel, Math.min(r, Math.min(g, b)));
            if (Math.min(r, Math.min(g, b)) >= 240) nearWhite++;
            int source = body.getRGB(x, y), emission = glow == null ? 0 : glow.getRGB(x, y);
            for (int channel = 0; channel < 3; channel++) {
                int original = source >> (16 - channel * 8) & 255;
                int maskChannel = emission >> (16 - channel * 8) & 255;
                double relief = 0;
                double maskColor = Math.min(1, strength) * maskChannel * (emission >>> 24) / 255D;
                int expected = Math.min(255, (int) Math.round(original * (worldLight / 255D + relief) + maskColor));
                allPixelMaxError = Math.max(allPixelMaxError, Math.abs((pixels.get(index + channel) & 255) - expected));
            }
            if ((emission >>> 24) == 0) {
                unmasked++;
                double relief = 0;
                for (int channel = 0; channel < 3; channel++) {
                    int actual = pixels.get(index + channel) & 255;
                    int original = source >> (16 - channel * 8) & 255;
                    int expected = Math.min(255, (int) Math.round(original * (worldLight / 255D + relief)));
                    maxError = Math.max(maxError, Math.abs(actual - expected));
                }
            }
        }
        ImageIO.write(image, "png", out.resolve(name + ".png").toFile());
        JsonObject result = new JsonObject();
        result.addProperty("case", name);
        result.addProperty("worldLight", worldLight);
        result.addProperty("nearWhitePixels", nearWhite);
        result.addProperty("rgbMean", rgbSum / (SIZE * SIZE * 3D));
        result.addProperty("minChannel", minChannel);
        result.addProperty("unmaskedTexels", unmasked);
        result.addProperty("unmaskedWorldLightMaxError", maxError);
        result.addProperty("bodyAndDeclaredMaskStrengthMaxError", allPixelMaxError);
        result.addProperty("glError", GL11.glGetError());
        GL11.glDeleteLists(albedoList, 1);
        GL11.glDeleteLists(emissionList, 1);
        GL11.glDeleteTextures(atlasTexture);
        return result;
    }

    public static void main(String[] arguments) throws Exception {
        Path assets = Paths.get(arguments[0]), out = Paths.get(arguments[1]);
        Files.createDirectories(out);
        Pbuffer surface = new Pbuffer(SIZE, SIZE, new PixelFormat(8, 24, 0), null, null);
        surface.makeCurrent();
        try {
            JsonArray cases = new JsonArray();
            for (int light : new int[] { 51, 204 }) {
                cases.add(render(assets, out, "contact-mask-light" + light, "superconducting_contact",
                    "superconducting_contact_emission", 1F, light));
                cases.add(render(assets, out, "contact-no-mask-light" + light, "superconducting_contact__top",
                    null, 1F, light));
                cases.add(render(assets, out, "guide-mask-light" + light, "loom_guide",
                    "loom_guide_emission", .85F, light));
                cases.add(render(assets, out, "gravity-mask-light" + light, "gravity_modulator",
                    "gravity_modulator_emission", 1.15F, light));
                cases.add(render(assets, out, "nonemissive-light" + light, "reinforced_panel", null, 0F, light));
            }
            JsonObject report = new JsonObject();
            report.addProperty("renderer", GL11.glGetString(GL11.GL_RENDERER));
            report.addProperty("meshCodeSource", CriticalMeshCache.class.getProtectionDomain().getCodeSource().getLocation().toString());
            report.addProperty("tessellatorCodeSource", net.minecraft.client.renderer.Tessellator.class.getProtectionDomain().getCodeSource().getLocation().toString());
            report.addProperty("scope", "Production CriticalMeshCache.compileList + patched MC Tessellator + real PNG first frames, LWJGL Pbuffer. Synthetic constant light texture; no game client/world/atlas ticking.");
            report.add("cases", cases);
            Files.writeString(out.resolve("runtime.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            System.out.println(report);
        } finally {
            surface.destroy();
        }
    }
}
