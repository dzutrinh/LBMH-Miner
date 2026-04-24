import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class LBMHMiner {

	double minUtil;
	int minLength;
	int maxLength;
	int effectiveMinLength;
	int effectiveMaxLength;
	double twus[];
	int itemCount[];
	HashSet<Integer> reusableSet = new HashSet<>();
	long candidates = 0;
	long transCount = 0;
	long patterns = 0;
	long lenPruned = 0;
	long combPruned = 0;
	long timerStart;
	long timerEnd;
	double algoMemUsage;
	double algoRuntime;
	boolean mergeTrans = true;
	boolean combPruning = true;
	boolean earlyculTerm = true;
	int scanDepth = -1;
	List<Integer> reusableKeyList = new ArrayList<>();
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

	public void runAlgorithm(String input,
							 String tax,
							 String output,
							 double minUtility,
							 int minLength,
							 int maxLength,
							 boolean mergeTrans,
							 int maxTrans) throws IOException {

		this.minUtil = minUtility;
		this.minLength = minLength;
		this.maxLength = maxLength;
		this.mergeTrans = mergeTrans;

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

		for (int j = 1; j < twus.length; j++) {
			if (twus[j] >= minUtil) {
				int levelVal = itemToLevel.get(j);
				if (levelVal <= maxLevel)
					itemsToKeepPerLevel.get(levelVal - 1).add(j);
			}
		}

		sortItemsPerLevel(itemsToKeepPerLevel);

		for (int i = 0; i < maxLevel; i++) {
			ArrayList<Integer> itemsToKeep = itemsToKeepPerLevel.get(i);
			int itemLevel = itemsToKeep.size();
			itemCount[i] = itemLevel;
			double[][] EUCS;
			if (maxLength != -1 && maxLength < 2) {

				EUCS = new double[1][1];
			} else {

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

		ArrayList<HashMap<List<Integer>, Integer>> htPerLevel = new ArrayList<>();
		if (mergeTrans) {
			for (int i = 0; i < maxLevel; i++) {
				HashMap<List<Integer>, Integer> ht = new HashMap<>();
				htPerLevel.add(ht);
			}
		}

		for (int i = 0; i < maxLevel; i++) {
			CUL[] oneItemCURs = new CUL[itemCount[i]];
			for (int j = 0; j < itemCount[i]; j++) {
				oneItemCURs[j] = new CUL();
				oneItemCURs[j].setItem(j + 1);
			}
			oneItemCURsPerLevel.add(oneItemCURs);
		}

		ArrayList<HashMap<List<Integer>, Integer>> emptyHt = new ArrayList<>();
		for (int j = 0; j < maxLevel; j++) {
			emptyHt.add(new HashMap<>());
		}
		for (int i = 0; i < transCount; i++) {
			Transaction trans = dataset.getTransactions().get(i);
			trans.initLevels(maxLevel);

			trans.prune(dataset, i, newNamesPerLevel, itemToParent, itemToLevel, emptyHt);
		}

		for (int tid = 0; tid < transCount; tid++) {
			Transaction tran = dataset.getTransactions().get(tid);
			for (int i = 0; i < maxLevel; i++) {

				if (tran.listTUs[i] == 0 || tran.itemsPerLevel.get(i).isEmpty()) continue;
				double ru = 0;
				ArrayList<Integer> transItems = tran.itemsPerLevel.get(i);
			Integer dupPos = mergeTrans ? htPerLevel.get(i).get(transItems) : null;
			ArrayList<Double> transUtils = tran.utilsPerLevel.get(i);

			if (dupPos == null) {

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

				int n = transItems.size();
				double tu = tran.listTUs[i];
				if (maxLength == -1 || maxLength >= 2) {

					for (int u = 0; u < n - 1; u++)
						for (int v = u + 1; v < n; v++)
							mlEUCS.get(i)[transItems.get(u)][transItems.get(v)] += tu;
				}

				if (mergeTrans && i < maxLevel - 1) {
					htPerLevel.get(i).clear();
				}
			}
		}

		if (mergeTrans) {
			htPerLevel.clear();
		}

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

	private void save(Itemset hui) throws IOException {
		patterns++;

		if (writer != null) {
			StringBuilder outputBuilder = new StringBuilder();

			outputBuilder.setLength(0);
			outputBuilder.append("Level: [");
			outputBuilder.append(hui.getLevel());
			outputBuilder.append("], Pattern: [");
			int[] patternItems = hui.getItems();
			int[] originalNamesMapping = oldNamesPerLevel.get(hui.getLevel());
			for (int i = 0; i < patternItems.length; i++) {
				int renamedItem = patternItems[i];
				int originalItemName = originalNamesMapping[renamedItem];
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

		int sizeCUL = CULs.length;
		int n;
		n = (R == null) ? 0 : R.length;

		if (maxLength != -1 && n >= maxLength) {
			return;
		}

		for (int i = 0; i < sizeCUL; i++) {
			CUL cul = CULs[i];
			int x = cul.item;

			int itemsetLength = n + 1;
			if (combPruning && minLength != -1 && itemsetLength < minLength) {
				int itemsStillNeeded = minLength - itemsetLength;
				int itemsAvailableForExtension = sizeCUL - i - 1;

				if (itemsAvailableForExtension < itemsStillNeeded) {

					combPruned++;

					continue;
				}
			}

			double U = cul.NU + cul.CU;

			int itemset[] = new int[n + 1];
			if (n != 0) {
				System.arraycopy(R, 0, itemset, 0, n);
			}
			itemset[n] = x;
			candidates++;

			if (U >= minUtil) {

				boolean satisfiesLength = (itemsetLength >= effectiveMinLength && itemsetLength <= effectiveMaxLength);

				if (satisfiesLength) {
					Itemset newHUI = new Itemset(itemset, U, level);

					try {
						save(newHUI);

					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {

					lenPruned++;

				}
			}

			if (maxLength != -1 && itemsetLength >= maxLength) {

				continue;
			}

			double RU = cul.NRU + cul.CRU;
			if (U + RU >= minUtil) {

				int currentDepth = (itemset == null) ? 1 : itemset.length + 1;
				CUL[] promisingExtensions = constructCULs(x, CULs, i + 1, level, currentDepth);
				if (promisingExtensions != null) {

					exploreSearchTree(itemset, promisingExtensions, level);
				}
			}
		}
	}

	private CUL[] constructCULs(int x, CUL[] CULs, int st, int level, int currentDepth) {

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

				exCULs[j] = null;
				extSz--;
			} else {
				CUL newCUL = new CUL();
				newCUL.item = y;
				newCUL.CU = culX.CU + cul.CU - culX.CPU;
				newCUL.CRU = cul.CRU;

				newCUL.CPU = culX.CU;

				exCULs[j] = newCUL;
				ey[j] = 0;

				LAU[j] = culX.CU + culX.CRU + culX.NRU + culX.NU;
			}
		}

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
					if (LAU[j] < minUtil) {
						exCULs[j] = null;
						extSz--;
					}
				}
			}

			if (count == 0) continue;

			if (extSz == count) {
				processClosedTransaction(tidcul, exCULs, CULs, st, ey, sz);
			} else {
				processNonClosedTransaction(tidcul, exCULs, CULs, st, ey, arr, count, ht);
			}
		}

		int i = 0;
		for (int j = 0; j < sz; j++) {

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

		reusableKeyList.clear();
		for (int k = 0; k < count; k++) {
			reusableKeyList.add(arr[k]);
		}

		List<Integer> numericalKey = new ArrayList<>(reusableKeyList);
		int dupPos = ht.getOrDefault(numericalKey, -1);

		if (dupPos == -1) {

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

	private List<Integer> buildHierarchyForItem(int item) {
		List<Integer> hierarchy = new ArrayList<>();
		Integer currentItem = item;
		while (currentItem != null) {
			hierarchy.add(currentItem);
			currentItem = taxonomy.childToParent.get(currentItem);
		}
		return hierarchy;
	}

	private int firstDatabaseScan() {
		int maxLevel = 0;
		twus = new double[dataset.getMaxItem() + 1];
		transCount = dataset.getTransactions().size();

		for (int i = 0; i < transCount; i++) {
			Transaction trans = dataset.getTransactions().get(i);
			reusableSet.clear();

			double transactionTWU = trans.getTU();

			for (int item : trans.getItems()) {

				List<Integer> hierarchy = itemToParent.get(item);

				if (hierarchy == null) {
					hierarchy = buildHierarchyForItem(item);

					for (Integer itemInHierarchy : hierarchy) {
						itemToParent.put(itemInHierarchy, hierarchy);
					}

					int currentLevelValue = hierarchy.size();
					if (currentLevelValue > maxLevel) {
						maxLevel = currentLevelValue;
					}

					for (Integer itemInHierarchy : hierarchy) {
						itemToLevel.put(itemInHierarchy, currentLevelValue--);
					}
				}

				for (Integer itemInHierarchy : hierarchy) {

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

		String dataset = "sample";
		double minutil = 40;
		int	minLength = 2;
		int	maxLength = 4;
		boolean mergeTrans = true;

		int dbSize = -1;
		double ratio = 1;
		int transCount = dbSize == -1 ? Integer.MAX_VALUE : (int) (dbSize * ratio);

		if (args.length >= 1) dataset = args[0];
		if (args.length >= 2) minutil = Double.parseDouble(args[1]);
		if (args.length >= 3) minLength = Integer.parseInt(args[2]);
		if (args.length >= 4) maxLength = Integer.parseInt(args[3]);
		if (args.length >= 5) mergeTrans = Boolean.parseBoolean(args[4]);

		String	dataFolder = "./";
		String	outFolder = "./";
		String	trans = dataFolder + dataset+"_trans.txt";
		String	taxonomy = dataFolder + dataset+"_tax.txt";
		@SuppressWarnings("unused")
		String	output = outFolder + dataset + "_output.txt";

		int count = 1;
		double sumRun = 0;
		double avgRun = 0;
		double minMem = Integer.MAX_VALUE;
		for (int i = 0; i < count; i++) {
			LBMHMiner algo = new LBMHMiner();

			algo.earlyculTerm = true;
			algo.combPruning = false;
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

