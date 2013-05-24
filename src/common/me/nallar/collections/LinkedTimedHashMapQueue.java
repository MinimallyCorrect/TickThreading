package me.nallar.collections;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import me.nallar.tickthreading.Log;

public class LinkedTimedHashMapQueue<K> extends HashMap<K, Integer> {
	int ticks = Integer.MIN_VALUE;
	private final Map<K, Integer> map;
	private static final long serialVersionUID = 7249069246763182397L;

	public LinkedTimedHashMapQueue(int initialCapacity, float loadFactor) {
		this();
	}

	public LinkedTimedHashMapQueue(int initialCapacity) {
		this();
	}

	public LinkedTimedHashMapQueue() {
		map = Collections.synchronizedMap(new LinkedHashMap<K, Integer>());
	}

	public LinkedTimedHashMapQueue(Map<? extends K, ? extends Integer> m) {
		this();
		this.putAll(m);
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
	public Integer get(Object key) {
		throw new UnsupportedOperationException("Doesn't make sense with this collection choice.");
	}

	@Override
	public boolean containsKey(Object key) {
		throw new UnsupportedOperationException("Doesn't make sense with this collection choice.");
	}

	@Override
	public Integer put(K key, Integer value) {
		if (value > 200) {
			Log.warning("Set time too high: " + value, new Throwable());
		}
		return map.put(key, value + ticks);
	}

	@Override
	public void putAll(Map<? extends K, ? extends Integer> m) {
		for (Map.Entry<? extends K, ? extends Integer> entry : m.entrySet()) {
			put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public Integer remove(Object key) {
		Integer ret = map.remove(key);
		return ret == null ? null : ret - ticks;
	}

	@Override
	public void clear() {
		map.clear();
	}

	@Override
	public boolean containsValue(Object value) {
		return map.containsValue(value);
	}

	@Override
	public Set<K> keySet() {
		throw new UnsupportedOperationException("Doesn't make sense with this collection choice.");
	}

	@Override
	public Collection<Integer> values() {
		throw new UnsupportedOperationException("Doesn't make sense with this collection choice.");
	}

	@Override
	public Set<Map.Entry<K, Integer>> entrySet() {
		return new Set<Map.Entry<K, Integer>>() {
			private final Set<Map.Entry<K, Integer>> entrySet = LinkedTimedHashMapQueue.super.entrySet();

			@Override
			public int size() {
				return entrySet.size();
			}

			@Override
			public boolean isEmpty() {
				return entrySet.isEmpty();
			}

			@Override
			public boolean contains(final Object o) {
				return entrySet.contains(o);
			}

			@Override
			public Iterator<Map.Entry<K, Integer>> iterator() {
				final LinkedList<Map.Entry<K, Integer>> list = new LinkedList<Map.Entry<K, Integer>>();
				int ticks = LinkedTimedHashMapQueue.this.ticks++;
				synchronized (map) {
					if (map.size() > 2000) {
						Log.severe("Too many items in map: " + map.size());
					}
					for (Map.Entry<K, Integer> entry : entrySet) {
						if (entry.getValue() > ticks) {
							break;
						}
						list.add(entry);
					}
				}
				return new Iterator<Map.Entry<K, Integer>>() {
					private final Iterator<Map.Entry<K, Integer>> iterator = list.iterator();
					private Map.Entry<K, Integer> last;

					@Override
					public boolean hasNext() {
						return iterator.hasNext();
					}

					@Override
					public Map.Entry<K, Integer> next() {
						return last = iterator.next();
					}

					@Override
					public void remove() {
						if (map.remove(last.getKey()) == null) {
							Log.warning("Failed to remove " + last.getKey());
						}
					}
				};
			}

			@Override
			public Object[] toArray() {
				return entrySet.toArray();
			}

			@Override
			public <T> T[] toArray(final T[] a) {
				return entrySet.toArray(a);
			}

			@Override
			public boolean add(final Map.Entry<K, Integer> kIntegerEntry) {
				return entrySet.add(kIntegerEntry);
			}

			@Override
			public boolean remove(final Object o) {
				return entrySet.remove(o);
			}

			@Override
			public boolean containsAll(final Collection<?> c) {
				return entrySet.containsAll(c);
			}

			@Override
			public boolean addAll(final Collection<? extends Map.Entry<K, Integer>> c) {
				return entrySet.addAll(c);
			}

			@Override
			public boolean retainAll(final Collection<?> c) {
				return entrySet.retainAll(c);
			}

			@Override
			public boolean removeAll(final Collection<?> c) {
				return entrySet.removeAll(c);
			}

			@Override
			public void clear() {
				entrySet.clear();
			}

			@Override
			public boolean equals(final Object o) {
				return entrySet.equals(o);
			}

			@Override
			public int hashCode() {
				return entrySet.hashCode();
			}
		};
	}

	@SuppressWarnings ("EqualsWhichDoesntCheckParameterClass")
	@Override
	public boolean equals(Object o) {
		return map.equals(o);
	}

	@Override
	public int hashCode() {
		return map.hashCode();
	}

	@Override
	public String toString() {
		return map.toString();
	}
}
