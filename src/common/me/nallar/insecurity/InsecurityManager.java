package me.nallar.insecurity;

import java.security.Permission;
import java.util.logging.Level;

import cpw.mods.fml.common.FMLLog;

public class InsecurityManager extends java.lang.SecurityManager {
	static {
		System.setSecurityManager(new InsecurityManager());
	}

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
		FMLLog.log(Level.FINE, new Throwable(), "Server killed.");
	}
}
