import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterData;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterSite;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Actual ledger/catalog/vanilla NBT; malformed progress is preserved, not replayed as a fresh site. */
public final class RemasterSavedStateValidationCheck {
    private static int checks, failures;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) { failures++; System.err.println("FAIL " + message); }
    }

    private static NBTTagCompound root(NBTTagCompound... records) {
        NBTTagList rows = new NBTTagList();
        for (NBTTagCompound record : records) rows.appendTag(record);
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("sites", rows);
        return root;
    }

    private static boolean contains(NBTTagCompound saved, NBTTagCompound row) {
        NBTTagList rows = saved.getTagList("sites", 10);
        for (int i = 0; i < rows.tagCount(); i++) if (row.equals(rows.getCompoundTagAt(i))) return true;
        return false;
    }

    private static void frozen(String name, RemasterSite site, NBTTagCompound malformed, boolean canonical) {
        RemasterData data = new RemasterData();
        data.readFromNBT(root(malformed));
        check(data.site(site.id()) == null, name + ": not a runtime owner");
        try {
            check(data.generatedOwners().isEmpty(), name + ": not a generated owner");
        } catch (RuntimeException failure) {
            check(false, name + ": generated owner enumeration threw " + failure.getClass().getSimpleName());
        }
        check(data.inChunk(site.x >> 4, site.z >> 4).isEmpty(), name + ": not indexed");
        if (canonical) {
            check(!data.allowsSavedPlacement(site), name + ": cannot replan over frozen progress");
            data.register(site);
            check(data.site(site.id()) == null, name + ": registration cannot replace frozen progress");
        }
        for (int cycle = 0; cycle < 3; cycle++) {
            NBTTagCompound saved = new NBTTagCompound();
            data.writeToNBT(saved);
            check(contains(saved, malformed), name + ": exact opaque NBT roundtrip " + cycle);
            data.readFromNBT(saved);
        }
    }

    public static void main(String[] args) {
        RemasterSite site = new RemasterSite("fallen_foundry", 0, 17L, 0, 64, 0);
        NBTTagCompound wrongState = site.save();
        wrongState.setString("state", "unreadable progress must not become a fresh reward ledger");
        frozen("string state", site, wrongState, true);
        NBTTagCompound wrongVariant = site.save();
        wrongVariant.setString("variant", "not-zero");
        frozen("string variant", site, wrongVariant, false);
        NBTTagCompound missingX = site.save();
        missingX.removeTag("x");
        frozen("missing coordinate", site, missingX, false);
        NBTTagCompound wrongSeed = site.save();
        wrongSeed.setInteger("seed", 17);
        frozen("wrong seed type", site, wrongSeed, false);
        NBTTagCompound wrongY = site.save();
        wrongY.setInteger("y", Integer.MAX_VALUE);
        frozen("invalid height", site, wrongY, true);
        RemasterSite outside = new RemasterSite("fallen_foundry", 0, 17L, Integer.MAX_VALUE, 64, 0);
        frozen("overflowing anchor", outside, outside.save(), true);

        // A healthy duplicate must not defeat a malformed same-identity reservation in either order.
        for (boolean badFirst : new boolean[] { false, true }) {
            NBTTagCompound healthy = site.save();
            RemasterData duplicate = new RemasterData();
            duplicate.readFromNBT(badFirst ? root(wrongState, healthy) : root(healthy, wrongState));
            check(duplicate.site(site.id()) == null, "ambiguous identity stays frozen, badFirst=" + badFirst);
            NBTTagCompound written = new NBTTagCompound();
            duplicate.writeToNBT(written);
            check(contains(written, wrongState) && contains(written, healthy), "both ambiguous records retained");
        }

        // New, genuinely absent state remains writable and linked to the ledger.
        RemasterData normal = new RemasterData();
        normal.readFromNBT(root(site.save()));
        normal.flag(site.id(), "claimed:reward", true);
        NBTTagCompound saved = new NBTTagCompound();
        normal.writeToNBT(saved);
        normal.readFromNBT(saved);
        check(normal.flag(site.id(), "claimed:reward"), "valid newly created state survives reload");
        check(normal.allowsSavedPlacement(site), "valid saved identity remains stable");
        normal.readFromNBT(root(wrongState));
        normal.readFromNBT(new NBTTagCompound());
        normal.register(site);
        check(normal.site(site.id()) != null, "replacement load clears old quarantine reservations");

        check(site.intersects(0, 0), "ordinary footprint intersects its owner chunk");
        check(!site.intersects(268435456, 0), "positive chunk shift overflow cannot alias origin");
        check(!site.intersects(-268435456, 0), "negative chunk shift overflow cannot alias origin");
        check(!site.intersects(0, 268435456), "Z chunk shift overflow cannot alias origin");
        RemasterSite negative = new RemasterSite("fallen_foundry", 0, 17L, -128, 64, -128);
        check(negative.intersects(-8, -8), "negative-coordinate owner chunk is preserved");
        NBTTagCompound forgedOwner = site.save();
        NBTTagCompound state = new NBTTagCompound(); state.setBoolean("geom:268435456:0", true);
        forgedOwner.setTag("state", state);
        normal.readFromNBT(root(forgedOwner));
        check(normal.generatedOwners().isEmpty(), "wrapped saved chunk cannot authorize generated geometry");
        java.util.Random random = new java.util.Random(1789L);
        for (int i = 0; i < 1024; i++) {
            RemasterSite sample = new RemasterSite("fallen_foundry", 0, 17L,
                random.nextInt(58000001) - 29000000, 64, random.nextInt(58000001) - 29000000);
            int cx = (sample.x >> 4) + random.nextInt(40) - 10;
            int cz = (sample.z >> 4) + random.nextInt(40) - 10;
            boolean old = (cx << 4) <= sample.maxX() && (cx << 4) + 15 >= sample.minX()
                && (cz << 4) <= sample.maxZ() && (cz << 4) + 15 >= sample.minZ();
            check(sample.intersects(cx, cz) == old, "legal-world chunk footprint parity " + i);
        }
        if (failures != 0) throw new AssertionError(failures + "/" + checks + " saved-state assertions failed");
        System.out.println("PASS RemasterSavedStateValidationCheck: " + checks + " native-NBT assertions");
    }
}
