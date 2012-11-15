package me.nallar.tickthreading.tests.patcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.Loader;
import me.nallar.tickthreading.patcher.NewExprChanger;
import org.junit.Test;

public class NewExprChangerTest {
	private HashMap<String, String> h;
	private Map<String, String> h2;

	@Test
	public void testFixUnsafeCollections() throws Exception {
		Map replacementClasses = new HashMap();
		List hashMapReplacementClasses = new ArrayList();
		hashMapReplacementClasses.add("java.util.concurrent.ConcurrentHashMap");
		hashMapReplacementClasses.add("me.nallar.tickthreading.concurrentcollections.CHashMap");
		replacementClasses.put("java.util.HashMap", hashMapReplacementClasses);
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
