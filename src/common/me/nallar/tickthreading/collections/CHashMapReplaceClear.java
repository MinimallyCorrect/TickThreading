package me.nallar.tickthreading.collections;

import java.util.concurrent.ConcurrentHashMap;

import me.nallar.exception.ConcurrencyError;
import me.nallar.tickthreading.util.BooleanThreadLocal;

public class CHashMapReplaceClear<K, V> extends CHashMap<K, V> {
	private final BooleanThreadLocal inReplace = new BooleanThreadLocal();
	private ConcurrentHashMap<K, V> replaceMap;

	@Override
	public synchronized V put(K key, V value) {
		if (inReplace.get() == Boolean.TRUE) {
			return replaceMap.put(key, value);
		} else {
			return super.put(key, value);
		}
	}

	@Override
	public synchronized void clear() {
		if (inReplace.getAndSet(true) != Boolean.FALSE || replaceMap != null) {
			throw new ConcurrencyError("Already replacing");
		}
		replaceMap = new ConcurrentHashMap<K, V>();
	}

	public synchronized void done() {
		inReplace.set(false);
		hashMap = replaceMap;
		if (replaceMap == null) {
			throw new ConcurrencyError("Already done");
		}
		replaceMap = null;
	}
}
