package nallar.tickthreading.patcher.remapping;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.RemappingClassAdapter;

public class Transformer {
	public static byte[] transform(byte[] bytes) {
		ClassReader classReader = new ClassReader(bytes);
		ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		RemappingClassAdapter remapAdapter = new Adapter(classWriter);
		classReader.accept(remapAdapter, ClassReader.EXPAND_FRAMES);
		return classWriter.toByteArray();
	}

	public static String remapClassName(String name) {
		return Deobfuscator.INSTANCE.map(name.replace('.', '/')).replace('/', '.');
	}

	public static String unmapClassName(String name) {
		return Deobfuscator.INSTANCE.unmap(name.replace('.', '/')).replace('/', '.');
	}

	public static class Adapter extends RemappingClassAdapter {
		private static final String[] EMPTY_INTERFACES = new String[0];

		public Adapter(ClassVisitor cv) {
			super(cv, Deobfuscator.INSTANCE);
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			if (interfaces == null) {
				interfaces = EMPTY_INTERFACES;
			}
			Deobfuscator.INSTANCE.mergeSuperMaps(name, superName, interfaces);
			super.visit(version, access, name, signature, superName, interfaces);
		}
	}
}
