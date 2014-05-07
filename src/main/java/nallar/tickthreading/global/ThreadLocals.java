package nallar.tickthreading.global;

import java.util.*;

@SuppressWarnings("UnusedDeclaration")
/**
 * A small utility class containing fields with ThreadLocals to be used in patched mod classes.
 * Also has utility inner classes with correct initialValues for ThreadLocals, as inner classes with patch XML
 * are a pain
 */
public class ThreadLocals {
	public static final ThreadLocal eligibleChunksForSpawning = new HashMapThreadLocal();
	public static final ThreadLocal factorizationFindLightAirParentToVisit = new HashSetThreadLocal();
	public static final ThreadLocal mapChunkTempByteArray = new MapChunkTempByteArrayThreadLocal();

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

	public static class MapChunkTempByteArrayThreadLocal extends ThreadLocal<byte[]> {
		@Override
		public byte[] initialValue() {
			return new byte[196864];
		}
	}
}
