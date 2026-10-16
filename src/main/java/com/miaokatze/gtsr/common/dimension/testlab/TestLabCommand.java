package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Agent-first console interface. Every mutation is OP gated by the command dispatcher. */
public final class TestLabCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "gtsrtest";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gtsrtest enter|return|list|run <all|id>|ui <all|id|list|status|stop>|perf <quick|all|machine id|ui id|status|stop>|status|report|stop|reset|regenerate [all|id]|supply <x y z fluidName|list|clear|off x y z>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
            reply(sender, "ERROR 需要 OP 等级2");
            return;
        }
        try {
            if (args.length == 0) throw new IllegalArgumentException(getCommandUsage(sender));
            String command = args[0];
            if ("perf".equals(command)) {
                performance(sender, args);
                return;
            }
            if ("supply".equals(command)) {
                supply(sender, args);
                return;
            }
            if ("ui".equals(command)) {
                ui(sender, args);
                return;
            }
            int max = "run".equals(command) || "regenerate".equals(command) ? 2 : 1;
            if (args.length > max) throw new IllegalArgumentException("参数过多；" + getCommandUsage(sender));
            switch (command) {
                case "enter":
                    enter(getCommandSenderAsPlayer(sender));
                    break;
                case "return":
                    leave(getCommandSenderAsPlayer(sender));
                    break;
                case "list":
                    for (TestLabTask task : TestLabCatalog.tasks()) reply(
                        sender,
                        "id=" + task.id()
                            + " name="
                            + task.displayName()
                            + " timeoutTicks="
                            + task.timeoutTicks()
                            + " origin="
                            + TestLabRuntime.originX(task.id())
                            + ",65,"
                            + TestLabRuntime.originZ(task.id()));
                    break;
                case "run":
                    if (args.length != 2) throw new IllegalArgumentException("用法：/gtsrtest run <all|id>");
                    TestLabRuntime.INSTANCE.run(args[1], sender);
                    break;
                case "status":
                case "report":
                    reply(sender, TestLabRuntime.INSTANCE.status());
                    break;
                case "stop":
                    TestLabRuntime.INSTANCE.stop("操作员停止");
                    reply(sender, "state=STOPPED");
                    break;
                case "reset":
                    TestLabRuntime.INSTANCE.reset(null, sender);
                    break;
                case "regenerate":
                    TestLabRuntime.INSTANCE.reset(args.length == 2 ? args[1] : "all", sender);
                    break;
                default:
                    throw new IllegalArgumentException(getCommandUsage(sender));
            }
        } catch (Exception e) {
            reply(
                sender,
                "ERROR " + e.getClass()
                    .getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void enter(EntityPlayerMP player) {
        WorldServer target = TestLabDimension.world();
        if (player.dimension != TestLabDimension.id()) {
            NBTTagCompound data = player.getEntityData();
            NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
            NBTTagCompound back = new NBTTagCompound();
            back.setInteger("dimension", player.dimension);
            back.setDouble("x", player.posX);
            back.setDouble("y", player.posY);
            back.setDouble("z", player.posZ);
            back.setFloat("yaw", player.rotationYaw);
            back.setFloat("pitch", player.rotationPitch);
            persisted.setTag("gtsrTestLabReturn", back);
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        }
        transfer(player, target, 0.5, 65, 0.5, 0, 0);
        reply(
            player,
            "dimension=" + TestLabDimension.id()
                + " debug=READY；控制台/OP 可用 run、status、report、stop、reset、regenerate；进入不自动运行任务");
    }

    private static void ui(ICommandSender sender, String[] args) {
        if (args.length != 2) throw new IllegalArgumentException("用法：/gtsrtest ui <all|id|list|status|stop>");
        switch (args[1]) {
            case "list":
                for (TestLabTask task : TestLabUiProbes.tasks()) reply(
                    sender,
                    "category=UI id=" + task
                        .id() + " name=" + task.displayName() + " timeoutTicks=" + task.timeoutTicks());
                break;
            case "status":
                reply(sender, TestLabRuntime.INSTANCE.status());
                break;
            case "stop":
                TestLabRuntime.INSTANCE.stop("操作员停止 UI/机器探针");
                reply(sender, "state=STOPPED");
                break;
            default:
                TestLabRuntime.INSTANCE.runUi(args[1], sender);
                break;
        }
    }

    private static void performance(ICommandSender sender, String[] args) {
        if (args.length == 2 && ("status".equals(args[1]) || "report".equals(args[1]))) {
            reply(sender, TestLabRuntime.INSTANCE.status());
            return;
        }
        if (args.length == 2 && "stop".equals(args[1])) {
            TestLabRuntime.INSTANCE.stop("操作员停止性能/机器/UI任务");
            reply(sender, "state=STOPPED");
            return;
        }
        if (args.length == 2 && ("quick".equals(args[1]) || "all".equals(args[1]))) {
            TestLabRuntime.INSTANCE.runPerformance(args[1], null, sender);
            return;
        }
        if (args.length == 3 && ("machine".equals(args[1]) || "ui".equals(args[1]))) {
            TestLabRuntime.INSTANCE.runPerformance(args[1], args[2], sender);
            return;
        }
        throw new IllegalArgumentException(
            "用法：/gtsrtest perf <quick|all|machine id|ui id|status|stop>；all为模块代表覆盖，不是全部机器功能覆盖");
    }

    private static void supply(ICommandSender sender, String[] args) {
        WorldServer world = TestLabDimension.world();
        if (args.length == 2 && "list".equals(args[1])) {
            reply(sender, "supply=" + TestLabSupply.describe(world));
            return;
        }
        if (args.length == 2 && "clear".equals(args[1])) {
            TestLabRuntime.INSTANCE.clearManualSupply();
            TestLabSupply.clear(world);
            reply(sender, "supply=CLEARED（包括当前测试任务供给；重生成也会清除手动供给）");
            return;
        }
        boolean off = args.length == 5 && "off".equals(args[1]);
        if (!off && args.length != 5)
            throw new IllegalArgumentException("用法：supply x y z fluidName | supply list|clear|off x y z");
        int offset = off ? 2 : 1;
        int x = parseIntBounded(sender, args[offset], -29999984, 29999984);
        int y = parseIntBounded(sender, args[offset + 1], 0, 255);
        int z = parseIntBounded(sender, args[offset + 2], -29999984, 29999984);
        TestLabFixtures.requireChunk(world, x >> 4, z >> 4);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof IGregTechTileEntity)) throw new IllegalArgumentException("该测试维度坐标不是 GT 仓室");
        IGregTechTileEntity gt = (IGregTechTileEntity) tile;
        if (off) {
            TestLabSupply.remove(gt);
            TestLabRuntime.INSTANCE.pruneManualSupply();
            reply(sender, "supply=OFF pos=" + x + "," + y + "," + z);
            return;
        }
        Fluid fluid = FluidRegistry.getFluid(args[4]);
        if (fluid == null) throw new IllegalArgumentException("未知流体注册名：" + args[4]);
        FluidStack request = new FluidStack(fluid, 1);
        TestLabSupply.validateFluid(world, gt, request);
        TestLabRuntime.INSTANCE.retainSupply(world, x, z);
        TestLabSupply.add(world, gt, new FluidStack(fluid, 1));
        reply(
            sender,
            "supply=ON pos=" + x
                + ","
                + y
                + ","
                + z
                + " fluid="
                + fluid.getName()
                + " scope=测试维度；实际接纳遵循原仓流体/过滤/容量，供给不跨重启，reset/regenerate会清除");
    }

    private static void leave(EntityPlayerMP player) {
        if (player.dimension != TestLabDimension.id()) throw new IllegalStateException("玩家当前不在测试维度");
        NBTTagCompound persisted = player.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        NBTTagCompound back = persisted.getCompoundTag("gtsrTestLabReturn");
        int dim = back.hasKey("dimension") ? back.getInteger("dimension") : 0;
        if (dim == TestLabDimension.id() || !DimensionManager.isDimensionRegistered(dim)) dim = 0;
        WorldServer target = DimensionManager.getWorld(dim);
        if (target == null) {
            DimensionManager.initDimension(dim);
            target = DimensionManager.getWorld(dim);
        }
        if (target == null) throw new IllegalStateException("返回维度加载失败");
        boolean saved = back.hasKey("x");
        transfer(
            player,
            target,
            saved ? back.getDouble("x") : target.getSpawnPoint().posX + 0.5,
            saved ? back.getDouble("y") : target.getSpawnPoint().posY,
            saved ? back.getDouble("z") : target.getSpawnPoint().posZ + 0.5,
            back.getFloat("yaw"),
            back.getFloat("pitch"));
        persisted.removeTag("gtsrTestLabReturn");
        reply(player, "已返回 dimension=" + dim);
    }

    private static void transfer(EntityPlayerMP player, WorldServer target, final double x, final double y,
        final double z, final float yaw, final float pitch) {
        if (player.dimension != target.provider.dimensionId) {
            MinecraftServer.getServer()
                .getConfigurationManager()
                .transferPlayerToDimension(player, target.provider.dimensionId, new Teleporter(target) {

                    @Override
                    public void placeInPortal(Entity entity, double ox, double oy, double oz, float rotation) {
                        entity.setLocationAndAngles(x, y, z, yaw, pitch);
                    }
                });
        }
        player.playerNetServerHandler.setPlayerLocation(x, y, z, yaw, pitch);
        player.motionX = player.motionY = player.motionZ = 0;
        player.fallDistance = 0;
    }

    private static void reply(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText(TestLabRuntime.PREFIX + text));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(
            args,
            "enter",
            "return",
            "list",
            "run",
            "ui",
            "perf",
            "status",
            "report",
            "stop",
            "reset",
            "regenerate",
            "supply");
        if (args.length == 2 && "perf".equals(args[0]))
            return getListOfStringsMatchingLastWord(args, "quick", "all", "machine", "ui", "status", "stop");
        if (args.length == 3 && "perf".equals(args[0])) {
            List<String> ids = new ArrayList<>();
            if ("machine".equals(args[1])) for (TestLabTask task : TestLabCatalog.tasks()) ids.add(task.id());
            if ("ui".equals(args[1])) for (TestLabTask task : TestLabUiProbes.tasks()) ids.add(task.id());
            return getListOfStringsMatchingLastWord(args, ids.toArray(new String[0]));
        }
        if (args.length == 2 && "ui".equals(args[0])) {
            List<String> ids = new ArrayList<>();
            java.util.Collections.addAll(ids, "all", "list", "status", "stop");
            for (TestLabTask task : TestLabUiProbes.tasks()) ids.add(task.id());
            return getListOfStringsMatchingLastWord(args, ids.toArray(new String[0]));
        }
        if (args.length == 2 && ("run".equals(args[0]) || "regenerate".equals(args[0]))) {
            List<String> ids = new ArrayList<>();
            ids.add("all");
            for (TestLabTask task : TestLabCatalog.tasks()) ids.add(task.id());
            return getListOfStringsMatchingLastWord(args, ids.toArray(new String[0]));
        }
        return null;
    }
}
