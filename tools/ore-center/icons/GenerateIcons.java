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
    private static final String[] SHELL_SHA256 = { "b03a38e1571502cb20f7e1bbd70783d5fc6797ea572df491d869198d7e2ddd98",
        "5870509d5c5fa47ccf627e941cae1ed48706807a0f702624dbb13e831720abdd",
        "421ca2502e010c7eb08d68183bc54f48d09df1df1b99b761df9e78c79848ba30" };

    public static void main(String[] args) throws Exception {
        boolean check = args.length == 1 && args[0].equals("--check");
        if (args.length == 1 && args[0].equals("--preview")) {
            previewPackages();
            return;
        }
        if (args.length > 0 && !check) throw new IllegalArgumentException(
            "Usage: java tools/ore-center/icons/GenerateIcons.java [--check|--preview]");
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
            "name\tsource_size\tsource_sha256\toutput_size\toutput_sha256\ttransparent\tpartial_alpha\topaque\topaque_colors\n");
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
            boolean isShell = name.startsWith("shell-");
            BufferedImage result = isShell ? shell(Integer.parseInt(name.substring(6, 7))) : modulePackage(name);
            Path output = TARGET.resolve(name);
            if (isShell && !sha(output).equals(SHELL_SHA256[Integer.parseInt(name.substring(6, 7)) - 1]))
                throw new IOException("Shell PNG byte identity changed: " + name);
            if (!check && !isShell) ImageIO.write(result, "png", output.toFile());
            BufferedImage actual = ImageIO.read(output.toFile());
            if (actual.getWidth() != 16 || actual.getHeight() != 16
                || !actual.getColorModel()
                    .hasAlpha())
                throw new IOException("Invalid 16px RGBA output: " + name);
            int transparent = 0, partial = 0, opaque = 0;
            var colors = new java.util.HashSet<Integer>();
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                if (actual.getRGB(x, y) != result.getRGB(x, y))
                    throw new IOException("Stale generated output: " + name);
                int alpha = actual.getRGB(x, y) >>> 24;
                if (alpha == 0) transparent++;
                else if (alpha == 255) {
                    opaque++;
                    colors.add(actual.getRGB(x, y));
                } else partial++;
            }
            if (transparent == 0 || opaque == 0) throw new IOException("Missing transparent/opaque pixels: " + name);
            if (!isShell && (partial != 0 || colors.size() > 10))
                throw new IOException("Package requires binary alpha and at most 10 opaque colors: " + name);
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
                .append('\t')
                .append(colors.size())
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
                + " 54 icons: 51 native pixel module packages + 3 unchanged cube shells; all 16x16 RGBA.");
    }

    /** Crisp 16px authored geometry; fixed palette, binary alpha and 3x5 code glyphs. */
    private static BufferedImage modulePackage(String name) {
        int split = name.lastIndexOf('-');
        String kind = name.substring(0, split);
        int tier = Integer.parseInt(name.substring(split + 1, split + 2));
        String code = switch (kind) {
            case "crusher" -> "CR";
            case "washer" -> "OW";
            case "sifter" -> "SF";
            case "centrifuge" -> "CF";
            case "electrolyzer" -> "EL";
            case "dryer" -> "VD";
            case "furnace" -> "RF";
            case "reactor" -> "CH";
            case "separator" -> "EM";
            case "extractor" -> "EX";
            case "thermalCentrifuge" -> "TC";
            case "smelting" -> "FU";
            case "speed" -> "SP";
            case "parallel" -> "PA";
            case "yield" -> "PY";
            case "secondary" -> "BY";
            case "logistics" -> "LG";
            default -> throw new IllegalArgumentException("Unknown package " + kind);
        };
        int accent = switch (kind) {
            case "crusher" -> 0xd06b4c;
            case "washer" -> 0x31a3af;
            case "sifter" -> 0xb4975e;
            case "centrifuge" -> 0x507ec3;
            case "electrolyzer" -> 0x47c1d8;
            case "dryer" -> 0xab75c9;
            case "furnace" -> 0xc94443;
            case "reactor" -> 0x91ae43;
            case "separator" -> 0x6971c1;
            case "extractor" -> 0x3d9c7e;
            case "thermalCentrifuge" -> 0xd58c3e;
            case "smelting" -> 0x9f5d40;
            case "speed" -> 0xdfb743;
            case "parallel" -> 0x927ae1;
            case "yield" -> 0x58be6b;
            case "secondary" -> 0xcc689c;
            case "logistics" -> 0xb8bea6;
            default -> throw new IllegalArgumentException("Unknown package " + kind);
        };
        int[][] metal = { { 0xbfcdd4, 0x8297a3, 0x4b6371, 0xe5edf0, 0x273c49 },
            { 0xedf3f6, 0xb5c8d2, 0x7895a4, 0xffffff, 0x405c6b },
            { 0xb6bddc, 0x7e8ab4, 0x48577f, 0xe2e7ff, 0x2b3658 } };
        int[] p = metal[tier - 1];
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        // Wide stepped base and colored middle band preserve the source package style.
        polygon(g, p[1], new int[] { 0, 7, 7, 0 }, new int[] { 8, 12, 15, 11 });
        polygon(g, p[2], new int[] { 7, 15, 15, 7 }, new int[] { 12, 8, 11, 15 });
        polygon(g, p[0], new int[] { 0, 7, 15, 7 }, new int[] { 8, 4, 8, 12 });
        g.setColor(new Color(p[4]));
        g.drawPolyline(new int[] { 0, 0, 7, 15, 15 }, new int[] { 8, 11, 15, 11, 8 }, 5);
        g.setColor(new Color(accent));
        g.drawLine(1, 10, 7, 13);
        g.drawLine(7, 13, 14, 9);
        g.setColor(new Color(p[3]));
        g.drawLine(1, 8, 7, 11);
        g.drawLine(7, 11, 14, 8);
        // Smaller module case on the base: two solid side faces and a beveled lid.
        polygon(g, p[1], new int[] { 6, 10, 10, 6 }, new int[] { 4, 6, 9, 7 });
        polygon(g, p[2], new int[] { 10, 14, 14, 10 }, new int[] { 6, 4, 7, 9 });
        polygon(g, p[0], new int[] { 6, 10, 14, 10 }, new int[] { 4, 2, 4, 6 });
        g.setColor(new Color(p[4]));
        g.drawLine(10, 6, 10, 8);
        g.setColor(new Color(p[3]));
        g.drawLine(6, 4, 10, 2);
        g.drawLine(7, 5, 10, 6);
        g.setColor(new Color(accent));
        g.fillRect(7, 6, 2, 1);
        // The labels retain the exact original two-letter codes at an independent scale.
        drawCode(g, code, 0, 0);
        // Five-pixel tall tier strokes keep I/II/III readable at native GUI scale.
        g.setColor(new Color(0x141820));
        g.fillRect(9, 10, 7, 6);
        g.setColor(new Color(0xf1cc73));
        for (int n = 0; n < tier; n++) g.fillRect(12 - (tier - 1) + n * 2, 11, 1, 5);
        g.dispose();
        return image;
    }

    private static void drawCode(Graphics2D g, String code, int x, int y) {
        for (int pass = 0; pass < 2; pass++) {
            int offset = pass == 0 ? 1 : 0;
            g.setColor(new Color(pass == 0 ? 0x17242f : 0xffffff));
            for (int n = 0; n < code.length(); n++) {
                String glyph = switch (code.charAt(n)) {
                    case 'A' -> "010101111101101";
                    case 'B' -> "110101110101110";
                    case 'C' -> "111100100100111";
                    case 'D' -> "110101101101110";
                    case 'E' -> "111100110100111";
                    case 'F' -> "111100110100100";
                    case 'G' -> "111100101101111";
                    case 'H' -> "101101111101101";
                    case 'L' -> "100100100100111";
                    case 'M' -> "101111111101101";
                    case 'O' -> "111101101101111";
                    case 'P' -> "110101110100100";
                    case 'R' -> "110101110101101";
                    case 'S' -> "111100111001111";
                    case 'T' -> "111010010010010";
                    case 'U' -> "101101101101111";
                    case 'V' -> "101101101101010";
                    case 'W' -> "101101111111101";
                    case 'X' -> "101101010101101";
                    case 'Y' -> "101101010010010";
                    default -> throw new IllegalArgumentException("Missing pixel glyph");
                };
                for (int row = 0; row < 5; row++)
                    for (int col = 0; col < 3; col++) if (glyph.charAt(row * 3 + col) == '1')
                        g.fillRect(x + n * 4 + col + offset, y + row + offset, 1, 1);
            }
        }
    }

    /** Preview leaves production PNGs unchanged for review before generation. */
    private static void previewPackages() throws Exception {
        Files.createDirectories(REVIEW);
        List<Path> packages;
        try (var paths = Files.list(SOURCE)) {
            packages = paths.filter(
                p -> !p.getFileName()
                    .toString()
                    .startsWith("shell-"))
                .sorted()
                .toList();
        }
        BufferedImage sheet = new BufferedImage(1020, 6 * 152, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x20252c));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setFont(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 11));
        int i = 0;
        for (Path source : packages) {
            String name = source.getFileName()
                .toString();
            int x = i % 9 * 113, y = i / 9 * 152;
            checker(g, x + 8, y + 7, 96, 128);
            g.drawImage(
                ImageIO.read(
                    TARGET.resolve(name)
                        .toFile()),
                x + 24,
                y + 7,
                64,
                64,
                null);
            g.drawImage(modulePackage(name), x + 24, y + 71, 64, 64, null);
            g.setColor(Color.WHITE);
            g.drawString(name.substring(0, name.length() - 4), x + 4, y + 148);
            i++;
        }
        g.dispose();
        ImageIO.write(
            sheet,
            "png",
            REVIEW.resolve("packages-preview.png")
                .toFile());
        System.out.println(
            "Previewed 51 module packages (top: current, bottom: crisp candidate); production PNGs untouched.");
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
