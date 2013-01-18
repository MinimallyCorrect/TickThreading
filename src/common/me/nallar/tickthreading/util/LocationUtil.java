package me.nallar.tickthreading.util;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.patcher.PatchMain;

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
		if (path.length() > 0 && path.charAt(0) == '/') {
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
		return directoryOf(PatchMain.class).getParentFile();
	}

	public static File getCoreModsDirectory() {
		return new File(getServerDirectory(), "coremods");
	}

	public static File getModsDirectory() {
		return new File(getServerDirectory(), "mods");
	}

	public static List<File> getJarLocations() {
		List<File> jarLocations = new ArrayList<File>();
		File forgeJar = locationOf(net.minecraft.server.MinecraftServer.class);
		// Tuple = class not likely to be modified by forge
		// Minecraft and forge aren't necessarily in the same place
		File minecraftJar = locationOf(net.minecraft.util.Tuple.class);
		if (!minecraftJar.equals(forgeJar)) {
			jarLocations.add(forgeJar.getParentFile());
		}
		jarLocations.add(minecraftJar);
		jarLocations.add(getCoreModsDirectory());
		jarLocations.add(getModsDirectory());
		return jarLocations;
	}
}
