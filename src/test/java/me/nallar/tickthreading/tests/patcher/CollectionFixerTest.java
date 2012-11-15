package me.nallar.tickthreading.tests.patcher;

import java.util.HashMap;
import java.util.Map;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.Loader;
import me.nallar.tickthreading.patcher.NewExprChanger;
import org.junit.Test;

public class CollectionFixerTest {
	private HashMap<String, String> h;
	private Map<String, String> h2;

	@Test
	public void testFixUnsafeCollections() throws Exception {
		NewExprChanger collectionFixer = new NewExprChanger();

		CtClass ctClass = ClassPool.getDefault().get("me.nallar.tickthreading.tests.patcher.CollectionFixerTest");
		collectionFixer.fixUnsafeCollections(ctClass);
		Loader loader = new Loader(ClassPool.getDefault());
		Object obj = loader.loadClass("me.nallar.tickthreading.tests.patcher.CollectionFixerTest").newInstance();
		Class<?> objClass = obj.getClass();
		objClass.getDeclaredMethod("testMethodWhichUsesHashMap").invoke(obj);
		objClass.getDeclaredMethod("testMethodWhichUsesHashMap2").invoke(obj);
	}

	public void testMethodWhichUsesHashMap() {
		h = new HashMap<String, String>();
		h2 = new HashMap<String, String>();
		System.out.println("h: " + h.getClass().getName());
		System.out.println("h2: " + h2.getClass().getName());
		h.put("test", "test2");
		h2.put("test", "test2");
	}

	public void testMethodWhichUsesHashMap2() {
		h.put("test", "test2");
		h2.put("test", "test2");
	}
}
