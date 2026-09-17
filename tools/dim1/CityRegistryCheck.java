import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;

/**
 * S4b 验收②补充证据（离线等价；不进 jar）：调用 {@link CityVariants#registerVariants()} 后
 * 断言 {@link StructureRegistry} 含 26 个 PROSPERITY 城变体名（与 runServer 日志行
 * "[GTSR] prosperity city variants: 26 registered" 同源数据）。零 Minecraft 依赖：
 * {@code java -cp build/classes/java/main tools/dim1/CityRegistryCheck.java}
 */
public class CityRegistryCheck {

    public static void main(String[] args) {
        CityVariants.registerVariants();
        int prosperitCity = 0;
        for (final String name : StructureRegistry.names()) {
            final StructureRegistry.Entry e = StructureRegistry.get(name);
            if (e.dimension == StructureRegistry.Dimension.PROSPERITY) {
                prosperitCity++;
            }
        }
        if (prosperitCity != 26 || CityVariants.ALL.length != 26) {
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
