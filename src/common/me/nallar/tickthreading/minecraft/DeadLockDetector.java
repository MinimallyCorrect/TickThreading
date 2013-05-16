package me.nallar.tickthreading.minecraft;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.base.Function;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import cpw.mods.fml.common.FMLCommonHandler;
import me.nallar.exception.ThreadStuckError;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.util.ChatFormat;
import me.nallar.tickthreading.util.CollectionsUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.packet.Packet3Chat;
import net.minecraft.server.MinecraftServer;

public class DeadLockDetector {
	private static final StackTraceElement[] EMPTY_STACK_TRACE = new StackTraceElement[0];
	private boolean attemptedToRecoverDeadlock = false;
	private boolean sentWarningRecently = false;
	private static volatile long lastTickTime = 0;
	public static final Set<ThreadManager> threadManagers = Collections.newSetFromMap(new ConcurrentHashMap<ThreadManager, Boolean>());

	public DeadLockDetector() {
		final int sleepTime = Math.max(1000, (TickThreading.instance.deadLockTime * 1000) / 6);
		Thread deadlockThread = new Thread(new Runnable() {
			@Override
			public void run() {
				trySleep(10000);
				while (checkForDeadlocks()) {
					trySleep(sleepTime);
				}
			}
		});
		deadlockThread.setName("Deadlock Detector");
		deadlockThread.start();
	}

	public static void tickAhead(int seconds) {
		tick(System.nanoTime() + seconds * 1000000000L);
	}

	public static synchronized long tick(long nanoTime) {
		if (lastTickTime < nanoTime) {
			lastTickTime = nanoTime;
		}
		return nanoTime;
	}

	public static void sendChatSafely(final String message) {
		// This might freeze, if the deadlock was related to the playerlist, so do it in another thread.
		new Thread() {
			@Override
			public void run() {
				MinecraftServer.getServerConfigurationManager(MinecraftServer.getServer())
						.sendPacketToAllPlayers(new Packet3Chat(message));
			}
		}.start();
	}

	private static void tryFixDeadlocks(String stuckManagerName) {
		stuckManagerName += " - ";
		Iterable<Thread> threads = Thread.getAllStackTraces().keySet();
		boolean lastWasKill = false;
		for (Thread thread : threads) {
			if (thread.getName().startsWith(stuckManagerName)) {
				if (lastWasKill) {
					trySleep(5);
				}
				StackTraceElement[] stackTraceElements = thread.getStackTrace();
				int runCount = 0;
				for (StackTraceElement stackTraceElement : stackTraceElements) {
					if ("run".equals(stackTraceElement.getMethodName())) {
						runCount++;
						Log.info(stackTraceElement.toString());
					}
				}
				if (runCount >= 3) {
					thread.stop(new ThreadStuckError("Deadlock detected, appears to be caused by " + stuckManagerName));
					lastWasKill = true;
				}
			}
		}
	}

	boolean checkForDeadlocks() {
		Log.flush();
		long deadTime = System.nanoTime() - lastTickTime;
		if (lastTickTime == 0 || (!MinecraftServer.getServer().isServerRunning() && deadTime < (TickThreading.instance.deadLockTime * 10000000000l))) {
			attemptedToRecoverDeadlock = false;
			return true;
		}
		if (TickThreading.instance.exitOnDeadlock) {
			if (sentWarningRecently && deadTime < 10000000000l) {
				sentWarningRecently = false;
				sendChatSafely(ChatFormat.GREEN + TickThreading.instance.messageDeadlockRecovered);
			} else if (deadTime >= 10000000000l && !sentWarningRecently) {
				sentWarningRecently = true;
				sendChatSafely(String.valueOf(ChatFormat.RED) + ChatFormat.BOLD + TickThreading.instance.messageDeadlockDetected);
				return true;
			}
		}
		if (deadTime < (TickThreading.instance.deadLockTime * 1000000000l)) {
			return true;
		}
		final MinecraftServer minecraftServer = MinecraftServer.getServer();
		if (minecraftServer.currentlySaving.get() != 0) {
			Log.severe("The server seems to have frozen while saving - Waiting for two minutes to give it time to complete.");
			Log.flush();
			trySleep(180000);
			if (minecraftServer.currentlySaving.get() != 0) {
				Log.info("Server still seems to be saving, must've deadlocked.");
				minecraftServer.currentlySaving.set(0);
			} else {
				return true;
			}
		}
		TreeMap<String, String> sortedThreads = new TreeMap<String, String>();
		StringBuilder sb = new StringBuilder();
		ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
		if (!attemptedToRecoverDeadlock) {
			sb
					.append("The server appears to have deadlocked.")
					.append("\nLast tick ").append(deadTime / 1000000000).append("s ago.");
			String prefix = "\nWaiting ThreadManagers: ";
			Set<String> threadManagerSet = new HashSet<String>();
			for (ThreadManager threadManager : DeadLockDetector.threadManagers) {
				if (threadManager.isWaiting()) {
					threadManagerSet.add(threadManager.getName());
				}
			}
			for (ThreadManager threadManager : DeadLockDetector.threadManagers) {
				if (threadManager.isWaiting()) {
					threadManagerSet.remove(threadManager.getParentName());
				}
			}
			for (String threadManager : threadManagerSet) {
				sb.append(prefix).append(threadManager);
				prefix = ", ";
			}
			sb.append("\n\n");
			LoadingCache<String, List<ThreadInfo>> threads = CacheBuilder.newBuilder().build(new CacheLoader<String, List<ThreadInfo>>() {
				@Override
				public List<ThreadInfo> load(final String key) throws Exception {
					return new ArrayList<ThreadInfo>();
				}
			});
			ThreadInfo[] t = threadMXBean.dumpAllThreads(true, true);
			for (ThreadInfo thread : t) {
				String info = toString(thread, false);
				if (info != null) {
					threads.getUnchecked(info).add(thread);
				}
			}
			for (Map.Entry<String, List<ThreadInfo>> entry : threads.asMap().entrySet()) {
				List<ThreadInfo> threadInfoList = entry.getValue();
				ThreadInfo lowest = null;
				for (ThreadInfo threadInfo : threadInfoList) {
					if (lowest == null || threadInfo.getThreadName().toLowerCase().compareTo(lowest.getThreadName().toLowerCase()) < 0) {
						lowest = threadInfo;
					}
				}
				List threadNameList = CollectionsUtil.newList(threadInfoList, new Function<Object, Object>() {
					@Override
					public Object apply(final Object input) {
						return ((ThreadInfo) input).getThreadName();
					}
				});
				Collections.sort(threadNameList);
				sortedThreads.put(lowest.getThreadName(), '"' + CollectionsUtil.join(threadNameList, "\", \"") + "\" " + entry.getKey());
			}
			sb.append(CollectionsUtil.join(sortedThreads.values(), "\n"));
			long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();

			if (deadlockedThreads != null) {
				ThreadInfo[] infos = threadMXBean.getThreadInfo(deadlockedThreads, true, true);
				sb.append("Definitely deadlocked: \n");
				for (ThreadInfo threadInfo : infos) {
					sb.append(toString(threadInfo, true)).append('\n');
				}
			}
			sb.append("\nAttempting to recover without restarting.");
			Log.severe(sb.toString());
			for (String threadManager : threadManagerSet) {
				tryFixDeadlocks(threadManager);
			}
			attemptedToRecoverDeadlock = true;
			return true;
		}
		if (TickThreading.instance.exitOnDeadlock) {
			sendChatSafely(ChatFormat.RED + TickThreading.instance.messageDeadlockSavingExiting);
		}
		Log.severe("Failed to recover from the deadlock.");
		Log.flush();
		if (!TickThreading.instance.exitOnDeadlock) {
			Log.severe("Now attempting to save the world. The server will not stop, you must do this yourself. If you want the server to stop automatically on deadlock, enable exitOnDeadlock in TT's config.");
			minecraftServer.saveEverything();
			return false;
		}
		// Yes, we save multiple times - handleServerStopping may freeze on the same thing we deadlocked on, but if it doesn't might change stuff
		// which needs to be saved.
		minecraftServer.getNetworkThread().stopListening();
		trySleep(500);
		new Thread() {
			@Override
			public void run() {
				// We can't lock here, deadlock may be due to the playerEntityList lock.
				int attempts = 5;
				while (attempts-- > 0) {
					try {
						for (EntityPlayerMP entityPlayerMP : new ArrayList<EntityPlayerMP>(minecraftServer.getConfigurationManager().playerEntityList)) {
							entityPlayerMP.playerNetServerHandler.kickPlayerFromServer("Restarting");
						}
						attempts = 0;
					} catch (ConcurrentModificationException ignored) {
					}
				}
			}
		}.start();
		trySleep(1000);
		Log.info("Attempting to save");
		Log.flush();
		new Thread() {
			@Override
			public void run() {
				trySleep(300000);
				Log.severe("Froze while attempting to stop - halting server.");
				Log.flush();
				Runtime.getRuntime().exit(1);
			}
		}.start();
		minecraftServer.saveEverything(); // Save first
		Log.info("Saved, now attempting to stop the server and disconnect players cleanly");
		try {
			minecraftServer.stopServer();
			FMLCommonHandler.instance().handleServerStopping(); // Try to get mods to save data - this may lock up, as we deadlocked.
		} catch (Throwable throwable) {
			Log.severe("Error stopping server", throwable);
		}
		minecraftServer.saveEverything(); // Save again, in case they changed anything.
		minecraftServer.initiateShutdown();
		Log.flush();
		trySleep(1000);
		Runtime.getRuntime().exit(1);
		return false;
	}

	private static void trySleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ignored) {
		}
	}

	private static String toString(ThreadInfo threadInfo, boolean name) {
		if (threadInfo == null) {
			return null;
		}
		StackTraceElement[] stackTrace = threadInfo.getStackTrace();
		if (stackTrace == null) {
			stackTrace = EMPTY_STACK_TRACE;
		}
		StringBuilder sb = new StringBuilder();
		if (name) {
			sb.append('"').append(threadInfo.getThreadName()).append('"').append(" Id=").append(threadInfo.getThreadId()).append(' ');
		}
		sb.append(threadInfo.getThreadState());
		if (threadInfo.getLockName() != null) {
			sb.append(" on ").append(threadInfo.getLockName());
		}
		if (threadInfo.getLockOwnerName() != null) {
			sb.append(" owned by \"").append(threadInfo.getLockOwnerName()).append("\" Id=").append(threadInfo.getLockOwnerId());
		}
		if (threadInfo.isSuspended()) {
			sb.append(" (suspended)");
		}
		if (threadInfo.isInNative()) {
			sb.append(" (in native)");
		}
		int run = 0;
		sb.append('\n');
		for (int i = 0; i < stackTrace.length; i++) {
			String steString = stackTrace[i].toString();
			if (steString.contains(".run(")) {
				run++;
			}
			sb.append("\tat ").append(steString);
			sb.append('\n');
			if (i == 0 && threadInfo.getLockInfo() != null) {
				Thread.State ts = threadInfo.getThreadState();
				switch (ts) {
					case BLOCKED:
						sb.append("\t-  blocked on ").append(threadInfo.getLockInfo());
						sb.append('\n');
						break;
					case WAITING:
						sb.append("\t-  waiting on ").append(threadInfo.getLockInfo());
						sb.append('\n');
						break;
					case TIMED_WAITING:
						sb.append("\t-  waiting on ").append(threadInfo.getLockInfo());
						sb.append('\n');
						break;
					default:
				}
			}

			for (MonitorInfo mi : threadInfo.getLockedMonitors()) {
				if (mi.getLockedStackDepth() == i) {
					sb.append("\t-  locked ").append(mi);
					sb.append('\n');
				}
			}
		}

		LockInfo[] locks = threadInfo.getLockedSynchronizers();
		if (locks.length > 0) {
			sb.append("\n\tNumber of locked synchronizers = ").append(locks.length);
			sb.append('\n');
			for (LockInfo li : locks) {
				sb.append("\t- ").append(li);
				sb.append('\n');
			}
		}
		sb.append('\n');
		String ret = sb.toString();
		return (run <= 2 && ret.contains("at java.util.concurrent.LinkedBlockingQueue.take(\n") &&
				ret.contains("at me.nallar.tickthreading.minecraft.ThreadManager$1.run(ThreadManager.java:\n")) ? null : ret;
	}

	public static void checkForLeakedThreadManagers() {
		StringBuilder sb = new StringBuilder();
		String prefix = "Leaked ThreadManagers: ";
		for (ThreadManager threadManager : threadManagers) {
			if (threadManager.isWaiting()) {
				sb.append(prefix).append(threadManager.getName());
				prefix = ", ";
			}
		}
		//noinspection StringEquality
		if (prefix == ", ") {
			Log.severe(sb.toString());
		}
	}

	public static void printLocks(final long id) {
		Log.severe(toString(ManagementFactory.getThreadMXBean().getThreadInfo(new long[]{id}, true, true)[0], true));
	}
}
