package nallar.tickthreading.patcher;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import javassist.NotFoundException;
import nallar.tickthreading.Log;
import nallar.tickthreading.mappings.MCPMappings;
import nallar.tickthreading.util.CollectionsUtil;
import nallar.tickthreading.util.VersionUtil;
import org.xml.sax.SAXException;

public class PatchMain {
	public static void main(String[] argv) {
		Log.info("Running " + VersionUtil.versionString());
		if (argv.length < 1) {
			Log.severe("Type must be passed");
			return;
		}
		String type = argv[0];
		String[] args = Arrays.copyOfRange(argv, 1, argv.length);
		if ("obfuscator".equalsIgnoreCase(type)) {
			obfuscator(args);
		} else if ("patcher".equalsIgnoreCase(type)) {
			patcher(args);
		} else {
			Log.severe(type + " is not a valid action.");
		}
	}

	private static void obfuscator(String[] args) {
		String mcpConfigPath = args.length > 0 ? args[0] : "build/forge/mcp/conf";
		String inputPatchPath = args.length > 1 ? args[1] : "resources/patches-deobfuscated.xml";
		String outputPatchPath = args.length > 2 ? args[2] : "build/classes/patches.xml";
		String outputMappingsPath = args.length > 2 ? args[2] : "build/classes/mappings.obj";

		Log.info("Obfuscating " + inputPatchPath + " to " + outputPatchPath + " via " + mcpConfigPath);

		try {
			MCPMappings mcpMappings = new MCPMappings(new File(mcpConfigPath));
			PatchManager patchManager = new PatchManager(new File(inputPatchPath).toURI().toURL().openStream(), Patches.class);

			try {
				patchManager.obfuscate(mcpMappings);
				patchManager.save(new File(outputPatchPath));
			} catch (TransformerException e) {
				Log.severe("Failed to save obfuscated patch");
			}

			try {
				File file = new File(outputMappingsPath);
				FileOutputStream f = new FileOutputStream(file);
				ObjectOutputStream s = new ObjectOutputStream(f);
				try {
					s.writeObject(mcpMappings.getSimpleClassNameMappings());
				} finally {
					s.close();
				}
			} catch (IOException e) {
				Log.severe("Failed to save class name mappings", e);
			}
		} catch (IOException e) {
			Log.severe("Failed to read input file", e);
		} catch (SAXException e) {
			Log.severe("Failed to parse input file", e);
		}
	}

	private static void patcher(String[] args) {
		List<String> argsList = Arrays.asList(args);
		Log.setFileName("patcher", Level.FINEST, Log.LOGGER);
		// TODO: Auto force-patch if Patches.class changes
		boolean forcePatching = true;//  args.length >= 2 && "force".equalsIgnoreCase(args[1]);
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
			ClassRegistry classRegistry = patchManager.classRegistry;
			classRegistry.writeAllClasses = argsList.contains("all");
			classRegistry.serverFile = filesToLoad.get(0);
			classRegistry.forcePatching = forcePatching;
			patchManager.loadBackups(filesToLoad);
			classRegistry.loadFiles(filesToLoad);
			try {
				classRegistry.getClass("org.bukkit.craftbukkit.libs.jline.Terminal");
				patchManager.patchEnvironment = "mcpc";
			} catch (NotFoundException ignored) {
			}
			Log.info("Patching with " + VersionUtil.versionString());
			Log.info("Patching in environment: " + patchManager.patchEnvironment);
			patchManager.runPatches();
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
