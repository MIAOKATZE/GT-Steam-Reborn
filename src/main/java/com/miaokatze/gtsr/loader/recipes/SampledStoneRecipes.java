// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.loader.recipes;

import static gregtech.api.enums.Mods.EtFuturumRequiem;
import static gregtech.api.enums.Mods.GalacticraftCore;
import static gregtech.api.enums.Mods.GalacticraftMars;
import static gregtech.api.enums.Mods.GalaxySpace;
import static gregtech.api.enums.Mods.GregTech;
import static gregtech.api.enums.VoltageIndex.EV;
import static gregtech.api.enums.VoltageIndex.HV;
import static gregtech.api.enums.VoltageIndex.IV;
import static gregtech.api.enums.VoltageIndex.LV;
import static gregtech.api.enums.VoltageIndex.LuV;
import static gregtech.api.enums.VoltageIndex.MV;
import static gregtech.api.enums.VoltageIndex.UV;
import static gregtech.api.enums.VoltageIndex.ZPM;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Mods;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.basic.MTERockBreaker;
import gregtech.common.tileentities.machines.basic.MTERockBreaker.RockBreakerRecipe;

/** One catalog drives the native single-block matcher, its NEI view, and both industrial stone makers. */
public final class SampledStoneRecipes {

    private static final int DURATION = 16;
    private static final Map<RockBreakerRecipe, StoneBreakerRule> SINGLE_RULES = new IdentityHashMap<>();

    private SampledStoneRecipes() {}

    public static void register() {
        add(StoneSite.NETHER, LV, dust(Materials.Glowstone), 6, new ItemStack(Blocks.netherrack));
        add(StoneSite.END, LV, dust(Materials.Endstone), 8, new ItemStack(Blocks.end_stone));
        add(StoneSite.OVERWORLD, LV, dust(Materials.GraniteRed), 8, item(GregTech, "gt.blockgranites", 8));
        add(StoneSite.OVERWORLD, LV, dust(Materials.GraniteBlack), 8, item(GregTech, "gt.blockgranites", 0));
        add(StoneSite.OVERWORLD, LV, dust(Materials.Basalt), 8, item(GregTech, "gt.blockstones", 8));
        add(StoneSite.OVERWORLD, LV, dust(Materials.Marble), 8, item(GregTech, "gt.blockstones", 0));
        add(StoneSite.OVERWORLD, LV, nhDust("TuffDust"), 8, item(EtFuturumRequiem, "tuff", 0));
        add(StoneSite.OVERWORLD, LV, nhDust("DeepslateDust"), 8, item(EtFuturumRequiem, "deepslate", 0));

        // 月球
        add(StoneSite.MOON, LV, nhDust("MoonStoneDust"), 8, item(GalacticraftCore, "tile.moonBlock", 4));
        add(StoneSite.MOON, LV, nhDust("MoonStoneDust"), 9, item(GalacticraftCore, "tile.moonBlock", 5));
        add(StoneSite.MOON, LV, nhDust("MoonStoneDust"), 10, item(GalacticraftCore, "tile.moonBlock", 3));

        // 火星
        add(StoneSite.MARS, MV, nhDust("MarsStoneDust"), 8, item(GalacticraftMars, "tile.mars", 9));
        add(StoneSite.MARS, MV, nhDust("MarsStoneDust"), 9, item(GalacticraftMars, "tile.mars", 5));
        add(StoneSite.MARS, MV, nhDust("MarsStoneDust"), 10, item(GalacticraftMars, "tile.mars", 6));
        add(StoneSite.MARS, MV, nhDust("MarsStoneDust"), 11, item(GalacticraftMars, "tile.mars", 4));

        // 火卫一
        add(StoneSite.PHOBOS, MV, nhDust("PhobosStoneDust"), 8, item(GalaxySpace, "phobosblocks", 2));
        add(StoneSite.PHOBOS, MV, nhDust("PhobosStoneDust"), 9, item(GalaxySpace, "phobosblocks", 0));
        add(StoneSite.PHOBOS, MV, nhDust("PhobosStoneDust"), 10, item(GalaxySpace, "phobosblocks", 1));

        // 火卫二
        add(StoneSite.DEIMOS, MV, nhDust("DeimosStoneDust"), 8, item(GalaxySpace, "deimosblocks", 1));
        add(StoneSite.DEIMOS, MV, nhDust("DeimosStoneDust"), 9, item(GalaxySpace, "deimosblocks", 0));

        // 小行星带
        add(StoneSite.ASTEROIDS, HV, nhDust("AsteroidsStoneDust"), 8, item(GalacticraftMars, "tile.asteroidsBlock", 1));
        add(StoneSite.ASTEROIDS, HV, nhDust("AsteroidsStoneDust"), 9, item(GalacticraftMars, "tile.asteroidsBlock", 0));
        add(
            StoneSite.ASTEROIDS,
            HV,
            nhDust("AsteroidsStoneDust"),
            10,
            item(GalacticraftMars, "tile.asteroidsBlock", 2));

        // 谷神星
        add(StoneSite.CERES, HV, nhDust("CeresStoneDust"), 8, item(GalaxySpace, "ceresblocks", 1));
        add(StoneSite.CERES, HV, nhDust("CeresStoneDust"), 9, item(GalaxySpace, "ceresblocks", 0));
        add(
            StoneSite.CERES,
            IV,
            item(GalaxySpace, "item.GlowstoneDusts", 0),
            13,
            item(GalaxySpace, "ceresglowstone", 0));

        // 木卫二
        add(StoneSite.EUROPA, HV, nhDust("EuropaStoneDust"), 8, item(GalaxySpace, "europagrunt", 0));
        add(StoneSite.EUROPA, IV, nhDust("EuropaStoneDust"), 12, item(GalaxySpace, "europageyser", 0));
        add(StoneSite.EUROPA, IV, nhDust("EuropaIceDust"), 12, item(GalaxySpace, "europaunderwatergeyser", 0));
        add(StoneSite.EUROPA, IV, nhDust("EuropaIceDust"), 16, item(GalaxySpace, "europagrunt", 1));

        // 木卫三
        add(StoneSite.GANYMEDE, HV, nhDust("GanymedeStoneDust"), 8, item(GalaxySpace, "ganymedeblocks", 1));
        add(StoneSite.GANYMEDE, HV, nhDust("GanymedeStoneDust"), 9, item(GalaxySpace, "ganymedeblocks", 0));

        // 木卫四
        add(StoneSite.CALLISTO, HV, nhDust("CallistoStoneDust"), 8, item(GalaxySpace, "callistoblocks", 1));
        add(StoneSite.CALLISTO, HV, nhDust("CallistoStoneDust"), 9, item(GalaxySpace, "callistoblocks", 0));

        // 木卫一
        add(StoneSite.IO, HV, nhDust("IoStoneDust"), 8, item(GalaxySpace, "ioblocks", 2));
        add(StoneSite.IO, HV, nhDust("IoStoneDust"), 9, item(GalaxySpace, "ioblocks", 0));
        add(StoneSite.IO, HV, nhDust("IoStoneDust"), 10, item(GalaxySpace, "ioblocks", 1));
        add(StoneSite.IO, IV, item(GalaxySpace, "item.GlowstoneDusts", 1), 13, item(GalaxySpace, "ioglowstone", 0));

        // 金星
        add(StoneSite.VENUS, HV, nhDust("VenusStoneDust"), 8, item(GalaxySpace, "venusblocks", 1));
        add(StoneSite.VENUS, HV, nhDust("VenusStoneDust"), 9, item(GalaxySpace, "venusblocks", 0));

        // 水星
        add(StoneSite.MERCURY, HV, nhDust("MercuryStoneDust"), 8, item(GalaxySpace, "mercuryblocks", 1));
        add(StoneSite.MERCURY, HV, nhDust("MercuryStoneDust"), 9, item(GalaxySpace, "mercuryblocks", 0));
        add(StoneSite.MERCURY, HV, nhDust("MercuryCoreDust"), 14, item(GalaxySpace, "mercuryblocks", 2));

        // 土卫二
        add(StoneSite.ENCELADUS, EV, nhDust("EnceladusStoneDust"), 8, item(GalaxySpace, "enceladusblocks", 1));
        add(
            StoneSite.ENCELADUS,
            LuV,
            item(GalaxySpace, "item.GlowstoneDusts", 2),
            13,
            item(GalaxySpace, "enceladusglowstone", 0));
        add(StoneSite.ENCELADUS, LuV, nhDust("EnceladusIceDust"), 16, item(GalaxySpace, "enceladusblocks", 0));
        add(StoneSite.ENCELADUS, LuV, nhDust("EnceladusIceDust"), 17, item(GalaxySpace, "enceladusblocks", 3));

        // 土卫六
        add(StoneSite.TITAN, EV, nhDust("TitanStoneDust"), 8, item(GalaxySpace, "titanblocks", 2));
        add(StoneSite.TITAN, EV, nhDust("TitanStoneDust"), 9, item(GalaxySpace, "titanblocks", 0));
        add(StoneSite.TITAN, EV, nhDust("TitanStoneDust"), 10, item(GalaxySpace, "titanblocks", 1));

        // 天卫五
        add(StoneSite.MIRANDA, EV, nhDust("MirandaStoneDust"), 8, item(GalaxySpace, "mirandablocks", 2));
        add(StoneSite.MIRANDA, EV, nhDust("MirandaStoneDust"), 9, item(GalaxySpace, "mirandablocks", 0));
        add(StoneSite.MIRANDA, EV, nhDust("MirandaStoneDust"), 10, item(GalaxySpace, "mirandablocks", 1));

        // 天卫四
        add(StoneSite.OBERON, EV, nhDust("OberonStoneDust"), 8, item(GalaxySpace, "oberonblocks", 2));
        add(StoneSite.OBERON, EV, nhDust("OberonStoneDust"), 9, item(GalaxySpace, "oberonblocks", 0));
        add(StoneSite.OBERON, EV, nhDust("OberonStoneDust"), 10, item(GalaxySpace, "oberonblocks", 1));

        // 海卫八
        add(StoneSite.PROTEUS, IV, nhDust("ProteusStoneDust"), 8, item(GalaxySpace, "proteusblocks", 2));
        add(StoneSite.PROTEUS, IV, nhDust("ProteusStoneDust"), 9, item(GalaxySpace, "proteusblocks", 0));
        add(StoneSite.PROTEUS, IV, nhDust("ProteusStoneDust"), 10, item(GalaxySpace, "proteusblocks", 1));
        add(
            StoneSite.PROTEUS,
            ZPM,
            item(GalaxySpace, "item.GlowstoneDusts", 3),
            13,
            item(GalaxySpace, "proteusglowstone", 0));

        // 海卫一
        add(StoneSite.TRITON, IV, nhDust("TritonStoneDust"), 8, item(GalaxySpace, "tritonblocks", 2));
        add(StoneSite.TRITON, IV, nhDust("TritonStoneDust"), 9, item(GalaxySpace, "tritonblocks", 0));
        add(StoneSite.TRITON, IV, nhDust("TritonStoneDust"), 10, item(GalaxySpace, "tritonblocks", 1));

        // 冥王星
        add(StoneSite.PLUTO, LuV, nhDust("PlutoStoneDust"), 8, item(GalaxySpace, "plutoblocks", 5));
        add(
            StoneSite.PLUTO,
            UV,
            item(GalaxySpace, "item.GlowstoneDusts", 4),
            13,
            item(GalaxySpace, "plutoglowstone", 0));
        add(StoneSite.PLUTO, UV, nhDust("PlutoIceDust"), 16, item(GalaxySpace, "plutoblocks", 0));
        add(StoneSite.PLUTO, UV, nhDust("PlutoIceDust"), 17, item(GalaxySpace, "plutoblocks", 1));
        add(StoneSite.PLUTO, UV, nhDust("PlutoIceDust"), 18, item(GalaxySpace, "plutoblocks", 2));
        add(StoneSite.PLUTO, UV, nhDust("PlutoIceDust"), 19, item(GalaxySpace, "plutoblocks", 3));
        add(StoneSite.PLUTO, UV, nhDust("PlutoIceDust"), 20, item(GalaxySpace, "plutoblocks", 4));

        // 鸟神星
        add(StoneSite.MAKEMAKE, LuV, nhDust("MakeMakeStoneDust"), 8, item(GalaxySpace, "makemakegrunt", 1));
        add(StoneSite.MAKEMAKE, LuV, nhDust("MakeMakeStoneDust"), 9, item(GalaxySpace, "makemakegrunt", 0));

        // 妊神星
        add(StoneSite.HAUMEA, LuV, nhDust("HaumeaStoneDust"), 8, item(GalaxySpace, "haumeablocks", 0));

        // 半人马座 α 星 Bb
        add(StoneSite.CENTAURI_BB, LuV, nhDust("CentauriAStoneDust"), 8, item(GalaxySpace, "acentauribbsubgrunt", 0));
        add(StoneSite.CENTAURI_BB, LuV, nhDust("CentauriASurfaceDust"), 9, item(GalaxySpace, "acentauribbgrunt", 0));

        // 织女一 B
        add(StoneSite.VEGA_B, LuV, nhDust("VegaBStoneDust"), 8, item(GalaxySpace, "vegabsubgrunt", 0));
        add(StoneSite.VEGA_B, LuV, nhDust("VegaBStoneDust"), 9, item(GalaxySpace, "vegabgrunt", 0));

        // 巴纳德 E
        add(StoneSite.BARNARDA_E, LuV, nhDust("BarnardaEStoneDust"), 8, item(GalaxySpace, "barnardaEsubgrunt", 0));
        add(StoneSite.BARNARDA_E, LuV, nhDust("BarnardaEStoneDust"), 9, item(GalaxySpace, "barnardaEgrunt", 0));

        // 巴纳德 F
        add(StoneSite.BARNARDA_F, LuV, nhDust("BarnardaFStoneDust"), 8, item(GalaxySpace, "barnardaFsubgrunt", 0));
        add(StoneSite.BARNARDA_F, LuV, nhDust("BarnardaFStoneDust"), 9, item(GalaxySpace, "barnardaFgrunt", 0));

        // 鲸鱼座 T 星 E
        add(StoneSite.T_CETI_E, LuV, nhDust("TCetiEStoneDust"), 8, item(GalaxySpace, "tcetieblocks", 2));
        add(StoneSite.T_CETI_E, LuV, nhDust("TCetiEStoneDust"), 9, item(GalaxySpace, "tcetieblocks", 0));
        add(StoneSite.T_CETI_E, LuV, nhDust("TCetiEStoneDust"), 10, item(GalaxySpace, "tcetieblocks", 1));
    }

    private static ItemStack dust(Materials material) {
        return RecipeLoaderUtils.get(OrePrefixes.dust, material, 1);
    }

    private static ItemStack nhDust(String name) {
        return item(Mods.NewHorizonsCoreMod, name, 0);
    }

    private static ItemStack item(Mods mod, String name, int meta) {
        if (!mod.isModLoaded()) return null;
        return GTModHandler.getModItem(mod.ID, name, 1, meta);
    }

    private static void add(StoneSite site, int tier, ItemStack sample, int circuit, ItemStack output) {
        // Optional pack components may be absent. Never register the output without its sample.
        if (RecipeLoaderUtils.hasNull(sample, output) || !site.resolve()) return;
        int eut = (int) GTValues.VP[tier];
        StoneBreakerRule rule = new StoneBreakerRule(site, eut, sample, circuit);
        var builder = GTValues.RA.stdBuilder()
            .itemInputsUnified(GTUtility.copyAmount(0, sample))
            .circuit(circuit)
            .itemOutputs(output.copy())
            .duration(DURATION)
            .eut(eut)
            .metadata(StoneBreakerRule.KEY, rule);
        // The paid and reusable glowstone recipes share their input IDs, with disjoint dimensions.
        if (site == StoneSite.NETHER) builder.ignoreCollision();
        Collection<GTRecipe> industrial = builder.addTo(RecipeMaps.multiblockRockBreakerRecipes);
        if (industrial.isEmpty()) throw new IllegalStateException("Industrial stone recipe rejected: " + output);

        SampledBuilder single = new SampledBuilder(rule);
        if (site == StoneSite.NETHER) single.sideBlocks(Blocks.water)
            .anywhereBlocks(Blocks.lava);
        else single.sideBlocks(Blocks.water, Blocks.lava);
        MTERockBreaker.addRockBreakerRecipe(
            unused -> single.inputItem(sample, false)
                .circuit(circuit)
                .outputItem(output)
                .duration(DURATION));
    }

    public static StoneBreakerRule singleRule(RockBreakerRecipe recipe) {
        return SINGLE_RULES.get(recipe);
    }

    public static void bindSingle(RockBreakerRecipe recipe, StoneBreakerRule rule) {
        SINGLE_RULES.put(recipe, rule);
    }

    public static final class SampledBuilder extends RockBreakerRecipe.Builder {

        public final StoneBreakerRule rule;

        private SampledBuilder(StoneBreakerRule rule) {
            this.rule = rule;
        }
    }
}
