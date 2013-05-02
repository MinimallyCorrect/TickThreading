package me.nallar.tickthreading.collections;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class TimedHashMapQueue<K> extends HashMap<K, Integer> {
	private int ticks = Integer.MIN_VALUE;
	private static final long serialVersionUID = 7249069246763182397L;
	private final TreeHashSet<Node<K>> nodes;
	private final Set<Map.Entry<K, Integer>> entries = new Set<Map.Entry<K, Integer>>() {
		@Override
		public int size() {
			return TimedHashMapQueue.this.size();
		}

		@Override
		public boolean isEmpty() {
			return size() > 0;
		}

		@Override
		public boolean contains(final Object o) {
			throw new UnsupportedOperationException("Doesn't make sense with this collection choice.");
		}

		@Override
		public Iterator<Map.Entry<K, Integer>> iterator() {
			ticks++;
			return new Iterator<Map.Entry<K, Integer>>() {
				private final Iterator<Node<K>> nodeIterator = nodes.iterator();
				private Node<K> last = null;

				@Override
				public boolean hasNext() {
					if (!nodeIterator.hasNext()) {
						return false;
					}
					Node<K> node = nodeIterator.next();
					if (node.time > ticks) {
						return false;
					}
					last = node;

					return true;
				}

				@Override
				public Map.Entry<K, Integer> next() {
					Node<K> node = last;
					if (node == null) {
						if (!hasNext()) {
							throw new IllegalStateException("Called .next() without .hasNex()");
						}
						node = last;
					}
					return new Entry<K>(node.value, node.time);
				}

				@Override
				public void remove() {
					if (last == null) {
						throw new IllegalStateException("Removed without .next() call, or removed repeatedly.");
					}
					nodes.remove(last);
					last = null;
				}
			};
		}

		@Override
		public Object[] toArray() {
			throw new UnsupportedOperationException("Doesn't make sense with this collection choice.");
		}

		@Override
		public <T> T[] toArray(final T[] a) {
			throw new UnsupportedOperationException("Doesn't make sense with this collection choice.");
		}

		@Override
		public boolean add(final Map.Entry<K, Integer> kIntegerEntry) {
			throw new UnsupportedOperationException("Doesn't make sense with this collection choice.");
		}

		@Override
		public boolean remove(final Object o) {
			throw new UnsupportedOperationException("Doesn't make sense with this collection choice.");
		}

		@Override
		public boolean containsAll(final Collection<?> c) {
			throw new UnsupportedOperationException("Doesn't make sense with this collection choice.");
		}

		@Override
		public boolean addAll(final Collection<? extends Map.Entry<K, Integer>> c) {
			throw new UnsupportedOperationException("Doesn't make sense with this collection choice.");
		}

		@Override
		public boolean retainAll(final Collection<?> c) {
			throw new UnsupportedOperationException("Doesn't make sense with this collection choice.");
		}

		@Override
		public boolean removeAll(final Collection<?> c) {
			throw new UnsupportedOperationException("Doesn't make sense with this collection choice.");
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException("Doesn't make sense with this collection choice.");
		}
	};

	public TimedHashMapQueue(int initialCapacity, float loadFactor) {
		this();
	}

	public TimedHashMapQueue(int initialCapacity) {
		this();
	}

	public TimedHashMapQueue() {
		nodes = new TreeHashSet<Node<K>>();
	}

	public TimedHashMapQueue(Map<? extends K, ? extends Integer> m) {
		this();
		this.putAll(m);
	}

	@Override
	public int size() {
		return nodes.size();
	}

	@Override
	public boolean isEmpty() {
		return nodes.isEmpty();
	}

	@Override
	public Integer get(Object key) {
		throw new UnsupportedOperationException("Doesn't make sense with this collection choice.");
	}

	@Override
	public boolean containsKey(Object key) {
		return key != null && nodes.contains(new Node<K>((K) key, null));
	}

	@Override
	public Integer put(K key, Integer value) {
		nodes.add(new Node<K>(key, ticks + value));
		return null;
	}

	@Override
	public void putAll(Map<? extends K, ? extends Integer> m) {
		for (Map.Entry<? extends K, ? extends Integer> entry : m.entrySet()) {
			put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public Integer remove(Object key) {
		if (key != null) {
			if (nodes.remove(new Node<K>((K) key, null))) {
				return 1;
			}
		}
		return null;
	}

	@Override
	public void clear() {
		nodes.clear();
	}

	@Override
	public boolean containsValue(Object value) {
		throw new UnsupportedOperationException("Doesn't make sense with this collection choice.");
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
		return entries;
	}

	@SuppressWarnings ("EqualsWhichDoesntCheckParameterClass")
	@Override
	public boolean equals(Object o) {
		return nodes.equals(o);
	}

	@Override
	public int hashCode() {
		return nodes.hashCode();
	}

	@Override
	public String toString() {
		return nodes.toString();
	}

	private static class Entry<K> implements Map.Entry<K, Integer> {
		private final K key;
		private final Integer value;

		Entry(final K key, final Integer value) {
			this.key = key;
			this.value = value;
		}

		@Override
		public K getKey() {
			return key;
		}

		@Override
		public Integer getValue() {
			return value;
		}

		@Override
		public Integer setValue(final Integer value) {
			throw new UnsupportedOperationException("Doesn't make sense with this collection choice.");
		}
	}

	private class Node<K> implements Comparable<Node<K>> {
		final K value;
		final Integer time;

		private Node(final K value, final Integer time) {
			this.value = value;
			this.time = time;
		}

		@Override
		public boolean equals(Object a) {
			return a instanceof Node && value.equals(((Node) a).value);
		}

		@Override
		public int hashCode() {
			return value.hashCode();
		}

		@Override
		public int compareTo(final Node<K> node) {
			return time.compareTo(node.time);
		}
	}
}
