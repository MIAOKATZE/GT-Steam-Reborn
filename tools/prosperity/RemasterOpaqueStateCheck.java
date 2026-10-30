import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterData;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterSite;
import net.minecraft.nbt.*;

/** Native NBT: malformed containers/progress must survive intact, never authorizing a fresh replay. */
public final class RemasterOpaqueStateCheck {
    private static int checks, failures;
    private static final RemasterSite SITE = new RemasterSite("fallen_foundry", 0, 17L, 0, 64, 0);
    private static void check(boolean value, String message) {
        checks++; if (!value) { failures++; System.err.println("FAIL " + message); }
    }
    private static NBTTagCompound root(NBTTagCompound... records) {
        NBTTagCompound root = new NBTTagCompound(); NBTTagList list = new NBTTagList();
        for (NBTTagCompound record : records) list.appendTag(record);
        root.setTag("sites", list); return root;
    }
    private static boolean contains(NBTTagCompound saved, NBTTagCompound record) {
        NBTTagList list = saved.getTagList("sites", 10);
        for (int i = 0; i < list.tagCount(); i++) if (record.equals(list.getCompoundTagAt(i))) return true;
        return false;
    }
    private static void frozen(String label, NBTTagCompound record) {
        RemasterData data = new RemasterData(); data.readFromNBT(root(record));
        check(data.site(SITE.id()) == null, label + " not admitted");
        check(data.generatedOwners().isEmpty() && data.inChunk(0, 0).isEmpty(), label + " not indexed");
        check(!data.allowsSavedPlacement(SITE), label + " cannot replan");
        data.register(SITE); check(data.site(SITE.id()) == null, label + " cannot replace");
        for (int i = 0; i < 3; i++) {
            NBTTagCompound saved = new NBTTagCompound(); data.writeToNBT(saved);
            check(contains(saved, record), label + " opaque roundtrip " + i);
            data.readFromNBT(saved);
        }
    }
    private static NBTTagCompound record(String key, NBTBase value) {
        NBTTagCompound record = SITE.save(), state = new NBTTagCompound(); state.setTag(key, value);
        record.setTag("state", state); return record;
    }
    public static void main(String[] args) {
        NBTTagList strings = new NBTTagList(); strings.appendTag(new NBTTagString("saved progress"));
        NBTTagList nested = new NBTTagList(); nested.appendTag(strings.copy());
        for (NBTBase malformed : new NBTBase[] { new NBTTagString("opaque"), new NBTTagInt(4), strings, nested }) {
            RemasterData data = new RemasterData(); NBTTagCompound input = new NBTTagCompound(); input.setTag("sites", malformed);
            boolean loaded = true;
            try { data.readFromNBT(input); } catch (RuntimeException failure) { loaded = false; }
            check(loaded, "malformed sites container does not crash: " + malformed.getId());
            check(!data.allowsSavedPlacement(SITE), "unknown top-level progress blocks seed replay");
            data.register(SITE); check(data.site(SITE.id()) == null, "unknown top-level progress blocks registration");
            for (int i = 0; i < 3; i++) {
                NBTTagCompound saved = new NBTTagCompound(); data.writeToNBT(saved);
                check(malformed.equals(saved.getTag("sites")), "malformed sites retained exactly " + i);
                try { data.readFromNBT(saved); } catch (RuntimeException failure) { break; }
            }
            data.readFromNBT(new NBTTagCompound()); data.register(SITE);
            check(data.site(SITE.id()) != null, "legitimate replacement load clears opaque-container quarantine");
        }
        frozen("string spawner", record("spawner:cage", new NBTTagString("spent")));
        for (String key : new String[] {"count", "serial", "deadCount", "contract", "pendingLedgerVersion", "next", "unsealAt", "unlockAt", "pulseAt"}) {
            NBTTagCompound nestedState = new NBTTagCompound(); nestedState.setString(key, "unreadable");
            frozen("spawner " + key, record("spawner:cage", nestedState));
        }
        for (String key : new String[] {"claimed:cache", "geom:0:0", "entity:0", "dead:guard", "spawner-admitted:cage:1", "spawner-dead:cage:1", "spawner-admitted:group:cage:1", "spawner-dead:group:cage:1", "node:cage", "sealed:cage", "destroyed:cage", "puzzle:p", "witness:p", "story-read:p", "boss-dead", "boss-dead:b", "mission-boss-ready"})
            frozen("wrong flag " + key, record(key, new NBTTagString("true")));
        for (String key : new String[] {"legacyAnchorX", "legacyAnchorY", "legacyAnchorZ", "industrialBossStage", "industrialRevivalTicks"})
            frozen("wrong integer " + key, record(key, new NBTTagString("64")));
        frozen("wrong boss health", record("industrialBossHealth", new NBTTagString("100")));
        frozen("wrong legacy owner", record("legacyEncounter", new NBTTagInt(1)));
        for (boolean spentFirst : new boolean[] { false, true }) {
            NBTTagCompound spent = record("claimed:cache", new NBTTagByte((byte) 1));
            NBTTagCompound fresh = SITE.save();
            RemasterData duplicate = new RemasterData();
            duplicate.readFromNBT(spentFirst ? root(spent, fresh) : root(fresh, spent));
            check(duplicate.site(SITE.id()) == null, "conflicting duplicate cannot choose unclaimed progress");
            check(!duplicate.allowsSavedPlacement(SITE), "duplicate identity blocks replay in either order");
            duplicate.register(SITE);
            check(duplicate.site(SITE.id()) == null, "duplicate cannot be replaced by planned site");
            for (int i = 0; i < 3; i++) {
                NBTTagCompound saved = new NBTTagCompound(); duplicate.writeToNBT(saved);
                check(contains(saved, spent) && contains(saved, fresh), "both duplicate ledgers preserved " + i);
                check(saved.getTagList("sites", 10).tagCount() == 2, "duplicate count stable " + i);
                duplicate.readFromNBT(saved);
                check(duplicate.site(SITE.id()) == null, "duplicates stay quarantined after reload " + i);
            }
        }
        // Do not impose a lifecycle TTL or drop outstanding identities during offline/reload.
        NBTTagCompound spawner = new NBTTagCompound(); spawner.setInteger("serial", 54321); spawner.setInteger("deadCount", 54320);
        spawner.setInteger("count", 8); spawner.setInteger("contract", 1); spawner.setInteger("pendingLedgerVersion", 1);
        spawner.setLong("next", 5); spawner.setLong("unlockAt", -40);
        NBTTagCompound legitimate = record("spawner:cage", spawner);
        NBTTagCompound state = legitimate.getCompoundTag("state");
        state.setBoolean("spawner-admitted:cage:54320", true); state.setBoolean("claimed:cache", true);
        state.setTag("extension:opaque", strings.copy()); state.setString("legacyEncounter", "legacy:123");
        for (String prefix : new String[] { "spawner-admitted:", "spawner-dead:" })
            for (String suffix : new String[] { "opaque", "", "-1", "+1", "01", " 1", "1 ", "2147483648" })
                state.setString(prefix + "cage:" + suffix, "future marker");
        RemasterData data = new RemasterData();
        for (int i = 0; i < 4; i++) {
            data.readFromNBT(root(legitimate)); check(data.site(SITE.id()) != null, "valid pending ledger admitted " + i);
            check(data.flag(SITE.id(), "spawner-admitted:cage:54320"), "unloaded survivor kept " + i);
            for (String prefix : new String[] { "spawner-admitted:", "spawner-dead:" })
                for (String suffix : new String[] { "opaque", "", "-1", "+1", "01", " 1", "1 ", "2147483648" })
                    check("future marker".equals(data.state(SITE.id()).getString(prefix + "cage:" + suffix)),
                        "noncanonical spawner marker stays opaque " + prefix + suffix + " cycle " + i);
            NBTTagCompound saved = new NBTTagCompound(); data.writeToNBT(saved);
            check(contains(saved, legitimate), "complete valid/unknown nested state retained " + i);
        }
        data.readFromNBT(root(SITE.save())); check(data.site(SITE.id()) != null, "absent initial progress remains legal");
        NBTTagCompound empty = new NBTTagCompound(); empty.setTag("sites", new NBTTagList());
        data.readFromNBT(empty); data.register(SITE); check(data.site(SITE.id()) != null, "empty new-world list remains legal");
        if (failures != 0) throw new AssertionError(failures + "/" + checks + " opaque-state assertions failed");
        System.out.println("PASS RemasterOpaqueStateCheck: " + checks + " native NBT assertions");
    }
}
