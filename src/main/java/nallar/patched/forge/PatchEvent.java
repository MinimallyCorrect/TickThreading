package nallar.patched.forge;

import nallar.collections.PartiallySynchronizedMap;
import net.minecraftforge.event.Event;

public abstract class PatchEvent extends Event {
	private static final PartiallySynchronizedMap<Class, PartiallySynchronizedMap<Class, Boolean>> annotationMap = new PartiallySynchronizedMap<Class, PartiallySynchronizedMap<Class, Boolean>>();

	@Override
	protected boolean hasAnnotation(Class annotation) {
		Class cls = this.getClass();
		PartiallySynchronizedMap<Class, Boolean> map = annotationMap.get(cls);
		if (map == null) {
			map = new PartiallySynchronizedMap<Class, Boolean>();
			annotationMap.put(cls, map);
		}
		Boolean cachedResult = map.get(annotation);
		if (cachedResult != null) {
			return cachedResult;
		}
		Class searchClass = cls;
		while (searchClass != Event.class) {
			if (searchClass.isAnnotationPresent(annotation)) {
				map.put(annotation, true);
				return true;
			}
			searchClass = searchClass.getSuperclass();
		}
		map.put(annotation, false);
		return false;
	}
}
