package me.nallar.insecurity;

import java.security.Permission;
import java.util.logging.Handler;
import java.util.logging.Level;

import cpw.mods.fml.common.FMLLog;
import me.nallar.tickthreading.Log;

public class InsecurityManager extends java.lang.SecurityManager {
	static {
		System.setSecurityManager(new InsecurityManager());
	}

	@SuppressWarnings ("EmptyMethod")
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
		FMLLog.log(Level.WARNING, new Throwable(), "Server stopped.");
		for (Handler handler : FMLLog.getLogger().getHandlers()) {
			handler.flush();
		}
		for (Handler handler : Log.LOGGER.getHandlers()) {
			handler.flush();
		}
	}
}
