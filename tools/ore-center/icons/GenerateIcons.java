// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import javax.imageio.ImageIO;

/** JDK-only, reproducible 16px production icons from immutable 32px sources. */
public final class GenerateIcons {

    private static final Path ROOT = Path.of("")
        .toAbsolutePath();
    private static final Path TOOL = ROOT.resolve("tools/ore-center/icons");
    private static final Path SOURCE = TOOL.resolve("source-32");
    private static final Path TARGET = ROOT.resolve("src/main/resources/assets/gtsr/textures/items/orecenter");
    private static final Path REVIEW = TOOL.resolve("review");

    public static void main(String[] args) throws Exception {
        boolean check = args.length == 1 && args[0].equals("--check");
        if (args.length > 0 && !check)
            throw new IllegalArgumentException("Usage: java tools/ore-center/icons/GenerateIcons.java [--check]");
        List<Path> sources;
        try (var paths = Files.list(SOURCE)) {
            sources = paths.filter(
                p -> p.toString()
                    .endsWith(".png"))
                .sorted()
                .toList();
        }
        if (sources.size() != 54) throw new IOException("Expected exactly 54 source PNGs");
        try (var paths = Files.list(TARGET)) {
            if (paths.filter(
                p -> p.toString()
                    .endsWith(".png"))
                .count() != 54) throw new IOException("Expected exactly 54 target PNGs");
        }
        Files.createDirectories(REVIEW);
        StringBuilder report = new StringBuilder(
            "name\tsource_size\tsource_sha256\toutput_size\toutput_sha256\ttransparent\tpartial_alpha\topaque\n");
        BufferedImage sheet = new BufferedImage(1008, 6 * 94, BufferedImage.TYPE_INT_RGB);
        Graphics2D sg = sheet.createGraphics();
        sg.setColor(new Color(0x20252c));
        sg.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        sg.setFont(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 11));
        sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int i = 0;
        for (Path source : sources) {
            String name = source.getFileName()
                .toString();
            BufferedImage original = ImageIO.read(source.toFile());
            if (original.getWidth() != 32 || original.getHeight() != 32
                || !original.getColorModel()
                    .hasAlpha())
                throw new IOException("Invalid 32px RGBA source: " + name);
            BufferedImage result = name.startsWith("shell-") ? shell(Integer.parseInt(name.substring(6, 7)))
                : half(original);
            Path output = TARGET.resolve(name);
            if (!check) ImageIO.write(result, "png", output.toFile());
            BufferedImage actual = ImageIO.read(output.toFile());
            if (actual.getWidth() != 16 || actual.getHeight() != 16
                || !actual.getColorModel()
                    .hasAlpha())
                throw new IOException("Invalid 16px RGBA output: " + name);
            int transparent = 0, partial = 0, opaque = 0;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                if (actual.getRGB(x, y) != result.getRGB(x, y))
                    throw new IOException("Stale generated output: " + name);
                int alpha = actual.getRGB(x, y) >>> 24;
                if (alpha == 0) transparent++;
                else if (alpha == 255) opaque++;
                else partial++;
            }
            if (transparent == 0 || opaque == 0) throw new IOException("Missing transparent/opaque pixels: " + name);
            report.append(name)
                .append("\t32x32\t")
                .append(sha(source))
                .append("\t16x16\t")
                .append(sha(output))
                .append('\t')
                .append(transparent)
                .append('\t')
                .append(partial)
                .append('\t')
                .append(opaque)
                .append('\n');
            int sx = (i % 9) * 112, sy = (i / 9) * 94;
            checker(sg, sx + 6, sy + 7, 96, 64);
            sg.drawImage(original, sx + 6, sy + 7, 64, 64, null);
            sg.drawImage(actual, sx + 70, sy + 7, 32, 32, null);
            sg.setColor(Color.WHITE);
            sg.drawString(name.substring(0, name.length() - 4), sx + 5, sy + 87);
            i++;
        }
        sg.dispose();
        ImageIO.write(
            sheet,
            "png",
            REVIEW.resolve("all-before-after.png")
                .toFile());
        Files.writeString(REVIEW.resolve("verification.tsv"), report);
        shellReview();
        System.out.println(
            (check ? "Verified" : "Generated and verified")
                + " 54 icons: 51 alpha-weighted 2x downsamples + 3 geometric cube shells; all 16x16 RGBA.");
    }

    /** Exact 2x2 area reduction; RGB weighted by alpha prevents dark fringes. */
    private static BufferedImage half(BufferedImage source) {
        BufferedImage result = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int a = 0, r = 0, g = 0, b = 0;
            for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) {
                int c = source.getRGB(x * 2 + dx, y * 2 + dy), ca = c >>> 24;
                a += ca;
                r += ((c >>> 16) & 255) * ca;
                g += ((c >>> 8) & 255) * ca;
                b += (c & 255) * ca;
            }
            int color = a == 0 ? 0
                : (((a + 2) / 4) << 24) | (((r + a / 2) / a) << 16) | (((g + a / 2) / a) << 8) | ((b + a / 2) / a);
            result.setRGB(x, y, color);
        }
        return result;
    }

    /** Full-height dimetric cube: top diamond plus two equally tall side faces. */
    private static BufferedImage shell(int tier) {
        int[][] palettes = { { 0xc5d1d7, 0x8296a1, 0x4a5f6b, 0xe5edf0, 0x293b47 },
            { 0xf3f6f7, 0xb8c8cf, 0x7c929e, 0xffffff, 0x435967 },
            { 0xc6c7e7, 0x898eb7, 0x535d8b, 0xe9e8ff, 0x303957 } };
        int[] p = palettes[tier - 1];
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        polygon(g, p[1], new int[] { 0, 7, 7, 0 }, new int[] { 4, 8, 15, 11 });
        polygon(g, p[2], new int[] { 7, 14, 14, 7 }, new int[] { 8, 4, 11, 15 });
        polygon(g, p[0], new int[] { 0, 7, 14, 7 }, new int[] { 4, 0, 4, 8 });
        g.setColor(new Color(p[4]));
        g.drawPolyline(new int[] { 0, 0, 7, 14, 14 }, new int[] { 4, 11, 15, 11, 4 }, 5);
        g.drawLine(7, 8, 7, 14);
        g.setColor(new Color(p[3]));
        g.drawLine(0, 4, 7, 0);
        g.drawLine(7, 0, 14, 4);
        g.drawLine(1, 5, 7, 8);
        g.drawLine(7, 8, 13, 5);
        // The inset top keeps the shell's original hollow-rim silhouette.
        polygon(g, p[2], new int[] { 3, 7, 11, 7 }, new int[] { 4, 2, 4, 6 });
        g.setColor(new Color(p[1]));
        g.drawLine(3, 4, 7, 2);
        g.setColor(new Color(p[3]));
        g.drawLine(3, 5, 7, 7);
        g.drawLine(7, 7, 11, 5);
        // Recessed diagonal seams and small corner fasteners communicate casing.
        g.setColor(new Color(p[4]));
        g.drawLine(2, 7, 5, 9);
        g.drawLine(9, 9, 12, 7);
        g.setColor(new Color(p[3]));
        g.fillRect(2, 9, 1, 1);
        g.fillRect(5, 11, 1, 1);
        g.fillRect(12, 9, 1, 1);
        // The corner badge uses the original gold Roman numeral tier vocabulary.
        g.setColor(new Color(0x141820));
        g.fillRect(9, 10, 7, 6);
        g.setColor(new Color(0x45505b));
        g.drawLine(9, 10, 15, 10);
        g.setColor(new Color(0xf1cc73));
        int start = 12 - (tier - 1);
        for (int n = 0; n < tier; n++) g.fillRect(start + n * 2, 12, 1, 3);
        g.dispose();
        return image;
    }

    private static void polygon(Graphics2D g, int color, int[] x, int[] y) {
        g.setColor(new Color(color));
        g.fillPolygon(x, y, x.length);
    }

    private static void checker(Graphics2D g, int x, int y, int w, int h) {
        for (int dy = 0; dy < h; dy += 8) for (int dx = 0; dx < w; dx += 8) {
            g.setColor(new Color((((dx + dy) / 8) % 2 == 0) ? 0x444950 : 0x353a40));
            g.fillRect(x + dx, y + dy, Math.min(8, w - dx), Math.min(8, h - dy));
        }
    }

    private static void shellReview() throws IOException {
        BufferedImage sheet = new BufferedImage(768, 360, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x20252c));
        g.fillRect(0, 0, 768, 360);
        g.setFont(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 15));
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (int tier = 1; tier <= 3; tier++) {
            int x = (tier - 1) * 256;
            checker(g, x + 16, 24, 224, 288);
            g.drawImage(
                ImageIO.read(
                    SOURCE.resolve("shell-" + tier + ".png")
                        .toFile()),
                x + 64,
                32,
                128,
                128,
                null);
            g.drawImage(
                ImageIO.read(
                    TARGET.resolve("shell-" + tier + ".png")
                        .toFile()),
                x + 64,
                176,
                128,
                128,
                null);
            g.setColor(Color.WHITE);
            g.drawString("Tier " + tier + " : 32px before / 16px after", x + 14, 340);
        }
        g.dispose();
        ImageIO.write(
            sheet,
            "png",
            REVIEW.resolve("shell-before-after.png")
                .toFile());
    }

    private static String sha(Path path) throws Exception {
        return HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(path)));
    }
}
