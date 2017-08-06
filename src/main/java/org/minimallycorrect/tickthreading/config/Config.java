package org.minimallycorrect.tickthreading.config;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.val;
import org.minimallycorrect.typedconfig.TypedConfig;
import org.minimallycorrect.typedconfig.TypedConfig.Entry;

import java.io.*;
import java.nio.file.*;

@EqualsAndHashCode
@ToString
public class Config {
	public static final Config $ = loadConfig();

	@Entry(description = "Target ticks per second")
	public int targetTps = 20;
	// TODO implement simple deadlock detector
	@Entry(description = "Maximum frozen time before deadlock detector assumes a deadlock has occurred")
	public int deadLockSeconds = 15;
	@Entry(description = "Enable world unloading")
	public boolean worldUnloading = true;
	@Entry(description = "Enable cleaning of unloaded worlds to aid in determining the cause of world leaks")
	public boolean worldCleaning = true;
	@Entry(description = "[extended] Separate per-world tick loops. When enabled each world's main loop is decoupled from other worlds. When disabled, the slowest world will reduce the TPS of all worlds.")
	public boolean separatePerWorldTickLoops = true;

	private static Config loadConfig() {
		val typedConfig = TypedConfig.of(Config.class, Paths.get("config", "tickthreading", "tickthreading.cfg"));
		val c = typedConfig.load();
		typedConfig.save(c);
		return c;
	}
}
