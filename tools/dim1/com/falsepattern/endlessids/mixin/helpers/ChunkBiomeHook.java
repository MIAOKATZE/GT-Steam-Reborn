package com.falsepattern.endlessids.mixin.helpers;

/**
 * EndlessIDs 群系扩展 hook 接口的<b>测试替身</b>（<b>tools/dim1 专用，不属于生产代码</b>；由
 * {@code -sourcepath tools/dim1} 隐式编入 {@code temp/p4-surface/tools}，只可能出现在离线断言
 * classpath 上——它不在 {@code src/main/java} 下，因此不进 jar、不进生产 classpath）。
 * <p>
 * 方法名与签名与 {@code endlessids-mc1.7.10-1.7.4.jar} 的 javap 实测<b>逐字一致</b>
 * （{@code temp/eid/com/falsepattern/endlessids/mixin/helpers/ChunkBiomeHook.class}）：
 *
 * <pre>
 * public interface com.falsepattern.endlessids.mixin.helpers.ChunkBiomeHook {
 *   public abstract short[] getBiomeShortArray();
 *   public abstract void setBiomeShortArray(short[]);
 * }
 * </pre>
 * <p>
 * 为什么替身是等价的：生产侧 {@code BiomePlaneAccess} 对该类只有<b>运行期按名字探测</b>
 * （{@code Class.forName("com.falsepattern.endlessids.mixin.helpers.ChunkBiomeHook")} +
 * {@code getMethod("setBiomeShortArray", short[].class)} + {@code isInstance}/{@code invoke}），
 * 零编译期依赖、零字节码耦合。游戏内该接口由 EndlessIDs 提供、并由其
 * {@code mixin/mixins/common/biome/vanilla/ChunkMixin} 混入 {@code Chunk}；离线 JVM 既没有
 * EndlessIDs jar 也没有 mixin 处理器 ⇒ 无法构造"真 hook 的 Chunk"。而 {@code Class.forName}
 * 只看类名、{@code isInstance} 只看"目标是否实现了这个 Class 对象"、{@code getMethod} 只看方法描述符
 * ——三条判据在"同名同签名"的替身与真类上行为完全相同。因此本替身能把 short 通道跑到
 * <b>真实反射派发</b>（不是 mock 返回值），这正是 {@code BiomePlaneCompatCheck} 需要的证据。
 * <p>
 * <b>禁止同 classpath 混用</b>：{@code tools/dim1} 编出的 {@code temp/p4-surface/tools} 在离线
 * classpath 上排在依赖 jar 之前。若将来把真实 EndlessIDs jar 挂进同一 classpath，本替身会
 * <b>遮蔽</b>真类（{@code Class.forName} 命中先加载者），此时 {@code hookPresent()} 仍为真但
 * {@code isInstance(真 Chunk)} 为假（两个不同的 Class 对象）⇒ short 通道静默退化为 skip。
 * 届时必须删除本替身或把 {@code tools/dim1} 移出首位（与 {@code tools/dim1/gregtech/api/GregTechAPI.java}
 * 同一条纪律）。
 */
public interface ChunkBiomeHook {

    short[] getBiomeShortArray();

    void setBiomeShortArray(short[] shortArray);
}
