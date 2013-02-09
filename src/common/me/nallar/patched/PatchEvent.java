package me.nallar.patched;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraftforge.event.Cancelable;
import net.minecraftforge.event.Event;

public abstract class PatchEvent extends Event {
	private static Map<Class, Boolean> annotationMap; // Class -> boolean instead of Class -> (Class -> boolean) because forge ignores annotation type

	public static void staticConstruct() {
		annotationMap = new ConcurrentHashMap<Class, Boolean>();
	}

	@Override
	protected boolean hasAnnotation(Class annotation) {
		return hasAnnotation(annotation, this.getClass());
	}

	private static boolean hasAnnotation(Class annotation, Class cls) {
		Boolean cachedResult = annotationMap.get(cls);
		if (cachedResult != null) {
			return cachedResult;
		}
		while (cls != Event.class) {
			if (cls.isAnnotationPresent(Cancelable.class)) // Forge bug, not fixed. Buggy behaviour may be required for some mods.
			{
				annotationMap.put(cls, true);
				return true;
			}
			cls = cls.getSuperclass();
		}
		annotationMap.put(cls, false);
		return false;
	}
}
