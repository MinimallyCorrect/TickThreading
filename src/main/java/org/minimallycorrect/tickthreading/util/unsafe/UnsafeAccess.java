package org.minimallycorrect.tickthreading.util.unsafe;

import org.minimallycorrect.tickthreading.log.Log;
import sun.misc.Unsafe;

import java.lang.reflect.*;

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
