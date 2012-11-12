package me.nallar.tickthreading.tests.patcher;

import java.util.HashMap;
import java.util.Map;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.Loader;
import me.nallar.tickthreading.patcher.CollectionFixer;
import org.junit.Test;

public class CollectionFixerTest {
	HashMap h = new HashMap();
	Map h2 = new HashMap();

	@Test
	public void testFixUnsafeCollections() throws Exception {
		CollectionFixer collectionFixer = new CollectionFixer();

		CtClass ctClass = ClassPool.getDefault().get("me.nallar.tickthreading.tests.patcher.CollectionFixerTest");
		collectionFixer.fixUnsafeCollections(ctClass);
		Loader loader = new Loader(ClassPool.getDefault());
		Object obj = loader.loadClass("me.nallar.tickthreading.tests.patcher.CollectionFixerTest").newInstance();
		Class<?> objClass = obj.getClass();
		objClass.getDeclaredMethod("testMethodWhichUsesHashMap").invoke(obj);
	}

	public void testMethodWhichUsesHashMap() {
		h = new HashMap();
		h2 = new HashMap();
		System.out.println("h: " + h.getClass().getName());
		System.out.println("h2: " + h2.getClass().getName());
		h.put("test", "test2");
		h2.put("test", "test2");
	}
}
