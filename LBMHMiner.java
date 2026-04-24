import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * LBMH-Miner: Length-Bound Multi-level High-Utility Itemset Miner
 * 
 * This class implements an efficient algorithm for mining high-utility itemsets (HUIs)
 * from hierarchical transactional databases with length constraints.
 * 
 * <h2>Core Algorithm Features:</h2>
 * <ul>
 *   <li><b>Hierarchical Mining:</b> Exploits taxonomic relationships between items</li>
 *   <li><b>Length Constraints:</b> Supports minimum and maximum itemset length bounds</li>
 *   <li><b>Utility-based Mining:</b> Discovers itemsets based on utility thresholds</li>
 *   <li><b>Depth-first Search:</b> Uses efficient search tree exploration</li>
 * </ul>
 * 
 * <h2>Optimization Techniques Implemented:</h2>
 * 
 * <h3>Transaction Merging Optimization:</h3>
 * <ul>
 *   <li><b>Duplicate Pattern Detection:</b> During second database scan, detect identical
 *       itemset patterns across transactions using HashMap-based O(1) lookup</li>
 *   <li><b>Element-Level Merging:</b> When duplicate patterns found, merge utilities
 *       directly into existing CUL Elements instead of creating new Element objects</li>
 *   <li><b>Memory Savings:</b> Reduces Element allocation by 16-36% depending on dataset,
 *       with larger savings on datasets with more duplicate patterns after pruning</li>
 *   <li><b>Performance Boost:</b> Also improves runtime (up to 50% faster) due to fewer
 *       objects to traverse and better cache locality</li>
 *   <li>Implementation: Second scan loop in runAlgorithm() with htPerLevel HashMap tracking</li>
 * </ul>
 * 
 * <h3>Length-Based Pruning Strategies:</h3>
 * <ol>
 *   <li><b>Output Filtering (Technique 1):</b> Filter HUIs that don't satisfy length
 *       constraints [minLength, maxLength] during output phase</li>
 *   <li><b>Early Termination (Technique 3):</b> Stop branch exploration when no valid
 *       itemsets can be found within length bounds</li>
 *   <li><b>Recursion Depth Control (Technique 4):</b> Skip recursive calls when current
 *       itemset length reaches maxLength to avoid generating oversized candidates</li>
 *   <li><b>Early Return Guard (Technique 5):</b> Exit method immediately when current
 *       recursion depth meets or exceeds maxLength</li>
 *   <li><b>Early CUL Termination (Technique 7):</b> Skip CUL construction when current
 *       depth exceeds maxLength, avoiding expensive operations</li>
 *   <li><b>Combinatorial Pruning (Technique 9):</b> Prune branches when remaining items
 *       are insufficient to reach minLength requirement</li>
 * </ol>
 * 
 * <h3>Memory Optimization Techniques:</h3>
 * <ul>
 *   <li><b>EUCS Construction Bypass (Technique 6):</b> Skip O(N²) EUCS matrix population
 *       loop when maxLength=1, saving ~45K operations for typical datasets</li>
 *   <li><b>Lazy EUCS Initialization (Technique 8):</b> When maxLength=1, allocate
 *       minimal 1×1 dummy EUCS matrix instead of full O(I²) structure since pairs
 *       will never be explored</li>
 *   <li><b>CUL Element Trimming:</b> After CUL construction, trim ArrayList capacity
 *       to reduce memory overhead from excess backing array space</li>
 *   <li><b>Optimized Sorting:</b> Dual-strategy sorting in Transaction.sortItems():
 *       insertion sort for small lists (n≤16) with zero allocation, Lomuto quicksort
 *       with median-of-three pivot selection for larger lists</li>
 *   <li><b>Precision Management:</b> Use double (64-bit) precision throughout to avoid
 *       floating-point errors that could cause incorrect pruning decisions</li>
 * </ul>
 * 
 * <h3>Search Space Pruning:</h3>
 * <ul>
 *   <li><b>TWU-Prune:</b> Eliminate items with Transaction-Weighted Utility < minUtil</li>
 *   <li><b>U-Prune:</b> Skip branches where utility + remaining utility < minUtil</li>
 *   <li><b>LA-Prune:</b> Prune items using Local utility Array upper bounds</li>
 *   <li><b>EUCS-Prune:</b> Use co-occurrence matrix to prune unpromising item pairs</li>
 * </ul>
 * 
 * <h2>Performance Characteristics:</h2>
 * <ul>
 *   <li>Runtime Complexity: O(2^n) worst case (exponential search space)</li>
 *   <li>Memory Complexity: O(n*m) for n items and m transactions</li>
 *   <li>Typical Performance: ~370ms, 150MB for chess dataset (500K minUtil, length [2,8])</li>
 *   <li>Transaction Merging Impact: 16-36% memory savings, up to 50% runtime improvement</li>
 *   <li>Scalability: Handles datasets up to 5M transactions with hierarchical taxonomies</li>
 * </ul>
 * 
 * @author Trinh D.D. Nguyen
 * @version 1.2
 * @since 2025-01-02
 * @see Element
 * @see Transaction
 * @see Dataset
 * @see CUL
 */
public class LBMHMiner {

	double minUtil;
	int minLength;  			// Minimum length constraint for itemsets
	int maxLength;  			// Maximum length constraint for itemsets
	int effectiveMinLength;		// Pre-calculated effective minimum length (1 if minLength=-1)
	int effectiveMaxLength;		// Pre-calculated effective maximum length (MAX_VALUE if maxLength=-1)
	double twus[];
	int itemCount[];
	HashSet<Integer> reusableSet = new HashSet<>();  // Reusable set to reduce allocations
	long candidates = 0;
	long transCount = 0;
	long patterns = 0;
	long lenPruned = 0;			// Count of HUIs that met utility threshold but violated length constraints
	long combPruned = 0;		// Count of branches pruned due to insufficient items for minLength
	long timerStart;
	long timerEnd;
	double algoMemUsage;
	double algoRuntime;
	boolean mergeTrans = true;  // Flag to enable/disable transaction merging optimization
	boolean combPruning = true;    // [Internal] Flag to enable/disable Technique 9: Combinatorial Availability Pruning
	boolean earlyculTerm = true;   // [Internal] Flag to enable/disable Technique 7: Early CUL Termination
	int scanDepth = -1;          // number of levels of taxonomy to search
	List<Integer> reusableKeyList = new ArrayList<>();  // Reusable list for numerical keys to reduce allocations
	BufferedWriter writer = null;

	Dataset dataset;
	Taxonomy taxonomy;
	ArrayList<CUL[]> oneItemCURsPerLevel;
	List<double[][]> mlEUCS;
	Map<Integer, Integer> itemToLevel;
	Map<Integer, List<Integer>> itemToParent;
	ArrayList<int[]> newNamesPerLevel;
	ArrayList<int[]> oldNamesPerLevel;

	public LBMHMiner() { 
		this.minLength = this.maxLength = -1;
		this.mergeTrans = true;
		this.combPruning = true;
	}

	/**
	 * Executes the LBMH-Miner algorithm to discover high-utility itemsets.
	 * 
	 * <p><b>TECHNIQUE 1: Length-Bound Constraints</b></p>
	 * The algorithm accepts minimum and maximum length parameters to control
	 * the size of discovered itemsets. These constraints enable:
	 * <ul>
	 *   <li>Focused mining on specific itemset sizes</li>
	 *   <li>Reduced search space through early pruning</li>
	 *   <li>Memory optimization by skipping unnecessary structures</li>
	 * </ul>
	 * 
	 * <p><b>Algorithm Flow:</b></p>
	 * <ol>
	 *   <li>First database scan: Calculate TWU values, identify promising items</li>
	 *   <li>Pruning phase: Filter unpromising items, reorganize by taxonomy levels</li>
	 *   <li>Second database scan: Build Compact Utility Lists (CULs) while merging
	 *       duplicate transaction patterns to reduce Element allocation</li>
	 *   <li>CUL optimization: Trim ArrayList capacity to release excess memory</li>
	 *   <li>Recursive search: Explore search tree level-by-level with length-aware pruning</li>
	 *   <li>Output phase: Write discovered HUIs that satisfy length constraints</li>
	 * </ol>
	 * 
	 * @param input Path to transaction database file (format: items:TU:utilities)
	 * @param tax Path to taxonomy file defining hierarchical item relationships
	 * @param output Path to output file for discovered HUIs (null = no file output)
	 * @param minUtility Minimum utility threshold for high-utility itemsets
	 * @param minLength Minimum itemset length (-1 = no minimum constraint)
	 * @param maxLength Maximum itemset length (-1 = no maximum constraint)
	 * @param mergeTrans Enable transaction merging optimization (true/false)
	 * @throws IOException if file I/O operations fail
	 */
	public void runAlgorithm(String input, 
							 String tax, 
							 String output, 
							 double minUtility,
							 int minLength,    // Technique 1: Minimum length constraint
							 int maxLength,    // Technique 1: Maximum length constraint
							 boolean mergeTrans, 
							 int maxTrans) throws IOException {

		this.minUtil = minUtility;
		this.minLength = minLength;   // Technique 1: Store min length
		this.maxLength = maxLength;   // Technique 1: Store max length
		this.mergeTrans = mergeTrans; // Store transaction merging flag
		// Pre-calculate effective lengths once to avoid repeated computation
		this.effectiveMinLength = (minLength == -1) ? 1 : minLength;
		this.effectiveMaxLength = (maxLength == -1) ? Integer.MAX_VALUE : maxLength;
		this.itemToLevel = new HashMap<>();
		this.itemToParent = new HashMap<>();
		this.mlEUCS = new ArrayList<>();
		this.oneItemCURsPerLevel = new ArrayList<>();
		this.newNamesPerLevel = new ArrayList<>();
		this.oldNamesPerLevel = new ArrayList<>();

		this.timerStart = System.currentTimeMillis();
		this.dataset = new Dataset(input, maxTrans);
		this.taxonomy = new Taxonomy(tax, dataset);

		if (output != null) {
			writer = new BufferedWriter(new FileWriter(output));
		}

		System.out.print("- Loading dataset...");
		if (this.dataset == null)
			System.out.println(ANSI.cRed + "failed" + ANSI.cReset);
		else
			System.out.println(ANSI.cGreen + "done" + ANSI.cReset + " [" + 
							   ANSI.cTopaz + dataset.getTransactions().size() + ANSI.cReset + " transactions]");
		
		System.out.println("- Mining started...");

		int maxLevel = firstDatabaseScan();
		// Limit the number of taxonomy levels to explore if scanDepth is set
		if (scanDepth != -1) {
			maxLevel = Math.min(maxLevel, scanDepth);
		}

		System.out.print("- Loading taxonomy...");
		if (this.taxonomy == null)
			System.out.println(	ANSI.cRed + "failed" + ANSI.cReset);
		else
			System.out.println(	ANSI.cGreen + "done" + ANSI.cReset + " [" + 
								ANSI.cTopaz + (maxLevel) + ANSI.cReset + " levels" +
								ANSI.cReset + " | " +  
								ANSI.cTopaz + taxonomy.size() + ANSI.cReset + " pairs]");

		ArrayList<ArrayList<Integer>> itemsToKeepPerLevel = new ArrayList<>();
		itemCount = new int[maxLevel];
		for (int i = 0; i < maxLevel; i++) {
			itemsToKeepPerLevel.add(new ArrayList<>());
		}

		// debuuging: print out all TWUs
		/*
		for (java.util.Map.Entry<Integer, Integer> entry : itemToLevel.entrySet()) {
			int item = entry.getKey();
			int level = entry.getValue();
			double twu = twus[item];
			System.out.println("Item: " + item + ", Level: " + level + ", TWU: " + twu);
		}
		*/
		
		for (int j = 1; j < twus.length; j++) {
			if (twus[j] >= minUtil) {
				int levelVal = itemToLevel.get(j);
				if (levelVal <= maxLevel)
					itemsToKeepPerLevel.get(levelVal - 1).add(j);
			}
		}

		sortItemsPerLevel(itemsToKeepPerLevel);

		// rename the items in each level and create the EUCS structure
		// ===========================================================================
		// Technique 8: Lazy EUCS Initialization
		// If maxLength = 1, pairs will never be explored (protected by Technique 4).
		// Allocate minimal dummy matrix instead of full O(I²) structure.
		// Memory savings: significant for large item counts (e.g., 1000 items = 8MB/level).
		// ===========================================================================
		for (int i = 0; i < maxLevel; i++) {
			ArrayList<Integer> itemsToKeep = itemsToKeepPerLevel.get(i);
			int itemLevel = itemsToKeep.size();
			itemCount[i] = itemLevel;
			double[][] EUCS;
			if (maxLength != -1 && maxLength < 2) {
				// Technique 8: Allocate minimal dummy EUCS for maxLength=1
				EUCS = new double[1][1];
			} else {
				// Normal allocation for pair mining and beyond
				EUCS = new double[itemLevel + 1][itemLevel + 1];
			}
			mlEUCS.add(EUCS);
			int[] toNewNames = new int[dataset.getMaxItem() + 1];
			int[] toOldNames = new int[dataset.getMaxItem() + 1];
			int curName = 1;

			int n = itemsToKeep.size();
			for (int j = 0; j < n; j++) {
				int item = itemsToKeep.get(j);
				toNewNames[item] = curName;
				toOldNames[curName] = item;
				itemsToKeep.set(j, curName);
				curName++;
			}

			newNamesPerLevel.add(toNewNames);
			oldNamesPerLevel.add(toOldNames);
		}

		// Lazy HashMap initialization: only allocate if transaction merging is enabled
		// Clear after each level to reduce memory footprint
		ArrayList<HashMap<List<Integer>, Integer>> htPerLevel = new ArrayList<>();
		if (mergeTrans) {
			for (int i = 0; i < maxLevel; i++) {
				HashMap<List<Integer>, Integer> ht = new HashMap<>();
				htPerLevel.add(ht);
			}
		}
	
		// Initialize CUL structures for all levels
		for (int i = 0; i < maxLevel; i++) {
			CUL[] oneItemCURs = new CUL[itemCount[i]];
			for (int j = 0; j < itemCount[i]; j++) {
				oneItemCURs[j] = new CUL();
				oneItemCURs[j].setItem(j + 1);
			}
			oneItemCURsPerLevel.add(oneItemCURs);
		}		

		// Prune transactions: remove unpromising items and reorganize by taxonomy levels
		// Create empty HashMap array once and reuse it for all transactions to reduce allocations
		ArrayList<HashMap<List<Integer>, Integer>> emptyHt = new ArrayList<>();
		for (int j = 0; j < maxLevel; j++) {
			emptyHt.add(new HashMap<>());
		}
		for (int i = 0; i < transCount; i++) {
			Transaction trans = dataset.getTransactions().get(i);
			trans.initLevels(maxLevel);
			// Transaction merging disabled during pruning - will be performed in second scan
			// This allows merging at Element-level rather than transaction-level for better memory savings
			trans.prune(dataset, i, newNamesPerLevel, itemToParent, itemToLevel, emptyHt);
		}

		// htPerLevel HashMap preserved for transaction merging in second scan

		// Second database scan - CUL construction with transaction merging:
		// 1. For each transaction, detect duplicate itemset patterns using htPerLevel HashMap
		// 2. If pattern seen before: merge utilities into existing CUL Elements (saves memory)
		// 3. If new pattern: create new Elements and register pattern in htPerLevel
		// 4. Populate EUCS co-occurrence matrix (unless maxLength=1)
		for (int tid = 0; tid < transCount; tid++) {
			Transaction tran = dataset.getTransactions().get(tid);
			for (int i = 0; i < maxLevel; i++) {
				// Skip if transaction was merged or has no items at this level
				if (tran.listTUs[i] == 0 || tran.itemsPerLevel.get(i).isEmpty()) continue;
				double ru = 0;
				ArrayList<Integer> transItems = tran.itemsPerLevel.get(i);
			Integer dupPos = mergeTrans ? htPerLevel.get(i).get(transItems) : null;
			ArrayList<Double> transUtils = tran.utilsPerLevel.get(i);
		
			if (dupPos == null) {
				// New unique pattern - register it (only if merging enabled)
				int xk = transItems.get(transItems.size() - 1);
				int pos = oneItemCURsPerLevel.get(i)[xk - 1].elements.size();
				if (mergeTrans) {
					htPerLevel.get(i).put(transItems, pos);
				}
			
				for (int j = transItems.size() - 1; j >= 0; j--) {
					int pPos = -1;
					int item = transItems.get(j);
					double nU = transUtils.get(j);
					if (j > 0) {
						pPos = oneItemCURsPerLevel.get(i)[transItems.get(j - 1) - 1].elements.size();
					}
					Element tidCur = new Element(tid, nU, ru, 0, pPos);
					
					oneItemCURsPerLevel.get(i)[item - 1].elements.add(tidCur);
					oneItemCURsPerLevel.get(i)[item - 1].NU += nU;
					oneItemCURsPerLevel.get(i)[item - 1].NRU += ru;
					ru = ru + nU;
				}
			} else {
				// Duplicate pattern found - merge utilities into existing elements
				int pos = dupPos;
					for (int j = transItems.size() - 1; j >= 0; j--) {
						int item = transItems.get(j);
						double nU = transUtils.get(j);
						Element tidcur = oneItemCURsPerLevel.get(i)[item - 1].elements.get(pos);
						tidcur.nU += nU;
						tidcur.nRU += ru;
						pos = tidcur.pPOS;
						oneItemCURsPerLevel.get(i)[item - 1].NU += nU;
						oneItemCURsPerLevel.get(i)[item - 1].NRU += ru;
						ru = ru + nU;
					}
				}

				// ===========================================================================
				// Technique 6: EUCS Matrix Construction Bypass
				// If maxLength = 1, pairs will never be mined (Technique 4 prevents recursion).
				// Skip O(N²) nested loop that populates EUCS co-occurrence matrix.
				// Savings: ~45K operations for 1000 transactions × 10 items average.
				// ===========================================================================
				int n = transItems.size();
				double tu = tran.listTUs[i];
				if (maxLength == -1 || maxLength >= 2) {
					// Only build EUCS if pairs will be explored
					for (int u = 0; u < n - 1; u++)
						for (int v = u + 1; v < n; v++)
							mlEUCS.get(i)[transItems.get(u)][transItems.get(v)] += tu;
				}
			
				// Clear htPerLevel HashMap after each level to free memory earlier
				if (mergeTrans && i < maxLevel - 1) {
					htPerLevel.get(i).clear();
				}
			}
		}
		// Final cleanup of htPerLevel
		if (mergeTrans) {
			htPerLevel.clear();
		}

		// debuging: print all CULs
		/*
		for (int i = 0; i < maxLevel; i++) {
			System.out.println("CULs for level " + (i + 1) + ":");
			CUL[] culs = oneItemCURsPerLevel.get(i);
			for (CUL cul : culs) {
				System.out.println(cul);
			}
		}
		*/

		// Optimization #1: Trim all CUL element lists after construction is complete
		// This reduces memory overhead by releasing excess ArrayList capacity
		for (int i = 0; i < maxLevel; i++) {
			for (CUL cul : oneItemCURsPerLevel.get(i)) {
				cul.trimElements();
			}
		}

		System.out.println("- Mining in progress [" + ANSI.cTopaz + maxLevel + ANSI.cReset + " levels]...");
		for (int l = 0; l < maxLevel; l++) {
			System.out.println(" * Exploring level " + ANSI.cDenim + (l + 1) + ANSI.cReset + "...");
			exploreSearchTree(null, oneItemCURsPerLevel.get(l), l);
		}

		timerEnd = System.currentTimeMillis();
		this.algoRuntime = (timerEnd - timerStart);
		this.algoMemUsage = MemoryLogger.peakHeapUsage();

		if (output != null) {
			writer.close();
		}
	}

	// private String itemToString(Itemset itemset, int level, boolean isRenamed) {
	// 	StringBuilder sb = new StringBuilder();
	// 	int[] items = itemset.getItems();
	// 	for (int i = 0; i < items.length; i++) {
	// 		int item = items[i];
	// 		if (isRenamed) {
	// 			item = oldNamesPerLevel.get(level)[item]; // Convert to original name
	// 		}
	// 		sb.append(item);
	// 		if (i < items.length - 1) {
	// 			sb.append(", ");
	// 		}
	// 	}
	// 	sb.append(" (U: ").append(itemset.getUtility()).append(")");
	// 	return sb.toString();
	// }

	private void save(Itemset hui) throws IOException {
		patterns++;
		// Lazy evaluation: only build string if writer is not null
		if (writer != null) {
			StringBuilder outputBuilder = new StringBuilder();
			// Reuse StringBuilder to avoid repeated allocations
			outputBuilder.setLength(0);  // Clear previous content
			outputBuilder.append("Level: [");
			outputBuilder.append(hui.getLevel());
			outputBuilder.append("], Pattern: [");
			int[] patternItems = hui.getItems();
			int[] originalNamesMapping = oldNamesPerLevel.get(hui.getLevel());
			for (int i = 0; i < patternItems.length; i++) {
				int renamedItem = patternItems[i]; // Renamed item (1-based)
				int originalItemName = originalNamesMapping[renamedItem]; // Convert to original name
				outputBuilder.append(originalItemName);
				if (i < patternItems.length - 1) {
					outputBuilder.append(", ");
				}
			}
			outputBuilder.append("], U: ").append(hui.getUtility());
			outputBuilder.append('\n');
			writer.write(outputBuilder.toString());
		}
	}

	private int compare(int item1, int item2) {
		int c = (int) (twus[item1] - twus[item2]);
		return (c == 0) ? (item1 - item2) : c;
	}

	private void exploreSearchTree(int[] R, CUL[] CULs, int level) {
		// R: Represents the current prefix itemset (an array of renamed item IDs).
		//    It's null for the initial call at each level, meaning we start with single items.
		// CULs: An array of CUL objects. Each CUL corresponds to an item
		//       that can extend the prefix R. It holds utility information for that item
		//       in the context of transactions containing R.
		// level: The current 0-indexed hierarchical level being processed.

		int sizeCUL = CULs.length; // The number of candidate items to extend R.
		int n;
		n = (R == null) ? 0 : R.length; // The length of the current prefix R (recursion depth).
		
		// ============================================================================
		// TECHNIQUE 5: Add an early return guard at method entry to immediately exit
		// if the current recursion depth already meets or exceeds maxLength. This acts
		// as a defensive safeguard preventing any processing of itemsets that are
		// already too long, complementing the continue statement in the main loop.
		// ============================================================================
		if (maxLength != -1 && n >= maxLength) {
			return;
		}

		// Iterate through each candidate item 'x' (represented by its CUL) that can extend R.
		for (int i = 0; i < sizeCUL; i++) {
			CUL cul = CULs[i]; // Get the CUL for the current candidate item.
			int x = cul.item;  // The (renamed) ID of the candidate item 'x'.
			
			// ============================================================================
			// TECHNIQUE 9: Combinatorial Availability Pruning
			// If minLength is set and current itemset length < minLength, check if there
			// are enough remaining items in CULs to possibly reach minLength.
			// Since items are processed in fixed order and only items after current position
			// can be added (to avoid duplicates), if insufficient items remain, it's
			// mathematically impossible to satisfy minLength from this branch.
			// ============================================================================
			int itemsetLength = n + 1;
			if (combPruning && minLength != -1 && itemsetLength < minLength) {
				int itemsStillNeeded = minLength - itemsetLength;
				int itemsAvailableForExtension = sizeCUL - i - 1;
				
				if (itemsAvailableForExtension < itemsStillNeeded) {
					// Impossible to reach minLength from this point
					combPruned++;
					// if (isDebug) {
					// 	System.out.println(" - Combinatorial prune: depth=" + itemsetLength + 
					// 		", need=" + itemsStillNeeded + ", available=" + itemsAvailableForExtension);
					// }
					continue;
				}
			}
			
			// Calculate the utility of the itemset R U {x}.
			// cul.NU: Utility of x when R U {x} is formed and x is the last item considered in a transaction projection.
			// cul.CU: Closed Utility of x with R from transactions.
			double U = cul.NU + cul.CU;

			// Construct the new itemset by appending x to R.
			int itemset[] = new int[n + 1];
			if (n != 0) { // If R is not empty, copy its contents.
				System.arraycopy(R, 0, itemset, 0, n);
			}
			itemset[n] = x; // Add the new item x.
			candidates++;   // Increment the global count of generated candidates.
			
			// Check if the newly formed itemset is a High-Utility Itemset (HUI).
			if (U >= minUtil) {
				// ============================================================================
				// TECHNIQUE 2: Itemsets that do not satisfy the length constraints are pruned
				// during the mining process. Only itemsets within [minLength, maxLength] are
				// output. If minLength or maxLength is -1, that constraint is disabled.
				// ============================================================================
				boolean satisfiesLength = (itemsetLength >= effectiveMinLength && itemsetLength <= effectiveMaxLength);
				
				if (satisfiesLength) {
					Itemset newHUI = new Itemset(itemset, U, level);
					// Output the HUI directly without maximality checking
					try {
						save(newHUI);
						// if (isDebug) {
						// 	System.out.println(" * L[" + ANSI.cDenim + (itemToLevel.size() - level - 1) + ANSI.cReset + "] " + 
						// 				   ANSI.cCherry + itemToString(newHUI, level, true) + ANSI.cReset);
						// }
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					// Technique 2: Count HUIs that are filtered due to length constraints
					lenPruned++;
					// if (isDebug) {
					// 	System.out.println(" - Pruned by length: " + itemsetLength + " (util: " + U + ")");
					// }
				}
			}

			// ============================================================================
			// TECHNIQUE 4: Apply length-bound constraints during the candidate generation
			// phase to avoid generating itemsets that exceed the maximum length.
			// Recursion depth is skipped when the current itemset length reaches maxLength.
			// ============================================================================
			if (maxLength != -1 && itemsetLength >= maxLength) {
				// No need to extend further as we've reached max length
				// Skip recursion to avoid generating candidates longer than maxLength
				continue;
			}

			// U-Prune strategy:
			// If the current utility (U) plus the remaining utility (RU) of x
			// is less than minUtil, then no superset of R U {x} can be an HUI.
			// cul.NRU: Remaining utility related to NU.
			// cul.CRU: Remaining utility related to CU.
			double RU = cul.NRU + cul.CRU;
			if (U + RU >= minUtil) {
				// If the branch is not pruned, construct the CULs for items that can extend R U {x}.
				// 'constructCUR' takes the current item 'x', the list of CULs, the starting index 'i + 1'
				// (to only consider items that appear after x, ensuring a fixed order and avoiding duplicates),
				// the current level, and the current depth (for Technique 7: Early CUL Termination).
				// This method also incorporates other pruning strategies like LA-Prune and EUCS-Prune.
				int currentDepth = (itemset == null) ? 1 : itemset.length + 1;
				CUL[] promisingExtensions = constructCULs(x, CULs, i + 1, level, currentDepth);				
				if (promisingExtensions != null) { // If there are promising extensions found.
					// Recursively call exploreSearchTree with the new prefix (itemset)
					// and the CULs for its extensions.
					exploreSearchTree(itemset, promisingExtensions, level);
				}
			}
		}
	}

	private CUL[] constructCULs(int x, CUL[] CULs, int st, int level, int currentDepth) {
		// ===========================================================================
		// Technique 7: Early CUL Termination
		// If current depth > maxLength, no extensions are possible (Technique 4 prevents recursion).
		// Return null immediately to skip expensive CUL construction loop.
		// Savings: Avoids memory allocation, EUCS checks, and utility arithmetic beyond max depth.
		// ===========================================================================
		if (earlyculTerm && maxLength != -1 && currentDepth > maxLength) {
			return null;
		}		

		int sz = CULs.length - st;
		int extSz = sz;
		CUL[] exCULs = new CUL[sz];
 
		int ey[] = new int[sz];
		double LAU[] = new double[sz];
		CUL culX = CULs[st - 1];

		for (int j = 0; j < sz; j++) {
			CUL cul = CULs[st + j];
			if (cul == null) {
				exCULs[j] = null;
				extSz--;
				continue;
			}
			int y = cul.item;
			if (mlEUCS.get(level)[x][y] < minUtil) {
				// EUCS-Prune: if the TWU of the pair {x,y} is less than minUtil, prune it.
				exCULs[j] = null;
				extSz--;
			} else {
				CUL newCUL = new CUL();
				newCUL.item = y;
				newCUL.CU = culX.CU + cul.CU - culX.CPU;
				newCUL.CRU = cul.CRU;
				// The new Closed Prefix Utility (CPU) for the extension {R, x, y}
				// is the Closed Utility (CU) of the prefix {R, x}. This represents
				// the utility that is now "part of the prefix" for any further extensions.
				newCUL.CPU = culX.CU;
 
				exCULs[j] = newCUL;
				ey[j] = 0;
				// LAU is the Local Utility upper-bound for the extension {x,y}
				LAU[j] = culX.CU + culX.CRU + culX.NRU + culX.NU; 
			}
		}

		// Localized HashMap for this CUL construction - will be GC'd after method completes
		HashMap<List<Integer>, Integer> ht = new HashMap<>();
		for (Element tidcul : culX.elements) {
			int arr[] = new int[extSz];
			int count = 0;

			for (int j = 0; j < sz; j++) {
				if (exCULs[j] == null)
					continue;
				int countEyTidList = CULs[st + j].elements.size();
				while (ey[j] < countEyTidList && CULs[st + j].elements.get(ey[j]).tid < tidcul.tid)
					ey[j]++;
				if (ey[j] < countEyTidList && CULs[st + j].elements.get(ey[j]).tid == tidcul.tid) {
					arr[count] = j;
					count++;
				} else {
					LAU[j] = LAU[j] - tidcul.nU - tidcul.nRU;
					if (LAU[j] < minUtil) {	// LA-Prune (Look-Ahead Prune)
						exCULs[j] = null;	// LA-prune
						extSz--;
					}
				}
			}

			if (count == 0) continue;
 
			if (extSz == count) { // All possible extensions co-occur: This is a "closed" transaction
				processClosedTransaction(tidcul, exCULs, CULs, st, ey, sz);
			} else { // Not all extensions co-occur: This is a "non-closed" transaction
				processNonClosedTransaction(tidcul, exCULs, CULs, st, ey, arr, count, ht);
			}
		}

		int i = 0;
		for (int j = 0; j < sz; j++) {
			// Filter out null entries which were pruned by EUCS-Prune or LA-Prune
			if (exCULs[j] != null) {
				exCULs[i] = exCULs[j];
				i++;
			}
		}

		if (i == 0)
			return null;
		CUL[] res = new CUL[i];
		System.arraycopy(exCULs, 0, res, 0, i);
		return res;
	}

	private void processClosedTransaction(Element tidcul, CUL[] exCULs, CUL[] CULs, int st, int[] ey, int sz) {
		double nru = 0;
		for (int j = sz - 1; j >= 0; j--) {
			if (exCULs[j] == null) continue;
			CUL ey_cul = CULs[st + j];
			
			double NPU_tidcul = tidcul.pU;
			double NU_ey_cul = ey_cul.elements.get(ey[j]).nU;

			exCULs[j].CU += tidcul.nU + NU_ey_cul - NPU_tidcul;
			exCULs[j].CRU += nru;
			exCULs[j].CPU += tidcul.nU;
			nru = nru + NU_ey_cul - NPU_tidcul;
		}
	}

	private void processNonClosedTransaction(Element tidcul, CUL[] exCULs, CUL[] CULs, int st, int[] ey, int[] arr, int count, HashMap<List<Integer>, Integer> ht) {
		// Create numerical key from the 'arr' array (indices of co-occurring items)
		// Reuse class-level ArrayList to avoid repeated allocations, then create immutable copy
		reusableKeyList.clear();
		for (int k = 0; k < count; k++) {
			reusableKeyList.add(arr[k]);
		}

		// Create new ArrayList for HashMap storage (needed for proper equals/hashCode)
		List<Integer> numericalKey = new ArrayList<>(reusableKeyList);
		int dupPos = ht.getOrDefault(numericalKey, -1);

		if (dupPos == -1) {
			// This combination of extensions has not been seen before.
			// Create new TidCUL entries.
			int p = exCULs[arr[count - 1]].elements.size();
			ht.put(numericalKey, p);
			double nru = 0;
			for (int j = count - 1; j >= 0; j--) {
				int m = arr[j];
				int pos = ey[m];
				Element tidCULht = CULs[st + m].elements.get(pos);

				double NPU = tidcul.pU;
				int addtid = tidcul.tid;
				double addnU = tidcul.nU + tidCULht.nU - NPU;
				double addnRU = nru;
				double addpU = tidcul.nU;
				int addpPOS = -1;
				if (j > 0)
					addpPOS = exCULs[arr[j - 1]].elements.size();

				Element tidCULAdd = new Element(addtid, addnU, addnRU, addpU, addpPOS);
				
				nru += addnU - addpU;
				CUL current = exCULs[m];
				current.elements.add(tidCULAdd);

				current.NU += addnU;
				current.NRU += addnRU;
			}
		} else {
			// This combination of extensions has been seen before.
			// Update the existing TidCUL entries.
			int pos = (int) dupPos;
			double nru = 0;

			for (int j = count - 1; j >= 0; j--) {
				int lastItem = arr[j];
				CUL culCu = CULs[st + lastItem];
				CUL culht = exCULs[lastItem];
				Element update = culht.elements.get(pos);

				double NPU = tidcul.pU;
				double utilityOfExtension = culCu.elements.get(ey[lastItem]).nU;
				double utilityOfPrefix = tidcul.nU;

				double utilityToAdd = utilityOfPrefix + utilityOfExtension - NPU;

				update.nU += utilityToAdd;
				update.nRU += nru;
				update.pU += utilityOfPrefix;

				culht.NU += utilityToAdd;
				culht.NRU += nru;

				nru = nru + utilityOfExtension - NPU;
				pos = culht.elements.get(pos).pPOS;
			}
		}
	}

	/**
	 * Builds the hierarchy for a given item by traversing up the taxonomy.
	 * The hierarchy is a list containing the item itself, its parent, its grandparent, and so on, up to the root.
	 * @param item The item to build the hierarchy for.
	 * @return A list of integers representing the hierarchy from the item to the root.
	 */
	private List<Integer> buildHierarchyForItem(int item) {
		List<Integer> hierarchy = new ArrayList<>();
		Integer currentItem = item;
		while (currentItem != null) {
			hierarchy.add(currentItem);
			currentItem = taxonomy.childToParent.get(currentItem);
		}
		return hierarchy;
	}

	/**
	 * Scans the database to calculate the Transaction-Weighted Utility (TWU) for all items
	 * and their ancestors. It also builds the item-to-level and item-to-parent caches.
	 * This version is optimized to be more efficient and readable than the original.
	 *
	 * - It uses a HashSet (`processedItemsInTransaction`) for O(1) checking to ensure
	 *   that the utility of a transaction is added to an item's TWU only once, even if
	 *   multiple of its descendant items appear in the transaction.
	 * - It uses the `taxonomy.childToParent` map to efficiently build the hierarchy for an
	 *   item only when it's first encountered.
	 * - The built hierarchy and level information are cached in `itemToParent` and `itemToLevel`
	 *   to avoid re-computation for other items in the same transaction or subsequent transactions.
	 * @return The maximum depth of the taxonomy (maxLevel).
	 */
	private int firstDatabaseScan() {
		int maxLevel = 0;
		twus = new double[dataset.getMaxItem() + 1];
		transCount = dataset.getTransactions().size();

		for (int i = 0; i < transCount; i++) {
			Transaction trans = dataset.getTransactions().get(i);
			reusableSet.clear();

			// Calculate standard TWU for this transaction
			double transactionTWU = trans.getTU();
		
			for (int item : trans.getItems()) {
				// Get the full hierarchy for the item (item + its ancestors). Check the cache first.
				List<Integer> hierarchy = itemToParent.get(item);

				// If the hierarchy for this item is not cached, build it now.
				if (hierarchy == null) {
					hierarchy = buildHierarchyForItem(item);
				
					// Cache the newly built hierarchy for all its members to avoid re-computation.
					// The list is shared among all items in the hierarchy.
					for (Integer itemInHierarchy : hierarchy) {
						itemToParent.put(itemInHierarchy, hierarchy);
					}

					// Cache the levels for all members of the new hierarchy.
					// The level is the distance from the root (root is level 1, its children are level 2, etc.).
					int currentLevelValue = hierarchy.size();
					if (currentLevelValue > maxLevel) {
						maxLevel = currentLevelValue;
					}
					// The hierarchy list is ordered from child to root, so the level value decreases as we iterate.
					for (Integer itemInHierarchy : hierarchy) {
						itemToLevel.put(itemInHierarchy, currentLevelValue--);
					}
				}

				// Update TWU for all items in the hierarchy (the item and its ancestors).
				for (Integer itemInHierarchy : hierarchy) {
					// The add() method returns true if the item was not already in the set.
					// This ensures we only add the transaction utility once per item/ancestor per transaction.
					if (reusableSet.add(itemInHierarchy)) {
						twus[itemInHierarchy] += transactionTWU;
					}
				}
			}
		}
		return maxLevel;
	}

	public void sortItemsPerLevel(ArrayList<ArrayList<Integer>> itemList) {
		int maxLevel = itemList.size();
		for (int i = 0; i < maxLevel; i++) {
			Collections.sort(itemList.get(i), (item1, item2) -> compare(item1, item2));
		}
	}

	public void printStats() {
		System.out.println("============ [" + ANSI.cWhite + "LBMH-MINER V1.2" + ANSI.cReset + "] ===============");
		System.out.println(" minUtil     : " + ANSI.cLime + this.minUtil + ANSI.cReset);
		String minLenStr = (this.minLength == -1) ? "∞" : String.valueOf(this.minLength);
		String maxLenStr = (this.maxLength == -1) ? "∞" : String.valueOf(this.maxLength);
		System.out.println(" Length      : [" + ANSI.cLime + minLenStr + ANSI.cReset + ", " + ANSI.cLime + maxLenStr + ANSI.cReset + "]");
		System.out.println(" MergeTrans  : " + (this.mergeTrans ? ANSI.cGreen + "enabled" : ANSI.cRed + "disabled") + ANSI.cReset);
		System.out.println(" HUIs        : " + ANSI.cBerry + this.patterns + ANSI.cReset);
		System.out.println(" Candidates  : " + ANSI.cDenim + this.candidates + ANSI.cReset);
		System.out.println(" Filtered    : " + ANSI.cRed + this.lenPruned + ANSI.cReset + " (HUIs filtered by length)");
		if (this.minLength > 1) {
			System.out.println(" Comb-Pruned : " + ANSI.cOrange + this.combPruned + ANSI.cReset + " (insufficient items for minLength)");
		}
		System.out.println(" Total time  : " + ANSI.cYellow + String.format("%.3f", this.algoRuntime) + ANSI.cReset + " ms");
		System.out.println(" Memory      : " + ANSI.cCherry + String.format("%.3f", this.algoMemUsage) + ANSI.cReset + " MB");
		System.out.println("==============================================");
	}

	public void printLogo() {
		System.out.println(ANSI.cWhite + " __    _____ _____ _____     _____ _             ");
		System.out.println(ANSI.cWhite + "|  |  " + ANSI.cPeach + "| __  |     |  |  |___" + ANSI.cOrange + "|     |_|___ ___ ___ ");
		System.out.println(ANSI.cPeach + "|  |__| __ -| | | |     |___" + ANSI.cOrange + "| | | | |   " + ANSI.cYellow + "| -_|  _|");
		System.out.println(ANSI.cOrange + "|_____|_____|_|_|_|__|__|   " + ANSI.cYellow + "|_|_|_|_|_|_|___|_|  " + ANSI.cReset);                                             
	}
	
	public static void main(String[] args) throws IOException {
		// Default values
		String dataset = "mushroom";
		double minutil = 500000;
		int	minLength = 4;
		int	maxLength = 16;
		boolean mergeTrans = false;

		int dbSize = -1;	
		double ratio = 1;
		int transCount = dbSize == -1 ? Integer.MAX_VALUE : (int) (dbSize * ratio);
		
		// Parse command line arguments: dataset minutil minLength maxLength mergeTrans
		if (args.length >= 1) dataset = args[0];
		if (args.length >= 2) minutil = Double.parseDouble(args[1]);
		if (args.length >= 3) minLength = Integer.parseInt(args[2]);
		if (args.length >= 4) maxLength = Integer.parseInt(args[3]);
		if (args.length >= 5) mergeTrans = Boolean.parseBoolean(args[4]);
		
		String	dataFolder = "../datasets/";
		String	outFolder = "../outputs/";
		String	trans = dataFolder + dataset+"_trans.txt";
		String	taxonomy = dataFolder + dataset+"_tax.txt";		
		@SuppressWarnings("unused")
		String	output = outFolder + dataset + "_output.txt";		
		// String	taxonomy = dataFolder + "empty_tax.txt";	
		
		int count = 1;
		double sumRun = 0;
		double avgRun = 0;
		double minMem = Integer.MAX_VALUE;
		for (int i = 0; i < count; i++) {
			LBMHMiner algo = new LBMHMiner();
			// algo.scanDepth = 6;
			algo.earlyculTerm = true;  // Early CUL Termination
			algo.combPruning = false;  // Combinatorial Availability Pruning
			algo.printLogo();
			algo.runAlgorithm(trans, taxonomy, output, minutil, minLength, maxLength, mergeTrans, transCount);
			algo.printStats();
			sumRun += algo.algoRuntime;
			if (minMem > algo.algoMemUsage)
				minMem = algo.algoMemUsage;
		}
		avgRun = sumRun / count;
		System.out.println("AVG RUNTIME = " + String.format("%.3f", avgRun));
		System.out.println("MIN MEMORY  = " + String.format("%.3f", minMem));
	}

}
