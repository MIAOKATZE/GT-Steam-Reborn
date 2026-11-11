// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.workeffects;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Restricts the existing heat foreground to the current firebox's dark, neutral grate openings. */
final class FireboxGrateMask {

    private FireboxGrateMask() {}

    static List<double[]> openings(BufferedImage base, BufferedImage foreground) {
        int size = base.getWidth();
        if (base.getHeight() % size != 0) throw new IllegalArgumentException("Firebox frames must be square");
        List<int[]> rectangles = new ArrayList<>();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size;) {
                if (!opening(base, foreground, x, y, size)) {
                    x++;
                    continue;
                }
                int left = x++;
                while (x < size && opening(base, foreground, x, y, size)) x++;
                int[] rectangle = null;
                for (int[] previous : rectangles) {
                    if (previous[0] == left && previous[2] == x && previous[3] == y) {
                        rectangle = previous;
                        break;
                    }
                }
                if (rectangle == null) rectangles.add(new int[] { left, y, x, y + 1 });
                else rectangle[3]++;
            }
        }
        List<double[]> result = new ArrayList<>();
        for (int[] rectangle : rectangles) {
            result.add(
                new double[] { rectangle[0] / (double) size, rectangle[1] / (double) size, rectangle[2] / (double) size,
                    rectangle[3] / (double) size });
        }
        return result;
    }

    private static boolean opening(BufferedImage base, BufferedImage foreground, int x, int y, int size) {
        if ((foreground.getRGB(x * foreground.getWidth() / size, y * foreground.getHeight() / size) >>> 24) == 0)
            return false;
        // Check every animation frame so changing texture frames never paints over solid trim.
        for (int frame = 0; frame < base.getHeight(); frame += size) {
            int pixel = base.getRGB(x, y + frame);
            int red = pixel >> 16 & 255, green = pixel >> 8 & 255, blue = pixel & 255;
            int brightest = Math.max(red, Math.max(green, blue));
            int darkest = Math.min(red, Math.min(green, blue));
            if ((pixel >>> 24) != 255 || brightest - darkest > 1 || brightest > 20) return false;
        }
        return true;
    }
}
