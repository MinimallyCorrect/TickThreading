package javassist;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import nallar.tickthreading.Log;
import nallar.tickthreading.patcher.remapping.ByteSource;
import nallar.tickthreading.patcher.remapping.Transformer;

public class RemappingPool extends ClassPool {
	public boolean allowDeobfuscation = false;

	@Override
	protected synchronized CtClass get0(String classname, boolean useCache) throws NotFoundException {
		CtClass clazz;
		if (useCache) {
			clazz = getCached(classname);
			if (clazz != null) {
				return clazz;
			}
		}

		clazz = createCtClass(classname, useCache);
		if (clazz != null) {
			// clazz.getName() != classname if classname is "[L<name>;".
			if (useCache) {
				cacheCtClass(clazz.getName(), clazz, false);
			}
		}

		return clazz;
	}

	protected CtClass createCtClass(String className, boolean useCache) {
		/*if (!Transformer.remapClassName(className).equals(className)) {
			throw new Error("Attempted to load obfuscated class " + className);
		}*/
		String remappedName = Transformer.unmapClassName(className);
		if (!className.equals(remappedName)) {
			if (!allowDeobfuscation) {
				Log.severe("Attempted to load SRG class " + className + " before finished patching server code.", new Throwable());
				return null;
			}
			byte[] bytes = ByteSource.classes.get(remappedName);
			if (bytes == null) {
				Log.severe("Couldn't find bytes " + remappedName + " for " + className);
			} else {
				bytes = Transformer.transform(bytes);
				try {
					Log.severe(className + " deobfuscated from file " + remappedName);
					return new CtClassType(new ByteArrayInputStream(bytes), this);
				} catch (IOException e) {
					Log.severe("Failed to make " + className + " from " + remappedName, e);
				}
			}
		}

		return super.createCtClass(className, useCache);
	}
}
