package me.nallar.unsafe;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

import javassist.Modifier;
import me.nallar.tickthreading.Log;
import sun.misc.Unsafe;

public class UnsafeUtil {
	private static final Unsafe $ = UnsafeAccess.$;
	private static final long baseOffset = $.arrayBaseOffset(Object[].class);
	private static final long headerSize = baseOffset - 8;

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
		long temporaryInstanceLocation2 = $.allocateMemory(sizeA);
		$.copyMemory(a, 0, null, temporaryInstanceLocation, sizeA);
		$.copyMemory(b, 0, null, temporaryInstanceLocation2, sizeA);
		copyMemory(temporaryInstanceLocation, b, sizeA);
		copyMemory(temporaryInstanceLocation2, a, sizeA);
		$.freeMemory(temporaryInstanceLocation);
		$.freeMemory(temporaryInstanceLocation2);
	}

	private static void copyMemory(long src, Object dest, long size) {
		long sSize = size;
		while (size > 0) {
			if (size >= 8) {
				$.putLong(dest, sSize - size, $.getLong(null, src));
				size -= 8;
				src += 8;
			} else if (size >= 4) {
				$.putInt(dest, sSize - size, $.getInt(null, src));
				size -= 4;
				src += 4;
			} else if (size >= 2) {
				$.putShort(dest, sSize - size, $.getShort(null, src));
				size -= 2;
				src += 2;
			} else {
				$.putByte(dest, sSize - size, $.getByte(null, src));
				size--;
				src++;
			}
		}
	}

	public static long sizeOf(Class<?> clazz) {
		if (clazz.equals(byte.class) || clazz.equals(char.class)) {
			return 1L;
		} else if (clazz.equals(short.class)) {
			return 2L;
		} else if (clazz.equals(int.class) || clazz.equals(float.class)) {
			return 4L;
		} else if (clazz.equals(long.class) || clazz.equals(double.class)) {
			return 8L;
		} else {
			Field[] fields = clazz.getDeclaredFields();
			long sz = 0;
			for (Field f : fields) {
				if (f.getType().isPrimitive()) {
					sz += sizeOf(f.getType());
				} else {
					sz += 4; //ptr
				}
			}
			sz += headerSize;
			return sz;
		}
	}

	public static long sizeOf(Object o) {
		return sizeOf(o.getClass());
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

	public static Object fromAddress(long address) {
		Pointer p = new Pointer();
		p.setAddress(address);
		return p.o;
	}

	public static long addressOf(Object o) {
		Pointer p = new Pointer();
		p.o = o;
		return p.getAddress();
	}

	public static <T> T createUninitialisedObject(Class<T> c) {
		try {
			return (T) $.allocateInstance(c);
		} catch (Exception e) {
			throw new Error(e);
		}
	}

	public static String info(Object o) {
		return "size: " + sizeOf(o) + ", address: " + addressOf(o) + ", class: " + o.getClass().getSimpleName();
	}

	public static String debugOut(Object a) {
		Class<?> c = a.getClass();
		StringBuilder out = new StringBuilder();
		boolean secondOrLater = false;
		for (Field f : c.getDeclaredFields()) {
			if ((f.getModifiers() & Modifier.STATIC) == Modifier.STATIC) {
				continue;
			}
			f.setAccessible(true);
			try {
				if (secondOrLater) {
					out.append('\n');
				}
				out
						.append(f.getName())
						.append(": ")
						.append(f.getType().getName())
						.append("; ");
				Object value = f.get(a);
				Class vC = value.getClass();
				if (vC.isArray()) {
					if (char[].class.equals(vC)) {
						int s = Array.getLength(value);
						for (int i = 0; i < s; i++) {
							out.append(Array.getChar(value, i));
						}
					} else {
						int s = Array.getLength(value);
						for (int i = 0; i < s; i++) {
							out.append(Array.get(value, i)).append(',');
						}
					}
				} else {
					out.append(value);
				}
			} catch (IllegalAccessException e) {
				Log.severe("", e);
			}
			secondOrLater = true;
		}
		return out.toString();
	}

	public static String compare(Object a, Object b) {
		StringBuilder sb = new StringBuilder();
		sb.append("a=b: ").append(a.equals(b)).append('\n');
		sb.append("a: ").append(debugOut(a)).append('\n');
		sb.append("b: ").append(debugOut(b)).append('\n');
		sb.append('a').append(UnsafeUtil.info(a)).append('\n');
		sb.append('b').append(UnsafeUtil.info(b)).append('\n');
		return sb.toString();
	}

	public static void main(String[] args) {
		for (int i = 0; i < 16; i++) {
			Log.info(String.valueOf($.getInt(new Long[1], (long) i)));
		}
		Log.info(String.valueOf($.getInt(new Long[1], 12L)));
		//Log.info(String.valueOf(baseOffset));
		String a = "String 1";
		String b = "String 2";
		Log.info(compare(a, b));
		UnsafeUtil.swap(a, b);
		Log.info(compare(a, b));
	}

	public static void clean(Object o) {
		//Log.info("Clearing " + o.getClass() + '@' + System.identityHashCode(o));
		Class c = o.getClass();
		while (c != null) {
			for (Field field : c.getDeclaredFields()) {
				if (field.getType().isPrimitive() || (field.getModifiers() & Modifier.STATIC) == Modifier.STATIC) {
					continue;
				}
				try {
					field.setAccessible(true);
					field.set(o, null);
					//Log.info("Cleaned field " + MappingUtil.debobfuscate(field.getType().getName()) + ':' + field.getName());
				} catch (IllegalAccessException e) {
					Log.warning("Exception cleaning " + o.getClass() + '@' + System.identityHashCode(o), e);
				}
			}
			c = c.getSuperclass();
		}
	}

	public static void throwIgnoreChecked(Throwable t) {
		$.throwException(t);
	}
}
