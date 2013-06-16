package nallar.collections;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import nallar.tickthreading.Log;

public class LinkedTimedHashMapQueue<K> extends HashMap<K, Integer> {
	int ticks = Integer.MIN_VALUE;
	private final Map<K, Integer> map = Collections.synchronizedMap(new LinkedHashMap<K, Integer>());
	private static final long serialVersionUID = 7249069246763182397L;

	public LinkedTimedHashMapQueue(int initialCapacity, float loadFactor) {
		this();
	}

	public LinkedTimedHashMapQueue(int initialCapacity) {
		this();
	}

	public LinkedTimedHashMapQueue() {
	}

	public LinkedTimedHashMapQueue(Map<? extends K, ? extends Integer> m) {
		this();
		this.putAll(m);
	}

	@Override
	public int size() {
		return map.size();
	}

	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override
	public Integer get(Object key) {
		throw new UnsupportedOperationException("Doesn't make sense with this collection choice.");
	}

	@Override
	public boolean containsKey(Object key) {
		throw new UnsupportedOperationException("Doesn't make sense with this collection choice.");
	}

	@Override
	public Integer put(K key, Integer value) {
		if (value > 200) {
			Log.warning("Set time too high: " + value, new Throwable());
		}
		return map.put(key, value + ticks);
	}

	@Override
	public void putAll(Map<? extends K, ? extends Integer> m) {
		for (Map.Entry<? extends K, ? extends Integer> entry : m.entrySet()) {
			put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public Integer remove(Object key) {
		Integer ret = map.remove(key);
		return ret == null ? null : ret - ticks;
	}

	@Override
	public void clear() {
		map.clear();
	}

	@Override
	public boolean containsValue(Object value) {
		return map.containsValue(value);
	}

	@Override
	public Set<K> keySet() {
		return new AbstractSet<K>() {
			@Override
			public int size() {
				throw new UnsupportedOperationException();
			}

			@Override
			public Iterator<K> iterator() {
				ArrayList<K> list = new ArrayList<K>();
				int ticks = LinkedTimedHashMapQueue.this.ticks++;
				synchronized (map) {
					if (map.size() > 2000) {
						Log.severe("Too many items in map: " + map.size());
					}
					Iterator<Map.Entry<K, Integer>> i$ = map.entrySet().iterator();
					while (i$.hasNext()) {
						Map.Entry<K, Integer> entry = i$.next();
						if (entry.getValue() > ticks) {
							break;
						}
						i$.remove();
						list.add(entry.getKey());
					}
				}
				return list.iterator();
			}
		};
	}

	@Override
	public Collection<Integer> values() {
		throw new UnsupportedOperationException("Doesn't make sense with this collection choice.");
	}

	@Override
	public Set<Map.Entry<K, Integer>> entrySet() {
		throw new UnsupportedOperationException("Doesn't make sense with this collection choice.");
	}

	@SuppressWarnings ("EqualsWhichDoesntCheckParameterClass")
	@Override
	public boolean equals(Object o) {
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
}
