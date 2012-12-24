package me.nallar.tickthreading.patcher;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import me.nallar.tickthreading.util.EnumerableWrapper;

public class ClassRegistry {
	private Map<String, File> classNameToLocation = new HashMap<String, File>();

	public void loadJars(File folder) throws IOException {
		if (!folder.isDirectory()) {
			throw new IllegalArgumentException(folder + " isn't a directory");
		}
		for (File file : folder.listFiles()) {
			if (file.getName().toLowerCase().endsWith(".jar")) {
				loadJar(new JarFile(file));
			}
		}
	}

	public void loadJar(JarFile jar) throws IOException {
		loadZip(jar);
		// TODO: Remove code signing?
	}

	public void loadZip(ZipFile zip) throws IOException {
		for (ZipEntry zipEntry : new EnumerableWrapper<ZipEntry>((Enumeration<ZipEntry>)zip.entries())) {
			String name = zipEntry.getName();
			if (name.endsWith(".class")) {
				classNameToLocation.put(name.replace('/', '.').substring(0, name.lastIndexOf('.')), new File(zip.getName()));
			}
		}
		zip.close();
	}
}
