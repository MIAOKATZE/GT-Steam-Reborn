package com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachineShapes;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;

/**
 * 废墟族的单个模板（<b>P8</b>）：一条"由既有结构破败化派生出来的破坏结构"。
 * <p>
 * 形状字段（{@code name/sizeX/sizeY/sizeZ/layers/charAt}）与 {@code CityVariants.Variant} /
 * {@code ProsperityOutpostPlacer.Outpost} / {@code RuinedMachineShapes.Shape} <b>逐字段同形</b>
 * （含"{@code layers[0]} = 顶层"这一条约定）——不是抄样式：{@code RosterIntegrityCheck} 的派生器、
 * {@code StructureViewerExport} 的第五条腿与 {@code RuinFamilyCheck} 的 TE 组都按这一个形状读，
 * 换任何一处口径就要在工具里多写一个分支（plan §2.4 判据 4 的反面）。
 * <p>
 * 本族独有的三个字段是<b>谱系与算子申报</b>：{@link #mother()}（母体注册名）、{@link #ops()}
 * （用了哪几个 {@link RuinDamageOps} 算子、按什么顺序）、{@link #damageOps()}（算子步数，
 * 展示页与机检读的"损毁档强度"）。派生在构造时<b>一次</b>完成并定盘（{@link RuinDamageOps#derive}），
 * 运行期不再改形状；运行期只决定"额外缺多少块"（{@link CityVariants#MISSING_RATES} 档）。
 * <p>
 * <b>母体为什么只有机型 + outpost 两族</b>：城 26 变体的 {@code Variant.layers} 是<b>包内字段</b>
 * （{@code CityVariants} 的既有封装，本片禁止改旧模板文件），跨包只能反射读——而在生产类的构造期
 * 反射读另一个包的私有字段，等于给旧名册开了一条"新族可以改写母体"的路。机型 5 + outpost 6 = 11 个
 * 母体足够派生 8 条破坏结构，故本片不碰那层封装。
 */
public final class RuinTemplate {

    public final String name;
    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;
    /** layers[0] = 顶层；layers[layer] 为该层 sizeZ 行、每行 sizeX 字符。 */
    public final String[][] layers;
    private final String mother;
    private final String[] ops;
    private final long salt;

    RuinTemplate(String name, String mother, String[] ops, long salt) {
        final String[][] src = motherLayers(mother);
        this.name = name;
        this.mother = mother;
        this.ops = ops;
        this.salt = salt;
        this.sizeZ = src[0].length;
        this.sizeY = src.length;
        this.sizeX = src[0][0].length();
        this.layers = RuinDamageOps.derive(src, salt, ops);
    }

    /** 取（世界向上 y ∈ 0..sizeY-1，dx ∈ 0..sizeX-1，dz ∈ 0..sizeZ-1）处字符——与既有三族同一实现式。 */
    public char charAt(int y, int dx, int dz) {
        return this.layers[this.sizeY - 1 - y][dz].charAt(dx);
    }

    /** 母体注册名（旧名册成员；派生谱系的唯一申报口）。 */
    public String mother() {
        return this.mother;
    }

    /** 算子序列（按施加顺序）。 */
    public String[] ops() {
        return this.ops.clone();
    }

    /** 损毁档强度 = 算子步数（1..3）。 */
    public int damageOps() {
        return this.ops.length;
    }

    /**
     * 派生盐（只读申报）。公开的唯一目的是让 {@code tools/dim1/RuinFamilyCheck} 能用
     * "母体 + 本盐 + 本算子序列"<b>重跑一次</b>派生并逐字节比对在册模板——
     * 没有它，"由既有结构破败化派生"就只是一句无法验算的注释。
     */
    public long salt() {
        return this.salt;
    }

    /**
     * 本模板用到的记号字符（升序去重，{@code '.'}/空格除外）。
     * 机检用它断言"落块键全在既有键表内且不含禁项"，不从字符盘另推一份真值。
     */
    public String usedChars() {
        final Set<Character> seen = new LinkedHashSet<>();
        for (int y = 0; y < this.sizeY; y++) {
            for (int z = 0; z < this.sizeZ; z++) {
                for (int x = 0; x < this.sizeX; x++) {
                    final char c = charAt(y, x, z);
                    if (c != ' ' && c != '.') {
                        seen.add(Character.valueOf(c));
                    }
                }
            }
        }
        final StringBuilder b = new StringBuilder();
        for (final char c : new TreeSet<>(seen)) {
            b.append(c);
        }
        return b.toString();
    }

    /** 本模板用到的方块键（{@link CityVariants#blockKeyOf(char)} 折算后去重；TE 机检的输入）。 */
    public Set<String> usedBlockKeys() {
        final Set<String> keys = new TreeSet<>();
        for (final char c : this.usedChars()
            .toCharArray()) {
            final String key = CityVariants.blockKeyOf(c);
            if (key == null) {
                throw new IllegalStateException("[GTSR] ruin " + this.name + ": char '" + c + "' has no block key");
            }
            keys.add(key);
        }
        return keys;
    }

    /** 本模板的非空气落块总数（未损伤口径；机检用它断言"派生结果仍是一座结构"）。 */
    public int solidChars() {
        int n = 0;
        for (int y = 0; y < this.sizeY; y++) {
            for (int z = 0; z < this.sizeZ; z++) {
                for (int x = 0; x < this.sizeX; x++) {
                    final char c = charAt(y, x, z);
                    if (c != ' ' && c != '.') {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /** 母体层串（只读，绝不回写；机型 5 + outpost 6 共 11 个可选母体）。 */
    private static String[][] motherLayers(String mother) {
        for (final RuinedMachineShapes.Shape s : RuinedMachineShapes.ALL) {
            if (s.name.equals(mother)) {
                return s.layers;
            }
        }
        for (final ProsperityOutpostPlacer.Outpost o : ProsperityOutpostPlacer.ALL) {
            if (o.name.equals(mother)) {
                return o.layers;
            }
        }
        throw new IllegalStateException("[GTSR] ruin mother not registered: " + mother);
    }
}
