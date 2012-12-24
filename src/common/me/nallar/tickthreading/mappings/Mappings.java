package me.nallar.tickthreading.mappings;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import me.nallar.tickthreading.Log;

public abstract class Mappings {
	@SuppressWarnings("unchecked")
	public List map(List things) {
		List mappedThings = new ArrayList();
		for (Object thing : things) {
			Object old = thing;
			if (thing instanceof MethodDescription) {
				mappedThings.add(thing = map((MethodDescription) thing));
			} else if (thing instanceof ClassDescription) {
				mappedThings.add(thing = map((ClassDescription) thing));
			} else {
				throw new IllegalArgumentException("Must be mappable: " + thing + "isn't!");
			}
			Log.info(old + " -> " + thing);
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
