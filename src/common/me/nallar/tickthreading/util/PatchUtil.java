package me.nallar.tickthreading.util;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.common.io.Files;

import me.nallar.tickthreading.minecraft.TickThreading;

public enum PatchUtil {
	;

	public static void writePatchRunners() throws IOException {
		String java = System.getProperties().getProperty("java.home") + File.separator + "bin" + File.separator + "java";
		String CP = LocationUtil.locationOf(TickThreading.class).getAbsolutePath();
		String MS = CollectionsUtil.join(LocationUtil.getJarLocations());

		ZipFile zipFile = new ZipFile(new File(CP));
		try {
			CP += File.pathSeparator + new File(LocationUtil.getServerDirectory(), "lib/guava-12.0.1.jar").getAbsolutePath();
			for (ZipEntry zipEntry : new EnumerableWrapper<ZipEntry>((Enumeration<ZipEntry>) zipFile.entries())) {
				if (zipEntry.getName().startsWith("patchrun/") && !(zipEntry.getName().length() > 0 && zipEntry.getName().charAt(zipEntry.getName().length() - 1) == '/')) {
					String data = new Scanner(zipFile.getInputStream(zipEntry), "UTF-8").useDelimiter("\\A").next();
					data = data.replace("%JAVA%", java).replace("%CP%", CP).replace("%MS%", MS).replace("\r\n", "\n");
					Files.write(data.getBytes("UTF-8"), new File(LocationUtil.getServerDirectory(), zipEntry.getName().replace("patchrun/", "")));
				}
			}
		} finally {
			zipFile.close();
		}
	}
}
