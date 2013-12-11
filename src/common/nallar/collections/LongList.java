package nallar.collections;

import java.util.*;

public class LongList {
	private static final long[] EMPTY_LIST = new long[0];
	public int size = 0;
	public long[] list;

	public LongList() {
		list = EMPTY_LIST;
	}

	public LongList(int initialSize) {
		list = new long[initialSize];
	}

	public void add(long value) {
		int index = size++;
		long[] list = this.list;
		int length = list.length;
		if (index >= length) {
			this.list = list = Arrays.copyOf(list, length == 0 ? 10 : length * 2);
		}
		list[index] = value;
	}

	public void set(int index, long value) {
		if (index >= size) {
			throw new ArrayIndexOutOfBoundsException(index + " > " + size);
		}
		list[index] = value;
	}

	public long get(int index) {
		if (index >= size) {
			throw new ArrayIndexOutOfBoundsException(index + " > " + size);
		}
		return list[index];
	}
}
