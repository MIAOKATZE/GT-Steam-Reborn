import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.storage.WorldInfo;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.shattered.WorldProviderShatteredLands;

/** Runs actual spawn-list and client-fog consumer bodies against native vanilla world objects. */
public final class BiomeAuthorityConsumerCheck {
    private static int checks;
    private static Object fixture(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = BiomeAuthorityWorldScopeCheck.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
    private static World world(GTSRWorldChunkManager manager, boolean client, long seed) throws Exception {
        World world = (World) fixture("world", new Class<?>[] { GTSRWorldChunkManager.class, int.class, boolean.class }, manager, 79, client);
        Field info = World.class.getDeclaredField("worldInfo");
        info.setAccessible(true);
        info.set(world, new WorldInfo(new WorldSettings(seed, WorldSettings.GameType.SURVIVAL, false, false, WorldType.DEFAULT), "authority-fixture"));
        return world;
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static String spawn(List<BiomeGenBase.SpawnListEntry> table) {
        if (table == null) return "null";
        StringBuilder out = new StringBuilder();
        for (BiomeGenBase.SpawnListEntry entry : table) out.append(entry.entityClass.getName()).append(':')
            .append(entry.itemWeight).append(':').append(entry.minGroupCount).append(':').append(entry.maxGroupCount).append(';');
        return out.toString();
    }
    public static void main(String[] args) throws Exception {
        GTSRDimensionDef def = (GTSRDimensionDef) fixture("def", new Class<?>[] { String.class, int.class }, GTSRBiomeAuthority.DIM_KEY_SHATTERED, 79);
        // A standalone client must have a source before any server world exists.
        GTSRWorldChunkManager client = new GTSRWorldChunkManager(0L, def);
        World clientWorld = world(client, true, 0L);
        WorldProviderShatteredLands fog = new WorldProviderShatteredLands();
        Field owner = WorldProvider.class.getDeclaredField("worldObj");
        owner.setAccessible(true);
        owner.set(fog, clientWorld);
        Method fogIdentity = WorldProviderShatteredLands.class.getDeclaredMethod("biomeIdentityAt", int.class, int.class);
        fogIdentity.setAccessible(true);
        check(fogIdentity.invoke(fog, 0, 0) != null, "standalone client has no fog source");
        GTSRWorldChunkManager server = new GTSRWorldChunkManager(19L, def);
        World serverWorld = world(server, false, 19L);
        GTSRChunkProviderBase provider = new GTSRChunkProviderBase(serverWorld, 19L);
        List<String> clean = new ArrayList<>();
        for (int i = 0; i < 256; i++) clean.add(spawn(provider.getPossibleCreatures(EnumCreatureType.creature, i * 257 - 32768, 64, i * 251 - 32768)));
        new GTSRWorldChunkManager(0L, def);
        for (int i = 0; i < 256; i++) {
            int x = i * 257 - 32768, z = i * 251 - 32768;
            check(clean.get(i).equals(spawn(provider.getPossibleCreatures(EnumCreatureType.creature, x, 64, z))), "client creation changed server spawn table");
            check(fogIdentity.invoke(fog, x, z) == client.biomeAuthority().ordinalAt(x, z).biomeId, "fog samples another world's source");
            check(clean.get(i).equals(spawn(server.biomeAt(x >> 4, z >> 4).getSpawnableList(EnumCreatureType.creature))), "spawn consumer lost correct-world table");
        }
        GTSRDimensionDef shortDef = (GTSRDimensionDef) fixture("def", new Class<?>[] { String.class, int.class }, GTSRBiomeAuthority.DIM_KEY_PROSPERITY, 78);
        new GTSRWorldChunkManager(19L, shortDef);
        Method surface = GTSRChunkProviderBase.class.getDeclaredMethod("columnSurfaceable", String.class, BiomeGenBase.class);
        surface.setAccessible(true);
        check((Boolean) surface.invoke(null, shortDef.getKey(), BiomeGenBase.plains), "scoped registration rejected an allocated SHORT surface");
        check(!(Boolean) surface.invoke(null, shortDef.getKey(), BiomeGenBase.ocean), "absence of legacy binding disabled SHORT surface guard");
        GTSRDimensionDef emptyDef = (GTSRDimensionDef) fixture("def", new Class<?>[] { String.class, int.class }, "empty-biome-fixture", 80);
        new GTSRWorldChunkManager(19L, emptyDef);
        check(!(Boolean) surface.invoke(null, emptyDef.getKey(), BiomeGenBase.plains), "registered EMPTY surface guard is disabled");
        System.out.println("PASS BiomeAuthorityConsumerCheck: " + checks + " actual spawn/fog/surface checks; native worlds, no render or Forge launch");
    }
}
