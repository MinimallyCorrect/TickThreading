package me.nallar.unsafe;

import java.lang.reflect.Array;

import sun.misc.Unsafe;

public class UnsafeUtil {
	private static final Unsafe $ = UnsafeAccess.$;
	private static final Object[] addressGet = new Object[1];
	private static final long baseOffset = $.arrayBaseOffset(Object[].class);
	private static final int addressSize = $.addressSize();

	public static boolean arrayEquals(Object a, Object b) {
		if (a == b) {
			return true;
		}
		if (Array.getLength(a) != Array.getLength(b) || !a.getClass().equals(b.getClass())) {
			return false;
		}
		int baseOffset = $.arrayBaseOffset(a.getClass());
		int scaleOffset = $.arrayIndexScale(a.getClass());
		int numLongs = Array.getLength(a);

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

	public static <T> T initialise(Class<T> c) {
		try {
			return (T) $.allocateInstance(c);
		} catch (Exception e) {
			throw new Error(e);
		}
	}
}
