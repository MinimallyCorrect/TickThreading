package nallar.tickthreading.mappings;

import java.util.*;

public abstract class Mappings {
	@SuppressWarnings("unchecked")
	public List<?> map(List<?> things) {
		List mappedThings = new ArrayList();
		for (Object thing : things) {
			if (thing instanceof MethodDescription) {
				mappedThings.add(map((MethodDescription) thing));
			} else if (thing instanceof ClassDescription) {
				mappedThings.add(map((ClassDescription) thing));
			} else if (thing instanceof FieldDescription) {
				mappedThings.add(map((FieldDescription) thing));
			} else {
				throw new IllegalArgumentException("Must be mappable: " + thing + "isn't!");
			}
		}
		return mappedThings;
	}

	public abstract MethodDescription map(MethodDescription methodDescription);

	public abstract ClassDescription map(ClassDescription classDescription);

	public abstract FieldDescription map(FieldDescription fieldDescription);

	public abstract MethodDescription rmap(MethodDescription methodDescription);

	public abstract String obfuscate(String code);

	public abstract String shortClassNameToFullClassName(String shortName);
}
