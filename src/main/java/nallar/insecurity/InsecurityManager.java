package nallar.insecurity;

import nallar.log.Log;
import nallar.util.unsafe.UnsafeUtil;

import java.security.*;

/**
 * Used to intercept shutdown attempts by other code - some mods have anti-modification code
 * which is rather annoying as it makes them impossible to patch. This is used to find where that code is
 * and disable it.
 * Currently in general unnecessary as code is patched at runtime, and these mods detect jar signature violations
 * Additionally, this ensures that even in the case of a Runtime.halt(), logging will be flushed to disk.
 */
public class InsecurityManager extends java.lang.SecurityManager {
	public static void init() {
		if (!System.getProperty("tt.removeSecurityManager", "true").equalsIgnoreCase("false") && System.getSecurityManager() != null) {
			UnsafeUtil.removeSecurityManager();
			if (System.getSecurityManager() != null)
				Log.severe("Failed to remove SecurityManager");
		}

		if (System.getProperty("tt.installSecurityManager", "false").equalsIgnoreCase("true")) {
			System.setSecurityManager(new InsecurityManager());
		}
	}

	@Override
	public void checkPermission(Permission perm) {
	}

	@Override
	public void checkPermission(Permission perm, Object context) {
	}

	@Override
	public void checkExit(int status) {
		super.checkExit(status);
		Log.flushLogs();
	}
}
