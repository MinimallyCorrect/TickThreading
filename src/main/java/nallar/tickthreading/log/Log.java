package nallar.tickthreading.log;

import nallar.tickthreading.exception.ThisIsNotAnError;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import java.io.*;
import java.util.*;
import java.util.logging.*;

@SuppressWarnings({"UnusedDeclaration", "UseOfSystemOutOrSystemErr"})
public class Log {
	public static final Logger LOGGER = Logger.getLogger("TickThreading");
	public static final boolean debug = System.getProperty("tickthreading.debug") != null;
	public static final Level DEBUG = DebugLevel.DEBUG;
	private static final int numberOfLogFiles = Integer.valueOf(System.getProperty("tickthreading.numberOfLogFiles", "5"));
	private static final File logFolder = new File("TickThreadingLogs");
	private static final String[] loggersToFlush = {
		"ForgeModLoader",
		"TickThreading",
		"TTPatcher",
	};

	static {
		try {
			Logger parent = Logger.getLogger("ForgeModLoader");
			if (parent == null) {
				throw new NoClassDefFoundError();
			}
			LOGGER.setParent(parent);
			LOGGER.setUseParentHandlers(true);
			Logger mcLogger = Logger.getLogger("Minecraft-Server");
			if (mcLogger.getParent() != parent) {
				mcLogger.setParent(parent);
				mcLogger.setUseParentHandlers(true);
				for (Handler handler : mcLogger.getHandlers()) {
					if (handler instanceof FileHandler) {
						continue;
					}
					handler.flush();
					handler.close();
					mcLogger.removeHandler(handler);
				}
			}
		} catch (NoClassDefFoundError e) {
			// Make this very very obvious.
			System.err.println("--------------------------------------------------------");
			System.err.println("Initialised logger while not running under forge!");
			e.printStackTrace(System.err);
			System.err.println("--------------------------------------------------------");
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
			throw e;
		}
		LOGGER.setLevel(Level.ALL);
		setFileName("tickthreading", LOGGER);
	}

	public static void flushLogs() {
		if (Log.debug && MinecraftServer.getServer().isServerRunning()) {
			Log.debug("Server shutting down - requested at ", new ThisIsNotAnError());
		}
		for (String name : loggersToFlush) {
			for (Handler handler : Logger.getLogger(name).getHandlers()) {
				handler.flush();
			}
		}
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	public static void setFileName(String name, Logger... loggers) {
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
		final File saveFile = new File(logFolder, name + ".1.log");
		saveFile.delete();
		try {
			Handler handler = new FileHandler(saveFile.getCanonicalPath());
			handler.setFormatter(new LogFormatter());
			for (Logger logger : loggers) {
				logger.addHandler(handler);
			}
		} catch (IOException e) {
			Log.severe("Can't write logs to disk", e);
		}
	}

	public static void debug(String msg) {
		debug(msg, null);
	}

	public static void severe(String msg) {
		severe(msg, null);
	}

	public static void warning(String msg) {
		warning(msg, null);
	}

	public static void info(String msg) {
		info(msg, null);
	}

	public static void config(String msg) {
		config(msg, null);
	}

	public static void fine(String msg) {
		fine(msg, null);
	}

	public static void finer(String msg) {
		finer(msg, null);
	}

	public static void finest(String msg) {
		finest(msg, null);
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
			return "Broken world with ID ";// TODO + MinecraftServer.getServer().getId((WorldServer) world);
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
					s += tE.getPos().toString();
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
		return (unloaded ? "un" : "") + "loaded world " + name(world) + '@' + System.identityHashCode(world) + ", dimension: " + world.getDimension() + ", provider dimension: " + (unloaded ? "unknown" : world.provider.getDimensionId()) + ", original dimension: " + world.originalDimension;
	}

	public static void checkWorlds() {
		MinecraftServer minecraftServer = MinecraftServer.getServer();
		int a = minecraftServer.worldServers.length;
		int b = DimensionManager.getWorlds().length;
		if (a != b) {
			Log.severe("World counts mismatch.\n" + dumpWorlds());
		} else if (hasDuplicates(minecraftServer.worldServers) || hasDuplicates(DimensionManager.getWorlds())) {
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
		List<World> dimensionManagerWorlds = new ArrayList<>(Arrays.asList(DimensionManager.getWorlds()));
		MinecraftServer minecraftServer = MinecraftServer.getServer();
		List<World> minecraftServerWorlds = new ArrayList<>(Arrays.asList(minecraftServer.worldServers));
		sb.append("Worlds in dimensionManager: \n").append(dumpWorlds(dimensionManagerWorlds));
		sb.append("Worlds in minecraftServer: \n").append(dumpWorlds(minecraftServerWorlds));
		return sb.toString();
	}

	private static boolean hasDuplicates(Object[] array) {
		return hasDuplicates(Arrays.asList(array));
	}

	private static boolean hasDuplicates(List<?> list) {
		if (list == null) {
			return false;
		}
		Set<Object> set = Collections.newSetFromMap(new IdentityHashMap<>());
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
