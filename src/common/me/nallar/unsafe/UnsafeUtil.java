package me.nallar.unsafe;

import java.lang.reflect.Array;

import sun.misc.Unsafe;

public class UnsafeUtil {
	private static final Unsafe $ = UnsafeAccess.$;
	private static final Object[] addressGet = new Object[1];
	private static final long baseOffset = $.arrayBaseOffset(Object[].class);
	private static final int addressSize = $.addressSize();

	public static void swap(Object a, Object b) {
		// I'm sorry.
		// Really.
		// :(
		long sizeA = sizeOf(a);
		long sizeB = sizeOf(b);
		if (sizeA != sizeB) {
			throw new UnsupportedOperationException("Objects of different classes or sizes can not be swapped.");
		}
		long temporaryInstanceLocation = $.allocateMemory(sizeA);
		$.copyMemory(a, 0, null, temporaryInstanceLocation, sizeA); // Copy object A to temporary directly allocated memory
		$.copyMemory(b, 0, a, 0, sizeA); // Copy object B to object A
		$.copyMemory(null, temporaryInstanceLocation, b, 0, sizeA); // Copy temporary copy of A to B
		$.freeMemory(temporaryInstanceLocation);
	}

	public static long sizeOf(Object object) {
		return $.getAddress(normalize($.getInt(object, 4L)) + 12L);
	}

	private static long normalize(int value) {
		return (value >= 0) ? value : (~0L >>> 32) & value;
	}

	public static boolean arrayEquals(Object a, Object b) {
		if (a == b) {
			return true;
		}
		int numLongs = Array.getLength(a);
		if (numLongs != Array.getLength(b) || !a.getClass().equals(b.getClass())) {
			return false;
		}
		int baseOffset = $.arrayBaseOffset(a.getClass());
		int scaleOffset = $.arrayIndexScale(a.getClass());

		for (long currentOffset = baseOffset, i = 0; i < numLongs; ++i, currentOffset += scaleOffset) {
			long l1 = $.getLong(a, currentOffset);
			long l2 = $.getLong(b, currentOffset);
			if (l1 != l2) {
				return false;
			}
		}
		return true;
	}

	public static synchronized long getAddress(Object o) {
		addressGet[0] = o;
		switch (addressSize) {
			case 4:
				return $.getInt(addressGet, baseOffset);
			case 8:
				return $.getLong(addressGet, baseOffset);
		}

		throw new Error("unsupported address size: " + addressSize);
	}

	public static <T> T createUninitialisedObject(Class<T> c) {
		try {
			return (T) $.allocateInstance(c);
		} catch (Exception e) {
			throw new Error(e);
		}
	}
}
