package nallar.tickthreading.patcher;

import javassist.NotFoundException;
import nallar.tickthreading.Log;
import nallar.tickthreading.mappings.MCPMappings;
import nallar.tickthreading.patcher.remapping.ByteSource;
import nallar.tickthreading.patcher.remapping.Deobfuscator;
import nallar.tickthreading.patcher.remapping.JarPatcher;
import nallar.tickthreading.util.CollectionsUtil;
import nallar.tickthreading.util.FileUtil;
import nallar.tickthreading.util.VersionUtil;
import nallar.unsafe.UnsafeUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import java.util.logging.Level;

public class PatchMain {
	public static void main(String[] argv) {
		Log.info("Running " + VersionUtil.versionString());
		if (argv.length < 1) {
			Log.severe("Type must be passed");
			return;
		}
		String type = argv[0];
		String[] args = Arrays.copyOfRange(argv, 1, argv.length);
		if ("patcher".equalsIgnoreCase(type)) {
			patcher(args);
		} else {
			Log.severe(type + " is not a valid action.");
		}
	}

	private static void addToClassPath(File file) {
		try {
			Method method = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
			method.setAccessible(true);
			method.invoke(ClassLoader.getSystemClassLoader(), file.toURI().toURL());
		} catch (Exception e) {
			throw UnsafeUtil.throwIgnoreChecked(e);
		}
	}

	private static void patcher(String[] args) {
		List<String> argsList = Arrays.asList(args);
		Log.setFileName("patcher", Level.FINEST, Log.LOGGER);
		PatchManager patchManager;
		try {
			//noinspection IOResourceOpenedButNotSafelyClosed
			patchManager = new PatchManager(PatchMain.class.getResourceAsStream("/patches.xml"), Patches.class);
		} catch (Exception e) {
			Log.severe("Failed to initialize Patch Manager", e);
			return;
		}
		try {
			List<File> filesToLoad = CollectionsUtil.toObjects(CollectionsUtil.split(args[0]), File.class);
			for (int i = 0; i < filesToLoad.size(); i++) {
				filesToLoad.set(i, filesToLoad.get(i).getAbsoluteFile());
			}
			List<File> forgeFiles = CollectionsUtil.toObjects(CollectionsUtil.split(args[0]), File.class);
			File minecraft = forgeFiles.get(0);
			File forge = forgeFiles.size() < 2 ? minecraft : forgeFiles.get(1);
			File minecraftLibCopy = new File(minecraft.getName() + ".lib");
			File forgeLibCopy = new File(forge.getName() + ".lib");
			FileUtil.copyFile(minecraft, minecraftLibCopy);
			addToClassPath(minecraftLibCopy);
			if (minecraft != forge) {
				FileUtil.copyFile(forge, forgeLibCopy);
				addToClassPath(forgeLibCopy);
			}
			Log.info("Minecraft jar: " + minecraft + ", Forge jar: " + forge);
			ByteSource.addFiles(filesToLoad.toArray(new File[filesToLoad.size()]));
			JarFile jarFile = null;
			try {
				jarFile = new JarFile(forge);
				InputStream inputStream = jarFile.getInputStream(jarFile.getJarEntry("deobfuscation_data-1.6.4.lzma"));
				try {
					Deobfuscator.INSTANCE.setup(inputStream);
				} finally {
					inputStream.close();
				}
				inputStream = jarFile.getInputStream(jarFile.getJarEntry("binpatches.pack.lzma"));
				try {
					JarPatcher.INSTANCE.setup(inputStream);
				} finally {
					inputStream.close();
				}
			} catch (IOException e) {
				Log.warning("Exception reading deobfuscation data", e);
			} finally {
				if (jarFile != null) {
					jarFile.close();
				}
			}
			if (true) {
				Log.severe("1.6.4 patcher not yet finished. It just breaks your install, so disabled for now. Why are you even running this build?");
				return;
			}
			ClassRegistry classRegistry = patchManager.classRegistry;
			classRegistry.writeAllClasses = argsList.contains("all");
			classRegistry.serverFile = filesToLoad.get(0);
			patchManager.loadBackups(filesToLoad);
			classRegistry.loadFiles(filesToLoad);
			try {
				classRegistry.getClass("org.bukkit.craftbukkit.libs.jline.Terminal");
				patchManager.patchEnvironment = "mcpc";
			} catch (NotFoundException ignored) {
			}
			Log.info("Patching with " + VersionUtil.versionString());
			Log.info("Patching in environment: " + patchManager.patchEnvironment);
			patchManager.runPatches(new MCPMappings());
			try {
				classRegistry.save(patchManager.backupDirectory);
			} catch (IOException e) {
				Log.severe("Failed to save patched classes", e);
				if (e.getMessage().contains("Couldn't rename ")) {
					Log.severe("Make sure none of the mods/server jar are currently open in any running programs" +
							"If you are using linux, check what has open files with the lsof command.");
				}
			}
		} catch (IOException e) {
			Log.severe("Failed to load jars", e);
		} catch (Exception e) {
			Log.severe("Unhandled patching failure", e);
		} catch (Error e) {
			Log.severe("An error occurred while patching, can't continue", e);
		}
		try {
			if (System.getProperty("unattend") == null) {
				Log.info("Done. Press enter to exit.");
				System.console().readLine();
			}
		} catch (Exception ignored) {
		}
	}
}
