/**
 * This file is LGPL licensed.
 * "Borrowed" from CraftBukkit.
 */
package nallar.patched.collection;

import nallar.patched.annotation.FakeExtend;
import nallar.patched.annotation.Generic;
import nallar.tickthreading.patcher.Declare;
import net.minecraft.util.IntHashMap;

import java.util.*;

@SuppressWarnings("unchecked")
@FakeExtend
public abstract class PatchIntHashMap extends IntHashMap {
	private static final int EMPTY_KEY = Integer.MIN_VALUE;
	private static final int BUCKET_SIZE = 4096;
	int[][] keys;
	private java.lang.Object[][] values;
	int size;
	int modCount;

	public PatchIntHashMap() {
		initialize();
	}

	@Override
	public boolean containsItem(int key) {
		return lookup(key) != null;
	}

	@Override
	@Generic
	public Object lookup(int key) {
		int index = keyIndex(key) & (BUCKET_SIZE - 1);
		int[] inner = keys[index];
		if (inner == null) {
			return null;
		}

		for (int i = 0; i < inner.length; i++) {
			int innerKey = inner[i];
			if (innerKey == EMPTY_KEY) {
				return null;
			} else if (innerKey == key) {
				java.lang.Object[] value = values[index];
				if (value != null) {
					return (Object) value[i];
				}
			}
		}

		return null;
	}

	@Override
	public void addKey(int key, java.lang.Object value) {
		put(key, value);
	}

	@Override
	@Declare
	public synchronized Object put(int key, java.lang.Object value) {
		int index = (int) (keyIndex(key) & (BUCKET_SIZE - 1));
		int[] innerKeys = keys[index];
		java.lang.Object[] innerValues = values[index];

		if (innerKeys == null) {
			// need to make a new chain
			keys[index] = innerKeys = new int[8];
			Arrays.fill(innerKeys, EMPTY_KEY);
			values[index] = innerValues = new java.lang.Object[8];
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
					java.lang.Object old = innerValues[i];
					innerKeys[i] = key;
					innerValues[i] = value;
					return (Object) old;
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
		return null;
	}

	@Override
	public java.lang.Object removeObject(int key) {
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
				java.lang.Object value = values[index][i];

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
		values = new java.lang.Object[BUCKET_SIZE][];
	}

	private static int keyIndex(int key) {
		key ^= key >> 16;
		key *= 0x85ebca6b;
		key ^= key >> 13;
		key *= 0xc2b2ae35;
		key ^= key >> 16;
		return key;
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
		java.lang.Object prevValue;

		ValueIterator() {
			expectedModCount = PatchIntHashMap.this.modCount;
		}

		@Override
		public boolean hasNext() {
			return count < PatchIntHashMap.this.size;
		}

		@Override
		public void remove() {
			if (PatchIntHashMap.this.modCount != expectedModCount) {
				throw new ConcurrentModificationException();
			}

			if (lastReturned == EMPTY_KEY) {
				throw new IllegalStateException();
			}

			count--;
			PatchIntHashMap.this.removeObject(lastReturned);
			lastReturned = EMPTY_KEY;
			expectedModCount = PatchIntHashMap.this.modCount;
		}

		@Override
		public java.lang.Object next() {
			if (PatchIntHashMap.this.modCount != expectedModCount) {
				throw new ConcurrentModificationException();
			}

			if (!hasNext()) {
				throw new NoSuchElementException();
			}

			int[][] keys = PatchIntHashMap.this.keys;
			count++;

			if (prevKey != EMPTY_KEY) {
				innerIndex++;
			}

			for (; index < keys.length; index++) {
				if (keys[index] != null) {
					if (innerIndex < keys[index].length) {
						int key = keys[index][innerIndex];
						java.lang.Object value = values[index][innerIndex];
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
