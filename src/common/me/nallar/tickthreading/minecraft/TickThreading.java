package me.nallar.tickthreading.minecraft;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkMod;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.commands.TPSCommand;
import me.nallar.tickthreading.minecraft.commands.TicksCommand;
import me.nallar.tickthreading.minecraft.entitylist.EntityList;
import me.nallar.tickthreading.minecraft.entitylist.LoadedTileEntityList;
import me.nallar.tickthreading.patcher.PatchManager;
import me.nallar.tickthreading.util.EnumerableWrapper;
import me.nallar.tickthreading.util.FieldUtil;
import me.nallar.tickthreading.util.LocationUtil;
import net.minecraft.command.ServerCommandManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Property;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.world.WorldEvent;

@SuppressWarnings ("WeakerAccess")
@Mod (modid = "TickThreading", name = "TickThreading", version = "1.0")
@NetworkMod (clientSideRequired = false, serverSideRequired = false)
public class TickThreading {
	private final int loadedTileEntityFieldIndex = 2;
	private final int loadedEntityFieldIndex = 0;
	public final boolean enabled;
	private int regionSize = 16;
	private boolean variableTickRate = true;
	private boolean requirePatched = true;
	private static File configurationDirectory;
	final Map<World, TickManager> managers = new HashMap<World, TickManager>();
	private static TickThreading instance;

	public TickThreading() {
		if (requirePatched) {
			MinecraftServer.class.getProtectionDomain().getCodeSource().getLocation();
			if (PatchManager.shouldPatch(LocationUtil.locationOf(MinecraftServer.class))) {
				enabled = false;
				try {
					writePatchRunners();
				} catch (IOException e) {
					Log.severe("Failed to write patchrunners", e);
				}
			} else {
				enabled = true;
			}
		} else {
			enabled = true;
		}
	}

	@Mod.Init
	public void init(FMLInitializationEvent event) {
		if (enabled) {
			MinecraftForge.EVENT_BUS.register(this);
		}
		instance = this;
	}

	@Mod.PreInit
	public void preInit(FMLPreInitializationEvent event) {
		configurationDirectory = event.getSuggestedConfigurationFile().getParentFile();
		Configuration config = new Configuration(event.getSuggestedConfigurationFile());
		config.load();
		Property regionSizeProperty = config.get(Configuration.CATEGORY_GENERAL, "regionSize", String.valueOf(regionSize));
		regionSizeProperty.comment = "width/length of tick regions, specified in blocks.";
		Property variableTickRateProperty = config.get(Configuration.CATEGORY_GENERAL, "variableRegionTickRate", variableTickRate);
		variableTickRateProperty.comment = "Allows tick rate to vary per region so that each region uses at most 50ms on average per tick.";
		Property ticksCommandName = config.get(Configuration.CATEGORY_GENERAL, "ticksCommandName", TicksCommand.name);
		ticksCommandName.comment = "Name of the command to be used for performance stats. Defaults to ticks.";
		Property tpsCommandName = config.get(Configuration.CATEGORY_GENERAL, "tpsCommandName", TPSCommand.name);
		tpsCommandName.comment = "Name of the command to be used for TPS reports.";
		Property requirePatchedProperty = config.get(Configuration.CATEGORY_GENERAL, "requirePatched", requirePatched);
		tpsCommandName.comment = "If the server must be patched to run with TickThreading";
		config.save();

		regionSize = regionSizeProperty.getInt(regionSize);
		variableTickRate = variableTickRateProperty.getBoolean(variableTickRate);
		TicksCommand.name = ticksCommandName.value;
		TPSCommand.name = tpsCommandName.value;
		requirePatched = requirePatchedProperty.getBoolean(requirePatched);
	}

	@Mod.ServerStarting
	public void serverStarting(FMLServerStartingEvent event) {
		if (enabled) {
			ServerCommandManager serverCommandManager = (ServerCommandManager) event.getServer().getCommandManager();
			serverCommandManager.registerCommand(new TicksCommand());
			serverCommandManager.registerCommand(new TPSCommand());
		} else {
			Log.severe("TickThreading is disabled, because your server has not been patched!" +
					"\nTo patch your server, simply run the PATCHME.bat/sh file in your server directory" +
					"\nAlternatively, you can try to run without patching, just edit the config. Probably won't end well.");
		}
	}

	@ForgeSubscribe
	public void onWorldLoad(WorldEvent.Load event) {
		TickManager manager = new TickManager(event.world, regionSize);
		manager.setVariableTickRate(variableTickRate);
		try {
			Field loadedTileEntityField = FieldUtil.getFields(World.class, List.class)[loadedTileEntityFieldIndex];
			Field loadedEntityField = FieldUtil.getFields(World.class, List.class)[loadedEntityFieldIndex];
			new LoadedTileEntityList<TileEntity>(event.world, loadedTileEntityField, manager);
			Log.info("Threading initialised for world " + Log.name(event.world));
			// TODO: Enable entity tick threading
			//new LoadedEntityList<TileEntity>(event.world, loadedEntityField, manager);
			managers.put(event.world, manager);
		} catch (Exception e) {
			Log.severe("Failed to initialise threading for world " + Log.name(event.world), e);
		}
	}

	@ForgeSubscribe
	public void onWorldUnload(WorldEvent.Unload event) {
		managers.remove(event.world);
		try {
			Field loadedTileEntityField = FieldUtil.getFields(World.class, List.class)[loadedTileEntityFieldIndex];
			Object loadedTileEntityList = loadedTileEntityField.get(event.world);
			if (loadedTileEntityList instanceof EntityList) {
				((EntityList) loadedTileEntityList).unload();
			} else {
				Log.severe("Looks like another mod broke TickThreading in world: " + Log.name(event.world));
			}
		} catch (Exception e) {
			Log.severe("Probable memory leak, failed to unload threading for world " + Log.name(event.world), e);
		}
	}

	public TickManager getManager(World world) {
		return managers.get(world);
	}

	public List<TickManager> getManagers() {
		return new ArrayList<TickManager>(managers.values());
	}

	public static TickThreading instance() {
		return instance;
	}

	public static File getServerDirectory() {
		File jarPath = LocationUtil.directoryOf(MinecraftServer.class);
		return MinecraftServer.getServer().isDedicatedServer() ? jarPath : jarPath.getParentFile();
	}

	public static File getDataDirectory() {
		return configurationDirectory;
	}

	private void writePatchRunners() throws IOException {
		String java = System.getProperties().getProperty("java.home") + File.separator + "bin" + File.separator + "java";
		String TT = LocationUtil.locationOf(TickThreading.class).getAbsolutePath();
		String MS = LocationUtil.locationOf(MinecraftServer.class).getAbsolutePath();

		ZipFile zipFile = new ZipFile(new File(TT));
		for (ZipEntry zipEntry : new EnumerableWrapper<ZipEntry>((Enumeration<ZipEntry>) zipFile.entries())) {
			if (zipEntry.getName().startsWith("patchrun/") && !zipEntry.getName().endsWith("/")) {
				String data = new java.util.Scanner(zipFile.getInputStream(zipEntry)).useDelimiter("\\A").next();
				FileWriter fileWriter = new FileWriter(new File(getServerDirectory(), zipEntry.getName().replace("patchrun/", "")));
				data = data.replace("%JAVA%", java).replace("%TT%", TT).replace("%MS%", MS);
				fileWriter.write(data);
				fileWriter.close();
			}
		}
		zipFile.close();
	}
}
