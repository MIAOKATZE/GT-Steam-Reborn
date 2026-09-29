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
 * <li>{@code source}——判据 1/5 源级钉：GTSRCommand 基线 2 参分支的调用序列/文案/return
 * 逐字保留且群系分支仅在其后可达；tab 名单取 {@code rosterBiomeNames()} 单一真值；
 * diag 走 P12 同一实现体（buildEntryDiagLine/bootSummaryLine，禁复制装配）；
 * 指令层零 {@code getBiomeGenForCoords}、零 {@code BiomeGenBase} 直触（身份只经 L1）。
 * <b>P23 R1·S6 新增</b>：S5 新路径源级钉（原点参数化/sanzu 湖格分支/NOT_FOUND 文案补引号）
 * ＋ 别名↔lang 双向同步钉（9 串对 lang 键往返）＋ 落点水感知谓词零 chunk 读源级钉
 * ＋ nearestActiveLakeCenter 站格枚举 vs 暴力扫描对拍（distDiff==0，纯函数直调）。
 * <b>P24-F（v1.20.47）新增</b>：sanzu 落点契约断言（nearestActiveLakeCenter → sanzuArrivalColumn，
 * 落点必须满足「滩带内 ∧ 无水格 heightAt ≥ SEA_LEVEL−1 ∧ isSanzuColumn==true」，
 * 走公开出口不抄第二份判定）。</li>
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
        // T5/T8 重钉（plan §3.3）：补全名单 = 账本名册（6 元，含 roster-only 的 sanzu/枯竭河床）；
        // 定位主表仍只走 4 家 selector 群系（两平面由 populate 后置写入，不在链身份面）。
        checkNamesAndParsing(auth, new String[] { "Rusted Steppe", "Gearwork Forest", "Brass Wastes",
            "Fumarole Swamp", "Sanzu Lake", "Withered Riverbed" });
        // P25 新行：第 6 元 WITHERED_RIVERBED 与 SANZU_RIVER 同族——已配槽（rosterBiomeNames 含它）
        // 但链面（GenLayer 4 家 selector + coarse 环带）结构性解析不到 ⇒ 环带搜索必须 NOT_FOUND、
        // ordinalAt 采样域必须 0 命中（tpdim 对它的可达路径只能是"平面谓词专属分支"——生产侧
        // 本轮未加该分支，属设计态：枯竭河床经 /gtsr tpdim 直达不可达，与 sanzu 的 S5 前状态同形）。
        final NearestBiomeChunk w = auth.nearestBiomeChunk(
            GTSRBiomeAuthority.BiomeId.WITHERED_RIVERBED,
            0,
            0,
            256,
            Integer.MAX_VALUE);
        check(
            w.biome == null && w.status == GTSRBiomeAuthority.NearestStatus.NOT_FOUND,
            "d78 WITHERED_RIVERBED 链面结构性不可解析：环带搜索应 NOT_FOUND，实为 " + w);
        int witheredHits = 0;
        for (int cz = -32; cz <= 32; cz += 2) {
            for (int cx = -32; cx <= 32; cx += 2) {
                final GTSRBiomeAuthority.Resolution v = auth.ordinalAt(cx * 16 + 8, cz * 16 + 8);
                if (v.resolved() && v.biomeId == GTSRBiomeAuthority.BiomeId.WITHERED_RIVERBED) {
                    witheredHits++;
                }
            }
        }
        check(
            witheredHits == 0,
            "d78 WITHERED_RIVERBED 链面结构性不可解析：ordinalAt 采样域命中 = " + witheredHits
                + "（roster-only 成员泄漏进链身份面 = 双写平面外多出第二身份源）");
        System.out.println("TPDIM78 WITHERED-UNREACHABLE search=" + w.status + " ordinalHits=" + witheredHits);
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
        // ══ P23 R1·S5（v1.20.46 批1）新路径源级钉：只钉新代码路径，基线 2 参分支钉 1a-1e 逐字保留 ══
        check(
            flat.contains("finalintoriginChunkX=MathHelper.floor_double(player.posX)>>4;")
                && flat.contains("finalintoriginChunkZ=MathHelper.floor_double(player.posZ)>>4;")
                && flat.contains("target,originChunkX,originChunkZ,Config.tpdimBiomeSearchMaxRadiusChunks"),
            "钉10：环带搜索原点 = 玩家当前 chunk（原硬编码 0,0 已参数化，4 家 selector 路径同一调用点）");
        check(
            flat.contains("if(target==GTSRBiomeAuthority.BiomeId.SANZU_RIVER){")
                && flat.contains("processTpdimSanzuLake(sender,player,targetWorld,dimId,which,rawName);")
                && flat.contains("GTSRVoronoiRiverField.nearestSanzuArrival(")
                && flat.contains("GTSRVoronoiRiverField.isSanzuColumn(worldSeed,bx,bz);"),
            "钉11：sanzu（roster-only）专属安全湖分支在场（nearestSanzuArrival 同时返回湖心+干滩列，"
                + "就地复核走 isSanzuColumn——ordinalAt 对 roster-only 结构性不可见）");
        check(
            flat.contains("\"tpdimfailed:该维度内未找到该群系'\"+rawName+\"'（已搜半径\""),
            "钉12：NOT_FOUND 文案补闭合引号（S5 前丢引号，玩家看到不闭合回执）");
        aliasLangSync();
        waterSenseSourcePins(flat);
        arrivalColumnDryBeach();
        nearestLakeEnumerationParity();
    }

    /**
     * P24-F（v1.20.47）落点契约断言，P25 S5b 改走安全湖单出口：沿
     * {@link #nearestLakeEnumerationParity} 同一组 (seed, origin)，直调生产出口
     * {@code nearestSanzuArrival}（跳过无群系内干滩湖），再以
     * {@code lakeAt / heightAt / isSanzuColumn} 三个公开谓词复核落点列满足新语义
     * 「滩带内 ∧ 无水格 ∧ 在 sanzu 平面内」：
     * <ul>
     * <li>{@code lakeAt ∈ [LAKE_ISLAND, LAKE_SHORE)}——仍是滩带列（避岛心树干/岛底柱）；</li>
     * <li>{@code heightAt ≥ SEA_LEVEL−1}（= 67 = 最高水格 y）——生成侧
     * {@code fillSanzuLakes} 只在 {@code h < SEA_LEVEL} 时灌水，故该门逐列等价于"列顶无水格"；</li>
     * <li>{@code isSanzuColumn == true}——仍在遗忘之湖滩带平面内（指令层就地 verify 为真）。</li>
     * </ul>
     * 全部走公开出口，<b>不抄第二份判定</b>（不重写射线扫描、滩带压力口径或水位判据）。
     */
    private static void arrivalColumnDryBeach() {
        final long[] seeds = { 0x1AFEE7A9E5A7L, 0x7269_7665_4C42L, 20260925L };
        final int[][] origins = { { 0, 0 }, { 4813, -2277 }, { -7153, 2269 }, { 350, -90000 }, { 123456, -654321 } };
        int checked = 0;
        int nullCol = 0;
        int notBeach = 0;
        int wet = 0;
        int outOfPlane = 0;
        for (final long seed : seeds) {
            for (final int[] o : origins) {
                final int[] out = new int[2];
                final int[] col = new int[2];
                if (!com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField
                    .nearestSanzuArrival(seed, o[0], o[1], out, col)) {
                    nullCol++;
                    continue;
                }
                checked++;
                final double p = com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField
                    .lakeAt(seed, col[0], col[1]);
                if (!(p >= com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField.LAKE_ISLAND
                    && p < com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField.LAKE_SHORE)) {
                    notBeach++;
                }
                if (com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile
                    .heightAt(seed, col[0], col[1]) < com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile.SEA_LEVEL
                        - 1) {
                    wet++;
                }
                if (!com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField
                    .isSanzuColumn(seed, col[0], col[1])) {
                    outOfPlane++;
                }
            }
        }
        System.out.println(
            "TPDIM-SOURCE ARRIVAL-CONTRACT checked=" + checked + " null=" + nullCol + " notBeach=" + notBeach + " wet="
                + wet + " outOfPlane=" + outOfPlane);
        check(checked > 0, "钉16a：nearestSanzuArrival 落点契约样本为空（7×7 站窗内无安全干滩湖）");
        check(nullCol == 0, "钉16b：nearestSanzuArrival 未命中安全干滩湖：" + nullCol);
        check(notBeach == 0, "钉16c：落点不在滩带 lakeAt∈[LAKE_ISLAND, LAKE_SHORE)：" + notBeach);
        check(wet == 0, "钉16d：落点是置水列（heightAt < SEA_LEVEL−1，会落湖水面）：" + wet);
        check(outOfPlane == 0, "钉16e：落点不在 sanzu 平面（isSanzuColumn==false）：" + outOfPlane);
    }

    /** P23 R1·S6 新档①：别名↔lang 双向同步钉（9 串对 lang 键往返）。 */
    private static void aliasLangSync() throws Exception {
        final String authority = new String(
            java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get("src/main/java/com/miaokatze/gtsr/common/dimension/framework/GTSRBiomeAuthority.java")),
            "UTF-8");
        final String zh = new String(
            java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/assets/gtsr/lang/zh_CN.lang")),
            "UTF-8");
        final String en = new String(
            java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/assets/gtsr/lang/en_US.lang")),
            "UTF-8");
        // 正抽：别名表 put 行（中文 → BiomeId）应恰 10 条（P25 +枯竭河床）
        final java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("ZH_CN_DISPLAY_NAME_ALIASES\\.put\\(\"([^\"]+)\", BiomeId\\.([A-Z_]+)\\)")
            .matcher(authority);
        final java.util.List<String[]> pairs = new java.util.ArrayList<>();
        while (m.find()) {
            pairs.add(new String[] { m.group(1), m.group(2) });
        }
        check(pairs.size() == 10, "钉13a：别名表恰 10 串（P25 +枯竭河床；实测 " + pairs.size() + "）");
        int roundTripped = 0;
        for (final String[] pair : pairs) {
            // 反抽：lang 文件里应恰有一条 biome.<英文名>.name=<中文>——英文名按 BiomeId 的注册名
            // （roster 顺序钉：dim78 5 家 + dim79 4 家；lang 键与注册名同轮改值，见 P23 R1 批2 S2）
            final String biomesSrc = new String(
                java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get("src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/biome/ProsperityBiomes.java")),
                "UTF-8");
            // 中文值行（lang）：值 → 键
            final java.util.regex.Matcher lz = java.util.regex.Pattern
                .compile("(?m)^biome\\.(.+)\\.name=(.+)$")
                .matcher(zh);
            String keyForZh = null;
            while (lz.find()) {
                if (lz.group(2).equals(pair[0])) {
                    keyForZh = lz.group(1);
                    break;
                }
            }
            check(keyForZh != null, "钉13b：别名 '" + pair[0] + "' 在 zh_CN.lang 无对应 biome.*.name 值（改名失同步）");
            if (keyForZh != null) {
                roundTripped++;
                check(
                    en.contains("biome." + keyForZh + ".name="),
                    "钉13c：zh 值 '" + pair[0] + "' 的 lang 键 biome." + keyForZh + ".name 在 en_US.lang 缺失（双语文案失同步）");
            }
        }
        // 反向：lang 的 biome.*.name 值若不在别名表 ⇒ 中文客户端 F3 名解析断链
        final java.util.regex.Matcher lzAll = java.util.regex.Pattern
            .compile("(?m)^biome\\.(.+)\\.name=(.+)$")
            .matcher(zh);
        int zhBiomeLines = 0;
        int orphan = 0;
        while (lzAll.find()) {
            zhBiomeLines++;
            boolean known = false;
            for (final String[] pair : pairs) {
                if (pair[0].equals(lzAll.group(2))) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                orphan++;
                System.out.println("  FAIL 钉13d：zh_CN.lang 值 '" + lzAll.group(2) + "' 不在别名表（10 串外）");
            }
        }
        check(
            zhBiomeLines == 10 && orphan == 0,
            "钉13d：zh_CN.lang biome 行恰 10（P25 +枯竭河床）且全部在别名表（实测 行=" + zhBiomeLines
                + " 孤儿=" + orphan + "）");
        System.out.println("TPDIM-SOURCE ALIAS-SYNC pairs=" + pairs.size() + " roundTripped=" + roundTripped);
    }

    /** P23 R1·S6 新档③：落点水感知谓词零 chunk 读源级钉。 */
    private static void waterSenseSourcePins(String flat) {
        // 取 columnTopSafeY + populateWaterColumnAt 两方法的区段（到 joinTrailingArgs 为止）
        final int begin = flat.indexOf("privatestaticintcolumnTopSafeY(");
        final int end = flat.indexOf("privatestaticStringjoinTrailingArgs(");
        check(begin > 0 && end > begin, "钉14a：水感知两方法区段抽取失败（方法被改名/删除）");
        if (begin > 0 && end > begin) {
            final String seg = flat.substring(begin, end);
            check(
                seg.contains("populateWaterColumnAt(seed,x,z)")
                    && seg.contains("populateWaterColumnAt(seed,nx,nz)")
                    && seg.contains("GTSRVoronoiRiverField.sanzuShoreWaterAt(worldSeed,x,z)")
                    // [P25 D7 残潭退役] 原"残潭 O1a 出口"腿（swampRiverPoolColumnAt）已随生产删除：
                    // 水感知链 = 湖 fillSanzuLakes 同式单腿（残潭置水通道不存在）。
                    // [v1.20.53 P30 I3] 湖腿对齐 RVF sanzuShoreWaterAt 单一出口（=
                    // fillSanzuLakes 置水门同式，P27-L D1·4a 灌水对齐；旧 lakeAt<LAKE_SHORE
                    // 直比式随生产删除退役）。
                    && seg.contains("ProsperityTerrainProfile.SEA_LEVEL-1;"),
                "钉14b：水感知链 = populate 同源谓词（湖走 sanzuShoreWaterAt 单一出口，与 fillSanzuLakes"
                    + " 置水门同式；P25 D7 残潭腿已退役、P30 I3 起灌水对齐）"
                    + "+ 螺旋避让 + 水面 fallback（SEA_LEVEL−1）——形态完整");
            check(
                !seg.contains("getChunk") && !seg.contains(".getBlock(") && !seg.contains("worldObj")
                    && !seg.contains("getBiomeGenForCoords"),
                "钉14c：落点水感知谓词零 chunk 读（纯函数；出现 Chunk/方块读 = 谓词抄了第二份世界判定）");
        }
    }

    /**
     * P23 R1·S6 新档②：nearestActiveLakeCenter 站格枚举 vs 暴力扫描对拍（distDiff==0）。
     * 暴力侧 = 9×9 站窗（＞生产的 ≤3 环 7×7）逐站 lakeCellCenterAt 反解 + 活湖门
     * （lakeAt < LAKE_ISLAND，与生产同式直调），取欧氏最近；两者距离平方必须相等
     * （生产窗外的更近湖不存在 ⇒ 枚举窗闭合性成立）。
     */
    private static void nearestLakeEnumerationParity() {
        final long[] seeds = { 0x1AFEE7A9E5A7L, 0x7269_7665_4C42L, 20260925L };
        final int[][] origins = { { 0, 0 }, { 4813, -2277 }, { -7153, 2269 }, { 350, -90000 }, { 123456, -654321 } };
        int checked = 0;
        int diffBad = 0;
        int hitMiss = 0;
        double worstGap = 0.0D;
        for (final long seed : seeds) {
            for (final int[] o : origins) {
                final int[] out = new int[2];
                final boolean hit = com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField
                    .nearestActiveLakeCenter(seed, o[0], o[1], out);
                // 暴力：原点所在站格 ±4 环（9×9 站）
                final double iv = com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField.LAKE_INTERVAL;
                final int gx = (int) Math.floor(o[0] / iv + 0.5D);
                final int gz = (int) Math.floor(o[1] / iv + 0.5D);
                double bestD = Double.POSITIVE_INFINITY;
                final double[] cc = new double[4];
                for (int dz = -4; dz <= 4; dz++) {
                    for (int dx = -4; dx <= 4; dx++) {
                        com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField.lakeCellCenterAt(
                            seed,
                            (int) Math.round((gx + dx) * iv),
                            (int) Math.round((gz + dz) * iv),
                            cc);
                        if ((int) cc[2] == Integer.MIN_VALUE) {
                            continue;
                        }
                        final int ax = (int) Math.round(cc[0]);
                        final int az = (int) Math.round(cc[1]);
                        if (com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField
                            .lakeAt(seed, ax, az) >= com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField.LAKE_ISLAND) {
                            continue; // 死湖
                        }
                        bestD = Math.min(bestD, (double) (ax - o[0]) * (ax - o[0]) + (double) (az - o[1]) * (az - o[1]));
                    }
                }
                checked++;
                if (hit) {
                    final double pd = (double) (out[0] - o[0]) * (out[0] - o[0])
                        + (double) (out[1] - o[1]) * (out[1] - o[1]);
                    if (pd != bestD) {
                        diffBad++;
                        worstGap = Math.max(worstGap, Math.sqrt(pd) - Math.sqrt(bestD));
                    }
                } else if (bestD != Double.POSITIVE_INFINITY) {
                    hitMiss++;
                }
            }
        }
        System.out.println(
            "TPDIM-SOURCE LAKE-PARITY checked=" + checked + " distBad=" + diffBad + " hitMiss=" + hitMiss
                + " worstGap=" + worstGap);
        check(checked > 0 && diffBad == 0, "钉15：nearestActiveLakeCenter 站格枚举最近性（distDiff==0）失守 " + diffBad + "/" + checked);
        check(hitMiss == 0, "钉15b：枚举未命中但暴力窗内有活湖（枚举窗闭合性破裂）：" + hitMiss);
    }

    private static void check(boolean ok, String msg) {
        assertions++;
        if (!ok) {
            failed = true;
            System.out.println("  FAIL " + msg);
        }
    }
}
