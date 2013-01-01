package me.nallar.reporting;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

public class Reporter {
	private static final Set<Long> reportedHashes = new ConcurrentSkipListSet<Long>();
	private static ReportingThread reportingThread;

	static {
		reportedHashes.add(0L);
	}

	public static void report(Throwable e) {
		if (reportedHashes.size() > 20 || !reportedHashes.add(hashCode(e))) {
			return;
		}
		getReportingThread().addIssue(e);
	}

	private static synchronized ReportingThread getReportingThread() {
		if (reportingThread == null) {
			reportingThread = new ReportingThread();
		}
		return reportingThread;
	}

	private static long hashCode(Throwable e) {
		if (e == null) {
			return 0;
		}
		StackTraceElement stackTraceElement = e.getStackTrace()[0];
		return e.getLocalizedMessage().hashCode() + stackTraceElement.getClassName().hashCode() + stackTraceElement.hashCode() + hashCode(e.getCause());
	}
}
