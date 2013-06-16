package nallar.tickthreading.util;

import nallar.tickthreading.Log;
import nallar.unsafe.UnsafeAccess;
import sun.misc.Unsafe;

/**
 * Accessing a field is much faster than a normal ThreadLocal get.
 * Here, we maintain a field which counts the number of threads in which the variable is true
 * In most cases, this allows us to avoid the performance hit of a hashmap lookup in
 * ThreadLocal.get(), assuming the variable is normally false in all threads.
 */
public class BooleanThreadLocal extends ThreadLocal<Boolean> {
	@SuppressWarnings ({"FieldCanBeLocal", "FieldMayBeFinal"})
	private int count = 0;
	private static final Unsafe $ = UnsafeAccess.$;
	private static final long index = $.objectFieldOffset(ReflectUtil.getField(BooleanThreadLocal.class, "count"));
	// Unsafe magics.

	@Override
	public Boolean initialValue() {
		return false;
	}

	/**
	 * @param value Must be Boolean.TRUE or Boolean.FALSE. No new Boolean(true/false)!
	 * Must always set back to false before the thread stops, else performance will be worse.
	 * Not going to break anything, but bad for performance.
	 */
	@Override
	public void set(Boolean value) {
		if (value == get()) {
			Log.severe("Pointless repeat set on threadlocal", new Throwable());
			return;
		}
		super.set(value);
		if (value == Boolean.TRUE) {
			do {
				int old = $.getIntVolatile(this, index);
				int next = old + 1;
				if ($.compareAndSwapInt(this, index, old, next)) {
					return;
				}
			} while (true);
		} else if (value == Boolean.FALSE) {
			do {
				int old = $.getIntVolatile(this, index);
				int next = old - 1;
				if ($.compareAndSwapInt(this, index, old, next)) {
					return;
				}
			} while (true);
		} else {
			throw new Error("Must use Boolean.TRUE/FALSE.");
		}
	}

	/**
	 * @param value Must be Boolean.TRUE or Boolean.FALSE. No new Boolean(true/false)!
	 * Must always set back to false before the thread stops, else performance will be worse.
	 * Not going to break anything, but bad for performance.
	 */
	public Boolean getAndSet(Boolean value) {
		Boolean oldValue = get();
		if (value == oldValue) {
			return oldValue;
		}
		super.set(value);
		if (value == Boolean.TRUE) {
			do {
				int old = $.getIntVolatile(this, index);
				int next = old + 1;
				if ($.compareAndSwapInt(this, index, old, next)) {
					return oldValue;
				}
			} while (true);
		} else if (value == Boolean.FALSE) {
			do {
				int old = $.getIntVolatile(this, index);
				int next = old - 1;
				if ($.compareAndSwapInt(this, index, old, next)) {
					return oldValue;
				}
			} while (true);
		} else {
			throw new Error("Must use Boolean.TRUE/FALSE.");
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
