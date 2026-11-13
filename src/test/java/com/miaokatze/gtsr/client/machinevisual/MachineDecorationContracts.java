// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.machinevisual;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.FieldNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualDefinition;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualSnapshot;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Actual authority policy, packaged geometry and bytecode boundaries; no World, GL, game or Testlab fixture. */
public final class MachineDecorationContracts {

    private static int checks;

    private MachineDecorationContracts() {}

    public static void main(String[] args) throws Exception {
        authorityContracts();
        budgetContracts();
        geometryContracts();
        bytecodeContracts();
        System.out.println("PASS MachineDecoration contracts: " + checks);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static MachineVisualSnapshot snapshot(int flags) {
        ByteBuf bytes = Unpooled.buffer(MachineVisualSnapshot.BYTES);
        try {
            for (int i = 0; i < 9; i++) bytes.writeInt(i == 6 ? flags : i == 8 ? 100 : 0);
            for (int i = 0; i < 8; i++) bytes.writeLong(1);
            return MachineVisualSnapshot.read(bytes);
        } finally {
            bytes.release();
        }
    }

    private static void authorityContracts() {
        MachineVisualSnapshot working = snapshot(7);
        check(MachineVisualClient.decorationMask(true, null, 2, 0) == 0, "no snapshot cannot claim decoration");
        check(
            MachineVisualClient.decorationMask(false, working, 2, 0) == 0,
            "stale or OFF authority cannot claim decoration");
        for (int flags : new int[] { 0, 1, 2, 3, 4, 5, 6, 11 }) check(
            MachineVisualClient.decorationMask(true, snapshot(flags), 2, 0) == 0,
            "formation, motion and actual work are all required: " + flags);
        check(
            MachineVisualClient.decorationMask(true, working, 2, 0) == 3,
            "fresh formed working machine grants its groups");
        check(
            MachineVisualClient.decorationMask(true, working, 2, 1) == 2,
            "restoring group stays reserved and cannot be reclaimed");
        check(MachineVisualClient.decorationMask(true, working, 2, 3) == 0, "all restoring groups remain static");
        check(
            MachineVisualClient.decorationMask(true, snapshot(3), 2, 0) == 0,
            "authority stop revokes prior working mask");
        check(
            MachineVisualClient.decorationMask(true, working, 2, 0) == 3,
            "fresh work resumes after restoration acknowledgement");
        check(MachineVisualClient.decorationMask(true, working, 0, 0) == 0, "effect-only host has no motion claim");
        check(!MachineVisualClient.restorationAcknowledged(4, 3, false), "old upload cannot release restoration");
        check(!MachineVisualClient.restorationAcknowledged(4, 4, true), "hidden upload cannot release restoration");
        check(
            MachineVisualClient.restorationAcknowledged(4, 4, false),
            "current visible static upload releases restoration");
    }

    private static boolean admitted(int quality, int members, int groups, int heldMembers, int heldGroups) {
        return MachineVisualClient.selectAdmissions(
            MachineVisualClient.memberLimit(quality),
            MachineVisualClient.groupLimit(quality),
            heldMembers,
            heldGroups,
            new int[] { members },
            new int[] { groups },
            new int[] { members },
            new int[] { groups },
            new long[] { Long.MIN_VALUE },
            new long[] { 0 },
            new boolean[] { false },
            1)[0];
    }

    private static void budgetContracts() {
        MachineVisualDefinition turbine = MachineVisualDefinition.get("mega_steam_turbine_4");
        int members = 0;
        for (MachineVisualDefinition.Group group : turbine.groups) members += group.members.size();
        check(
            members == 2814 && turbine.groups.size() == 2,
            "budget uses the actual largest two-shaft turbine members");
        check(admitted(2, members, turbine.groups.size(), 0, 0), "STANDARD admits the complete largest turbine");
        check(
            !admitted(1, members, turbine.groups.size(), 0, 0),
            "LOW cannot discount decoration to exceed its member budget");
        check(!admitted(0, 4, 2, 0, 0), "OFF cannot admit even a small decoration");
        check(admitted(1, 30, 2, 98, 2), "exact complete-host member boundary fits");
        check(!admitted(1, 30, 2, 99, 2), "pending restoration retains its member reservation");
        check(!admitted(1, 4, 2, 0, 15), "independent group budget still limits small hosts");
    }

    private static void geometryContracts() throws Exception {
        JsonObject root;
        try (InputStream stream = resource("assets/gtsr/machinevisual/motions.json")) {
            root = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
        }
        int definitions = 0;
        for (JsonElement value : root.getAsJsonArray("definitions")) {
            String id = value.getAsJsonObject()
                .get("id")
                .getAsString();
            MachineVisualDefinition definition = MachineVisualDefinition.get(id);
            check(definition.groups.size() <= 8, "packaged group count fits the authority mask: " + id);
            for (ExtendedFacing facing : ExtendedFacing.values()) {
                MachineVisualClearance.Plan plan = MachineVisualClearance.plan(definition, facing);
                check(
                    plan == MachineVisualClearance.plan(definition, facing),
                    "same immutable orientation is reused: " + id);
                check(
                    plan.groups.length == definition.groups.size(),
                    "orientation retains every authorised group: " + id);
                check(Math.abs(plan.handedness) == 1, "rotation/reflection retains unit handedness: " + id);
                for (int index = 0; index < plan.groups.length; index++) {
                    MachineVisualDefinition.Group group = definition.groups.get(index);
                    MachineVisualClearance.GroupGeometry geometry = plan.groups[index];
                    boolean exact = geometry.members.length == group.members.size() * 3;
                    for (int i = 0; i < group.members.size(); i++) {
                        MachineVisualDefinition.Cell cell = group.members.get(i);
                        int[] expected = new int[3];
                        facing.getWorldOffset(new int[] { cell.x, -cell.y, cell.z }, expected);
                        for (int axis = 0; axis < 3; axis++) exact &= geometry.members[3 * i + axis] == expected[axis];
                    }
                    check(exact, "real member coordinates survive signed orientation: " + id);
                    double[] pivot = new double[3];
                    facing.getWorldOffset(new double[] { group.pivotX, -group.pivotY, group.pivotZ }, pivot);
                    check(sameVector(pivot, geometry.pivot), "real rotation pivot survives orientation: " + id);
                    double[] axis = new double[3];
                    boolean vertical = group.kind.equals("rotate_y") || group.axis.equals("y");
                    facing.getWorldOffset(
                        new double[] { group.axis.equals("x") && !vertical ? 1 : 0, vertical ? -1 : 0,
                            group.axis.equals("z") && !vertical ? 1 : 0 },
                        axis);
                    check(sameVector(axis, geometry.axis), "real motion axis survives orientation: " + id);
                }
            }
            definitions++;
        }
        check(definitions >= 10, "contract exercises packaged machine profiles including all turbine lengths");
    }

    private static void bytecodeContracts() throws Exception {
        for (String name : new String[] { "MachineVisualClearance", "MachineVisualClearance$Plan",
            "MachineVisualClearance$GroupGeometry" }) {
            ClassNode type = bytecode("com/miaokatze/gtsr/client/machinevisual/" + name + ".class");
            for (FieldNode field : type.fields) check(
                !field.name.toLowerCase()
                    .contains("sweep") && !field.name.equals("air") && !worldType(field.desc),
                "orientation has no swept volume or world state: " + name + "." + field.name);
            for (MethodNode method : type.methods) {
                check(
                    !method.name.toLowerCase()
                        .contains("sweep"),
                    "orientation never constructs a sweep");
                noWorldCalls(method, "orientation " + name);
            }
        }
        ClassNode client = bytecode("com/miaokatze/gtsr/client/machinevisual/MachineVisualClient.class");
        boolean calledPolicy = false, published = false;
        for (MethodNode method : client.methods) {
            if (method.name.equals("decorationMask") || method.name.equals("admitMotion"))
                noWorldCalls(method, "decoration policy " + method.name);
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction
                != null; instruction = instruction.getNext()) if (instruction instanceof MethodInsnNode call) {
                    if (method.name.equals("tick") && call.name.equals("decorationMask")) calledPolicy = true;
                    if (method.name.equals("admitMotion") && call.name.equals("publish")) published = true;
                    if (method.name.equals("admitMotion") || method.name.equals("tick")) check(
                        !call.owner.endsWith("MachineVisualClearance") && !call.name.equals("safeGroups"),
                        "production tick/admission does not revalidate decorative space");
                }
        }
        check(
            calledPolicy && published,
            "real tick calls the tested authority policy and budget admission publishes it");
    }

    private static boolean worldType(String type) {
        return type.contains("net/minecraft/world/") || type.contains("net/minecraft/tileentity/")
            || type.contains("IBlockAccess");
    }

    private static boolean sameVector(double[] expected, double[] actual) {
        if (expected.length != actual.length) return false;
        for (int i = 0; i < expected.length; i++) if (Math.abs(expected[i] - actual[i]) > 1e-12) return false;
        return true;
    }

    private static void noWorldCalls(MethodNode method, String context) {
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction
            != null; instruction = instruction.getNext())
            if (instruction instanceof MethodInsnNode call) check(
                !worldType(call.owner) && !call.name.equals("getWorld")
                    && !call.name.equals("getBlock")
                    && !call.name.equals("getBlockMetadata")
                    && !call.name.equals("getLightBrightnessForSkyBlocks")
                    && !call.name.equals("isAirBlock")
                    && !call.name.equals("getTileEntity")
                    && !call.name.equals("getChunkFromChunkCoords"),
                context + " makes no world/space/light query: " + call.owner + "." + call.name);
    }

    private static ClassNode bytecode(String name) throws Exception {
        try (InputStream stream = resource(name)) {
            ClassNode result = new ClassNode();
            new ClassReader(stream).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }

    private static InputStream resource(String name) {
        InputStream stream = MachineDecorationContracts.class.getClassLoader()
            .getResourceAsStream(name);
        if (stream == null) throw new AssertionError("Missing actual classpath resource " + name);
        return stream;
    }
}
