package nallar.patched.storage;

public abstract class IntCache {
	public static int[] getIntCache(int size) {
		return new int[size];
	}

	public static void resetIntCache() {
	}

	public static String func_85144_b() {
		return "IntCache disabled.";
	}
}
