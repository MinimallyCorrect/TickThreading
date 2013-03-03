package me.nallar.tickthreading.util;

public enum VersionUtil {
	;

	@SuppressWarnings ("SameReturnValue")
	public static String versionString() {
		return "@MOD_NAME@ v@MOD_VERSION@ for MC@MC_VERSION@";
	}
}
