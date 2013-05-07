package me.nallar.reporting;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import me.nallar.tickthreading.Log;
import me.nallar.unsafe.UnsafeUtil;

public class LeakDetector {
	private final Timer timer = new Timer("Leak Detector", true);
	private final long waitTime;
	private final Map<Long, LeakCheckEntry> scheduledObjects = new ConcurrentHashMap<Long, LeakCheckEntry>();

	public LeakDetector(final long waitTimeSeconds) {
		waitTime = waitTimeSeconds * 1000;
	}

	public void scheduleLeakCheck(Object o, String oDescription_, boolean clean) {
		if (clean) {
			timer.schedule(new CleanerTask(o), Math.min(waitTime / 2, 60000));
		}
		final long id = UnsafeUtil.addressOf(o);
		final String oDescription = (oDescription_ == null ? "" : oDescription_ + " : ") + o.getClass() + '@' + System.identityHashCode(o) + ':' + id;
		scheduledObjects.put(id, new LeakCheckEntry(o, oDescription));
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				LeakCheckEntry leakCheckEntry = scheduledObjects.remove(id);
				Object o = leakCheckEntry.o.get();
				if (o == null) {
					Log.fine("Object " + leakCheckEntry.description + " has been removed normally.");
				} else {
					Log.warning("Probable memory leak detected. \"" + leakCheckEntry.description + "\" has not been garbage collected after " + waitTime / 1000 + "s.");
				}
			}
		}, waitTime);
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
