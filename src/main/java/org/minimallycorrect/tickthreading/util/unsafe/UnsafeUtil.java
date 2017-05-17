package org.minimallycorrect.tickthreading.util.unsafe;

import lombok.SneakyThrows;
import org.minimallycorrect.tickthreading.log.Log;
import sun.misc.Unsafe;

import java.lang.reflect.*;

@SuppressWarnings({"unchecked", "rawtypes"})
public class UnsafeUtil {
	private static final Unsafe $ = UnsafeAccess.$;
	private static final long baseOffset = $.arrayBaseOffset(Object[].class);
	private static final long headerSize = baseOffset - 8;
	private static final int ADDRESS_SIZE = $.addressSize();
	private static final int ADDRESS_SIZE_IN_MEMORY = getAddressSizeInMemory();
	private static final int MIN_SIZE = 16;

	@SneakyThrows
	private static int getAddressSizeInMemory() {
		Field out = System.class.getDeclaredField("out");
		Field err = System.class.getDeclaredField("err");
		return (int) ($.staticFieldOffset(err) - $.staticFieldOffset(out));
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

	/**
	 * Creates an instance of class c without calling any constructors - all fields will be null/default primitive values, INCLUDING FINAL FIELDS.
	 * This breaks assumptions about final fields which may be made elsewhere.
	 *
	 * @param c Class to instantiate
	 * @return the instance of c
	 */
	@SneakyThrows
	public static <T> T createUninitialisedObject(Class<T> c) {
		return (T) $.allocateInstance(c);
	}

	public static String dump(Object a) {
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
				Log.error("", e);
			}
			secondOrLater = true;
		}
		return out.toString();
	}

	/**
	 * Sets all non-primitive/array fields of o to null. For use when you know some stupid mod/plugin is going to leak this object,
	 * and want to leak only the size of the object, not everything it references.
	 *
	 * @param o Object to clean.
	 */
	public static void clean(Object o) {
		//Log.info("Clearing " + o.getClass() + '@' + System.identityHashCode(o));
		Class c = o.getClass();
		while (c != null) {
			for (Field field : c.getDeclaredFields()) {
				if ((!field.getType().isArray() && field.getType().isPrimitive()) || (field.getModifiers() & Modifier.STATIC) == Modifier.STATIC) {
					continue;
				}
				try {
					field.setAccessible(true);
					field.set(o, null);
					//Log.info("Cleaned field " + field.getType().getName() + ':' + field.getName());
				} catch (IllegalAccessException e) {
					Log.warn("Exception cleaning " + o.getClass() + '@' + System.identityHashCode(o), e);
				}
			}
			c = c.getSuperclass();
		}
	}

	@SuppressWarnings("deprecation")
	public static void stopThread(Thread t, Throwable thr) {
		try {
			t.stop(thr);
		} catch (Throwable ignored) {
			try {
				Method m = Thread.class.getDeclaredMethod("stop0", Object.class);
				m.setAccessible(true);
				m.invoke(t, thr);
			} catch (Throwable throwable) {
				Log.error("Failed to stop thread t with throwable, reverting to normal stop.", throwable);
				t.stop();
			}
		}
	}

	@SneakyThrows
	public static void removeSecurityManager() {
		if (System.getSecurityManager() == null)
			return;

		Field err = System.class.getDeclaredField("err");
		$.putObjectVolatile($.staticFieldBase(err), $.staticFieldOffset(err) + ADDRESS_SIZE_IN_MEMORY, null);

		if (System.getSecurityManager() != null)
			Log.error("Failed to remove SecurityManager");
	}
}
