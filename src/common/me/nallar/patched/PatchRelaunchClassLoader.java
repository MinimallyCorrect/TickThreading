package me.nallar.patched;

import java.io.File;
import java.net.URL;
import java.util.logging.Level;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.IClassTransformer;
import cpw.mods.fml.relauncher.RelaunchClassLoader;
import me.nallar.tickthreading.Log;
import net.minecraft.server.MinecraftServer;

public abstract class PatchRelaunchClassLoader extends RelaunchClassLoader {
	public PatchRelaunchClassLoader(URL[] sources) {
		super(sources);
	}

	private static File locationOf(Class clazz) {
		String path = clazz.getProtectionDomain().getCodeSource().getLocation().getPath();
		path = path.contains("!") ? path.substring(0, path.lastIndexOf('!')) : path;
		if (!path.isEmpty() && path.charAt(0) == '/') {
			path = "file:" + path;
		}
		try {
			return new File(new URL(path).toURI());
		} catch (Exception e) {
			Log.severe("", e);
			return new File(path);
		}
	}

	public void construct() {
		try {
			for (File file : new File(locationOf(MinecraftServer.class).getParentFile(), "mods").listFiles()) {
				if (file.getName().toLowerCase().contains("tickthreading") && file.getName().endsWith(".jar")) {
					URL toAdd = file.toURI().toURL();
					if (!sources.contains(toAdd)) {
						sources.add(toAdd);
					}
				}
			}
		} catch (Throwable ignored) {
		}
	}

	@Override
	protected byte[] runTransformers(String name, byte[] basicClass) {
		if (basicClass == null) {
			if (DEBUG_CLASSLOADING) {
				FMLLog.warning("Could not find the class " + name + ". This is not necessarily an issue.");
			}
		} else {
			for (IClassTransformer transformer : transformers) {
				try {
					byte[] oldClass = basicClass;
					basicClass = transformer.transform(name, basicClass);
					if (basicClass == null) {
						basicClass = oldClass;
						FMLLog.severe(transformer.getClass() + " returned a null class during transformation, ignoring.");
					}
				} catch (Throwable throwable) {
					if (throwable.getMessage().contains("for invalid side")) {
						invalidClasses.add(name);
						throw (RuntimeException) throwable;
					} else {
						FMLLog.log(DEBUG_CLASSLOADING ? Level.WARNING : Level.FINE, throwable, "Failed to transform " + name);
					}
				}
			}
		}
		return basicClass;
	}
}
