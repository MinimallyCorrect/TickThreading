package me.nallar.tickthreading.util;

import java.io.File;
import java.net.URL;

import me.nallar.tickthreading.Log;
import net.minecraft.server.MinecraftServer;

public enum LocationUtil {
	;

	public static File directoryOf(Class clazz) {
		File location = locationOf(clazz);
		if (location.isDirectory()) {
			return location;
		}
		return location.getParentFile();
	}

	public static File locationOf(Class clazz) {
		String path = clazz.getProtectionDomain().getCodeSource().getLocation().getPath();
		path = path.contains("!") ? path.substring(0, path.lastIndexOf('!')) : path;
		if (path.startsWith("/")) {
			path = "file:" + path;
		}
		try {
			return new File(new URL(path).toURI());
		} catch (Exception e) {
			Log.severe("", e);
			return new File(path);
		}
	}

	public static File getServerDirectory() {
		File jarPath = directoryOf(MinecraftServer.class);
		return MinecraftServer.getServer().isDedicatedServer() ? jarPath : jarPath.getParentFile();
	}
}
