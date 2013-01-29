package me.nallar.tickthreading.minecraft.patched;

import java.net.URL;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.IClassTransformer;
import cpw.mods.fml.relauncher.RelaunchClassLoader;
import me.nallar.tickthreading.Log;

public abstract class PatchRelaunchClassLoader extends RelaunchClassLoader {
	public PatchRelaunchClassLoader(URL[] sources) {
		super(sources);
	}

	@Override
	protected byte[] runTransformers(String name, byte[] basicClass) {
		if (basicClass == null) {
			FMLLog.warning("Could not find the class " + name + ". This is not necessarily an issue.");
		} else {
			for (IClassTransformer transformer : transformers) {
				try {
					byte[] oldClass = basicClass;
					basicClass = transformer.transform(name, basicClass);
					if (basicClass == null) {
						basicClass = oldClass;
						Log.severe(transformer.getClass() + " returned a null class during transformation, ignoring.");
					}
				} catch (Throwable throwable) {
					FMLLog.severe("Failed to transform " + name, throwable);
				}
			}
		}
		return basicClass;
	}
}
