package me.nallar.tickthreading.patched;

import java.util.ArrayList;

public class ThreadLocals {
	public static final ThreadLocal entitiesWithinAABBExcludingEntity = new ListThreadLocal();
	public static final ThreadLocal collidingBoundingBoxes = new ListThreadLocal();

	private static class ListThreadLocal extends ThreadLocal {
		@Override
		protected Object initialValue() {
			return new ArrayList();
		}
	}
}
