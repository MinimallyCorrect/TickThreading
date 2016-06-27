package nallar.tickthreading.log;

import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

@SuppressWarnings({"UnusedDeclaration", "UseOfSystemOutOrSystemErr"})
public class Log {
	public static final Logger LOGGER = LogManager.getLogger("@MOD_NAME@");

	public static void error(String msg) {
		LOGGER.error(msg);
	}

	public static void warn(String msg) {
		LOGGER.warn(msg);
	}

	public static void info(String msg) {
		LOGGER.info(msg);
	}

	public static void trace(String msg) {
		LOGGER.trace(msg);
	}

	public static void error(String msg, Throwable t) {
		LOGGER.log(Level.ERROR, msg, t);
	}

	public static void warn(String msg, Throwable t) {
		LOGGER.log(Level.WARN, msg, t);
	}

	public static void info(String msg, Throwable t) {
		LOGGER.log(Level.INFO, msg, t);
	}

	public static void trace(String msg, Throwable t) {
		LOGGER.log(Level.TRACE, msg, t);
	}

	public static String name(World world) {
		if (world == null) {
			return "null world.";
		} else if (world.provider == null) {
			return "Broken world with ID ";// TODO + MinecraftServer.getServer().getId((WorldServer) world);
		}
		return world.getName();
	}

	public static String classString(Object o) {
		return "c " + o.getClass().getName() + ' ';
	}

	public static String toString(Object o) {
		try {
			if (o instanceof World) {
				return name((World) o);
			}
			String cS = classString(o);
			String s = o.toString();
			if (!s.startsWith(cS)) {
				s = cS + s;
			}
			if (o instanceof TileEntity) {
				TileEntity tE = (TileEntity) o;
				if (!s.contains(" x, y, z: ")) {
					s += tE.getPos().toString();
				}
			}
			return s;
		} catch (Throwable t) {
			Log.error("Failed to perform toString on object of class " + o.getClass(), t);
			return "unknown";
		}
	}

	public static void log(Level level, Throwable throwable, String s) {
		LOGGER.log(level, s, throwable);
	}

	public static String pos(final World world, final int x, final int z) {
		return "in " + world.getName() + ' ' + pos(x, z);
	}

	public static String pos(final int x, final int z) {
		return x + ", " + z;
	}

	public static String pos(final World world, final int x, final int y, final int z) {
		return "in " + world.getName() + ' ' + pos(x, y, z);
	}

	public static String pos(final int x, final int y, final int z) {
		return "at " + x + ", " + y + ", " + z;
	}

	public static String dumpWorld(World world) {
		boolean unloaded = world.unloaded;
		return (unloaded ? "un" : "") + "loaded world " + name(world) + '@' + System.identityHashCode(world) + ", dimension: " + world.getDimensionId();
	}

	public static void checkWorlds() {
		MinecraftServer minecraftServer = FMLCommonHandler.instance().getMinecraftServerInstance();
		int a = minecraftServer.worldServers.length;
		int b = DimensionManager.getWorlds().length;
		if (a != b) {
			Log.error("World counts mismatch.\n" + dumpWorlds());
		} else if (hasDuplicates(minecraftServer.worldServers) || hasDuplicates(DimensionManager.getWorlds())) {
			Log.error("Duplicate worlds.\n" + dumpWorlds());
		} else {
			for (WorldServer worldServer : minecraftServer.worldServers) {
				if (worldServer.unloaded || worldServer.provider == null) {
					Log.error("Broken/unloaded world in worlds list.\n" + dumpWorlds());
				}
			}
		}
	}

	public static String dumpWorlds() {
		StringBuilder sb = new StringBuilder();
		List<World> dimensionManagerWorlds = new ArrayList<>(Arrays.asList(DimensionManager.getWorlds()));
		MinecraftServer minecraftServer = FMLCommonHandler.instance().getMinecraftServerInstance();
		List<World> minecraftServerWorlds = new ArrayList<>(Arrays.asList(minecraftServer.worldServers));
		sb.append("Worlds in dimensionManager: \n").append(dumpWorlds(dimensionManagerWorlds));
		sb.append("Worlds in minecraftServer: \n").append(dumpWorlds(minecraftServerWorlds));
		return sb.toString();
	}

	private static boolean hasDuplicates(Object[] array) {
		return hasDuplicates(Arrays.asList(array));
	}

	private static boolean hasDuplicates(List<?> list) {
		if (list == null) {
			return false;
		}
		Set<Object> set = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Object o : list) {
			if (!set.add(o)) {
				return true;
			}
		}
		return false;
	}

	private static String dumpWorlds(final Collection<World> worlds) {
		StringBuilder sb = new StringBuilder();
		for (World world : worlds) {
			sb.append(dumpWorld(world)).append('\n');
		}
		return sb.toString();
	}
}
