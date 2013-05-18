package me.nallar.tickthreading.collections;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class SynchronizedMap<K, V> extends HashMap<K, V> {
	private KeySet ks;

	public SynchronizedMap(final int initialCapacity, final float loadFactor) {
		super(initialCapacity, loadFactor);
	}

	public SynchronizedMap(final int initialCapacity) {
		super(initialCapacity);
	}

	public SynchronizedMap() {
		super();
	}

	public SynchronizedMap(final Map<? extends K, ? extends V> m) {
		super(m);
	}

	@Override
	public boolean isEmpty() {
		return super.isEmpty();
	}

	@Override
	public V get(final Object key) {
		return super.get(key);
	}

	@Override
	public boolean containsKey(final Object key) {
		return super.containsKey(key);
	}

	@Override
	public V put(final K key, final V value) {
		return super.put(key, value);
	}

	@Override
	public void putAll(final Map<? extends K, ? extends V> m) {
		super.putAll(m);
	}

	@Override
	public V remove(final Object key) {
		return super.remove(key);
	}

	@Override
	public void clear() {
		super.clear();
	}

	@Override
	public boolean containsValue(final Object value) {
		return super.containsValue(value);
	}

	@Override
	public Object clone() {
		return super.clone();
	}

	@Override
	public Set<K> keySet() {
		return ks == null ? (ks = new KeySet(super.keySet())) : ks;
	}

	@Override
	public Collection<V> values() {
		return super.values();
	}

	@Override
	public Set<Map.Entry<K, V>> entrySet() {
		return super.entrySet();
	}

	private final class KeySet extends AbstractSet<K> {
		private final Set<K> realSet;

		KeySet(final Set<K> realSet) {
			this.realSet = realSet;
		}

		@Override
		public Iterator<K> iterator() {
			return realSet.iterator();
		}

		@Override
		public int size() {
			return SynchronizedMap.this.size();
		}

		@Override
		public boolean contains(Object o) {
			return containsKey(o);
		}

		@Override
		public boolean remove(Object o) {
			return SynchronizedMap.this.remove(o) != null;
		}

		@Override
		public void clear() {
			SynchronizedMap.this.clear();
		}
	}
}
