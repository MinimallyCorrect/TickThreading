package nallar.tickthreading.util;

import nallar.tickthreading.Log;

import java.io.*;
import java.net.*;
import java.util.regex.*;

public enum LocationUtil {
	;
	private static final File currentDir = new File(".").getAbsoluteFile();

	private static String normalize(String path, String separator) {
		path = path.replace(separator + separator, separator);
		path = path.replace(separator + separator, separator);
		if (path.endsWith(separator)) {
			path = path.substring(0, path.length() - 1);
		}
		return path;
	}

	// Derived from http://stackoverflow.com/a/3054692/250076
	public static File getRelativeFile(File targetFile, File baseFile) {
		try {
			targetFile = targetFile.getCanonicalFile();
			baseFile = baseFile.getCanonicalFile();
		} catch (IOException ignored) {
		}
		String pathSeparator = File.separator;
		String basePath = baseFile.getAbsolutePath();
		String normalizedTargetPath = normalize(targetFile.getAbsolutePath(), pathSeparator);
		String normalizedBasePath = normalize(basePath, pathSeparator);

		String[] base = normalizedBasePath.split(Pattern.quote(pathSeparator));
		String[] target = normalizedTargetPath.split(Pattern.quote(pathSeparator));

		StringBuilder common = new StringBuilder();

		int commonIndex = 0;
		while (commonIndex < target.length && commonIndex < base.length && target[commonIndex].equals(base[commonIndex])) {
			common.append(target[commonIndex]).append(pathSeparator);
			commonIndex++;
		}

		if (commonIndex == 0) {
			throw new Error("No common path element found for '" + normalizedTargetPath + "' and '" + normalizedBasePath + '\'');
		}

		boolean baseIsFile = true;

		if (baseFile.exists()) {
			baseIsFile = baseFile.isFile();
		} else if (basePath.endsWith(pathSeparator)) {
			baseIsFile = false;
		}

		StringBuilder relative = new StringBuilder();

		if (base.length != commonIndex) {
			int numDirsUp = baseIsFile ? base.length - commonIndex - 1 : base.length - commonIndex;

			for (int i = 0; i < numDirsUp; i++) {
				relative.append("..").append(pathSeparator);
			}
		}
		relative.append(normalizedTargetPath.substring(common.length()));
		return new File(relative.toString());
	}

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
		if (!path.isEmpty() && path.charAt(0) == '/') {
			path = "file:" + path;
		}
		try {
			return getRelativeFile(new File(new URL(path).toURI()), currentDir);
		} catch (Exception e) {
			Log.severe("", e);
			return getRelativeFile(new File(path), currentDir);
		}
	}
<<<<<<< HEAD

	public static File getServerDirectory() {
		return directoryOf(PatchMain.class).getParentFile();
	}

	private static File getModsDirectory() {
		return new File(getServerDirectory(), "mods");
	}

	private static File getPluginsDirectory() {
		return new File(getServerDirectory(), "plugins");
	}

	public static List<File> getJarLocations() {
		List<File> jarLocations = new ArrayList<File>();
		jarLocations.addAll(getForgeJarLocations());
		jarLocations.add(getModsDirectory());
		jarLocations.add(getPluginsDirectory());
		return jarLocations;
	}

	public static List<File> getForgeJarLocations() {
		List<File> jarLocations = new ArrayList<File>();
		File forgeJar = locationOf(net.minecraftforge.common.ForgeDummyContainer.class);
		// Tuple = class not likely to be modified by forge
		// Minecraft and forge aren't necessarily in the same place
		File minecraftJar = locationOf(net.minecraft.util.Tuple.class);
		jarLocations.add(minecraftJar);
		if (!minecraftJar.equals(forgeJar)) {
			jarLocations.add(forgeJar);
		}
		return jarLocations;
	}
=======
>>>>>>> upstream/master
}
