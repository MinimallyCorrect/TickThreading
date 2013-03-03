package me.nallar.tickthreading.util.contextaccess;

import me.nallar.tickthreading.Log;

class ContextAccessProvider {
	private static final Class[] contextAccessClasses = {
			ContextAccessReflection.class,
			ContextAccessSecurityManager.class,
	};

	static ContextAccess getContextAccess() {
		for (Class<?> clazz : contextAccessClasses) {
			try {
				ContextAccess contextAccess = (ContextAccess) clazz.newInstance();
				Class<?> currentClass = contextAccess.getContext(0);
				if (currentClass != ContextAccessProvider.class) {
					throw new Error("Wrong class returned: " + currentClass + ", expected ContextAccessProvider");
				}
				return contextAccess;
			} catch (Throwable t) {
				Log.warning("Failed to instantiate " + clazz, t);
			}
		}
		throw new Error("Failed to set up any context access");
	}
}
