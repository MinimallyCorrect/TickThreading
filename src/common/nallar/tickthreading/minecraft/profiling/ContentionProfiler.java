package nallar.tickthreading.minecraft.profiling;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.primitives.Longs;

import nallar.tickthreading.Log;
import nallar.tickthreading.minecraft.commands.Command;
import nallar.tickthreading.util.CollectionsUtil;
import nallar.tickthreading.util.TableFormatter;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.ThreadMinecraftServer;

public class ContentionProfiler {
	public ContentionProfiler(final ICommandSender commandSender, int seconds, int resolution) {
		this.resolution = resolution;
		final int ticks = seconds * 1000 / resolution;
		final TableFormatter tf = new TableFormatter(commandSender);
		new Thread(new Runnable() {
			@Override
			public void run() {
				profile(ticks);
				dump(tf);
				Command.sendChat(commandSender, tf.toString());
			}
		}, "Contention Profiler").start();
	}

	private final long resolution;
	private long[] threads;
	private final Map<String, IntegerHolder> monitorMap = new IntHashMap<String>();
	private final Map<String, IntegerHolder> waitingMap = new IntHashMap<String>();
	private final Map<String, IntegerHolder> traceMap = new IntHashMap<String>();

	private void dump(final TableFormatter tf) {
		tf
				.heading("Monitor")
				.heading("Time (ms)");
		for (String key : CollectionsUtil.sortedKeys(monitorMap, 6)) {
			tf
					.row(key)
					.row(monitorMap.get(key).value);
		}
		tf.finishTable();
		tf.sb.append('\n');
		tf
				.heading("Wait")
				.heading("Time (ms)");
		for (String key : CollectionsUtil.sortedKeys(waitingMap, 6)) {
			tf
					.row(key)
					.row(waitingMap.get(key).value);
		}
		tf.finishTable();
		tf.sb.append('\n');
		tf
				.heading("Stack")
				.heading("Time (ms)");
		for (String key : CollectionsUtil.sortedKeys(traceMap, 6)) {
			tf
					.row(key)
					.row(traceMap.get(key).value);
		}
		tf.finishTable();
	}

	private void profile(int ticks) {
		List<Long> threads = new ArrayList<Long>();
		for (Thread thread : Thread.getAllStackTraces().keySet()) {
			if (thread instanceof ThreadMinecraftServer) {
				threads.add(thread.getId());
			}
		}
		this.threads = Longs.toArray(threads);
		while (ticks --> 0) {
			long r = resolution - tick();

			if (r > 0) {
				try {
					Thread.sleep(r, 0);
				} catch (InterruptedException e) {
					Log.severe("Interrupted in profiling", e);
					return;
				}
			} else if (r < -10) {
				ticks--;
			}
		}
	}

	private long tick() {
		long t = System.currentTimeMillis();
		ThreadInfo[] threads = ManagementFactory.getThreadMXBean().getThreadInfo(this.threads, 4);
		for (ThreadInfo thread : threads) {
			if (thread == null) {
				continue;
			}
			Thread.State ts = thread.getThreadState();
			switch (ts) {
				case WAITING:
				case TIMED_WAITING:
				case BLOCKED:
					LockInfo lockInfo = thread.getLockInfo();
					if (lockInfo != null) {
						(ts == Thread.State.BLOCKED ? monitorMap : waitingMap).get(lockInfo.toString()).value++;
					}
					StackTraceElement stackTraceElement = getTrace(thread.getStackTrace());
					if (stackTraceElement != null) {
						traceMap.get(stackTraceElement.getClassName() + '.' + stackTraceElement.getMethodName()).value++;
					}
					break;
			}
		}
		return System.currentTimeMillis() - t;
	}

	private static StackTraceElement getTrace(final StackTraceElement[] stackTrace) {
		for (StackTraceElement stackTraceElement : stackTrace) {
			String className = stackTraceElement.getClassName();
			if (className.startsWith("java") || className.startsWith("sun.")) {
				continue;
			}
			return stackTraceElement;
		}
		return null;
	}

	private static class IntHashMap<K> extends HashMap<K, IntegerHolder> {
		IntHashMap() {
		}

		@Override
		public IntegerHolder get(Object k) {
			IntegerHolder integerHolder = super.get(k);
			if (integerHolder == null) {
				integerHolder = new IntegerHolder();
				put((K) k, integerHolder);
			}
			return integerHolder;
		}
	}

	private static class IntegerHolder implements Comparable<IntegerHolder> {
		public int value;

		IntegerHolder() {
		}

		@Override
		public int compareTo(final IntegerHolder o) {
			int x = value;
			int y = o.value;
			return (x < y) ? -1 : ((x == y) ? 0 : 1);
		}
	}
}
