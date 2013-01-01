package me.nallar.unsafe;

public class ResizeableIntBuffer extends RawBuffer {
	public static final int SIZE_OF_INT = 4;

	public ResizeableIntBuffer(int capacity) {
		super(capacity * 4, capacity);
	}

	public void resize(int length) {
		super.resize(length * SIZE_OF_INT, length);
	}

	public Integer get(int index) {
		if (index < 0 || index >= size) {
			throw new IndexOutOfBoundsException();
		}
		return $.getInt(address + index * SIZE_OF_INT);
	}

	public int getUnchecked(int index) {
		return $.getInt(address + index * SIZE_OF_INT);
	}

	public void put(int index, int value) {
		if (index < 0 || index >= size) {
			throw new IndexOutOfBoundsException();
		}
		$.putInt(address + index * SIZE_OF_INT, value);
	}

	public void putUnchecked(int index, int value) {
		$.putInt(address + index * SIZE_OF_INT, value);
	}

	public void copyToArray(int[] destination, int startIndex, int length) {
		if (startIndex < 0 || (startIndex + length) > size || (length) > destination.length) {
			throw new IndexOutOfBoundsException();
		}
		long startAddress = address + startIndex * SIZE_OF_INT;
		$.copyMemory(null, startAddress, destination, SIZE_OF_INT, length * SIZE_OF_INT);
	}
}
