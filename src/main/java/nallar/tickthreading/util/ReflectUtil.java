package nallar.tickthreading.util;

import java.lang.reflect.*;

public enum ReflectUtil {
	;

	public static Field getField(Class<?> c, String name) {
		Field field = null;
		do {
			try {
				field = c.getDeclaredField(name);
			} catch (NoSuchFieldException ignored) {
			}
		} while (field == null && (c = c.getSuperclass()) != Object.class);
		if (field != null) {
			field.setAccessible(true);
		}
		return field;
	}
}
