package nallar.insecurity;

import cpw.mods.fml.common.FMLLog;
import nallar.tickthreading.Log;
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
	static {
		System.setSecurityManager(new InsecurityManager());
	}

	@SuppressWarnings("EmptyMethod")
	public static void init() {
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
		if (Log.debug && MinecraftServer.getServer().isServerRunning()) {
			Log.debug("Server shutting down - requested at ", new ThisIsNotAnError());
		}
		for (Handler handler : FMLLog.getLogger().getHandlers()) {
			handler.flush();
		}
		for (Handler handler : Log.LOGGER.getHandlers()) {
			handler.flush();
		}
	}
}
