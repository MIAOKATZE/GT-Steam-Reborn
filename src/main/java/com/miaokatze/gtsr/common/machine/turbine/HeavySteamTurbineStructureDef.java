package com.miaokatze.gtsr.common.machine.turbine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.isAir;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.withChannel;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.common.machine.MTEHeavySteamTurbine;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualDefinition;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputSlave;

/** Every structural material shares one tier accumulator; mixed bronze/steel cannot form. */
public final class HeavySteamTurbineStructureDef {

    public static final int BRONZE_CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10);
    public static final int STEEL_CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
    private static IStructureDefinition<MTEHeavySteamTurbine> definition;

    private HeavySteamTurbineStructureDef() {}

    private static IStructureElement<MTEHeavySteamTurbine> material(Block bronze, int bronzeMeta, Block steel,
        int steelMeta) {
        List<Pair<Block, Integer>> blocks = ImmutableList.of(Pair.of(bronze, bronzeMeta), Pair.of(steel, steelMeta));
        return withChannel("casing", ofBlocksTiered((block, meta) -> {
            if (block == bronze && meta == bronzeMeta) return 1;
            if (block == steel && meta == steelMeta) return 2;
            return null;
        }, blocks, -1, (machine, tier) -> machine.mMaterialTier = tier, machine -> machine.mMaterialTier));
    }

    public static IStructureDefinition<MTEHeavySteamTurbine> definition() {
        if (definition == null) {
            IStructureElement<MTEHeavySteamTurbine> casing = material(
                GregTechAPI.sBlockCasings1,
                10,
                GregTechAPI.sBlockCasings2,
                0);
            IStructureElement<MTEHeavySteamTurbine> pipe = material(
                GregTechAPI.sBlockCasings2,
                12,
                GregTechAPI.sBlockCasings2,
                13);
            IStructureElement<MTEHeavySteamTurbine> casingWithHatches = ofChain(
                buildHatchAdder(MTEHeavySteamTurbine.class)
                    .atLeast(
                        InputHatch.withMteClasses(
                            MTEHatchInput.class,
                            MTEHatchCraftingInputME.class,
                            MTEHatchCraftingInputSlave.class),
                        OutputHatch,
                        MTEHeavySteamTurbine.DynamoElement.OUTPUT)
                    .casingIndex(BRONZE_CASING_INDEX)
                    .hint(1)
                    .build(),
                casing);
            definition = StructureDefinition.<MTEHeavySteamTurbine>builder()
                .addShape(
                    "main",
                    transpose(
                        MachineVisualDefinition.get("heavy_steam_turbine")
                            .shapeLayers()))
                .addElement('B', casingWithHatches)
                .addElement('F', casingWithHatches)
                .addElement('H', casingWithHatches)
                .addElement('R', casing)
                .addElement('C', pipe)
                .addElement('S', pipe)
                .addElement('D', material(GregTechAPI.sBlockCasings2, 2, GregTechAPI.sBlockCasings2, 3))
                .addElement(
                    'E',
                    material(
                        GregTechAPI.sBlockFrames,
                        Materials.Bronze.mMetaItemSubID,
                        GregTechAPI.sBlockFrames,
                        Materials.Steel.mMetaItemSubID))
                .addElement('G', ofBlock(Blocks.glass, 0))
                .addElement('A', isAir())
                .build();
        }
        return definition;
    }
}
