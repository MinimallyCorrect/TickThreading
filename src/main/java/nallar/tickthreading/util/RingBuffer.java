package nallar.tickthreading.util;

import java.util.concurrent.atomic.*;

// TODO - Make this class work
/**
 * Very unsafe, very fast multiple producer multiple consumer ringbuffer queue
 * Non-resizable
 * Will explode if you produce more than the fixed size without consuming
 * Not FIFO or LIFO - FIWTFO
 * ALSO DOES NOT WORK. I'm not Doug Lea, lock-free code is hard :(
 */
public class RingBuffer<T> {
	private static final int SIZE = 1024;
	private final Entry[] ring = new Entry[SIZE];
	private final AtomicInteger putPosition = new AtomicInteger();
	private final AtomicInteger getPosition = new AtomicInteger();

	public RingBuffer() {
		for (int i = 0; i < SIZE; i++) {
			ring[i] = new Entry();
		}
	}

	public T get() {
		int position = getPosition.incrementAndGet();
		Entry e = ring[position % SIZE];
		Object o = e.o;
		if (o == null) {
			getPosition.decrementAndGet();
			return null;
		}
		e.o = null;
		return (T) o;
	}

	public void add(T o) {
		int position = putPosition.incrementAndGet();
		Entry e = ring[position % SIZE];
		if (e.o != null) {
			throw new Error("Overflowed ring buffer");
		}
		e.o = o;
	}

	private static class Entry {
		volatile Object o;
	}
}
