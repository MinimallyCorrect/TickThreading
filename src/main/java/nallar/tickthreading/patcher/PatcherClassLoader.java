package nallar.tickthreading.patcher;

public class PatcherClassLoader extends ClassLoader {
	public PatcherClassLoader(ClassLoader parent) {
		super(parent);
	}

	@Override
	public Class<?> loadClass(String name) throws ClassNotFoundException {
		if (name.startsWith("net.minecraft") || name.startsWith("cpw.")) {
			String message = "Cannot load minecraft class " + name + " from patcher.";
			System.err.println(message);
			new Throwable().printStackTrace(System.err);
			throw new ClassNotFoundException(message);
		}
		return super.loadClass(name);
	}
}
