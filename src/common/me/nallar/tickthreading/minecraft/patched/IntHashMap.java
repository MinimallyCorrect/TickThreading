/**
 * This file is LGPL licensed.
 * "Borrowed" from CraftBukkit.
 */
package me.nallar.tickthreading.minecraft.patched;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

@SuppressWarnings ("unchecked")
public abstract class IntHashMap extends net.minecraft.util.IntHashMap {
	private static final int EMPTY_KEY = Integer.MIN_VALUE;
	private static final int BUCKET_SIZE = 4096;
	int[][] keys;
	private Object[][] values;
	int size;
	int modCount;

	public IntHashMap() {
		initialize();
	}

	@Override
	public Set getKeySet() {
		return new KeySet();
	}

	@Override
	public boolean containsItem(int key) {
		return lookup(key) != null;
	}

	@Override
	public Object lookup(int key) {
		int index = (keyIndex(key) & (BUCKET_SIZE - 1));
		int[] inner = keys[index];
		if (inner == null) {
			return null;
		}

		for (int i = 0; i < inner.length; i++) {
			int innerKey = inner[i];
			if (innerKey == EMPTY_KEY) {
				return null;
			} else if (innerKey == key) {
				return values[index][i];
			}
		}

		return null;
	}

	@Override
	public void addKey(int key, Object value) {
		int index = (keyIndex(key) & (BUCKET_SIZE - 1));
		int[] innerKeys = keys[index];
		Object[] innerValues = values[index];
		modCount++;

		if (innerKeys == null) {
			// need to make a new chain
			keys[index] = innerKeys = new int[8];
			Arrays.fill(innerKeys, EMPTY_KEY);
			values[index] = innerValues = new Object[8];
			innerKeys[0] = key;
			innerValues[0] = value;
			size++;
		} else {
			int i;
			for (i = 0; i < innerKeys.length; i++) {
				// found an empty spot in the chain to put this
				int currentKey = innerKeys[i];
				if (currentKey == EMPTY_KEY) {
					size++;
				}
				if (currentKey == EMPTY_KEY || currentKey == key) {
					innerKeys[i] = key;
					innerValues[i] = value;
					return;
				}
			}

			// chain is full, resize it and add our new entry
			keys[index] = innerKeys = Arrays.copyOf(innerKeys, i << 1);
			Arrays.fill(innerKeys, i, innerKeys.length, EMPTY_KEY);
			values[index] = innerValues = Arrays.copyOf(innerValues, i << 1);
			innerKeys[i] = key;
			innerValues[i] = value;
			size++;
		}
	}

	@Override
	public Object removeObject(int key) {
		int index = (keyIndex(key) & (BUCKET_SIZE - 1));
		int[] inner = keys[index];
		if (inner == null) {
			return null;
		}

		for (int i = 0; i < inner.length; i++) {
			// hit the end of the chain, didn't find this entry
			if (inner[i] == EMPTY_KEY) {
				break;
			}

			if (inner[i] == key) {
				Object value = values[index][i];

				for (i++; i < inner.length; i++) {
					if (inner[i] == EMPTY_KEY) {
						break;
					}

					inner[i - 1] = inner[i];
					values[index][i - 1] = values[index][i];
				}

				inner[i - 1] = EMPTY_KEY;
				values[index][i - 1] = null;
				size--;
				modCount++;
				return value;
			}
		}

		return null;
	}

	@Override
	public void clearMap() {
		if (size == 0) {
			return;
		}

		modCount++;
		size = 0;
		Arrays.fill(keys, null);
		Arrays.fill(values, null);
	}

	private void initialize() {
		keys = new int[BUCKET_SIZE][];
		values = new Object[BUCKET_SIZE][];
	}

	private static int keyIndex(int key) {
		key ^= key >> 16;
		key *= 0x85ebca6b;
		key ^= key >> 13;
		key *= 0xc2b2ae35;
		key ^= key >> 16;
		return key;
	}

	private class KeySet extends AbstractSet {
		KeySet() {
		}

		@Override
		public void clear() {
			IntHashMap.this.clearMap();
		}

		@Override
		public int size() {
			return IntHashMap.this.size;
		}

		@Override
		public boolean contains(Object key) {
			return key instanceof Integer && IntHashMap.this.containsItem((Integer) key);
		}

		@Override
		public boolean remove(Object key) {
			return IntHashMap.this.removeObject((Integer) key) != null;
		}

		@Override
		public Iterator iterator() {
			return new KeyIterator();
		}
	}

	private class KeyIterator implements Iterator {
		final ValueIterator iterator;

		public KeyIterator() {
			iterator = new ValueIterator();
		}

		@Override
		public void remove() {
			iterator.remove();
		}

		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}

		@Override
		public Integer next() {
			iterator.next();
			return iterator.prevKey;
		}
	}

	private class ValueIterator implements Iterator {
		private int count;
		private int index;
		private int innerIndex;
		private int expectedModCount;
		private int lastReturned = EMPTY_KEY;

		int prevKey = EMPTY_KEY;
		Object prevValue;

		ValueIterator() {
			expectedModCount = IntHashMap.this.modCount;
		}

		@Override
		public boolean hasNext() {
			return count < IntHashMap.this.size;
		}

		@Override
		public void remove() {
			if (IntHashMap.this.modCount != expectedModCount) {
				throw new ConcurrentModificationException();
			}

			if (lastReturned == EMPTY_KEY) {
				throw new IllegalStateException();
			}

			count--;
			IntHashMap.this.removeObject(lastReturned);
			lastReturned = EMPTY_KEY;
			expectedModCount = IntHashMap.this.modCount;
		}

		@Override
		public Object next() {
			if (IntHashMap.this.modCount != expectedModCount) {
				throw new ConcurrentModificationException();
			}

			if (!hasNext()) {
				throw new NoSuchElementException();
			}

			int[][] keys = IntHashMap.this.keys;
			count++;

			if (prevKey != EMPTY_KEY) {
				innerIndex++;
			}

			for (; index < keys.length; index++) {
				if (keys[index] != null) {
					for (; innerIndex < keys[index].length; innerIndex++) {
						int key = keys[index][innerIndex];
						Object value = values[index][innerIndex];
						if (key == EMPTY_KEY) {
							break;
						}

						lastReturned = key;
						prevKey = key;
						prevValue = value;
						return prevValue;
					}
					innerIndex = 0;
				}
			}

			throw new NoSuchElementException();
		}
	}

}
