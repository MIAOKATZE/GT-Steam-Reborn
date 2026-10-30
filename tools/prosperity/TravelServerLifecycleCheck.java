import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.miaokatze.gtsr.common.dimension.prosperity.travel.SpacetimeTravel;

/** Production static server-state lifecycle seam; does not simulate a dimension transfer or item debit. */
public final class TravelServerLifecycleCheck {
    private static Object state(String name) throws Exception {
        Field field = SpacetimeTravel.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Map<UUID, Long> cooldown = (Map<UUID, Long>) state("COOLDOWN");
        Set<UUID> transferring = (Set<UUID>) state("TRANSFERRING");
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        cooldown.put(id, 1000000L + 600);
        transferring.add(id);
        Method stop;
        try {
            stop = SpacetimeTravel.class.getMethod("resetServerState");
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("No server-state reset; old absolute recall deadline survives a replacement world", missing);
        }
        stop.invoke(null);
        for (String dependent : new String[] { "travel.AnchorIntent", "travel.BeaconNavigationNetwork", "lore.NavigatorNetwork",
                "altar.SpacetimeAltarStory", "altar.SpacetimeAltarStoryNetwork", "altar.SpacetimeAltarNavigationSearch" }) {
            Class<?> type = Class.forName("com.miaokatze.gtsr.common.dimension.prosperity." + dependent);
            if (type.getField("resets").getInt(null) != 1)
                throw new AssertionError("Server stop must reset dependent transient service: " + dependent);
        }
        if (!cooldown.isEmpty() || !transferring.isEmpty())
            throw new AssertionError("Stopped server must release recall deadlines and in-progress players");
        stop.invoke(null);
        if (!cooldown.isEmpty() || !transferring.isEmpty()) throw new AssertionError("Stop reset must be idempotent");
        cooldown.put(id, 100L + 600);
        if (cooldown.get(id) != 700L) throw new AssertionError("Replacement world starts its own recall clock");
        stop.invoke(null);
        System.out.println("PASS TravelServerLifecycleCheck: reset/empty/idempotent/new-world clock; API fixtures, no travel/game startup");
    }
}
