package me.nallar.TickThreading.Patcher;

import javassist.ClassPool;

/**
 * Finds non-concurrent collections, determines whether they should be changed,
 * and automatically fixes if possible.
 */
public class CollectionFixer {
	ClassPool pool;

	public CollectionFixer(ClassPool pool) {
		this.pool = pool;
	}

	private void findUnsafeCollections() {

	}
}
