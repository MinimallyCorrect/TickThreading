package nallar.insecurity;

import cpw.mods.fml.common.FMLLog;
import nallar.tickthreading.Log;
import net.minecraft.server.MinecraftServer;

import java.security.Permission;
import java.util.logging.Handler;

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
