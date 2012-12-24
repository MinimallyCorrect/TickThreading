package me.nallar.tickthreading.mappings;

public class ClassDescription {
	public final String name;

	public ClassDescription(String className) {
		name = className;
	}

	public ClassDescription(Class clazz) {
		this(clazz.getCanonicalName());
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public boolean equals(Object other) {
		return other == this || (other instanceof ClassDescription && ((ClassDescription) other).name.equals(this.name)) || (other instanceof Class<?> && new ClassDescription((Class) other).equals(this));
	}
}
