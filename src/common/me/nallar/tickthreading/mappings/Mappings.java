package me.nallar.tickthreading.mappings;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public abstract class Mappings {
	@SuppressWarnings ("unchecked")
	public List map(List things) {
		List mappedThings = new ArrayList();
		for (Object thing : things) {
			if (thing instanceof MethodDescription) {
				mappedThings.add(map((MethodDescription) thing));
			} else if (thing instanceof ClassDescription) {
				mappedThings.add(map((ClassDescription) thing));
			} else {
				throw new IllegalArgumentException("Must be mappable: " + thing + "isn't!");
			}
		}
		return mappedThings;
	}

	public MethodDescription map(Method method) {
		return map(new MethodDescription(method));
	}

	public abstract MethodDescription map(MethodDescription methodDescription);

	public ClassDescription map(Class clazz) {
		return map(new ClassDescription(clazz));
	}

	public abstract ClassDescription map(ClassDescription classDescription);
}
