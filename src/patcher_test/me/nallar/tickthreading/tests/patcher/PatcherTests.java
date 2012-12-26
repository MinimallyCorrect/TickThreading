package me.nallar.tickthreading.tests.patcher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.Loader;
import me.nallar.tickthreading.patcher.Patches;
import me.nallar.tickthreading.util.ListUtil;
import org.junit.Assert;
import org.junit.Test;

@SuppressWarnings ("UnusedDeclaration")
public class PatcherTests {
	@Test
	public void testReplaceInstantiations() throws Exception {
		Map<String, List<String>> replacementClasses = new HashMap<String, List<String>>();
		replacementClasses.put("java.util.HashMap", ListUtil.split("java.util.concurrent.ConcurrentHashMap,me.nallar.tickthreading.collections.CHashMap"));
		Patches patches = new Patches(classRegistry);

		CtClass ctClass = ClassPool.getDefault().get("me.nallar.tickthreading.tests.patches.PatcherTests");
		for (CtMethod ctMethod : ctClass.getDeclaredMethods()) {
			patches.replaceInstantiationsImplementation(ctMethod, replacementClasses);
		}

		Loader loader = new Loader(ClassPool.getDefault());
		Object obj = loader.loadClass("me.nallar.tickthreading.tests.patches.PatcherTests").newInstance();
		Class<?> objClass = obj.getClass();
		objClass.getDeclaredMethod("testMethodWhichUsesHashMap").invoke(obj);
		objClass.getDeclaredMethod("testMethodWhichUsesHashMap2").invoke(obj);
	}

	private HashMap<String, String> h;
	private Map<String, String> h2;

	public void testMethodWhichUsesHashMap() {
		h = new HashMap<String, String>();
		h2 = new HashMap<String, String>();
		if (h.getClass().getName().equals("java.util.HashMap") || h.getClass().getName().equals("java.util.HashMap")) {
			System.out.println("h: " + h.getClass().getName());
			System.out.println("h2: " + h2.getClass().getName());
			Assert.fail("Expected replacement concurrent classes, h: " + h.getClass().getName() + "h2: " + h2.getClass().getName());
		}
		h.put("test", "test2");
		h2.put("test", "test2");
	}

	public void testMethodWhichUsesHashMap2() {
		h.put("test", "test2");
		h2.put("test", "test2");
	}
}
