package nallar.tickthreading;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import cpw.mods.fml.common.FMLLog;
import nallar.reporting.Reporter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.gui.TextAreaLogHandler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

@SuppressWarnings ({"UnusedDeclaration", "UseOfSystemOutOrSystemErr"})
public class Log {
	public static final Logger LOGGER = Logger.getLogger("TickThreading");
	public static final boolean debug = System.getProperty("tickthreading.debug") != null;
	public static final Level DEBUG = new Level("DEBUG", Level.SEVERE.intValue(), null) {
		// Inner class as constructors are protected.
	};
	private static Handler handler;
	@SuppressWarnings ("UseOfArchaicSystemPropertyAccessors") // Need a default value.
	private static final int numberOfLogFiles = Integer.getInteger("tickthreading.numberOfLogFiles", 5);
	private static final File logFolder = new File("TickThreadingLogs");
	private static Handler wrappedHandler;
	private static final Handler handlerWrapper = new Handler() {
		Pattern pattern = Pattern.compile("\\P{Print}");

		@Override
		public void publish(final LogRecord record) {
			String initialMessage = record.getMessage();
			String sanitizedMessage = java.text.Normalizer.normalize(initialMessage, Normalizer.Form.NFC).replace("\r\n", "\n");
			sanitizedMessage = pattern.matcher(sanitizedMessage).replaceAll("");
			record.setMessage(sanitizedMessage);
			synchronized (wrappedHandler) {
				try {
					wrappedHandler.publish(record);
				} catch (Throwable ignored) {
				}
			}
			record.setMessage(initialMessage);
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() throws SecurityException {
		}
	};

	static {
		try {
			Logger parent = FMLLog.getLogger();
			if (parent == null) {
				throw new NoClassDefFoundError();
			}
			LOGGER.setParent(parent);
			LOGGER.setUseParentHandlers(true);
			setFileName("tickthreading", Level.INFO, LOGGER);
			Logger minecraftLogger = Logger.getLogger("Minecraft");
			for (Handler handler : minecraftLogger.getHandlers()) {
				if (handler instanceof TextAreaLogHandler) {
					if (!Arrays.asList(parent.getHandlers()).contains(handlerWrapper)) {
						wrappedHandler = handler;
						parent.addHandler(handlerWrapper);
						break;
					}
				}
			}
		} catch (NoClassDefFoundError ignored) {
			// Not running under forge
			LOGGER.setUseParentHandlers(false);
			LOGGER.addHandler(new Handler() {
				private final LogFormatter logFormatter = new LogFormatter();

				@Override
				public void publish(LogRecord record) {
					System.out.print(logFormatter.format(record));
				}

				@Override
				public void flush() {
				}

				@Override
				public void close() throws SecurityException {
				}
			});
		}
		LOGGER.setLevel(Level.ALL);
	}

	public static synchronized void disableDiskWriting(String finalMessage) {
		Handler handler = Log.handler;
		if (handler == null) {
			return;
		}
		Log.handler = null;
		LogRecord finalRecord = new LogRecord(Level.SEVERE, finalMessage);
		try {
			Logger fmlLog = FMLLog.getLogger();
			for (Handler handler1 : fmlLog.getHandlers()) {
				if (handler1 instanceof FileHandler) {
					fmlLog.removeHandler(handler1);
					handler1.publish(finalRecord);
					handler1.flush();
				}
			}
		} catch (NoClassDefFoundError ignored) {

		}
		handler.publish(finalRecord);
		handler.flush();
		LOGGER.removeHandler(handler);
		Log.severe(finalMessage);
	}

	public static void setFileName(String name, final Level minimumLevel, Logger... loggers) {
		logFolder.mkdir();
		for (int i = numberOfLogFiles; i >= 1; i--) {
			File currentFile = new File(logFolder, name + '.' + i + ".log");
			if (currentFile.exists()) {
				if (i == numberOfLogFiles) {
					currentFile.delete();
				} else {
					currentFile.renameTo(new File(logFolder, name + '.' + (i + 1) + ".log"));
				}
			}
		}
		// TODO: Remove after two recommended builds
		File oldNamingFile = new File(logFolder, name + ".log");
		if (oldNamingFile.exists()) {
			oldNamingFile.renameTo(new File(logFolder, name + ".2.log"));
		}
		final File saveFile = new File(logFolder, name + ".1.log");
		try {
			RandomAccessFile randomAccessFile = new RandomAccessFile(saveFile, "rw");
			try {
				randomAccessFile.setLength(0);
			} finally {
				randomAccessFile.close();
			}
			//noinspection IOResourceOpenedButNotSafelyClosed
			handler = new Handler() {
				private final LogFormatter logFormatter = new LogFormatter();
				private final BufferedWriter outputWriter = new BufferedWriter(new FileWriter(saveFile));

				@Override
				public void publish(LogRecord record) {
					if (record.getLevel().intValue() >= minimumLevel.intValue()) {
						try {
							outputWriter.write(logFormatter.format(record));
						} catch (IOException ignored) {
							// Can't log here, might cause infinite recursion
						}
					}
				}

				@Override
				public void flush() {
					try {
						outputWriter.flush();
					} catch (IOException ignored) {
					}
				}

				@Override
				public void close() throws SecurityException {
					try {
						outputWriter.close();
					} catch (IOException ignored) {
						// ignored - shouldn't log if logging fails
					}
				}
			};
			for (Logger logger : loggers) {
				logger.addHandler(handler);
			}
		} catch (IOException e) {
			Log.severe("Can't write logs to disk", e);
		}
	}

	public static void flush() {
		if (handler != null) {
			handler.flush();
		}
	}

	public static void debug(String msg) {
		debug(msg, null);
	}

	public static void severe(String msg) {
		LOGGER.severe(msg);
	}

	public static void warning(String msg) {
		LOGGER.warning(msg);
	}

	public static void info(String msg) {
		LOGGER.info(msg);
	}

	public static void config(String msg) {
		LOGGER.config(msg);
	}

	public static void fine(String msg) {
		LOGGER.fine(msg);
	}

	public static void finer(String msg) {
		LOGGER.finer(msg);
	}

	public static void finest(String msg) {
		LOGGER.finest(msg);
	}

	public static void severe(String msg, Throwable t, boolean report) {
		if (report) {
			Reporter.report(t);
		}
		severe(msg, t);
	}

	public static void debug(String msg, Throwable t) {
		if (debug) {
			LOGGER.log(DEBUG, msg, t);
		} else {
			throw new Error("Logged debug message when not in debug mode.");
			// To prevent people logging debug messages which will just be ignored, wasting resources constructing the message.
			// (s/people/me being lazy/ :P)
		}
	}

	public static void severe(String msg, Throwable t) {
		LOGGER.log(Level.SEVERE, msg, t);
	}

	public static void warning(String msg, Throwable t) {
		LOGGER.log(Level.WARNING, msg, t);
	}

	public static void info(String msg, Throwable t) {
		LOGGER.log(Level.INFO, msg, t);
	}

	public static void config(String msg, Throwable t) {
		LOGGER.log(Level.CONFIG, msg, t);
	}

	public static void fine(String msg, Throwable t) {
		LOGGER.log(Level.FINE, msg, t);
	}

	public static void finer(String msg, Throwable t) {
		LOGGER.log(Level.FINER, msg, t);
	}

	public static void finest(String msg, Throwable t) {
		LOGGER.log(Level.FINEST, msg, t);
	}

	public static String name(World world) {
		if (world == null) {
			return "null world.";
		} else if (world.provider == null) {
			return "Broken world with ID " + MinecraftServer.getServer().getId((WorldServer) world);
		}
		return world.getName();
	}

	public static String classString(Object o) {
		return "c " + o.getClass().getName() + ' ';
	}

	public static String toString(Object o) {
		try {
			if (o instanceof World) {
				return name((World) o);
			}
			String cS = classString(o);
			String s = o.toString();
			if (!s.startsWith(cS)) {
				s = cS + s;
			}
			if (o instanceof TileEntity) {
				TileEntity tE = (TileEntity) o;
				if (!s.contains(" x, y, z: ")) {
					s += " x, y, z: " + tE.xCoord + ", " + tE.yCoord + ", " + tE.zCoord;
				}
			}
			return s;
		} catch (Throwable t) {
			Log.severe("Failed to perform toString on object of class " + o.getClass(), t);
			return "unknown";
		}
	}

	public static void log(Level level, Throwable throwable, String s) {
		LOGGER.log(level, s, throwable);
	}

	public static String pos(final World world, final int x, final int z) {
		return "in " + world.getName() + ' ' + pos(x, z);
	}

	public static String pos(final int x, final int z) {
		return x + ", " + z;
	}

	public static String pos(final World world, final int x, final int y, final int z) {
		return "in " + world.getName() + ' ' + pos(x, y, z);
	}

	public static String pos(final int x, final int y, final int z) {
		return "at " + x + ", " + y + ", " + z;
	}

	public static String dumpWorld(World world) {
		boolean unloaded = world.unloaded;
		return (unloaded ? "un" : "") + "loaded world " + name(world) + '@' + System.identityHashCode(world) + ", dimension: " + world.getDimension() + ", provider dimension: " + (unloaded ? "unknown" : world.provider.dimensionId) + ", original dimension: " + world.originalDimension;
	}

	public static void checkWorlds() {
		MinecraftServer minecraftServer = MinecraftServer.getServer();
		List<WorldServer> bukkitWorldList = minecraftServer.worlds;
		int a = bukkitWorldList == null ? -1 : bukkitWorldList.size();
		int b = minecraftServer.worldServers.length;
		int c = DimensionManager.getWorlds().length;
		if ((a != -1 && a != b) || b != c) {
			Log.severe("World counts mismatch.\n" + dumpWorlds());
		} else if (hasDuplicates(bukkitWorldList) || hasDuplicates(minecraftServer.worldServers) || hasDuplicates(DimensionManager.getWorlds())) {
			Log.severe("Duplicate worlds.\n" + dumpWorlds());
		} else {
			for (WorldServer worldServer : minecraftServer.worldServers) {
				if (worldServer.unloaded || worldServer.provider == null) {
					Log.severe("Broken/unloaded world in worlds list.\n" + dumpWorlds());
				}
			}
		}
	}

	public static String dumpWorlds() {
		StringBuilder sb = new StringBuilder();
		List<World> dimensionManagerWorlds = new ArrayList<World>(Arrays.asList(DimensionManager.getWorlds()));
		MinecraftServer minecraftServer = MinecraftServer.getServer();
		List<World> minecraftServerWorlds = new ArrayList<World>(Arrays.asList(minecraftServer.worldServers));
		List<WorldServer> bukkitWorldList = minecraftServer.worlds;
		List<World> bukkitWorlds = bukkitWorldList == null ? null : new ArrayList<World>(bukkitWorldList);
		if (bukkitWorlds != null) {
			sb.append("Worlds in bukkitWorlds: \n").append(dumpWorlds(bukkitWorlds));
		}
		sb.append("Worlds in dimensionManager: \n").append(dumpWorlds(dimensionManagerWorlds));
		sb.append("Worlds in minecraftServer: \n").append(dumpWorlds(minecraftServerWorlds));
		return sb.toString();
	}

	private static boolean hasDuplicates(Object[] array) {
		return hasDuplicates(Arrays.asList(array));
	}

	private static boolean hasDuplicates(List list) {
		if (list == null) {
			return false;
		}
		Set<Object> set = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
		for (Object o : list) {
			if (!set.add(o)) {
				return true;
			}
		}
		return false;
	}

	private static String dumpWorlds(final Collection<World> worlds) {
		StringBuilder sb = new StringBuilder();
		for (World world : worlds) {
			sb.append(dumpWorld(world)).append('\n');
		}
		return sb.toString();
	}
}
