package me.nallar.tickthreading;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import cpw.mods.fml.common.FMLLog;
import me.nallar.reporting.Reporter;
import me.nallar.tickthreading.util.MappingUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

@SuppressWarnings ({"UnusedDeclaration", "UseOfSystemOutOrSystemErr"})
public class Log {
	public static Logger LOGGER = Logger.getLogger("TickThreading");
	private static Handler handler;
	private static final int numberOfLogFiles = 5;
	private static final File logFolder = new File("TickThreadingLogs");

	static {
		try {
			Logger parent = FMLLog.getLogger();
			if (parent == null) {
				throw new NoClassDefFoundError();
			}
			LOGGER.setParent(parent);
			LOGGER.setUseParentHandlers(true);
			setFileName("tickthreading", Level.INFO, LOGGER);
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
		handler.flush();
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
		if (world.provider == null) {
			return "Broken world with ID " + MinecraftServer.getServer().getId((WorldServer) world);
		}
		return world.getName();
	}

	public static String toString(Object o) {
		String deobfuscatedName = MappingUtil.debobfuscate(o.getClass().getName());
		return "c " + deobfuscatedName + ' ' + o.toString();
	}

	public static void log(Level level, Throwable throwable, String s) {
		LOGGER.log(level, s, throwable);
	}
}
