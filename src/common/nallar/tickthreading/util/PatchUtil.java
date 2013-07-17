package nallar.tickthreading.util;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.common.io.Files;

import nallar.tickthreading.Log;
import nallar.tickthreading.minecraft.TickThreading;
import nallar.tickthreading.patcher.PatchMain;
import nallar.tickthreading.patcher.PatchManager;
import nallar.tickthreading.patcher.Patches;
import nallar.tickthreading.util.contextaccess.ContextAccess;

public enum PatchUtil {
	;
	private static boolean written = false;

	private static String getClassPath() {
		return LocationUtil.locationOf(TickThreading.class).toString() + File.pathSeparator + new File(LocationUtil.getServerDirectory(), "lib/guava-14.0-rc3.jar") + File.pathSeparator + new File(LocationUtil.getServerDirectory(), "lib/asm-all-4.1.jar");
	}

	public static synchronized void writePatchRunners() throws IOException {
		if (written) {
			return;
		}
		written = true;
		String java = System.getProperties().getProperty("java.home") + File.separator + "bin" + File.separator + "java";
		String CP = getClassPath();
		String MS = CollectionsUtil.join(LocationUtil.getJarLocations());

		ZipFile zipFile = new ZipFile(new File(LocationUtil.locationOf(TickThreading.class).toString()));
		try {
			for (ZipEntry zipEntry : new IterableEnumerationWrapper<ZipEntry>((Enumeration<ZipEntry>) zipFile.entries())) {
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

	@SuppressWarnings ("IOResourceOpenedButNotSafelyClosed")
	public static void startPatch() {
		String classPath = getClassPath();
		String separator = System.getProperty("file.separator");
		String path = System.getProperty("java.home")
				+ separator + "bin" + separator + "java";
		ProcessBuilder processBuilder = new ProcessBuilder(path, "-Dunattend=true", "-cp", classPath, PatchMain.class.getCanonicalName(), "patcher", CollectionsUtil.join(LocationUtil.getJarLocations()));
		Process p;
		try {
			p = processBuilder.start();
		} catch (IOException e) {
			Log.severe("Failed to start patcher", e);
			return;
		}
		inheritIO(p.getInputStream(), new PrintStream(new FileOutputStream(FileDescriptor.out)));
		inheritIO(p.getErrorStream(), new PrintStream(new FileOutputStream(FileDescriptor.err)));
		try {
			p.waitFor();
		} catch (InterruptedException ignored) {
		}
	}

	private static void inheritIO(final InputStream src, final PrintStream dst) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				Scanner sc = new Scanner(src);
				while (sc.hasNextLine()) {
					dst.println(sc.nextLine());
				}
			}
		}).start();
	}

	private static boolean patchesChecked = false;

	public static void checkPatches() {
		if (patchesChecked) {
			return;
		}
		patchesChecked = true;
		try {
			PatchUtil.writePatchRunners();
		} catch (IOException e) {
			Log.severe("Failed to write patch runners", e);
		}
		List<File> filesToCheck = LocationUtil.getJarLocations();
		if (PatchUtil.shouldPatch(filesToCheck)) {
			Log.severe("TickThreading is disabled, because your server has not been patched" +
					" or the patches are out of date" +
					"\nTo patch your server, simply run the PATCHME.bat/sh file in your server directory" +
					"\n\nAlso, make a full backup of your server if you haven't already!" +
					"\n\nFiles checked for patches: " + CollectionsUtil.join(filesToCheck));
			if (!System.getProperty("os.name").startsWith("Windows")) {
				PatchUtil.startPatch();
			}
			Runtime.getRuntime().exit(1);
		}
		ContextAccess.$.getContext(0);
	}
}
