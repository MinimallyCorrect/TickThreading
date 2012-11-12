package me.nallar.tickthreading.tests.patcher;

import java.util.HashMap;
import java.util.Map;

import javassist.ClassPool;
import me.nallar.tickthreading.patcher.CollectionFixer;
import org.junit.Test;

public class CollectionFixerTest {
	@Test
	public void testFixUnsafeCollections() throws Exception {
		CollectionFixer collectionFixer = new CollectionFixer();

		collectionFixer.fixUnsafeCollections(ClassPool.getDefault().get("me.nallar.tickthreading.tests.patcher.CollectionFixerTest"));
	}

	public void testMethodWhichUsesHashMap() {
		HashMap h = new HashMap();
		Map h2 = new HashMap();
	}
}
