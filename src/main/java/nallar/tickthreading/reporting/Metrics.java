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

import lombok.val;
import nallar.tickthreading.log.Log;
import net.minecraft.world.World;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

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
@SuppressWarnings({"unused"})
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
	 * The separator to use for custom data. This MUST NOT change unless you are
	 * hosting your own version of metrics and want to change it.
	 */
	private static final String CUSTOM_DATA_SEPARATOR = "~~";
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
	 * All of the custom graphs to submit to metrics
	 */
	private final Set<Graph> graphs = Collections.synchronizedSet(new HashSet<>());
	/**
	 * The default graph, used for addCustomData when you don't want a specific
	 * graph
	 */
	private final Graph defaultGraph = new Graph("Default");
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
	int tickCount;
	private Thread thrd = null;
	private boolean firstPost = true;

	public Metrics(final String modname, final String modversion) {
		if ((modname == null) || (modversion == null)) {
			throw new IllegalArgumentException(
				"modname and modversion cannot be null");
		}

		this.modname = modname;
		this.modversion = modversion;

		// load the config
		configuration = new Configuration(getConfigFile());

		// Get values, and add some defaults, if needed
		configuration.get(Configuration.CATEGORY_GENERAL, "opt-out", false,
			"Set to true to disable all reporting");
		guid = configuration.get(Configuration.CATEGORY_GENERAL, "guid", UUID
			.randomUUID().toString(), "Server unique ID").getString();
		debug = configuration.get(Configuration.CATEGORY_GENERAL, "debug",
			false, "Set to true for verbose debug").getBoolean(false);
		configuration.save();
		if (!isOptOut()) {
			Graph graph = createGraph("Perf");
			graph.addPlotter(new TileEntityPlotter());
			graph.addPlotter(new EntityPlotter());
			graph.addPlotter(new ChunkPlotter());
			if (start()) {
				Log.trace("Started TT metrics reporting. This can be disabled in PluginMetrics.cfg");
			}
		}
	}

	/**
	 * Gets the File object of the config file that should be used to store data
	 * such as the GUID and opt-out status
	 *
	 * @return the File object for the config file
	 */
	public static File getConfigFile() {
		return new File(Loader.instance().getConfigDir(), "PluginMetrics.cfg");
	}

	/**
	 * Check if mineshafter is present. If it is, we need to bypass it to send
	 * POST requests
	 *
	 * @return true if mineshafter is installed on the server
	 */
	private static boolean isMineshafterPresent() {
		try {
			Class.forName("mineshafter.MineServer");
			return true;
		} catch (Exception e) {
			return false;
		}
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
	private static void encodeDataPair(final StringBuilder buffer,
									   final String key, final String value)
		throws UnsupportedEncodingException {
		buffer.append('&').append(encode(key)).append('=')
			.append(encode(value));
	}

	/**
	 * Encode text as UTF-8
	 *
	 * @param text the text to encode
	 * @return the encoded text, as UTF-8
	 */
	private static String encode(final String text)
		throws UnsupportedEncodingException {
		return URLEncoder.encode(text, "UTF-8");
	}

	/**
	 * Construct and create a Graph that can be used to separate specific
	 * plotters to their own graphs on the metrics website. Plotters can be
	 * added to the graph object returned.
	 *
	 * @param name The name of the graph
	 * @return Graph object created. Will never return NULL under normal
	 * circumstances unless bad parameters are given
	 */
	public Graph createGraph(final String name) {
		if (name == null) {
			throw new IllegalArgumentException("Graph name cannot be null");
		}

		// Construct the graph object
		final Graph graph = new Graph(name);

		// Now we can add our graph
		graphs.add(graph);

		// and return back
		return graph;
	}

	/**
	 * Add a Graph object to Metrics that represents data for the plugin that
	 * should be sent to the backend
	 *
	 * @param graph The name of the graph
	 */
	public void addGraph(final Graph graph) {
		if (graph == null) {
			throw new IllegalArgumentException("Graph cannot be null");
		}

		graphs.add(graph);
	}

	/**
	 * Adds a custom data plotter to the default graph
	 *
	 * @param plotter The plotter to use to plot custom data
	 */
	public void addCustomData(final Plotter plotter) {
		if (plotter == null) {
			throw new IllegalArgumentException("Plotter cannot be null");
		}

		// Add the plotter to the graph o/
		defaultGraph.addPlotter(plotter);

		// Ensure the default graph is included in the submitted graphs
		graphs.add(defaultGraph);
	}

	/**
	 * Start measuring statistics. This will immediately create an async
	 * repeating task as the plugin and send the initial data to the metrics
	 * backend, and then after that it will post in increments of PING_INTERVAL
	 * * 1200 ticks.
	 *
	 * @return True if statistics measuring is running, otherwise false.
	 */
	public boolean start() {
		// Did we opt out?
		if (isOptOut()) {
			return false;
		}

		MinecraftForge.EVENT_BUS.register(this);

		return true;
	}

	@SuppressWarnings("FieldRepeatedlyAccessedInMethod")
	@SubscribeEvent
	public void tick(TickEvent.ServerTickEvent tick) {
		if (tick.phase != TickEvent.Phase.END) return;

		if (tickCount++ % (PING_INTERVAL * 1200) != 0) return;

		if (thrd == null) {
			thrd = new Thread(() -> {
				try {
					// Disable Task, if it is running and the server owner decided
					// to opt-out
					if (isOptOut()) {
						// Tell all plotters to stop gathering information.
						graphs.forEach(Graph::onOptOut);

						MinecraftForge.EVENT_BUS.unregister(this);
						return;
					}
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
				} catch (IOException e) {
					if (debug) {
						FMLLog.info("[Metrics] Exception - %s",
							e.getMessage());
					}
				} finally {
					thrd = null;
				}
			});
			thrd.start();
		}
	}

	/**
	 * Stop processing
	 */
	public void stop() {
		MinecraftForge.EVENT_BUS.unregister(this);
	}

	/**
	 * Has the server owner denied plugin metrics?
	 *
	 * @return true if metrics should be opted out of it
	 */
	public boolean isOptOut() {
		// Reload the metrics file
		configuration.load();
		return configuration.get(Configuration.CATEGORY_GENERAL, "opt-out", false).getBoolean(false);
	}

	/**
	 * Enables metrics for the server by setting "opt-out" to false in the
	 * config file and starting the metrics task.
	 *
	 * @throws java.io.IOException
	 */
	public void enable() throws IOException {
		// Check if the server owner has already set opt-out, if not, set it.
		if (isOptOut()) {
			configuration.getCategory(Configuration.CATEGORY_GENERAL).get("opt-out").set(false);
			configuration.save();
		}
		// Enable Task, if it is not running
		MinecraftForge.EVENT_BUS.register(this);
	}

	/**
	 * Disables metrics for the server by setting "opt-out" to true in the
	 * config file and canceling the metrics task.
	 *
	 * @throws java.io.IOException
	 */
	public void disable() throws IOException {
		// Check if the server owner has already set opt-out, if not, set it.
		if (!isOptOut()) {
			configuration.getCategory(Configuration.CATEGORY_GENERAL).get("opt-out").set(true);
			configuration.save();
		}
		MinecraftForge.EVENT_BUS.unregister(this);
	}

	/**
	 * Generic method that posts a plugin to the metrics website
	 */
	private void postPlugin(final boolean isPing) throws IOException {
		// Server software specific section
		val server = FMLCommonHandler.instance().getMinecraftServerInstance();
		boolean onlineMode = server.isServerInOnlineMode(); // TRUE
		// if
		// online
		// mode
		// is
		// enabled
		String serverVersion = server.getServerModName() + " (MC: "
			+ server.getMinecraftVersion() + ')';
		int playersOnline = server.getCurrentPlayerCount();

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

		// Acquire a lock on the graphs, which lets us make the assumption we
		// also lock everything
		// inside of the graph (e.g plotters)
		synchronized (graphs) {

			for (final Graph graph : graphs) {
				for (Plotter plotter : graph.getPlotters()) {
					// The key name to send to the metrics server
					// The format is C-GRAPHNAME-PLOTTERNAME where separator -
					// is defined at the top
					// Legacy (R4) submitters use the format Custom%s, or
					// CustomPLOTTERNAME
					final String key = String.format("C%s%s%s%s",
						CUSTOM_DATA_SEPARATOR, graph.getName(),
						CUSTOM_DATA_SEPARATOR, plotter.getColumnName());

					// The value to send, which for the foreseeable future is
					// just the string
					// value of plotter.getValue()
					final String value = Integer.toString(plotter.getValue());

					// Add it to the http post data :)
					encodeDataPair(data, key, value);
				}
			}
		}

		// Create the url
		URL url = new URL(BASE_URL
			+ String.format(REPORT_URL, encode(modname)));

		// Connect to the website
		URLConnection connection;

		// Mineshafter creates a socks proxy, so we can safely bypass it
		// It does not reroute POST requests so we need to go around it
		if (isMineshafterPresent()) {
			connection = url.openConnection(Proxy.NO_PROXY);
		} else {
			connection = url.openConnection();
		}

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
		} else {
			// Is this the first update this hour?
			if (response.contains("OK This is your first update this hour")) {
				synchronized (graphs) {
					for (final Graph graph : graphs) {
						graph.getPlotters().forEach(Plotter::reset);
					}
				}
			}
		}
	}

	/**
	 * Represents a custom graph on the website
	 */
	@SuppressWarnings("EmptyMethod")
	public static class Graph {
		/**
		 * The graph's name, alphanumeric and spaces only :) If it does not
		 * comply to the above when submitted, it is rejected
		 */
		private final String name;
		/**
		 * The set of plotters that are contained within this graph
		 */
		private final Set<Plotter> plotters = new LinkedHashSet<>();

		Graph(final String name) {
			this.name = name;
		}

		/**
		 * Gets the graph's name
		 *
		 * @return the Graph's name
		 */
		public String getName() {
			return name;
		}

		/**
		 * Add a plotter to the graph, which will be used to plot entries
		 *
		 * @param plotter the plotter to add to the graph
		 */
		public void addPlotter(final Plotter plotter) {
			plotters.add(plotter);
		}

		/**
		 * Remove a plotter from the graph
		 *
		 * @param plotter the plotter to remove from the graph
		 */
		public void removePlotter(final Plotter plotter) {
			plotters.remove(plotter);
		}

		/**
		 * Gets an <b>unmodifiable</b> set of the plotter objects in the graph
		 *
		 * @return an unmodifiable {@link java.util.Set} of the plotter objects
		 */
		public Set<Plotter> getPlotters() {
			return Collections.unmodifiableSet(plotters);
		}

		@Override
		public int hashCode() {
			return name.hashCode();
		}

		@Override
		public boolean equals(final Object object) {
			if (!(object instanceof Graph)) {
				return false;
			}

			final Graph graph = (Graph) object;
			return graph.name.equals(name);
		}

		/**
		 * Called when the server owner decides to opt-out of Metrics while the
		 * server is running.
		 */
		protected void onOptOut() {
		}
	}

	/**
	 * Interface used to collect custom data for a plugin
	 */
	public abstract static class Plotter {
		/**
		 * The plot's name
		 */
		private final String name;

		/**
		 * Construct a plotter with the default plot name
		 */
		public Plotter() {
			this("Default");
		}

		/**
		 * Construct a plotter with a specific plot name
		 *
		 * @param name the name of the plotter to use, which will show up on the
		 *             website
		 */
		public Plotter(final String name) {
			this.name = name;
		}

		/**
		 * Get the current value for the plotted point. Since this function
		 * defers to an external function it may or may not return immediately
		 * thus cannot be guaranteed to be thread friendly or safe. This
		 * function can be called from any thread so care should be taken when
		 * accessing resources that need to be synchronized.
		 *
		 * @return the current value for the point to be plotted.
		 */
		public abstract int getValue();

		/**
		 * Get the column name for the plotted point
		 *
		 * @return the plotted point's column name
		 */
		public String getColumnName() {
			return name;
		}

		/**
		 * Called after the website graphs have been updated
		 */
		public void reset() {
		}

		@Override
		public int hashCode() {
			return getColumnName().hashCode();
		}

		@Override
		public boolean equals(final Object object) {
			if (!(object instanceof Plotter)) {
				return false;
			}

			final Plotter plotter = (Plotter) object;
			return plotter.name.equals(name)
				&& plotter.getValue() == getValue();
		}
	}

	private static class TileEntityPlotter extends Plotter {
		public TileEntityPlotter() {
			super("TileEntities");
		}

		@Override
		public int getValue() {
			int i = 0;
			for (World world : DimensionManager.getWorlds()) {
				i += world.loadedTileEntityList.size();
			}
			return i;
		}
	}

	private static class EntityPlotter extends Plotter {
		public EntityPlotter() {
			super("Entities");
		}

		@Override
		public int getValue() {
			int i = 0;
			for (World world : DimensionManager.getWorlds()) {
				i += world.loadedEntityList.size();
			}
			return i;
		}
	}

	private static class ChunkPlotter extends Plotter {
		public ChunkPlotter() {
			super("Chunks");
		}

		@Override
		public int getValue() {
			int i = 0;
			for (World world : DimensionManager.getWorlds()) {
				val provider = world.getChunkProvider();
				if (provider instanceof ChunkProviderServer)
					i += ((ChunkProviderServer) world.getChunkProvider()).getLoadedChunkCount();
			}
			return i;
		}
	}
}
