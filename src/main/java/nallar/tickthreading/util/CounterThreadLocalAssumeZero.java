package nallar.tickthreading.util;

import nallar.unsafe.UnsafeAccess;
import sun.misc.Unsafe;

/**
 * Accessing a field is much faster than a normal ThreadLocal get.
 * Here, we maintain a field which counts the number of threads in which the variable is true
 * In most cases, this allows us to avoid the performance hit of a hashmap lookup in
 * ThreadLocal.get(), assuming the variable is normally false in all threads.
 */
public class CounterThreadLocalAssumeZero extends ThreadLocal<Integer> {
	@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
	private int count = 0;
	private static final Unsafe $ = UnsafeAccess.$;
	private static final long index = $.objectFieldOffset(ReflectUtil.getField(CounterThreadLocalAssumeZero.class, "count"));
	// Unsafe magics.

	@Override
	public Integer initialValue() {
		return 0;
	}

	@Override
	public Integer get() {
		throw new UnsupportedOperationException();
	}

	public int getCount() {
		if (count == 0) {
			return 0;
		}
		return super.get();
	}

	@Override
	public void set(Integer value) {
		throw new UnsupportedOperationException();
	}

	public int increment() {
		int count = getCount();
		int n = count + 1;
		super.set(n);
		if (count == 0) {
			do {
				int old = $.getIntVolatile(this, index);
				int next = old + 1;
				if ($.compareAndSwapInt(this, index, old, next)) {
					return 1;
				}
			} while (true);
		}
		return n;
	}

	public int decrement() {
		int count = getCount();
		if (count == 0) {
			throw new Error("Can not decrement counter below 0.");
		}
		int n = count - 1;
		super.set(n);
		if (count == 1) {
			do {
				int old = $.getIntVolatile(this, index);
				int next = old - 1;
				if ($.compareAndSwapInt(this, index, old, next)) {
					return 0;
				}
			} while (true);
		}
		return n;
	}
}
