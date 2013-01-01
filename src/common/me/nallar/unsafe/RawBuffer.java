package me.nallar.unsafe;

import sun.misc.Cleaner;
import sun.misc.Unsafe;

/**
 * Be very careful using this class!
 */
public class RawBuffer {
	public static final Unsafe $ = UnsafeAccess.$;
	public final long address;
	int sizeBytes;
	int size;

	public RawBuffer(int sizeBytes, int size) {
		this.address = $.allocateMemory(sizeBytes);
		this.sizeBytes = sizeBytes;
		this.size = size;
		Cleaner.create(this, new UnsafeDeallocator(address));
	}

	public int size() {
		return size;
	}

	/**
	 * Raw resize - no checking is done, be careful!
	 */
	public void resize(int sizeBytes, int size) {
		assert sizeBytes > 0 && size > 0;
		int oldSizeBytes = this.sizeBytes;
		$.reallocateMemory(address, sizeBytes);
		if (sizeBytes > oldSizeBytes) {
			$.setMemory(address + oldSizeBytes, address + sizeBytes, (byte) 0);
		}
		this.sizeBytes = sizeBytes;
		this.size = size;
	}

	private static class UnsafeDeallocator implements Runnable {
		private final long address;

		private UnsafeDeallocator(long address) {
			this.address = address;
		}

		@Override
		public void run() {
			$.freeMemory(address);
		}
	}
}

