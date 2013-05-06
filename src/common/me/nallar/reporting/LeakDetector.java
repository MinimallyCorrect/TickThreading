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

	public void scheduleLeakCheck(Object o) {
		scheduleLeakCheck(o, null);
	}

	public void scheduleLeakCheck(Object o, String oDescription_) {
		final long id = UnsafeUtil.addressOf(o);
		final String oDescription = (oDescription_ == null ? "" : oDescription_ + " : ") + o.getClass() + '@' + System.identityHashCode(o) + ':' + id;
		scheduledObjects.put(id, new LeakCheckEntry(o, oDescription));
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				LeakCheckEntry leakCheckEntry = scheduledObjects.remove(id);
				Object o = leakCheckEntry.o.get();
				if (o == null) {
					Log.info("Object " + leakCheckEntry.description + " has been removed normally. :)");
				} else {
					Log.warning("Memory leak detected. \"" + leakCheckEntry.description + "\" has not been garbage collected after " + waitTime + "ms." +
							"\nApproximate leaked memory: " + UnsafeUtil.unsafeSizeOf(o) + " bytes");
				}
			}
		}, waitTime);
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
