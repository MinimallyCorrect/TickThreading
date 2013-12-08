package nallar.unsafe;

import javassist.Modifier;
import nallar.tickthreading.Log;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import sun.misc.Unsafe;

import java.lang.ref.*;
import java.lang.reflect.*;
import java.util.*;

public class UnsafeUtil {
	private static final Unsafe $ = UnsafeAccess.$;
	private static final long baseOffset = $.arrayBaseOffset(Object[].class);
	private static final long headerSize = baseOffset - 8;

	/**
	 * Smalltalk's `become` for java.
	 */
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

	private static final int NR_BITS = Integer.valueOf(System.getProperty("sun.arch.data.model"));
	private static final int BYTE = 8;
	private static final int WORD = NR_BITS / BYTE;
	private static final int MIN_SIZE = 16;

	public static long unsafeSizeOf(Object o) {
		Set<Object> searched = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
		searched.add(MinecraftServer.getServer());
		searched.add(o);
		long size = unsafeSizeOf(o, searched, 0);
		searched.clear();
		return size;
	}

	private static boolean canSearch(Object o) {
		return o != null && !(o instanceof Reference || o instanceof World);
	}

	public static long unsafeSizeOf(Object o, Set<Object> searched, int depth) {
		Class<?> cls = o.getClass();
		if (cls.isArray()) {
			long size = $.arrayBaseOffset(cls) + $.arrayIndexScale(cls) * Array.getLength(o);
			if (!cls.getComponentType().isPrimitive() && depth < 20) {
				Object[] a = (Object[]) o;
				for (Object in : a) {
					if (canSearch(in) && searched.add(in)) {
						size += unsafeSizeOf(in, searched, depth + 1);
					}
				}
			}
			return size;
		}
		List<Field> fields = new LinkedList<Field>();
		while (cls != Object.class && cls != null) {
			try {
				for (Field f : cls.getDeclaredFields()) {
					if ((f.getModifiers() & Modifier.STATIC) != Modifier.STATIC) {
						fields.add(f);
					}
				}
			} catch (NoClassDefFoundError ignored) {
				// The class has fields of types which don't exist. We can't do anything about this easily :(
			}
			cls = cls.getSuperclass();
		}

		long maxOffset = MIN_SIZE;
		long innerSize = 0;
		for (Field f : fields) {
			long offset = $.objectFieldOffset(f);
			if (offset > maxOffset) {
				maxOffset = offset;
			}
			if (!f.getType().isPrimitive() && depth < 20) {
				Object in = $.getObject(o, offset);
				if (canSearch(in) && searched.add(in)) {
					innerSize += unsafeSizeOf(in, searched, depth + 1);
				}
			}
		}
		return (((int) maxOffset / WORD) + 1) * WORD + innerSize;
	}

	public static long sizeOf(Object o) {
		return sizeOf(o.getClass());
	}

	/**
	 * *Very* quickly compares two arrays.
	 */
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

	/**
	 * Creates an instance of class c without calling any constructors - all fields will be null/default primitive values, INCLUDING FINAL FIELDS.
	 * This kinda' breaks the java memory model.
	 *
	 * @param c Class to instantiate
	 * @return the instance of c
	 */
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
					Log.warning("Exception cleaning " + o.getClass() + '@' + System.identityHashCode(o), e);
				}
			}
			c = c.getSuperclass();
		}
	}

	public static RuntimeException throwIgnoreChecked(Throwable t) {
		throw UnsafeUtil.<RuntimeException>throwIgnoreCheckedErasure(t);
	}

	private static <T extends Throwable> T throwIgnoreCheckedErasure(Throwable toThrow) throws T {
		throw (T) toThrow;
	}

	/**
	 * Should only be used if you have already attempted to stop the server properly, called Runtime.exit after that failed, and then waited a reasonable time.
	 */
	public static void crashMe() {
		new Thread() {
			@Override
			public void run() {
				try {
					Thread.sleep(10000);
				} catch (InterruptedException ignored) {
				}
				// If the JVM refuses to die, may as well just segfault instead.
				$.putLong(addressOf(new Object()) << 2, 0);
				$.putLong(addressOf(new Object()) << 3, 0);
				$.putLong(addressOf(new Object()) << 4, 0);
				$.putLong(addressOf(new Object()) << 5, 0);
				$.putLong(addressOf(new Object()) << 6, 0);
			}
		}.start();
		Runtime.getRuntime().halt(1);
	}
}
