package javassist.is.faulty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * This class is in a different package as javassist treats "me." as "me$" instead of "me/".
 * <p/>
 * :(
 */
@SuppressWarnings ("UnusedDeclaration")
public class ThreadLocals {
	public static final ThreadLocal entitiesWithinAABBExcludingEntity = new ArrayListThreadLocal();
	public static final ThreadLocal collidingBoundingBoxes = new ArrayListThreadLocal();
	public static final ThreadLocal redPowerBlockUpdateSet = new HashSetThreadLocal();
	public static final ThreadLocal redPowerPowerSearch = new LinkedListThreadLocal();
	public static final ThreadLocal redPowerPowerSearchTest = new HashSetThreadLocal();
	public static final ThreadLocal redPowerIsSearching = new BooleanThreadLocal();
	public static final ThreadLocal eligibleChunksForSpawning = new HashMapThreadLocal();
	public static final ThreadLocal factorizationFindLightAirParentToVisit = new HashSetThreadLocal();

	private static class ArrayListThreadLocal extends ThreadLocal {
		ArrayListThreadLocal() {
		}

		@Override
		protected Object initialValue() {
			return new ArrayList();
		}
	}

	private static class HashMapThreadLocal extends ThreadLocal {
		HashMapThreadLocal() {
		}

		@Override
		protected Object initialValue() {
			return new HashMap();
		}
	}

	private static class HashSetThreadLocal extends ThreadLocal {
		HashSetThreadLocal() {
		}

		@Override
		protected Object initialValue() {
			return new HashSet();
		}
	}

	private static class LinkedListThreadLocal extends ThreadLocal {
		LinkedListThreadLocal() {
		}

		@Override
		protected Object initialValue() {
			return new LinkedList();
		}
	}

	private static class BooleanThreadLocal extends ThreadLocal {
		BooleanThreadLocal() {
		}

		@Override
		protected Object initialValue() {
			return Boolean.FALSE;
		}
	}
}
