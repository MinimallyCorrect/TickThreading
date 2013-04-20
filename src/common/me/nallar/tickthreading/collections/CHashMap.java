package me.nallar.tickthreading.collections;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.cliffc.high_scale_lib.NonBlockingHashMap;

/**
 * Concurrent HashMap, which extends HashMap.
 * Necessary as NonBlockingHashMap does not, so it's
 * much harder to replace usages of HashMap with it
 */
@SuppressWarnings ("UnusedDeclaration")
public class CHashMap<K, V> extends HashMap<K, V> {
	private static final long serialVersionUID = 7249069246763182397L;
	private final NonBlockingHashMap<K, V> hashMap;

	public CHashMap(int initialCapacity, float loadFactor) {
		hashMap = new NonBlockingHashMap<K, V>(initialCapacity);
	}

	public CHashMap(int initialCapacity) {
		hashMap = new NonBlockingHashMap<K, V>(initialCapacity);
	}

	public CHashMap() {
		hashMap = new NonBlockingHashMap<K, V>();
	}

	public CHashMap(Map<? extends K, ? extends V> m) {
		hashMap = new NonBlockingHashMap<K, V>(m.size());
		for (Map.Entry<? extends K, ? extends V> entry : m.entrySet()) {
			hashMap.put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public int size() {
		return hashMap.size();
	}

	@Override
	public boolean isEmpty() {
		return hashMap.isEmpty();
	}

	@Override
	public V get(Object key) {
		return key == null ? null : hashMap.get(key);
	}

	@Override
	public boolean containsKey(Object key) {
		return key != null && hashMap.containsKey(key);
	}

	@Override
	public V put(K key, V value) {
		return (key == null || value == null) ? null : hashMap.put(key, value);
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		hashMap.putAll(m);
	}

	@Override
	public V remove(Object key) {
		return key == null ? null : hashMap.remove(key);
	}

	@Override
	public void clear() {
		hashMap.clear();
	}

	@Override
	public boolean containsValue(Object value) {
		return value != null && hashMap.containsValue(value);
	}

	@Override
	public Set<K> keySet() {
		return hashMap.keySet();
	}

	@Override
	public Collection<V> values() {
		return hashMap.values();
	}

	@Override
	public Set<Map.Entry<K, V>> entrySet() {
		return hashMap.entrySet();
	}

	@SuppressWarnings ("EqualsWhichDoesntCheckParameterClass")
	@Override
	public boolean equals(Object o) {
		return hashMap.equals(o);
	}

	@Override
	public int hashCode() {
		return hashMap.hashCode();
	}

	@Override
	public String toString() {
		return hashMap.toString();
	}
}
