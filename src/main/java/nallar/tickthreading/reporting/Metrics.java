/*
 * Copyright 2011-2013 Tyler Blair. All rights reserved.
 * Ported to Minecraft Forge by Mike Primm
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 *
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ''AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those of the
 * authors and contributors and should not be interpreted as representing official policies,
 * either expressed or implied, of anybody else.
 */
package nallar.tickthreading.reporting;

import lombok.NonNull;
import lombok.SneakyThrows;
import nallar.tickthreading.log.Log;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.Level;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * <p>
 * The metrics class obtains data about a plugin and submits statistics about it
 * to the metrics backend.
 * </p>
 * <p>
 * Public methods provided by this class:
 * </p>
 * <code>
 * Graph createGraph(String name); <br/>
 * void addCustomData(Metrics.Plotter plotter); <br/>
 * void start(); <br/>
 * </code>
 */
public class Metrics {
	/**
	 * The current revision number
	 */
	private static final int REVISION = 7;
	/**
	 * The base url of the metrics domain
	 */
	private static final String BASE_URL = "http://mcstats.org";
	/**
	 * The url used to report a server's status
	 */
	private static final String REPORT_URL = "/report/%s";
	/**
	 * Interval of time to ping (in minutes)
	 */
	private static final int PING_INTERVAL = 15;
	/**
	 * The mod this metrics submits for
	 */
	private final String modname;
	private final String modversion;
	/**
	 * The metrics configuration file
	 */
	private final Configuration configuration;
	/**
	 * Unique server id
	 */
	private final String guid;
	/**
	 * Debug mode
	 */
	private final boolean debug;
	private int tickCount;
	private Thread thrd = null;
	private boolean firstPost = true;

	public Metrics(@NonNull final String modname, @NonNull final String modversion) {
		this.modname = modname;
		this.modversion = modversion;

		// load the config
		configuration = new Configuration(getConfigFile());

		// Get values, and add some defaults, if needed
		configuration.get(Configuration.CATEGORY_GENERAL, "opt-out", false, "Set to true to disable all reporting");
		guid = configuration.get(Configuration.CATEGORY_GENERAL, "guid", UUID.randomUUID().toString(), "Server unique ID").getString();
		debug = configuration.get(Configuration.CATEGORY_GENERAL, "debug", false, "Set to true for verbose debug").getBoolean(false);
		configuration.save();

		if (!isOptOut() && start())
			Log.info("Started TickProfiler mcstats.org metrics reporting. This can be disabled in PluginMetrics.cfg");
	}

	/**
	 * Gets the File object of the config file that should be used to store data
	 * such as the GUID and opt-out status
	 *
	 * @return the File object for the config file
	 */
	private static File getConfigFile() {
		return new File(Loader.instance().getConfigDir(), "PluginMetrics.cfg");
	}

	/**
	 * <p>
	 * Encode a key/value data pair to be used in a HTTP post request. This
	 * INCLUDES a & so the first key/value pair MUST be included manually, e.g:
	 * </p>
	 * <code>
	 * StringBuffer data = new StringBuffer();
	 * data.append(encode("guid")).append('=').append(encode(guid));
	 * encodeDataPair(data, "version", description.getVersion());
	 * </code>
	 *
	 * @param buffer the stringbuilder to append the data pair onto
	 * @param key    the key value
	 * @param value  the value
	 */
	private static void encodeDataPair(final StringBuilder buffer, final String key, final String value) {
		buffer.append('&').append(encode(key)).append('=').append(encode(value));
	}

	/**
	 * Encode text as UTF-8
	 *
	 * @param text the text to encode
	 * @return the encoded text, as UTF-8
	 */
	@SneakyThrows
	private static String encode(final String text) {
		return URLEncoder.encode(text, "UTF-8");
	}

	/**
	 * Start measuring statistics. This will immediately create an async
	 * repeating task as the plugin and send the initial data to the metrics
	 * backend, and then after that it will post in increments of PING_INTERVAL
	 * * 1200 ticks.
	 *
	 * @return True if statistics measuring is running, otherwise false.
	 */
	private boolean start() {
		// Did we opt out?
		if (isOptOut()) {
			return false;
		}

		MinecraftForge.EVENT_BUS.register(this);

		return true;
	}

	@SubscribeEvent
	public void tick(TickEvent.ServerTickEvent tick) {
		if (tick.phase != TickEvent.Phase.END) return;

		if (tickCount++ % (PING_INTERVAL * 1200) != 0) return;

		if (thrd == null) {
			thrd = new Thread(() -> {
				try {
					// We use the inverse of firstPost because if it
					// is the first time we are posting,
					// it is not a interval ping, so it evaluates to
					// FALSE
					// Each time thereafter it will evaluate to
					// TRUE, i.e PING!
					postPlugin(!firstPost);
					// After the first post we set firstPost to
					// false
					// Each post thereafter will be a ping
					firstPost = false;
				} catch (Throwable t) {
					//noinspection ConstantConditions
					if (debug || !(t instanceof IOException)) {
						FMLLog.log(Level.WARN, t, "[Metrics] Exception");
					}
				} finally {
					thrd = null;
				}
			});
			thrd.start();
		}
	}

	/**
	 * Has the server owner denied plugin metrics?
	 *
	 * @return true if metrics should be opted out of it
	 */
	private boolean isOptOut() {
		// Reload the metrics file
		return configuration.get(Configuration.CATEGORY_GENERAL, "opt-out",
			false).getBoolean(false);
	}

	/**
	 * Generic method that posts a plugin to the metrics website
	 */
	@SneakyThrows
	private void postPlugin(final boolean isPing) {
		// Server software specific section
		boolean onlineMode = FMLCommonHandler.instance().getMinecraftServerInstance().isServerInOnlineMode(); // TRUE
		String serverVersion = FMLCommonHandler.instance().getMinecraftServerInstance().getServerModName() + " (MC: "
			+ FMLCommonHandler.instance().getMinecraftServerInstance().getMinecraftVersion() + ')';
		int playersOnline = FMLCommonHandler.instance().getMinecraftServerInstance().getCurrentPlayerCount();

		// END server software specific section -- all code below does not use
		// any code outside of this class / Java

		// Construct the post data
		final StringBuilder data = new StringBuilder();

		// The plugin's description file containg all of the plugin data such as
		// name, version, author, etc
		data.append(encode("guid")).append('=').append(encode(guid));
		encodeDataPair(data, "version", modversion);
		encodeDataPair(data, "server", serverVersion);
		encodeDataPair(data, "players", Integer.toString(playersOnline));
		encodeDataPair(data, "revision", String.valueOf(REVISION));

		// New data as of R6
		String osname = System.getProperty("os.name");
		String osarch = System.getProperty("os.arch");
		String osversion = System.getProperty("os.version");
		String java_version = System.getProperty("java.version");
		int coreCount = Runtime.getRuntime().availableProcessors();

		// normalize os arch .. amd64 -> x86_64
		if ("amd64".equals(osarch)) {
			osarch = "x86_64";
		}

		encodeDataPair(data, "osname", osname);
		encodeDataPair(data, "osarch", osarch);
		encodeDataPair(data, "osversion", osversion);
		encodeDataPair(data, "cores", Integer.toString(coreCount));
		encodeDataPair(data, "online-mode", Boolean.toString(onlineMode));
		encodeDataPair(data, "java_version", java_version);

		// If we're pinging, append it
		if (isPing) {
			encodeDataPair(data, "ping", "true");
		}

		// Create the url
		URL url = new URL(BASE_URL + String.format(REPORT_URL, encode(modname)));

		// Connect to the website
		URLConnection connection = url.openConnection();
		connection.setDoOutput(true);

		// Write the data
		try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())) {
			writer.write(data.toString());
			writer.flush();
		}

		// Now read the response
		final String response;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
			response = reader.readLine();
		}

		if (response == null || response.startsWith("ERR")) {
			throw new IOException(response); // Throw the exception
		}
	}
}
