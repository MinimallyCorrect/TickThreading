package me.nallar.reporting;

import java.util.concurrent.LinkedBlockingQueue;

@SuppressWarnings ("ThrowableResultOfMethodCallIgnored")
public class ReportingThread extends Thread {
	public ReportingThread() {
		super();
		this.setDaemon(true);
		this.setName("me.nallar error reporter");
		this.run();
	}

	private final LinkedBlockingQueue<Throwable> issues = new LinkedBlockingQueue<Throwable>();

	public void addIssue(Throwable throwable) {
		issues.add(throwable);
	}

	@Override
	public void run() {
		while (true) {
			Throwable throwable;
			try {
				throwable = issues.take();
			} catch (InterruptedException e) {
				continue;
			}
			// TODO: Actually report the issue
		}
	}
}
