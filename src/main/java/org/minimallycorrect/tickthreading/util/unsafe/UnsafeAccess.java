package org.minimallycorrect.tickthreading.util.unsafe;

import java.lang.reflect.*;

import sun.misc.Unsafe;

import org.minimallycorrect.tickthreading.log.Log;

public class UnsafeAccess {
	public static final Unsafe $;

	static {
		Unsafe temp = null;
		try {
			Field theUnsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
			theUnsafe.setAccessible(true);
			temp = (Unsafe) theUnsafe.get(null);
		} catch (Exception e) {
			Log.error("Failed to get unsafe", e);
		}
		$ = temp;
	}
}
