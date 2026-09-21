import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedColossusShapes;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachinePlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachineShapes;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinShapes;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinTemplate;
import com.miaokatze.gtsr.common.dimension.shattered.WorldGenShatteredRuins;

/**
 * P0 资产基线单一真值断言（一次性自检 main，不进 jar，tools/ 惯例）：以
 * {@link StructureRegistry} 为唯一名册真值，调用全部五个 {@code registerVariants()} 后钉死三件事：
 * <ol>
 * <li><b>名集合相等</b>：注册表名集合 == 五类源派生名集合 == {@link #PIN} 的
 * {@link #EXPECTED_TOTAL} 名（① 不只数量相等）；</li>
 * <li><b>逐名 footprint</b>：{@code Entry.footprintX/Z} == 源形状按登记规则算出的 footprint == PIN 值
 * （城/outpost/<b>废墟</b> 用 {@code max(sizeX,sizeZ)} 旋转安全口径，机型/husk 用原始 sizeX×sizeZ）；</li>
 * <li><b>字符模板 SHA256 逐名钉死</b>：按 {@link #templateSha(Tpl)}
 * 的规范序列化（name|sizeX|sizeY|sizeZ + 自上而下逐行原文，含 {@code '.'} 与尾随空格）取 SHA-256，
 * 与 PIN 常量逐名比对 ⇒ 任何模板字符改动（哪怕一格）都会指名变红。</li>
 * </ol>
 * 另有 ④ 敏感度自检（逐名把模板深拷贝改一个字符，断言 SHA 必须变，防「钉死」本身假绿）
 * 与进程内双跑对拍（两次独立派生逐字节一致）证明派生本身确定。
 * <p>
 * <b>39 名的来源不是推定</b>：用户实机日志 {@code plan/log.txt:11452} 打印
 * {@code machines=5 outposts=6} + 37 个 dim78 名，加 2 名 dim79 husk = 39；
 * 与 {@code S8RegistryRosterCheck} 同一名单口径。
 * <p>
 * <b>P8：名册 39 → {@value #EXPECTED_TOTAL} 是"增长"，不是 P0 那条"未丢"判据</b>。两者必须分开读：
 * P0 的"未丢"钉的是名集合三方相等 + 逐名 SHA 与基线相同；本片的旧名册零改动钉的是
 * <b>旧 39 行的 footprint/SHA 常量逐字节沿用 P0 的钉值、一行都没重钉</b>（{@code --emit} 重钉会
 * 顺手改掉旧行，故本片只在表尾追加 ruin 行）。PIN 表里任何一条非 ruin 行发生变化都意味着
 * 旧模板被动过 ⇒ 判据 1 直接红。
 * <p>
 * <b>废墟行的 SHA 是"派生产物"的 SHA</b>：{@link RuinShapes#ALL} 的 {@code layers} 是
 * {@link RuinDamageOps} 的纯函数结果（母体字符盘 + 盐 + 算子序列），类加载期定盘。
 * 本工具与生产读的是同一份，"派生可复算"由 {@code tools/dim1/RuinFamilyCheck} 单独钉。
 * <p>
 * <b>运行（与 S8 同配方，只需 forge universal jar 满足 {@code IWorldGenerator} 接口链接；
 * 零 MC 类初始化，故不需要 patchedMc/guava/log4j——本类只读纯 Java 形状字段）</b>：
 * <pre>
 * FORGE_JAR=~/.gradle/caches/modules-2/files-2.1/net.minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/\
 * 25fd97f72beca728112256938e03e8105b1b78cc/forge-1.7.10-10.13.4.1614-1.7.10-universal.jar
 * MSYS2_ARG_CONV_EXCL='*' java -cp "build\classes\java\main;$FORGE_JAR" \
 *   tools/dim1/RosterIntegrityCheck.java            # 断言模式（PASS 才退出 0）
 * MSYS2_ARG_CONV_EXCL='*' java -cp "build\classes\java\main;$FORGE_JAR" \
 *   tools/dim1/RosterIntegrityCheck.java --emit      # 重钉模式：打印可粘贴的 PIN 表
 * </pre>
 * <b>重钉纪律</b>：{@code --emit} 输出只用于「本就有意的模板改动」后更新基线；日常回归必须走断言模式。
 * {@code --emit} 与断言模式共用同一派生代码，二者不可能互相掩盖。
 */
public class RosterIntegrityCheck {

    /**
     * 期望名数：P0 基线 39（26 城 + 6 outpost + 5 机型 + 2 husk）+ P8 废墟族
     * {@link RuinShapes#ALL} 条（8）+ P16-B1 城外跨 chunk 巨构 {@link RuinedColossusShapes#ALL} 条（2）
     * + P16-B3 城内巨构（{@code CityVariants.ALL} 中申报边长 &gt;16 的那一路）
     * = <b>51</b>（26+2 城 / 6 / 5 / 8 / 2 / 2）。
     * <p>
     * 增长数<b>不写死</b>：这里写成 {@code 39 + RuinShapes.ALL.length + RuinedColossusShapes.ALL.length
     * + 城内巨构派生数}，于是"名册增长了几条"这件事只有一个出处（三张增员来源本身）。{@link #PIN}
     * 的行数仍单独钉（下面 main 里），两者相等才 PASS——把表加长而忘了重钉 PIN，会红而不是静默通过。
     * <p>
     * <b>城内巨构那一路为什么不写数字、也不在本类另写一条边长判据</b>：判据"申报边长 &gt;
     * {@code ChunkSpans.CHUNK_BLOCKS} 的城变体＝巨构"已经住在 {@link S8RegistryRosterCheck#cityMegaCount()}
     * （它自己的 {@code EXPECTED_TOTAL} 也因 {@code CITY_MEGA} 字段的 1–2 上界而必须走方法、不走字段）。
     * 本类同处默认包 ⇒ 直接复用那一个入口，全仓只有这一处边长口径；复制一份判据＝第二处真值，
     * 哪天 {@code ChunkSpans.CHUNK_BLOCKS} 或名册变了两处会各说各话。
     * <b>类初始化环核查</b>（2026-09-21 实测）：{@code S8RegistryRosterCheck} 不引用本类，其 clinit
     * 只会向下拉 {@code CityVariants}/{@code RuinShapes}/{@code RuinedColossusShapes} 三张表 ⇒ 单向、无环。
     * 代价是 S8 的"城内巨构须 1–2 个"上界若自抛，会以 {@code ExceptionInInitializerError} 的形式打断
     * 本类取数 ⇒ {@link #expectedTotal()} 把它翻成指名红行（否则 {@code asset_baseline_check.sh}
     * 只 grep {@code ROSTER INTEGRITY (PASS|FAIL)} 会出现"红但看不出为什么红"）。
     */
    static final int EXPECTED_TOTAL = expectedTotal();

    private static int expectedTotal() {
        try {
            return 39 + RuinShapes.ALL.length + RuinedColossusShapes.ALL.length
                + S8RegistryRosterCheck.cityMegaCount();
        } catch (LinkageError | RuntimeException e) {
            fail("名册增员不可派生（废墟/城外巨构/城内巨构三张源表之一不齐，含 S8 的城内巨构 1-2 上界自抛）: "
                + e.getClass().getSimpleName() + ": " + e.getMessage());
            return -1; // 不可达：fail 即 exit 1
        }
    }

    /**
     * 逐名钉死表：{name, footprintX, footprintZ, templateSha256}。
     * 由 {@code --emit} 从五类源真实定义派生后钉入（禁止手编形状；改动走重钉 + 双跑）。
     * 序 = {@link StructureRegistry#names()} 的 TreeMap 字典序。
     * <p>
     * <b>P8 增量的边界</b>：下面 51 行里只有 8 条 {@code ruin_*} 是新钉的（表尾由 {@code --emit} 追加），
     * 其余 39 行与 P0/基线 {@code e02b451} <b>逐字节相同</b>——这是判据 1「旧名册零改动」的机检面，
     * 由 {@code RuinFamilyCheck} 的 LEGACY 组与本工具的逐名 SHA 比对共同钉住。
     * <b>P16-B1 再增 2 条 {@code colossus_*}</b>（城外跨 chunk 巨构，见 {@link RuinedColossusShapes}），
     * <b>P16-B3 再增 2 条城内巨构 {@code great_forge}/{@code titan_gearworks}</b>
     * （{@code CityMegaVariants} 汇入 {@link CityVariants#ALL} 尾部，见下段 footprint 口径），
     * 追加同样只走 {@code --emit}，旧行一字未重钉（实测：49 → 51 行的 diff 只有 2 行新增、
     * 其余按字母序落位后逐字节不变）。
     * <p>
     * <b>{@code colossus_*} 两行的 footprint 口径与 5 个小机型同形（原始 sizeX×sizeZ，不做
     * {@code max(sizeX,sizeZ)} 外接放大）</b>：机器族没有旋转算子，而跨片巨构登记的就是<b>总 bbox</b>
     * （24×16 那条横向跨 2 个 chunk、20×20 那条 X/Z 各跨 2 个 chunk）——"总 bbox 与分片并集一致"
     * 由 {@code tools/dim1/OutpostTemplateCheck} 的成对断言钉，本工具只钉名字/footprint/字符模板三件事。
     * <b>城内巨构两行不同</b>：它们走 {@code city} 一路的 {@code max(sizeX,sizeZ)} 旋转安全口径
     * （城链有 rot0/90/180/270 算子），登记的同样是<b>申报总 bbox</b>（24×24 / 20×20），
     * "申报边长确实 &gt;16 且真的跨了片"由 {@code S8RegistryRosterCheck} 的 CITYMEGA 面与
     * {@code CityShapeCheck}/{@code CityDeterminismCheck} 的切片面钉。
     */
    private static final String[][] PIN = {
        { "boiler_frame", "7", "7", "9d6738fda5a6a2218d356fedc09044c9fd0d10ecd1e8a1bfc5241f9b30aacea8" },
        { "boiler_house", "11", "11", "bec2905d2c277e5bee48048f945894d01ddba2c06613bc640f8df298c4c26f37" },
        { "broken_bridge", "16", "16", "9340d89f58e46201f2d4b0f982236b295b6cc85f2883a49b33aa3d323ba509fe" },
        { "broken_pillars", "6", "6", "615e5109f6e75b473454a124dcf08c8e9480b95f754557e7c2d7deee29b2f929" },
        { "canal_gate", "12", "12", "8af6685d1285c576ce1b20d58c45263c254863866ebb1212585058ef55dae7b9" },
        { "chimney_base", "5", "5", "9d895d330e26dc7ad151a343d7cdc5c35f7c73425aed04d0b083b25b1979d3f4" },
        { "chimney_stack", "8", "8", "f9a2b3ed4b75618f0c9d2e34afa71eb8a04caf3fabf88917f0949d06fd490010" },
        { "clock_tower", "5", "5", "17ea1aa2ec54c1fe19463a4cbd87813aa3428379375d3e86db7f2308806af3a3" },
        { "colossus_boiler_hall", "20", "20", "7b7d36c2071be03767ae5dfc82f319e1f2d0638a7bafd84d98ab236e1e1b6123" },
        { "colossus_hub_array", "24", "16", "b943596798f7d49d0640f7b14e4470e7534a4dd746406f8db727a6deaf444d81" },
        { "cooling_tower", "9", "9", "6d4a528567d402f640e080a3c1da866b4966fe87ed62c46ddc804b1bb7a6f134" },
        { "crane_ruin", "6", "6", "8add4e9ded9a2200fe5f2a8881c82af2ccf34f9f5d66a0bb2b3f2e3b170c6a5f" },
        { "dome_hall", "11", "11", "9e7c85893f4907a09625e74b9b062a16e0a5c47ed7c399c2499ae7e2d9281a14" },
        { "engine_room", "9", "9", "803f15c0802822c8b6828c9b63dc109d5c756eea182f96a712879b66afb37b72" },
        { "fallen_arch", "7", "7", "b21e80ee1eca3e590f35a14a7cdfadd233cfd98da646b06c66c1228e5ab2fd5c" },
        { "forge_hall", "13", "13", "c0888678b9936e16995aeb50b5205088241040f04137645a5b632a7fe241846f" },
        { "fountain_basin", "5", "5", "c787565c6333843c23533b29a64a842c37d4a7bbc256b553b3e37739b66448e4" },
        { "gas_holder", "7", "7", "a05b42301e244d03272a3ccccf324b8019ab5aaaef2efa565b28cdd76cae43d8" },
        { "gear_mill_table", "6", "6", "d48c20f744c2d0273dc995492c86103fa642c60301743569c7f1533af89a6fd8" },
        { "gear_tower", "7", "7", "3e333b5fa3d46eb5c145dbd70a788dfd4c283d09f6525b0da98f635682e74bab" },
        { "great_forge", "24", "24", "4ca8d47704a7228dc25f32603e0b9a3b62dcee5006f42288d1544c6ba7cb4bcd" },
        { "husk_small", "2", "2", "26a3a4c583d0ee2e0d263847f93b485a3dd78ee3665e81212c9fff0766a92b77" },
        { "husk_tall", "2", "2", "7e53fcaad1b803705e4d10d26d68b9f43925dfef2daa9cec6873b406a5fc870e" },
        { "machine_plinth", "4", "4", "42778a637a5fbfccd62c3525d66027287c3f56bd0c4b050eef89d4207aea59e9" },
        { "manor_ruin", "9", "9", "ebe937372e39fa3ac7437c78ad5a0e7af333b1902b7051aa0e5d4560582964fc" },
        { "market_colonnade", "13", "13", "7b3ec2e97e92748139b4d81097b63c63771315b83b9ea9f008044d418d644348" },
        { "outpost_brick_kiln", "8", "8", "87e628ca6871d06ba5370491035859514fdeca80d59461ec5b325b17b7a90dca" },
        { "outpost_broken_aqueduct", "16", "16", "e50c7a98cc867ece1cce07a88de7a4ac7f6688f7699235145784baf537f4ea4d" },
        { "outpost_collapsed_truss", "16", "16", "8188f0fce022c9926ef07837a792585af137f743273649c7cad6669786f15311" },
        { "outpost_gear_slag_mound", "12", "12", "9de2ea7b64035d9185f720f610fa247fdbc80db450cc2a60045f84b338d1ccc6" },
        { "outpost_toppled_boiler", "9", "9", "935c005bf11a239a0a960f7b2b31c900d73a8912c9f54822c6b77c013605d077" },
        { "outpost_watch_post", "7", "7", "85a0fa4d445a700a50540e7375335c14896e58772fe9e81596733fc004997cad" },
        { "pressure_tank_row", "12", "12", "5720d3c9bdf708d4177c599ca6c0b0b027119d87e9463c0761cbde94dfce9974" },
        { "pump_base", "5", "5", "e98ea8012b1ace9e53d63f19e52ffa487b4dea189b6ecc561670f0e712fb15c7" },
        { "pump_house", "7", "7", "1b318d5a4bb61572c939da10624b9e132a09f81b01ee9eb50375bd4b20ee86db" },
        { "rail_platform", "12", "12", "ed4c5067f0beca8f94cc67f48f7fe84dd21e725323f422c239604a1f22792657" },
        { "ruin_aqueduct_span", "16", "16", "ec744287c321542dd63c429e095edc56917789a97cc8bb86d639b501f615297f" },
        { "ruin_boiler_lean", "9", "9", "dac0563a9a4c1b4821f4182ce5a267881b397bbe1c436071e79d7f0e90454580" },
        { "ruin_chimney_fan", "5", "5", "f1e745093461af6fc6e9b5ddf4589f43f06e02083f9fb27d58eac45436314688" },
        { "ruin_gallery_span", "13", "13", "f8eac8e8ff4e84b377dfed3df8ddf65a78512cc9b6431a5168d1ee8e56a65bf0" },
        { "ruin_kiln_stump", "8", "8", "e7d84372567c946ffc12168185d91aefb75c9bdfbc29fbd6030bdf8b445c852a" },
        { "ruin_pump_chip", "5", "5", "1b5e909d26a3c8835be43c62e79b6bffce7f83afc92ea3f3f9e03037bb9ca273" },
        { "ruin_truss_fan", "16", "16", "6c0980dd906c03e1fd4fd551e4aa4a15c497abd7677aa933be452fe1f5cecb5a" },
        { "ruin_watch_buried", "7", "7", "f6a72656a6d83d1a6819b4a362255dac28268a471c8e54f34c455ad68df47a03" },
        { "slag_heap", "5", "5", "4a7c7811f132db6f7443d8c14580b2feb15067b77796c4cc3c038ee14cf2e3e4" },
        { "steam_gallery", "13", "3", "77cbe3cbbfc2cdb57bb5d33f0a981a31368d6f078fe22c9425e92846c58cca65" },
        { "titan_gearworks", "20", "20", "48d6cf7a745efb83407aab2c9642976a23b500380d2280e610d235948b4e3e45" },
        { "tram_depot", "9", "9", "b8664e78ca55e89307c80aa004c59029f6a691cf9fbf8cd081db759712de3412" },
        { "viaduct", "16", "16", "94c525b328d2bdeeec24aeae976235ccbbb319bb9b103769c5d7870375b2fb88" },
        { "watch_tower", "5", "5", "bc01a8cfb7ad78d879901cb837ecb6ab8bc13e44d3aae9280de193cd5d1e3035" },
        { "water_tower", "5", "5", "52bf4326ed114073b44e0ac0b61f63c9113bdcb3e3766c09e7955227603dde1f" },
    };

    /** 派生出的真值条目。 */
    private static final class Tpl {

        final String name;
        final String group;
        final int sizeX;
        final int sizeY;
        final int sizeZ;
        final String[][] layers;

        Tpl(String name, String group, int sizeX, int sizeY, int sizeZ, String[][] layers) {
            this.name = name;
            this.group = group;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.layers = layers;
        }

        /** 登记进 StructureRegistry 时的 footprint（与各类 registerVariants 的真实口径一致）。 */
        int fpX() {
            return "city".equals(group) || "outpost".equals(group) || "ruin".equals(group)
                ? Math.max(sizeX, sizeZ)
                : sizeX;
        }

        int fpZ() {
            return "city".equals(group) || "outpost".equals(group) || "ruin".equals(group)
                ? Math.max(sizeX, sizeZ)
                : sizeZ;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--emit".equals(args[0])) {
            emit();
            return;
        }
        RuinedMachinePlacer.registerVariants();
        CityVariants.registerVariants();
        ProsperityOutpostPlacer.registerVariants();
        // P8：废墟族（破坏结构）。注册顺序不参与名册序（TreeMap 字典序），放在这里只为"五源齐全"。
        RuinPlacer.registerVariants();
        WorldGenShatteredRuins.registerVariants();

        final Map<String, Tpl> derived = deriveFromSources();
        // 进程内双跑：派生本身必须确定（同一 JVM 两次独立派生逐名 SHA 一致）
        final Map<String, Tpl> second = deriveFromSources();
        if (derived.size() != second.size()) {
            fail("double-build size mismatch: " + derived.size() + " vs " + second.size());
        }
        for (final Map.Entry<String, Tpl> e : derived.entrySet()) {
            final Tpl r = second.get(e.getKey());
            if (r == null || !templateSha(e.getValue()).equals(templateSha(r))) {
                fail("double-build template mismatch at " + e.getKey());
            }
        }

        final List<String> names = StructureRegistry.names();
        if (names.size() != EXPECTED_TOTAL || derived.size() != EXPECTED_TOTAL) {
            fail("count: registry=" + names.size() + " derived=" + derived.size() + " expected=" + EXPECTED_TOTAL);
        }

        // ① 名集合三方相等：注册表 == 源派生 == PIN
        final Set<String> reg = new LinkedHashSet<>(names);
        final Set<String> src = derived.keySet();
        final Set<String> pin = new LinkedHashSet<>();
        for (final String[] p : PIN) {
            pin.add(p[0]);
        }
        diffSets("registry-vs-sources", reg, src);
        diffSets("registry-vs-pin", reg, pin);
        if (PIN.length != EXPECTED_TOTAL) {
            fail("PIN rows=" + PIN.length + " != " + EXPECTED_TOTAL);
        }

        // ② + ③ 逐名 footprint 与模板 SHA 钉死
        final Map<String, String[]> pinMap = new LinkedHashMap<>();
        for (final String[] p : PIN) {
            pinMap.put(p[0], p);
        }
        int fpOk = 0;
        int shaOk = 0;
        for (final StructureRegistry.Entry e : StructureRegistry.all()) {
            final Tpl t = derived.get(e.name);
            if (t == null) {
                fail("registry name not derivable from sources: " + e.name);
            }
            final String[] p = pinMap.get(e.name);
            if (p == null) {
                fail("no PIN row for " + e.name);
            }
            if (e.footprintX != t.fpX() || e.footprintZ != t.fpZ()) {
                fail("registry footprint != source rule @ " + e.name + ": registry=" + e.footprintX + "x"
                    + e.footprintZ + " source=" + t.fpX() + "x" + t.fpZ());
            }
            if (e.footprintX != Integer.parseInt(p[1]) || e.footprintZ != Integer.parseInt(p[2])) {
                fail("footprint drift vs PIN @ " + e.name + ": now=" + e.footprintX + "x" + e.footprintZ + " pinned="
                    + p[1] + "x" + p[2]);
            }
            fpOk++;
            final String sha = templateSha(t);
            if (!sha.equals(p[3])) {
                fail("template SHA drift vs PIN @ " + e.name + " (" + t.group + "): now=" + sha + " pinned=" + p[3]);
            }
            shaOk++;
        }
        if (fpOk != EXPECTED_TOTAL || shaOk != EXPECTED_TOTAL) {
            fail("checked footprint=" + fpOk + " sha=" + shaOk + " != " + EXPECTED_TOTAL);
        }

        // ④ 摘要敏感度自检：逐名把模板改一个字符，SHA 必须变（否则「钉死」是假绿）
        int canary = 0;
        for (final Tpl t : derived.values()) {
            final String[][] flipped = flipOneChar(t);
            if (templateSha(t)
                .equals(templateSha(
                    new Tpl(t.name, t.group, t.sizeX, t.sizeY, t.sizeZ, flipped)))) {
                fail("template SHA insensitive to single-char flip @ " + t.name);
            }
            canary++;
        }
        if (canary != EXPECTED_TOTAL) {
            fail("canary=" + canary + " != " + EXPECTED_TOTAL);
        }

        final int shards = countByGroup(derived, "husk");
        System.out.println("ROSTER INTEGRITY PASS: roster=" + shaOk + "/" + EXPECTED_TOTAL
            + " footprint=OK templateSHA=OK double-build=OK canary=" + canary + "/" + EXPECTED_TOTAL);
        System.out.println(
            "  city=" + countByGroup(derived, "city") + " outpost=" + countByGroup(derived, "outpost")
                + " machine=" + countByGroup(derived, "machine") + " ruin=" + countByGroup(derived, "ruin")
                + " colossus=" + countByGroup(derived, "colossus") + " husk=" + shards
                + " dim78=" + (EXPECTED_TOTAL - shards));
        System.out.println("ROSTER INTEGRITY DONE");
    }

    // ═══ 源派生（四类 ALL 表；CityVariants.layers 为包内字段，反射读，不改生产代码） ═══

    /** 深拷贝模板并把第一个字符改掉（只用于 ④ 敏感度自检，绝不回写源数据）。 */
    private static String[][] flipOneChar(Tpl t) {
        final String[][] copy = new String[t.layers.length][];
        for (int y = 0; y < t.layers.length; y++) {
            copy[y] = t.layers[y].clone();
        }
        for (int y = 0; y < copy.length; y++) {
            for (int z = 0; z < copy[y].length; z++) {
                final String row = copy[y][z];
                if (row.isEmpty()) {
                    continue;
                }
                final char c = row.charAt(0);
                final char n = c == 'x' ? 'y' : 'x';
                copy[y][z] = n + row.substring(1);
                return copy;
            }
        }
        throw new IllegalStateException("no char to flip @" + t.name);
    }

    private static Map<String, Tpl> deriveFromSources() throws Exception {
        final Map<String, Tpl> out = new LinkedHashMap<>();
        for (final CityVariants.Variant v : CityVariants.ALL) {
            put(out, new Tpl(v.name, "city", v.sizeX, v.sizeY, v.sizeZ, (String[][])layersOf(v)));
        }
        for (final ProsperityOutpostPlacer.Outpost o : ProsperityOutpostPlacer.ALL) {
            put(out, new Tpl(o.name, "outpost", o.sizeX, o.sizeY, o.sizeZ, o.layers));
        }
        for (final RuinedMachineShapes.Shape s : RuinedMachineShapes.ALL) {
            put(out, new Tpl(s.name, "machine", s.sizeX, s.sizeY, s.sizeZ, s.layers));
        }
        // P8 废墟族：layers 是 RuinDamageOps 的纯函数产物（母体 + 盐 + 算子序列），类加载期已定盘，
        // 这里读的就是生产放置器读的同一份；"派生可复算"另由 RuinFamilyCheck 的 DERIVE 组钉。
        for (final RuinTemplate r : RuinShapes.ALL) {
            put(out, new Tpl(r.name, "ruin", r.sizeX, r.sizeY, r.sizeZ, r.layers));
        }
        // P16-B1 城外跨 chunk 巨构（新机型，族仍是 machine）：footprint 走<b>原始 sizeX×sizeZ</b>
        // 口径（与 5 个小机型同形——本族没有旋转算子，所以不做 max(sizeX,sizeZ) 的外接放大）。
        for (final RuinedColossusShapes.Colossus c : RuinedColossusShapes.ALL) {
            put(out, new Tpl(c.name, "colossus", c.sizeX(), c.sizeY(), c.sizeZ(), c.shape.layers));
        }
        put(out, husk(WorldGenShatteredRuins.HUSK_SMALL));
        put(out, husk(WorldGenShatteredRuins.HUSK_TALL));
        return out;
    }

    private static Tpl husk(WorldGenShatteredRuins.HuskShape h) {
        return new Tpl(h.name, "husk", h.sizeX, h.sizeY, h.sizeZ, h.layers);
    }

    private static void put(Map<String, Tpl> out, Tpl t) {
        if (out.put(t.name, t) != null) {
            fail("duplicate name across sources: " + t.name);
        }
    }

    private static Object layersOf(Object variant) throws Exception {
        final Field f = variant.getClass()
            .getDeclaredField("layers");
        f.setAccessible(true);
        return f.get(variant);
    }

    // ═══ 规范序列化 + SHA-256 ═══

    /**
     * name|sizeX|sizeY|sizeZ 头 + 自上而下（layers[0] 为顶层）逐行原文，行间 '\n'。
     * 保留 {@code '.'}（清空）与空格（不触碰）与行尾空格，故字符级任何改动都会改摘要。
     */
    static String templateSha(Tpl t) {
        final StringBuilder b = new StringBuilder(1 << 12);
        b.append(t.name)
            .append('|')
            .append(t.sizeX)
            .append('|')
            .append(t.sizeY)
            .append('|')
            .append(t.sizeZ)
            .append('\n');
        for (final String[] layer : t.layers) {
            for (final String row : layer) {
                b.append(row).append('\n');
            }
        }
        return sha256(b.toString());
    }

    private static String sha256(String s) {
        try {
            final byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            final StringBuilder sb = new StringBuilder(64);
            for (final byte by : d) {
                sb.append(String.format("%02x", by));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ═══ --emit：打印可粘贴 PIN 表（仅用于有意改动后的重钉） ═══

    private static void emit() throws Exception {
        final Map<String, Tpl> derived = deriveFromSources();
        final List<String> keys = new ArrayList<>(derived.keySet());
        keys.sort(null);
        System.out.println("    private static final String[][] PIN = {");
        for (final String k : keys) {
            final Tpl t = derived.get(k);
            System.out.println("        { \"" + k + "\", \"" + t.fpX() + "\", \"" + t.fpZ() + "\", \"" + templateSha(t)
                + "\" },");
        }
        System.out.println("    };");
        System.out.println("// rows=" + keys.size());
    }

    // ═══ 工具 ═══

    private static int countByGroup(Map<String, Tpl> m, String group) {
        int n = 0;
        for (final Tpl t : m.values()) {
            if (group.equals(t.group)) {
                n++;
            }
        }
        return n;
    }

    private static void diffSets(String what, Set<String> a, Set<String> b) {
        final Set<String> left = new TreeSet<>(a);
        left.removeAll(b);
        final Set<String> right = new TreeSet<>(b);
        right.removeAll(a);
        if (!left.isEmpty() || !right.isEmpty()) {
            System.out.println("ROSTER FAIL: " + what + " missing=" + left + " extra=" + right + " (" + a.size()
                + "/" + b.size() + ")");
            System.out.println("NAMES: " + a);
            System.exit(1);
        }
    }

    private static boolean fail(String message) {
        System.out.println("ROSTER INTEGRITY FAIL: " + message);
        System.exit(1);
        return false;
    }
}
