import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the transactional database for high-utility itemset mining.
 * 
 * <p>This class handles loading, parsing, and processing transaction data from
 * input files. It provides utility calculation methods for LBMH-Miner.</p>
 * 
 * <h3>Input Format:</h3>
 * <p>Each line represents one transaction in the format:</p>
 * <pre>item1 item2 ... itemN:transactionUtility:utility1 utility2 ... utilityN</pre>
 * 
 * <p><b>Example:</b></p>
 * <pre>1 2 5 7:45:10 15 8 12</pre>
 * <ul>
 *   <li>Items: 1, 2, 5, 7</li>
 *   <li>Transaction Utility (TU): 45</li>
 *   <li>Item Utilities: 10, 15, 8, 12 respectively</li>
 * </ul>
 * 
 * @author Trinh D.D. Nguyen
 * @version 1.2
 * @since 2025-05-18
 */
public class Dataset {
	
	List<Transaction> transactions;
	private long maxTransLength;
	private long sumTransLength; 
	int maxItem = 0;

	public List<Transaction> getTransactions() { return transactions; }
	public void setTransactions(List<Transaction> transactions) { this.transactions = transactions; }
	public int getMaxItem() { return maxItem; }
	public void setMaxItem(int maxItem) { this.maxItem = maxItem; }
    public long getMaxTransLength() { return maxTransLength; }
    public double getAvgTransLength() { return (double) sumTransLength / transactions.size();  }

	public Dataset(String dataset, int maxTrans) throws IOException {
		this.transactions = new ArrayList<>();
		
		BufferedReader br = new BufferedReader(new FileReader(dataset));
		String line;
		int tid = 0;
		while ((line = br.readLine()) != null) { 
			// ignore empty lines
			if (line.isEmpty() == true) continue;
			
			// ignore comments
			char c = line.charAt(0);
			if (c == '#' || c == '%' || c == '@')  continue;

			tid++;
			transactions.add(create(line));
			if (tid == maxTrans) break;				
		}
		br.close();
	}
	 
	private Transaction create(String line) {
		String[] split = line.trim().split(":");
		double transactionUtility = Double.parseDouble(split[1]);
		String[] strItems = split[0].split(" ");
		String[] strUtils = split[2].split(" ");
		
		int[] items = new  int[strItems.length];
		double[] utils = new  double[strItems.length];

		for (int i = 0; i < items.length; i++) {
			items[i] = Integer.parseInt(strItems[i]);
			utils[i] = Double.parseDouble(strUtils[i]);
			if(items[i] > maxItem) 
				maxItem = items[i];
		}

		if (maxTransLength < items.length)	
        	maxTransLength = items.length;
        sumTransLength += items.length; 

		// create the transaction object for this transaction and return it
		return new Transaction(items, utils, transactionUtility);
	}

	public double sumTU() {
		double t = 0;
		for (int tid = 0; tid < transactions.size(); tid++) {
			Transaction tran = transactions.get(tid);
			t += tran.getTU();
		}
		return t;
	}

}

