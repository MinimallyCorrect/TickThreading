package nallar.tickthreading.reporting;

import nallar.tickthreading.log.Log;
import nallar.tickthreading.util.unsafe.UnsafeUtil;

import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.*;

public class LeakDetector {
	private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);
	private final long waitTimeSeconds;
	private final Map<Long, LeakCheckEntry> scheduledObjects = new ConcurrentHashMap<>();

	public LeakDetector(final long waitTimeSeconds) {
		this.waitTimeSeconds = waitTimeSeconds;
	}

	public synchronized void scheduleLeakCheck(Object o, String oDescription_, final boolean clean) {
		try {
			if (clean) {
				scheduledThreadPoolExecutor.schedule(new CleanerTask(o), Math.min(waitTimeSeconds / 2, 20), TimeUnit.SECONDS);
			}
			final long id = System.identityHashCode(o);
			final String oDescription = (oDescription_ == null ? "" : oDescription_ + " : ") + o.getClass() + '@' + System.identityHashCode(o) + ':' + id;
			scheduledObjects.put(id, new LeakCheckEntry(o, oDescription));
			scheduledThreadPoolExecutor.schedule((Runnable) () -> {
				LeakCheckEntry leakCheckEntry = scheduledObjects.remove(id);
				Object o1 = leakCheckEntry.o.get();
				if (o1 == null) {
					Log.trace("Object " + leakCheckEntry.description + " has been removed normally.");
				} else {
					String warning = "Probable memory leak detected. \"" + leakCheckEntry.description + "\" has not been garbage collected after " + waitTimeSeconds + "s.";
					if (clean) {
						Log.trace(warning);
					} else {
						Log.warn(warning);
					}
				}
			}, waitTimeSeconds, TimeUnit.SECONDS);
		} catch (Throwable t) {
			Log.error("Failed to schedule leak check for " + oDescription_, t);
		}
	}

	private static class CleanerTask extends TimerTask {
		final Object toClean;

		CleanerTask(final Object toClean) {
			this.toClean = toClean;
		}

		@Override
		public void run() {
			UnsafeUtil.clean(toClean);
		}
	}

	private static class LeakCheckEntry {
		public final WeakReference<Object> o;
		public final String description;

		LeakCheckEntry(final Object o, final String description) {
			this.o = new WeakReference<>(o);
			this.description = description;
		}
	}
}
