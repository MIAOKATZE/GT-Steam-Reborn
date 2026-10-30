import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
import com.miaokatze.gtsr.common.dimension.testlab.TestLabCatalog;
import com.miaokatze.gtsr.common.dimension.testlab.TestLabCriticalTask;
import com.miaokatze.gtsr.common.dimension.testlab.TestLabTask;

/** Exercises the complete production catalog, including critical controllers and ME ports. */
public final class TestLabCatalogContract {
    public static void main(String[] args) {
        List<TestLabTask> tasks = TestLabCatalog.tasks();
        Map<String, TestLabTask> byId = new HashMap<>();
        for (TestLabTask task : tasks) {
            if (byId.put(task.id(), task) != null) throw new AssertionError("Duplicate task: " + task.id());
        }
        for (MetaTileEntityID id : MetaTileEntityID.values()) {
            String key = id.name().toLowerCase(Locale.ROOT);
            if (!byId.containsKey(key)) throw new AssertionError("Missing registered machine: " + id);
        }
        requireCritical(byId, "critical_loom");
        for (CriticalMachineKind kind : CriticalMachineKind.values()) {
            requireCritical(byId, "critical_" + kind.name().toLowerCase(Locale.ROOT));
        }
        for (String port : new String[] { "critical_input_hatch_me", "critical_input_bus_me",
            "critical_output_hatch_me", "critical_output_bus_me" }) {
            TestLabTask task = byId.get(port);
            if (task == null || !task.getClass().getSimpleName().equals("TestLabMachineTask")) {
                throw new AssertionError("ME port must use the generic machine task: " + port);
            }
        }
        if (byId.get("critical_singularity_compressor") instanceof TestLabCriticalTask) {
            throw new AssertionError("Singularity compressor must retain its generic task");
        }
        System.out.println("RESULT TestLabCatalog PASS tasks=" + tasks.size()
            + " registered=" + MetaTileEntityID.values().length + " critical="
            + (CriticalMachineKind.values().length + 1) + " ME_ports=4");
    }

    private static void requireCritical(Map<String, TestLabTask> tasks, String id) {
        if (!(tasks.get(id) instanceof TestLabCriticalTask)) {
            throw new AssertionError("Critical controller must use its live critical task: " + id);
        }
    }
}
