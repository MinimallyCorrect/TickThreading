package nallar.tickthreading.reporting;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import nallar.tickthreading.log.Log;
import nallar.tickthreading.util.unsafe.UnsafeUtil;
import org.apache.logging.log4j.Level;

import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.*;

public class LeakDetector {
	final long waitTimeSeconds;
	private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat("tt-clean-%1").build());
	private final Map<Long, LeakCheckEntry> scheduledObjects = new ConcurrentHashMap<>();

	public LeakDetector(final long waitTimeSeconds) {
		this.waitTimeSeconds = waitTimeSeconds;
	}

	private void scheduleCleanupTask(Object toClean, long seconds) {
		scheduledThreadPoolExecutor.schedule(() -> UnsafeUtil.clean(toClean), seconds, TimeUnit.SECONDS);
	}

	public synchronized void scheduleLeakCheck(Object o, String name, boolean clean) {
		try {
			if (clean)
				scheduleCleanupTask(o, Math.min(waitTimeSeconds / 2, 20));

			long id = System.identityHashCode(o);
			scheduledObjects.put(id, new LeakCheckEntry(o, getDescription(o, name, id), clean ? Level.TRACE : Level.WARN));

			scheduledThreadPoolExecutor.schedule(scheduledObjects.remove(id)::check, waitTimeSeconds, TimeUnit.SECONDS);
		} catch (Throwable t) {
			Log.error("Failed to schedule leak check for " + name, t);
		}
	}

	private String getDescription(Object o, String oDescription_, long id) {
		return (oDescription_ == null ? "" : oDescription_ + " : ") + o.getClass() + '@' + System.identityHashCode(o) + ':' + id;
	}

	private class LeakCheckEntry {
		public final WeakReference<Object> o;
		public final String description;
		public final Level level;

		LeakCheckEntry(final Object o, final String description, Level level) {
			this.o = new WeakReference<>(o);
			this.description = description;
			this.level = level;
		}

		public void check() {
			if (o.get() == null) {
				Log.trace("Object " + description + " has been removed normally.");
				return;
			}

			String warning = "Probable memory leak detected. \"" + description + "\" has not been garbage collected after " + waitTimeSeconds + "s.";
			Log.log(level, null, warning);
		}
	}
}
