package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.world.World;

import com.miaokatze.miaokatzecore.api.testlab.LabCase;
import com.miaokatze.miaokatzecore.api.testlab.LabModule;
import com.miaokatze.miaokatzecore.api.testlab.LabRegistry;

import cpw.mods.fml.common.event.FMLServerStartingEvent;

/** Complete testlab variant only. The normal mod loads this solely after an explicit opt in. */
public final class TestLabSupport implements LabModule {

    private static final TestLabSupport MODULE = new TestLabSupport();

    public static void preInit() {
        try {
            Class
                .forName("com.miaokatze.miaokatzecore.testlab.LabDriver", false, TestLabSupport.class.getClassLoader());
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException("GTSR testlab requires the complete Core -testlab variant", missing);
        }
        TestLabDimension.register();
        if (TestLabDimension.enabled()) {
            TestLabRuntime.register();
            LabRegistry.register(MODULE);
        }
    }

    public static void serverStarting(FMLServerStartingEvent event) {
        if (!TestLabDimension.enabled()) return;
        TestLabRuntime.INSTANCE.serverStarting();
        event.registerServerCommand(new TestLabCommand());
    }

    public String id() {
        return "gtsr";
    }

    public boolean enabled() {
        return TestLabDimension.enabled();
    }

    public List<LabCase> cases() {
        List<LabCase> result = new ArrayList<>();
        for (TestLabTask task : TestLabCatalog.tasks()) result.add(TestLabMetadata.describe(task));
        for (TestLabTask task : TestLabCatalog.tasks()) if (TestLabExistingLayoutTask.supports(task)) result.add(
            new LabCase(
                "existing-layout." + task.id(),
                "Saved layout recovery: " + task.displayName(),
                "server",
                new java.util.LinkedHashSet<>(
                    java.util.Arrays.asList(
                        "group:existing-layout",
                        "recovery",
                        "world:isolated-persistent-lab",
                        "mutation:fixture-resupply")),
                false,
                false));
        for (TestLabTask task : TestLabUiProbes.tasks()) result.add(TestLabMetadata.describe(task));
        for (TestLabTask task : TestLabStructureContractTask.select("all")) result.add(TestLabMetadata.describe(task));
        result.add(
            new LabCase(
                "perf.quick",
                "Bounded server performance representatives",
                "server",
                java.util.Collections.singleton("stress"),
                false,
                false));
        result.add(
            new LabCase(
                "perf.all",
                "Bounded server performance representatives and UI models",
                "server",
                java.util.Collections.singleton("stress"),
                false,
                false));
        result.add(
            new LabCase(
                "client.display-list",
                "Real GL display-list ownership and rollback",
                "client",
                java.util.Collections.singleton("client"),
                false,
                false));
        return result;
    }

    public void execute(ICommandSender sender, String[] args) {
        if (!sender.canCommandSenderUseCommand(2, "miaotest"))
            throw new IllegalArgumentException("OP level 2 required");
        if (args.length == 2 && "run".equals(args[0])) {
            String id = args[1];
            if (id.startsWith("existing-layout.")) {
                TestLabRuntime.INSTANCE.runExisting(id.substring("existing-layout.".length()), sender);
                return;
            }
            if (id.startsWith("ui.")) {
                TestLabRuntime.INSTANCE.runUi(id, sender);
                return;
            }
            if (id.startsWith("structure.")) {
                TestLabRuntime.INSTANCE.runStructure(id, sender);
                return;
            }
            if ("perf.quick".equals(id) || "perf.all".equals(id)) {
                TestLabRuntime.INSTANCE.runPerformance(id.substring(5), null, sender);
                return;
            }
            if ("client.display-list".equals(id)) throw new IllegalArgumentException(
                "UNSUPPORTED on server: requires isolated client GL QA, no desktop input; invoke TestLabDisplayListProbe from client QA");
        }
        if (args.length == 2 && "run".equals(args[0])) {
            TestLabRuntime.INSTANCE.run(args[1], sender);
            return;
        }
        if (args.length == 1 && "stop".equals(args[0])) {
            TestLabRuntime.INSTANCE.stop("Operator stop through /miaotest");
            return;
        }
        if (args.length == 1 && "reset".equals(args[0])) {
            TestLabRuntime.INSTANCE.reset(null, sender);
            return;
        }
        if (args.length == 1 && ("status".equals(args[0]) || "report".equals(args[0]))) {
            sender.addChatMessage(new net.minecraft.util.ChatComponentText(TestLabRuntime.INSTANCE.status()));
            return;
        }
        throw new IllegalArgumentException(
            "Usage: /miaotest gtsr run <all|caseId>|status|report|stop|reset; extended legacy operations use /gtsrtest");
    }

    public java.util.Map<String, Object> snapshot() {
        return TestLabRuntime.INSTANCE.snapshot();
    }

    public String status() {
        return TestLabRuntime.INSTANCE.status();
    }

    public boolean ownsWorld(World world) {
        return enabled() && world != null
            && world.provider instanceof TestLabWorldProvider
            && world.provider.dimensionId == TestLabDimension.id();
    }
}
