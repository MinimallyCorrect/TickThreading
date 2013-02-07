package me.nallar.tickthreading.minecraft.profiling;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.base.Functions;
import com.google.common.collect.Ordering;

import me.nallar.tickthreading.util.MappingUtil;
import me.nallar.tickthreading.util.TableFormatter;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import org.cliffc.high_scale_lib.NonBlockingHashMap;

public class EntityTickProfiler {
	private int ticks;
	private final AtomicLong totalTime = new AtomicLong();

	public void record(Object o, long time) {
		if (time < 0) {
			time = 0;
		} else if (time > 500000) {
			AtomicLong currentTime = getSingleTime(o);
			synchronized (currentTime) {
				if (currentTime.get() < time) {
					currentTime.set(time);
				}
			}
			//slowTicks.append(o + " took too long: " + time / 1000000 + "ms\n"); // No chained append for threadsafety
		}
		Class<?> clazz = o.getClass();
		getTime(clazz).addAndGet(time);
		getInvocationCount(clazz).incrementAndGet();
		totalTime.addAndGet(time);
	}

	public void clear() {
		invocationCount.clear();
		time.clear();
		totalTime.set(0);
		ticks = 0;
	}

	public void tick() {
		ticks++;
	}

	public TableFormatter writeData(TableFormatter tf) {
		Map<Class<?>, Long> time = new HashMap<Class<?>, Long>();
		for (Map.Entry<Class<?>, AtomicLong> entry : this.time.entrySet()) {
			time.put(entry.getKey(), entry.getValue().get());
		}
		Map<Object, Long> singleTime = new HashMap<Object, Long>();
		for (Map.Entry<Object, AtomicLong> entry : this.singleTime.entrySet()) {
			singleTime.put(entry.getKey(), entry.getValue().get());
		}
		final List<Object> sortedSingleKeysByTime = Ordering.natural().reverse().onResultOf(Functions.forMap(singleTime)).immutableSortedCopy(singleTime.keySet());
		tf
				.heading("Obj")
				.heading("Max Time");
		for (int i = 0; i < 5 && i < sortedSingleKeysByTime.size(); i++) {
			tf
					.row(niceName(sortedSingleKeysByTime.get(i)))
					.row(singleTime.get(sortedSingleKeysByTime.get(i)) / 1000000d);
		}
		tf.finishTable();
		tf.sb.append('\n');
		final List<Class<?>> sortedKeysByTime = Ordering.natural().reverse().onResultOf(Functions.forMap(time)).immutableSortedCopy(time.keySet());
		tf
				.heading("Class")
				.heading("Total Time/Tick")
				.heading("%");
		for (int i = 0; i < 5 && i < sortedKeysByTime.size(); i++) {
			tf
					.row(niceName(sortedKeysByTime.get(i)))
					.row(time.get(sortedKeysByTime.get(i)) / (1000000d * ticks))
					.row((time.get(sortedKeysByTime.get(i)) / (double) totalTime.get()) * 100);
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

	private static Object niceName(Object o) {
		if (o instanceof TileEntity) {
			return niceName(o.getClass()) + ' ' + ((TileEntity) o).xCoord + ',' + ((TileEntity) o).yCoord + ',' + ((TileEntity) o).zCoord;
		} else if (o instanceof Entity) {
			return niceName(o.getClass()) + ' ' + (int) ((Entity) o).posX + ',' + (int) ((Entity) o).posY + ',' + (int) ((Entity) o).posZ;
		}
		return o.toString().substring(0, 48);
	}

	private static String niceName(Class<?> clazz) {
		String name = MappingUtil.debobfuscate(clazz.getName());
		if (name.contains(".")) {
			String cName = name.substring(name.lastIndexOf('.') + 1);
			String pName = name.substring(0, name.lastIndexOf('.'));
			if (pName.contains(".")) {
				pName = pName.substring(pName.lastIndexOf('.') + 1);
			}
			return (cName.length() < 15 ? pName + '.' : "") + cName;
		}
		return name;
	}

	private final Map<Class<?>, AtomicInteger> invocationCount = new NonBlockingHashMap<Class<?>, AtomicInteger>();
	private final Map<Class<?>, AtomicLong> time = new NonBlockingHashMap<Class<?>, AtomicLong>();
	private final Map<Object, AtomicLong> singleTime = new NonBlockingHashMap<Object, AtomicLong>();

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

	private AtomicLong getSingleTime(Object o) {
		AtomicLong t = singleTime.get(o);
		if (t == null) {
			synchronized (o) {
				t = singleTime.get(o);
				if (t == null) {
					t = new AtomicLong();
					singleTime.put(o, t);
				}
			}
		}
		return t;
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
