package com.miaokatze.gtsr.common.structure;

/** Shared structure catalog and mechanical-casing policy for non-critical machines. */
public final class GTSRStructureUtility {

    private GTSRStructureUtility() {}

    /** Only replaceable mechanical-casing positions belong in {@code shellPositions}. */
    public static int minimumCasings(int shellPositions) {
        return shellPositions <= 0 ? 0 : Math.max(1, Math.min(8, shellPositions / 10));
    }

    /** Reserve enough positions for required ports on very small structures. */
    public static int minimumCasings(int shellPositions, int requiredPorts) {
        return Math.min(minimumCasings(shellPositions), Math.max(0, shellPositions - Math.max(0, requiredPorts)));
    }

    public static boolean hasMinimumCasings(int actualCasings, int shellPositions) {
        return actualCasings >= minimumCasings(shellPositions);
    }

    public static boolean hasMinimumCasings(int actualCasings, int shellPositions, int requiredPorts) {
        return actualCasings >= minimumCasings(shellPositions, requiredPorts);
    }

    public static int countShape(String[][] shape, char... symbols) {
        int count = 0;
        for (String[] slice : shape) {
            for (String row : slice) {
                for (int i = 0; i < row.length(); i++) {
                    for (char symbol : symbols) {
                        if (row.charAt(i) == symbol) {
                            count++;
                            break;
                        }
                    }
                }
            }
        }
        return count;
    }

}
