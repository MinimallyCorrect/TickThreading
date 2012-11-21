package me.nallar.tickthreading.minecraft;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.entitylist.EntityList;
import me.nallar.tickthreading.minecraft.entitylist.LoadedTileEntityList;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
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
	private int tileEntityRegionSize = 16;
	private int entityRegionSize = 64;
	private Configuration config;

	@Mod.Init
	public void init(FMLInitializationEvent event) {
		MinecraftForge.EVENT_BUS.register(this);
	}

	@Mod.PreInit
	public void preInit(FMLPreInitializationEvent event) {
		config = new Configuration(event.getSuggestedConfigurationFile());
		config.load();
		Property tileEntityRegionSizeProperty = config.get(Configuration.CATEGORY_GENERAL, "tileEntityRegionSize", String.valueOf(tileEntityRegionSize));
		tileEntityRegionSizeProperty.comment = "width/length of tile entity tick regions, specified in blocks.";
		Property entityRegionSizeProperty = config.get(Configuration.CATEGORY_GENERAL, "entityRegionSize", String.valueOf(entityRegionSize));
		tileEntityRegionSizeProperty.comment = "width/length of entity tick regions, specified in blocks.";
		config.save();

		tileEntityRegionSize = tileEntityRegionSizeProperty.getInt(tileEntityRegionSize);
		entityRegionSize = entityRegionSizeProperty.getInt(entityRegionSize);
	}

	@ForgeSubscribe
	public void onWorldLoad(WorldEvent.Load event) {
		TickManager manager = new TickManager(event.world, tileEntityRegionSize, entityRegionSize);
		try {
			Field loadedTileEntityField = getListFields(World.class)[loadedTileEntityFieldIndex];
			Field loadedEntityField = getListFields(World.class)[loadedEntityFieldIndex];
			new LoadedTileEntityList<TileEntity>(event.world, loadedTileEntityField, manager);
			Log.info("Threading initialised for world " + Log.name(event.world));
			// TODO: Enable entity tick threading
			// Requires:
			//	- AxisAlignedBB pool threadlocal
			// 	- ^automated patching
			//new LoadedEntityList<TileEntity>(event.world, loadedEntityField, manager);
		} catch (Exception e) {
			Log.severe("Failed to initialise tile threading for world " + Log.name(event.world), e);
		}
	}

	@ForgeSubscribe
	public void onWorldUnload(WorldEvent.Unload event) {
		try {
			Field loadedTileEntityField = getListFields(World.class)[loadedTileEntityFieldIndex];
			Object loadedTileEntityList = loadedTileEntityField.get(event.world);
			if (loadedTileEntityList instanceof EntityList) {
				((EntityList) loadedTileEntityList).unload();
			} else {
				Log.severe("Looks like another mod broke threading for world, probably a long time ago: " + event.world.getWorldInfo().getWorldName());
			}
		} catch (Exception e) {
			Log.severe("Probable memory leak: Failed to unload tile threading for world " + Log.name(event.world), e);
		}
	}

	private static Field[] getListFields(Class c) {
		List<Field> listFields = new ArrayList<Field>();
		List<Field> fields = Arrays.asList(c.getDeclaredFields());
		for (Field field : fields) {
			if (List.class.isAssignableFrom(field.getType())) {
				listFields.add(field);
			}
		}
		return listFields.toArray(new Field[listFields.size()]);
	}
}
