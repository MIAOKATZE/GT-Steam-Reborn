import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;

/**
 * S4b 验收②补充证据（离线等价；不进 jar）：调用 {@link CityVariants#registerVariants()} 后
 * 断言 {@link StructureRegistry} 含 <b>28</b> 个 PROSPERITY 城变体名（26 基础 + P16-B3 城内
 * 巨构 2；与 runServer 日志行 "[GTSR] prosperity city variants: 28 registered" 同源数据；
 * 巨构登记 footprint = 申报总 bbox 24/20，"申报不是钳制"与 P16-B1 城外跨片同语义）。
 * 零 Minecraft 依赖：
 * {@code java -cp build/classes/java/main tools/dim1/CityRegistryCheck.java}
 */
public class CityRegistryCheck {

    /** 城内变体名册数（P16-B3：26 基础 + 2 巨构 = 28）。 */
    private static final int EXPECTED_CITY_VARIANTS = 28;

    public static void main(String[] args) {
        CityVariants.registerVariants();
        int prosperitCity = 0;
        for (final String name : StructureRegistry.names()) {
            final StructureRegistry.Entry e = StructureRegistry.get(name);
            if (e.dimension == StructureRegistry.Dimension.PROSPERITY) {
                prosperitCity++;
            }
        }
        if (prosperitCity != EXPECTED_CITY_VARIANTS || CityVariants.ALL.length != EXPECTED_CITY_VARIANTS) {
            System.out.println("REGISTRY FAIL: city=" + prosperitCity + " all=" + CityVariants.ALL.length);
            System.exit(1);
        }
        System.out.println("REGISTRY PASS: [GTSR] prosperity city variants: " + prosperitCity + " registered");
        final StringBuilder names = new StringBuilder();
        for (final CityVariants.Variant v : CityVariants.ALL) {
            names.append(v.name)
                .append(' ');
        }
        System.out.println("NAMES: " + names);
        System.out.println("REGISTRY DONE");
    }
}
