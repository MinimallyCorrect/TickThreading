package javassist.is.faulty;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * This class is in a different package as javassist replace "me." with "me$" instead of "me/".
 * <p/>
 * :(
 */
@SuppressWarnings ("UnusedDeclaration")
public class ThreadLocals {
	public static final ThreadLocal entitiesWithinAABBExcludingEntity = new ArrayListThreadLocal();
	public static final ThreadLocal collidingBoundingBoxes = new ArrayListThreadLocal();
	public static final ThreadLocal redPowerBlockUpdateSet = new HashSetThreadLocal();

	private static class ArrayListThreadLocal extends ThreadLocal {
		@Override
		protected Object initialValue() {
			return new ArrayList();
		}
	}

	private static class HashSetThreadLocal extends ThreadLocal {
		@Override
		protected Object initialValue() {
			return new HashSet();
		}
	}
}
