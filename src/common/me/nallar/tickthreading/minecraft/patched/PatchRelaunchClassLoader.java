package me.nallar.tickthreading.minecraft.patched;

import java.net.URL;

import cpw.mods.fml.relauncher.IClassTransformer;
import cpw.mods.fml.relauncher.RelaunchClassLoader;
import me.nallar.tickthreading.Log;

public abstract class PatchRelaunchClassLoader extends RelaunchClassLoader {
	public PatchRelaunchClassLoader(URL[] sources) {
		super(sources);
	}

	@Override
	protected byte[] runTransformers(String name, byte[] basicClass) {
		for (IClassTransformer transformer : transformers) {
			try {
				basicClass = transformer.transform(name, basicClass);
			} catch (Throwable throwable) {
				Log.severe("Failed to transform " + name, throwable);
			}
		}
		return basicClass;
	}
}
