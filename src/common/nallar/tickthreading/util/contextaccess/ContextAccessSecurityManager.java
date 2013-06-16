package nallar.tickthreading.util.contextaccess;

public class ContextAccessSecurityManager extends SecurityManager implements ContextAccess {
	@Override
	public Class getContext(int depth) {
		return getClassContext()[depth + 1];
	}

	@Override
	public boolean runningUnder(Class c) {
		Class[] classes = getClassContext();
		for (int i = 1; i < classes.length; i++) {
			if (classes[i] == c) {
				return true;
			}
		}
		return false;
	}
}
