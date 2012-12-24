package me.nallar.tickthreading.util;

import java.io.File;

public class LocationUtil {
	public static File directoryOf(Class clazz) {
		File location = locationOf(clazz);
		if (location.isDirectory()) {
			return location;
		}
		return location.getParentFile();
	}

	public static File locationOf(Class clazz) {
		return new File(clazz.getProtectionDomain().getCodeSource().getLocation().getPath());
	}
}
