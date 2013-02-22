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
import me.nallar.tickthreading.util.CollectionsUtil;
import me.nallar.tickthreading.util.EnumerableWrapper;
import me.nallar.tickthreading.util.LocationUtil;
import me.nallar.unsafe.UnsafeUtil;
import net.minecraft.server.MinecraftServer;

public class ClassRegistry {
	private static final String hashFileName = "TickThreading.hash";
	private static final String patchedModsFolderName = "patchedMods";
	private final Map<String, File> classNameToLocation = new HashMap<String, File>();
	private final Map<File, Integer> locationToPatchHash = new HashMap<File, Integer>();
	private final Map<File, Integer> expectedPatchHashes = new HashMap<File, Integer>();
	private final Map<File, Map<String, byte[]>> additionalClasses = new HashMap<File, Map<String, byte[]>>();
	private final Set<File> loadedFiles = new HashSet<File>();
	private final Set<File> updatedFiles = new HashSet<File>();
	private final Map<String, Set<File>> duplicateClassNamesToLocations = new HashMap<String, Set<File>>();
	private final Set<ClassPath> classPathSet = new HashSet<ClassPath>();
	private final Map<String, byte[]> replacementFiles = new HashMap<String, byte[]>();
	public File serverFile;
	private File patchedModsFolder;
	public final ClassPool classes = new ClassPool(false);
	public boolean disableJavassistLoading = false;
	public boolean forcePatching = false;
	public boolean writeAllClasses = false;

	{
		MethodInfo.doPreverify = true;
		classes.appendSystemPath();
	}

	public void clearClassInfo() {
		finishModifications();
		classNameToLocation.clear();
		additionalClasses.clear();
		updatedFiles.clear();
		duplicateClassNamesToLocations.clear();
		replacementFiles.clear();
		loadedFiles.clear();
	}

	public void loadFiles(Iterable<File> filesToLoad) throws IOException {
		for (File file : filesToLoad) {
			String extension = file.getName().toLowerCase();
			extension = extension.substring(extension.lastIndexOf('.') + 1);
			try {
				if (file.isDirectory()) {
					if (!".disabled".equals(file.getName())) {
						loadFiles(Arrays.asList(file.listFiles()));
					}
				} else if ("jar".equals(extension) || "zip".equals(extension) || "litemod".equals(extension)) {
					loadZip(new ZipFile(file));
					loadHashes(file);
				}
			} catch (ZipException e) {
				throw new ZipException(e.getMessage() + " file: " + file);
			}
		}
	}

	void appendClassPath(String path) throws NotFoundException {
		if (!disableJavassistLoading) {
			classPathSet.add(classes.appendClassPath(path));
		}
	}

	void loadHashes(File zipFile) throws IOException {
		if (!"mods".equalsIgnoreCase(zipFile.getParentFile().getName())) {
			return;
		}
		if (patchedModsFolder == null) {
			patchedModsFolder = new File(zipFile.getParentFile().getParentFile(), patchedModsFolderName);
		}
		File file = new File(patchedModsFolder, zipFile.getName());
		if (!file.exists()) {
			return;
		}
		try {
			MinecraftServer.getServer();
		} catch (Throwable t) {
			file.delete();
			return;
		}
		ZipFile zip = new ZipFile(file);
		for (ZipEntry zipEntry : new EnumerableWrapper<ZipEntry>((Enumeration<ZipEntry>) zip.entries())) {
			String name = zipEntry.getName();
			if (name.equals(hashFileName)) {
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				ByteStreams.copy(zip.getInputStream(zipEntry), output);
				int hash = Integer.valueOf(new String(output.toByteArray(), "UTF-8"));
				locationToPatchHash.put(zipFile, hash);
			}
		}
		zip.close();
	}

	void loadZip(ZipFile zip) throws IOException {
		File file = new File(zip.getName());
		if (!loadedFiles.add(file)) {
			return;
		}
		try {
			appendClassPath(file.getAbsolutePath());
		} catch (Exception e) {
			Log.severe("Javassist could not load " + file, e);
		}
		for (ZipEntry zipEntry : new EnumerableWrapper<ZipEntry>((Enumeration<ZipEntry>) zip.entries())) {
			String name = zipEntry.getName();
			if (name.endsWith(".class")) {
				String className = name.replace('/', '.').substring(0, name.lastIndexOf('.'));
				if (classNameToLocation.containsKey(className)) {
					Set<File> locations = duplicateClassNamesToLocations.get(className);
					if (locations == null) {
						locations = new HashSet<File>();
						locations.add(classNameToLocation.get(className));
						duplicateClassNamesToLocations.put(className, locations);
					}
					locations.add(file);
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
		if (duplicateClassNamesToLocations.containsKey(className)) {
			Log.warning(className + " is in multiple jars: " + CollectionsUtil.join(duplicateClassNamesToLocations.get(className), ", "));
		}
		updatedFiles.add(classNameToLocation.get(className));
		replacementFiles.put(className.replace('.', '/') + ".class", replacement);
	}

	Map<String, byte[]> getAdditionalClasses(File file) {
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

	void add(File file, String className, byte[] byteCode) {
		getAdditionalClasses(file).put(className.replace('.', '/') + ".class", byteCode);
	}

	public void finishModifications() {
		for (ClassPath classPath : classPathSet) {
			classes.removeClassPath(classPath);
		}
		classPathSet.clear();
	}

	private static File makeTempFile(File tempLocation, File file) {
		return new File(tempLocation, file.getName() + ".tmp");
	}

	private static void delete(File f) {
		if (f.isDirectory()) {
			for (File c : f.listFiles()) {
				delete(c);
			}
		}
		f.delete();
	}

	private void writeChanges(File zipFile, ZipInputStream zin, ZipOutputStream zout, boolean onlyClasses) throws Exception {
		Set<String> replacements = new HashSet<String>();
		Map<String, byte[]> additionalClasses = getAdditionalClasses(zipFile);
		ZipEntry zipEntry;
		while ((zipEntry = zin.getNextEntry()) != null) {
			String entryName = zipEntry.getName();
			if (entryName.equals(hashFileName) || additionalClasses.containsKey(entryName) || (entryName.startsWith("META-INF/") && !(!entryName.isEmpty() && entryName.charAt(entryName.length() - 1) == '/') && !entryName.toUpperCase().endsWith("MANIFEST.MF")) && (entryName.length() - entryName.replace("/", "").length() == 1)) {
				// Skip
			} else if (onlyClasses && !entryName.toLowerCase().endsWith(".class")) {
				// Skip
			} else if (replacementFiles.containsKey(entryName)) {
				replacements.add(entryName);
			} else if (!onlyClasses || writeAllClasses) {
				zout.putNextEntry(new ZipEntry(entryName));
				ByteStreams.copy(zin, zout);
			}
		}
		for (String name : replacements) {
			zout.putNextEntry(new ZipEntry(name));
			zout.write(replacementFiles.get(name));
			zout.closeEntry();
		}
		for (Map.Entry<String, byte[]> stringEntry : additionalClasses.entrySet()) {
			zout.putNextEntry(new ZipEntry(stringEntry.getKey()));
			zout.write(stringEntry.getValue());
			zout.closeEntry();
		}
		boolean hasPatchHash = expectedPatchHashes.containsKey(zipFile);
		zout.putNextEntry(new ZipEntry(hashFileName));
		String patchHash = hasPatchHash ? String.valueOf(expectedPatchHashes.get(zipFile)) : "-1";
		zout.write(patchHash.getBytes("UTF-8"));
		if (hasPatchHash) {
			Log.info("Patched " + replacements.size() + " classes in " + zipFile.getName() + ", patchHash: " + patchHash);
		}
		if (!additionalClasses.isEmpty()) {
			Log.info("Added " + additionalClasses.size() + " classes required by patches.");
		}
		zin.close();
		zout.close();
	}

	public void save(File backupDirectory) throws IOException {
		finishModifications();
		File tempFile = null, renameFile = null;
		File tempDirectory = new File(backupDirectory.getParentFile(), "TTTemp");
		tempDirectory.mkdir();
		ZipInputStream zin = null;
		ZipOutputStream zout = null;
		backupDirectory.mkdir();
		updatedFiles.remove(LocationUtil.locationOf(PatchMain.class));
		try {
			for (File zipFile : updatedFiles) {
				if (zipFile == serverFile || !"mods".equals(zipFile.getParentFile().getName())) {
					File backupFile = new File(backupDirectory, zipFile.getName());
					backupFile.delete();
					Files.copy(zipFile, backupFile);
					tempFile = makeTempFile(tempDirectory, zipFile);
					tempFile.delete();
					if (zipFile.renameTo(tempFile)) {
						renameFile = zipFile;
						tempFile.deleteOnExit();
					} else {
						throw new IOException("Couldn't rename " + zipFile + " -> " + tempFile);
					}
					zin = new ZipInputStream(new FileInputStream(tempFile));
					zout = new ZipOutputStream(new FileOutputStream(zipFile));
					writeChanges(zipFile, zin, zout, false);
					tempFile.delete();
					renameFile = null;
				} else {
					zin = new ZipInputStream(new FileInputStream(zipFile));
					Log.info(patchedModsFolder.toString());
					patchedModsFolder.mkdir();
					zout = new ZipOutputStream(new FileOutputStream(new File(patchedModsFolder, zipFile.getName())));
					writeChanges(zipFile, zin, zout, true);
				}
				zin = null;
				zout = null;
			}
		} catch (Exception e) {
			if (zin != null) {
				zin.close();
			}
			if (zout != null) {
				zout.close();
			}
			if (renameFile != null) {
				tempFile.renameTo(renameFile);
			}
			UnsafeUtil.throwIgnoreChecked(e);
		} finally {
			delete(tempDirectory);
		}
	}

	public CtClass getClass(String className) throws NotFoundException {
		return classes.get(className);
	}

	public void loadPatchHashes(PatchManager patchManager) {
		Map<String, Integer> patchHashes = patchManager.getHashes();
		for (Map.Entry<String, Integer> stringIntegerEntry : patchHashes.entrySet()) {
			File location = classNameToLocation.get(stringIntegerEntry.getKey());
			if (location == null) {
				continue;
			}
			int hash = stringIntegerEntry.getValue();
			Integer currentHash = expectedPatchHashes.get(location);
			expectedPatchHashes.put(location, (currentHash == null) ? hash : currentHash * 31 + hash);
		}
	}

	public boolean shouldPatch(String className) {
		return shouldPatch(classNameToLocation.get(className));
	}

	boolean shouldPatch(File file) {
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
		for (Map.Entry<File, Integer> fileIntegerEntry : locationToPatchHash.entrySet()) {
			Integer expectedHash = expectedPatchHashes.get(fileIntegerEntry.getKey());
			Integer actualHash = fileIntegerEntry.getValue();
			if (actualHash == null) {
				continue;
			}
			if (forcePatching || !actualHash.equals(expectedHash)) {
				File backupFile = new File(backupDirectory, fileIntegerEntry.getKey().getName());
				if (new File(patchedModsFolder, backupFile.getName()).exists()) {
					continue;
				}
				if (!backupFile.exists()) {
					Log.severe("Can't patch - no backup for " + fileIntegerEntry.getKey().getName() + " exists, and a patched copy is already in the mods directory.");
					throw new Error("Missing backup for patched file");
				}
				fileIntegerEntry.getKey().delete();
				try {
					Files.move(backupFile, fileIntegerEntry.getKey());
				} catch (IOException e) {
					Log.severe("Failed to restore unpatched backup before patching.");
				}
			}
		}
	}
}
