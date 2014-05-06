package nallar.tickthreading.patcher;

import nallar.tickthreading.PatchLog;

import java.lang.reflect.*;

public class PatchHook {
	private static Patcher patcher;

	static {
		PatchLog.fine("PatchHook running under classloader " + PatchHook.class.getClassLoader().getClass().getName());
		try {
			Class<?> clazz = Class.forName("cpw.mods.fml.relauncher.ServerLaunchWrapper");
			try {
				Field field = clazz.getDeclaredField("startupArgs");
				field.set(null, PatchLauncher.startupArgs);
			} catch (NoSuchFieldError ignored) {
			}
		} catch (Throwable t) {
			PatchLog.severe("Failed to set up MCPC+ startup args. This is only a problem if you are using MCPC+", t);
		}
		try {
			patcher = new Patcher(PatchLauncher.class.getResourceAsStream("/patches.xml"), Patches.class);
		} catch (Throwable t) {
			PatchLog.severe("Failed to create Patcher", t);
			System.exit(1);
		}
	}

	public static byte[] preSrgTransformationHook(String name, String transformedName, byte[] originalBytes) {
		try {
			return patcher.preSrgTransformation(name, transformedName, originalBytes);
		} catch (Throwable t) {
			PatchLog.severe("Failed to patch " + transformedName, t);
		}
		return originalBytes;
	}

	public static byte[] postSrgTransformationHook(String name, String transformedName, byte[] originalBytes) {
		try {
			return patcher.postSrgTransformation(name, transformedName, originalBytes);
		} catch (Throwable t) {
			PatchLog.severe("Failed to patch " + transformedName, t);
		}
		return originalBytes;
	}
}
