public class Element {

	int tid;

	double nU;

	double nRU;

	double pU;

	int pPOS;

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

