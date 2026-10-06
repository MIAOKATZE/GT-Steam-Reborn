package com.miaokatze.gtsr.common.dimension.prosperity.air;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.StatCollector;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.BiomeId;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.FictionTimeline;
import com.miaokatze.gtsr.config.Config;
import com.miaokatze.gtsr.loader.recipes.ProsperityAirCompressorRecipes;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.util.GTRecipe;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchAirIntake;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchAirIntakeAtmosphere;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchAirIntakeExtreme;

/** Read-only registry checks and detached real GT tick diagnostics. Never places a tile in the world. */
public final class ProsperityAirAudit {

    private ProsperityAirAudit() {}

    public static void verify(ICommandSender sender, WorldServer world) {
        for (String line : verifyWorld(world)) sender.addChatMessage(new ChatComponentText("[GTSR-AIR-AUDIT] " + line));
    }

    private static List<String> verifyWorld(WorldServer world) {
        require(
            world != null && world.provider.dimensionId == Config.prosperityDimId,
            "prosperity WorldServer required");
        Materials[] feeds = { GTSRProsperityAirMaterials.WastesSigh, GTSRProsperityAirMaterials.ThickGrease,
            GTSRProsperityAirMaterials.MetalGrit, GTSRProsperityAirMaterials.UmbralMire,
            GTSRProsperityAirMaterials.SanzuResidualSteam, GTSRProsperityAirMaterials.WitheredBreath };
        BiomeId[] biomes = { BiomeId.RUSTED_STEPPE, BiomeId.GEARWORK_FOREST, BiomeId.BRASS_WASTES,
            BiomeId.FUMAROLE_SWAMP, BiomeId.SANZU_RIVER, BiomeId.WITHERED_RIVERBED };
        GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimension(world.provider.dimensionId);
        require(authority.boundDimId() == world.provider.dimensionId, "biome authority not bound");
        for (int i = 0; i < feeds.length; i++) {
            require(feeds[i] != null && feeds[i].mGas != null, "missing registered air " + biomes[i]);
            require(authority.biomeOf(biomes[i]) != null, "missing allocated biome " + biomes[i]);
            int count = 0;
            for (GTRecipe recipe : GTSRRecipeMaps.airCompressorRecipes.getAllRecipes()) {
                if (recipe.mFluidOutputs == null) continue;
                for (FluidStack output : recipe.mFluidOutputs) {
                    if (output != null && output.getFluid() == feeds[i].mGas && recipe.mFakeRecipe) {
                        require(output.amount == 800 && recipe.mDuration == 20, "display recipe quantity/interval");
                        String source = ProsperityAirCompressorRecipes.sourceKey(output);
                        require(source != null && StatCollector.canTranslate(source), "missing NEI source translation");
                        count++;
                    }
                }
            }
            require(count == 1, "expected one fake recipe for " + biomes[i] + ", got " + count);
        }

        int[][] samples = new int[6][];
        int found = 0;
        // Pure authority/river queries: this does not generate or load terrain chunks.
        long sequence = world.getSeed();
        for (int n = 0; n < 32768 && found < 6; n++) {
            sequence = sequence * 6364136223846793005L + 1442695040888963407L;
            int x = (int) (sequence >>> 32) % 32768;
            sequence = sequence * 6364136223846793005L + 1442695040888963407L;
            int z = (int) (sequence >>> 32) % 32768;
            Materials actual = ProsperityAirLookup.of(world, x, z);
            for (int i = 0; i < 6; i++) if (actual == feeds[i] && samples[i] == null) {
                require(authority.ordinalAt(x, z).biomeId != null, "unresolved sample authority");
                samples[i] = new int[] { x, z };
                found++;
            }
        }
        require(found == 6, "actual six-air samples incomplete: " + found + "/6");
        List<String> result = new ArrayList<>();
        result.add("PASS air fakeRecipes=6 authority=6 lookupSamples=6");
        for (int i = 0; i < 6; i++) result.add("sample " + biomes[i] + "=" + samples[i][0] + "," + samples[i][1]);

        // Use the same lazy initialization as real intake collection; never alter completion state.
        FictionTimeline timeline = FictionTimeline.get(world);
        if (timeline.worldCompleted()) {
            result.add("SKIP unstable intake tick rates: world has already completed the Fiction Expansion Project");
            return result;
        }
        MTEHatchAirIntake[] prototypes = new MTEHatchAirIntake[3];
        for (IMetaTileEntity registered : GregTechAPI.METATILEENTITIES) {
            if (registered == null) continue;
            if (registered.getClass() == MTEHatchAirIntake.class) prototypes[0] = (MTEHatchAirIntake) registered;
            if (registered.getClass() == MTEHatchAirIntakeExtreme.class) prototypes[1] = (MTEHatchAirIntake) registered;
            if (registered.getClass() == MTEHatchAirIntakeAtmosphere.class)
                prototypes[2] = (MTEHatchAirIntake) registered;
        }
        int[] rates = { 100, 500, 2000 };
        for (int type = 0; type < 3; type++) {
            require(prototypes[type] != null, "missing registered intake prototype " + type);
            for (int feed = 0; feed < 6; feed++) {
                DetachedBase base = new DetachedBase();
                base.setWorldObj(world);
                base.xCoord = samples[feed][0];
                base.yCoord = 255;
                base.zCoord = samples[feed][1];
                MTEHatchAirIntake hatch = (MTEHatchAirIntake) prototypes[type].newMetaEntity(base);
                hatch.setBaseMetaTileEntity(base);
                require(base.getMetaTileEntity() == hatch, "detached GT base binding");
                require(hatch.getFluidToGenerate() == feeds[feed].mGas, "real intake fluid dispatch");
                require(hatch.doesHatchMeetConditionsToGenerate(), "real front air condition");
                for (int tick = 1; tick <= 19; tick++) hatch.onPostTick(base, tick);
                require(hatch.getFillableStack() == null, "intake generated before 20 ticks");
                hatch.onPostTick(base, 20);
                FluidStack output = hatch.getFillableStack();
                require(
                    output != null && output.getFluid() == feeds[feed].mGas && output.amount == rates[type],
                    "real mixed-in intake generation mismatch type=" + type + " feed=" + feed);
                hatch.setFillableStack(new FluidStack(feeds[feed].mGas, hatch.getCapacity() - 1));
                for (int tick = 21; tick <= 40; tick++) hatch.onPostTick(base, tick);
                require(hatch.getFillableStack().amount == hatch.getCapacity(), "intake exceeded capacity");
                for (int tick = 41; tick <= 60; tick++) hatch.onPostTick(base, tick);
                require(hatch.getFillableStack().amount == hatch.getCapacity(), "full intake overflow");
            }
        }
        result.add("PASS realIntakeTicks cases=18 rates=100/500/2000 interval=20 cap=true mixin=true");
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("air audit: " + message);
    }

    /** Suppress detached dirty propagation; otherwise GT would mark/load a real terrain chunk. */
    private static final class DetachedBase extends BaseMetaTileEntity {

        @Override
        public void markDirty() {}

        @Override
        public ForgeDirection getFrontFacing() {
            return ForgeDirection.UP;
        }
    }
}
