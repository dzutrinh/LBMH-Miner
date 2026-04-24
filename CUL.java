import java.util.ArrayList;
import java.util.List;

public class CUL {

	int item;

	double NU;

	double NRU;

	double CU;

	double CRU;

	double CPU;

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

		return sum;
	}

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

