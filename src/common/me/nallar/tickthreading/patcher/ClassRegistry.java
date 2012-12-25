package me.nallar.tickthreading.patcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
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
	private static final byte[] BUFFER = new byte[1024 * 1024];
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

	public static void copy(InputStream input, OutputStream output) throws IOException {
		for (int read = input.read(BUFFER); read > -1; read = input.read(BUFFER)) {
			output.write(BUFFER, 0, read);
		}
	}

	public void save() throws IOException {
		for (ClassPath classPath : classPathSet) {
			classes.removeClassPath(classPath);
		}
		File tempFile = null, renameFile = null;
		ZipInputStream zin = null;
		ZipOutputStream zout = null;
		try {
			for (File zipFile : updatedFiles) {
				tempFile = File.createTempFile(zipFile.getName(), null);
				tempFile.delete();
				if (zipFile.renameTo(tempFile)) {
					renameFile = zipFile;
				} else {
					throw new IOException("Couldn't rename " + zipFile + " -> " + tempFile);
				}
				if (isJar(zipFile)) {
					zin = new JarInputStream(new FileInputStream(tempFile));
					zout = new JarOutputStream(new FileOutputStream(zipFile));
				} else {
					zin = new ZipInputStream(new FileInputStream(tempFile));
					zout = new ZipOutputStream(new FileOutputStream(zipFile));
				}
				Set<String> replacements = new HashSet<String>();
				ZipEntry zipEntry;
				while ((zipEntry = zin.getNextEntry()) != null) {
					if (replacementFiles.containsKey(zipEntry.getName())) {
						replacements.add(zipEntry.getName());
					} else {
						// TODO: Ignore meta-inf?
						zout.putNextEntry(isJar(zipFile) ? new JarEntry(zipEntry.getName()) : new ZipEntry(zipEntry.getName()));
						copy(zin, zout);
					}
				}
				for (String name : replacements) {
					zout.putNextEntry(isJar(zipFile) ? new JarEntry(name) : new ZipEntry(name));
					zout.write(replacementFiles.get(name));
					zout.closeEntry();
				}
				Log.info("Patched " + replacements.size() + " classes in " + zipFile.getName());
				zin.close();
				zout.close();
				tempFile.delete();
				renameFile = null;
			}
		} catch (ZipException e) {
			zin.close();
			zout.close();
			if (renameFile != null && tempFile != null) {
				tempFile.renameTo(renameFile);
			}
			throw e;
		}
	}

	private static boolean isJar(File file) {
		return file.getName().toLowerCase().endsWith(".jar");
	}

	public CtClass getClass(String className) throws NotFoundException {
		return classes.get(className);
	}
}
