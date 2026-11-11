// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.compat.gtnl;

import java.io.IOException;
import java.io.InputStream;

import net.minecraftforge.common.config.Configuration;

import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodNode;

/** Optional transforms are selected from disk configuration, never uninitialized Config defaults. */
public final class GTNLMixinSelection {

    private static final boolean PRESENT = resource("com/science/gtnl/ScienceNotLeisure.class");
    private static final Configuration CONFIGURATION = GTNLCompatibilityConfig.startupConfiguration();
    private static final boolean ENHANCEMENT = GTNLCompatibilityConfig.enhancement(CONFIGURATION);
    private static final boolean PIPELESS = GTNLCompatibilityConfig.pipeless(CONFIGURATION);

    private GTNLMixinSelection() {}

    public static boolean shouldApply(String mixin) {
        if (!PRESENT) return false;
        if (mixin.endsWith("GTNLNativeSteamMachineMixin")) return true;
        boolean enabled = mixin.endsWith("SteamMultiMachineBaseGTNLMixin") ? ENHANCEMENT : PIPELESS;
        if (!enabled) return false;
        if (!supported()) throw new IllegalStateException(
            "GTSR GTNL compatibility requires the GTNH 2.9.0 / GTNL dev-290 @223c7c12769d steam API");
        return true;
    }

    public static boolean pipelessEnabled() {
        if (!PRESENT || !PIPELESS) return false;
        if (!Supported.API) throw new IllegalStateException(
            "GTSR GTNL compatibility requires the GTNH 2.9.0 / GTNL dev-290 @223c7c12769d steam API");
        return true;
    }

    private static final class Supported {

        private static final boolean API = supported();
    }

    private static boolean resource(String name) {
        return GTNLMixinSelection.class.getClassLoader()
            .getResource(name) != null;
    }

    private static boolean supported() {
        String name = "com/science/gtnl/common/machine/multiMachineBase/SteamMultiMachineBase.class";
        try (InputStream bytes = GTNLMixinSelection.class.getClassLoader()
            .getResourceAsStream(name)) {
            if (bytes == null || !resource("com/science/gtnl/common/machine/hatch/WirelessSteamEnergyHatch.class"))
                return false;
            ClassNode node = new ClassNode();
            new ClassReader(bytes)
                .accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            boolean running = false, deplete = false;
            for (MethodNode method : node.methods) {
                running |= method.name.equals("onRunningTick")
                    && method.desc.equals("(Lnet/minecraft/item/ItemStack;)Z");
                deplete |= method.name.equals("depleteInputQuantity")
                    && method.desc.equals("(Lnet/minecraftforge/fluids/FluidStack;Z)I");
            }
            if (!running || !deplete) return false;
            try (InputStream typeBytes = GTNLMixinSelection.class.getClassLoader()
                .getResourceAsStream("com/science/gtnl/utils/enums/SteamTypes.class")) {
                if (typeBytes == null) return false;
                ClassNode types = new ClassNode();
                new ClassReader(typeBytes)
                    .accept(types, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                boolean fluid = false, efficiency = false;
                for (org.spongepowered.asm.lib.tree.FieldNode field : types.fields) {
                    if ((field.access & org.spongepowered.asm.lib.Opcodes.ACC_PUBLIC) == 0) continue;
                    fluid |= field.name.equals("fluid") && field.desc.equals("Lgregtech/api/util/GTUtility$FluidId;");
                    efficiency |= field.name.equals("efficiencyFactor") && field.desc.equals("I");
                }
                return fluid && efficiency;
            }
        } catch (IOException unreadable) {
            throw new IllegalStateException("Cannot inspect GTNL steam API", unreadable);
        }
    }
}
