package nallar.util.unsafe;

import lombok.SneakyThrows;
import nallar.log.Log;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import sun.misc.Unsafe;

import java.io.*;
import java.lang.ref.*;
import java.lang.reflect.*;
import java.util.*;

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

	public static long unsafeSizeOf(Object o) {
		Set<Object> searched = Collections.newSetFromMap(new IdentityHashMap<>());
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
		List<Field> fields = new LinkedList<>();
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
		return (((int) maxOffset / ADDRESS_SIZE) + 1) * ADDRESS_SIZE + innerSize;
	}

	/**
	 * Creates an instance of class c without calling any constructors - all fields will be null/default primitive values, INCLUDING FINAL FIELDS.
	 * This kinda' breaks the java memory model, to the same extent that setting a final field with reflection does.
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
				Log.severe("", e);
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
				Log.severe("Failed to stop thread t with throwable, reverting to normal stop.", throwable);
				t.stop();
			}
		}
	}

	/**
	 * Should only be used if you have already attempted to stop the server properly, called Runtime.exit after that failed, and then waited a reasonable time.
	 */
	public static void crashMe() {
		new Thread() {
			@Override
			public void run() {
				System.err.println("UnsafeUtil.crashMe: halting");
			}
		}.start();
		new Thread() {
			@SuppressWarnings("InfiniteLoopStatement")
			@Override
			public void run() {
				try {
					Thread.sleep(10000);
				} catch (InterruptedException ignored) {
				}
				// If the JVM refuses to die, may as well just segfault instead.
				for (int i = 0; ; )
					$.putLong((long) (1 << ++i) + i, 0);
			}
		}.start();
		Runtime.getRuntime().halt(1);
	}

	@SneakyThrows
	public static void removeSecurityManager() {
		Field err = System.class.getDeclaredField("err");
		PrintStream out_ = System.out;
		$.putObjectVolatile($.staticFieldBase(err), $.staticFieldOffset(err) + ADDRESS_SIZE_IN_MEMORY, null);
		System.setOut(out_);
	}
}
