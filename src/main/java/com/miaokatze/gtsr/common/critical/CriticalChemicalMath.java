package com.miaokatze.gtsr.common.critical;

import java.math.BigInteger;

/** Exact compound arithmetic; RULE_VERSION identifies the read-only legacy paid-heat ledger. */
public final class CriticalChemicalMath {

    public static final int RULE_VERSION = 1;

    private CriticalChemicalMath() {}

    /** q=min(actual fraction / ideal fraction); efficiency=1+(peak-1)*q^2. */
    public static Evaluation evaluate(BigInteger[] componentLitres, int[] ratios, BigInteger totalLitres,
        BigInteger rawHeat, int peakMultiplier) {
        if (componentLitres.length != ratios.length || ratios.length < 2
            || ratios.length > 9
            || totalLitres.signum() < 0
            || rawHeat.signum() < 0
            || peakMultiplier < 1
            || peakMultiplier > 100) throw new IllegalArgumentException("Invalid compound reaction");
        BigInteger ratioTotal = BigInteger.ZERO, componentTotal = BigInteger.ZERO;
        for (int i = 0; i < ratios.length; i++) {
            if (ratios[i] <= 0 || componentLitres[i].signum() < 0)
                throw new IllegalArgumentException("Invalid compound component");
            ratioTotal = ratioTotal.add(BigInteger.valueOf(ratios[i]));
            componentTotal = componentTotal.add(componentLitres[i]);
        }
        if (componentTotal.compareTo(totalLitres) > 0)
            throw new IllegalArgumentException("Components exceed the whole input");
        BigInteger qn = BigInteger.ONE, qd = BigInteger.ONE;
        boolean eligible = totalLitres.signum() > 0 && rawHeat.signum() > 0;
        for (int i = 0; i < ratios.length; i++) {
            if (componentLitres[i].signum() == 0) eligible = false;
            BigInteger n = componentLitres[i].multiply(ratioTotal);
            BigInteger d = totalLitres.multiply(BigInteger.valueOf(ratios[i]));
            if (n.multiply(qd)
                .compareTo(qn.multiply(d)) < 0) {
                qn = n;
                qd = d;
            }
        }
        if (!eligible) return new Evaluation(
            false,
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.ONE,
            BigInteger.ONE,
            BigInteger.ZERO,
            BigInteger.ONE);
        BigInteger gcd = qn.gcd(qd);
        qn = qn.divide(gcd);
        qd = qd.divide(gcd);
        BigInteger ed = qd.multiply(qd);
        BigInteger en = ed.add(
            qn.multiply(qn)
                .multiply(BigInteger.valueOf(peakMultiplier - 1)));
        gcd = en.gcd(ed);
        en = en.divide(gcd);
        ed = ed.divide(gcd);
        BigInteger gn = rawHeat.multiply(en);
        gcd = gn.gcd(ed);
        return new Evaluation(true, qn, qd, en, ed, gn.divide(gcd), ed.divide(gcd));
    }

    public static final class Evaluation {

        public final boolean eligible;
        public final BigInteger qualityNumerator, qualityDenominator;
        public final BigInteger efficiencyNumerator, efficiencyDenominator;
        public final BigInteger generatedNumerator, generatedDenominator, generatedEU;

        private Evaluation(boolean eligible, BigInteger qn, BigInteger qd, BigInteger en, BigInteger ed, BigInteger gn,
            BigInteger gd) {
            this.eligible = eligible;
            qualityNumerator = qn;
            qualityDenominator = qd;
            efficiencyNumerator = en;
            efficiencyDenominator = ed;
            generatedNumerator = gn;
            generatedDenominator = gd;
            generatedEU = gn.divide(gd);
        }

        public int efficiencyHundredths() {
            return efficiencyNumerator.multiply(BigInteger.valueOf(100))
                .divide(efficiencyDenominator)
                .intValueExact();
        }
    }

    public static BigInteger sum(BigInteger[] heat) {
        return heat[0].add(heat[1])
            .add(heat[2]);
    }

}
