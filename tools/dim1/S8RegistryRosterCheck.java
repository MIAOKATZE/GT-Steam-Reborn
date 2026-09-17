import java.util.ArrayList;
import java.util.List;

import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachinePlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachineShapes;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;
import com.miaokatze.gtsr.common.dimension.shattered.WorldGenShatteredRuins;

/**
 * dim1 S8 总冒烟离线断言①（一次性自检 main，不进 jar，tools/ 惯例）：依次调用
 * {@link RuinedMachinePlacer#registerVariants()}（5 机型）+
 * {@link CityVariants#registerVariants()}（26 城变体）+
 * {@link WorldGenShatteredRuins#registerVariants()}（2 husk，静态方法不触 GTSteamReborn），
 * 断言 {@link StructureRegistry} 全名单 = 33 名且维度归属 26+5=PROSPERITY / 2=SHATTERED，
 * 并逐名打印（与 runServer 日志 "[GTSR] prosperity city variants: 26 registered" 同源数据）。
 * {@code java -cp build/classes/java/main tools/dim1/S8RegistryRosterCheck.java}
 */
public class S8RegistryRosterCheck {

    public static void main(String[] args) {
        RuinedMachinePlacer.registerVariants();
        CityVariants.registerVariants();
        WorldGenShatteredRuins.registerVariants();

        final List<String> names = StructureRegistry.names();
        int machines = 0;
        int cities = 0;
        int husks = 0;
        final List<String> machineNames = new ArrayList<>();
        final List<String> huskNames = new ArrayList<>();
        for (final StructureRegistry.Entry e : StructureRegistry.all()) {
            if (e.dimension == StructureRegistry.Dimension.SHATTERED) {
                husks++;
                huskNames.add(e.name);
            } else if (contains(RuinedMachineShapes.ALL, e.name)) {
                machines++;
                machineNames.add(e.name);
            } else {
                cities++;
            }
        }

        if (names.size() != 33 || machines != 5 || cities != 26 || husks != 2
            || CityVariants.ALL.length != 26
            || RuinedMachineShapes.ALL.length != 5) {
            System.out
                .println("ROSTER FAIL: total=" + names.size() + " machines=" + machines + " cities=" + cities
                    + " husks=" + husks
                    + " cityAll=" + CityVariants.ALL.length + " machineAll=" + RuinedMachineShapes.ALL.length);
            System.out.println("NAMES: " + names);
            System.exit(1);
        }
        for (final String n : names) {
            if (!n.equals(n.trim()) || n.isEmpty()) {
                System.out.println("ROSTER FAIL: bad name token '" + n + "'");
                System.exit(1);
            }
        }
        System.out.println("ROSTER PASS: StructureRegistry names() = 33 (5 machines + 26 city + 2 husk)");
        System.out.println("MACHINES(5): " + machineNames);
        System.out.println("HUSKS(2): " + huskNames);
        System.out.println("NAMES(33): " + names);
        System.out.println("ROSTER DONE");
    }

    private static boolean contains(RuinedMachineShapes.Shape[] shapes, String name) {
        for (final RuinedMachineShapes.Shape s : shapes) {
            if (s.name.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
