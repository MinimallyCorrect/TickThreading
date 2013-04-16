package me.nallar.tickthreading.minecraft;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;

import com.google.common.base.Function;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import javassist.is.faulty.Timings;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.util.ChatFormat;
import me.nallar.tickthreading.util.CollectionsUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.packet.Packet3Chat;
import net.minecraft.server.MinecraftServer;

public class DeadLockDetector {
	private boolean sentWarningRecently = false;
	private static volatile String lastJob = "";
	private static volatile long lastTickTime = 0;
	private static final ITickHandler tickHandler = new ITickHandler() {
		private final EnumSet<TickType> tickTypes = EnumSet.of(TickType.SERVER, TickType.CLIENT);

		@Override
		public void tickStart(EnumSet<TickType> type, Object... tickData) {
			tick("Server tick start");
			if (type.contains(TickType.SERVER)) {
				Timings.tick();
			}
		}

		@Override
		public void tickEnd(EnumSet<TickType> type, Object... tickData) {
		}

		@Override
		public EnumSet<TickType> ticks() {
			return tickTypes;
		}

		@Override
		public String getLabel() {
			return "TickThreading Deadlock Detector";
		}
	};

	public DeadLockDetector() {
		Thread deadlockThread = new Thread(new Runnable() {
			@Override
			public void run() {
				while (checkForDeadlocks()) {
					try {
						Thread.sleep(6000);
					} catch (InterruptedException ignored) {
					}
				}
			}
		});
		deadlockThread.setName("Deadlock Detector");
		deadlockThread.start();
		TickRegistry.registerTickHandler(tickHandler, Side.SERVER);
		TickRegistry.registerTickHandler(tickHandler, Side.CLIENT);
	}

	public static synchronized long tick(String name) {
		return tick(name, System.nanoTime());
	}

	public static synchronized long tick(String name, long time) {
		lastJob = name;
		return lastTickTime = time;
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

	public boolean checkForDeadlocks() {
		Log.flush();
		long deadTime = System.nanoTime() - lastTickTime;
		if (lastTickTime == 0 || (!MinecraftServer.getServer().isServerRunning() && deadTime < (TickThreading.instance.deadLockTime * 10000000000l))) {
			return true;
		}
		if (TickThreading.instance.exitOnDeadlock) {
			if (sentWarningRecently && deadTime < 10000000000l) {
				sentWarningRecently = false;
				sendChatSafely(ChatFormat.GREEN + "The server has recovered and will not need to restart. :)");
			} else if (deadTime > 10000000000l && !sentWarningRecently) {
				sentWarningRecently = true;
				sendChatSafely(String.valueOf(ChatFormat.RED) + ChatFormat.BOLD + "The server appears to have frozen on '" + lastJob + "' and will restart soon if it does not recover. :(");
				return true;
			}
		}
		if (deadTime < (TickThreading.instance.deadLockTime * 1000000000l)) {
			return true;
		}
		if (TickThreading.instance.exitOnDeadlock) {
			sendChatSafely(ChatFormat.RED + "The server is saving the world and restarting - be right back!");
		}
		final MinecraftServer minecraftServer = MinecraftServer.getServer();
		if (minecraftServer.currentlySaving) {
			Log.severe("The server seems to have frozen while saving - Waiting for two minutes to give it time to complete.");
			Log.flush();
			trySleep(120000);
			if (minecraftServer.currentlySaving) {
				Log.info("Server still seems to be saving, must've deadlocked.");
				minecraftServer.currentlySaving = false;
			} else {
				return true;
			}
		}
		TreeMap<String, String> sortedThreads = new TreeMap<String, String>();
		StringBuilder sb = new StringBuilder();
		ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
		sb
				.append("The server appears to have deadlocked.")
				.append("\nLast tick ").append(deadTime / 1000000000).append("s ago.")
				.append("\nTicking: ").append(lastJob).append('\n');
		LoadingCache<String, List<ThreadInfo>> threads = CacheBuilder.newBuilder().build(new CacheLoader<String, List<ThreadInfo>>() {
			@Override
			public List<ThreadInfo> load(final String key) throws Exception {
				return new ArrayList<ThreadInfo>();
			}
		});
		ThreadInfo[] t = threadMXBean.dumpAllThreads(true, true);
		for (ThreadInfo thread : t) {
			try {
				String info = toString(thread, false);
				if (info != null) {
					threads.get(info).add(thread);
				}
			} catch (ExecutionException e) {
				Log.severe("Well... this shouldn't happen...", e);
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
		Log.severe(sb.toString());
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
		} catch (Exception e) {
			Log.severe("Error stopping server", e);
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
			return null;
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
		sb.append('\n');
		for (int i = 0; i < stackTrace.length; i++) {
			StackTraceElement ste = stackTrace[i];
			sb.append("\tat ").append(ste.toString());
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
		return sb.toString();
	}
}
