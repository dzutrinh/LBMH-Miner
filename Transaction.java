import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a transaction in a hierarchical transactional database.
 * 
 * <p>A transaction contains items with associated utility values and supports
 * multi-level taxonomy organization. This class implements optimizations for
 * efficient high-utility itemset mining.</p>
 * 
 * <h3>Hierarchical Organization:</h3>
 * <p>Items are organized by taxonomy levels, where each level represents a
 * different abstraction level in the item hierarchy:</p>
 * <ul>
 *   <li><b>Level 0:</b> Most specific items (leaf nodes in taxonomy)</li>
 *   <li><b>Level k:</b> More general categories (ancestors in taxonomy)</li>
 *   <li>Each level maintains separate item lists, utilities, and totals</li>
 * </ul>
 * 
 * <h3>Transaction Merging Support:</h3>
 * <p>The <code>prune()</code> method includes infrastructure for transaction merging,
 * though in LBMH-Miner's current implementation, merging is performed at a different stage:</p>
 * <ul>
 *   <li><b>Pruning Phase:</b> Filters unpromising items and reorganizes by taxonomy level.
 *       Accepts htPerLevel HashMap parameter but typically receives empty maps</li>
 *   <li><b>Actual Merging:</b> Performed during second database scan in LBMHMiner at the
 *       Element-level within CULs, which provides better memory savings (16-36%)</li>
 *   <li><b>Design Flexibility:</b> The htPerLevel parameter allows for alternative merging
 *       strategies if needed in future optimizations</li>
 * </ul>
 * 
 * <h3>Sorting Optimization:</h3>
 * <p>The <code>sortItems()</code> method uses a dual-strategy approach:</p>
 * <ul>
 *   <li><b>Small Lists (n≤16):</b> In-place insertion sort with zero memory allocation</li>
 *   <li><b>Large Lists (n>16):</b> Lomuto quicksort with median-of-three pivot selection
 *       using primitive int arrays for optimal performance</li>
 *   <li><b>Benefits:</b> Reduces memory allocation overhead while maintaining O(n log n)
 *       average-case performance for larger lists</li>
 * </ul>
 * 
 * @author Trinh D.D. Nguyen
 * @version 1.2
 * @since 2025-01-02
 */
public class Transaction {
	int items[];
	double utils[];
	double tu;	
	double[] listTUs;
	
	ArrayList<ArrayList<Integer>> itemsPerLevel = new ArrayList<>();
	ArrayList<ArrayList<Double>> utilsPerLevel = new ArrayList<>();
	
	public Transaction(int[] items, double[] utils, double tu) {
		this.items = items;
		this.utils = utils;
		this.tu = tu;		
	}

	public void initLevels(int maxLevel) {
		listTUs = new double[maxLevel];
		for (int i = 0; i < maxLevel; i++) {
			itemsPerLevel.add(new ArrayList<Integer>());
			utilsPerLevel.add(new ArrayList<Double>());
			listTUs[i] = 0.0;
		}
	}

	public void addItem(int level, int item, double util) {
		itemsPerLevel.get(level-1).add(item);
		utilsPerLevel.get(level-1).add(util);
		listTUs[level-1] += util;
	}

	public int[] getItems() { return items; }
	public void setItems(int[] items) { this.items = items; }
	public double[] getUtils() { return utils; }
	public void setUtils(double[] utils) { this.utils = utils; }
	public double getTU() { return tu; }
	public void setTU(int tu) { this.tu = tu; }

	@Override
	public String toString() {
		StringBuffer strBuff=new StringBuffer();
		int n=items.length;
		for(int i=0;i<n;i++)
 			strBuff.append(items[i] + "["+utils[i]+"] ");
		strBuff.append(":" + tu + "\n");
		return strBuff.toString(); 
	}
	
	/**
	 * Prunes unpromising items and reorganizes transaction by taxonomy levels.
	 * 
	 * <p>This method performs item filtering and hierarchical reorganization:</p>
	 * 
	 * <h4>Item Pruning and Reorganization:</h4>
	 * <ul>
	 *   <li>Filters out items with TWU below minimum utility threshold</li>
	 *   <li>Aggregates utilities for items and their taxonomic ancestors</li>
	 *   <li>Reorganizes items by taxonomy level using oldToNewNames mapping</li>
	 *   <li>Sorts items within each level in ascending item ID order</li>
	 * </ul>
	 * 
	 * <h4>Transaction Merging Support:</h4>
	 * <p>The htPerLevel parameter enables optional transaction merging. However, in
	 * LBMH-Miner's implementation, this parameter typically receives empty HashMaps
	 * because merging is performed later during the second database scan at the
	 * Element-level, which provides superior memory savings.</p>
	 * 
	 * <p><b>Note:</b> If merging were enabled here (non-empty htPerLevel), duplicate
	 * transaction patterns would be detected and their utilities aggregated to reduce
	 * redundancy. The current architecture deliberately defers this to achieve better
	 * optimization during CUL construction.</p>
	 * 
	 * @param dataset The dataset containing all transactions
	 * @param tid This transaction's identifier
	 * @param oldToNewNames Mapping arrays for item renaming per level
	 * @param itemToParent Map from items to their taxonomic ancestors
	 * @param itemToLevel Map from items to their taxonomy level
	 * @param htPerLevel Hash tables for tracking transaction patterns (typically empty)
	 */
	public void prune(	Dataset dataset,
						int tid,
						ArrayList<int[]> oldToNewNames, 
						Map<Integer, List<Integer>> itemToParent, 
						Map<Integer, Integer> itemToLevel,
						ArrayList<HashMap<List<Integer>, Integer>> htPerLevel) {
    	Map<Integer, Double> mapItemToUtility = new HashMap<>();

    	for(int j = 0; j < items.length; j++) {
    		int item = items[j];    		
    		mapItemToUtility.put(item, utils[j]);
    		List<Integer> listParent = itemToParent.get(item);
			int n = listParent.size();
    		for (int k = 1; k < n; k++) {
				int parentItem = listParent.get(k);
				Double parentUtil = mapItemToUtility.get(parentItem);
				if (null == parentUtil)
					parentUtil = utils[j];
				else
					parentUtil += utils[j];
				mapItemToUtility.put(parentItem, parentUtil);
			}
    	}
    	for (int j : mapItemToUtility.keySet()) {
			int level = itemToLevel.get(j);
			if (level > oldToNewNames.size()) continue;  // skip items beyond scan depth
			if (oldToNewNames.get(level-1)[j] != 0)  {
				this.itemsPerLevel.get(level-1).add(oldToNewNames.get(level-1)[j]);
				this.utilsPerLevel.get(level-1).add(mapItemToUtility.get(j));
				this.listTUs[level-1] += mapItemToUtility.get(j);
			}
		}
    	sortItems();
    	
    	// Transaction merging: check if this pattern already exists
    	for (int i = 0; i < this.itemsPerLevel.size(); i++) {
    		if (this.itemsPerLevel.get(i).isEmpty()) continue;
    		
			HashMap<List<Integer>, Integer> htPerLevelMap = htPerLevel.get(i);
			Integer existingTid = htPerLevelMap.get(this.itemsPerLevel.get(i));
			
			if (existingTid == null) {
				// New unique pattern - register it
				htPerLevelMap.put(new ArrayList<>(this.itemsPerLevel.get(i)), tid);
			} else {
				// Duplicate pattern found - merge utilities into existing transaction
				Transaction existingTrans = dataset.getTransactions().get(existingTid);
				List<Double> existingUtils = existingTrans.utilsPerLevel.get(i);
				List<Double> currentUtils = this.utilsPerLevel.get(i);
				
				for (int j = 0; j < existingUtils.size(); j++) {
					existingUtils.set(j, existingUtils.get(j) + currentUtils.get(j));
				}
				existingTrans.listTUs[i] += this.listTUs[i];
				
				// Clear merged data to save memory
				this.itemsPerLevel.set(i, new ArrayList<>());
				this.utilsPerLevel.set(i, new ArrayList<>());
				this.listTUs[i] = 0.0;
			}
		}
	}

	/**
	 * Sorts items within each level in ascending order of their item IDs.
	 * The corresponding utilities in utilsPerLevel are rearranged to maintain consistency.
	 * 
	 * <p><b>Dual-Strategy Sorting:</b></p>
	 * <ul>
	 *   <li><b>Small Lists (n≤16):</b> In-place insertion sort with O(n²) complexity
	 *       but zero memory allocation. Empirically faster for small n due to low overhead</li>
	 *   <li><b>Large Lists (n>16):</b> Lomuto quicksort with O(n log n) average complexity
	 *       using primitive int arrays and median-of-three pivot selection</li>
	 * </ul>
	 * 
	 * <p><b>Performance Benefits:</b></p>
	 * <ul>
	 *   <li>Reduces memory allocation overhead for typical transaction sizes (5-15 items)</li>
	 *   <li>Uses primitive int arrays instead of boxed Integer objects for better performance</li>
	 *   <li>Median-of-three pivot selection reduces worst-case scenarios in quicksort</li>
	 * </ul>
	 */
	public void sortItems() {
		int size = itemsPerLevel.size();
		for (int level = 0; level < size; level++) {
			ArrayList<Integer> itemsList = itemsPerLevel.get(level);
			ArrayList<Double> utilitysList = utilsPerLevel.get(level);

			int n = itemsList.size();
			if (n <= 1) continue; // Already sorted
			
			if (n <= 16) {
				// For small lists, use in-place insertion sort (no allocation)
				for (int i = 1; i < n; i++) {
					int itemKey = itemsList.get(i);
					double utilKey = utilitysList.get(i);
					int j = i - 1;
					
					while (j >= 0 && itemsList.get(j) > itemKey) {
						itemsList.set(j + 1, itemsList.get(j));
						utilitysList.set(j + 1, utilitysList.get(j));
						j--;
					}
					itemsList.set(j + 1, itemKey);
					utilitysList.set(j + 1, utilKey);
				}
			} else {
				// For larger lists, use array-based quicksort
				int[] itemsArray = new int[n];
				double[] utilsArray = new double[n];
				int[] indices = new int[n];
				
				for (int k = 0; k < n; k++) {
					itemsArray[k] = itemsList.get(k);
					utilsArray[k] = utilitysList.get(k);
					indices[k] = k;
				}
				
				quickSortIndices(indices, itemsArray, 0, n - 1);
				
				// Reorder lists based on sorted indices
				for (int k = 0; k < n; k++) {
					itemsList.set(k, itemsArray[indices[k]]);
					utilitysList.set(k, utilsArray[indices[k]]);
				}
			}
		}
	}
	
	/**
	 * Quicksort implementation using Lomuto partition scheme with optimizations.
	 * 
	 * <p>Sorts an index array based on values in a separate values array, allowing
	 * synchronized reordering of multiple parallel arrays (items and utilities).</p>
	 * 
	 * <p><b>Optimizations:</b></p>
	 * <ul>
	 *   <li><b>Median-of-Three:</b> Selects pivot as median of first, middle, and last
	 *       elements to avoid O(n²) worst-case on sorted or reverse-sorted data</li>
	 *   <li><b>Lomuto Partition:</b> Simpler than Hoare partition with clear termination
	 *       conditions, preventing infinite recursion bugs</li>
	 *   <li><b>Primitive Arrays:</b> Uses int[] instead of Integer[] to avoid boxing
	 *       overhead and improve cache locality</li>
	 * </ul>
	 * 
	 * @param indices Array of indices to be sorted
	 * @param values Values array used for comparison (indices sorted by these values)
	 * @param low Starting index of range to sort
	 * @param high Ending index of range to sort
	 */
	private void quickSortIndices(int[] indices, int[] values, int low, int high) {
		if (low >= high) return;
		
		// Use median-of-three for pivot selection
		int mid = low + (high - low) / 2;
		if (values[indices[mid]] < values[indices[low]]) swap(indices, low, mid);
		if (values[indices[high]] < values[indices[low]]) swap(indices, low, high);
		if (values[indices[mid]] < values[indices[high]]) swap(indices, mid, high);
		
		int pivot = values[indices[high]];
		int i = low - 1;
		
		for (int j = low; j < high; j++) {
			if (values[indices[j]] <= pivot) {
				i++;
				swap(indices, i, j);
			}
		}
		swap(indices, i + 1, high);
		
		int pi = i + 1;
		quickSortIndices(indices, values, low, pi - 1);
		quickSortIndices(indices, values, pi + 1, high);
	}
	
	/**
	 * Swaps two elements in an integer array.
	 * 
	 * @param arr The array containing elements to swap
	 * @param i Index of first element
	 * @param j Index of second element
	 */
	private void swap(int[] arr, int i, int j) {
		int temp = arr[i];
		arr[i] = arr[j];
		arr[j] = temp;
	}
}
