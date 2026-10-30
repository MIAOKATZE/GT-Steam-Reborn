import java.lang.reflect.*;
import java.util.Map;
import com.miaokatze.gtsr.common.dimension.prosperity.altar.SpacetimeAltarNavigationSearch;

/** Actual search-cache stop seam; no target/disk probing or live world-generation claim. */
public final class AltarSearchLifecycleCheck {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void main(String[] args) throws Exception {
        Field f=SpacetimeAltarNavigationSearch.class.getDeclaredField("SEARCHES");f.setAccessible(true);
        Map cache=(Map)f.get(null);cache.put(null,null);
        Method stop;
        try { stop=SpacetimeAltarNavigationSearch.class.getMethod("resetServerState"); }
        catch(NoSuchMethodException missing){throw new AssertionError("Server stop must release search cache",missing);}
        stop.invoke(null);if(!cache.isEmpty())throw new AssertionError("Stopped search cache retained entries");
        stop.invoke(null);if(!cache.isEmpty())throw new AssertionError("Search reset is not idempotent");
        cache.put(null,null);stop.invoke(null);if(!cache.isEmpty())throw new AssertionError("Replacement search cache not cleared");
        System.out.println("PASS AltarSearchLifecycleCheck: three actual reset seam checks; no search probe execution");
    }
}
