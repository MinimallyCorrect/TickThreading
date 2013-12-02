package nallar.collections;

import java.util.*;

public class TreeMapHashMap extends TreeMap {
	private final Map map = new HashMap();

	@Override
	public boolean equals(final Object o) {
		return map.equals(o);
	}

	@Override
	public int hashCode() {
		return map.hashCode();
	}

	@Override
	public String toString() {
		return map.toString();
	}

	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override
	public int size() {
		return map.size();
	}

	@Override
	public Object get(final Object key) {
		return map.get(key);
	}

	@Override
	public boolean containsKey(final Object key) {
		return map.containsKey(key);
	}

	@Override
	public boolean containsValue(final Object value) {
		return map.containsValue(value);
	}

	@Override
	public Object put(final Object key, final Object value) {
		return map.put(key, value);
	}

	public TreeMapHashMap() {
		super();
	}

	public TreeMapHashMap(final Comparator comparator) {
		super(comparator);
	}

	public TreeMapHashMap(final Map m) {
		super(m);
	}

	public TreeMapHashMap(final SortedMap m) {
		super(m);
	}

	@Override
	public void putAll(final Map m) {
		map.putAll(m);
	}

	@Override
	public Object remove(final Object key) {
		return map.remove(key);
	}

	@Override
	public void clear() {
		map.clear();
	}

	@Override
	public Set keySet() {
		return map.keySet();
	}

	@Override
	public Collection values() {
		return map.values();
	}

	@Override
	public Set<Map.Entry> entrySet() {
		return map.entrySet();
	}
}
