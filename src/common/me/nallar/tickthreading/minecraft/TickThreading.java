package me.nallar.tickthreading.minecraft;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import me.nallar.tickthreading.util.FieldUtil;
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
			enabled = true;
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
		Log.info(getServerDirectory().toString());
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
			"\nAlternatively, you can try to run without patching, just edit th");
		}
	}

	@ForgeSubscribe
	public void onWorldLoad(WorldEvent.Load event) {
		TickManager manager = new TickManager(event.world, regionSize);
		manager.setVariableTickRate(variableTickRate);
		try {
			Field loadedTileEntityField = FieldUtil.getFields(World.class, List.class)[loadedTileEntityFieldIndex];
			//Field loadedEntityField = FieldUtil.getFields(World.class)[loadedEntityFieldIndex];
			new LoadedTileEntityList<TileEntity>(event.world, loadedTileEntityField, manager);
			Log.info("Threading initialised for world " + Log.name(event.world));
			// TODO: Enable entity tick threading
			// Requires:
			//	- AxisAlignedBB pool threadLocal
			// 	- ^automated patching
			//new LoadedEntityList<TileEntity>(event.world, loadedEntityField, manager);
			managers.put(event.world, manager);
		} catch (Exception e) {
			Log.severe("Failed to initialise tile threading for world " + Log.name(event.world), e);
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
			Log.severe("Probable memory leak, failed to unload tile threading for world " + Log.name(event.world), e);
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
		if (MinecraftServer.getServer().isDedicatedServer()) {
			return new File(MinecraftServer.class.getProtectionDomain().getCodeSource().getLocation().getPath());
		} else {
			return new File(MinecraftServer.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParentFile();
		}
	}

	public static File getDataDirectory() {
		return configurationDirectory;
	}
}
