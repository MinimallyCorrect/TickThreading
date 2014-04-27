package nallar.tickthreading.minecraft.profiling;

import com.google.common.base.Functions;
import com.google.common.collect.Ordering;
import nallar.tickthreading.util.TableFormatter;
import org.cliffc.high_scale_lib.NonBlockingHashMap;

import java.util.*;
import java.util.concurrent.atomic.*;

public enum Timings {
	;
	public static boolean enabled = false;
	private static int tickCount;

	public static void record(String name, long time) {
		if (time < 0) {
			time = 0;
		}
		getTime(name).addAndGet(time);
		getInvocationCount(name).incrementAndGet();
	}

	public static void tick() {
		if (enabled) {
			tickCount++;
		}
	}

	public static void clear() {
		invocationCount.clear();
		time.clear();
		tickCount = 0;
	}

	public static TableFormatter writeData(TableFormatter tf) {
		Map<String, Long> time = new HashMap<String, Long>();
		for (Map.Entry<String, AtomicLong> entry : Timings.time.entrySet()) {
			time.put(entry.getKey(), entry.getValue().get());
		}
		final List<String> sortedKeysByTime = Ordering.natural().reverse().onResultOf(Functions.forMap(time)).immutableSortedCopy(time.keySet());
		tf
				.heading("Class")
				.heading("Time");
		for (int i = 0; i < 5 && i < sortedKeysByTime.size(); i++) {
			tf
					.row(niceName(sortedKeysByTime.get(i)))
					.row(time.get(sortedKeysByTime.get(i)) / 1000000d);
		}
		tf.finishTable();
		tf.sb.append('\n');
		Map<String, Long> timePerTick = new HashMap<String, Long>();
		for (Map.Entry<String, AtomicLong> entry : Timings.time.entrySet()) {
			timePerTick.put(entry.getKey(), entry.getValue().get() / tickCount);
		}
		final List<String> sortedKeysByTimePerTick = Ordering.natural().reverse().onResultOf(Functions.forMap(timePerTick)).immutableSortedCopy(timePerTick.keySet());
		tf
				.heading("Class")
				.heading("Time/tick")
				.heading("Calls");
		for (int i = 0; i < 5 && i < sortedKeysByTimePerTick.size(); i++) {
			tf
					.row(niceName(sortedKeysByTimePerTick.get(i)))
					.row(timePerTick.get(sortedKeysByTimePerTick.get(i)) / 1000000d)
					.row(invocationCount.get(sortedKeysByTimePerTick.get(i)));
		}
		tf.finishTable();
		return tf;
	}

	private static String niceName(String clazz) {
		int slash = clazz.lastIndexOf('/');
		if (slash == -1) {
			return clazz;
		}
		String suffix = clazz.substring(slash);
		String name = clazz.substring(0, slash);
		if (name.contains(".")) {
			return name.substring(name.lastIndexOf('.') + 1) + suffix;
		}
		return name + suffix;
	}

	private static final Map<String, AtomicInteger> invocationCount = new NonBlockingHashMap<String, AtomicInteger>();
	private static final Map<String, AtomicLong> time = new NonBlockingHashMap<String, AtomicLong>();

	private static AtomicInteger getInvocationCount(String clazz) {
		AtomicInteger i = invocationCount.get(clazz);
		if (i == null) {
			synchronized (Timings.class) {
				i = invocationCount.get(clazz);
				if (i == null) {
					i = new AtomicInteger();
					invocationCount.put(clazz, i);
				}
			}
		}
		return i;
	}

	private static AtomicLong getTime(String clazz) {
		AtomicLong t = time.get(clazz);
		if (t == null) {
			synchronized (Timings.class) {
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
