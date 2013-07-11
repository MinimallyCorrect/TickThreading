package nallar.tickthreading.patcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
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

import javassist.ClassPath;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.RemappingPool;
import javassist.bytecode.MethodInfo;
import nallar.tickthreading.Log;
import nallar.tickthreading.util.CollectionsUtil;
import nallar.tickthreading.util.IterableEnumerationWrapper;
import nallar.tickthreading.util.LocationUtil;
import nallar.tickthreading.util.NormalFileFilter;
import nallar.unsafe.UnsafeUtil;
import net.minecraft.server.MinecraftServer;

// Unchecked - Enumeration cast due to old java APIs.
// ResultOfMethodCallIgnored - No, I do not care if mkdir fails to make the directory.
// FieldRepeatedlyAccessedInMethod - I don't care about minor optimisations in patcher code, can't find a way to change inspections per package.
@SuppressWarnings ({"unchecked", "ResultOfMethodCallIgnored", "FieldRepeatedlyAccessedInMethod"})
public class ClassRegistry {
	private final String hashFileName = "patcher.hash";
	private final String patchedModsFolderName = "patchedMods";
	private final String modsFolderName = "mods";
	private final Map<String, File> classNameToLocation = new HashMap<String, File>();
	private final Map<File, Integer> locationToPatchHash = new HashMap<File, Integer>();
	private final Map<File, Integer> expectedPatchHashes = new HashMap<File, Integer>();
	private final Set<File> loadedFiles = new HashSet<File>();
	private final Set<File> updatedFiles = new HashSet<File>();
	private final Map<String, Set<File>> duplicateClassNamesToLocations = new HashMap<String, Set<File>>();
	private final Set<ClassPath> classPathSet = new HashSet<ClassPath>();
	private final Map<String, byte[]> replacementFiles = new HashMap<String, byte[]>();
	public File serverFile;
	private File patchedModsFolder;
	public final RemappingPool classes = new RemappingPool();
	public boolean disableJavassistLoading = false;
	public boolean forcePatching = false;
	public boolean writeAllClasses = false;

	{
		MethodInfo.doPreverify = true;
		classes.appendSystemPath();
		classes.importPackage("java.util");
		classes.importPackage("java.io");
		classes.importPackage("nallar.tickthreading");
		classes.importPackage("nallar.tickthreading.global");
		classes.importPackage("nallar.collections");
	}

	public void clearClassInfo() {
		closeClassPath();
		classNameToLocation.clear();
		updatedFiles.clear();
		duplicateClassNamesToLocations.clear();
		replacementFiles.clear();
		loadedFiles.clear();
	}

	public void closeClassPath() {
		for (ClassPath classPath : classPathSet) {
			classes.removeClassPath(classPath);
		}
		classPathSet.clear();
	}

	public void loadFiles(Iterable<File> filesToLoad) throws IOException {
		for (File file : filesToLoad) {
			try {
				if (file.isDirectory()) {
					File[] files = file.listFiles(NormalFileFilter.$);
					if (files != null) {
						loadFiles(Arrays.asList(files));
					}
					continue;
				}
				String extension = file.getName();
				extension = extension.substring(extension.lastIndexOf('.') + 1).toLowerCase();
				if ("jar".equals(extension) || "zip".equals(extension) || "litemod".equals(extension)) {
					ZipFile zipFile = new ZipFile(file);
					try {
						loadZip(zipFile);
					} finally {
						zipFile.close();
					}
					loadHashes(file);
				}
			} catch (ZipException e) {
				throw new ZipException(e.getMessage() + " file: " + file);
			}
		}
	}

	public void update(String className, byte[] replacement) {
		Collection<File> duplicates = duplicateClassNamesToLocations.get(className);
		if (duplicates != null) {
			Log.warning(className + " is in multiple jars: " + CollectionsUtil.join(duplicates, ", "));
			updatedFiles.addAll(duplicates);
		}
		updatedFiles.add(classNameToLocation.get(className));
		replacementFiles.put(className.replace('.', '/') + ".class", replacement);
	}

	@SuppressWarnings ("IOResourceOpenedButNotSafelyClosed")
	public void save(File backupDirectory) throws IOException {
		closeClassPath();
		File tempFile = null, renameFile = null;
		File tempDirectory = new File(backupDirectory.getParentFile(), "TTTemp");
		tempDirectory.mkdir();
		ZipInputStream zin = null;
		ZipOutputStream zout = null;
		backupDirectory.mkdir();
		patchedModsFolder.mkdir();
		File modsFolder = new File(patchedModsFolder.getParent(), "mods");
		int patchedClasses = 0;
		try {
			for (File zipFile : updatedFiles) {
				if (zipFile == serverFile || (!zipFile.equals(LocationUtil.locationOf(PatchMain.class).getAbsoluteFile()) && !modsFolderName.equals(zipFile.getParentFile().getName()))) {
					File backupFile = new File(backupDirectory, zipFile.getName());
					if (backupFile.exists() && !backupFile.delete()) {
						Log.warning("Failed to remove old backup");
					}
					Files.copy(zipFile, backupFile);
					tempFile = makeTempFile(tempDirectory, zipFile);
					if (zipFile.renameTo(tempFile)) {
						renameFile = zipFile;
						tempFile.deleteOnExit();
					} else {
						throw new IOException("Couldn't rename " + zipFile + " -> " + tempFile);
					}
					zin = new ZipInputStream(new FileInputStream(tempFile));
					zout = new ZipOutputStream(new FileOutputStream(zipFile));
					writeChanges(zipFile, zin, zout, false);
					if (!tempFile.delete()) {
						Log.warning("Failed to delete temporary patching file " + tempFile + " after patching " + zipFile);
					}
					renameFile = null;
				} else {
					zin = new ZipInputStream(new FileInputStream(zipFile));
					File patchedModFile = new File(patchedModsFolder, zipFile.getName());
					if (patchedModFile.exists() && !patchedModFile.delete()) {
						Log.severe("Failed to write patches for " + zipFile + ", could not delete old patchedMods file.");
					}
					zout = new ZipOutputStream(new FileOutputStream(patchedModFile));
					patchedClasses += writeChanges(zipFile, zin, zout, true);
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
			if (renameFile != null && !tempFile.renameTo(renameFile)) {
				Log.warning("Failed to restore " + renameFile + " after patching, you will need to get a new copy of it.");
			}
			throw UnsafeUtil.throwIgnoreChecked(e);
		} finally {
			delete(tempDirectory);
		}
		Log.info("Patched " + patchedClasses + " mod classes.");
		for (File file : patchedModsFolder.listFiles()) {
			if (!new File(modsFolder, file.getName()).exists() && file.delete()) {
				Log.info("Deleted old patched mod file " + file.getName());
			}
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
			currentHash = (currentHash == null) ? hash : currentHash * 13 + hash;
			expectedPatchHashes.put(location, currentHash);
		}
	}

	public boolean shouldPatch(String className) {
		return shouldPatch(classNameToLocation.get(className));
	}

	boolean shouldPatch(File file) {
		return forcePatching || file == null || !(expectedPatchHashes.get(file).equals(locationToPatchHash.get(file)));
	}

	public boolean shouldPatch() {
		boolean shouldPatch = false;
		for (Map.Entry<File, Integer> fileIntegerEntry : expectedPatchHashes.entrySet()) {
			if (shouldPatch(fileIntegerEntry.getKey())) {
				Integer expectedPatchHash = fileIntegerEntry.getValue();
				Log.warning("Patching required for " + fileIntegerEntry.getKey() + " because " + (expectedPatchHash == null ? "it has not been patched" : "it is out of date." +
						"\nExpected " + expectedPatchHash + ", got " + locationToPatchHash.get(fileIntegerEntry.getKey())));
				shouldPatch = true;
			}
		}
		return shouldPatch;
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
				boolean restoreFailed = false;
				if (!backupFile.exists()) {
					restoreFailed = true;
				} else {
					fileIntegerEntry.getKey().delete();
					try {
						Files.move(backupFile, fileIntegerEntry.getKey());
					} catch (IOException e) {
						restoreFailed = true;
						Log.severe("Failed to restore unpatched backup before patching", e);
					}
				}
				if (restoreFailed) {
					Log.severe("Can't patch - no backup for " + fileIntegerEntry.getKey().getName() + " exists, and a patched copy is already in the mods directory." +
							"\nYou will need to replace this file with a new unpatched copy.");
					throw new Error("Missing backup for patched file");
				}
			}
		}
	}

	void appendClassPath(String path) throws NotFoundException {
		if (!disableJavassistLoading) {
			classPathSet.add(classes.appendClassPath(path));
		}
	}

	void loadHashes(File zipFile) throws IOException {
		if (!modsFolderName.equalsIgnoreCase(zipFile.getParentFile().getName())) {
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
			if (MinecraftServer.getServer() == null) {
				throw new NullPointerException();
			}
		} catch (Throwable t) {
			if (!file.delete()) {
				Log.warning("Unable to delete old patchedMods file " + file);
			}
			return;
		}
		ZipFile zip = new ZipFile(file);
		try {
			for (ZipEntry zipEntry : new IterableEnumerationWrapper<ZipEntry>((Enumeration<ZipEntry>) zip.entries())) {
				String name = zipEntry.getName();
				if (name.equals(hashFileName)) {
					ByteArrayOutputStream output = new ByteArrayOutputStream();
					ByteStreams.copy(zip.getInputStream(zipEntry), output);
					int hash = Integer.valueOf(new String(output.toByteArray(), "UTF-8"));
					locationToPatchHash.put(zipFile, hash);
				}
			}
		} finally {
			zip.close();
		}
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
		for (ZipEntry zipEntry : new IterableEnumerationWrapper<ZipEntry>((Enumeration<ZipEntry>) zip.entries())) {
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
	}

	private static File makeTempFile(File tempLocation, File file) {
		File tempFile = new File(tempLocation, file.getName() + ".tmp");
		if (tempFile.exists() && !tempFile.delete()) {
			throw new Error("Failed to delete old temp file " + tempFile);
		}
		return tempFile;
	}

	private static void delete(File f) {
		File[] files = f.listFiles();
		if (files != null) {
			for (File c : files) {
				delete(c);
			}
		}
		f.delete();
	}

	@SuppressWarnings ("StatementWithEmptyBody")
	private int writeChanges(File zipFile, ZipInputStream zin, ZipOutputStream zout, boolean onlyClasses) throws Exception {
		int patchedClasses = 0;
		Set<String> replacements = new HashSet<String>();
		ZipEntry zipEntry;
		while ((zipEntry = zin.getNextEntry()) != null) {
			String entryName = zipEntry.getName();
			if (entryName.equals(hashFileName) || (entryName.startsWith("META-INF/") && !(!entryName.isEmpty() && entryName.charAt(entryName.length() - 1) == '/') && !entryName.toUpperCase().endsWith("MANIFEST.MF")) && (entryName.length() - entryName.replace("/", "").length() == 1)) {
				// Skip
			} else if (onlyClasses && !entryName.toLowerCase().endsWith(".class")) {
				// Skip
			} else if (replacementFiles.containsKey(entryName)) {
				replacements.add(entryName);
				patchedClasses++;
			} else if (!onlyClasses || writeAllClasses) {
				zout.putNextEntry(new ZipEntry(entryName));
				ByteStreams.copy(zin, zout);
				patchedClasses++;
			}
		}
		for (String name : replacements) {
			zout.putNextEntry(new ZipEntry(name));
			zout.write(replacementFiles.get(name));
			zout.closeEntry();
		}
		boolean hasPatchHash = expectedPatchHashes.containsKey(zipFile);
		zout.putNextEntry(new ZipEntry(hashFileName));
		String patchHash = hasPatchHash ? String.valueOf(expectedPatchHashes.get(zipFile)) : "-1";
		zout.write(patchHash.getBytes("UTF-8"));
		if (hasPatchHash) {
			Log.info("Patched " + replacements.size() + " classes in " + zipFile + ", patchHash: " + patchHash + ", " + (onlyClasses ? "mod" : "server jar"));
		}
		zin.close();
		zout.close();
		return patchedClasses;
	}
}
