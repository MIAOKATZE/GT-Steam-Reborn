package com.miaokatze.gtsr.common.dimension.prosperity.air;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import com.miaokatze.gtsr.main.GTSteamReborn;

import gregtech.api.enums.MaterialBuilder;
import gregtech.api.enums.Materials;
import gregtech.api.enums.SubTag;
import gregtech.api.enums.TextureSet;
import gregtech.api.interfaces.IMaterialHandler;

/**
 * 繁荣维度四空气材料注册器（dim1 S3，plan §5 命名表）。
 * <p>
 * 挂载契约：{@code Materials.add(new GTSRProsperityAirMaterials())} 在 CommonProxy.preInit 最早处
 * （GTSR 自身首次触碰 gregtech Materials 类之前）登记。GT5U 于 {@code Materials.init()}
 * （GTMod.onPreInitialization，参考库 GTMod.java:337）末尾回调
 * {@code mMaterialHandlers.forEach(IMaterialHandler::onMaterialsInit)}（参考库 Materials.java:1576，
 * BartWorks werkstoff 官方扩展点），本类在此构造四材料。
 * <p>
 * 形态 {@code .addCell().addGas().addSubTag(SubTag.TRANSPARENT)}（ToxicAir 同款，参考库
 * MaterialsInit.java:14426-14435）——显式 addGas 保证 mGas 生成通路必然非空（规避 Air 的
 * mGas 隐式通路未复核问题，调查笔记 B2 反例）。生成的 Fluid 由其后的
 * initMaterialProperties/addHasGasFluid 管线以 {@code setName 小写} 注册进 FluidRegistry，
 * 即流体注册名 = 材料名（wastesigh/thickgrease/metalgrit/umbralmire）。
 * <p>
 * 失败回退（plan S3）：逐个 try/catch，失败者跳过并告警，不炸全局材料链。
 * 日志锚点（plan §6.1 grep 点）：{@code [GTSR] prosperity air materials registered: 4}。
 */
public class GTSRProsperityAirMaterials implements IMaterialHandler {

    /** 荒原叹息（en: Waste Sigh）——锈蚀草原 rustedSteppe 产出（用户口径，运行时查表见 ProsperityAirLookup）。 */
    public static Materials WastesSigh;
    /** 浓稠油污（en: Thick Grease）——齿轮森林 gearworkForest 产出（同上）。 */
    public static Materials ThickGrease;
    /** 金属风沙（en: Metal Grit）——黄铜荒漠 brassWastes 产出（同上）。 */
    public static Materials MetalGrit;
    /** 至暗泥泞（en: Umbral Mire）——起雾沼泽 fumaroleSwamp 产出（同上）。 */
    public static Materials UmbralMire;

    @Override
    public void onMaterialsInit() {
        // R4 门禁（plan S3 实现期裁决点）已过：先单独注册命名表第一个 wastesigh 并经 runServer
        // 回读 FluidRegistry/mGas 非空（run/s3-r4-stage1.log），确认无下划线小写命名通路安全后放开其余 3 个。
        int registered = 0;
        registered += register("wastesigh", "Waste Sigh", 0x00b08a4a, material -> WastesSigh = material);
        registered += register("thickgrease", "Thick Grease", 0x004a3f28, material -> ThickGrease = material);
        registered += register("metalgrit", "Metal Grit", 0x0096613d, material -> MetalGrit = material);
        registered += register("umbralmire", "Umbral Mire", 0x002b2333, material -> UmbralMire = material);
        GTSteamReborn.LOG.info("[GTSR] prosperity air materials registered: " + registered);
    }

    /**
     * 单材料注册：MaterialBuilder 链（NetherAir/ToxicAir 同款），成功写入静态持有者并计 1；
     * 异常只告警跳过该材料（返回 0），不影响后续材料与 GT 全局注册链。
     */
    private static int register(String name, String defaultLocalName, int argb, Holder setter) {
        try {
            final Materials material = new MaterialBuilder().setName(name)
                .setDefaultLocalName(defaultLocalName)
                .setIconSet(TextureSet.SET_FLUID)
                .setARGB(argb)
                .addCell()
                .addGas()
                .addSubTag(SubTag.TRANSPARENT)
                .constructMaterial();
            setter.accept(material);
            return 1;
        } catch (Throwable t) {
            GTSteamReborn.LOG.error("[GTSR] prosperity air material " + name + " registration failed, skipped", t);
            return 0;
        }
    }

    /** dev 探针（S3 验收②）：回读 FluidRegistry 与静态持有者/mGas，逐材料输出非空证据。 */
    public static void logRegistrationProbe() {
        probe("wastesigh", WastesSigh);
        probe("thickgrease", ThickGrease);
        probe("metalgrit", MetalGrit);
        probe("umbralmire", UmbralMire);
    }

    private static void probe(String fluidName, Materials holder) {
        final Fluid fluid = FluidRegistry.getFluid(fluidName);
        final String gasName = holder != null && holder.mGas != null ? holder.mGas.getName() : "null";
        GTSteamReborn.LOG.info(
            "[GTSR] prosperity air dev probe: " + fluidName
                + " FluidRegistry="
                + (fluid != null ? "ok" : "null")
                + " mGas="
                + gasName);
    }

    /** 单参消费接口（避免额外依赖 java.util.function.Consumer 的触发时机差异，语义同其 accept）。 */
    @FunctionalInterface
    private interface Holder {

        void accept(Materials material);
    }
}
