package me.nallar.tickthreading.patcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
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

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import javassist.CannotCompileException;
import javassist.ClassPath;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.MethodInfo;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.util.EnumerableWrapper;

public class ClassRegistry {
	private static final String hashFileName = "TickThreading.hash";
	private final Map<String, File> classNameToLocation = new HashMap<String, File>();
	private final Map<File, Integer> locationToPatchHash = new HashMap<File, Integer>();
	private final Map<File, Integer> expectedPatchHashes = new HashMap<File, Integer>();
	private final Map<File, Map<String, byte[]>> additionalClasses = new HashMap<File, Map<String, byte[]>>();
	private final Set<File> updatedFiles = new HashSet<File>();
	private final Set<String> unsafeClassNames = new HashSet<String>();
	private final Set<ClassPath> classPathSet = new HashSet<ClassPath>();
	private final Map<String, byte[]> replacementFiles = new HashMap<String, byte[]>();
	public final ClassPool classes = new ClassPool(false);
	public boolean disableJavassistLoading = false;
	public boolean forcePatching = false;

	{
		MethodInfo.doPreverify = true;
		classes.appendSystemPath();
	}

	public void clearClassInfo() {
		finishModifications();
		classNameToLocation.clear();
		additionalClasses.clear();
		updatedFiles.clear();
		unsafeClassNames.clear();
		replacementFiles.clear();
	}

	public void loadFiles(Iterable<File> filesToLoad) throws IOException {
		for (File file : filesToLoad) {
			String extension = file.getName().toLowerCase();
			extension = extension.substring(extension.lastIndexOf('.') + 1);
			if (file.isDirectory()) {
				loadFiles(Arrays.asList(file.listFiles()));
			} else if (extension.equals("jar")) {
				loadJar(new JarFile(file));
			} else if (extension.equals("zip") || extension.equals("litemod")) {
				loadZip(new ZipFile(file));
			}
		}
	}

	public void loadJar(JarFile jar) throws IOException {
		loadZip(jar);
		// TODO: Remove code signing?
	}

	public ClassPath appendClassPatch(String path) throws NotFoundException {
		if (disableJavassistLoading) {
			return null;
		}
		ClassPath classPath = classes.appendClassPath(path);
		classPathSet.add(classPath);
		return classPath;
	}

	public void loadZip(ZipFile zip) throws IOException {
		File file = new File(zip.getName());
		try {
			appendClassPatch(file.getAbsolutePath());
		} catch (Exception e) {
			Log.severe("Javassist could not load " + file, e);
		}
		for (ZipEntry zipEntry : new EnumerableWrapper<ZipEntry>((Enumeration<ZipEntry>) zip.entries())) {
			String name = zipEntry.getName();
			if (name.endsWith(".class")) {
				String className = name.replace('/', '.').substring(0, name.lastIndexOf('.'));
				if (classNameToLocation.containsKey(className)) {
					unsafeClassNames.add(className);
				} else {
					classNameToLocation.put(className, file);
				}
			} else if (name.equals(hashFileName)) {
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				ByteStreams.copy(zip.getInputStream(zipEntry), output);
				int hash = Integer.valueOf(new String(output.toByteArray(), "UTF-8"));
				locationToPatchHash.put(file, hash);
			}
		}
		zip.close();
	}

	public void update(String className, byte[] replacement) {
		if (unsafeClassNames.contains(className)) {
			Log.severe(className + " is in multiple jars. Patching may not work correctly.");
		}
		updatedFiles.add(classNameToLocation.get(className));
		className = className.replace('.', '/') + ".class";
		replacementFiles.put(className, replacement);
	}

	public Map<String, byte[]> getAdditionalClasses(File file) {
		Map<String, byte[]> additionalClasses = this.additionalClasses.get(file);
		if (additionalClasses == null) {
			additionalClasses = new HashMap<String, byte[]>();
			this.additionalClasses.put(file, additionalClasses);
		}
		return additionalClasses;
	}

	public void add(Object requires, String additionalClassName) throws NotFoundException, IOException, CannotCompileException {
		if (additionalClassName.startsWith("java.")) {
			return;
		}
		String requiringClassName = null;
		if (requires instanceof CtBehavior) {
			requiringClassName = ((CtBehavior) requires).getDeclaringClass().getName();
		} else if (requires instanceof CtClass) {
			requiringClassName = ((CtClass) requires).getName();
		}
		if (requiringClassName == null) {
			Log.severe("Can't add " + additionalClassName + " as a class required by unknown type: " + requires.getClass().getCanonicalName());
			return;
		}
		File location = classNameToLocation.get(requiringClassName);
		add(location, additionalClassName, getClass(additionalClassName).toBytecode());
	}

	public void add(File file, String className, byte[] byteCode) {
		getAdditionalClasses(file).put(className.replace('.', '/') + ".class", byteCode);
	}

	public File getLocation(String className) {
		return classNameToLocation.get(className);
	}

	public void finishModifications() {
		for (ClassPath classPath : classPathSet) {
			classes.removeClassPath(classPath);
		}
		classPathSet.clear();
	}

	public void save(File backupDirectory) throws IOException {
		finishModifications();
		File tempFile = null, renameFile = null;
		ZipInputStream zin = null;
		ZipOutputStream zout = null;
		backupDirectory.mkdir();
		try {
			for (File zipFile : updatedFiles) {
				File backupFile = new File(backupDirectory, zipFile.getName());
				backupFile.delete();
				Files.copy(zipFile, backupFile);
				tempFile = File.createTempFile(zipFile.getName(), null);
				tempFile.delete();
				if (zipFile.renameTo(tempFile)) {
					renameFile = zipFile;
					tempFile.deleteOnExit();
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
				Map<String, byte[]> additionalClasses = getAdditionalClasses(zipFile);
				ZipEntry zipEntry;
				while ((zipEntry = zin.getNextEntry()) != null) {
					String entryName = zipEntry.getName();
					if (entryName.equals(hashFileName) || additionalClasses.containsKey(entryName) || (entryName.startsWith("META-INF") && !entryName.endsWith("MANIFEST.MF"))) {
						// Skip
					} else if (replacementFiles.containsKey(entryName)) {
						replacements.add(entryName);
					} else {
						// TODO: Ignore meta-inf?
						zout.putNextEntry(isJar(zipFile) ? new JarEntry(entryName) : new ZipEntry(entryName));
						ByteStreams.copy(zin, zout);
					}
				}
				for (String name : replacements) {
					zout.putNextEntry(isJar(zipFile) ? new JarEntry(name) : new ZipEntry(name));
					zout.write(replacementFiles.get(name));
					zout.closeEntry();
				}
				for (String name : additionalClasses.keySet()) {
					zout.putNextEntry(isJar(zipFile) ? new JarEntry(name) : new ZipEntry(name));
					zout.write(additionalClasses.get(name));
					zout.closeEntry();
				}
				zout.putNextEntry(isJar(zipFile) ? new JarEntry(hashFileName) : new ZipEntry(hashFileName));
				String patchHash = String.valueOf(expectedPatchHashes.get(zipFile));
				zout.write(patchHash.getBytes("UTF-8"));
				Log.info("Patched " + replacements.size() + " classes in " + zipFile.getName() + ", patchHash: " + patchHash);
				Log.info("Added " + additionalClasses.size() + " classes required by patches.");
				zin.close();
				zout.close();
				tempFile.delete();
				renameFile = null;
			}
		} catch (ZipException e) {
			if (zin != null) {
				zin.close();
			}
			if (zout != null) {
				zout.close();
			}
			if (renameFile != null) {
				tempFile.renameTo(renameFile);
			}
			throw e;
		}
	}

	public CtClass getClass(String className) throws NotFoundException {
		return classes.get(className);
	}

	public void loadPatchHashes(PatchManager patchManager) {
		Map<String, Integer> patchHashes = patchManager.getHashes();
		for (String clazz : patchHashes.keySet()) {
			File location = classNameToLocation.get(clazz);
			if (location == null) {
				continue;
			}
			int hash = patchHashes.get(clazz);
			Integer currentHash = expectedPatchHashes.get(location);
			expectedPatchHashes.put(location, (currentHash == null) ? hash : currentHash * 31 + hash);
		}
	}

	public boolean shouldPatch(String className) {
		return shouldPatch(classNameToLocation.get(className));
	}

	public boolean shouldPatch(File file) {
		return forcePatching || file == null || !(expectedPatchHashes.get(file).equals(locationToPatchHash.get(file)));
	}

	public boolean shouldPatch() {
		for (File file : expectedPatchHashes.keySet()) {
			if (shouldPatch(file)) {
				return true;
			}
		}
		return false;
	}

	public void restoreBackups(File backupDirectory) {
		for (File file : locationToPatchHash.keySet()) {
			Integer expectedHash = expectedPatchHashes.get(file);
			Integer actualHash = locationToPatchHash.get(file);
			if (actualHash == null) {
				continue;
			}
			if (forcePatching || !actualHash.equals(expectedHash)) {
				file.delete();
				try {
					Files.copy(new File(backupDirectory, file.getName()), file);
				} catch (IOException e) {
					Log.severe("Failed to restore unpatched backup before patching.");
				}
			}
		}
	}

	private static boolean isJar(File file) {
		return file.getName().toLowerCase().endsWith(".jar");
	}
}
