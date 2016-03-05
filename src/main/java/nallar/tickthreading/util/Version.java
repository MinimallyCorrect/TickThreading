package nallar.tickthreading.util;

import lombok.experimental.UtilityClass;
import net.minecraft.server.MinecraftServer;

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
			Class.forName("nallar.tickthreading.mixin.extended.package-info");
		} catch (ClassNotFoundException e) {
			return false;
		}

		return true;
	}

	public static String getFullDescription() {
		String version = "";
		try {
			// TODO: Don't try this if too early in CoreMod loading
			Class.forName("net.minecraft.server.MinecraftServer");
			MinecraftServer minecraftServer = MinecraftServer.getServer();
			if (minecraftServer != null) {
				version = " on " + minecraftServer.getServerModName();
			}
		} catch (ClassNotFoundException ignored) {
		}
		return DESCRIPTION + version + " - " + System.getProperty("java.runtime.version");
	}
}
