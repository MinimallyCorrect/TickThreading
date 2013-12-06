package javassist;

import nallar.tickthreading.patcher.remapping.StringExtractor;

public class RemappingPool extends ClassPool {
	private static final String target = "net/minecraft/";

	public static boolean classIsSrgObfuscated(byte[] bytes) {
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
		return super.createCtClass(className, useCache);
	}
}
