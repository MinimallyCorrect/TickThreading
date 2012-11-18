package me.nallar.tickthreading.tests.patcher;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.Loader;
import me.nallar.tickthreading.patcher.NewExprChanger;
import org.junit.Assert;
import org.junit.Test;

@SuppressWarnings ("UnusedDeclaration")
public class NewExprChangerTest {
	private HashMap<String, String> h;
	private Map<String, String> h2;

	@Test
	public void testFixUnsafeCollections() throws Exception {
		Map<String, List<String>> replacementClasses = new HashMap<String, List<String>>();
		replacementClasses.put("java.util.HashMap", Arrays.asList("java.util.concurrent.ConcurrentHashMap,me.nallar.tickthreading.collections.CHashMap".split(",")));
		NewExprChanger newExprChanger = new NewExprChanger(replacementClasses);

		CtClass ctClass = ClassPool.getDefault().get("me.nallar.tickthreading.tests.patcher.NewExprChangerTest");
		newExprChanger.patchClass(ctClass);
		Loader loader = new Loader(ClassPool.getDefault());
		Object obj = loader.loadClass("me.nallar.tickthreading.tests.patcher.NewExprChangerTest").newInstance();
		Class<?> objClass = obj.getClass();
		objClass.getDeclaredMethod("testMethodWhichUsesHashMap").invoke(obj);
		objClass.getDeclaredMethod("testMethodWhichUsesHashMap2").invoke(obj);
	}

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
