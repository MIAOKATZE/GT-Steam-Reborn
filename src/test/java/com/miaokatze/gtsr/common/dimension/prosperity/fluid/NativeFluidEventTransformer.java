// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.dimension.prosperity.fluid;

import net.minecraft.launchwrapper.IClassTransformer;

/**
 * Restore Forge's native event constructors only for FML network events used by real IC2.
 * The old Forge ASM cannot inspect GT's Java-17 multi-release classes in this isolated JVM.
 * No production class or game installation is changed.
 */
public final class NativeFluidEventTransformer implements IClassTransformer {

    private final cpw.mods.fml.common.asm.transformers.EventSubscriptionTransformer nativeTransformer = new cpw.mods.fml.common.asm.transformers.EventSubscriptionTransformer();

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        return name != null && name.startsWith("cpw.mods.fml.common.network.FMLNetworkEvent")
            ? nativeTransformer.transform(name, transformedName, bytes)
            : bytes;
    }
}
