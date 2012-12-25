package me.nallar.tickthreading.patcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javassist.ClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.util.EnumerableWrapper;

public class ClassRegistry {
	private Map<String, File> classNameToLocation = new HashMap<String, File>();
	private Set<File> updatedFiles = new HashSet<File>();
	private Set<ClassPath> classPathSet = new HashSet<ClassPath>();
	private Map<String, byte[]> replacementFiles = new HashMap<String, byte[]>();
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
			classPathSet.add(classes.appendClassPath(file.getAbsolutePath()));
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

	public void update(String className, byte[] replacement) {
		updatedFiles.add(classNameToLocation.get(className));
		className = className.replace('.', '/') + ".class";
		replacementFiles.put(className, replacement);
	}

	public void save() throws IOException {
		for (ClassPath classPath : classPathSet) {
			classes.removeClassPath(classPath);
		}
		byte[] buf = new byte[1024];
		int len;
		for (File zipFile : updatedFiles) {
			File tempFile = File.createTempFile(zipFile.getName(), null);
			tempFile.delete();
			zipFile.renameTo(tempFile);
			ZipInputStream zin = new ZipInputStream(new FileInputStream(tempFile));
			ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(zipFile));

			ZipEntry zipEntry;
			while ((zipEntry = zin.getNextEntry()) != null) {
				if (replacementFiles.containsKey(zipEntry.getName())) {
					byte[] replacement = replacementFiles.get(zipEntry.getName());
					ZipEntry newEntry = new ZipEntry(zipEntry.getName());
					zout.putNextEntry(newEntry);
					zout.write(replacement);
				} else {
					// TODO: Ignore meta-inf?
					zout.putNextEntry(zipEntry);
					while ((len = zin.read(buf)) > 0) {
						zout.write(buf, 0, len);
					}
				}
			}
			zin.close();
			zout.close();
			tempFile.delete();
		}
	}

	public CtClass getClass(String className) throws NotFoundException {
		return classes.get(className);
	}
}
