package gregtech.api;

import net.minecraft.block.Block;

/**
 * 离线断言用的最小占位类（<b>tools/dim1 专用，不属于生产代码</b>；由 {@code -sourcepath
 * tools/dim1} 隐式编入 {@code temp/p4-surface/tools}，只可能出现在离线断言 classpath 上）。
 * <p>
 * 为什么需要：{@code CityBlockResolver.resolve} 会读 {@code GregTechAPI.sBlockCasings1/2}，
 * 而离线 JVM 的 classpath（temp/p1-cp.txt：本模组 class + patchedMc + 4 个第三方 jar）里没有
 * GT5U ⇒ 直接 {@code NoClassDefFoundError}，编排链（outpost/机器经 String 键解析）跑不动。
 * <p>
 * 为什么占位是等价的：本类的两个字段在游戏内由 GT5U 的 preInit 赋值；离线 JVM 从不执行那段注册，
 * 即便挂上真实 GT5U jar 读到的也是 {@code null}。{@code CityBlockResolver:66-71} 对 null 的
 * 既有防御链（不 put ⇒ resolve 落空 ⇒ 调用方跳过该部件）正是本工具走的分支。
 * 因此本占位只影响 {@code 'X'/'Z'} 两类 casing 部件（不进地表层），
 * 不影响本片关心的 {@code 's' = gtsr:ProsperitySurface} 地板（来自 {@code BlocksGTSR}）。
 * <p>
 * <b>禁止同 classpath 混用</b>：{@code tools/dim1} 在离线 classpath 上排在依赖 jar 之前，若将来把
 * 真实 GT5U jar 挂进同一 classpath，本占位会<b>遮蔽</b>真类而使 {@code 'X'/'Z'} 静默缺席。届时必须
 * 删除本文件或把 {@code tools/dim1} 移出首位，并重新量测 casing 部件的绝对落量（P4 只交付差分结论）。
 */
public class GregTechAPI {

    public static Block sBlockCasings1;
    public static Block sBlockCasings2;
}
