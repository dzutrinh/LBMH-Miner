import java.util.Arrays;

public class Itemset {
    private final int[] items;
    private final double utility;
    private final int level;

    public Itemset(int[] items, double utility, int level) {
        this.items = items;
        this.utility = utility;
        this.level = level;
    }

    public int[] getItems() {
        return items;
    }

    public double getUtility() {
        return utility;
    }

    public int getLevel() {
        return level;
    }

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
