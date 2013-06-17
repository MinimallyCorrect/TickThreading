package nallar.tickthreading.util.contextaccess;

public class ContextAccessReflection implements ContextAccess {
	@Override
	public Class getContext(int depth) {
		return sun.reflect.Reflection.getCallerClass(depth + 2);
	}

	@Override
	public boolean runningUnder(Class c) {
		for (int i = 3; i < 15; i++) {
			if (sun.reflect.Reflection.getCallerClass(i) == c) {
				return true;
			}
		}
		return false;
	}
}
