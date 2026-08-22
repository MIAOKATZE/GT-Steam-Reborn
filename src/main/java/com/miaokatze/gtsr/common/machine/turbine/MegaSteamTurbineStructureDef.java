package com.miaokatze.gtsr.common.machine.turbine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Dynamo;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.common.machine.MTEMegaSteamTurbineArray;

import bartworks.system.material.Werkstoff;
import bartworks.system.material.WerkstoffLoader;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.util.GTUtility;

/**
 * 巨型蒸汽轮机「结构定义与等级映射域」伴生类（O2-04/A01 段 2 自 MTEMegaSteamTurbineArray 外移）。
 *
 * <p>
 * 三件 shape（base/stack/cap）、惰性 casing 表与四套等级映射的单一事实来源。
 * 机器侧 checkMachine/construct/survivalConstruct 建造入口与偏移算式留机器类不搬。
 */
public final class MegaSteamTurbineStructureDef {

    private MegaSteamTurbineStructureDef() {}

    public static final String STRUCTURE_PIECE_BASE = "base";
    public static final String STRUCTURE_PIECE_STACK = "stack";
    public static final String STRUCTURE_PIECE_CAP = "cap";
    public static final int BASE_TOTAL_HEIGHT = 9;
    public static final int STACK_LAYER_HEIGHT = 4;
    public static final int SOLID_STEEL_CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);

    private static IStructureDefinition<MTEMegaSteamTurbineArray> STRUCTURE_DEFINITION = null;

    public static IStructureDefinition<MTEMegaSteamTurbineArray> definition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<MTEMegaSteamTurbineArray>builder()
                .addShape(
                    STRUCTURE_PIECE_BASE,
                    transpose(
                        new String[][] {
                            { "EEEEBBBBBEEEE", "E EBBBBBBBE E", "EEB       BEE", "EB BBBBBBB BE", "BB DCCCCCD BB",
                                "BB DCDDDCD BB", "BB DCDDDCD BB", "BB DCDDDCD BB", "BB DCCCCCD BB", "EB BBBBBBB BE",
                                "EEB       BEE", "E EBBBBBBBE E", "EEEEBBBBBEEEE" },
                            { "E   BBBBB   E", "  EBBBBBBBE  ", " EB       BE ", " B BBBBBBB B ", "BB DCCCCCD BB",
                                "BB DCDDDCD BB", "BB DCDDDCD BB", "BB DCDDDCD BB", "BB DCCCCCD BB", " B BBBBBBB B ",
                                " EB       BE ", "  EBBBBBBBE  ", "E   BBBBB   E" },
                            { "E   BBBBB   E", "  EBBBBBBBE  ", " EB       BE ", " B BBBBBBB B ", "BB BCCCCCB BB",
                                "BB BCBBBCB BB", "BB BCBBBCB BB", "BB BCBBBCB BB", "BB BCCCCCB BB", " B BBBBBBB B ",
                                " EB       BE ", "  EBBBBBBBE  ", "E   BBBBB   E" },
                            { "E           E", "  E  BBB  E  ", " E BBEEEBB E ", "  BBEEEEEBB  ", "  BEEEEEEEB  ",
                                " BEEEEEEEEEB ", " BEEEEDEEEEB ", " BEEEEEEEEEB ", "  BEEEEEEEB  ", "  BBEEEEEBB  ",
                                " E BBEEEBB E ", "  E  BBB  E  ", "E           E" },
                            { "EEEBBBBBBBEEE", "E BBBBBBBBB E", "EBB       BBE", "BB         BB", "BB         BB",
                                "BB         BB", "BB    D    BB", "BB         BB", "BB         BB", "BB         BB",
                                "EBB       BBE", "E BBBBBBBBB E", "EEEBBBBBBBEEE" },
                            { "E  BBB~BBB  E", "  BBBBCBBBB  ", " BBBDBCBDBBB ", "BBBCDCDDCBBB", "BBBBCDCDCBBBB",
                                "BBBBBCCCBBBBB", "BBCCCCDCCCCBB", "BBBBBCCCBBBBB", "BBBBCDCDCBBBB", "BBBCDCDDCBBB",
                                " BBBDBCBDBBB ", "  BBBBBBBBB  ", "E  BBBBBBB  E" },
                            { "E  BBBBBBB  E", "  BBBBBBBBB  ", " BBBBBBBBBBB ", "BBBBBBBBBBBBB", "BBBBBBBBBBBBB",
                                "BBBBBBBBBBBBB", "BBBBBBBBBBBBB", "BBBBBBBBBBBBB", "BBBBBBBBBBBBB", "BBBBBBBBBBBBB",
                                " BBBBBBBBBBB ", "  BBBBBBBBB  ", "E  BBBBBBB  E" } }))
                .addShape(
                    STRUCTURE_PIECE_STACK,
                    transpose(
                        new String[][] {
                            { "EEEEBBBBBEEEE", "E EBBBBBBBE E", "EEB       BEE", "EB BBBBBBB BE", "BB DCCCCCD BB",
                                "BB DCDDDCD BB", "BB DCDDDCD BB", "BB DCDDDCD BB", "BB DCCCCCD BB", "EB BBBBBBB BE",
                                "EEB       BEE", "E EBBBBBBBE E", "EEEEBBBBBEEEE" },
                            { "E   BBBBB   E", "  EBBBBBBBE  ", " EB       BE ", " B BBBBBBB B ", "BB DCCCCCD BB",
                                "BB DCDDDCD BB", "BB DCDDDCD BB", "BB DCDDDCD BB", "BB DCCCCCD BB", " B BBBBBBB B ",
                                " EB       BE ", "  EBBBBBBBE  ", "E   BBBBB   E" },
                            { "E   BBBBB   E", "  EBBBBBBBE  ", " EB       BE ", " B BBBBBBB B ", "BB BCCCCCB BB",
                                "BB BCBBBCB BB", "BB BCBBBCB BB", "BB BCBBBCB BB", "BB BCCCCCB BB", " B BBBBBBB B ",
                                " EB       BE ", "  EBBBBBBBE  ", "E   BBBBB   E" },
                            { "E           E", "  E  BBB  E  ", " E BBEEEBB E ", "  BBEEEEEBB  ", "  BEEEEEEEB  ",
                                " BEEEEEEEEEB ", " BEEEEDEEEEB ", " BEEEEEEEEEB ", "  BEEEEEEEB  ", "  BBEEEEEBB  ",
                                " E BBEEEBB E ", "  E  BBB  E  ", "E           E" } }))
                .addShape(
                    STRUCTURE_PIECE_CAP,
                    transpose(
                        new String[][] {
                            { "             ", "             ", "    CCBCC    ", "    BBCBB    ", "  CBBBCBBBC  ",
                                "  CBBBBBBBC  ", "  BCCBBBCCB  ", "  CBBBBBBBC  ", "  CBBBCBBBC  ", "    BBCBB    ",
                                "    CCBCC    ", "             ", "             " },
                            { "             ", "    BBBBB    ", "   BBEEEBB   ", "  BBEEEEEBB  ", " BBEEEEEEEBB ",
                                " BEEEEEEEEEB ", " BEEEEDEEEEB ", " BEEEEEEEEEB ", " BBEEEEEEEBB ", "  BBEEEEEBB  ",
                                "   BBEEEBB   ", "    BBBBB    ", "             " } }))
                .addElement(
                    'B',
                    ofChain(
                        // casing-first: NEI 投影优先渲染外壳；真实 hatch 坐标上 casing 匹配失败后继续匹配 hatch adder。
                        onElementPass(
                            MTEMegaSteamTurbineArray::onCasingAdded,
                            ofBlocksTiered(
                                MegaSteamTurbineStructureDef::casingTier,
                                allowedCasings(),
                                -1,
                                (t, tier) -> t.mCasingTier = Math.max(t.mCasingTier, tier),
                                t -> t.mCasingTier)),
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
                            .casingIndex(SOLID_STEEL_CASING_INDEX)
                            .hint(1)
                            .build(),
                        buildHatchAdder(MTEMegaSteamTurbineArray.class).atLeast(InputBus)
                            .casingIndex(SOLID_STEEL_CASING_INDEX)
                            .hint(1)
                            .build(),
                        buildHatchAdder(MTEMegaSteamTurbineArray.class).atLeast(Dynamo)
                            .casingIndex(SOLID_STEEL_CASING_INDEX)
                            .hint(1)
                            .build()))
                .addElement(
                    'C',
                    onElementPass(
                        MTEMegaSteamTurbineArray::onCasingAdded,
                        ofBlocksTiered(
                            MegaSteamTurbineStructureDef::pipeTier,
                            PIPE_CASINGS,
                            -1,
                            (t, tier) -> t.mPipeTier = tier,
                            t -> t.mPipeTier)))
                .addElement(
                    'D',
                    onElementPass(
                        MTEMegaSteamTurbineArray::onCasingAdded,
                        ofBlocksTiered(
                            MegaSteamTurbineStructureDef::gearTier,
                            GEAR_CASINGS,
                            -1,
                            (t, tier) -> t.mGearTier = tier,
                            t -> t.mGearTier)))
                .addElement(
                    'E',
                    onElementPass(
                        MTEMegaSteamTurbineArray::onCasingAdded,
                        ofBlocksTiered(
                            MegaSteamTurbineStructureDef::frameTier,
                            frameCasings(),
                            -1,
                            (t, tier) -> t.mFrameTier = tier,
                            t -> t.mFrameTier)))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    // 延迟初始化：避免在 MTE 构造时（sAfterGTPreload 遍历期间）触发 WerkstoffLoader 类加载，
    // 否则 Werkstoff 构造函数会向正在遍历的 sAfterGTPreload 列表添加 Runnable，导致
    // ConcurrentModificationException。WerkstoffLoader 在 bartworks preInit 中完成初始化后，
    // definition() 首次调用时才安全地引用 WerkstoffLoader.BWBlockCasings。
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<Pair<Block, Integer>> ALLOWED_CASINGS = null;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<Pair<Block, Integer>> allowedCasings() {
        if (ALLOWED_CASINGS == null) {
            ALLOWED_CASINGS = ImmutableList.of(
                Pair.of(GregTechAPI.sBlockCasings2, 0), // Tier 1 - Steel
                Pair.of(GregTechAPI.sBlockCasings1, 2), // Tier 2 - Stainless Steel
                Pair.of(GregTechAPI.sBlockCasings4, 1), // Tier 3 - Titanium
                Pair.of(GregTechAPI.sBlockCasings4, 2), // Tier 4 - Tungstensteel
                Pair.of(GregTechAPI.sBlockCasings4, 0), // Tier 5 - Chrome
                Pair.of(GregTechAPI.sBlockCasings8, 6), // Tier 6 - Advanced Rhodium Palladium
                Pair.of(GregTechAPI.sBlockCasings8, 7), // Tier 7 - Advanced Iridium
                Pair.of(GregTechAPI.sBlockCasings4, 14), // Tier 8 - Mining Osmiridium (UV)
                Pair.of(GregTechAPI.sBlockCasings1, 9), // Tier 9 - UHV Machine Casing
                Pair.of(GregTechAPI.sBlockCasingsNH, 10), // Tier 10 - UEV Machine Casing
                Pair.of(GregTechAPI.sBlockCasings8, 3), // Tier 11 - Mining Black Plutonium (UIV)
                Pair.of(GregTechAPI.sBlockCasings8, 10)); // Tier 12 - Radiant Naquadah Alloy (UMV)
        }
        return ALLOWED_CASINGS;
    }

    private static final List<Pair<Block, Integer>> PIPE_CASINGS = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockCasings2, 13),
        Pair.of(GregTechAPI.sBlockCasings2, 14),
        Pair.of(GregTechAPI.sBlockCasings2, 15));

    private static final List<Pair<Block, Integer>> GEAR_CASINGS = ImmutableList
        .of(Pair.of(GregTechAPI.sBlockCasings2, 3), Pair.of(GregTechAPI.sBlockCasings2, 4));

    // FRAME_CASINGS 与 ALLOWED_CASINGS 一样延迟初始化：等级 6 框架需要解析
    // WerkstoffLoader.RhodiumPlatedPalladium 材质并使用 BW 框架方块 bw.frames，
    // 不能在 MTE 类加载时触发 WerkstoffLoader 类加载。
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<Pair<Block, Integer>> FRAME_CASINGS = null;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<Pair<Block, Integer>> frameCasings() {
        if (FRAME_CASINGS == null) {
            FRAME_CASINGS = ImmutableList.of(
                Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID), // 1
                Pair.of(GregTechAPI.sBlockFrames, Materials.Aluminium.mMetaItemSubID), // 2
                Pair.of(GregTechAPI.sBlockFrames, Materials.StainlessSteel.mMetaItemSubID), // 3
                Pair.of(GregTechAPI.sBlockFrames, Materials.Titanium.mMetaItemSubID), // 4
                Pair.of(GregTechAPI.sBlockFrames, Materials.TungstenSteel.mMetaItemSubID), // 5
                Pair.of(tier6FrameBlock(), tier6FrameMeta()), // 6
                Pair.of(GregTechAPI.sBlockFrames, Materials.Iridium.mMetaItemSubID), // 7
                Pair.of(GregTechAPI.sBlockFrames, Materials.Osmium.mMetaItemSubID), // 8 - UV
                Pair.of(GregTechAPI.sBlockFrames, Materials.Neutronium.mMetaItemSubID), // 9 - UHV
                Pair.of(GregTechAPI.sBlockFrames, Materials.Bedrockium.mMetaItemSubID), // 10 - UEV
                Pair.of(GregTechAPI.sBlockFrames, 397), // 11 - Infinity (UIV)
                Pair.of(GregTechAPI.sBlockFrames, 588)); // 12 - UMV (SpaceTime)
        }
        return FRAME_CASINGS;
    }

    private static Block TIER6_FRAME_BLOCK;
    private static Integer TIER6_FRAME_META;

    private static Block tier6FrameBlock() {
        if (TIER6_FRAME_BLOCK == null) {
            TIER6_FRAME_BLOCK = GregTechAPI.sBlockFramesBW;
            if (TIER6_FRAME_BLOCK == null) TIER6_FRAME_BLOCK = GameRegistry.findBlock("gregtech", "bw.frames");
        }
        return TIER6_FRAME_BLOCK;
    }

    private static int tier6FrameMeta() {
        if (TIER6_FRAME_META == null) {
            Werkstoff werkstoff = WerkstoffLoader.RhodiumPlatedPalladium;
            TIER6_FRAME_META = werkstoff != null ? (int) werkstoff.getmID() : 88;
        }
        return TIER6_FRAME_META;
    }

    // 原 4 个 public static 等级映射实测零外部引用（仅结构 builder 方法引用使用），随搬降包私有。
    @Nullable
    static Integer casingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 1;
        if (block == GregTechAPI.sBlockCasings1 && meta == 2) return 2;
        if (block == GregTechAPI.sBlockCasings4) {
            if (meta == 1) return 3;
            if (meta == 2) return 4;
            if (meta == 0) return 5;
            if (meta == 14) return 8;
        }
        if (block == GregTechAPI.sBlockCasings8) {
            if (meta == 3) return 11; // Mining Black Plutonium (UIV)
            if (meta == 6) return 6;
            if (meta == 7) return 7;
            if (meta == 10) return 12; // Radiant Naquadah Alloy (UMV)
        }
        if (block == GregTechAPI.sBlockCasings1 && meta == 9) return 9;
        if (block == GregTechAPI.sBlockCasingsNH && meta == 10) return 10;
        return null;
    }

    @Nullable
    static Integer pipeTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 13) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 14) return 2;
        if (block == GregTechAPI.sBlockCasings2 && meta == 15) return 3;
        return null;
    }

    @Nullable
    static Integer gearTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 3) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 4) return 2;
        return null;
    }

    @Nullable
    static Integer frameTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockFrames) {
            if (meta == Materials.Steel.mMetaItemSubID) return 1;
            if (meta == Materials.Aluminium.mMetaItemSubID) return 2;
            if (meta == Materials.StainlessSteel.mMetaItemSubID) return 3;
            if (meta == Materials.Titanium.mMetaItemSubID) return 4;
            if (meta == Materials.TungstenSteel.mMetaItemSubID) return 5;
            if (meta == Materials.Iridium.mMetaItemSubID) return 7;
            if (meta == Materials.Osmium.mMetaItemSubID) return 8;
            if (meta == Materials.Neutronium.mMetaItemSubID) return 9;
            if (meta == Materials.Bedrockium.mMetaItemSubID) return 10;
            if (meta == 397) return 11; // Infinity (UIV)
            if (meta == 588) return 12; // SpaceTime
        }
        if (block == tier6FrameBlock() && meta == tier6FrameMeta()) return 6;
        return null;
    }
}
