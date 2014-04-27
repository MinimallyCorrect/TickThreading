package nallar.patched.forge;

import nallar.collections.PartiallySynchronizedMap;
import net.minecraftforge.event.Cancelable;
import net.minecraftforge.event.Event;

public abstract class PatchEvent extends Event {
	private static final PartiallySynchronizedMap<Class, Boolean> annotationMap = new PartiallySynchronizedMap<Class, Boolean>();

	@Override
	protected boolean hasAnnotation(Class annotation) {
		Class cls = this.getClass();
		Boolean cachedResult = annotationMap.get(cls);
		if (cachedResult != null) {
			return cachedResult;
		}
		Class searchClass = cls;
		while (searchClass != Event.class) {
			if (searchClass.isAnnotationPresent(Cancelable.class)) // TODO: Forge bug, not fixed. Buggy behaviour may be required for some mods.
			{
				annotationMap.put(cls, true);
				return true;
			}
			searchClass = searchClass.getSuperclass();
		}
		annotationMap.put(cls, false);
		return false;
	}
}
