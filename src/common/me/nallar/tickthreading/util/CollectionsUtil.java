package me.nallar.tickthreading.util;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import me.nallar.tickthreading.Log;

public enum CollectionsUtil {
	;
	private static final String defaultDelimiter = ",";

	public static List<String> split(String input) {
		return split(input, defaultDelimiter);
	}

	public static List<String> split(String input, String delimiter) {
		if (input == null || input.isEmpty()) {
			return Collections.emptyList();
		}
		return new ArrayList<String>(Arrays.asList(input.split(delimiter)));
	}

	public static List<?> toObjects(Iterable<String> stringIterable, Class type) {
		Constructor<?> constructor;
		try {
			constructor = type.getConstructor(String.class);
		} catch (NoSuchMethodException e) {
			Log.severe("Failed to convert string list to " + type, e);
			return Collections.emptyList();
		}
		List<Object> objects = new ArrayList<Object>();
		for (String s : stringIterable) {
			try {
				objects.add(constructor.newInstance(s));
			} catch (Exception e) {
				Log.severe("Failed to convert string list to " + type + " with string " + s, e);
			}
		}
		return objects;
	}

	public static String join(Iterable iterable) {
		return join(iterable, defaultDelimiter);
	}

	public static String join(Iterable iterable, String delimiter) {
		StringBuilder stringBuilder = new StringBuilder();
		boolean join = false;
		for (Object o : iterable) {
			if (join) {
				stringBuilder.append(delimiter);
			}
			stringBuilder.append(o);
			join = true;
		}
		return stringBuilder.toString();
	}

	public static Collection<String> stringify(Iterable objects, Collection<String> strings) {
		for (Object o : objects) {
			strings.add(o.toString());
		}
		return strings;
	}
}
