package me.nallar.collections;

import java.util.Arrays;

public class IntSet {
	private static final int EMPTY_KEY = Integer.MIN_VALUE;
	private static final int BUCKET_SIZE = 4096;
	private final int[][] keys = new int[BUCKET_SIZE][];
	private int size;

	public int[][] getValues() {
		return keys;
	}

	public int size() {
		return size;
	}

	public boolean contains(int key) {
		int index = keyIndex(key);
		int[] inner = keys[index];
		if (inner == null) {
			return false;
		}

		for (int i = 0; i < inner.length; i++) {
			int innerKey = inner[i];
			if (innerKey == EMPTY_KEY) {
				return false;
			} else if (innerKey == key) {
				return true;
			}
		}

		return false;
	}

	public synchronized boolean add(int key) {
		int index = keyIndex(key);
		int[] innerKeys = keys[index];

		if (innerKeys == null) {
			// need to make a new chain
			keys[index] = innerKeys = new int[8];
			Arrays.fill(innerKeys, EMPTY_KEY);
			innerKeys[0] = key;
			size++;
		} else {
			int i;
			for (i = 0; i < innerKeys.length; i++) {
				int currentKey = innerKeys[i];
				if (currentKey == EMPTY_KEY) {
					size++;
					return true;
				}
				if (currentKey == key) {
					return false;
				}
			}

			keys[index] = innerKeys = Arrays.copyOf(innerKeys, i << 1);
			Arrays.fill(innerKeys, i, innerKeys.length, EMPTY_KEY);
			innerKeys[i] = key;
			size++;
		}
		return true;
	}

	public synchronized boolean remove(int key) {
		int index = keyIndex(key);
		int[] inner = keys[index];
		if (inner == null) {
			return false;
		}

		for (int i = 0; i < inner.length; i++) {
			if (inner[i] == EMPTY_KEY) {
				break;
			}

			if (inner[i] == key) {
				for (i++; i < inner.length; i++) {
					if (inner[i] == EMPTY_KEY) {
						break;
					}

					inner[i - 1] = inner[i];
				}

				inner[i - 1] = EMPTY_KEY;
				size--;
				return true;
			}
		}

		return false;
	}

	private static int keyIndex(int key) {
		key ^= key >> 16;
		key *= 0x85ebca6b;
		key ^= key >> 13;
		key *= 0xc2b2ae35;
		key ^= key >> 16;
		return key & (BUCKET_SIZE - 1);
	}
}
