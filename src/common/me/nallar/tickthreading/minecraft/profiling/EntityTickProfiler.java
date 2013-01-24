package me.nallar.tickthreading.minecraft.profiling;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.base.Functions;
import com.google.common.collect.Ordering;

import me.nallar.tickthreading.util.TableFormatter;
import org.cliffc.high_scale_lib.NonBlockingHashMap;

public class EntityTickProfiler {
	public void record(Class<?> clazz, long time) {
		if (time < 0) {
			time = 0;
		}
		getTime(clazz).addAndGet(time);
		getInvocationCount(clazz).incrementAndGet();
	}

	public void clear() {
		invocationCount.clear();
		time.clear();
	}

	public TableFormatter writeData(TableFormatter tf) {
		Map<Class<?>, Long> time = new HashMap<Class<?>, Long>();
		for (Map.Entry<Class<?>, AtomicLong> entry : this.time.entrySet()) {
			time.put(entry.getKey(), entry.getValue().get());
		}
		final List<Class<?>> sortedKeysByTime = Ordering.natural().reverse().onResultOf(Functions.forMap(time)).immutableSortedCopy(time.keySet());
		tf
				.heading("Class")
				.heading("Time");
		for (int i = 0; i < 5 && i < sortedKeysByTime.size(); i++) {
			tf
					.row(sortedKeysByTime.get(i).getName())
					.row(time.get(sortedKeysByTime.get(i)) / 1000000d);
		}
		tf.finishTable();
		tf.sb.append('\n');
		Map<Class<?>, Long> timePerTick = new HashMap<Class<?>, Long>();
		for (Map.Entry<Class<?>, AtomicLong> entry : this.time.entrySet()) {
			timePerTick.put(entry.getKey(), entry.getValue().get() / invocationCount.get(entry.getKey()).get());
		}
		final List<Class<?>> sortedKeysByTimePerTick = Ordering.natural().reverse().onResultOf(Functions.forMap(timePerTick)).immutableSortedCopy(timePerTick.keySet());
		tf
				.heading("Class")
				.heading("Time/tick");
		for (int i = 0; i < 5 && i < sortedKeysByTimePerTick.size(); i++) {
			tf
					.row(sortedKeysByTimePerTick.get(i).getName())
					.row(timePerTick.get(sortedKeysByTimePerTick.get(i)) / 1000000d);
		}
		tf.finishTable();
		return tf;
	}

	private final Map<Class<?>, AtomicInteger> invocationCount = new NonBlockingHashMap<Class<?>, AtomicInteger>();
	private final Map<Class<?>, AtomicLong> time = new NonBlockingHashMap<Class<?>, AtomicLong>();

	// We synchronize on the class name as it is always the same object
	// We do not synchronize on the class object as that would also
	// prevent any synchronized static methods on it from running
	private AtomicInteger getInvocationCount(Class<?> clazz) {
		AtomicInteger i = invocationCount.get(clazz);
		if (i == null) {
			synchronized (clazz.getName()) {
				i = invocationCount.get(clazz);
				if (i == null) {
					i = new AtomicInteger();
					invocationCount.put(clazz, i);
				}
			}
		}
		return i;
	}

	private AtomicLong getTime(Class<?> clazz) {
		AtomicLong t = time.get(clazz);
		if (t == null) {
			synchronized (clazz.getName()) {
				t = time.get(clazz);
				if (t == null) {
					t = new AtomicLong();
					time.put(clazz, t);
				}
			}
		}
		return t;
	}
}
