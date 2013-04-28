package me.nallar.tickthreading.collections;

import java.util.concurrent.ConcurrentHashMap;

import me.nallar.exception.ConcurrencyError;
import me.nallar.tickthreading.util.BooleanThreadLocal;

public class CHashMapReplaceClear<K, V> extends CHashMap<K, V> {
	private final BooleanThreadLocal inReplace = new BooleanThreadLocal();
	private ConcurrentHashMap<K, V> replaceMap;

	@Override
	public V put(K key, V value) {
		if (inReplace.get() == Boolean.TRUE) {
			return replaceMap.put(key, value);
		} else {
			return super.put(key, value);
		}
	}

	@Override
	public void clear() {
		inReplace.set(true);
		if (replaceMap != null) {
			throw new ConcurrencyError("Already replacing");
		}
		replaceMap = new ConcurrentHashMap<K, V>();
	}

	public void done() {
		inReplace.set(false);
		hashMap = replaceMap;
		if (replaceMap == null) {
			throw new ConcurrencyError("Already done");
		}
		replaceMap = null;
	}
}
