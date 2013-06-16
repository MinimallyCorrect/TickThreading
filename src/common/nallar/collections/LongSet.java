package nallar.collections;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import net.minecraft.util.LongHashMap;

public class LongSet extends AbstractSet<Long> implements Set<Long> {
	private static final Object set = new Object();
	private final LongHashMap m = new LongHashMap();

	public boolean add(long l) {
		return m.put(l, set) == null;
	}

	public boolean contains(long l) {
		return m.containsItem(l);
	}

	@Override
	public boolean contains(Object l) {
		return m.containsItem((Long) l);
	}

	@Override
	public boolean remove(Object l) {
		return m.remove((Long) l) != null;
	}

	public boolean remove(long l) {
		return m.remove(l) != null;
	}

	@Override
	public LongIterator iterator() {
		return new LongIterator(m.getKeys());
	}

	@Override
	public int size() {
		return 0;
	}

	public static final class LongIterator implements Iterator<Long> {
		private static final long EMPTY_KEY = Long.MIN_VALUE;
		private final long[][] keys;
		private long nextKey = EMPTY_KEY;
		private int outerIndex = 0;
		private int innerIndex = 0;

		public LongIterator(final long[][] keys) {
			this.keys = keys;
			nextLong();
		}

		@Override
		public boolean hasNext() {
			return nextKey != EMPTY_KEY;
		}

		@Deprecated
		@Override
		public Long next() {
			return nextLong();
		}

		public long nextLong() {
			long thisKey = this.nextKey;
			if (thisKey == EMPTY_KEY) {
				throw new NoSuchElementException();
			}
			long searchingKey = EMPTY_KEY;
			for (; outerIndex < keys.length; outerIndex++) {
				long[] keys = this.keys[outerIndex];
				if (keys == null) {
					innerIndex = 0;
					continue;
				}
				for (; innerIndex < keys.length; innerIndex++) {
					searchingKey = keys[innerIndex];
					if (searchingKey == EMPTY_KEY) {
						innerIndex = 0;
						break;
					}
				}
			}
			this.nextKey = searchingKey;
			return thisKey;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
}
