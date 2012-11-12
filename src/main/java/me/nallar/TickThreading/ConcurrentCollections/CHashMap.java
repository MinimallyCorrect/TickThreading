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
public class CHashMap extends HashMap {
	private static final long serialVersionUID = 7249069246763182397L;
	private ConcurrentHashMap hashMap;

	public CHashMap(int initialCapacity, float loadFactor) {
		hashMap = new ConcurrentHashMap(initialCapacity, loadFactor);
	}

	public CHashMap(int initialCapacity) {
		hashMap = new ConcurrentHashMap(initialCapacity);
	}

	public CHashMap() {
		hashMap = new ConcurrentHashMap();
	}

	public CHashMap(Map m) {
		hashMap = new ConcurrentHashMap(m);
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
	public Object get(Object key) {
		return hashMap.get(key);
	}

	@Override
	public boolean containsKey(Object key) {
		return hashMap.containsKey(key);
	}

	@Override
	public Object put(Object key, Object value) {
		return hashMap.put(key, value);
	}

	@Override
	public void putAll(Map m) {
		hashMap.putAll(m);
	}

	@Override
	public Object remove(Object key) {
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
	public Set keySet() {
		return hashMap.keySet();
	}

	@Override
	public Collection values() {
		return hashMap.values();
	}

	@Override
	public Set entrySet() {
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
