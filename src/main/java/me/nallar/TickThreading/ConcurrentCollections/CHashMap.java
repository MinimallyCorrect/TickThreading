package me.nallar.tickthreading.concurrentcollections;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concurrent HashMap, which extends HashMap.
 * Necessary as ConcurrentHashMap does not, so it's
 * much harder to replace usages of HashMap with it
 */
public class CHashMap<K, V> extends HashMap<K, V> {
	private static final long serialVersionUID = 7249069246763182397L;
	private final ConcurrentHashMap<K, V> hashMap;

	public CHashMap(int initialCapacity, float loadFactor) {
		hashMap = new ConcurrentHashMap<K, V>(initialCapacity, loadFactor);
	}

	public CHashMap(int initialCapacity) {
		hashMap = new ConcurrentHashMap<K, V>(initialCapacity);
	}

	public CHashMap() {
		hashMap = new ConcurrentHashMap<K, V>();
	}

	public CHashMap(Map<? extends K, ? extends V> m) {
		hashMap = new ConcurrentHashMap<K, V>(m);
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
		return hashMap.get(key);
	}

	@Override
	public boolean containsKey(Object key) {
		return hashMap.containsKey(key);
	}

	@Override
	public V put(K key, V value) {
		return hashMap.put(key, value);
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		hashMap.putAll(m);
	}

	@Override
	public V remove(Object key) {
		return hashMap.remove(key);
	}

	@Override
	public void clear() {
		hashMap.clear();
	}

	@Override
	public boolean containsValue(Object value) {
		return hashMap.containsValue(value);
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
