import java.util.List;

import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.BiomeId;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.NearestBiomeChunk;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.NearestStatus;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerChain;
import com.miaokatze.gtsr.config.Config;

/**
 * P14 tpdim 群系定位的离线断言（plan §5 P14「离线纯函数断言 + 新增 TpdimNearestBiomeCheck」）。
 * <p>
 * 分档跑（同一 JVM 的 L1 账本/bind 会互污，档间必须分进程，与 BiomeAllocationCheck 同纪律）：
 * <ul>
 * <li>{@code d78}——判据 2/3/4（dim78 正常态）：四群系各跑一次 {@code nearestBiomeChunk}
 * （生产同一实现体），落点经 {@code ordinalAt} 复核确属目标带；对每个群系用
 * <b>本地重建的同参 GenLayer 链</b>（种子/等权 id 表/中心代表点与 manager 接线一致）穷举
 * "半径 = 算法命中距离外沿 +2 环"的正方形全域求 chunk 粒度真最近，与算法命中比较
 * （距离差必须为 0）；申报步数/耗时；两条上界档（半径调到最小命中环-1 ⇒ NOT_FOUND；
 * 步数保险丝 ⇒ 评估数不越 cap 且无崩溃）；</li>
 * <li>{@code d79}——同 d78（B1 起两维身份源同为等权 GenLayer 链，穷举对拍链种子掺各自的 def.seedSalt）
 * （离线装配为 NONE 正常态；实机 dim79 若处降级态，其"应报明确错误而不是乱指"判据由
 * {@code empty79} 档承担）；</li>
 * <li>{@code empty79}——dim79 强制 EMPTY：四名册成员 recordNoSlot + 空表 def ⇒
 * 补全名单为空、名字解析一律 null（指令层据此走错误通道，不触发搜索）、
 * 小半径搜索 0 命中且步数恰为环带轮廓格数（降级态路径同样有上界，不伪造身份）；</li>
 * <li>{@code source}——判据 1/5 源级钉：GTSRCommand 基线 (0,0) 路径的调用序列/文案/return
 * 逐字保留且群系分支仅在其后可达；tab 名单取 {@code rosterBiomeNames()} 单一真值；
 * diag 走 P12 同一实现体（buildEntryDiagLine/bootSummaryLine，禁复制装配）；
 * 指令层零 {@code getBiomeGenForCoords}、零 {@code BiomeGenBase} 直触（身份只经 L1）。</li>
 * </ul>
 * <b>列名申报</b>（d78/d79 每群系一行，前缀 {@code TPDIM78} / {@code TPDIM79}）：
 * {@code biome=}（目标名册身份）{@code algo=(cx,cz)}（环带步进命中 chunk）{@code algoDistSq=}
 * （到原点 chunk 的欧氏距离平方——"最近"的申报口径为 chunk 粒度距离）{@code status=}
 * （HIT/NOT_FOUND/STEP_LIMIT）{@code rings=}（完整扫过的环带数）{@code steps=}（实际评估
 * chunk 数）{@code ms=}（本次搜索耗时）{@code minCheb=}（穷举回读的最小命中环带号）
 * {@code brute=(cx,cz)} {@code bruteDistSq=} {@code evals=}（链粗层穷举侧数字）
 * {@code distDiff=}（algo-brute，判据 3 必须 0）{@code verify=}（ordinalAt 复核命中列中心，
 * 必须等于 biome）。
 * <p>
 * 运行（cwd=仓库根；classpath 见 surface_checks.sh [2h]）：
 * {@code java -cp temp/p4-surface/tools;temp/p4-surface/classes;<CP> TpdimNearestBiomeCheck d78}
 */
public class TpdimNearestBiomeCheck {

    /** dim78 def.seedSalt（与 SurfaceHarness.def(true,...) 同值；链种子 = SEED ^ salt 的对拍入参）。 */
    private static final long SEED_SALT78 = 0x50524F53L;
    /** dim79 def.seedSalt（同上）。 */
    private static final long SEED_SALT79 = 0x53484C53L;

    private static int assertions;
    private static boolean failed;

    public static void main(String[] args) throws Exception {
        final String mode = args.length >= 1 ? args[0] : "d78";
        if ("d78".equals(mode)) {
            dim78();
        } else if ("d79".equals(mode)) {
            dim79();
        } else if ("empty79".equals(mode)) {
            dim79ForcedEmpty();
        } else if ("source".equals(mode)) {
            sourcePins();
        } else {
            System.err.println("unknown mode: " + mode);
            System.exit(2);
        }
        System.out.println("TPDIM-" + mode.toUpperCase() + (failed ? " FAIL" : " PASS") + ": assertions=" + assertions);
        if (failed) {
            System.exit(1);
        }
    }

    // ═══════════════════════════ 装配（与 DiagLineCheck 同源，零伪造账本） ═══════════════════════════

    private static GTSRBiomeAuthority bindNormal78() {
        SurfaceHarness.initVanillaBlocks();
        SurfaceHarness.blockFamily();
        final BiomeGenBase[] p = SurfaceHarness.prosperityBiomes();
        final BiomeGenBase[] s = SurfaceHarness.shatteredBiomes();
        SurfaceHarness.recordAllAllocations(p, s);
        final GTSRDimensionDef def78 = SurfaceHarness.def(true, p, SurfaceHarness.prosperityWeights());
        final GTSRWorldChunkManager mgr78 = SurfaceHarness.manager(true, def78);
        // 与生产 bind 同源，但把维度 id 钉成 78（离线 def 未过 DimensionRegistrar，resolvedDimId=-1）
        GTSRBiomeAuthority.bind(GTSRBiomeAuthority.DIM_KEY_PROSPERITY, 78, mgr78::biomeAt);
        return GTSRBiomeAuthority.forDimension(78);
    }

    private static GTSRBiomeAuthority bindNormal79() {
        SurfaceHarness.initVanillaBlocks();
        SurfaceHarness.blockFamily();
        final BiomeGenBase[] p = SurfaceHarness.prosperityBiomes();
        final BiomeGenBase[] s = SurfaceHarness.shatteredBiomes();
        SurfaceHarness.recordAllAllocations(p, s);
        final GTSRDimensionDef def79 = SurfaceHarness.def(false, s, SurfaceHarness.shatteredWeights());
        final GTSRWorldChunkManager mgr79 = SurfaceHarness.manager(false, def79);
        GTSRBiomeAuthority.bind(GTSRBiomeAuthority.DIM_KEY_SHATTERED, 79, mgr79::biomeAt);
        return GTSRBiomeAuthority.forDimension(79);
    }

    // ═══════════════════════════ d78 / d79 主档 ═══════════════════════════

    private static void dim78() {
        final GTSRBiomeAuthority auth = bindNormal78();
        check(auth.degraded() == GTSRBiomeAuthority.Degraded.NONE, "d78 前提：degraded 应为 NONE");
        check(auth.isBound(), "d78 前提：应已绑定");
        runLocateTable(auth, "TPDIM78", SurfaceHarness.PROSPERITY_KEYS, SurfaceHarness.PROSPERITY_IDS, SEED_SALT78);
        // T5/T8 重钉（plan §3.3）：补全名单 = 账本名册（5 元，含 roster-only 的 sanzu）；
        // 定位主表仍只走 4 家 selector 群系（sanzu 平面由 populate 后置写入，不在链身份面）。
        checkNamesAndParsing(auth, new String[] { "Rusted Steppe", "Gearwork Forest", "Brass Wastes",
            "Fumarole Swamp", "Sanzu River" });
    }

    private static void dim79() {
        final GTSRBiomeAuthority auth = bindNormal79();
        check(auth.degraded() == GTSRBiomeAuthority.Degraded.NONE, "d79 前提：degraded 应为 NONE（离线正常态）");
        runLocateTable(auth, "TPDIM79", SurfaceHarness.SHATTERED_KEYS, SurfaceHarness.SHATTERED_IDS, SEED_SALT79);
        checkNamesAndParsing(auth, new String[] { "Ashen Prairie", "Slagwood Grove", "Vitreous Waste", "Tar Basin" });
    }

    /** 判据 2/3/4 主表：四群系逐个 定位 → 穷举对拍 → ordinalAt 复核；表尾两条上界档。
     *
     * @param ids   该维 def 群系表 id（与 manager 建链入参同一等权数组；穷举复算链的入参）
     * @param seedSalt 该维 def.seedSalt（与 manager 链种子 seed^seedSalt 同礼仪）
     */
    private static void runLocateTable(GTSRBiomeAuthority auth, String tag, BiomeId[] keys, int[] ids, long seedSalt) {
        BiomeId worst = null;
        int worstMinCheb = -1;
        for (final BiomeId key : keys) {
            final long t0 = System.nanoTime();
            final NearestBiomeChunk hit = auth.nearestBiomeChunk(
                key,
                0,
                0,
                Config.tpdimBiomeSearchMaxRadiusChunks,
                Config.tpdimBiomeSearchMaxSteps);
            final long ms = (System.nanoTime() - t0) / 1_000_000L;
            check(hit.status == NearestStatus.HIT && hit.biome == key, tag + " " + key + " 应 HIT，实为 " + hit);
            // 判据 2：落点经 L1 ordinalAt 复核确属目标带（命中 chunk 中心列，块坐标口径）
            final GTSRBiomeAuthority.Resolution v = auth.ordinalAt(hit.chunkX * 16 + 8, hit.chunkZ * 16 + 8);
            check(v.resolved() && v.biomeId == key, tag + " ordinalAt 复核失配：" + v + " != " + key);
            // 判据 3：链粗层穷举（正方形全域，半径 = 命中距离外沿 +2 环，覆盖一切可能更近格）。
            // B1 起身份源是 GenLayer 链（本地重建同参链独立复算，不再用带算法）。
            final int radius = (int)Math.ceil(Math.sqrt(hit.distSq)) + 2;
            final long[] b = bruteForce(ids, key.rosterIndex(), seedSalt, radius);
            check(b[0] >= 0 && b[0] == hit.distSq, tag + " 最近性不成立：algo=" + hit.distSq + " brute=" + b[0]);
            // 判据 4 前置：评估数不越过"扫满 rings 环带正方形"的组合上界
            check(hit.steps <= (2L * hit.ringsScanned + 1) * (2L * hit.ringsScanned + 1), tag + " 步数越界：" + hit);
            if (b[4] > worstMinCheb) {
                worstMinCheb = (int)b[4];
                worst = key;
            }
            System.out.println(
                tag + " biome=" + key + " algo=(" + hit.chunkX + "," + hit.chunkZ + ")" + " algoDistSq=" + hit.distSq
                    + " status=" + hit.status + " rings=" + hit.ringsScanned + " steps=" + hit.steps + " ms=" + ms
                    + " minCheb=" + b[4] + " brute=(" + b[1] + "," + b[2] + ")" + " bruteDistSq=" + b[0] + " evals="
                    + b[3] + " distDiff=" + (hit.distSq - b[0]) + " verify=" + v.biomeId);
        }
        // 判据 4a：半径上限调小（最小命中环 -1）⇒ NOT_FOUND 可读错误通道，无崩溃/空指针
        final int smallR = Math.max(0, worstMinCheb - 1);
        final NearestBiomeChunk tight = auth.nearestBiomeChunk(worst, 0, 0, smallR, Integer.MAX_VALUE);
        check(
            tight.biome == null && tight.status == NearestStatus.NOT_FOUND,
            tag + " 小半径应 NOT_FOUND，实为 " + tight);
        System.out.println(
            tag + " RADIUS-CAP biome=" + worst + " radius=" + smallR + " status=" + tight.status + " steps="
                + tight.steps);
        // 判据 4b：步数保险丝 ⇒ 评估数不越 cap（有/无命中都必须以 STEP_LIMIT/HIT 收口，不越界扫描）
        final int fuse = 25;
        final NearestBiomeChunk capped = auth.nearestBiomeChunk(worst, 0, 0, 4096, fuse);
        check(capped.steps <= fuse + 1L, tag + " 保险丝越界：" + capped);
        check(
            capped.biome != null || capped.status == NearestStatus.STEP_LIMIT,
            tag + " 无命中收口必须是 STEP_LIMIT：" + capped);
        System.out.println(
            tag + " STEP-FUSE biome=" + worst + " cap=" + fuse + " status=" + capped.status + " steps=" + capped.steps
                + " hit=" + (capped.biome != null));
    }

    /**
     * 穷举环带（判据 3 的独立实现，B1 起走 <b>本地重建的 GenLayer 链</b>——与 manager 同参：
     * 种子 {@code SEED ^ seedSalt}、等权 id 数组、chunk 中心代表点；不复用被测 manager 实例）：
     * 正方形全域 [-r,r]²。
     *
     * @return {bestDistSq(-1=无命中), bestCx, bestCy, evals, minCheb(目标群系最小命中环带号)}
     */
    private static long[] bruteForce(int[] ids, int idx, long seedSalt, int radius) {
        final GTSRGenLayerChain chain = new GTSRGenLayerChain(SurfaceHarness.SEED ^ seedSalt, ids);
        long bestD = Long.MAX_VALUE;
        long bx = 0;
        long bz = 0;
        boolean found = false;
        long evals = 0;
        long minCheb = Long.MAX_VALUE;
        for (int cx = -radius; cx <= radius; cx++) {
            for (int cz = -radius; cz <= radius; cz++) {
                evals++;
                if (chain.biomeAtCoarse(cx * 16 + 8, cz * 16 + 8) == ids[idx]) {
                    final long d = (long)cx * cx + (long)cz * cz;
                    if (d < bestD) {
                        bestD = d;
                        bx = cx;
                        bz = cz;
                    }
                    minCheb = Math.min(minCheb, Math.max(Math.abs(cx), Math.abs(cz)));
                    found = true;
                }
            }
        }
        return new long[] { found ? bestD : -1L, bx, bz, evals, found ? minCheb : -1L };
    }

    /** 判据 5 的离线可断言部分：补全名单 == roster 群系名集合 + 三形态解析 + 未知名 null。 */
    private static void checkNamesAndParsing(GTSRBiomeAuthority auth, String[] expectedNames) {
        final List<String> names = auth.rosterBiomeNames();
        check(names.size() == expectedNames.length, "roster 名单规模漂移：" + names);
        for (int i = 0; i < expectedNames.length && i < names.size(); i++) {
            check(expectedNames[i].equals(names.get(i)), "roster 名单第 " + i + " 项漂移：" + names);
            final String underscore = expectedNames[i].replace(' ', '_');
            check(auth.findRosterByName(underscore) != null, "下划线形态解析失败：" + underscore);
            check(auth.findRosterByName("\"" + expectedNames[i] + "\"") != null, "引号形态解析失败");
            check(auth.findRosterByName(expectedNames[i].toUpperCase()) != null, "大写空格形态解析失败");
            check(auth.findRosterByName(expectedNames[i].replace(" ", "  ")) != null, "多重空格折叠解析失败");
            check(auth.findRosterByName("  " + underscore + "  ") != null, "首尾空白未 trim");
        }
        check(auth.findRosterByName("Nope Biome") == null, "未知名必须解析为 null（可读错误通道）");
        check(auth.findRosterByName("") == null, "空名必须解析为 null");
        check(auth.findRosterByName("   ") == null, "纯空白必须解析为 null");
        check(auth.findRosterByName(null) == null, "null 名必须解析为 null 不 NPE");
    }

    // ═══════════════════════════ empty79：dim79 降级态判据 ═══════════════════════════

    private static void dim79ForcedEmpty() {
        SurfaceHarness.initVanillaBlocks();
        SurfaceHarness.blockFamily();
        for (int i = 0; i < 4; i++) {
            GTSRBiomeAuthority.recordNoSlot(
                SurfaceHarness.SHATTERED_KEYS[i],
                SurfaceHarness.SHATTERED_IDS[i],
                "p14red:forced-empty");
        }
        final GTSRDimensionDef def = SurfaceHarness.emptyDef(false);
        final GTSRWorldChunkManager mgr = SurfaceHarness.manager(false, def);
        GTSRBiomeAuthority.bind(GTSRBiomeAuthority.DIM_KEY_SHATTERED, 79, mgr::biomeAt);
        final GTSRBiomeAuthority auth = GTSRBiomeAuthority.forDimension(79);
        check(auth.degraded() == GTSRBiomeAuthority.Degraded.EMPTY, "empty79 前提：degraded 应为 EMPTY");
        check(auth.rosterBiomeNames().isEmpty(), "EMPTY 态补全名单必须为空（不得列出无身份群系）");
        check(auth.findRosterByName("Ashen Prairie") == null, "EMPTY 态名字解析必须落 null（指令层据此走错误通道）");
        check(auth.findRosterByName("ashen_prairie") == null, "EMPTY 态下划线别名同样落 null");
        final NearestBiomeChunk h = auth.nearestBiomeChunk(BiomeId.ASHEN_PRAIRIE, 0, 0, 4, 10000);
        check(h.biome == null && h.status == NearestStatus.NOT_FOUND, "EMPTY 态不得指野点：" + h);
        // 半径 4 的环带轮廓格数 = 1 + 8·(1+2+3+4) = 81，逐格评估无跳步（降级态路径同样有上界）
        check(h.steps == 81L, "EMPTY 态小半径步数应为 81，实为 " + h.steps);
        System.out.println("TPDIM79 EMPTY-GATE status=" + h.status + " steps=" + h.steps + " hit=null names=0");
    }

    // ═══════════════════════════ source：判据 1/5 源级钉 ═══════════════════════════

    private static void sourcePins() throws Exception {
        final String src = new String(
            java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get("src/main/java/com/miaokatze/gtsr/common/commands/GTSRCommand.java")),
            "UTF-8");
        // 与 DiagLineCheck 同法：去空白后比对（项目 spotless 会折长行，按原样 contains 会在纯格式化后假红）
        final String flat = src.replaceAll("\\s+", "");
        // —— 判据 1：基线（9735cef）"不填群系名"路径的逐字要素 ——
        check(
            flat.contains("if(args.length<2){thrownewWrongUsageException(getCommandUsage(sender));}"),
            "钉1a：tpdim 参数下界不是 <2");
        check(
            flat.contains("newChatComponentText(\"tpdimfailed:dimension\"+which+\"(id\"+dimId+\")isdisabledornotregistered\")"),
            "钉1b：未注册错误文案被改动");
        check(
            flat.contains(".transferPlayerToDimension(player,dimId,newGTSRDimTeleporter(target));"
                + "sender.addChatMessage(newChatComponentText(\"tpdim:sentplayertodimension\"+dimId+\"(\"+which+\")\"));return;"),
            "钉1c：基线 2 参分支的调用序列/文案/return 位置被改动");
        check(
            flat.indexOf("processTpdimBiome(sender,args,player,dimId,which,target);") > flat
                .indexOf("newChatComponentText(\"tpdim:sentplayertodimension\""),
            "钉1d：群系分支先于基线分支可达");
        check(
            flat.contains("if(args.length==2){") && flat.indexOf("if(args.length==2){") < flat
                .indexOf("processTpdimBiome(sender,args,player,dimId,which,target);"),
            "钉1e：2 参守卫不再是 return 早退分支");
        // —— 判据 5：补全名单与 roster 单一真值（不硬编码、不抄第二份）——
        check(flat.contains("finalList<String>names=authority.rosterBiomeNames();"), "钉5a：tab 补全不再走 rosterBiomeNames");
        // 注：去空白后 `' '` 字符字面量折叠为 `''`（与 DiagLineCheck 同款坑，实测踩过）
        check(flat.contains("tokens[i]=names.get(i).replace('','_');"), "钉5b：补全 token 不再做下划线形态");
        check(flat.contains("\"singularity\",\"structure\",\"tpdim\",\"diag\""), "钉5c：子命令补全未含 diag");
        // —— diag 复用 P12 同一实现体（反复制装配钉）——
        check(flat.contains("GTSRChunkProviderBase.buildEntryDiagLine(dimId,dimKey,mgr)"), "钉6a：diag 不再调 buildEntryDiagLine");
        check(flat.contains("CommonProxy.DiagAssembly.bootSummaryLine()"), "钉6b：diag 不再调 DiagAssembly.bootSummaryLine");
        // —— 搜索只走 L1（禁读 Chunk byte / 禁 getBiomeGenForCoords 逐格试探）——
        // 钉的是"调用形态"（点号+括号）；类注释里的禁令提及（无点号）不算违规。
        check(!flat.contains(".getBiomeGenForCoords("), "钉7：指令层出现 getBiomeGenForCoords 调用");
        check(!flat.contains("BiomeGenBase"), "钉8：指令层直接触碰 BiomeGenBase（身份应只经 L1 Resolution）");
        // —— Config 上界只在群系分支被读取（不填群系名时两键不参与）——
        check(
            flat.indexOf("Config.tpdimBiomeSearchMaxRadiusChunks") > flat.indexOf("if(args.length==2){"),
            "钉9：搜索上界 Config 键早于基线分支被读取（基线路径必须零触碰）");
    }

    private static void check(boolean ok, String msg) {
        assertions++;
        if (!ok) {
            failed = true;
            System.out.println("  FAIL " + msg);
        }
    }
}
