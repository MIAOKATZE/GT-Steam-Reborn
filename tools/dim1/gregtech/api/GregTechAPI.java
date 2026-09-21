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
 * <p>
 * <b>P16-B1 同步（B1 切片，城外跨 chunk 巨构）</b>：{@code CityBlockResolver} 的红线表新增两键
 * （燃烧室外壳 = {@code sBlockCasings3}、防爆玻璃 = {@code sBlockGlass1}），本占位类必须同名补字段，
 * 否则离线链在 {@code resolve()} 里 {@code NoSuchFieldError}。补法与既有两字段完全同形：
 * 只声明、不赋值 ⇒ 离线读到 null ⇒ 走"GT 缺席 ⇒ 不 put ⇒ 调用方跳过该部件"的既有防御链。
 * 需要"身份臂真的咬合"的证据时，由 {@code RuinFamilyCheck} 的 COLOSSUS 组当场给这些字段赋值再还原
 * （与既有 C3 探针同一手法），本类自身永远不自带方块。
 */
public class GregTechAPI {

    public static Block sBlockCasings1;
    public static Block sBlockCasings2;
    /** P16-B1：燃烧室外壳（青铜 meta13 / 钢 meta14 可入残骸，钨钢 meta15 禁用）。 */
    public static Block sBlockCasings3;
    /** P16-B1：防爆玻璃（meta10 = ReinforcedGlass，GT5U {@code BlockGlass1.java:43}）。 */
    public static Block sBlockGlass1;
    /**
     * P16-B1 只为<b>反假绿探针</b>而存在：{@code RuinFamilyCheck} 的 K3d 臂要拿"机器注册体的身份"
     * 验一次禁用清单咬不咬得住，故本类需要一个能承载该身份的字段。
     * <b>生产侧没有任何一条解析链会读到它</b>——{@code CityBlockResolver} 的键表里不存在
     * {@code gt.blockmachines}，那才是"残骸落块永不含机器块"的第一道闸。
     */
    public static Block sBlockMachines;
}
