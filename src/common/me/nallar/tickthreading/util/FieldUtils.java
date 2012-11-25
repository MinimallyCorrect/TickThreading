package me.nallar.tickthreading.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FieldUtils {
	public static Field[] getFields(Class clazz, Class fieldType) {
		List<Field> listFields = new ArrayList<Field>();
		List<Field> fields = Arrays.asList(clazz.getDeclaredFields());
		for (Field field : fields) {
			if (fieldType.isAssignableFrom(field.getType())) {
				listFields.add(field);
			}
		}
		return listFields.toArray(new Field[listFields.size()]);
	}
}
