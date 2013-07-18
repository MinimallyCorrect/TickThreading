package nallar.tickthreading.minecraft.profiling;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.common.primitives.Longs;

import nallar.tickthreading.Log;
import nallar.tickthreading.minecraft.commands.Command;
import nallar.tickthreading.util.CollectionsUtil;
import nallar.tickthreading.util.TableFormatter;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

public class UtilisationProfiler {
	public static void profile(final ICommandSender commandSender, int seconds) {
		final UtilisationProfiler contentionProfiler = new UtilisationProfiler(seconds);
		contentionProfiler.run(new Runnable() {
			@Override
			public void run() {
				TableFormatter tf = new TableFormatter(commandSender);
				contentionProfiler.dump(tf, commandSender instanceof MinecraftServer ? 15 : 6);
				Command.sendChat(commandSender, tf.toString());
			}
		});
	}

	public UtilisationProfiler(int seconds) {
		this.seconds = seconds;
	}

	private final int seconds;
	private final Map<String, Long> monitorMap = new HashMap<String, Long>();

	public void run(final Runnable completed) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				profile();
				completed.run();
			}
		}, "Contention Profiler").start();
	}

	public void dump(final TableFormatter tf, int entries) {

		tf
				.heading("Thread")
				.heading("Used CPU Time");
		for (String key : CollectionsUtil.sortedKeys(monitorMap, entries)) {
			tf
					.row(key)
					.row(monitorMap.get(key) / TimeUnit.SECONDS.toNanos(1));
		}
		tf.finishTable();
	}

	private void profile() {
		ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
		threadMXBean.setThreadCpuTimeEnabled(true);
		List<Long> threads = new ArrayList<Long>();
		for (Thread thread : Thread.getAllStackTraces().keySet()) {
			threads.add(thread.getId());
		}
		final long[] threads1 = Longs.toArray(threads);
		try {
			Thread.sleep(seconds * TimeUnit.SECONDS.toMillis(1));
			for (long threadId : threads1) {
				long time = threadMXBean.getThreadCpuTime(threadId);
				if (time < 0) {
					continue;
				}
				ThreadInfo thread = threadMXBean.getThreadInfo(threadId, 0);
				if (thread != null) {
					monitorMap.put(thread.getThreadName(), time);
				}
			}
		} catch (InterruptedException e) {
			Log.severe("Interrupted in profiling", e);
		} finally {
			threadMXBean.setThreadCpuTimeEnabled(false);
		}
	}
}
