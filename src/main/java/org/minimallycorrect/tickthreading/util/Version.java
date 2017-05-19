package org.minimallycorrect.tickthreading.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Version {
	public static final boolean EXTENDED = isExtended();
	public static final String RELEASE = getRelease();
	public static final String DESCRIPTION = "TickThreading v@MOD_VERSION@ for MC@MC_VERSION@";
	public static final String VERSION = "@MOD_VERSION@";

	private static String getRelease() {
		return EXTENDED ? "extended" : "core";
	}

	private static boolean isExtended() {
		try {
			Class.forName("org.minimallycorrect.tickthreading.mixin.extended.package-info");
		} catch (ClassNotFoundException e) {
			return false;
		}

		return true;
	}
}
