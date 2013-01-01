package me.nallar.unsafe;

import java.lang.reflect.Field;

import me.nallar.tickthreading.Log;
import sun.misc.Unsafe;

public class UnsafeAccess {
	public static final Unsafe $;

	static {
		Unsafe temp = null;
		try {
			Field theUnsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
			theUnsafe.setAccessible(true);
			temp = (Unsafe) theUnsafe.get(null);
		} catch (Exception e) {
			Log.severe("Failed to get unsafe", e);
		}
		$ = temp;
	}
}
