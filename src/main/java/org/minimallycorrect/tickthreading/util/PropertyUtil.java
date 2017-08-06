package org.minimallycorrect.tickthreading.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PropertyUtil {
	private static final String PROPERTY_PREFIX = "tt.";

	public static boolean get(String name, boolean default_) {
		return Boolean.parseBoolean(System.getProperty(PROPERTY_PREFIX + name, String.valueOf(default_)));
	}

	public static int get(String name, int default_) {
		return Integer.parseInt(System.getProperty(PROPERTY_PREFIX + name, String.valueOf(default_)));
	}
}
