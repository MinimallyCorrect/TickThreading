package nallar.tickthreading.util.contextaccess;

import nallar.tickthreading.Log;

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
					StringBuilder sb = new StringBuilder();
					sb.append("Stack:\n");
					for (int i = -2; i < 3; i++) {
						try {
							sb.append(contextAccess.getContext(i).getName()).append(" at ").append(i).append('\n');
						} catch (Throwable ignored) {
						}
					}
					throw new Error("Wrong class returned: " + currentClass + ", expected ContextAccessProvider. " + sb);
				}
				return contextAccess;
			} catch (Throwable t) {
				Log.warning("Unable to set up context access class " + clazz, t);
			}
		}
		throw new Error("Failed to set up any context access");
	}
}
