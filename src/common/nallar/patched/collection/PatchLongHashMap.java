/**
 * This file is LGPL licensed.
 * "Borrowed" from CraftBukkit.
 */
package nallar.patched.collection;

import nallar.patched.annotation.FakeExtend;
import nallar.patched.annotation.Generic;
import nallar.tickthreading.patcher.Declare;
import net.minecraft.util.LongHashMap;

import java.util.*;

@SuppressWarnings("unchecked")
@FakeExtend
public abstract class PatchLongHashMap<V> extends LongHashMap<V> {
	private static final long EMPTY_KEY = Long.MIN_VALUE;
	private static final int BUCKET_SIZE = 8192;
	private final long[][] keys = new long[BUCKET_SIZE][];
	private final Object[][] values = new Object[BUCKET_SIZE][];
	private int size;

	@Override
	@Declare
	public long[][] getKeys() {
		return keys;
	}

	@Override
	public int getNumHashElements() {
		return size;
	}

	@Override
	public boolean containsItem(long key) {
		return getValueByKey(key) != null;
	}

	@Override
	@Generic
	public V getValueByKey(long key) {
		int index = (int) (keyIndex(key) & (BUCKET_SIZE - 1));
		long[] inner = keys[index];
		if (inner == null) {
			return null;
		}

		for (int i = 0; i < inner.length; i++) {
			long innerKey = inner[i];
			if (innerKey == EMPTY_KEY) {
				return null;
			} else if (innerKey == key) {
				Object[] value = values[index];
				if (value != null) {
					return (V) value[i];
				}
			}
		}

		return null;
	}

	@Override
	public void add(long key, Object value) {
		put(key, value);
	}

	@Override
	@Declare
	public synchronized V put(long key, Object value) {
		int index = (int) (keyIndex(key) & (BUCKET_SIZE - 1));
		long[] innerKeys = keys[index];
		Object[] innerValues = values[index];

		if (innerKeys == null) {
			// need to make a new chain
			keys[index] = innerKeys = new long[8];
			Arrays.fill(innerKeys, EMPTY_KEY);
			values[index] = innerValues = new Object[8];
			innerKeys[0] = key;
			innerValues[0] = value;
			size++;
		} else {
			int i;
			for (i = 0; i < innerKeys.length; i++) {
				// found an empty spot in the chain to put this
				long currentKey = innerKeys[i];
				if (currentKey == EMPTY_KEY) {
					size++;
				}
				if (currentKey == EMPTY_KEY || currentKey == key) {
					Object old = innerValues[i];
					innerKeys[i] = key;
					innerValues[i] = value;
					return (V) old;
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
	public synchronized Object remove(long key) {
		int index = (int) (keyIndex(key) & (BUCKET_SIZE - 1));
		long[] inner = keys[index];
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
				return value;
			}
		}

		return null;
	}

	private static long keyIndex(long key) {
		key ^= key >>> 33;
		key *= 0xff51afd7ed558ccdL;
		key ^= key >>> 33;
		key *= 0xc4ceb9fe1a85ec53L;
		key ^= key >>> 33;
		return key;
	}
}
