package javassist.is.faulty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

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
	public static final ThreadLocal redPowerPowerSearchTest = new HashSetThreadLocal();
	public static final ThreadLocal redPowerPowerSearch = new LinkedListThreadLocal();
	public static final ThreadLocal redPowerIsSearching = new BooleanThreadLocal();
	public static final ThreadLocal eligibleChunksForSpawning = new HashMapThreadLocal();

	private static class ArrayListThreadLocal extends ThreadLocal {
		@Override
		protected Object initialValue() {
			return new ArrayList();
		}
	}

	private static class HashMapThreadLocal extends ThreadLocal {
		@Override
		protected Object initialValue() {
			return new HashMap();
		}
	}

	private static class HashSetThreadLocal extends ThreadLocal {
		@Override
		protected Object initialValue() {
			return new HashSet();
		}
	}

	private static class LinkedListThreadLocal extends ThreadLocal {
		@Override
		protected Object initialValue() {
			return new LinkedList();
		}
	}

	private static class BooleanThreadLocal extends ThreadLocal {
		@Override
		protected Object initialValue() {
			return Boolean.FALSE;
		}
	}
}
