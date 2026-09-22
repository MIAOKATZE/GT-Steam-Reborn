package com.miaokatze.gtsr.common.dimension.framework;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

import com.miaokatze.gtsr.main.GTSteamReborn;

/**
 * Chunk 群系平面的<b>唯一</b>写通道（P0：修 {@code /gtsr tpdim} 的
 * {@code UnsupportedOperationException: A mod that is incompatible with EndlessIDs has tried to
 * access the biome array of a chunk like in vanilla!}，取证见
 * {@code plan/investigation/eid174-biome-plane-crash-20260920.md}）。
 *
 * <h3>为什么必须存在这个类</h3>
 * 用户整合包带 EndlessIDs 1.7.4 且 {@code Biome Extension: ENABLED}（实测
 * {@code plan/log.txt:11116}）。它的 {@code mixin/mixins/common/biome/vanilla/ChunkMixin}
 * （javap 实测）把 {@code Chunk} 的群系存储整体换成 {@code private short[] eid$blockBiomeShortArray}
 * （构造期 {@code initShortArray} 用 {@code Arrays.fill(-1)} 填充），并<b>同时取消</b>原版两个访问器
 * {@code crashGetBiomeArray(CallbackInfoReturnable<byte[]>)} 与
 * {@code crashSetBiomeArray(byte[], CallbackInfo)}——两者都直接调 {@code eid$emergencyCrash()}
 * 抛 {@code UnsupportedOperationException}。也就是说：{@code hookPresent()} 为真时，
 * {@code Chunk.getBiomeArray()} / {@code Chunk.setBiomeArray(byte[])} <b>不是"能用但不好"，
 * 而是一碰就崩</b>，合法通道只剩 {@code Chunk} 实现的接口
 * {@value #HOOK_CLASS}（{@code short[] getBiomeShortArray()} / {@code void setBiomeShortArray(short[])}）。
 * <p>
 * dev 闭包（EndlessIDs 1.7.3-dev，{@code extendBiome=true}）当年实测 {@code biomeWriteThrow=0}
 * <b>并不是我们自己兼容</b>：EndlessIDs 的 coremod
 * {@code asm/transformer/chunk/ChunkProviderSuperPatcher}（{@code BytePatternMatcher} +
 * {@code STATE0..STATE17} 指令形状自动机）恰好匹配并改写了我们当时那个调用点的字节码。
 * P2 把写点抽成 {@code writeBiomePlane(...)} 后形状变了，它不再匹配 ⇒ 撞守卫。该风险早已登记在
 * {@code plan/smoketest/41-dimprobe-20260918-取证结果.md:109-111}（"改形即触发"）。
 * <b>结论：不得再依赖第三方 coremod 的字节码改写，必须显式走公开 API。</b>
 *
 * <h3>两条通道各自语义</h3>
 * <ul>
 * <li><b>byte 通道</b>（{@code hookPresent()==false}，即本运行时没有 EndlessIDs 群系扩展）：
 * 完全沿用 v1.20.32 的实现——{@code chunk.getBiomeArray()} +
 * {@link GTSRChunkProviderBase#writeBiomePlane(byte[], BiomeGenBase[])}。
 * <b>该分支与 v1.20.32 逐位一致</b>是红线：无 EndlessIDs 的存档/闭包（含全部离线断言的 byte 档）
 * 生成的群系平面必须一个比特都不变。</li>
 * <li><b>short 通道</b>（{@code useShortPlane(chunk)}，即该 chunk 真的实现了 hook ⇒ mixin 已应用）：
 * {@link #toShortPlane(BiomeGenBase[])} 产出 {@code short[256]}，经反射
 * {@code setBiomeShortArray(short[])} 写入。short 是 EndlessIDs 用来承载 0..65535 群系 id 的
 * 位宽，byte 截断（180→-76、260→4）在扩展态下是<b>真实的数据损坏面</b>，不只是崩溃诱因。</li>
 * <li><b>落回 byte</b>（{@code hookPresent()} 但该 chunk <b>没有</b>实现 hook）：说明 {@code ChunkMixin}
 * 没被应用（EndlessIDs 缺席，或其群系扩展被关成 {@code extendBiome=false}），此时 byte 访问器就是
 * 原版语义，必须继续按 v1.20.32 写平面。<b>这里不能改成"跳过不写"</b>：{@code new Chunk(...)} 把整片
 * 平面填成 {@code (byte)-1}/{@code 255} 哨兵，留空等于把身份交给懒回填，而 vanilla
 * {@code Chunk.java:1406-1407} 与 EndlessIDs 的 {@code func_76591_a} <b>都不对
 * {@code WorldChunkManager.getBiomeGenAt} 的返回值判空</b>——本 mod 的空表降级态（P1 起该处返回
 * {@code null}）会在那两处直接 NPE。留空从来不是无害降级。</li>
 * <li><b>skip 通道</b>（{@code chunk==null}、short 反射失败、或 byte 访问器<b>实际抛出</b>）：
 * 返回 {@code false} 且不写任何平面，首次打一行 {@code [GTSR][biome-plane]} error 说明原因；
 * {@code provideChunk} 忽略返回值，<b>任何情况下都不得让区块生成抛出</b>。</li>
 * </ul>
 *
 * <h3>为什么零编译期依赖</h3>
 * EndlessIDs 是<b>可选</b>第三方 mod：本仓 compile 闭包里既没有 1.7.4 也没有稳定的 1.7.3-dev 坐标，
 * 直接 {@code import} 会让缺它的环境编不过，也会把可选依赖变成硬依赖。因此本类只用
 * {@code Class.forName(HOOK_CLASS)} + {@code getMethod("setBiomeShortArray", short[].class)}，
 * 探测与反射的全部异常一律以 {@code Throwable} 兜住（含 {@code NoClassDefFoundError}
 * 与形状不符导致的 {@code NoSuchMethodException}），探测结果在类初始化期算一次并存为常量。
 *
 * <h3>缺席列占位为什么选 0（short 侧）</h3>
 * {@link GTSRChunkProviderBase} 现有三条禁止项（{@code writeBiomePlane} 的 Javadoc）：
 * <ol>
 * <li>不能写 255——vanilla 把 255 当懒回填哨兵，会回调 chunk manager 并把结果写回落盘；
 * short 侧的同一哨兵是 <b>{@code -1}</b>：{@code ChunkMixin.initShortArray} 用
 * {@code Arrays.fill(short[], (short)-1)} 填平面，{@code ChunkMixin.func_76591_a} 读回时把
 * {@code & 0xFFFF == 65535}（即 {@code -1}）判为"未写"并走懒回填。<b>故
 * {@link #MISSING_BIOME_SHORT_ID} 必须不等于 {@code (short)-1}</b>；</li>
 * <li>不能写 1——那是 plains，违反"降级维度绝不出现 plains"（byte/short 两侧同一条），
 * <b>故它也必须不等于 1</b>；</li>
 * <li>0 = vanilla ocean，非空、非哨兵、非 plains，且降级维度的地表本来就保持主体石。</li>
 * </ol>
 * 因此 short 侧取 {@code 0}，与 byte 侧 {@link GTSRChunkProviderBase#MISSING_BIOME_PLANE_ID}
 * 同语义。取值一律 {@code (short) biome.biomeID} <b>原样转窄</b>（不做 {@code & 255}）：
 * 与 byte 侧"写什么就是什么"的口径成对可读，且在 16 位位宽下 180/260 都不再回绕。
 */
public final class BiomePlaneAccess {

    /** EndlessIDs 群系扩展给 {@code Chunk} 追加的接口全名（探测键，亦是本类唯一的字符串耦合点）。 */
    public static final String HOOK_CLASS = "com.falsepattern.endlessids.mixin.helpers.ChunkBiomeHook";

    /** short 平面缺席列占位，与 byte 侧 {@code MISSING_BIOME_PLANE_ID} 同语义（0=ocean：非 -1 哨兵、非 1 plains）。 */
    public static final short MISSING_BIOME_SHORT_ID = 0;

    /** 群系平面列数（16×16），与 byte 侧 {@code writeBiomePlane} 的 {@code target.length} 口径一致。 */
    private static final int PLANE_COLUMNS = 256;

    /** hook 上合法写方法的名字（javap 实测签名：{@code void setBiomeShortArray(short[])}）。 */
    private static final String SETTER_NAME = "setBiomeShortArray";

    /**
     * hook 上的读方法名（{@code short[] getBiomeShortArray()}，与 {@link #SETTER_NAME} 同一接口的
     * 另一半；v1.20.39 T5 的列写通道 {@link #writeColumn} 需要"读出→改一列→写回"）。
     */
    private static final String GETTER_NAME = "getBiomeShortArray";

    /** 类初始化期探测一次：本运行时的 {@code Chunk} 是否被 EndlessIDs 换成 short 平面（可能为 null）。 */
    private static final Class<?> HOOK = probeHookClass();

    /** 与 {@link #HOOK} 同批探测；{@code null} 表示接口在但形状不符（老版本/改名），一律降级为 skip。 */
    private static final Method SHORT_SETTER = probeShortSetter(HOOK);

    /** 同上，读侧探针（仅 {@link #writeColumn} 用；null ⇒ short 列写不可用，走 skip 口径）。 */
    private static final Method SHORT_GETTER = probeShortGetter(HOOK);

    /** skip 原因只打一次（{@code provideChunk} 每 chunk 都过这里，逐 chunk 打会淹掉日志）。 */
    private static final AtomicBoolean SKIP_LOGGED = new AtomicBoolean();

    /** 实际落定的通道也只打一次——本轮故障就是因为"哪条通道真的跑了"只能靠猜。 */
    private static final AtomicBoolean RESOLVED_LOGGED = new AtomicBoolean();

    private BiomePlaneAccess() {}

    private static Class<?> probeHookClass() {
        try {
            return Class.forName(HOOK_CLASS);
        } catch (Throwable ignored) {
            // 没有 EndlessIDs / 版本更老 / 类名不同：一律视为"本运行时用 byte 平面"
            return null;
        }
    }

    private static Method probeShortSetter(Class<?> hook) {
        if (hook == null) {
            return null;
        }
        try {
            return hook.getMethod(SETTER_NAME, short[].class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method probeShortGetter(Class<?> hook) {
        if (hook == null) {
            return null;
        }
        try {
            return hook.getMethod(GETTER_NAME);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 探测到的 hook 接口本体；{@code null} = 本运行时没有 EndlessIDs 群系扩展。 */
    public static Class<?> hookClass() {
        return HOOK;
    }

    /** 本运行时是否存在该接口（= EndlessIDs 群系扩展在场，byte 入口已被 mixin 取消）。 */
    public static boolean hookPresent() {
        return HOOK != null;
    }

    /** 该 chunk 是否应走 short 平面（接口在场<b>且</b>这个 chunk 真的实现了它）。 */
    public static boolean useShortPlane(Chunk chunk) {
        return HOOK != null && chunk != null && HOOK.isInstance(chunk);
    }

    /**
     * 纯函数：群系数组 → short 平面（256 列，不截断）。
     * <p>
     * 与 {@link GTSRChunkProviderBase#writeBiomePlane(byte[], BiomeGenBase[])} 成对可读：同一列
     * 同一判空、同样的缺席占位口径（{@link #MISSING_BIOME_SHORT_ID}），差别只在位宽与不做 {@code & 255}。
     */
    public static short[] toShortPlane(BiomeGenBase[] biomes) {
        final short[] plane = new short[PLANE_COLUMNS];
        for (int i = 0; i < plane.length; i++) {
            final BiomeGenBase biome = biomes == null || i >= biomes.length ? null : biomes[i];
            plane[i] = biome == null ? MISSING_BIOME_SHORT_ID : (short) biome.biomeID;
        }
        return plane;
    }

    /**
     * 进维诊断行 {@code plane=} 列的取值：这是<b>classpath 级探测值</b>（"本运行时有没有 EndlessIDs 的
     * 群系扩展接口"），不是逐 chunk 的最终落点——后者归 {@link #mode(Chunk)}，并在首次写入时由
     * {@code [GTSR][biome-plane] resolved plane channel=…} 一次性行报出。
     */
    public static String runtimeMode() {
        return hookPresent() ? "short" : "byte";
    }

    /** 具体 chunk 的通道判定：{@code "short"} / {@code "byte"} / {@code "skip"}（skip 仅指根本无从写入的 {@code chunk==null}）。 */
    public static String mode(Chunk chunk) {
        if (chunk == null) {
            return "skip";
        }
        return useShortPlane(chunk) ? "short" : "byte";
    }

    /**
     * {@code provideChunk} 的<b>唯一</b>群系平面写点。
     *
     * @return {@code true} = 平面已写入；{@code false} = 已跳过（原因首次打一行 error）。
     *         调用方忽略该值即可——跳过绝不中断区块生成。
     */
    public static boolean write(Chunk chunk, BiomeGenBase[] biomes) {
        try {
            if (useShortPlane(chunk)) {
                if (writeViaHook(chunk, biomes)) {
                    logResolvedOnce("short");
                    return true;
                }
                logSkipOnce(
                    mode(chunk),
                    "reflective " + SETTER_NAME + " call failed (setter=" + SHORT_SETTER + ", hook=" + HOOK + ")");
                return false;
            }
            if (chunk == null) {
                logSkipOnce(mode(chunk), "chunk=null");
                return false;
            }
            // 走到这里 = 该 chunk 没实现 hook = ChunkMixin 未应用 ⇒ byte 访问器是原版语义，
            // 且这条分支必须与 v1.20.32 逐位一致（红线，见类头）。
            GTSRChunkProviderBase.writeBiomePlane(chunk.getBiomeArray(), biomes);
            logResolvedOnce(hookPresent() ? "byte(hook-class-present-but-not-applied)" : "byte");
            return true;
        } catch (Throwable t) {
            // 判据：任何情况下都不得让 provideChunk 抛出——含"byte 访问器仍被取消但该 chunk 未实现
            // hook"这种只可能来自第三方新形态的病态情形（离线断言 A5b 用抛错子类钉这条）。
            logSkipOnce(
                mode(chunk),
                "unexpected " + t.getClass()
                    .getName() + ": " + t.getMessage());
            return false;
        }
    }

    /**
     * <b>单列群系平面写（v1.20.39 T5，populate 后置的 sanzu 指派用）</b>：把 {@code chunk} 平面的
     * {@code (localX, localZ)} 列改写为 {@code biome}。与 {@link #write(Chunk, BiomeGenBase[])} 同一
     * 双通道纪律（short 走"读出→改列→写回"，byte 直接改 {@code getBiomeArray()} 后
     * {@code setBiomeArray} 回写），同一套一次性 skip 诊断；任何失败都不抛出（返回 false）。
     * <p>
     * <b>调用方纪律</b>：本方法不动 {@code chunk.isModified}——平面列不属于方块写，持久化标脏由
     * 调用方在确有写入后自行置位（GT5U {@code GTWorldgenerator:734} 先例）。
     *
     * @return true = 该列已写入；false = 入参非法或通道 skip（原因首次打一行 error）
     */
    public static boolean writeColumn(Chunk chunk, int localX, int localZ, BiomeGenBase biome) {
        if (chunk == null || biome == null || (localX & ~15) != 0 || (localZ & ~15) != 0) {
            return false;
        }
        final int index = (localZ << 4) | localX;
        try {
            if (useShortPlane(chunk)) {
                if (SHORT_GETTER == null || SHORT_SETTER == null) {
                    logSkipOnce(
                        mode(chunk),
                        "column write needs both " + GETTER_NAME
                            + "() and "
                            + SETTER_NAME
                            + "() (getter="
                            + SHORT_GETTER
                            + ", setter="
                            + SHORT_SETTER
                            + ")");
                    return false;
                }
                final short[] plane = (short[]) SHORT_GETTER.invoke(chunk);
                if (plane == null || plane.length != PLANE_COLUMNS) {
                    logSkipOnce(mode(chunk), "column write got malformed short plane (len=" + length(plane) + ")");
                    return false;
                }
                plane[index] = (short) biome.biomeID;
                SHORT_SETTER.invoke(chunk, new Object[] { plane });
                return true;
            }
            final byte[] plane = chunk.getBiomeArray();
            if (plane == null || plane.length != PLANE_COLUMNS) {
                logSkipOnce(mode(chunk), "column write got malformed byte plane (len=" + length(plane) + ")");
                return false;
            }
            plane[index] = (byte) biome.biomeID;
            chunk.setBiomeArray(plane);
            return true;
        } catch (Throwable t) {
            logSkipOnce(
                mode(chunk),
                "column write unexpected " + t.getClass()
                    .getName() + ": " + t.getMessage());
            return false;
        }
    }

    private static int length(short[] plane) {
        return plane == null ? -1 : plane.length;
    }

    private static int length(byte[] plane) {
        return plane == null ? -1 : plane.length;
    }

    /**
     * short 通道的派发核（离线可测：参数取 {@code Object}，测试替身即可驱动）。
     *
     * @return {@code true} = setter 已被成功调用；{@code false} = 目标不是 hook 实例 / 反射不可用 / 调用抛错。
     *         本方法<b>只派发、不记日志</b>（日志归 {@link #write(Chunk, BiomeGenBase[])} 的一次性锚点）。
     */
    public static boolean writeViaHook(Object target, BiomeGenBase[] biomes) {
        if (target == null || !hookPresent() || !HOOK.isInstance(target)) {
            return false;
        }
        final Method setter = SHORT_SETTER;
        if (setter == null) {
            return false;
        }
        try {
            setter.invoke(target, new Object[] { toShortPlane(biomes) });
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 一次性 skip 锚点：诊断本身也必须零抛出。 */
    private static void logSkipOnce(String mode, String reason) {
        if (!SKIP_LOGGED.compareAndSet(false, true)) {
            return;
        }
        try {
            GTSteamReborn.LOG.error(
                "[GTSR][biome-plane] biome plane write SKIPPED mode={} reason={}"
                    + " (chunk biomes stay unwritten; plane column in [GTSR][diag] still reports mode)",
                mode,
                reason);
        } catch (Throwable ignored) {
            // 日志器不可用（离线 JVM / 极早期装配）也必须安静
        }
    }

    /**
     * 一次性打「哪条通道真的落定了」——本轮故障的直接教训：{@code plane=} 列报的是 classpath 探测值，
     * 而"这个运行时的 {@code Chunk} 到底有没有被换成 short 存储"此前只能靠猜。
     */
    private static void logResolvedOnce(String resolved) {
        if (!RESOLVED_LOGGED.compareAndSet(false, true)) {
            return;
        }
        try {
            GTSteamReborn.LOG
                .info("[GTSR][biome-plane] resolved plane channel={} (hook={} on {})", resolved, HOOK, HOOK_CLASS);
        } catch (Throwable ignored) {
            // 同上：诊断零抛出
        }
    }
}
