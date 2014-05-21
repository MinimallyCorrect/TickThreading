package nallar.insecurity;

import nallar.tickthreading.Log;
import nallar.tickthreading.util.ThisIsNotAnError;
import net.minecraft.server.MinecraftServer;

import java.security.*;
import java.util.logging.*;

/**
 * Used to intercept shutdown attempts by other code - some mods have anti-modification code
 * which is rather annoying as it makes them impossible to patch. This is used to find where that code is
 * and disable it.
 * Currently in general unnecessary as code is patched at runtime, and these mods detect jar signature violations
 * Additionally, this ensures that even in the case of a Runtime.halt(), logging will be flushed to disk.
 */
public class InsecurityManager extends java.lang.SecurityManager {
	@SuppressWarnings("EmptyMethod")
	public static void init() {
		if (System.getProperty("tt.securityManager", "false").equalsIgnoreCase("true")) {
			System.setSecurityManager(new InsecurityManager());
		}
	}

	@Override
	public void checkPermission(Permission perm) {
	}

	@Override
	public void checkPermission(Permission perm, Object context) {
	}

	private static final String[] loggerNames = {
			"ForgeModLoader",
			"TickThreading",
			"TTPatcher",
	};

	public static void flushLogs() {
		if (Log.debug && MinecraftServer.getServer().isServerRunning()) {
			Log.debug("Server shutting down - requested at ", new ThisIsNotAnError());
		}
		for (String name : loggerNames) {
			for (Handler handler : Logger.getLogger(name).getHandlers()) {
				handler.flush();
			}
		}
	}

	@Override
	public void checkExit(int status) {
		super.checkExit(status);
		flushLogs();
	}
}
