package org.minimallycorrect.tickthreading.reporting;

import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.*;

import lombok.val;

import org.apache.logging.log4j.Level;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.minimallycorrect.tickthreading.config.Config;
import org.minimallycorrect.tickthreading.log.Log;
import org.minimallycorrect.tickthreading.util.unsafe.UnsafeUtil;

public class LeakDetector {
	private static final long waitTimeSeconds = 60;
	private static final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat("tt-clean-%d").build());
	private static final Map<Long, LeakCheckEntry> scheduledObjects = new ConcurrentHashMap<>();

	private static void scheduleCleanupTask(Object toClean, long seconds) {
		scheduledThreadPoolExecutor.schedule(() -> UnsafeUtil.clean(toClean), seconds, TimeUnit.SECONDS);
	}

	public static synchronized void scheduleLeakCheck(Object o, String name) {
		try {
			val clean = Config.$.worldCleaning;
			if (clean)
				scheduleCleanupTask(o, Math.min(waitTimeSeconds / 2, 20));

			long id = System.identityHashCode(o);
			scheduledObjects.put(id, new LeakCheckEntry(o, getDescription(o, name, id), clean ? Level.TRACE : Level.WARN));

			scheduledThreadPoolExecutor.schedule(scheduledObjects.remove(id)::check, waitTimeSeconds, TimeUnit.SECONDS);
		} catch (Throwable t) {
			Log.error("Failed to schedule leak check for " + name, t);
		}
	}

	private static String getDescription(Object o, String oDescription_, long id) {
		return (oDescription_ == null ? "" : oDescription_ + " : ") + o.getClass() + '@' + System.identityHashCode(o) + ':' + id;
	}

	private static class LeakCheckEntry {
		final WeakReference<Object> o;
		final String description;
		final Level level;

		LeakCheckEntry(final Object o, final String description, Level level) {
			this.o = new WeakReference<>(o);
			this.description = description;
			this.level = level;
		}

		void check() {
			if (o.get() == null) {
				Log.trace("Object " + description + " has been removed normally.");
				return;
			}

			String warning = "Probable memory leak detected. \"" + description + "\" has not been garbage collected after " + waitTimeSeconds + "s.";
			Log.log(level, null, warning);
		}
	}
}
