package nallar.collections;

import nallar.exception.ConcurrencyError;
import nallar.tickthreading.util.BooleanThreadLocalDefaultFalse;

import java.util.concurrent.ConcurrentHashMap;

public class CHashMapReplaceClear<K, V> extends CHashMap<K, V> {
	private final BooleanThreadLocalDefaultFalse inReplace = new BooleanThreadLocalDefaultFalse();
	private ConcurrentHashMap<K, V> replaceMap;

	@Override
	public synchronized V put(K key, V value) {
		if (inReplace.get() == Boolean.TRUE) {
			return replaceMap.put(key, value);
		} else {
			return super.put(key, value);
		}
	}

	public synchronized void start() {
		if (inReplace.getAndSet(true) != Boolean.FALSE || replaceMap != null) {
			throw new ConcurrencyError("Already replacing");
		}
		replaceMap = new ConcurrentHashMap<K, V>();
	}

	public synchronized void done() {
		if (inReplace.getAndSet(false) != Boolean.TRUE || replaceMap == null) {
			throw new ConcurrencyError("Not yet replacing");
		}
		hashMap = replaceMap;
		replaceMap = null;
	}
}
