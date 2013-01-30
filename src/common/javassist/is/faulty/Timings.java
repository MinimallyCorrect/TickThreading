package javassist.is.faulty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.base.Functions;
import com.google.common.collect.Ordering;

import me.nallar.tickthreading.util.MappingUtil;
import me.nallar.tickthreading.util.TableFormatter;
import org.cliffc.high_scale_lib.NonBlockingHashMap;

public enum Timings {
	;
	public static boolean enabled = false;

	public static void record(String name, long time) {
		if (time < 0) {
			time = 0;
		}
		getTime(name).addAndGet(time);
		getInvocationCount(name).incrementAndGet();
	}

	public static void clear() {
		invocationCount.clear();
		time.clear();
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
			timePerTick.put(entry.getKey(), entry.getValue().get() / invocationCount.get(entry.getKey()).get());
		}
		final List<String> sortedKeysByTimePerTick = Ordering.natural().reverse().onResultOf(Functions.forMap(timePerTick)).immutableSortedCopy(timePerTick.keySet());
		tf
				.heading("Class")
				.heading("Time/tick")
				.heading("Ticks");
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
		String suffix = clazz.substring(slash);
		String name = MappingUtil.debobfuscate(clazz.substring(0, slash));
		if (name.contains(".")) {
			String cName = name.substring(name.lastIndexOf('.') + 1);
			String pName = name.substring(0, name.lastIndexOf('.'));
			if (pName.contains(".")) {
				pName = pName.substring(pName.lastIndexOf('.') + 1);
			}
			return pName + '.' + cName + suffix;
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
