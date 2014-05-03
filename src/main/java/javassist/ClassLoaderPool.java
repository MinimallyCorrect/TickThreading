package javassist;

import com.google.common.base.Throwables;
import javassist.bytecode.Descriptor;
import nallar.tickthreading.Log;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.*;

/**
 * This is in the javassist package (which isn't sealed) to access package-local javassist internals needed to load
 * from the classloader at runtime for patching purposes.
 */
public class ClassLoaderPool extends ClassPool {
	private final boolean preSrg;

	public ClassLoaderPool(boolean preSrg) {
		this.preSrg = preSrg;
		this.appendSystemPath();
		this.importPackage("java.util");
		this.importPackage("java.lang");
	}

	@Override
	protected void cacheCtClass(String className, CtClass c, boolean dynamic) {
		super.cacheCtClass(className, c, dynamic);
	}

	@Override
	public CtClass getCached(String className) {
		return super.getCached(className);
	}

	@Override
	protected synchronized CtClass get0(String className, boolean useCache) throws NotFoundException {
		CtClass clazz;
		if (useCache) {
			clazz = getCached(className);
			if (clazz != null) {
				return clazz;
			}
		}

		clazz = createCtClass(className, useCache);
		if (clazz != null) {
			// clazz.getName() != classname if classname is "[L<name>;".
			if (useCache) {
				cacheCtClass(clazz.getName(), clazz, false);
			}
		}

		return clazz;
	}

	@Override
	protected CtClass createCtClass(String className, boolean useCache) {
		if (className.charAt(0) == '[') {
			className = Descriptor.toClassName(className);
		}
		if (LaunchClassLoader.instance.excluded(className)) {
			return super.createCtClass(className, useCache);
		}
		byte[] bytes = preSrg ? LaunchClassLoader.instance.getPreSrgBytes(className) : LaunchClassLoader.instance.getSrgBytes(className);
		if (bytes == null) {
			Log.warning("Failed to find class " + className + ", preSrg: " + preSrg);
			return super.createCtClass(className, useCache);
		}
		try {
			return new CtClassType(new ByteArrayInputStream(bytes), this);
		} catch (IOException e) {
			throw Throwables.propagate(e);
		}
	}
}
