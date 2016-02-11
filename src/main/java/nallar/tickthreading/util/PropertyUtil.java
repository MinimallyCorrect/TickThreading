package nallar.tickthreading.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PropertyUtil {
	private static final String PROPERTY_PREFIX = "tt.";

	public static boolean get(String name, boolean default_) {
		return Boolean.parseBoolean(System.getProperty(PROPERTY_PREFIX + name, String.valueOf(default_)));
	}
}
