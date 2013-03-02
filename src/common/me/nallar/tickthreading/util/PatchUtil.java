package me.nallar.tickthreading.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.common.io.Files;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.patcher.PatchMain;
import me.nallar.tickthreading.patcher.PatchManager;
import me.nallar.tickthreading.patcher.Patches;

public enum PatchUtil {
	;
	private static boolean written = false;

	public static synchronized void writePatchRunners() throws IOException {
		if (written) {
			return;
		}
		written = true;
		String java = System.getProperties().getProperty("java.home") + File.separator + "bin" + File.separator + "java";
		String CP = LocationUtil.locationOf(TickThreading.class).toString();
		String MS = CollectionsUtil.join(LocationUtil.getJarLocations());

		ZipFile zipFile = new ZipFile(new File(CP));
		try {
			CP += File.pathSeparator + new File(LocationUtil.getServerDirectory(), "lib/guava-12.0.1.jar");
			for (ZipEntry zipEntry : new EnumerableWrapper<ZipEntry>((Enumeration<ZipEntry>) zipFile.entries())) {
				if (zipEntry.getName().startsWith("patchrun/") && !(!zipEntry.getName().isEmpty() && zipEntry.getName().charAt(zipEntry.getName().length() - 1) == '/')) {
					String data = new Scanner(zipFile.getInputStream(zipEntry), "UTF-8").useDelimiter("\\A").next();
					data = data.replace("%JAVA%", java).replace("%CP%", CP).replace("%MS%", MS).replace("\r\n", "\n");
					Files.write(data.getBytes("UTF-8"), new File(LocationUtil.getServerDirectory(), zipEntry.getName().replace("patchrun/", "")));
				}
			}
		} finally {
			zipFile.close();
		}
	}

	public static boolean shouldPatch(Iterable<File> files) {
		try {
			PatchManager patchManager = new PatchManager(PatchMain.class.getResourceAsStream("/patches.xml"), Patches.class);
			ArrayList<File> fileList = new ArrayList<File>();
			for (File file : files) {
				fileList.add(file.getAbsoluteFile());
			}
			patchManager.classRegistry.loadFiles(fileList);
			patchManager.classRegistry.closeClassPath();
			patchManager.classRegistry.loadPatchHashes(patchManager);
			boolean result = patchManager.classRegistry.shouldPatch();
			patchManager.classRegistry.clearClassInfo();
			return result;
		} catch (Exception e) {
			Log.severe("Failed to determine whether patches should run", e);
		}
		return false;
	}
}
