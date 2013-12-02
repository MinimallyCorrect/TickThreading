package nallar.reporting;

import nallar.tickthreading.minecraft.ThreadManager;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

public class Reporter {
	private static final Set<Long> reportedHashes = new ConcurrentSkipListSet<Long>();
	private static final ThreadManager reportingThreadManager = new ThreadManager(1, "nallar error reporting thread");

	static {
		reportedHashes.add(0L);
	}

	public static void report(Throwable e) {
		if (reportedHashes.size() > 20 || !reportedHashes.add(hashCode(e))) {
			return;
		}
		reportingThreadManager.run(new Report(e));
	}

	private static long hashCode(Throwable e) {
		if (e == null) {
			throw new IllegalArgumentException("Throwable can not be null");
		}
		StackTraceElement stackTraceElement = e.getStackTrace()[0];
		return e.getLocalizedMessage().hashCode() + stackTraceElement.getClassName().hashCode() + stackTraceElement.hashCode() + hashCode(e.getCause());
	}

	private static class Report implements Runnable {
		private final Throwable throwable;

		public Report(Throwable throwable) {
			this.throwable = throwable;
		}

		@Override
		public void run() {
			// TODO: Actually report the issue
		}
	}
}
