// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.weapons;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;
import net.minecraftforge.client.model.ModelFormatException;
import net.minecraftforge.client.model.obj.WavefrontObject;

/** Resource packs keep their original OBJ override path, including Forge's failure behavior. */
public final class WeaponModelLoader {

    interface Opener {

        InputStream open(ResourceLocation location) throws IOException;
    }

    private WeaponModelLoader() {}

    public static IModelCustom load(ResourceLocation source) {
        return load(
            source,
            path -> Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(path)
                .getInputStream(),
            AdvancedModelLoader::loadModel);
    }

    static IModelCustom load(ResourceLocation source, Opener opener) {
        return load(source, opener, null);
    }

    static IModelCustom load(ResourceLocation source, Opener opener,
        Function<ResourceLocation, IModelCustom> rawLoader) {
        InputStream obj;
        try {
            obj = opener.open(source);
        } catch (FileNotFoundException absent) {
            String path = source.getResourcePath();
            if (!path.endsWith(".obj")) throw new ModelFormatException("Invalid weapon OBJ path " + source);
            ResourceLocation compiled = new ResourceLocation(
                source.getResourceDomain(),
                path.substring(0, path.length() - 4) + ".weaponmesh");
            try (InputStream input = opener.open(compiled)) {
                return new ForgeWeaponModel(WeaponModelData.readCompiled(input), source.toString());
            } catch (IOException failure) {
                throw new ModelFormatException("IO Exception reading model format", failure);
            }
        } catch (IOException failure) {
            throw new ModelFormatException("IO Exception reading model format", failure);
        }
        if (rawLoader != null) {
            // This stream only probes presence. The registered Forge handler opens and owns its own stream.
            try {
                obj.close();
            } catch (IOException ignored) {
                // Forge's original OBJ parser also ignores stream-close failures.
            }
            return rawLoader.apply(source);
        }
        // WavefrontObject owns/closes the stream. Bad OBJ overrides must never select the bundled binary.
        return new WavefrontObject(source.toString(), obj);
    }
}
