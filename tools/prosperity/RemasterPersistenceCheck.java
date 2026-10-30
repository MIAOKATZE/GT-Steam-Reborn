import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterData;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterSite;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Roundtrip production ledger with actual Minecraft NBT and the packaged catalog; no game startup. */
public final class RemasterPersistenceCheck {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static NBTTagCompound saved(RemasterSite site, String witness) {
        NBTTagCompound row = site.save();
        NBTTagCompound state = new NBTTagCompound();
        state.setBoolean("geom:0:0", true);
        state.setBoolean("claimed:reward", true);
        row.setTag("state", state);
        row.setString("futureSchemaWitness", witness);
        return row;
    }

    private static boolean contains(NBTTagList rows, NBTTagCompound expected) {
        for (int i = 0; i < rows.tagCount(); i++) if (expected.equals(rows.getCompoundTagAt(i))) return true;
        return false;
    }

    public static void main(String[] args) {
        RemasterSite active = new RemasterSite("fallen_foundry", 0, 17, 0, 64, 0);
        RemasterSite nonstandard = new RemasterSite("fallen_foundry", 1, 17, 0, 64, 0);
        RemasterSite paused = new RemasterSite("paused_missing_prefab", 0, 17, 0, 64, 0);
        RemasterSite oldLayout = new RemasterSite("fallen_foundry", 0, 17, 0, 64, 0, "legacy-layout");
        NBTTagCompound[] originals = { saved(active, "active"), saved(nonstandard, "variant"),
            saved(paused, "paused"), saved(oldLayout, "layout") };
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList rows = new NBTTagList();
        for (NBTTagCompound row : originals) rows.appendTag(row);
        root.setTag("sites", rows);
        RemasterData data = new RemasterData();
        data.readFromNBT(root);
        check(data.site(active.id()) != null, "active site remains available");
        check(data.site(nonstandard.id()) == null && data.site(paused.id()) == null && data.site(oldLayout.id()) == null,
            "frozen sites cannot authorize runtime state");
        check(data.generatedOwners().size() == 1, "frozen sites do not become generated owners");
        check(data.inChunk(0, 0).size() == 1, "frozen sites never reach descriptor/chunk indexing");
        for (int cycle = 0; cycle < 3; cycle++) {
            NBTTagCompound output = new NBTTagCompound();
            data.writeToNBT(output);
            NBTTagList written = output.getTagList("sites", 10);
            check(written.tagCount() == originals.length, "save must retain frozen records, cycle " + cycle);
            for (NBTTagCompound row : originals) check(contains(written, row), "exact NBT retained, cycle " + cycle);
            data.readFromNBT(output);
            check(data.generatedOwners().size() == 1, "reloading does not activate frozen records");
        }
        data.readFromNBT(new NBTTagCompound());
        NBTTagCompound empty = new NBTTagCompound();
        data.writeToNBT(empty);
        check(empty.getTagList("sites", 10).tagCount() == 0, "replacing ledger clears old frozen records");
        System.out.println("PASS RemasterPersistenceCheck: " + checks + " assertions; no game startup");
    }
}
