import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
			if (level > oldToNewNames.size()) continue;
			if (oldToNewNames.get(level-1)[j] != 0)  {
				this.itemsPerLevel.get(level-1).add(oldToNewNames.get(level-1)[j]);
				this.utilsPerLevel.get(level-1).add(mapItemToUtility.get(j));
				this.listTUs[level-1] += mapItemToUtility.get(j);
			}
		}
    	sortItems();

    	for (int i = 0; i < this.itemsPerLevel.size(); i++) {
    		if (this.itemsPerLevel.get(i).isEmpty()) continue;

			HashMap<List<Integer>, Integer> htPerLevelMap = htPerLevel.get(i);
			Integer existingTid = htPerLevelMap.get(this.itemsPerLevel.get(i));

			if (existingTid == null) {

				htPerLevelMap.put(new ArrayList<>(this.itemsPerLevel.get(i)), tid);
			} else {

				Transaction existingTrans = dataset.getTransactions().get(existingTid);
				List<Double> existingUtils = existingTrans.utilsPerLevel.get(i);
				List<Double> currentUtils = this.utilsPerLevel.get(i);

				for (int j = 0; j < existingUtils.size(); j++) {
					existingUtils.set(j, existingUtils.get(j) + currentUtils.get(j));
				}
				existingTrans.listTUs[i] += this.listTUs[i];

				this.itemsPerLevel.set(i, new ArrayList<>());
				this.utilsPerLevel.set(i, new ArrayList<>());
				this.listTUs[i] = 0.0;
			}
		}
	}

	public void sortItems() {
		int size = itemsPerLevel.size();
		for (int level = 0; level < size; level++) {
			ArrayList<Integer> itemsList = itemsPerLevel.get(level);
			ArrayList<Double> utilitysList = utilsPerLevel.get(level);

			int n = itemsList.size();
			if (n <= 1) continue;

			if (n <= 16) {

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

				int[] itemsArray = new int[n];
				double[] utilsArray = new double[n];
				int[] indices = new int[n];

				for (int k = 0; k < n; k++) {
					itemsArray[k] = itemsList.get(k);
					utilsArray[k] = utilitysList.get(k);
					indices[k] = k;
				}

				quickSortIndices(indices, itemsArray, 0, n - 1);

				for (int k = 0; k < n; k++) {
					itemsList.set(k, itemsArray[indices[k]]);
					utilitysList.set(k, utilsArray[indices[k]]);
				}
			}
		}
	}

	private void quickSortIndices(int[] indices, int[] values, int low, int high) {
		if (low >= high) return;

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

	private void swap(int[] arr, int i, int j) {
		int temp = arr[i];
		arr[i] = arr[j];
		arr[j] = temp;
	}
}

