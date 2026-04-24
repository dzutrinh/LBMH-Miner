import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Taxonomy
// - This class represents a taxonomy of items in the hierarchical dataset.
// - Coded by: Trinh D.D. Nguyen
// - Version 1.0
// - Date: 2025-05-18

public class Taxonomy {

	class Tuple {
		final int parent;
		final int child;
		
		public Tuple(int p, int c) {
			parent = p;
			child = c;
		}
	}
	
	final List<Tuple> taxonomy;
	final Set<Integer> parents;
	final Map<Integer, Integer> childToParent;

	public Taxonomy() { 		
		this.childToParent = new HashMap<>();
		this.taxonomy = new ArrayList<>();
		this.parents = new HashSet<>();
	}

	public Taxonomy(String filename, Dataset dataset) throws IOException { 
		this();
		load(filename, dataset);
	}
	
	public void add(int p, int c) {
		taxonomy.add(new Tuple(p, c));
		parents.add(p);
		childToParent.put(c,p);	
	}
	
	public void load(String filename, Dataset dataset) throws IOException {
		BufferedReader	reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(filename))));
		String line;

		try {
			while ((line = reader.readLine()) != null) {
				if (line.isEmpty() == true) continue;
				char c = line.charAt(0);
				if (c == '#' || c == '%' || c == '@')  continue;
											
				String	tokens[] = line.split(",");
				Integer	child = Integer.parseInt(tokens[0]);
				Integer	parent = Integer.parseInt(tokens[1]);
				if (parent > dataset.getMaxItem())
					dataset.setMaxItem(parent);
				add(parent, child);
			}
		}
		catch (Exception e) { 
			System.err.println("Error in loading taxonomy file: " + filename);
		}
		finally {
			if (reader != null) reader.close(); 
		}
	}
	
	public void load(String filename) throws IOException {
		BufferedReader	reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(filename))));
		String line;

		try {
			while ((line = reader.readLine()) != null) {
				if (line.isEmpty() == true) continue;
				char c = line.charAt(0);
				if (c == '#' || c == '%' || c == '@')  continue;
											
				String	tokens[] = line.split(",");
				Integer	child = Integer.parseInt(tokens[0]);
				Integer	parent = Integer.parseInt(tokens[1]);
				add(parent, child);
			}
		}
		catch (Exception e) { 
			System.err.println("Error in loading taxonomy file: " + filename);
		}
		finally {
			if (reader != null) reader.close(); 
		}
	}

	public List<Tuple> getPairs() { return taxonomy; }
	public Integer parent(int index) { return taxonomy.get(index).parent; }
	public Integer child(int index) { return taxonomy.get(index).child; }
	public Tuple get(int index) { return taxonomy.get(index); }
	public int size() { return taxonomy.size(); }
	public int parentCount() { return parents.size(); }
}
