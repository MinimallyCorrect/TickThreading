package me.nallar.tickthreading.minecraft.patched;

import java.net.URL;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.IClassTransformer;
import cpw.mods.fml.relauncher.RelaunchClassLoader;

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
				FMLLog.warning("Failed to transform " + name, throwable);
			}
		}
		return basicClass;
	}
}
