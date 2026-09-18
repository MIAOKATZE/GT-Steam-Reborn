import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;

/** dim1 崩溃修复回归：damageTier/rotationOf 对正负 seed 均须落在合法区间。 */
public class TierRangeCheck {
    public static void main(String[] args) {
        long seed = -1378031385801002063L; // 用户崩溃世界 seed
        int bad = 0, tiers = 0;
        for (long i = 0; i < 2_000_000L; i++) {
            long ps = seed ^ (i * 0x9E3779B97F4A7C15L);
            int t = CityVariants.damageTier(ps);
            if (t < 0 || t > 2) bad++;
            tiers |= (1 << t);
        }
        for (long ps : new long[] { Long.MIN_VALUE, -1L, Long.MAX_VALUE, 0L }) {
            if (CityVariants.damageTier(ps) < 0 || CityVariants.damageTier(ps) > 2) bad++;
        }
        System.out.println("TIER_CHECK bad=" + bad + " coverageMask=" + Integer.toBinaryString(tiers)
            + " (期望 111=三档全覆盖) => " + (bad == 0 && tiers == 0b111 ? "PASS" : "FAIL"));
    }
}
