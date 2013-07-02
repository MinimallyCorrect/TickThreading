package nallar.tickthreading.util;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Function;

import nallar.tickthreading.Log;

public enum CollectionsUtil {
	;
	private static final String defaultDelimiter = ",";

	public static List<String> split(String input) {
		return split(input, defaultDelimiter);
	}

	public static List<? extends Object> newList(List<?> input, Function<Object, ?> function) {
		List<Object> newList = new ArrayList<Object>(input.size());
		for (Object o : input) {
			newList.add(function.apply(o));
		}
		return newList;
	}

	private static List<String> split(String input, String delimiter) {
		if (input == null || input.isEmpty()) {
			return Collections.emptyList();
		}
		return new ArrayList<String>(Arrays.asList(input.split(delimiter)));
	}

	public static <T> List<T> toObjects(Iterable<String> stringIterable, Class<T> type) {
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
		return (List<T>) objects;
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

	public static String joinMap(Map<?, ?> map) {
		if (map.isEmpty()) {
			return "";
		}
		StringBuilder stringBuilder = new StringBuilder();
		boolean notFirst = false;
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (notFirst) {
				stringBuilder.append(',');
			}
			stringBuilder.append(entry.getKey().toString()).append(':').append(entry.getValue().toString());
			notFirst = true;
		}
		return stringBuilder.toString();
	}

	@SuppressWarnings ("unchecked")
	public static <K, V> Map<K, V> map(Object... objects) {
		HashMap map = new HashMap();
		Object key = null;
		for (final Object object : objects) {
			if (key == null) {
				key = object;
			} else {
				map.put(key, object);
				key = null;
			}
		}
		return map;
	}
}
