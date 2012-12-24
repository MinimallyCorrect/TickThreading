package me.nallar.tickthreading.patcher;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.util.EnumerableWrapper;

public class ClassRegistry {
	private Map<String, File> classNameToLocation = new HashMap<String, File>();
	private ClassPool classes = new ClassPool(false);

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
		File file = new File(zip.getName());
		try {
			classes.appendClassPath(file.getAbsolutePath());
		} catch (Exception e) {
			Log.severe("Javassist could not load " + file, e);
		}
		for (ZipEntry zipEntry : new EnumerableWrapper<ZipEntry>((Enumeration<ZipEntry>) zip.entries())) {
			String name = zipEntry.getName();
			if (name.endsWith(".class")) {
				classNameToLocation.put(name.replace('/', '.').substring(0, name.lastIndexOf('.')), file);
			}
		}
		zip.close();
	}

	public CtClass getClass(String className) throws NotFoundException {
		return classes.get(className);
	}
}
