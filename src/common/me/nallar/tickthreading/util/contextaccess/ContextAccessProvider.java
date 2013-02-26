package me.nallar.tickthreading.util.contextaccess;

import me.nallar.tickthreading.Log;

public class ContextAccessProvider {
	private static final Class[] contextAccessClasses = {
			ContextAccessReflection.class,
			ContextAccessSecurityManager.class,
	};

	public static ContextAccess getContextAccess() {
		for (Class<?> clazz : contextAccessClasses) {
			try {
				ContextAccess contextAccess = (ContextAccess) clazz.newInstance();
				Class<?> currentClass = contextAccess.getContext(0);
				if (currentClass != ContextAccessProvider.class) {
					throw new Error("Wrong class returned: " + currentClass + ", expected ContextAccessProvider");
				}
				return contextAccess;
			} catch (Throwable t) {
				Log.fine("Failed to instantiate " + clazz, t);
			}
		}
		throw new Error("Failed to set up any context access");
	}
}
