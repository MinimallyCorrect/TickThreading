package javassist;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import nallar.collections.PartiallySynchronizedMap;
import nallar.tickthreading.Log;
import nallar.tickthreading.patcher.remapping.ByteSource;
import nallar.tickthreading.patcher.remapping.StringExtractor;
import nallar.tickthreading.patcher.remapping.Transformer;

public class RemappingPool extends ClassPool {
	private final PartiallySynchronizedMap<String, CtClass> srgClasses = new PartiallySynchronizedMap<String, CtClass>();
	public boolean isSrg = false;

	public static boolean exclude(final String className) {
		return className.startsWith("java.") || className.startsWith("javax.") || className.startsWith("sun.") || className.startsWith("com.google");
	}

	public boolean setSrgFor(String className) {
		byte[] bytes = getBytes(className);
		if (bytes == null) {
			return isSrg;
		}
		return isSrg = classIsSrgObfuscated(bytes, className);
	}

	private static final String target = "net/minecraft/";

	public static boolean classIsSrgObfuscated(byte[] bytes, String name) {
		if (bytes == null) {
			return false;
		}
		for (String entry : StringExtractor.getStrings(bytes)) {
			if (entry.contains(target) && !entry.contains("server/MinecraftServer") && !entry.contains("client/Minecraft") && !entry.contains("network/packet/IPacketHandler")) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void cacheCtClass(String className, CtClass c, boolean dynamic) {
		if (isSrg && !exclude(className)) {
			return;
		}
		super.cacheCtClass(className, c, dynamic);
	}

	@Override
	public CtClass getCached(String className) {
		if (isSrg && !exclude(className)) {
			return srgClasses.get(className);
		}
		return super.getCached(className);
	}

	private byte[] getBytes(String name) {
		if (!name.trim().equals(name)) {
			Log.severe("Broken name? " + name + " different when trimmed.", new Throwable());
		}
		CtClass ctClass = super.getCached(name);
		if (ctClass != null && ctClass.isModified()) {
			try {
				boolean oldSrg = isSrg;
				if (oldSrg) {
					isSrg = false;
				}
				byte[] bytes = ctClass.toBytecode();
				ctClass.defrost();
				if (oldSrg) {
					isSrg = true;
				}
				return bytes;
			} catch (Throwable t) {
				Log.severe("Failed to get class bytes from modified class " + name, t);
			}
		}
		return ByteSource.classes.get(name);
	}

	@Override
	protected synchronized CtClass get0(String className, boolean useCache) throws NotFoundException {
		if (!exclude(className)) {
			if (isSrg) {
				CtClass cachedClass = srgClasses.get(className);
				if (cachedClass != null) {
					return cachedClass;
				}
				cachedClass = super.getCached(className);
				if (cachedClass != null && cachedClass.isPrimitive()) {
					return cachedClass;
				}
				if (!Transformer.remapClassName(className).equals(className)) {
					Log.severe("Attempted to load obfuscated class " + className, new Throwable());
					return null;
				}
				String remappedName = Transformer.unmapClassName(className);

				byte[] bytes = getBytes(remappedName);
				if (bytes != null) {
					bytes = Transformer.transform(bytes);
					try {
						CtClass ctClass = new CtClassType(new ByteArrayInputStream(bytes), this);
						if (!remappedName.equals(className)) {
							ctClass.freeze();
						}
						srgClasses.put(className, ctClass);
						return ctClass;
					} catch (IOException e) {
						Log.severe("Failed to make " + className + " from " + remappedName, e);
					}
					return null;
				}
			} else {
				if (!Transformer.unmapClassName(className).equals(className)) {
					Log.severe("Attempted to load SRG class " + className + " while patching a non-SRG class.", new Throwable());
					return null;
				}
			}
		}
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
		return super.createCtClass(className, useCache);
	}

	public void markChanged(final String className) {
		srgClasses.remove(className);
	}
}
