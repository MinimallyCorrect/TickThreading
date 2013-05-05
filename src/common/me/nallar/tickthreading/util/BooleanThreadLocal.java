package me.nallar.tickthreading.util;

import me.nallar.tickthreading.util.concurrent.SpinLockMutex;
import me.nallar.unsafe.UnsafeAccess;
import sun.misc.Unsafe;

/**
 * Accessing a field is much faster than a normal ThreadLocal get.
 * Here, we maintain a field which counts the number of threads in which the variable is true
 * In most cases, this allows us to avoid the performance hit of a hashmap lookup in
 * ThreadLocal.get(), assuming the variable is normally false in all threads.
 */
public class BooleanThreadLocal extends ThreadLocal<Boolean> {
	private static final Unsafe $ = UnsafeAccess.$;
	private static final long index = $.objectFieldOffset(SpinLockMutex.class.getFields()[0]);
	// Unsafe magics.
	@SuppressWarnings ({"FieldCanBeLocal", "FieldMayBeFinal"})
	private int count = 0;

	@Override
	public Boolean initialValue() {
		return false;
	}

	/**
	 * @param value Must be Boolean.TRUE or Boolean.FALSE. No new Boolean(true/false)!
	 */
	@Override
	public void set(Boolean value) {
		super.set(value);
		if (value == Boolean.TRUE) {
			do {
				int old = $.getIntVolatile(this, index);
				int next = old + 1;
				if ($.compareAndSwapInt(this, index, old, next)) {
					return;
				}
			} while (true);
		} else {
			do {
				int old = $.getIntVolatile(this, index);
				int next = old - 1;
				if ($.compareAndSwapInt(this, index, old, next)) {
					return;
				}
			} while (true);
		}
	}

	@Override
	public Boolean get() {
		if (count == 0) {
			return Boolean.FALSE;
		}
		return super.get();
	}
}
