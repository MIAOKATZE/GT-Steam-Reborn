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
        boolean enabled = (mixin.endsWith("SteamMultiMachineBaseGTNLMixin") || mixin.endsWith("GTNLSteamTypeMixin"))
            ? ENHANCEMENT
            : PIPELESS;
        if (!enabled) return false;
        if (!supported()) throw new IllegalStateException(
            "GTSR GTNL compatibility requires the GTNH 2.9.0 / GTNL 0.2.7-rc2.1 steam API");
        return true;
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
            return running && deplete;
        } catch (IOException unreadable) {
            throw new IllegalStateException("Cannot inspect GTNL steam API", unreadable);
        }
    }
}
