import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Historical SHA-policy fixture retained for evidence. The active probe now delegates to firebox_resource_contract.py. */
public final class ForegroundGuardContract {

    private static boolean flag(String name) throws Exception {
        Field field = ForegroundGuardUnderTest.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(null);
    }

    private static void check(String id, boolean condition) {
        if (!condition) throw new AssertionError(id);
        System.out.println("PASS\t" + id);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("foreground.png bronze.png steel.png");
        byte[] foreground = Files.readAllBytes(Paths.get(args[0]));
        byte[] bronze = Files.readAllBytes(Paths.get(args[1]));
        byte[] steel = Files.readAllBytes(Paths.get(args[2]));
        ResourceLocation fg = new ResourceLocation("gtsr", "workeffects/firebox_grate.png");
        ResourceLocation b = new ResourceLocation("gregtech", "textures/blocks/iconsets/MACHINE_CASING_FIREBOX_BRONZE.png");
        ResourceLocation s = new ResourceLocation("gregtech", "textures/blocks/iconsets/MACHINE_CASING_FIREBOX_STEEL.png");
        ResourceFixture resources = new ResourceFixture();
        resources.bytes.put(fg, foreground);
        resources.bytes.put(b, bronze);
        resources.bytes.put(s, steel);
        ForegroundGuardUnderTest policy = new ForegroundGuardUnderTest();
        policy.onResourceManagerReload(resources);
        check("good_foreground_hash", flag("foregroundAvailable"));
        check("good_bronze_hash", flag("bronzeCompatible"));
        check("good_steel_hash", flag("steelCompatible"));
        ForegroundGuardUnderTest.renderEntryProbe(true);
        check("working_guard_enters", ForegroundGuardUnderTest.renderEntries == 1);
        ForegroundGuardUnderTest.renderEntryProbe(false);
        check("idle_guard_stops", ForegroundGuardUnderTest.renderEntries == 1);
        byte[] corrupt = foreground.clone();
        corrupt[0] ^= 1;
        resources.bytes.put(fg, corrupt);
        policy.onResourceManagerReload(resources);
        check("bitflip_foreground_rejected", !flag("foregroundAvailable"));
        check("bitflip_preserves_base_flags", flag("bronzeCompatible") && flag("steelCompatible"));
        ForegroundGuardUnderTest.renderEntryProbe(true);
        check("bitflip_guard_stops", ForegroundGuardUnderTest.renderEntries == 1);
        int reads = resources.reads;
        for (int i = 0; i < 100; i++) ForegroundGuardUnderTest.renderEntryProbe(true);
        check("leading_guard_no_resource_reads", resources.reads == reads && ForegroundGuardUnderTest.renderEntries == 1);
        resources.bytes.remove(fg);
        policy.onResourceManagerReload(resources);
        check("missing_foreground_rejected", !flag("foregroundAvailable"));
        resources.bytes.put(fg, new byte[0]);
        policy.onResourceManagerReload(resources);
        check("empty_foreground_rejected", !flag("foregroundAvailable"));
        resources.bytes.put(fg, foreground);
        byte[] corruptBronze = bronze.clone();
        corruptBronze[0] ^= 1;
        resources.bytes.put(b, corruptBronze);
        policy.onResourceManagerReload(resources);
        check("bad_bronze_only_disables_bronze", !flag("bronzeCompatible") && flag("steelCompatible") && flag("foregroundAvailable"));
        resources.bytes.put(b, bronze);
        policy.onResourceManagerReload(resources);
        ForegroundGuardUnderTest.renderEntryProbe(true);
        check("reload_restores_good_foreground", flag("foregroundAvailable") && ForegroundGuardUnderTest.renderEntries == 2);
        check("default_foreground_unchanged", Arrays.equals(foreground, Files.readAllBytes(Paths.get(args[0]))));
    }
}

final class ResourceLocation {
    final String domain, path;
    ResourceLocation(String domain, String path) { this.domain = domain; this.path = path; }
    @Override public int hashCode() { return domain.hashCode() * 31 + path.hashCode(); }
    @Override public boolean equals(Object other) {
        if (!(other instanceof ResourceLocation)) return false;
        ResourceLocation value = (ResourceLocation) other;
        return domain.equals(value.domain) && path.equals(value.path);
    }
}

interface IResourceManager { Resource getResource(ResourceLocation location) throws IOException; }

final class Resource {
    private final byte[] bytes;
    Resource(byte[] bytes) { this.bytes = bytes; }
    InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
}

final class ResourceFixture implements IResourceManager {
    final Map<ResourceLocation, byte[]> bytes = new HashMap<>();
    int reads;
    public Resource getResource(ResourceLocation location) throws IOException {
        reads++;
        if (!bytes.containsKey(location)) throw new IOException("Synthetic missing resource");
        return new Resource(bytes.get(location));
    }
}
