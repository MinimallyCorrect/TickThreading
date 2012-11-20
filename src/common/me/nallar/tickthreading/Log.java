package me.nallar.tickthreading;

import java.util.logging.Level;
import java.util.logging.Logger;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.src.World;

@SuppressWarnings ("UnusedDeclaration")
public class Log {
	private static final Logger LOGGER = Logger.getLogger("TickThreading");

	static {
		LOGGER.setParent(FMLLog.getLogger());
		LOGGER.setLevel(Level.ALL);
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
		return world.getWorldInfo().getWorldName() + ":" + world.getWorldInfo().getDimension() + ":" + world.hashCode();
	}
}
