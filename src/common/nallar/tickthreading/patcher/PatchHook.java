package nallar.tickthreading.patcher;

import nallar.tickthreading.Log;

import java.util.*;

public class PatchHook {
	private boolean isPatched = false;
	HashMap<String, byte[]> cachedSrgBytes = new HashMap<String, byte[]>();
	HashMap<String, byte[]> patchedBytes = new HashMap<String, byte[]>();

	public static byte[] hook(String name, String transformedName, byte[] basicClass) {
		Log.info("");
		return basicClass;
	}
}
