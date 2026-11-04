package com.miaokatze.gtsr.common.machine.turbine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.isAir;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.withChannel;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.SteamInputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import net.minecraft.item.ItemStack;

import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.common.machine.MTEMegaSteamTurbineArray;
import com.miaokatze.gtsr.common.machine.base.MTEOverpressureTurbineInputHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualDefinition;
import com.miaokatze.gtsr.common.structure.RebuiltMachineGlass;
import com.miaokatze.gtsr.common.structure.RebuiltMachineMaterials;

import gregtech.api.GregTechAPI;
import gregtech.api.util.GTUtility;
import gregtech.common.blocks.ItemMachines;

/**
 * 巨型蒸汽轮机「结构定义与等级映射域」伴生类（O2-04/A01 段 2 自 MTEMegaSteamTurbineArray 外移）。
 *
 * <p>
 * 五份完整轴向 shape；标准机械壳与三档材料映射复用 RebuiltMachineMaterials。
 * 机器侧 checkMachine/construct/survivalConstruct 共用完整 piece 和固定控制器偏移。
 */
public final class MegaSteamTurbineStructureDef {

    private MegaSteamTurbineStructureDef() {}

    public static final int HORIZONTAL_OFFSET = 25;
    public static final int VERTICAL_OFFSET = 22;
    public static final int DEPTH_OFFSET = 1;
    public static final int STACK_PITCH = 12;
    public static final int SOLID_STEEL_CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
    private static IStructureDefinition<MTEMegaSteamTurbineArray> STRUCTURE_DEFINITION;

    public static String piece(int extraStacks) {
        return "axial_" + extraStacks;
    }

    public static int constructExtraStacks(ItemStack trigger) {
        int selection = trigger == null ? 1 : ChannelDataAccessor.getChannelData(trigger, "height");
        return (int) Math.max(0L, Math.min(MTEMegaSteamTurbineArray.MAX_EXTRA_STACKS, ((long) selection - 1L) / 4L));
    }

    public static MachineVisualDefinition visual(int extraStacks) {
        return MachineVisualDefinition.get("mega_steam_turbine_" + extraStacks);
    }

    public static IStructureDefinition<MTEMegaSteamTurbineArray> definition() {
        if (STRUCTURE_DEFINITION == null) {
            StructureDefinition.Builder<MTEMegaSteamTurbineArray> builder = StructureDefinition.builder();
            for (int count = 0; count <= MTEMegaSteamTurbineArray.MAX_EXTRA_STACKS; count++) {
                builder.addShape(piece(count), transpose(visual(count).shapeLayers()));
            }
            STRUCTURE_DEFINITION = builder.addElement(
                'U',
                ofChain(
                    buildHatchAdder(MTEMegaSteamTurbineArray.class)
                        .atLeast(MTEMegaSteamTurbineArray.MegaSteamTurbineArrayHatchElement.PressureSteamInput)
                        .casingIndex(SOLID_STEEL_CASING_INDEX)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTEMegaSteamTurbineArray.class)
                        .atLeast(MTEMegaSteamTurbineArray.MegaSteamTurbineArrayHatchElement.OverpressureInput)
                        .casingIndex(SOLID_STEEL_CASING_INDEX)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTEMegaSteamTurbineArray.class)
                        .atLeast(MTEMegaSteamTurbineArray.MegaSteamTurbineArrayHatchElement.CoolingHatch)
                        .casingIndex(SOLID_STEEL_CASING_INDEX)
                        .hint(2)
                        .build(),
                    buildHatchAdder(MTEMegaSteamTurbineArray.class).atLeast(InputHatch, OutputHatch)
                        .hatchItemFilterAnd(t -> stack -> {
                            var hatch = ItemMachines.getMetaTileEntity(stack);
                            return !(hatch instanceof MTEOverpressureTurbineInputHatch)
                                && !(hatch instanceof MTESteamCoolingHatch);
                        })
                        .casingIndex(SOLID_STEEL_CASING_INDEX)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTEMegaSteamTurbineArray.class).atLeast(SteamInputBus)
                        .casingIndex(SOLID_STEEL_CASING_INDEX)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTEMegaSteamTurbineArray.class)
                        .atLeast(MTEMegaSteamTurbineArray.MegaSteamTurbineArrayHatchElement.DynamoOutput)
                        .casingIndex(SOLID_STEEL_CASING_INDEX)
                        .hint(1)
                        .build(),
                    onElementPass(
                        MTEMegaSteamTurbineArray::onCasingAdded,
                        withChannel(
                            "casing",
                            ofBlocksTiered(
                                RebuiltMachineMaterials::voltageTier,
                                RebuiltMachineMaterials.voltageChoices(),
                                -1,
                                (t, tier) -> t.mCasingTier = tier,
                                t -> t.mCasingTier)))))
                .addElement(
                    'H',
                    withChannel(
                        "material",
                        ofBlocksTiered(
                            RebuiltMachineMaterials::shellTier,
                            RebuiltMachineMaterials.shellChoices(),
                            -1,
                            (t, tier) -> t.setMaterialTier(tier),
                            t -> t.mMaterialTier)))
                .addElement(
                    'R',
                    withChannel(
                        "material",
                        ofBlocksTiered(
                            RebuiltMachineMaterials::shellTier,
                            RebuiltMachineMaterials.shellChoices(),
                            -1,
                            (t, tier) -> t.setMaterialTier(tier),
                            t -> t.mMaterialTier)))
                .addElement(
                    'S',
                    withChannel(
                        "material",
                        ofBlocksTiered(
                            RebuiltMachineMaterials::pipeTier,
                            RebuiltMachineMaterials.pipeChoices(),
                            -1,
                            (t, tier) -> t.setMaterialTier(tier),
                            t -> t.mMaterialTier)))
                .addElement('A', isAir())
                .addElement(
                    'B',
                    withChannel(
                        "material",
                        ofBlocksTiered(
                            RebuiltMachineMaterials::shellTier,
                            RebuiltMachineMaterials.shellChoices(),
                            -1,
                            (t, tier) -> t.setMaterialTier(tier),
                            t -> t.mMaterialTier)))
                .addElement(
                    'G',
                    withChannel(
                        "glass",
                        ofBlocksTiered(
                            RebuiltMachineGlass::tier,
                            RebuiltMachineGlass.choices(),
                            -1,
                            (t, tier) -> t.mGlassTier = tier,
                            t -> t.mGlassTier)))
                .addElement(
                    'C',
                    withChannel(
                        "material",
                        ofBlocksTiered(
                            RebuiltMachineMaterials::pipeTier,
                            RebuiltMachineMaterials.pipeChoices(),
                            -1,
                            (t, tier) -> t.setMaterialTier(tier),
                            t -> t.mMaterialTier)))
                .addElement(
                    'D',
                    withChannel(
                        "material",
                        ofBlocksTiered(
                            RebuiltMachineMaterials::gearTier,
                            RebuiltMachineMaterials.gearChoices(),
                            -1,
                            (t, tier) -> t.setMaterialTier(tier),
                            t -> t.mMaterialTier)))
                .addElement(
                    'E',
                    withChannel(
                        "material",
                        ofBlocksTiered(
                            RebuiltMachineMaterials::frameTier,
                            RebuiltMachineMaterials.frameChoices(),
                            -1,
                            (t, tier) -> t.setMaterialTier(tier),
                            t -> t.mMaterialTier)))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

}
