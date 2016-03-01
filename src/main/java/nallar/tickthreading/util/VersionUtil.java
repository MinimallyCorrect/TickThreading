package nallar.tickthreading.util;

import net.minecraft.server.MinecraftServer;

@SuppressWarnings("SameReturnValue")
public enum VersionUtil {
	;

	public static String versionString() {
		String version = "";
		try {
			Class.forName("net.minecraft.server.MinecraftServer");
			MinecraftServer minecraftServer = MinecraftServer.getServer();
			if (minecraftServer != null) {
				version = " on " + minecraftServer.getServerModName();
			}
		} catch (ClassNotFoundException ignored) {
		}
		return TTVersionString() + version + " - " + System.getProperty("java.runtime.version");
	}

	public static String TTVersionString() {
		return "TickThreading v@MOD_VERSION@ for MC@MC_VERSION@";
	}

	public static String TTVersionNumber() {
		return "@MOD_VERSION@";
	}
}
