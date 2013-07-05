package nallar.collections;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentMap;

import com.google.common.collect.MapMaker;

public class ConcurrentWeakHashMap extends WeakHashMap {
	private static final MapMaker mapMaker = new MapMaker().weakKeys();
	private final ConcurrentMap map = mapMaker.makeMap();

	public Object putIfAbsent(final Object key, final Object value) {
		return map.putIfAbsent(key, value);
	}

	public Object replace(final Object key, final Object value) {
		return map.replace(key, value);
	}

	public boolean remove(final Object key, final Object value) {
		return map.remove(key, value);
	}

	public boolean replace(final Object key, final Object oldValue, final Object newValue) {
		return map.replace(key, oldValue, newValue);
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
	public boolean containsKey(final Object key) {
		return map.containsKey(key);
	}

	@Override
	public boolean containsValue(final Object value) {
		return map.containsValue(value);
	}

	@Override
	public Object get(final Object key) {
		return map.get(key);
	}

	@Override
	public Object put(final Object key, final Object value) {
		return map.put(key, value);
	}

	@Override
	public Object remove(final Object key) {
		return map.remove(key);
	}

	@Override
	public void putAll(final Map m) {
		map.putAll(m);
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

	@Override
	public boolean equals(final Object o) {
		return map.equals(o);
	}

	@Override
	public int hashCode() {
		return map.hashCode();
	}
}
