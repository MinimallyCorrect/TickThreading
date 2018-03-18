package org.minimallycorrect.tickthreading.log;

import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

import lombok.SneakyThrows;
import lombok.val;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;

import org.minimallycorrect.tickthreading.util.PropertyUtil;
import org.minimallycorrect.tickthreading.util.Version;

import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;

@SuppressWarnings({"UnusedDeclaration"})
public class Log {
	private static final Path logFolder = Paths.get("TickThreadingLogs");
	private static final int numberOfLogFiles = PropertyUtil.get("numberOfLogFiles", 5);
	private static final String[] logsToSave = new String[]{
			"@MOD_NAME@",
			"JavaPatcher",
			"JavaTransformer",
			"LibLoader",
			"Mixin",
			"ModPatcher",
	};
	private static final Logger LOGGER = LogManager.getLogger(Version.NAME);

	static {
		createLogFiles();
	}

	@SneakyThrows
	private static void createLogFiles() {
		if (!Files.isDirectory(logFolder))
			Files.createDirectory(logFolder);
		for (int i = numberOfLogFiles; i >= 1; i--) {
			val currentFile = logFolder.resolve(Version.NAME + '.' + i + ".log");
			if (Files.exists(currentFile)) {
				if (i == numberOfLogFiles) {
					Files.delete(currentFile);
				} else {
					Files.move(currentFile, logFolder.resolve(Version.NAME + '.' + (i + 1) + ".log"));
				}
			}
		}

		// TODO: rewrite this next bit using log4j public API at some point? gave up trying since it didn't work

		val saveFile = logFolder.resolve(Version.NAME + ".1.log");
		final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
		final Configuration config = ctx.getConfiguration();
		// can't define a static field for an anonymous class, so no serialVersionUID
		@SuppressWarnings("serial")
		val layout = new AbstractStringLayout(Charset.forName("UTF-8")) {
			@Override
			public String toSerializable(LogEvent event) {
				return "[" +
					event.getLoggerName() +
					'/' +
					event.getThreadName() +
					'/' +
					event.getLevel().name() +
					"] " +
					event.getMessage().getFormattedMessage() +
					'\n';
			}
		};
		val appender = FileAppender.createAppender(saveFile.toAbsolutePath().toString(), "false", "false", "File", "true", "false", "false", "4000", layout, null, "false", null, config);
		appender.start();
		config.addAppender(appender);
		for (val logName : logsToSave)
			((org.apache.logging.log4j.core.Logger) LogManager.getLogger(logName)).addAppender(appender);
		ctx.updateLoggers();
	}

	public static void error(String msg) {
		LOGGER.error(msg);
	}

	public static void warn(String msg) {
		LOGGER.warn(msg);
	}

	public static void info(String msg) {
		LOGGER.info(msg);
	}

	public static void trace(String msg) {
		LOGGER.trace(msg);
	}

	public static void error(String msg, Throwable t) {
		LOGGER.log(Level.ERROR, msg, t);
	}

	public static void warn(String msg, Throwable t) {
		LOGGER.log(Level.WARN, msg, t);
	}

	public static void info(String msg, Throwable t) {
		LOGGER.log(Level.INFO, msg, t);
	}

	public static void trace(String msg, Throwable t) {
		LOGGER.log(Level.TRACE, msg, t);
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
			Log.error("Failed to perform toString on object of class " + o.getClass(), t);
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

	private static String dumpWorld(World world) {
		boolean unloaded = world.unloaded;
		return (unloaded ? "un" : "") + "loaded world " + name(world) + '@' + System.identityHashCode(world) + ", dimension: " + world.getDimensionId();
	}

	public static void checkWorlds() {
		MinecraftServer minecraftServer = FMLCommonHandler.instance().getMinecraftServerInstance();
		val worlds = minecraftServer.worlds;
		int a = worlds.length;
		int b = DimensionManager.getWorlds().length;
		if (a != b) {
			Log.error("World counts mismatch.\n" + dumpWorlds());
		} else if (hasDuplicates(worlds) || hasDuplicates(DimensionManager.getWorlds())) {
			Log.error("Duplicate worlds.\n" + dumpWorlds());
		} else {
			for (WorldServer worldServer : worlds) {
				if (worldServer.unloaded || worldServer.provider == null) {
					Log.error("Broken/unloaded world in worlds list.\n" + dumpWorlds());
				}
			}
		}
	}

	private static String dumpWorlds() {
		StringBuilder sb = new StringBuilder();
		List<World> dimensionManagerWorlds = new ArrayList<>(Arrays.asList(DimensionManager.getWorlds()));
		MinecraftServer minecraftServer = FMLCommonHandler.instance().getMinecraftServerInstance();
		List<World> minecraftServerWorlds = new ArrayList<>(Arrays.asList(minecraftServer.worlds));
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
