package nallar.unsafe;

import sun.misc.Unsafe;

public class Pointer {
	public Object o;
	private static final Unsafe $ = UnsafeAccess.$;
	private static final long offset = getOffset();
	private static final int addressSize = $.addressSize();

	private static long getOffset() {
		try {
			return $.objectFieldOffset(Pointer.class.getField("o"));
		} catch (NoSuchFieldException e) {
			throw new UnsupportedOperationException("Unsupported JVM - can't get field offset", e);
		}
	}

	public void setAddress(long a) {
		if (addressSize == 4) {
			// TODO: denormalize
			$.putInt(this, offset, (int) a);
		} else {
			$.putLong(this, offset, a);
		}
	}

	public long getAddress() {
		if (addressSize == 4) {
			return normalize($.getInt(this, offset));
		} else {
			return $.getLong(this, offset);
		}
	}

	private static long normalize(int value) {
		return (value >= 0) ? value : (~0L >>> 32) & value;
	}
}
