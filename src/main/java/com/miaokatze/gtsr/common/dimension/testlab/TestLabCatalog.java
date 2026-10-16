package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;

/** The enum is the coverage authority: new registered IDs automatically appear in the lab. */
public final class TestLabCatalog {

    private TestLabCatalog() {}

    public static List<TestLabTask> tasks() {
        List<TestLabTask> tasks = new ArrayList<>();
        for (MetaTileEntityID id : MetaTileEntityID.values()) {
            if (id.name()
                .startsWith("CRITICAL_") && id != MetaTileEntityID.CRITICAL_SINGULARITY_COMPRESSOR) {
                tasks.add(
                    new TestLabCriticalTask(
                        id.name()
                            .substring("CRITICAL_".length())
                            .toLowerCase(java.util.Locale.ROOT)));
                continue;
            }
            if (id.name()
                .startsWith("CLUSTER_")) {
                tasks.add(new TestLabClusterTask(id));
                continue;
            }
            if (TestLabHubTask.handles(id)) {
                tasks.add(new TestLabHubTask(id));
                continue;
            }
            if (id == MetaTileEntityID.SINGULARITY_DRILLING_HUB || id == MetaTileEntityID.SINGULARITY_MINER_NODE
                || id == MetaTileEntityID.SINGULARITY_DRILLING_NODE) {
                tasks.add(new TestLabDrillingTask(id));
                continue;
            }
            TestLabTask hatch = TestLabMachineTask.hatchTask(id);
            if (hatch != null) {
                tasks.add(hatch);
                continue;
            }
            tasks.add(new TestLabMachineTask(id, 1));
            switch (id) {
                case LARGE_COKE_OVEN:
                case LARGE_STEAM_FURNACE:
                case AIR_COMPRESSOR:
                case ATMOSPHERIC_CENTRIFUGE:
                case STEAM_FLUID_DRILL:
                case CRUST_STEAM_BORER:
                case VEIN_STEAM_PYROLYZER:
                case LARGE_GEOTHERMAL_STEAM_BOILER:
                case LARGE_SOLAR_OVERPRESSURE_ARRAY:
                case GEAR_STEAM_COMPRESSOR:
                    tasks.add(new TestLabMachineTask(id, 2));
                    break;
                default:
                    break;
            }
        }
        return Collections.unmodifiableList(tasks);
    }

    public static List<String> machineIds() {
        List<String> result = new ArrayList<>();
        for (MetaTileEntityID id : MetaTileEntityID.values()) result.add(
            id.name()
                .toLowerCase(java.util.Locale.ROOT));
        return Collections.unmodifiableList(result);
    }
}
