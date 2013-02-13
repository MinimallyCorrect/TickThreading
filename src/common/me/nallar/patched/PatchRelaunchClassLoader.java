package me.nallar.patched;

import java.net.URL;
import java.util.logging.Level;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.IClassTransformer;
import cpw.mods.fml.relauncher.RelaunchClassLoader;

public abstract class PatchRelaunchClassLoader extends RelaunchClassLoader {
	public PatchRelaunchClassLoader(URL[] sources) {
		super(sources);
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
