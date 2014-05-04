package nallar.tickthreading.patcher;

import nallar.tickthreading.PatchLog;

public class PatchHook {
	private static Patcher patcher;

	static {
		try {
			patcher = new Patcher(PatchLauncher.class.getResourceAsStream("/patches.xml"), Patches.class);
		} catch (Throwable t) {
			PatchLog.severe("Failed to create Patcher", t);
			System.exit(1);
		}
	}

	public static byte[] preSrgTransformationHook(String name, String transformedName, byte[] originalBytes) {
		return patcher.preSrgTransformation(name, transformedName, originalBytes);
	}

	public static byte[] postSrgTransformationHook(String name, String transformedName, byte[] originalBytes) {
		return patcher.postSrgTransformation(name, transformedName, originalBytes);
	}
}
