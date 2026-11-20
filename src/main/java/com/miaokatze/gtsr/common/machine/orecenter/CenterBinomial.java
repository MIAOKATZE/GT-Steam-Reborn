package com.miaokatze.gtsr.common.machine.orecenter;

import java.util.Random;

/** Binomial sampling without an iteration for each successful item in a large lot. */
final class CenterBinomial {

    private CenterBinomial() {}

    static int sample(Random random, int trials, double probability) {
        if (trials == 0 || probability <= 0) return 0;
        if (probability >= 1) return trials;
        if (probability > .5) return trials - sample(random, trials, 1 - probability);
        if (trials * probability < 32) return geometric(random, trials, probability);

        // Devroye, X.4.5: condition on a uniform order statistic and sample the remaining interval.
        int rank = (int) ((trials + 1.0) * probability);
        double left = gamma(random, rank), right = gamma(random, trials + 1 - rank);
        double boundary = left / (left + right);
        if (boundary < probability)
            return rank + sample(random, trials - rank, (probability - boundary) / (1 - boundary));
        return sample(random, rank - 1, probability / boundary);
    }

    static int geometric(Random random, int trials, double probability) {
        double logMiss = Math.log1p(-probability);
        int hits = 0;
        long position = -1;
        while (position < trials) {
            double gap = 1 + Math.floor(Math.log1p(-random.nextDouble()) / logMiss);
            if (gap >= trials - position) break;
            position += (long) gap;
            hits++;
        }
        return hits;
    }

    /** Marsaglia and Tsang's gamma rejection sampler for shape >= 1. */
    private static double gamma(Random random, int shape) {
        double scale = shape - 1.0 / 3, factor = 1 / Math.sqrt(9 * scale);
        while (true) {
            double normal = random.nextGaussian(), base = 1 + factor * normal;
            if (base <= 0) continue;
            double cube = base * base * base, squared = normal * normal;
            double uniform = random.nextDouble();
            if (uniform < 1 - .0331 * squared * squared
                || Math.log(uniform) < .5 * squared + scale * (1 - cube + Math.log(cube))) return scale * cube;
        }
    }
}
