package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.isAir;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.apache.commons.lang3.tuple.Pair;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.util.Vec3Impl;
import com.miaokatze.gtsr.api.compat.GTVersionCompat;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.gui.MTESteamSingularityEntanglerGui;
import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;
import com.miaokatze.gtsr.loader.BlockLoader;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;

/** Tier 1 steam entanglement machine. */
public class MTESteamSingularityEntangler extends MTESingularityMachineBase implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 3;
    private static final int VERTICAL_OFF_SET = 19;
    private static final int DEPTH_OFF_SET = 2;

    private static IStructureDefinition<MTESteamSingularityEntangler> STRUCTURE_DEFINITION;

    private int mCasingTierA = -1;
    private int mCasingTierB = -1;
    private int mCasingTierC = -1;

    public MTESteamSingularityEntangler(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTESteamSingularityEntangler(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESteamSingularityEntangler(mName);
    }

    @Override
    protected int getRequiredTier() {
        return 1;
    }

    @Override
    protected double getHeatMax() {
        return 0.005d;
    }

    @Override
    protected long getHeatHalfPoint() {
        return 200000L;
    }

    @Override
    protected boolean includeDenseSteam() {
        return false;
    }

    @Override
    protected ItemStack getAggregationOutput() {
        return GTSRItemList.SteamEntangledSingularity.get(1);
    }

    @Nullable
    private static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 1;
        return null;
    }

    @Nullable
    private static Integer getFrameTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Steel.mMetaItemSubID) return 1;
        return null;
    }

    @Nullable
    private static Integer getGlassTier(Block block, int meta) {
        if (block == GTVersionCompat.getReinforcedGlassBlock() && meta == GTVersionCompat.getReinforcedGlassMeta()) {
            return 1;
        }
        return null;
    }

    private static IStructureDefinition<MTESteamSingularityEntangler> createStructureDefinition() {
        int casingIndex = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
        List<Pair<Block, Integer>> casingTiers = new ArrayList<>();
        casingTiers.add(Pair.of(GregTechAPI.sBlockCasings2, 0));
        List<Pair<Block, Integer>> frameTiers = new ArrayList<>();
        frameTiers.add(Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID));
        List<Pair<Block, Integer>> glassTiers = new ArrayList<>();
        glassTiers.add(Pair.of(GTVersionCompat.getReinforcedGlassBlock(), GTVersionCompat.getReinforcedGlassMeta()));

        return StructureDefinition.<MTESteamSingularityEntangler>builder()
            .addShape(
                STRUCTURE_PIECE_MAIN,
                transpose(
                    new String[][] {
                        { "------------------------------", "------------------------------",
                            "------------------------------", "------------------------------",
                            "------------------------------", "------------------------------",
                            "------------------------------", "------------------------------",
                            "------------------------------", "------------------------------",
                            "------------------------------", "------------------------------",
                            "------------------------------", "------------------------------",
                            "------------------------------", "------------------------------",
                            "------------------------------", "------------------------------",
                            "------------------------------", "------------------------------",
                            "------------------------------", "------------------------------",
                            "------------------------------", "------------------------------" },
                        { "------------------------------", "------------------------------",
                            "------------------------------", "------------------------------",
                            "------------------------------", "------------------------------",
                            "------------------------------", "------------------------------",
                            "----------------AAAAA---------", "---------------AAAAAAA--------",
                            "--------------AAACCCAAA-------", "--------------AACCCCCAA-------",
                            "--------------AACCCCCAA-------", "--------------AACCCCCAA-------",
                            "--------------AAACCCAAA-------", "---------------AAAAAAA--------",
                            "----------------AAAAA---------", "------------------------------",
                            "------------------------------", "------------------------------",
                            "------------------------------", "------------------------------",
                            "------------------------------", "------------------------------" },
                        { "------------------------------", "------------------------------",
                            "------------------------------", "------------------------------",
                            "------------------------------", "------------------------------",
                            "----------------AAAAA---------", "--------------AAAAAAAAA-------",
                            "-------------AAA*****AAA------", "-------------AA*******AA------",
                            "------------AA**B***B**AA-----", "------------AA*********AA-----",
                            "------------AA*********AA-----", "------------AA*********AA-----",
                            "------------AA**B***B**AA-----", "-------------AA*******AA------",
                            "-------------AAA*****AAA------", "--------------AAAAAAAAA-------",
                            "----------------AAAAA---------", "------------------------------",
                            "------------------------------", "------------------------------",
                            "------------------------------", "------------------------------" },
                        { "------------------------------", "------------------------------",
                            "------------------------------", "------------------------------",
                            "----------------AAAAA---------", "--------------AAAAAAAAA-------",
                            "------------AAAA*****AAAA-----", "------------AA*********AA-----",
                            "-----------AA***********AA----", "-----------AA***********AA----",
                            "----------AA****B***B****AA---", "----------AA*************AA---",
                            "----------AA*************AA---", "----------AA*************AA---",
                            "----------AA****B***B****AA---", "-----------AA***********AA----",
                            "-----------AA***********AA----", "------------AA*********AA-----",
                            "------------AAAA*****AAAA-----", "--------------AAAAAAAAA-------",
                            "----------------AAAAA---------", "------------------------------",
                            "------------------------------", "------------------------------" },
                        { "------------------------------", "------------------------------",
                            "------------------------------", "----------------AAAAA---------",
                            "--------------AAAAAAAAA-------", "------------AAAAAAAAAAAAA-----",
                            "-----------AAAAA*****AAAAA----", "-----------AAA*********AAA----",
                            "----------AAA***********AAA---", "----------AAA***********AAA---",
                            "---------AAA****B***B****AAA--", "---------AAA*************AAA--",
                            "---------AAA*************AAA--", "---------AAA*************AAA--",
                            "---------AAA****B***B****AAA--", "----------AAA***********AAA---",
                            "----------AAA***********AAA---", "-----------AAA*********AAA----",
                            "-----------AAAAA*****AAAAA----", "------------AAAAAAAAAAAAA-----",
                            "--------------AAAAAAAAA-------", "----------------AAAAA---------",
                            "------------------------------", "------------------------------" },
                        { "------------------------------", "------------------------------",
                            "------------------------------", "----------------AAAAA---------",
                            "--------------AA*****AA-------", "------------AA*********AA-----",
                            "-----------A*************A----", "-----------A*************A----",
                            "----------A***************A---", "----------A***************A---",
                            "---------A******B***B******A--", "---------A*****************A--",
                            "---------A*****************A--", "---------A*****************A--",
                            "---------A******B***B******A--", "----------A***************A---",
                            "----------A***************A---", "-----------A*************A----",
                            "-----------A*************A----", "------------AA*********AA-----",
                            "--------------AA*****AA-------", "----------------AAAAA---------",
                            "------------------------------", "------------------------------" },
                        { "------------------------------", "------------------------------",
                            "----------------AAAAA---------", "--------------AAAAAAAAA-------",
                            "------------AAAA*****AAAA-----", "-----------AAA*********AAA----",
                            "----------AA*************AA---", "----------AA*************AA---",
                            "---------AA***************AA--", "---------AA***************AA--",
                            "--------AA******B***B******AA-", "--------AA*****************AA-",
                            "--------AA*****************AA-", "--------AA*****************AA-",
                            "--------AA******B***B******AA-", "---------AA***************AA--",
                            "---------AA***************AA--", "----------AA*************AA---",
                            "----------AA*************AA---", "-----------AAA*********AAA----",
                            "------------AAAA*****AAAA-----", "--------------AAAAAAAAA-------",
                            "----------------AAAAA---------", "------------------------------" },
                        { "------------------------------", "------------------------------",
                            "----------------AAAAA---------", "--------------AA-***-AA-------",
                            "------------AA*********AA-----", "-----------A*************A----",
                            "----------A***************A---", "----------A***************A---",
                            "---------A*****************A--", "---------A*****************A--",
                            "--------A*******B***B******-A-", "--------A*******************A-",
                            "--------A*******************A-", "--------A*******************A-",
                            "--------A-******B***B******-A-", "---------A*****************A--",
                            "---------A*****************A--", "----------A***************A---",
                            "----------A***************A---", "-----------A*************A----",
                            "------------AA*********AA-----", "--------------AA-***-AA-------",
                            "----------------AAAAA---------", "------------------------------" },
                        { "------------------------------", "----------------AAAAA---------",
                            "--------------AAAAAAAAA-------", "------------AAAA-***-AAAA-----",
                            "--CCC------AAA*********AAA----", "--CCC-----AA*************AA---",
                            "--CCC----AA***************AA--", "--CCC----AA***************AA--",
                            "--CCC---AA*****************AA-", "--CCC---AA*****************AA-",
                            "--CCC--AA*******B***B******-AA", "--CCCCCAA*******************AA",
                            "--CCCCCAA*******************AA", "--CCCCCAA*******************AA",
                            "-------AA-******B***B******-AA", "--------AA*****************AA-",
                            "--------AA*****************AA-", "---------AA***************AA--",
                            "---------AA***************AA--", "----------AA*************AA---",
                            "-----------AAA*********AAA----", "------------AAAA-***-AAAA-----",
                            "--------------AAAAAAAAA-------", "----------------AAAAA---------" },
                        { "------------------------------", "----------------ACCCA---------",
                            "--------------AAB***BAA-------", "--CCC-------AA**B***B**AA-----",
                            "-CAAAC-----A****B***B****A----", "-CAAAC----A*****B***B*****A---",
                            "-CAAAC---A******B***B******A--", "-CAAAC---A******B***B******A--",
                            "-CAAAC--A*******B***B*******A-", "-CAAAC--A*******B***B*******A-",
                            "-CAAACCABBBBBBBBBBBBBBBBBBBBBA", "-CAAAAAA********B***B********C",
                            "-CAAAAAA********B***B********C", "-CAAAAAA********B***B********C",
                            "--CCCCCABBBBBBBBBBBBBBBBBBBBBA", "--------A*******B***B*******A-",
                            "--------A*******B***B*******A-", "---------A******B***B******A--",
                            "---------A******B***B******A--", "----------A*****B***B*****A---",
                            "-----------A****B***B****A----", "------------AA**B***B**AA-----",
                            "--------------AAB***BAA-------", "----------------ACCCA---------" },
                        { "------------------------------", "---------------ACCCCCA--------",
                            "--CCC---------AA*****AA-------", "-CAAAC------AA*********AA-----",
                            "CA***AC----A*************A----", "CA***AC---A***************A---",
                            "CA***AC--A*****************A--", "CA***AC--A*****************A--",
                            "CA***AC-A*******************A-", "CA***ACAA*******************AA",
                            "CA***AAA********B***B********C", "CA***************************C",
                            "CA***************************C", "CA***************************C",
                            "-CAAAAAA********B***B********C", "--CCCCCAA*******************AA",
                            "--------A*******************A-", "---------A*****************A--",
                            "---------A*****************A--", "----------A***************A---",
                            "-----------A*************A----", "------------AA*********AA-----",
                            "--------------AA*****AA-------", "---------------ACCCCCA--------" },
                        { "------------------------------", "--------------AACCCCCAA-------",
                            "--CCC-------AAAA*****AAAA-----", "-CAAAC-----AAA*********AAA----",
                            "CA***AC---AA*************AA---", "CA***AC--AA***************AA--",
                            "CA***AC-AA*****************AA-", "CA***AC-AA*****************AA-",
                            "CA***ACAA*******************AA", "CA***ACAA*******************AA",
                            "CA***AAA********B***B********C", "CA***************************C",
                            "CA****************D**********C", "CA***************************C",
                            "-CAAAAAA********B***B********C", "--CCCCCAA*******************AA",
                            "-------AA*******************AA", "--------AA*****************AA-",
                            "--------AA*****************A--", "---------AA***************A---",
                            "----------AA*************AA---", "-----------AAA*********AAA----",
                            "------------AAAA*****AAAA-----", "--------------AACCCCCAA-------" },
                        { "------------------------------", "---------------ACCCCCA--------",
                            "--CCC---------AA*****AA-------", "-CAAAC------AA*********AA-----",
                            "CA***AC---BA*************AB---", "CA***AC---A***************A---",
                            "CA***AC--A*****************A--", "CA***AC--A*****************A--",
                            "CA***AC-A*******************A-", "CA***ACAA*******************AA",
                            "CA***AAA********B***B********C", "CA***************************C",
                            "CA***************************C", "CA***************************C",
                            "-CAAAAAA********B***B********C", "--CCCCCAA*******************AA",
                            "--------A*******************A-", "---------A*****************A--",
                            "---------A*****************A--", "----------A***************A---",
                            "----------BA*************AB---", "------------AA*********AA-----",
                            "--------------AA*****AA-------", "---------------ACCCCCA--------" },
                        { "------------------------------", "---------------BACCCAB--------",
                            "--CCC---------AAB***BAA-------", "-CAAAC------AA**B***B**AA-----",
                            "CA***AC---BA****B***B**--AB---", "CA***AC---A*****B***B****-A---",
                            "CA***AC--A******B***B*****-A--", "-CAAAC---A******B***B*****-A--",
                            "-CAAAC--A-******B***B******-A-", "-CAAAC-BA-******B***B******-AB",
                            "-CAAACCABBBBBBBBBBBBBBBBBBBBBA", "-CAAAAAA********B***B********C",
                            "-CAAAAAA********B***B********C", "-CAAAAAA********B***B********C",
                            "-BCCCCCABBBBBBBBBBBBBBBBBBBBBA", "-------BA-******B***B******-AB",
                            "--------A-******B***B******-A-", "---------A******B***B******A--",
                            "---------A******B***B******A--", "----------A*****B***B*****A---",
                            "----------BA****B***B****AB---", "------------AA**B***B**AA-----",
                            "--------------AAB***BAA-------", "---------------BACCCAB--------" },
                        { "------------------------------", "---------------BAAAAAB--------",
                            "--CCC---------AAAAAAAAA-------", "-CAAAC------AAAA*****AAAA-----",
                            "CA***AC---BAAA*********AAAB---", "CA***AC---AA*************AA---",
                            "CA***AC--AA***************AA--", "-CAAAC---AA***************AA--",
                            "--CCC---AA*****************AA-", "--CCC--BAA*****************AAB",
                            "-BCCCB-AA*******B***B*******AA", "--CCCCCAA*******************AA",
                            "--CCCCCAA*******************AA", "--CCCCCAA*******************AA",
                            "-B---B-AA*******B***B*******AA", "-------BAA*****************AAB",
                            "--------AA*****************AA-", "---------AA***************AA--",
                            "---------AA***************AA--", "----------AA*************AA---",
                            "----------BAAA*********AAAB---", "------------AAAA*****AAAA-----",
                            "--------------AAAAAAAAA-------", "---------------BAAAAAB--------" },
                        { "------------------------------", "---------------B-----B--------",
                            "--CCC-----------AAAAA---------", "-CAAAC--------AA*****AA-------",
                            "CA***AC---B-AA*********AA-B---", "CA***AC----A*************A----",
                            "CA***AC---A***************A---", "-CAAAC----A***************A---",
                            "--CCC----A*****************A--", "-------B-A*****************A-B",
                            "-B---B--A*******B***B*******A-", "--------A*******************A-",
                            "--------A*******************A-", "--------A*******************A-",
                            "-B---B--A*******B***B*******A-", "-------B-A*****************A-B",
                            "---------A*****************A--", "----------A***************A---",
                            "----------A***************A---", "-----------A*************A----",
                            "----------B-AA*********AA-B---", "--------------AA*****AA-------",
                            "----------------AAAAA---------", "---------------B-----B--------" },
                        { "------------------------------", "---------------B-----B--------",
                            "--CCC-----------AAAAA---------", "-CAAAC--------AAAAAAAAA-------",
                            "CA***AC---B-AAAA*****AAAA-B---", "CA***AC----AAA*********AAA----",
                            "CA***AC---AA*************AA---", "-CAAAC----AA*************AA---",
                            "--CCC----AA***************AA--", "-------B-AA***************AA-B",
                            "-B---B--AA******B***B******AA-", "--------AA*****************AA-",
                            "--------AA*****************AA-", "--------AA*****************AA-",
                            "-B---B--AA******B***B******AA-", "-------B-AA***************AA-B",
                            "---------AA***************AA--", "----------AA*************AA---",
                            "----------AA*************AA---", "-----------AAA*********AAA----",
                            "----------B-AAAA*****AAAA-B---", "--------------AAAAAAAAA-------",
                            "----------------AAAAA---------", "---------------B-----B--------" },
                        { "------------------------------", "---------------B-----B--------",
                            "--CCC-------------------------", "-CAAAC----------AAAAA---------",
                            "CA***AC---B---AA*****AA---B---", "CA***AC-----AA*********AA-----",
                            "CA***AC----A*************A----", "-CAAAC-----A*************A----",
                            "--CCC-----A***************A---", "-------B--A***************A--B",
                            "-B---B---A******B***B******A--", "---------A*****************A--",
                            "---------A*****************A--", "---------A*****************A--",
                            "-B---B---A******B***B******A--", "-------B--A***************A--B",
                            "----------A***************A---", "-----------A*************A----",
                            "-----------A*************A----", "------------AA*********AA-----",
                            "----------B---AA*****AA---B---", "----------------AAAAA---------",
                            "------------------------------", "---------------B-----B--------" },
                        { "------------------------------", "---------------B-----B--------",
                            "-BAAAB------------------------", "CA***AC---------AAAAA---------",
                            "CA***AC---B---AAAAAAAAA---B---", "CA***AC-----AAAAAAAAAAAAA-----",
                            "CA***AC----AAAAA*****AAAAA----", "-CAAAC-----AAA*********AAA----",
                            "--CCC-----AAA***********AAA---", "-------B--AAA***********AAA--B",
                            "-B---B---AAA****B***B****AAA--", "---------AAA*************AAA--",
                            "---------AAA*************AAA--", "---------AAA*************AAA--",
                            "-B---B---AAA****B***B****AAA--", "-------B--AAA***********AAA--B",
                            "----------AAA***********AAA---", "-----------AAA*********AAA----",
                            "-----------AAAAA*****AAAAA----", "------------AAAAAAAAAAAAA-----",
                            "----------B---AAAAAAAAA---B---", "----------------AAAAA---------",
                            "------------------------------", "---------------B-----B--------" },
                        { "------------------------------", "---------------B-----B--------",
                            "-BA~AB------------------------", "CA***AC-----------------------",
                            "CA***AC---B-----AAAAA-----B---", "CA***AC-------AAAAAAAAA-------",
                            "CA***AC-----AAAA*****AAAA-----", "-CAAAC------AA*********AA-----",
                            "--CCC------AA***********AA----", "-------B---AA***********AA---B",
                            "-B---B----AA****B***B****AA---", "----------AA*************AA---",
                            "----------AA*************AA---", "----------AA*************AA---",
                            "-B---B----AA****B***B****AA---", "-------B---AA***********AA---B",
                            "-----------AA***********AA----", "------------AA*********AA-----",
                            "------------AAAA*****AAAA-----", "--------------AAAAAAAAA-------",
                            "----------B-----AAAAA-----B---", "------------------------------",
                            "------------------------------", "---------------B-----B--------" },
                        { "------------------------------", "---------------B-----B--------",
                            "-BAAAB------------------------", "CA***AC-----------------------",
                            "CA***AC---B---------------B---", "CA***AC-----------------------",
                            "CA***AC-----B---AAAAA---B-----", "-CAAAC--------AAAAAAAAA-------",
                            "--CCC--------AAA*****AAA------", "-------B-----AA*******AA-----B",
                            "-B---B------AA**B***B**AA-----", "------------AA*********AA-----",
                            "------------AA*********AA-----", "------------AA*********AA-----",
                            "-B---B------AA**B***B**AA-----", "-------B-----AA*******AA-----B",
                            "-------------AAA-****AAA------", "--------------AAAAAAAAA-------",
                            "------------B---AAAAA---B-----", "------------------------------",
                            "----------B---------------B---", "------------------------------",
                            "------------------------------", "---------------B-----B--------" },
                        { "------------------------------", "--AAA----------B-----B--------",
                            "-BAAAB------------------------", "-CAAAC------------------------",
                            "-CAAAC----B---------------B---", "-CAAAC------------------------",
                            "-CAAAC------B-----------B-----", "--CCC-------------------------",
                            "----------------AAAAA---------", "-------B-------AAAAAAA-------B",
                            "-B---B--------AAACCCAAA-------", "--------------AACCCCCAA-------",
                            "--------------AACCCCCAA-------", "--------------AACCCCCAA-------",
                            "-B---B--------AAACCCAAA-------", "-------B-------AAAAAAA-------B",
                            "----------------AAAAA---------", "------------------------------",
                            "------------B-----------B-----", "------------------------------",
                            "----------B---------------B---", "------------------------------",
                            "------------------------------", "---------------B-----B--------" },
                        { "-AAAAA------------------------", "-AAAAA---------B-----B--------",
                            "-BAAAB------------------------", "--CCC-------------------------",
                            "--CCC-----B---------------B---", "--CCC-------------------------",
                            "--CCC-------B-----------B-----", "------------------------------",
                            "------------------------------", "-------B---------------------B",
                            "-B---B------------------------", "------------------------------",
                            "------------------------------", "------------------------------",
                            "-B---B------------------------", "-------B---------------------B",
                            "------------------------------", "------------------------------",
                            "------------B-----------B-----", "------------------------------",
                            "----------B---------------B---", "------------------------------",
                            "------------------------------", "---------------B-----B--------" } }))
            .addElement(
                'A',
                ofChain(
                    ofBlocksTiered(
                        MTESteamSingularityEntangler::getCasingTier,
                        casingTiers,
                        -1,
                        (t, tier) -> t.mCasingTierA = tier,
                        t -> t.mCasingTierA),
                    buildHatchAdder(MTESteamSingularityEntangler.class).atLeast(SingularityHatchElement.SteamInput)
                        .casingIndex(casingIndex)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTESteamSingularityEntangler.class).atLeast(SingularityHatchElement.SteamInputBus)
                        .casingIndex(casingIndex)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTESteamSingularityEntangler.class).atLeast(SingularityHatchElement.SteamOutputBus)
                        .casingIndex(casingIndex)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTESteamSingularityEntangler.class)
                        .atLeast(SingularityHatchElement.SteamOutputHatch)
                        .casingIndex(casingIndex)
                        .hint(2)
                        .build()))
            .addElement(
                'B',
                ofBlocksTiered(
                    MTESteamSingularityEntangler::getFrameTier,
                    frameTiers,
                    -1,
                    (t, tier) -> t.mCasingTierB = tier,
                    t -> t.mCasingTierB))
            .addElement(
                'C',
                ofBlocksTiered(
                    MTESteamSingularityEntangler::getGlassTier,
                    glassTiers,
                    -1,
                    (t, tier) -> t.mCasingTierC = tier,
                    t -> t.mCasingTierC))
            .addElement(
                'D',
                ofChain(
                    // 特殊定位块：接受失控奇点方块或空气（砖高炉式容错：机器运行期间此处生成奇点，结构判定仍有效）
                    ofBlock(BlockLoader.blockRunawaySingularity, 0),
                    isAir()))
            .addElement('-', isAir())
            .addElement('*', isAir())
            .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public IStructureDefinition<MTESingularityMachineBase> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) STRUCTURE_DEFINITION = createStructureDefinition();
        return (IStructureDefinition<MTESingularityMachineBase>) (IStructureDefinition<?>) STRUCTURE_DEFINITION;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            HORIZONTAL_OFF_SET,
            VERTICAL_OFF_SET,
            DEPTH_OFF_SET,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        mCasingTierA = -1;
        mCasingTierB = -1;
        mCasingTierC = -1;
        mPressureSteamInputs.clear();
        mTier = getRequiredTier();

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) return;

        int tier = mCasingTierA;
        if (mCasingTierB != tier || mCasingTierC != tier || tier != getRequiredTier()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        mTier = tier;

        if ((mInputHatches.isEmpty() && mPressureSteamInputs.isEmpty() && mDualInputHatches.isEmpty())
            || mOutputBusses.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        updateAllHatchTextures();
    }

    @Override
    @Nullable
    protected EntanglementSpec getEntanglementSpec() {
        // D 定位块：形状偏移 (a+15, b-8, c+10)，经 ExtendedFacing 换算世界偏移（与 checkPiece 同源映射）
        Vec3Impl off = getExtendedFacing().getWorldOffset(new Vec3Impl(15, -8, 10));
        return new EntanglementSpec(off.get0(), off.get1(), off.get2(), 10.0D, 0.0D, 0.0D, -1, -1, "white");
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        String keyPrefix = getTooltipKeyPrefix();
        MultiblockTooltipBuilder tt = super.createTooltip();
        tt.addSeparator()
            .beginStructureBlock(24, 30, 23, false)
            .addController(StatCollector.translateToLocal(keyPrefix + "ctrl"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.shared.steam_input_hatch"),
                StatCollector.translateToLocal(keyPrefix + "steam_input"),
                1)
            .addOutputBus(StatCollector.translateToLocal(keyPrefix + "output_bus"), 1)
            .addStructureInfo("")
            .addStructureInfo(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal(keyPrefix + "desc6"))
            .addStructureInfo(
                EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal(keyPrefix + "tier1_blocks"))
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .toolTipFinisher(
                EnumChatFormatting.AQUA + "GT"
                    + EnumChatFormatting.GREEN
                    + "-"
                    + EnumChatFormatting.GOLD
                    + "Steam"
                    + EnumChatFormatting.RED
                    + "-"
                    + EnumChatFormatting.BLUE
                    + "Reborn");
        return tt;
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        return processAggregationCycle();
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        // Old mode keys are intentionally ignored; this machine is permanently tier 1 aggregation.
        mTier = 1;
    }

    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new MTESteamSingularityEntanglerGui(this);
    }
}
