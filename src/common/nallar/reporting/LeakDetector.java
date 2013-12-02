package nallar.reporting;

import nallar.tickthreading.Log;
import nallar.unsafe.UnsafeUtil;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class LeakDetector {
	private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);
	private final long waitTimeSeconds;
	private final Map<Long, LeakCheckEntry> scheduledObjects = new ConcurrentHashMap<Long, LeakCheckEntry>();

	public LeakDetector(final long waitTimeSeconds) {
		this.waitTimeSeconds = waitTimeSeconds;
	}

	public synchronized void scheduleLeakCheck(Object o, String oDescription_, final boolean clean) {
		try {
			if (clean) {
				scheduledThreadPoolExecutor.schedule(new CleanerTask(o), Math.min(waitTimeSeconds / 2, 20), TimeUnit.SECONDS);
			}
			final long id = UnsafeUtil.addressOf(o);
			final String oDescription = (oDescription_ == null ? "" : oDescription_ + " : ") + o.getClass() + '@' + System.identityHashCode(o) + ':' + id;
			scheduledObjects.put(id, new LeakCheckEntry(o, oDescription));
			scheduledThreadPoolExecutor.schedule(new Runnable() {
				@Override
				public void run() {
					LeakCheckEntry leakCheckEntry = scheduledObjects.remove(id);
					Object o = leakCheckEntry.o.get();
					if (o == null) {
						Log.fine("Object " + leakCheckEntry.description + " has been removed normally.");
					} else {
						String warning = "Probable memory leak detected. \"" + leakCheckEntry.description + "\" has not been garbage collected after " + waitTimeSeconds + "s.";
						if (clean && !Log.debug) {
							Log.fine(warning);
						} else {
							Log.warning(warning);
						}
					}
				}
			}, waitTimeSeconds, TimeUnit.SECONDS);
		} catch (Throwable t) {
			Log.severe("Failed to schedule leak check for " + oDescription_, t);
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
			this.o = new WeakReference<Object>(o);
			this.description = description;
		}
	}
}
