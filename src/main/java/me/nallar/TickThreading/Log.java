package me.nallar.tickthreading;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Log {
	private static final Logger logger = Logger.getLogger("Minecraft");

	public static void severe(String msg) {
		logger.severe(msg);
	}

	public static void warning(String msg) {
		logger.warning(msg);
	}

	public static void info(String msg) {
		logger.info(msg);
	}

	public static void config(String msg) {
		logger.config(msg);
	}

	public static void fine(String msg) {
		logger.fine(msg);
	}

	public static void finer(String msg) {
		logger.finer(msg);
	}

	public static void finest(String msg) {
		logger.finest(msg);
	}

	public static void severe(String msg, Throwable t) {
		logger.log(Level.SEVERE, msg, t);
	}

	public static void warning(String msg, Throwable t) {
		logger.log(Level.WARNING, msg, t);
	}

	public static void info(String msg, Throwable t) {
		logger.log(Level.INFO, msg, t);
	}

	public static void config(String msg, Throwable t) {
		logger.log(Level.CONFIG, msg, t);
	}

	public static void fine(String msg, Throwable t) {
		logger.log(Level.FINE, msg, t);
	}

	public static void finer(String msg, Throwable t) {
		logger.log(Level.FINER, msg, t);
	}

	public static void finest(String msg, Throwable t) {
		logger.log(Level.FINEST, msg, t);
	}
}
