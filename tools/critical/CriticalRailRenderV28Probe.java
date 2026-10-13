package com.miaokatze.gtsr.client.critical;

import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.critical.CriticalGeometry;
import com.miaokatze.gtsr.common.critical.CriticalMaterials;

/** Actual chunk handler/Tessellator in an invisible GL surface; no Minecraft/FML/world startup. */
public final class CriticalRailRenderV28Probe {
    private static final int SIZE = 512;
    private static int assertions;
    private static Method face;
    private static Path output;

    private static void check(boolean value, String label) {
        assertions++;
        if (!value) throw new AssertionError(label);
    }

    private static IIcon icon(int index, int count) {
        return (IIcon) Proxy.newProxyInstance(IIcon.class.getClassLoader(), new Class<?>[] { IIcon.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "getMinU" -> index / (float) count;
                case "getMaxU" -> (index + 1) / (float) count;
                case "getMinV" -> 0F;
                case "getMaxV" -> 1F;
                case "getInterpolatedU" -> (float) ((index + (Double) args[0] / 16) / count);
                case "getInterpolatedV" -> (float) ((Double) args[0] / 16);
                case "getIconWidth", "getIconHeight" -> 32;
                case "getIconName" -> "probe_" + index;
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static void camera(double distance, double yaw) {
        GL11.glViewport(0, 0, SIZE, SIZE);
        GL11.glDepthMask(true);
        GL11.glClearColor(0, 0, 0, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, .1F);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);
        GL11.glFrontFace(GL11.GL_CCW);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        // Keep the face sampled well while varying depth precision, independently of low-pixel LOD.
        double near = .05, halfWidth = near * 2.5 / distance;
        GL11.glFrustum(-halfWidth, halfWidth, -halfWidth, halfWidth, near, 512);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslated(0, 0, -distance);
        GL11.glRotated(yaw, 0, 1, 0);
        GL11.glTranslated(-.5, -.5, -.5);
    }

    private static int texture(List<String> paths, int frame, boolean diagnostic) throws Exception {
        ByteBuffer pixels = BufferUtils.createByteBuffer(paths.size() * 32 * 32 * 4);
        List<BufferedImage> images = new ArrayList<>();
        for (String path : paths) {
            if (diagnostic) images.add(null);
            else images.add(ImageIO.read(CriticalRailRenderV28Probe.class.getResourceAsStream(
                "/assets/gtsr/textures/blocks/" + path.split(":", 2)[1] + ".png")));
        }
        for (int y = 0; y < 32; y++) for (int i = 0; i < paths.size(); i++) for (int x = 0; x < 32; x++) {
            BufferedImage image = images.get(i);
            int color = diagnostic ? paths.get(i).contains("emission") ? 0xffff0000 : 0xff0000ff
                : image.getRGB(x, y + Math.min(frame, image.getHeight() / 32 - 1) * 32);
            pixels.put((byte) (color >>> 16)).put((byte) (color >>> 8)).put((byte) color).put((byte) (color >>> 24));
        }
        pixels.flip();
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, paths.size() * 32, 32, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        return id;
    }

    private static byte[] read() {
        GL11.glFinish();
        ByteBuffer buffer = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        GL11.glReadPixels(0, 0, SIZE, SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        check(GL11.glGetError() == GL11.GL_NO_ERROR, "GL error");
        return result;
    }

    private static void save(byte[] data, String name) throws Exception {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int i = (y * SIZE + x) * 4;
            image.setRGB(x, SIZE - y - 1, 0xff000000 | (data[i] & 255) << 16
                | (data[i + 1] & 255) << 8 | data[i + 2] & 255);
        }
        ImageIO.write(image, "png", output.resolve(name + ".png").toFile());
    }

    private static CriticalMaterials.MaterialBlock material() throws Exception {
        Constructor<CriticalMaterials.MaterialBlock> constructor = CriticalMaterials.MaterialBlock.class
            .getDeclaredConstructor(String.class, boolean.class, boolean.class, boolean.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance("v2_loom_guide", false, false, true, true);
    }

    private static JsonObject overlay() throws Exception {
        CriticalMaterials.MaterialBlock material = material();
        List<String> paths = new ArrayList<>();
        IIconRegister register = name -> {
            paths.add(name);
            return icon(paths.size() - 1, 128);
        };
        material.registerBlockIcons(register);
        // Icon slots use a fixed 128-panel atlas so registration order is preserved exactly.
        while (paths.size() < 128) paths.add("gtsr:critical/v2/loom_guide__top");
        Block solid = new Block(Material.rock) {};
        Constructor<?> airConstructor = Class.forName("net.minecraft.block.BlockAir").getDeclaredConstructor();
        airConstructor.setAccessible(true);
        Block air = (Block) airConstructor.newInstance();
        int[] selectedMask = { 0 };
        IBlockAccess world = (IBlockAccess) Proxy.newProxyInstance(IBlockAccess.class.getClassLoader(),
            new Class<?>[] { IBlockAccess.class }, (proxy, method, args) -> switch (method.getName()) {
                case "getBlock" -> {
                    int x = (int) args[0], y = (int) args[1], z = (int) args[2];
                    if (x == 0 && z == 1) yield air;
                    if (x == 0 && z == 0 && (y == -1 && (selectedMask[0] & 1) != 0
                        || y == 1 && (selectedMask[0] & 4) != 0)) yield material;
                    yield solid;
                }
                case "getBlockMetadata", "isBlockProvidingPowerTo" -> 0;
                case "getLightBrightnessForSkyBlocks" -> 0x00f000f0;
                case "getHeight" -> 256;
                default -> method.getReturnType() == boolean.class ? false : null;
            });
        CriticalMaterialRenderer renderer = new CriticalMaterialRenderer();
        int atlas = texture(paths, 0, true);
        JsonArray cases = new JsonArray();
        int missing = 0;
        for (double distance : new double[] { 4, 16, 32, 64, 128, 256 })
            for (double angle : new double[] { 0, 25, 55, 80, 88 }) {
                camera(distance, angle);
                Tessellator.instance.startDrawingQuads();
                check(renderer.renderWorldBlock(world, 0, 0, 0, material, 0, null), "Production handler emits face");
                Tessellator.instance.draw();
                byte[] actual = read();
                int red = 0, blue = 0;
                for (int i = 0; i < actual.length; i += 4) {
                    if ((actual[i] & 255) > 100) red++;
                    if ((actual[i + 2] & 255) > 100) blue++;
                }
                JsonObject scenario = new JsonObject();
                scenario.addProperty("distance", distance);
                scenario.addProperty("yawDegrees", angle);
                scenario.addProperty("emissionPixels", red);
                scenario.addProperty("albedoPixelsMissingEmission", blue);
                cases.add(scenario);
                missing += blue;
                if (distance == 128 && angle == 0) save(actual, "diagnostic-distance128-front");
            }
        GL11.glDeleteTextures(atlas);
        int realCases = 0, differing = 0;
        for (int frame : new int[] { 0, 5, 11 }) {
            atlas = texture(paths, frame, false);
            for (int mask : new int[] { 0, 1, 4, 5 }) {
                selectedMask[0] = mask;
                check(CriticalMaterialTextures.worldMask(world, 0, 0, 0, material, 3) == mask,
                    "Actual CTM neighbor lookup mask=" + mask);
            for (double distance : new double[] { 32, 64, 128 }) for (double angle : new double[] { 0, 25, 55, 80 }) {
                camera(distance, angle);
                Tessellator.instance.startDrawingQuads();
                renderer.renderWorldBlock(world, 0, 0, 0, material, 0, null);
                Tessellator.instance.draw();
                byte[] actual = read();
                // Independent oracle retains real icon lookup and emission mask, but disables depth rejection only
                // for the overlay. One exposed face means this does not admit an occluded back face.
                camera(distance, angle);
                Tessellator.instance.startDrawingQuads();
                Tessellator.instance.setColorOpaque_F(.8F, .8F, .8F);
                face.invoke(null, Tessellator.instance, 0D, 0D, 0D, 3,
                    CriticalMaterialTextures.icon(material.materialId, 3, mask, false, false), 0D);
                Tessellator.instance.draw();
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                Tessellator.instance.startDrawingQuads();
                Tessellator.instance.setColorOpaque_F(1, 1, 1);
                face.invoke(null, Tessellator.instance, 0D, 0D, 0D, 3,
                    CriticalMaterialTextures.icon(material.materialId, 3, mask, true, false), 0D);
                Tessellator.instance.draw();
                byte[] expected = read();
                int difference = 0;
                for (int i = 0; i < actual.length; i += 4)
                    if (actual[i] != expected[i] || actual[i + 1] != expected[i + 1] || actual[i + 2] != expected[i + 2])
                        difference++;
                differing += difference;
                realCases++;
                if (distance == 128 && angle == 0) save(actual, "real-mask" + mask + "-frame" + frame + "-distance128");
            }
            }
            GL11.glDeleteTextures(atlas);
        }
        JsonObject result = new JsonObject();
        result.add("diagnosticCases", cases);
        result.addProperty("diagnosticMissingEmissionPixels", missing);
        result.addProperty("realMaskFrameCases", realCases);
        result.addProperty("realMaskPixelsDifferentFromDepthIndependentOracle", differing);
        result.addProperty("scope", "Actual renderWorldBlock/renderV2, real MaterialBlock, all valid side CTM masks 0,1,4,5; diagnostic solid masks plus native PNG frames 0,5,11. Fixture atlas/IBlockAccess/lightmap plumbing.");
        return result;
    }

    private static void winding() throws Exception {
        Field cornersField = CriticalMeshCache.class.getDeclaredField("CORNERS");
        cornersField.setAccessible(true);
        double[][][] corners = (double[][][]) cornersField.get(null);
        IIcon icon = icon(0, 1);
        for (int side = 0; side < 6; side++) {
            Capture capture = new Capture();
            face.invoke(null, capture, 0D, 0D, 0D, side, icon, 0D);
            for (double[][] vertices : List.of(corners[side], capture.vertices.toArray(double[][]::new))) {
                double[] a = new double[3], b = new double[3];
                for (int axis = 0; axis < 3; axis++) {
                    a[axis] = vertices[1][axis] - vertices[0][axis];
                    b[axis] = vertices[2][axis] - vertices[0][axis];
                }
                double[] cross = { a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2],
                    a[0] * b[1] - a[1] * b[0] };
                double dot = 0;
                for (int axis = 0; axis < 3; axis++) dot += cross[axis] * CriticalMaterialTextures.NORMALS[side][axis];
                check(dot > 0, "Actual native/chunk outward winding side=" + side);
            }
        }
        Class<?> group = Class.forName(CriticalGroupVisibility.class.getName() + "$Group");
        Constructor<?> constructor = group.getDeclaredConstructor(String.class, List.class);
        constructor.setAccessible(true);
        Field loom = group.getDeclaredField("loom");
        loom.setAccessible(true);
        check(loom.getBoolean(constructor.newInstance("v2_v27_loom:test", List.of())), "Frozen V27 loom ownership");
    }

    public static void main(String[] args) throws Exception {
        output = Path.of(args[0]);
        face = CriticalMaterialRenderer.class.getDeclaredMethod("face", Tessellator.class, double.class, double.class,
            double.class, int.class, IIcon.class, double.class);
        face.setAccessible(true);
        Pbuffer surface = new Pbuffer(SIZE, SIZE, new PixelFormat(8, 24, 0), null, null);
        try {
            surface.makeCurrent();
            winding();
            JsonObject report = new JsonObject();
            report.addProperty("renderer", GL11.glGetString(GL11.GL_RENDERER));
            report.addProperty("vendor", GL11.glGetString(GL11.GL_VENDOR));
            report.addProperty("version", GL11.glGetString(GL11.GL_VERSION));
            report.addProperty("depthBits", GL11.glGetInteger(GL11.GL_DEPTH_BITS));
            report.addProperty("glExecuted", true);
            report.addProperty("minecraftStarted", false);
            report.addProperty("fmlStarted", false);
            report.addProperty("near", .05);
            report.addProperty("far", 512);
            report.addProperty("depthFunction", "GL_LEQUAL (Minecraft 1.7.10 default)");
            report.addProperty("surface", "Invisible 512x512 LWJGL2 Pbuffer, no Display/client/server");
            report.addProperty("materialRendererCodeSource", CriticalMaterialRenderer.class.getProtectionDomain().getCodeSource().getLocation().toString());
            report.addProperty("materialBlockCodeSource", CriticalMaterials.MaterialBlock.class.getProtectionDomain().getCodeSource().getLocation().toString());
            report.addProperty("materialTexturesCodeSource", CriticalMaterialTextures.class.getProtectionDomain().getCodeSource().getLocation().toString());
            report.addProperty("meshCacheCodeSource", CriticalMeshCache.class.getProtectionDomain().getCodeSource().getLocation().toString());
            report.addProperty("tessellatorCodeSource", Tessellator.class.getProtectionDomain().getCodeSource().getLocation().toString());
            JsonObject overlay = overlay();
            report.add("overlay", overlay);
            report.addProperty("assertions", assertions);
            report.addProperty("outwardFaceChecks", 12);
            boolean passed = overlay.get("diagnosticMissingEmissionPixels").getAsInt() == 0
                && overlay.get("realMaskPixelsDifferentFromDepthIndependentOracle").getAsInt() == 0;
            report.addProperty("status", passed ? "PASS" : "FAIL");
            Files.writeString(output.resolve("runtime.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            System.out.println(report);
            if (!passed) System.exit(2);
        } finally {
            surface.destroy();
        }
    }

    private static final class Capture extends Tessellator {
        private final List<double[]> vertices = new ArrayList<>();
        @Override
        public void addVertexWithUV(double x, double y, double z, double u, double v) {
            vertices.add(new double[] { x, y, z });
        }
    }
}
