package nallar.util;

import net.minecraft.server.MinecraftServer;

public enum VersionUtil {
	;

	public static String versionString() {
		String version = "";
		try {
			MinecraftServer minecraftServer = MinecraftServer.getServer();
			if (minecraftServer != null) {
				version = " on " + minecraftServer.getMinecraftVersion() + ' ' + minecraftServer.getServerModName();
			}
		} catch (NoClassDefFoundError ignored) {
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
