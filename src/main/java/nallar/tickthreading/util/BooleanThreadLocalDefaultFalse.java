package nallar.tickthreading.util;

import nallar.unsafe.UnsafeAccess;
import sun.misc.Unsafe;

/**
 * Accessing a field is much faster than a normal ThreadLocal get.
 * Here, we maintain a field which counts the number of threads in which the variable is true
 * In most cases, this allows us to avoid the performance hit of a hashmap lookup in
 * ThreadLocal.get(), assuming the variable is normally false in all threads.
 */
public class BooleanThreadLocalDefaultFalse extends ThreadLocal<Boolean> {
	@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
	private int count = 0;
	private static final Unsafe $ = UnsafeAccess.$;
	private static final long index = $.objectFieldOffset(ReflectUtil.getField(BooleanThreadLocalDefaultFalse.class, "count"));
	// Unsafe magics.

	@Override
	public Boolean initialValue() {
		return false;
	}

	/**
	 * @param value Must be Boolean.TRUE or Boolean.FALSE. No new Boolean(true/false)!
	 *              Must always set back to false before the thread stops, else performance will be worse.
	 *              Not going to break anything, but bad for performance.
	 */
	@Override
	public void set(Boolean value) {
		getAndSet(value);
	}

	/**
	 * @param value Must be Boolean.TRUE or Boolean.FALSE. No new Boolean(true/false)!
	 *              Must always set back to false before the thread stops, else performance will be worse.
	 *              Not going to break anything, but bad for performance.
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
		} else {
			do {
				int old = $.getIntVolatile(this, index);
				int next = old - 1;
				if ($.compareAndSwapInt(this, index, old, next)) {
					return oldValue;
				}
			} while (true);
		}
	}

	@Override
	public Boolean get() {
		// Not volatile, this is good. If we miss another thread's update, that just saves time.
		if (count == 0) {
			return Boolean.FALSE;
		}
		return super.get();
	}
}
