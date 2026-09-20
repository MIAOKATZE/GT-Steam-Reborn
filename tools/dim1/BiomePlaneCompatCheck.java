import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.block.Block;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

import com.falsepattern.endlessids.mixin.helpers.ChunkBiomeHook;
import com.miaokatze.gtsr.common.dimension.framework.BiomePlaneAccess;
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;

/**
 * P0 崩溃修复（EndlessIDs 1.7.4 取消 byte 群系入口）的离线断言，两档分进程跑。
 * <p>
 * <b>为什么必须两档</b>：{@code BiomePlaneAccess} 的通道由运行期 {@code Class.forName} 决定，
 * 单档 JVM 只能看到一条通道。测试替身 {@code tools/dim1/com/falsepattern/endlessids/mixin/helpers/
 * ChunkBiomeHook.java} 随 {@code -sourcepath tools/dim1} 编入 {@code temp/p4-surface/tools}
 * ⇒ 挂在 classpath 上时 {@code hookPresent()==true}（short 档）；把本工具的 class 另抄到不含替身的
 * 目录再跑（byte 档）⇒ {@code hookPresent()==false}。两档各自<b>硬钉前提</b>（short 档
 * {@code hookPresent} 必须为真、byte 档必须为假），任一档挂错 classpath 立刻变红，
 * 不会退化成"两档跑同一条通道"的假绿。
 * <p>
 * 覆盖（每条括注"实现回退即红"的理由）：
 * <ol>
 * <li><b>A1</b> short 不回绕：id 180/260 → 180/260，同输入 byte 路径必须 → -76/4（RED/GREEN 内建
 * 对偶，证明 short 通道存在的意义；{@code toShortPlane} 若被改回 {@code & 255} 即红）；</li>
 * <li><b>A2</b> 缺席列：null 槽、短数组越界、{@code biomes=null} 全部写
 * {@code MISSING_BIOME_SHORT_ID}，且该常量既不是 plains(1) 也不是 EndlessIDs 的 -1 懒回填哨兵
 * （占位被改成 -1/1 即红 = 把降级维重新暴露给 plains 与懒回填落盘）；</li>
 * <li><b>A3</b> short 通道真跑通：{@code writeViaHook} 驱动实现替身接口的 {@link FakeHookChunk}，
 * setter 收到的数组与 {@code toShortPlane(biomes)} <b>逐元素相等</b>且恰被调 1 次、写完后零
 * -1 残留（这是"short 分支确实被执行"的证据，不用"类不在所以永远走 byte"充当通过）；
 * 另含两条负档：非 hook 目标与 null 目标必不派发；</li>
 * <li><b>A4</b> 反向保护源文本钉：{@code provideChunk} 体内不得再出现 {@code chunk.getBiomeArray()} /
 * {@code setBiomeArray(} / {@code writeBiomePlane(}，且<b>必须</b>出现
 * {@code BiomePlaneAccess.write(chunk,biomes)}（前三条单独用会因"整行被删"而恒真，故配第四条成对钉；
 * 空白归一化后再比，防 spotless 折行假红——本仓踩过）；</li>
 * <li><b>A5</b> hook 类在场但该 chunk <b>没实现</b> hook（= {@code ChunkMixin} 未应用：EndlessIDs 缺席
 * 或 {@code extendBiome=false}）⇒ 必须<b>落回 byte 写入</b>：返回值 true、平面与直接调
 * {@code writeBiomePlane} 逐字节相同、且不消耗 skip 锚点、必须消耗 resolved 锚点。
 * <b>为什么不能反过来"跳过不写"</b>：{@code new Chunk(...)} 把平面填成 255/-1 哨兵，留空就是把身份
 * 交给懒回填，而 vanilla {@code Chunk.java:1406-1407} 与 EndlessIDs 的 {@code func_76591_a}
 * <b>都不对 {@code getBiomeGenAt} 判空</b>——本 mod 空表降级态返回 null 会在那两处直接 NPE，
 * 所以"留空"从来不是无害降级（这是本片写作过程中实测出来的修正，前一版实现的 skip 分支据此推翻）；</li>
 * <li><b>A5b</b> 零抛出红线实测：{@link ThrowingHooklessChunk} 模拟"byte 入口被取消但没实现 hook"
 * 的假想新形状 ⇒ {@code write} 必须 {@code catch (Throwable)} 后返回 false、<b>绝不上抛</b>、
 * 且确实走到过访问器（{@code calls==1}，否则本判据是空转）。此前这条只有结构性论证、无实测试金石；</li>
 * <li><b>A6</b> 零编译期依赖与通道组成钉：{@code BiomePlaneAccess.java} 源文本不得
 * {@code import com.falsepattern}、必须用 {@code Class.forName(HOOK_CLASS)} 与
 * {@code getMethod(SETTER_NAME, short[].class)}、byte 分支必须逐字组成
 * {@code GTSRChunkProviderBase.writeBiomePlane(chunk.getBiomeArray(),biomes)}（v1.20.32 等价体）、
 * short 侧必须原样转窄且全文无 {@code &255}；</li>
 * <li><b>A7</b> 诊断行列：{@code runtimeMode()} 与 {@code hookPresent()} 同真同假，且
 * {@code GTSRChunkProviderBase} 源文本必须真的把该值拼成 {@code " plane="} 列（缺列即红）；</li>
 * <li><b>B1/B2/B3</b>（byte 档）：无 hook 时 {@code mode==byte}、{@code write} 返回 true、
 * 真 {@code Chunk} 的 byte 平面与<b>直接反射调</b> {@code writeBiomePlane} 的结果逐字节相同
 * （byte-parity 红线，覆盖 180..193 生产 id 段与 null 缺席列），平面零 plains、零 255 哨兵，
 * 且与 short 通道满足 {@code (short)(b & 0xFF) == s} 的交叉对偶；{@code write(null,…)}
 * 与 {@code writeViaHook(chunk,…)} 在该档都必须返回 false。</li>
 * </ol>
 * 运行（cwd=仓库根）：
 * 
 * <pre>
 * java -cp temp/p4-surface/tools;temp/p4-surface/classes;&lt;CP&gt; BiomePlaneCompatCheck short
 * java -cp temp/p4-surface/bp-byte;temp/p4-surface/classes;&lt;CP&gt; BiomePlaneCompatCheck byte
 * </pre>
 * 
 * 由 {@code tools/dim1/surface_checks.sh} [2j] 挂链（两档都必须绿；byte 档的 classpath
 * <b>故意不含</b> {@code $OUT/tools}，抄 class 的动作也在那里）。
 */
public class BiomePlaneCompatCheck {

    private static final String PROVIDER_SRC =
        "src/main/java/com/miaokatze/gtsr/common/dimension/framework/GTSRChunkProviderBase.java";
    private static final String ACCESS_SRC =
        "src/main/java/com/miaokatze/gtsr/common/dimension/framework/BiomePlaneAccess.java";

    private static int assertions;

    public static void main(String[] args) throws Exception {
        final String mode = args.length > 0 ? args[0] : "short";
        if ("byte".equals(mode)) {
            checkByteChannel();
        } else if ("short".equals(mode)) {
            checkShortChannel();
        } else {
            System.out.println("BIOMEPLANE FAIL: 未知档 " + mode + "（可用 short|byte）");
            System.exit(2);
        }
        System.out.println("BIOMEPLANE PASS mode=" + mode + " assertions=" + assertions + " hookPresent="
            + BiomePlaneAccess.hookPresent() + " runtimeMode=" + BiomePlaneAccess.runtimeMode());
    }

    // ═══════════════════════════ short 档（替身 ChunkBiomeHook 在场）═══════════════════════════

    private static void checkShortChannel() throws Exception {
        check(BiomePlaneAccess.hookPresent(),
            "前提塌了：替身 ChunkBiomeHook 不在 classpath 上（hookPresent=false），short 档会整段退化成 byte ⇒ 判红");
        check(BiomePlaneAccess.hookClass() == ChunkBiomeHook.class,
            "探测到的 hook Class 不是替身本体：hookClass=" + BiomePlaneAccess.hookClass());
        check("short".equals(BiomePlaneAccess.runtimeMode()), "runtimeMode 错: " + BiomePlaneAccess.runtimeMode());

        // —— A1：不回绕 + 与 byte 通道的内建对偶 ——
        final BiomeGenBase b180 = probeBiome(180);
        final BiomeGenBase b260 = probeBiome(260);
        final short[] p180 = BiomePlaneAccess.toShortPlane(new BiomeGenBase[] { b180 });
        final short[] p260 = BiomePlaneAccess.toShortPlane(new BiomeGenBase[] { b260 });
        check(p180.length == 256, "平面长度不是 256: " + p180.length);
        check(p260.length == 256, "平面长度不是 256: " + p260.length);
        check(p180[0] == (short) 180, "id 180 在 short 平面回绕: " + p180[0]);
        check(p260[0] == (short) 260, "id 260 在 short 平面回绕: " + p260[0]);
        final byte[] k180 = new byte[256];
        final byte[] k260 = new byte[256];
        writeBiomePlane(k180, new BiomeGenBase[] { b180 });
        writeBiomePlane(k260, new BiomeGenBase[] { b260 });
        check(k180[0] == (byte) -76, "byte 对偶值漂移：180 应截成 -76，实得 " + k180[0]);
        check(k260[0] == (byte) 4, "byte 对偶值漂移：260 应截成 4，实得 " + k260[0]);
        check(p180[0] != k180[0], "short 与 byte 对 id 180 竟然同值 ⇒ short 通道没修任何东西");
        check((p260[0] & 0xFF) == (k260[0] & 0xFF),
            "id 260 的 short 值与 byte 回绕值不同族（&0xFF 后应相等）：" + p260[0] + " vs " + k260[0]);
        System.out.println("A1 toShortPlane: id180->" + p180[0] + " (byte " + k180[0] + ") id260->" + p260[0]
            + " (byte " + k260[0] + ")");

        // —— A2：缺席列占位 ——
        check(BiomePlaneAccess.MISSING_BIOME_SHORT_ID != (short) 1, "缺席占位等于 plains(1)——降级维禁令");
        check(BiomePlaneAccess.MISSING_BIOME_SHORT_ID != (short) -1,
            "缺席占位等于 EndlessIDs 的 -1 懒回填哨兵（会被回调 chunk manager 并写回落盘）");
        final short[] withNull = BiomePlaneAccess.toShortPlane(new BiomeGenBase[] { b180, null, b260 });
        check(withNull[1] == BiomePlaneAccess.MISSING_BIOME_SHORT_ID, "null 槽未写缺席占位: " + withNull[1]);
        check(withNull[0] == (short) 180 && withNull[2] == (short) 260, "null 槽的邻居列被污染");
        final int badTail = countNot(p180, 1, BiomePlaneAccess.MISSING_BIOME_SHORT_ID);
        check(badTail == 0, "短数组越界列未全部写缺席占位，异常列数=" + badTail);
        final short[] allNull = BiomePlaneAccess.toShortPlane(null);
        check(countNot(allNull, 0, BiomePlaneAccess.MISSING_BIOME_SHORT_ID) == 0,
            "biomes=null 时未整平面写缺席占位");

        // —— A3：writeViaHook 真派发（short 分支被执行的正证据）——
        final BiomeGenBase[] biomes = new BiomeGenBase[] { b180, b260, null, probeBiome(0) };
        final FakeHookChunk fake = new FakeHookChunk();
        fake.poke((short) -1);
        final short[] beforeWrite = fake.plane.clone();
        check(BiomePlaneAccess.writeViaHook(fake, biomes), "writeViaHook 在 hook 实例上返回 false（short 分支没执行）");
        check(fake.calls == 1, "setter 调用次数应为 1，实得 " + fake.calls);
        final short[] expect = BiomePlaneAccess.toShortPlane(biomes);
        check(Arrays.equals(fake.received, expect),
            "setter 收到的数组与 toShortPlane 不逐元素相等：firstDiff=" + firstDiff(fake.received, expect) + " got["
                + at(fake.received, 0) + "," + at(fake.received, 1) + "," + at(fake.received, 2) + ","
                + at(fake.received, 3) + "] want[" + expect[0] + "," + expect[1] + "," + expect[2] + "," + expect[3]
                + "]");
        check(fake.plane != null && fake.plane.length == 256, "setter 收到的数组长度异常: " + at2(fake.plane));
        check(countEq(fake.plane, (short) -1) == 0,
            "写完后仍残留 -1 懒回填哨兵列 " + countEq(fake.plane, (short) -1) + "（会被 vanilla 回调改写并落盘）");
        check(countDiff(beforeWrite, fake.plane) > 0, "short 平面写前写后逐列相同 ⇒ setter 是空转");
        check(!BiomePlaneAccess.useShortPlane(null), "null chunk 被判为走 short 平面");
        check(!BiomePlaneAccess.writeViaHook(new Object(), biomes), "writeViaHook 对非 hook 目标返回 true");
        check(!BiomePlaneAccess.writeViaHook(null, biomes), "writeViaHook 对 null 目标返回 true");
        final FakeHookChunk nullBiomes = new FakeHookChunk();
        check(BiomePlaneAccess.writeViaHook(nullBiomes, null), "biomes=null 时 short 通道未照常派发");
        check(nullBiomes.calls == 1, "biomes=null 未派发 setter（calls=" + nullBiomes.calls + "）");
        System.out.println("A3 writeViaHook: calls=" + fake.calls + " plane[0..3]=" + fake.plane[0] + ","
            + fake.plane[1] + "," + fake.plane[2] + "," + fake.plane[3]);

        // —— A4：provideChunk 反向保护（源文本钉）——
        final String flatProvider = flat(PROVIDER_SRC);
        final String body = methodBody(flatProvider, "publicChunkprovideChunk(intchunkX,intchunkZ){");
        check(!body.isEmpty(), "取不到 provideChunk 方法体（签名漂移？请同步本工具锚点）");
        check(body.contains("BiomePlaneAccess.write(chunk,biomes)"),
            "provideChunk 未接上 BiomePlaneAccess.write(chunk,biomes)（下面三条会因'整行被删'而恒真）");
        check(!body.contains("chunk.getBiomeArray()"), "provideChunk 仍在拿 byte 平面（EndlessIDs 在场即崩）");
        check(!body.contains("setBiomeArray("), "provideChunk 仍在调 Chunk.setBiomeArray(byte[])（同样被取消）");
        check(!body.contains("writeBiomePlane("), "provideChunk 仍直连 byte 写点（未经双通道派发）");

        // —— A5：hook 类在场 + 该 chunk 未实现 hook ⇒ 必须落回 byte 写入，不是把平面留空 ——
        // 留空不是无害降级：new Chunk(...) 把平面填成 255/-1 哨兵，之后靠懒回填，而
        // vanilla Chunk.java:1406-1407 与 EndlessIDs func_76591_a 都不对 getBiomeGenAt 判空。
        final Chunk chunk = realChunk();
        final byte[] plane0 = chunk.getBiomeArray();
        check(plane0 != null, "离线真 Chunk.getBiomeArray() 返回 null（byte 平面口径漂移，A5 无法判据）");
        final byte[] sentinel = new byte[plane0.length];
        java.util.Arrays.fill(sentinel, (byte) -1);
        check(Arrays.equals(sentinel, plane0),
            "A5 前置塌了：写前 byte 平面不是全 255/-1 哨兵（真 Chunk 构造口径漂移）index=" + firstDiffB(sentinel, plane0));
        check(!BiomePlaneAccess.useShortPlane(chunk), "离线真 Chunk 竟被判为 hook 实例（替身被真 mixin 上了？classpath 异常）");
        check("byte".equals(BiomePlaneAccess.mode(chunk)),
            "非 hook chunk 的 mode 应为 byte（落回 byte 分支），实得 " + BiomePlaneAccess.mode(chunk));
        check(BiomePlaneAccess.write(chunk, biomes),
            "hook 类在场但 chunk 未实现 hook ⇒ 应落回 byte 写入；返回 false 说明平面被留空（懒回填遇 null 即 NPE）");
        final byte[] want = new byte[plane0.length];
        writeBiomePlane(want, biomes);
        check(Arrays.equals(want, chunk.getBiomeArray()),
            "A5 落回 byte 的结果与直接 writeBiomePlane 不等价（v1.20.32 行为回归）：firstDiff="
                + firstDiffB(want, chunk.getBiomeArray()));
        check(!skipAnchorTaken(), "A5 走的是 byte 分支却消耗了 skip 锚点（说明又退回静默留空）");
        check(resolvedAnchorTaken(), "A5 落回 byte 写入后没打 resolved 行（『哪条通道真跑了』仍然不可知 = 本轮故障的原始教训）");

        // —— A5b：byte 访问器实际抛出的病态形态 ⇒ 必须降级为跳过，绝不上抛（provideChunk 零抛出红线）——
        final ThrowingHooklessChunk boom = new ThrowingHooklessChunk();
        boolean wrote;
        boolean propagated = false;
        try {
            wrote = BiomePlaneAccess.write(boom, biomes);
        } catch (Throwable t) {
            propagated = true;
            wrote = true;
        }
        check(boom.calls == 1, "A5b 没真的走到 byte 访问器（calls=" + boom.calls + " ⇒ 分支被绕开，本判据不成立）");
        check(!propagated, "write 对抛错的 byte 访问器上抛了（会把区块生成变成 ReportedException，即本轮原故障）");
        check(!wrote, "byte 通道抛错却返回 true（谎报平面已写入）");
        check(skipAnchorTaken(), "A5b 降级后没打出一次性 skip 理由（静默跳过 = 实机不可诊断）");
        check(!BiomePlaneAccess.write(null, biomes), "write(null,…) 不应返回 true");
        check("skip".equals(BiomePlaneAccess.mode(null)), "mode(null) 应为 skip，实得 " + BiomePlaneAccess.mode(null));

        // —— A6：零编译期依赖 + 两通道组成钉 ——
        final String acc = flat(ACCESS_SRC);
        check(!acc.contains("importcom.falsepattern"), "BiomePlaneAccess 出现编译期 import（可选依赖被硬化）");
        check(acc.contains("Class.forName(HOOK_CLASS)"), "BiomePlaneAccess 不再用 Class.forName(HOOK_CLASS) 探测");
        check(acc.contains("getMethod(SETTER_NAME,short[].class)"),
            "short setter 探测形状漂移（需 getMethod(SETTER_NAME, short[].class)）");
        check(acc.contains("GTSRChunkProviderBase.writeBiomePlane(chunk.getBiomeArray(),biomes)"),
            "byte 分支不再是 v1.20.32 的等价体（getBiomeArray + writeBiomePlane）");
        check(acc.contains("plane[i]=biome==null?MISSING_BIOME_SHORT_ID:(short)biome.biomeID;"),
            "toShortPlane 不再是\"与 writeBiomePlane 成对可读\"的原样转窄");
        check(!acc.contains("&255"), "BiomePlaneAccess 出现 & 255 截断");
        check(!acc.contains("throws"), "BiomePlaneAccess 暴露受检异常（provideChunk 不得被冒泡污染）");

        // —— A7：诊断 plane= 列真的接进来了 ——
        check(flatProvider.contains("+\"plane=\"+BiomePlaneAccess.runtimeMode()"),
            "进维诊断行不再拼 plane= 列（与 DiagLineCheck 的列申报对不上）");
    }

    // ═════════════════════════════════ byte 档（无替身）═════════════════════════════════

    private static void checkByteChannel() throws Exception {
        check(!BiomePlaneAccess.hookPresent(),
            "前提塌了：byte 档 classpath 上仍有 ChunkBiomeHook（hookPresent=true）⇒ 本档会在 short 通道上自比");
        check(BiomePlaneAccess.hookClass() == null, "byte 档 hookClass 应为 null: " + BiomePlaneAccess.hookClass());
        check("byte".equals(BiomePlaneAccess.runtimeMode()), "runtimeMode 错: " + BiomePlaneAccess.runtimeMode());

        // —— B1：无 hook 时的通道口径 ——
        final Chunk chunk = realChunk();
        check("byte".equals(BiomePlaneAccess.mode(chunk)),
            "byte 档 mode(chunk) 应为 byte，实得 " + BiomePlaneAccess.mode(chunk));
        check(!BiomePlaneAccess.useShortPlane(chunk), "byte 档 useShortPlane 竟为真");
        check("skip".equals(BiomePlaneAccess.mode(null)),
            "byte 档 mode(null) 应为 skip（null chunk 在<b>任何</b>档都无从写入，旧口径按档分成 byte/skip 是self-inconsistent）");

        // —— B2：byte-parity 红线（write 的结果 == 直接调 writeBiomePlane 的结果）——
        final BiomeGenBase[] biomes = new BiomeGenBase[] { probeBiome(180), probeBiome(181), probeBiome(182),
            probeBiome(183), null, probeBiome(193), probeBiome(190), probeBiome(0) };
        check(BiomePlaneAccess.write(chunk, biomes), "byte 档 write 返回 false（byte 通道被跳过了）");
        final byte[] viaAccess = chunk.getBiomeArray()
            .clone();
        final byte[] direct = new byte[viaAccess.length];
        writeBiomePlane(direct, biomes);
        check(java.util.Arrays.equals(viaAccess, direct),
            "byte 档 write 与直接 writeBiomePlane 不等价（v1.20.32 行为回归）：firstDiff="
                + firstDiffB(viaAccess, direct));
        check(plains(viaAccess) == 0, "byte 平面出现 plains(1) 列 " + plains(viaAccess) + "（降级维禁令）");
        check(sentinel(viaAccess) == 0, "byte 平面出现 255/-1 哨兵列 " + sentinel(viaAccess) + "（懒回填落盘风险）");
        check(viaAccess[4] == GTSRChunkProviderBase.MISSING_BIOME_PLANE_ID, "byte 缺席列占位漂移: " + viaAccess[4]);
        final short[] shortPlane = BiomePlaneAccess.toShortPlane(biomes);
        int crossMismatch = countCrossMismatch(viaAccess, shortPlane);
        check(crossMismatch == 0, "byte/short 两通道对同一 biomes 不满足 (short)(b&0xFF)==s，差异列=" + crossMismatch);
        System.out.println("B2 byte plane[0..7]=" + viaAccess[0] + "," + viaAccess[1] + "," + viaAccess[2] + ","
            + viaAccess[3] + "," + viaAccess[4] + "," + viaAccess[5] + "," + viaAccess[6] + "," + viaAccess[7]
            + " short=" + shortPlane[0] + "," + shortPlane[5] + "," + shortPlane[6]);

        // —— B3：该档下 short 通道完全不可达，且 null chunk 的跳过必须有可见理由 ——
        check(!skipAnchorTaken(), "byte 档进 B3 前不该已消耗 skip 锚点（前面没有任何跳过发生）");
        check(!BiomePlaneAccess.write(null, biomes), "byte 档 write(null,…) 不应返回 true");
        check(skipAnchorTaken(), "write(null,…) 没有打出一次性 skip 理由（静默跳过 = 实机不可诊断）");
        check(!BiomePlaneAccess.writeViaHook(chunk, biomes), "byte 档 writeViaHook 仍派发了（hook 不在场却写 short）");
    }

    // ═══════════════════════════════ 装配与断言原语 ═══════════════════════════════

    /** 实现替身接口的假 chunk：只提供 setter 派发面与计数器（不参与任何生产逻辑）。 */
    static final class FakeHookChunk implements ChunkBiomeHook {

        short[] plane;
        short[] received;
        int calls;

        void poke(short value) {
            this.plane = new short[256];
            Arrays.fill(this.plane, value);
        }

        @Override
        public short[] getBiomeShortArray() {
            return this.plane;
        }

        @Override
        public void setBiomeShortArray(short[] shortArray) {
            this.calls++;
            this.plane = shortArray;
            this.received = shortArray;
        }
    }

    /**
     * 病态形态模拟器：一个<b>没有</b>实现 hook、但 {@code getBiomeArray()} 会抛错的 Chunk 子类——
     * 也就是"byte 入口被第三方取消、却没走 {@code ChunkBiomeHook}"的假想新形状。
     * 生产侧唯一能兜住它的就是 {@code write} 的最外层 {@code catch (Throwable)}，本类就是那条红线的实测试金石。
     */
    static final class ThrowingHooklessChunk extends Chunk {

        int calls;

        ThrowingHooklessChunk() {
            super(SurfaceHarness.mockWorld(), new Block[65536], new byte[65536], 0, 0);
        }

        @Override
        public byte[] getBiomeArray() {
            this.calls++;
            throw new UnsupportedOperationException("simulated EndlessIDs guard on a non-hook chunk");
        }
    }

    /** 反射取框架的 {@code protected static writeBiomePlane}（先例 SurfaceDegradationCheck.java:284-286）。 */
    private static void writeBiomePlane(byte[] target, BiomeGenBase[] biomes) throws Exception {
        final Method write =
            GTSRChunkProviderBase.class.getDeclaredMethod("writeBiomePlane", byte[].class, BiomeGenBase[].class);
        write.setAccessible(true);
        write.invoke(null, target, biomes);
    }

    /**
     * 不注册、不进 {@code biomeList} 的纯载体群系：两个写通道只读 {@code biomeID} 一个字段，故
     * {@code Unsafe.allocateInstance} + 直写该字段（该字段是 {@code public final int}，
     * 普通反射写不进去，必须走 Unsafe）即可覆盖 id 260——{@code new BiomeGenBase(260)} 既有
     * 抽象类不可实例化、又会越界写 256 槽注册表，真 GTSR 子类又受名册 id 段限制。
     */
    private static BiomeGenBase probeBiome(int id) throws Exception {
        final sun.misc.Unsafe u = SurfaceHarness.unsafe();
        final BiomeGenBase biome = (BiomeGenBase) u.allocateInstance(ProbeBiome.class);
        final Field field = BiomeGenBase.class.getDeclaredField("biomeID");
        field.setAccessible(true);
        u.putInt(biome, u.objectFieldOffset(field), id);
        return biome;
    }

    /** 只为给 {@code probeBiome} 一个可 allocate 的非抽象载体；构造器<b>永不执行</b>。 */
    static final class ProbeBiome extends BiomeGenBase {

        private ProbeBiome(int id) {
            super(id);
        }
    }

    /** 真 {@code Chunk}（全 null 方块 → 构造期跳过所有 setBlock，只留 byte 群系平面），世界取 mock。 */
    private static Chunk realChunk() {
        return new Chunk(SurfaceHarness.mockWorld(), new Block[65536], new byte[65536], 0, 0);
    }

    /** 反射读生产侧 skip 一次性锚点（只读，不消耗；先例 surfaceNotLaidAnchorTaken）。 */
    private static boolean skipAnchorTaken() throws Exception {
        return anchorTaken("SKIP_LOGGED");
    }

    /** {@code BiomePlaneAccess} 的"通道已落定"一次性锚点（只读，不消耗）。 */
    private static boolean resolvedAnchorTaken() throws Exception {
        return anchorTaken("RESOLVED_LOGGED");
    }

    private static boolean anchorTaken(String field) throws Exception {
        final Field f = BiomePlaneAccess.class.getDeclaredField(field);
        f.setAccessible(true);
        return ((AtomicBoolean) f.get(null)).get();
    }

    /**
     * 读源文本 → <b>先去注释</b> → 再归一化空白（防 spotless 折行假红）。
     * <p>
     * 去注释是必需的，不是可选的：本片自己的修复注释里就写着
     * {@code Chunk.getBiomeArray()/setBiomeArray(byte[])} 与 {@code 不做 & 255}，
     * 直接 flatten 会让 A4/A6 的"不得出现"钉被注释文本触发（实测踩过）。
     * <b>前提</b>：被钉的两个文件里不得出现含 {@code //} 的字符串字面量（如 URL），
     * 否则行注释剥离会咬进字面量——实测 {@code grep -c "://" } 对两文件均为 0。
     */
    private static String flat(String path) throws Exception {
        final String src = new String(Files.readAllBytes(Paths.get(path)), "UTF-8");
        return src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//[^\n]*", "").replaceAll("\\s+", "");
    }

    /** 从归一化源码里按大括号配对取方法体（锚点必须逐字出现在归一化文本里）。 */
    private static String methodBody(String flat, String anchor) {
        final int at = flat.indexOf(anchor);
        if (at < 0) {
            return "";
        }
        final int open = at + anchor.length() - 1;
        int depth = 0;
        for (int i = open; i < flat.length(); i++) {
            final char c = flat.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return flat.substring(open + 1, i);
                }
            }
        }
        return flat.substring(open + 1);
    }

    /** {@code [from,256)} 内不等于 {@code want} 的列数。 */
    private static int countNot(short[] plane, int from, short want) {
        int n = 0;
        for (int i = from; i < plane.length; i++) {
            if (plane[i] != want) {
                n++;
            }
        }
        return n;
    }

    /** 整平面等于 {@code want} 的列数。 */
    private static int countEq(short[] plane, short want) {
        int n = 0;
        for (short v : plane) {
            if (v == want) {
                n++;
            }
        }
        return n;
    }

    private static int countDiff(short[] a, short[] b) {
        int n = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            if (a[i] != b[i]) {
                n++;
            }
        }
        return n;
    }

    private static int plains(byte[] plane) {
        int n = 0;
        for (byte v : plane) {
            if (v == (byte) 1) {
                n++;
            }
        }
        return n;
    }

    private static int sentinel(byte[] plane) {
        int n = 0;
        for (byte v : plane) {
            if (v == (byte) 255) {
                n++;
            }
        }
        return n;
    }

    private static int countCrossMismatch(byte[] plane, short[] shortPlane) {
        int n = 0;
        for (int i = 0; i < plane.length; i++) {
            if ((short) (plane[i] & 0xFF) != shortPlane[i]) {
                n++;
            }
        }
        return n;
    }

    private static int firstDiff(short[] a, short[] b) {
        if (a == null || b == null) {
            return -2;
        }
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            if (a[i] != b[i]) {
                return i;
            }
        }
        return a.length == b.length ? -1 : -3;
    }

    private static int firstDiffB(byte[] a, byte[] b) {
        if (a == null || b == null) {
            return -2;
        }
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            if (a[i] != b[i]) {
                return i;
            }
        }
        return a.length == b.length ? -1 : -3;
    }

    private static String at(short[] a, int i) {
        return a == null ? "null" : (i < a.length ? String.valueOf(a[i]) : "-");
    }

    private static String at2(short[] a) {
        return a == null ? "null" : String.valueOf(a.length);
    }

    private static void check(boolean ok, String message) {
        assertions++;
        if (!ok) {
            System.out.println("BIOMEPLANE FAIL: " + message);
            System.exit(1);
        }
    }
}
