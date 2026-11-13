// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.gtsr.client.machinevisual;

import java.lang.reflect.Proxy;

import net.minecraft.world.IBlockAccess;

/** Exact delegate results, coordinate keys and render/world scope; no GL or game instance. */
public final class MachineVisualRenderAccessContracts {

    private static int checks;

    private static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }

    public static void main(String[] args) throws Exception {
        int[] calls = { 0 }, light = { 0 }, metadata = { 1 };
        IBlockAccess world = (IBlockAccess) Proxy.newProxyInstance(
            IBlockAccess.class.getClassLoader(),
            new Class<?>[] { IBlockAccess.class },
            (proxy, method, values) -> {
                switch (method.getName()) {
                    case "getLightBrightnessForSkyBlocks":
                        calls[0]++;
                        return light[0];
                    case "getBlockMetadata":
                        return metadata[0];
                    case "getHeight":
                        return 256;
                    case "isAirBlock":
                        return true;
                    default:
                        return null;
                }
            });
        MachineVisualRenderAccess access = new MachineVisualRenderAccess();
        check(access.begin(world), "first invocation owns scope");
        check(access.getLightBrightnessForSkyBlocks(0, 0, 0, 0) == 0, "zero native light preserved");
        check(access.getLightBrightnessForSkyBlocks(0, 0, 0, 0) == 0 && calls[0] == 1, "zero cached");
        light[0] = 15728880;
        check(
            access.getLightBrightnessForSkyBlocks(0, 0, 0, 15) == light[0] && calls[0] == 2,
            "minimum block light participates in key");
        for (int[] point : new int[][] { { -1, 0, 0 }, { 0, -1, 0 }, { 0, 0, -1 }, { -33554432, -2048, -33554432 },
            { 33554431, 2047, 33554431 } }) {
            int before = calls[0];
            check(
                access.getLightBrightnessForSkyBlocks(point[0], point[1], point[2], 0) == light[0],
                "signed native result");
            access.getLightBrightnessForSkyBlocks(point[0], point[1], point[2], 0);
            check(calls[0] == before + 1, "signed/boundary key distinct and cached");
        }
        for (int[] point : new int[][] { { 33554432, 0, 0, 0 }, { -33554433, 0, 0, 0 }, { 0, 2048, 0, 0 },
            { 0, -2049, 0, 0 }, { 0, 0, 33554432, 0 }, { 0, 0, 0, 16 }, { 0, 0, 0, -1 } }) {
            int before = calls[0];
            access.getLightBrightnessForSkyBlocks(point[0], point[1], point[2], point[3]);
            access.getLightBrightnessForSkyBlocks(point[0], point[1], point[2], point[3]);
            check(calls[0] == before + 2, "out-of-range coordinates/light delegate without collisions");
        }
        check(!access.begin(world), "nested invocation uses caller fallback without replacing outer scope");
        check(access.getBlockMetadata(0, 0, 0) == 1, "metadata delegates");
        metadata[0] = 7;
        check(access.getBlockMetadata(0, 0, 0) == 7, "metadata remains uncached");
        check(access.isAirBlock(0, 0, 0) && access.getHeight() == 256, "other accessors delegate");
        access.end();
        check(access.begin(world), "next render starts a fresh scope");
        check(
            access.getLightBrightnessForSkyBlocks(0, 0, 0, 0) == light[0],
            "same-world light change visible next render");
        access.end();
        IBlockAccess replacement = (IBlockAccess) Proxy.newProxyInstance(
            IBlockAccess.class.getClassLoader(),
            new Class<?>[] { IBlockAccess.class },
            (proxy, method, values) -> method.getName()
                .equals("getLightBrightnessForSkyBlocks") ? 123 : null);
        access.begin(replacement);
        check(
            access.getLightBrightnessForSkyBlocks(0, 0, 0, 0) == 123,
            "world replacement never reuses previous light");
        access.end();
        java.lang.reflect.Field source = MachineVisualRenderAccess.class.getDeclaredField("source");
        source.setAccessible(true);
        check(source.get(access) == null, "render exit releases world reference");
        System.out.println("PASS MachineVisualRenderAccess contracts: " + checks);
    }
}
