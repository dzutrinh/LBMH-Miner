/**
 * Represents an element in a Compact Utility List (CUL).
 * 
 * <p>Each element corresponds to a transaction and stores utility information
 * needed for the depth-first search exploration and pruning strategies.</p>
 * 
 * <h3>Utility Fields Explained:</h3>
 * <ul>
 *   <li><b>nU (Non-closed Utility):</b> Utility of current itemset in this transaction</li>
 *   <li><b>nRU (Non-closed Remaining Utility):</b> Sum of utilities of items that can
 *       extend current itemset</li>
 *   <li><b>pU (Prefix Utility):</b> Utility contributed by prefix itemset</li>
 *   <li><b>pPOS (Previous Position):</b> Position of previous item in transaction,
 *       used for efficient itemset construction</li>
 * </ul>
 * 
 * @author Trinh D.D. Nguyen
 * @version 1.2
 * @since 2025-05-18
 */
public class Element {
	/** Transaction identifier */
	int tid;
	/** Non-closed utility: utility of current itemset in this transaction */
	double nU;
	/** Non-closed remaining utility: sum of utilities of items that can extend itemset */
	double nRU;
	/** Prefix utility: utility contributed by prefix itemset */
	double pU;
	/** Previous position: index of previous item in transaction */
	int pPOS;

	/**
	 * Constructs a new Element for a transaction in a Compact Utility List.
	 * 
	 * @param tid Transaction identifier
	 * @param nU Non-closed utility (utility of itemset in this transaction)
	 * @param nRU Non-closed remaining utility (sum of utilities of extendable items)
	 * @param pU Prefix utility (utility from prefix itemset)
	 * @param pPOS Previous position (index of previous item in transaction)
	 */
	public Element(int tid, double nU, double nRU, double pU, int pPOS) {
		this.tid = tid;
		this.nU = nU;
		this.nRU = nRU;
		this.pU = pU;
		this.pPOS = pPOS;
	}

	@Override
	public String toString() {	
		return String.format("Element(tid=%d, nU=%.2f, nRU=%.2f, pU=%.2f, pPOS=%d)", 
							 tid, nU, nRU, pU, pPOS);
	}
}
