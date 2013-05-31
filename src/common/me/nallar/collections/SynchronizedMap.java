package me.nallar.collections;

import java.util.AbstractSet;
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
	public synchronized V put(final K key, final V value) {
		return super.put(key, value);
	}

	@Override
	public synchronized void putAll(final Map<? extends K, ? extends V> m) {
		super.putAll(m);
	}

	@Override
	public synchronized V remove(final Object key) {
		return super.remove(key);
	}

	@Override
	public void clear() {
		super.clear();
	}

	@Override
	public Set<K> keySet() {
		return ks == null ? (ks = new KeySet(super.keySet())) : ks;
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
