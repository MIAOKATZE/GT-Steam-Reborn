"""Declared bootstrap/registry/capture seams for the local compat regressions. Not mod code."""

BQ = {'BqAbsentCheck.java': 'public class BqAbsentCheck{public static void main(String[] '
                       'args){com.miaokatze.gtsr.crossmod.bq.BqCompat.detect();if(com.miaokatze.gtsr.crossmod.bq.BqCompat.isBqLoaded())throw '
                       'new AssertionError();System.out.println("PASS BQ-absent optional detector '
                       'without injector linkage");}}',
 'GTSteamReborn.java': 'package com.miaokatze.gtsr.main; public class GTSteamReborn {public static '
                       'final org.apache.logging.log4j.Logger '
                       'LOG=org.apache.logging.log4j.LogManager.getLogger("GTSR-BQ-regression");}',
 'Tags.java': 'package com.miaokatze.gtsr;public class Tags{public static final String '
              'VERSION="test-new";}',
 'Config.java': 'package com.miaokatze.gtsr.config;public class Config{public static int '
                'prosperityDimId=78;}',
 'Loader.java': 'package cpw.mods.fml.common;public class Loader{public static boolean '
                'isModLoaded(String name){return true;}}',
 'BQ_Settings.java': 'package betterquesting.api.storage;public class BQ_Settings{public static '
                     'java.io.File curWorldDir;}',
 'SaveLoadHandler.java': 'package betterquesting.handlers;public class SaveLoadHandler{public '
                         'static final SaveLoadHandler INSTANCE=new SaveLoadHandler();public void '
                         'markDirty(){}}',
 'OreDictionary.java': 'package net.minecraftforge.oredict;public class OreDictionary{public '
                       'static java.util.ArrayList<net.minecraft.item.ItemStack> getOres(String '
                       's){return new java.util.ArrayList<>();}}',
 'NetSettingSync.java': 'package betterquesting.network.handlers;public class '
                        'NetSettingSync{public static void sendSync(Object x){}}',
 'NetQuestSync.java': 'package betterquesting.network.handlers;public class NetQuestSync{public '
                      'static void quickSync(Object x,boolean a,boolean b){}}',
 'NetChapterSync.java': 'package betterquesting.network.handlers;public class '
                        'NetChapterSync{public static void sendSync(Object x,Object y){}}'}

RECIPE = {'GTSteamReborn.java': 'package com.miaokatze.gtsr.main;public class GTSteamReborn{public static '
                       'final org.apache.logging.log4j.Logger '
                       'LOG=org.apache.logging.log4j.LogManager.getLogger("recipe");}',
 'GTModHandler.java': 'package gregtech.api.util;public class GTModHandler{public static class '
                      'RecipeBits{public static final long BITSD=1;} public static void '
                      'addCraftingRecipe(net.minecraft.item.ItemStack out,long bits,Object[] '
                      'in){}}',
 'GTRecipeBuilder.java': 'package gregtech.api.util; public class GTRecipeBuilder {public static '
                         'final int SECONDS=20;public static int registrations; public static '
                         'net.minecraft.item.ItemStack[] lastInputs;private '
                         'net.minecraft.item.ItemStack[] inputs; public GTRecipeBuilder '
                         'circuit(int n){return this;} public GTRecipeBuilder '
                         'itemInputs(net.minecraft.item.ItemStack... a){inputs=a;return this;} '
                         'public GTRecipeBuilder itemOutputs(net.minecraft.item.ItemStack... '
                         'a){return this;} public GTRecipeBuilder duration(int a){return this;} '
                         'public GTRecipeBuilder eut(long a){return this;} public GTRecipeBuilder '
                         'addTo(Object map){registrations++;lastInputs=inputs;return this;}}',
 'GTValues.java': 'package gregtech.api.enums;public class GTValues{public static final RA RA=new '
                  'RA(); public static class RA{public gregtech.api.util.GTRecipeBuilder '
                  'stdBuilder(){return new gregtech.api.util.GTRecipeBuilder();}}}',
 'RecipeMaps.java': 'package gregtech.api.recipe;public class RecipeMaps{public static final '
                    'Object assemblerRecipes=new Object();}',
 'Materials.java': 'package gregtech.api.enums;public enum Materials{Bronze,RoseGold}',
 'OrePrefixes.java': 'package gregtech.api.enums;public enum OrePrefixes{frameGt,plate}',
 'TierEU.java': 'package gregtech.api.enums;public class TierEU{public static final int '
                'RECIPE_HV=480;}',
 'MaterialsAlloy.java': 'package gtPlusPlus.core.material; public class MaterialsAlloy {public '
                        'static final MaterialsAlloy TUMBAGA=new MaterialsAlloy(); public '
                        'net.minecraft.item.ItemStack getPlate(int n){return new '
                        'net.minecraft.item.ItemStack(net.minecraft.init.Items.iron_ingot,n);}}',
 'GTOreDictUnificator.java': 'package gregtech.api.util;public class GTOreDictUnificator{public '
                             'static net.minecraft.item.ItemStack '
                             'get(gregtech.api.enums.OrePrefixes p,Object m,long n){return new '
                             'net.minecraft.item.ItemStack(net.minecraft.init.Items.iron_ingot,(int)n);}}'}

GRID = {'GTRecipe.java': 'package gregtech.api.util;public class GTRecipe implements '
                  'Comparable<GTRecipe>{public net.minecraft.item.ItemStack[] '
                  'mInputs,mOutputs;public net.minecraftforge.fluids.FluidStack[] '
                  'mFluidInputs,mFluidOutputs;public int compareTo(GTRecipe r){return 0;}}',
 'GTUITextures.java': 'package gregtech.api.gui.modularui;public class GTUITextures{public static '
                      'final com.gtnewhorizons.modularui.api.drawable.UITexture '
                      'PICTURE_GT_LOGO_17x17_TRANSPARENT=null;public static final '
                      'com.gtnewhorizons.modularui.api.drawable.AdaptableUITexture '
                      'BACKGROUND_NEI_SINGLE_RECIPE=new '
                      'com.gtnewhorizons.modularui.api.drawable.AdaptableUITexture(new '
                      'net.minecraft.util.ResourceLocation("test","test"),170,82,0,0);}',
 'GTGuiTextures.java': 'package gregtech.api.modularui2;public class GTGuiTextures{public static '
                       'final com.cleanroommc.modularui.drawable.UITexture '
                       'PROGRESSBAR_ARROW_STANDARD=null;}',
 'GUIColorOverride.java': 'package gregtech.api.gui;public class GUIColorOverride{public static '
                          'GUIColorOverride get(net.minecraft.util.ResourceLocation r){return '
                          'null;}}'}
