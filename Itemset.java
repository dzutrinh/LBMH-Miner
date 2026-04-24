// Itemset.java
// - This class represents a multi-level high-utility itemset.
// - Coded by: Trinh D.D. Nguyen
// - Version 1.0
// - Date: 2025-05-18

import java.util.Arrays;

public class Itemset {
    private final int[] items;  // Renamed items (1-based for mapping purposes in AlgoMLHMiner)
    private final double utility;  // Utility of the itemset
    private final int level;    // 0-indexed level in the hierarchy/algorithm processing

    /**
     * Constructs an Itemset.
     * @param items The array of items in this itemset (renamed, 1-based).
     *              The array reference is stored directly (no defensive clone)
     *              since the itemset is immutable and the array is never modified.
     *              Optimization #2: Eliminates 50% memory overhead of itemset storage.
     * @param utility The utility of this itemset.
     * @param level The 0-indexed level associated with this itemset.
     */
    public Itemset(int[] items, double utility, int level) {
        this.items = items; 
        this.utility = utility;
        this.level = level;
    }

    /**
     * @return The items in this itemset (renamed, 1-based).
     */
    public int[] getItems() {
        return items;
    }

    /**
     * @return The utility of this itemset.
     */
    public double getUtility() {
        return utility;
    }

    /**
     * @return The 0-indexed level of this itemset.
     */
    public int getLevel() {
        return level;
    }

    /**
     * Returns a string representation of the itemset, primarily for debugging.
     * The specific output format for algorithm results is handled elsewhere (e.g., AlgoMLHMiner.output).
     * @return A string representation of the itemset.
     */
    @Override
    public String toString() {
        return "L[" + level + "], " + Arrays.toString(items) + ", UTIL = " + utility;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Itemset itemset = (Itemset) o;
        return utility == itemset.utility &&
               level == itemset.level &&
               Arrays.equals(items, itemset.items);
    }

}