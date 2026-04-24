import java.util.ArrayList;
import java.util.List;

/**
 * Compact Utility List (CUL) - Core data structure for efficient HUI mining.
 * 
 * <p>A CUL represents all utility information for a specific item across all
 * transactions where it appears. This compact representation enables efficient
 * candidate generation and pruning during depth-first search.</p>
 * 
 * <h3>Structure:</h3>
 * <ul>
 *   <li><b>Aggregate Utilities:</b> NU, NRU, CU, CRU, CPU (global sums)</li>
 *   <li><b>Element List:</b> Per-transaction utility details (tid, nU, nRU, pU, pPOS)</li>
 * </ul>
 * 
 * <h3>Utility Types Explained:</h3>
 * <dl>
 *   <dt><b>NU (Non-Closed Utility):</b></dt>
 *   <dd>Sum of utilities where item appears without its taxonomic descendants</dd>
 *   
 *   <dt><b>NRU (Non-Closed Remaining Utility):</b></dt>
 *   <dd>Sum of remaining utilities for items that can extend current itemset</dd>
 *   
 *   <dt><b>CU (Closed Utility):</b></dt>
 *   <dd>Sum of utilities where item appears with its taxonomic descendants</dd>
 *   
 *   <dt><b>CRU (Closed Remaining Utility):</b></dt>
 *   <dd>Remaining utility considering closed itemsets</dd>
 *   
 *   <dt><b>CPU (Closed Prefix Utility):</b></dt>
 *   <dd>Prefix utility for closed itemset patterns</dd>
 * </dl>
 * 
 * <h3>Pruning Strategies Using CUL:</h3>
 * <ul>
 *   <li><b>U-Prune:</b> If NU + NRU + CU + CRU < minUtil, prune this branch</li>
 *   <li><b>LA-Prune:</b> Use local utility arrays from elements for tighter bounds</li>
 *   <li><b>EUCS-Prune:</b> Combine with co-occurrence matrix to eliminate unpromising pairs</li>
 * </ul>
 * 
 * @author Trinh D.D. Nguyen
 * @version 1.2
 * @since 2025-05-18
 * @see Element
 */
public class CUL {
	/** Item identifier */
	int item;
	/** Non-Closed Utility: sum of utilities without descendants */
	double NU;
	/** Non-Closed Remaining Utility: sum of remaining utilities */
	double NRU;
	/** Closed Utility: sum of utilities with descendants */
	double CU;
	/** Closed Remaining Utility: remaining utility for closed patterns */
	double CRU;
	/** Closed Prefix Utility: prefix utility for closed itemsets */
	double CPU;

	/**
	 * List of elements, each containing per-transaction utility information:
	 * <ul>
	 *   <li>tid: Transaction ID</li>
	 *   <li>nU: Non-closed utility in this transaction</li>
	 *   <li>nRU: Non-closed remaining utility in this transaction</li>
	 *   <li>pU: Prefix utility in this transaction</li>
	 *   <li>pPOS: Previous item position in transaction</li>
	 * </ul>
	 */
	List<Element> elements;
	
	public CUL() {
		elements = new ArrayList<Element>();
		this.NU  = 0;
		this.NRU = 0;
		this.CU  = 0;
		this.CRU = 0;
		this.CPU = 0;
	}
	
	public int getItem() { return this.item; }
	public void setItem(int item) { this.item = item; }

	public double getNU() { return this.NU; }
	public void setNU(double NU) { this.NU = NU; }
	
	public double getNRU() { return this.NRU; }
	public void setNRU(double NRU) { this.NRU = NRU; }

	public double getCU() { return this.CU; }
	public void setCU(double CU) { this.CU = CU; }

	public double getCRU() { return this.CRU; }
	public void setCRU(double CRU) { this.CRU = CRU; }

	public double getCPU() { return this.CPU; }
	public void setCPU(double CPU) { this.CPU = CPU; }

	public List<Element> getElements() { return elements; }
	public void setElements(List<Element> tidList) { this.elements = tidList; }
	
	/**
	 * Optimization #1: Trim the elements list after construction is complete.
	 * This reduces memory overhead by releasing excess ArrayList capacity.
	 * Savings: ~25-33% per CUL (ArrayList internal array overhead).
	 */
	public void trimElements() {
		if (elements instanceof ArrayList) {
			((ArrayList<Element>) elements).trimToSize();
		}
	}
	
	public int getNPU() {
		int sum = 0;
		int sz = elements.size();
		for (int i = 0; i < sz; i++)
			sum += elements.get(i).pU;
		// for(Element tid : elements)
		// 	sum += tid.pU;
		return sum;
	}

	// Join two CULs by intersecting their elements, applying EUCS-Prune and LA-Prune
	// public static CUL join(CUL x, CUL y) {
	// WORK-IN-PROGRESS
	// }

	// @Override
	public String toString() {
		StringBuilder str=new StringBuilder();
		str.append("CUL [item=" + item + ", NU=" + NU + ", NRU=" + NRU + ", CU=" + CU + ", CRU=" + CRU + ", CPU=" + CPU
				+ "]");
		str.append(System.getProperty("line.separator"));
		for(Element tid:elements) {
			str.append(tid.toString());
			str.append(System.getProperty("line.separator"));
		}
		return str.toString();
	}
}
