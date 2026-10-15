package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;

/** Server-only production UI probes, deliberately independent of machine structure construction. */
public final class TestLabUiProbes {

    private TestLabUiProbes() {}

    public static List<TestLabTask> tasks() {
        List<TestLabTask> tasks = new ArrayList<>();
        for (MetaTileEntityID id : MetaTileEntityID.values()) {
            tasks.add(new TestLabUiTask(id, false));
            if (id.name()
                .startsWith("CRITICAL_") && id != MetaTileEntityID.CRITICAL_SINGULARITY_COMPRESSOR) {
                tasks.add(new TestLabUiTask(id, true));
            }
        }
        return Collections.unmodifiableList(tasks);
    }
}
