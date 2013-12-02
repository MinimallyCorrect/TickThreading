package nallar.collections;

import nallar.tickthreading.util.EnumerationIteratorWrapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CHashtable<K, V> extends Hashtable<K, V> {
	private final ConcurrentHashMap<K, V> m;

	public CHashtable(final int initialCapacity, final float loadFactor) {
		m = new ConcurrentHashMap<K, V>(initialCapacity, loadFactor);
	}

	public CHashtable(final int initialCapacity) {
		m = new ConcurrentHashMap<K, V>(initialCapacity);
	}

	public CHashtable() {
		m = new ConcurrentHashMap<K, V>();
	}

	public CHashtable(final Map<K, V> map) {
		m = new ConcurrentHashMap<K, V>(map);
	}

	@Override
	public int size() {
		return m.size();
	}

	@Override
	public boolean isEmpty() {
		return m.isEmpty();
	}

	@Override
	public Enumeration<K> keys() {
		return new EnumerationIteratorWrapper<K>(keySet().iterator());
	}

	@Override
	public Enumeration<V> elements() {
		return new EnumerationIteratorWrapper<V>(values().iterator());
	}

	@Override
	public boolean contains(final Object value) {
		return m.contains(value);
	}

	@Override
	public boolean containsKey(final Object key) {
		return m.containsKey(key);
	}

	@Override
	public V get(final Object key) {
		return m.get(key);
	}

	@Override
	protected void rehash() {
		// Do nothing, CHM does this automatically.
	}

	@Override
	public V put(final Object key, final Object value) {
		return m.put((K) key, (V) value);
	}

	@Override
	public V remove(final Object key) {
		return m.remove(key);
	}

	@Override
	public void putAll(final Map<? extends K, ? extends V> map) {
		m.putAll(map);
	}

	@Override
	public void clear() {
		m.clear();
	}

	@Override
	public Object clone() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("{");

		boolean second = false;
		for (final Map.Entry<K, V> entry : entrySet()) {
			if (second) {
				sb.append(", ");
			}
			K key = entry.getKey();
			V value = entry.getValue();
			sb.append(key == this ? "(this Map)" : key.toString());
			sb.append('=');
			sb.append(value == this ? "(this Map)" : value.toString());

			second = true;
		}

		return sb.append('}').toString();
	}

	@Override
	public Set<K> keySet() {
		return m.keySet();
	}

	@Override
	public Set<Map.Entry<K, V>> entrySet() {
		return m.entrySet();
	}

	@Override
	public Collection<V> values() {
		return m.values();
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}

		if (!(o instanceof Map)) {
			return false;
		}

		Map<K, V> t = (Map<K, V>) o;
		if (t.size() != size()) {
			return false;
		}

		try {
			for (final Map.Entry<K, V> e : entrySet()) {
				K key = e.getKey();
				V value = e.getValue();
				if (value == null) {
					if (!(t.get(key) == null && t.containsKey(key))) {
						return false;
					}
				} else {
					if (!value.equals(t.get(key))) {
						return false;
					}
				}
			}
		} catch (ClassCastException ignored) {
			return false;
		} catch (NullPointerException ignored) {
			return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		return System.identityHashCode(this);
	}
}
