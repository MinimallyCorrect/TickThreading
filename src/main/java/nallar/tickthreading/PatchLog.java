package nallar.tickthreading;

import cpw.mods.fml.relauncher.FMLRelaunchLog;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.logging.*;

@SuppressWarnings({"UnusedDeclaration", "UseOfSystemOutOrSystemErr"})
public class PatchLog {
	public static final Logger LOGGER = Logger.getLogger("TTPatcher");
	public static final boolean debug = System.getProperty("tickthreading.debug") != null;
	public static final Level DEBUG = new Level("DEBUG", Level.SEVERE.intValue(), null) {
		// Inner class as Level's constructor is protected, so that user log levels will be of their own class.
	};
	private static Handler handler;
	private static final int numberOfLogFiles = Integer.valueOf(System.getProperty("tickthreading.numberOfLogFiles", "5"));
	private static final File logFolder = new File("TTPatcherLogs");
	private static final boolean FINE_TO_FILE = true;
	private static final Logger fineLogger = Logger.getLogger("TTPatcher" + Character.toString((char) 255));

	public static void coloriseLogger() {
		try {
			Field consoleLogThreadField = FMLRelaunchLog.class.getDeclaredField("consoleLogThread");
			consoleLogThreadField.setAccessible(true);
			Thread consoleLogThreadThread = (Thread) consoleLogThreadField.get(null);
			Field target = Thread.class.getDeclaredField("target");
			target.setAccessible(true);
			Object consoleLogThread = target.get(consoleLogThreadThread);
			Field handlerField = consoleLogThread.getClass().getDeclaredField("wrappedHandler");
			Handler wrappedHandler = (Handler) handlerField.get(consoleLogThreadThread);
			wrappedHandler.setFormatter(new ColorLogFormatter());
		} catch (Exception e) {
			System.err.println("Failed to replace FML logger with colorised logger");
			e.printStackTrace(System.err);
		}
	}

	static {
		coloriseLogger();
		try {
			final Logger parent = Logger.getLogger("TickThreading");
			if (parent == null) {
				throw new NoClassDefFoundError();
			}
			LOGGER.setParent(parent);
			LOGGER.setUseParentHandlers(true);
			LOGGER.addHandler(new Handler() {
				private final LogFormatter logFormatter = new LogFormatter();

				@Override
				public void publish(LogRecord record) {
					if (parent.getHandlers().length == 0) {
						System.out.print(logFormatter.format(record));
					}
				}

				@Override
				public void flush() {
				}

				@Override
				public void close() throws SecurityException {
				}
			});
		} catch (NoClassDefFoundError ignored) {
			System.err.println("Failed to get parent logger.");
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
		setFileName("patcher", Level.ALL, LOGGER);
		if (FINE_TO_FILE) {
			LOGGER.setLevel(Level.INFO);
			fineLogger.setParent(LOGGER);
			fineLogger.addHandler(handler);
			fineLogger.setUseParentHandlers(false);
			fineLogger.setLevel(Level.ALL);
		} else {
			LOGGER.setLevel(Level.ALL);
		}
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
							outputWriter.write(logFormatter.format(record).replace(Character.toString((char) 255), ""));
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
			PatchLog.severe("Can't write logs to disk", e);
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
		(FINE_TO_FILE ? fineLogger : LOGGER).log(Level.FINE, msg, t);
	}

	public static void finer(String msg, Throwable t) {
		(FINE_TO_FILE ? fineLogger : LOGGER).log(Level.FINER, msg, t);
	}

	public static void finest(String msg, Throwable t) {
		(FINE_TO_FILE ? fineLogger : LOGGER).log(Level.FINEST, msg, t);
	}

	public static String classString(Object o) {
		return "c " + o.getClass().getName() + ' ';
	}

	public static void log(Level level, Throwable throwable, String s) {
		LOGGER.log(level, s, throwable);
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
}
