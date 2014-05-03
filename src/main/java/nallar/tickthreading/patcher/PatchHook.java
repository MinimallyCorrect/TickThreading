package nallar.tickthreading.patcher;

public class PatchHook {
	private static Patcher patcher;

	static {
		patcher = new Patcher(PatchLauncher.class.getResourceAsStream("/patches.xml"), Patches.class);
	}

	public static byte[] preSrgTransformationHook(String name, String transformedName, byte[] originalBytes) {
		return patcher.preSrgTransformation(name, transformedName, originalBytes);
	}

	public static byte[] postSrgTransformationHook(String name, String transformedName, byte[] originalBytes) {
		return patcher.postSrgTransformation(name, transformedName, originalBytes);
	}
}
