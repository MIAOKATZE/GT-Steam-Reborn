package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import java.lang.reflect.Field;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Actual ledger deserialization and native Minecraft NBT; no world/gameplay is launched. */
public final class RuinsLedgerBoundsCheck {

    private static int checks;

    public static void main(String[] args) throws Exception {
        NBTTagCompound oversized = record(0, 2, 0, 0);
        oversized.setInteger("width", 4096);
        oversized.setInteger("depth", 4096);
        RuinsEncounterData ledger = read(oversized);
        check(cells(ledger) == 2, "saved dimensions cannot amplify authored 288x192 index");
        check(ledger.existingSitesNear(0, 0, 0).size() == 1, "valid saved anchor remains discoverable");
        check(ledger.dead("saved", 3), "accepted death is preserved");

        oversized.setInteger("width", -1);
        oversized.setInteger("depth", Integer.MAX_VALUE);
        check(cells(read(oversized)) == 2, "negative/huge dimensions use immutable authored footprint");
        for (int kind = 0; kind < RuinSite.NAMES.length; kind++) {
            for (int layout = 1; layout <= 2; layout++) {
                NBTTagCompound saved = record(kind, layout, -1, -1);
                ledger = read(saved);
                RuinSite site = ledger.site("saved");
                check(site != null && site.width == RuinSite.widthFor(kind, layout), "supported kind/layout remains valid");
                check(cells(ledger) <= 6, "supported negative-coordinate footprint stays bounded");
            }
        }
        for (NBTTagCompound opaque : new NBTTagCompound[] {
            record(-1, 2, 0, 0), record(27, 2, 0, 0), record(0, 3, 0, 0),
            record(0, 2, Integer.MAX_VALUE, 0), record(0, 2, 0, Integer.MIN_VALUE)
        }) {
            ledger = read(opaque);
            check(ledger.site("saved") == null && cells(ledger) == 0, "unknown/out-of-world record is not an active site");
            check(ledger.dead("saved", 3), "opaque record retains completion ledger");
            NBTTagCompound out = new NBTTagCompound();
            ledger.writeToNBT(out);
            check(out.getTagList("sites", 10).getCompoundTagAt(0).getCompoundTag("record").equals(opaque),
                "opaque record round-trips without rewriting player progress");
        }
        ledger = read(record(0, 2, 29999999, -30000000));
        check(ledger.site("saved") != null && cells(ledger) <= 6, "legal world-edge anchors remain bounded");
        System.out.println("PASS: " + checks + " native NBT ruins ledger bounds checks");
    }

    private static NBTTagCompound record(int kind, int layout, int x, int z) {
        NBTTagCompound record = new NBTTagCompound();
        record.setInteger("kind", kind);
        record.setInteger("layoutVersion", layout);
        record.setInteger("x", x);
        record.setInteger("z", z);
        record.setInteger("y", 64);
        record.setInteger("width", RuinSite.widthFor(kind, layout));
        record.setInteger("depth", RuinSite.depthFor(kind, layout));
        record.setBoolean("dead:3", true);
        return record;
    }

    private static RuinsEncounterData read(NBTTagCompound record) {
        NBTTagCompound entry = new NBTTagCompound(), root = new NBTTagCompound();
        entry.setString("id", "saved");
        entry.setTag("record", record);
        NBTTagList list = new NBTTagList();
        list.appendTag(entry);
        root.setTag("sites", list);
        RuinsEncounterData ledger = new RuinsEncounterData();
        ledger.readFromNBT(root);
        return ledger;
    }

    private static int cells(RuinsEncounterData ledger) throws Exception {
        Field field = RuinsEncounterData.class.getDeclaredField("spatial");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(ledger)).size();
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
        checks++;
    }
}
