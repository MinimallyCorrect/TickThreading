package nallar.tickthreading.minecraft.profiling;

import nallar.tickthreading.minecraft.TickManager;
import nallar.tickthreading.minecraft.TickThreading;
import nallar.tickthreading.minecraft.commands.ProfileCommand;
import nallar.tickthreading.util.CollectionsUtil;
import nallar.tickthreading.util.TableFormatter;
import nallar.tickthreading.util.stringfillers.StringFiller;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.cliffc.high_scale_lib.NonBlockingHashMap;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;

public class EntityTickProfiler {
	public static final EntityTickProfiler ENTITY_TICK_PROFILER = new EntityTickProfiler();
	public static ProfileCommand.ProfilingState profilingState = ProfileCommand.ProfilingState.NONE;
	private int ticks;
	private final AtomicLong totalTime = new AtomicLong();
	private volatile long startTime;

	private EntityTickProfiler() {
	}

	public static synchronized boolean startProfiling(ProfileCommand.ProfilingState profilingState_) {
		if (profilingState != ProfileCommand.ProfilingState.NONE) {
			return false;
		}
		profilingState = profilingState_;
		return true;
	}

	public static synchronized void endProfiling() {
		profilingState = ProfileCommand.ProfilingState.NONE;
	}

	public boolean startProfiling(final Runnable runnable, ProfileCommand.ProfilingState state, final int time, final Collection<World> worlds_) {
		if (time <= 0) {
			throw new IllegalArgumentException("time must be > 0");
		}
		final Collection<World> worlds = new ArrayList<World>(worlds_);
		synchronized (EntityTickProfiler.class) {
			if (!startProfiling(state)) {
				return false;
			}
			for (World world_ : worlds) {
				TickThreading.instance.getManager(world_).profilingEnabled = true;
			}
		}

		Runnable profilingRunnable = new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(1000 * time);
				} catch (InterruptedException ignored) {
				}

				synchronized (EntityTickProfiler.class) {
					endProfiling();
					runnable.run();
					clear();
					for (World world_ : worlds) {
						TickManager tickManager = TickThreading.instance.getManager(world_);
						if (tickManager != null) {
							tickManager.profilingEnabled = false;
						}
					}
				}
			}
		};
		Thread profilingThread = new Thread(profilingRunnable);
		profilingThread.setName("TickProfiler");
		profilingThread.start();
		startTime = System.currentTimeMillis();
		return true;
	}

	public void record(Object o, long time) {
		if (time < 0) {
			time = 0;
		}
		getSingleTime(o).addAndGet(time);
		getSingleInvocationCount(o).incrementAndGet();
		Class<?> clazz = o.getClass();
		getTime(clazz).addAndGet(time);
		getInvocationCount(clazz).incrementAndGet();
		totalTime.addAndGet(time);
	}

	public void clear() {
		invocationCount.clear();
		time.clear();
		totalTime.set(0);
		singleTime.clear();
		singleInvocationCount.clear();
		ticks = 0;
	}

	public void tick() {
		if (profilingState != ProfileCommand.ProfilingState.NONE) {
			ticks++;
		}
	}

	public void writeJSONData(File file) throws IOException {
		TableFormatter tf = new TableFormatter(StringFiller.FIXED_WIDTH);
		tf.recordTables();
		writeData(tf, 20);
		ObjectMapper objectMapper = new ObjectMapper();
		List<Object> tables = tf.getTables();
		long timeProfiled = System.currentTimeMillis() - startTime;
		float tps = ticks * 1000f / timeProfiled;
		tables.add(0, CollectionsUtil.map(
				"TPS", tps,
				"Load", TableFormatter.formatDoubleWithPrecision((MinecraftServer.getTickTime() * 100) / MinecraftServer.getTargetTickTime(), 2)
		));
		objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, tables);
	}

	public TableFormatter writeStringData(TableFormatter tf) {
		return writeStringData(tf, 5);
	}

	public TableFormatter writeStringData(TableFormatter tf, int elements) {
		long timeProfiled = System.currentTimeMillis() - startTime;
		float tps = ticks * 1000f / timeProfiled;
		tf.sb.append("TPS: ").append(tps).append('\n').append(tf.tableSeparator);
		return writeData(tf, elements);
	}

	public TableFormatter writeData(TableFormatter tf, int elements) {
		Map<Class<?>, Long> time = new HashMap<Class<?>, Long>();
		for (Map.Entry<Class<?>, AtomicLong> entry : this.time.entrySet()) {
			time.put(entry.getKey(), entry.getValue().get());
		}
		Map<Object, Long> singleTime = new HashMap<Object, Long>();
		for (Map.Entry<Object, AtomicLong> entry : this.singleTime.entrySet()) {
			singleTime.put(entry.getKey(), entry.getValue().get());
		}
		double totalTime = this.totalTime.get();
		tf
				.heading("Single Entity")
				.heading("Time/Tick")
				.heading("%");
		final List<Object> sortedSingleKeysByTime = CollectionsUtil.sortedKeys(singleTime, elements);
		for (Object o : sortedSingleKeysByTime) {
			tf
					.row(niceName(o))
					.row(singleTime.get(o) / (1000000d * singleInvocationCount.get(o).get()))
					.row((singleTime.get(o) / totalTime) * 100);
		}
		tf.finishTable();
		tf.sb.append('\n');
		final Map<ChunkCoords, ComparableLongHolder> chunkTimeMap = new HashMap<ChunkCoords, ComparableLongHolder>() {
			@Override
			public ComparableLongHolder get(Object key_) {
				ChunkCoords key = (ChunkCoords) key_;
				ComparableLongHolder value = super.get(key);
				if (value == null) {
					value = new ComparableLongHolder();
					put(key, value);
				}
				return value;
			}
		};
		for (final Map.Entry<Object, Long> singleTimeEntry : singleTime.entrySet()) {
			int x;
			int z;
			int dimension;
			Object o = singleTimeEntry.getKey();
			if (o instanceof Entity) {
				x = ((Entity) o).chunkCoordX;
				z = ((Entity) o).chunkCoordZ;
				dimension = getDimension((Entity) o);
			} else if (o instanceof TileEntity) {
				x = ((TileEntity) o).xCoord >> 4;
				z = ((TileEntity) o).zCoord >> 4;
				dimension = getDimension((TileEntity) o);
			} else {
				throw new RuntimeException("Wrong type: " + o.getClass());
			}
			if (x != Integer.MIN_VALUE) {
				chunkTimeMap.get(new ChunkCoords(x, z, dimension)).value += singleTimeEntry.getValue();
			}
		}
		tf
				.heading("Chunk")
				.heading("Time/Tick")
				.heading("%");
		for (ChunkCoords chunkCoords : CollectionsUtil.sortedKeys(chunkTimeMap, elements)) {
			long chunkTime = chunkTimeMap.get(chunkCoords).value;
			tf
					.row(chunkCoords.dimension + ": " + chunkCoords.chunkXPos + ", " + chunkCoords.chunkZPos)
					.row(chunkTime / (1000000d * ticks))
					.row((chunkTime / totalTime) * 100);
		}
		tf.finishTable();
		tf.sb.append('\n');
		tf
				.heading("All Entities of Type")
				.heading("Time/Tick")
				.heading("%");
		for (Class c : CollectionsUtil.sortedKeys(time, elements)) {
			tf
					.row(niceName(c))
					.row(time.get(c) / (1000000d * ticks))
					.row((time.get(c) / totalTime) * 100);
		}
		tf.finishTable();
		tf.sb.append('\n');
		Map<Class<?>, Long> timePerTick = new HashMap<Class<?>, Long>();
		for (Map.Entry<Class<?>, AtomicLong> entry : this.time.entrySet()) {
			timePerTick.put(entry.getKey(), entry.getValue().get() / invocationCount.get(entry.getKey()).get());
		}
		tf
				.heading("Average Entity of Type")
				.heading("Time/tick")
				.heading("Calls");
		for (Class c : CollectionsUtil.sortedKeys(timePerTick, elements)) {
			tf
					.row(niceName(c))
					.row(timePerTick.get(c) / 1000000d)
					.row(invocationCount.get(c));
		}
		tf.finishTable();
		return tf;
	}

	private static int getDimension(TileEntity o) {
		WorldProvider worldProvider = o.worldObj.provider;
		return worldProvider == null ? -999 : worldProvider.dimensionId;
	}

	private static int getDimension(Entity o) {
		WorldProvider worldProvider = o.worldObj.provider;
		return worldProvider == null ? -999 : worldProvider.dimensionId;
	}

	private static Object niceName(Object o) {
		if (o instanceof TileEntity) {
			return niceName(o.getClass()) + ' ' + ((TileEntity) o).xCoord + ',' + ((TileEntity) o).yCoord + ',' + ((TileEntity) o).zCoord + ':' + getDimension((TileEntity) o);
		} else if (o instanceof Entity) {
			return niceName(o.getClass()) + ' ' + (int) ((Entity) o).posX + ',' + (int) ((Entity) o).posY + ',' + (int) ((Entity) o).posZ + ':' + getDimension((Entity) o);
		}
		return o.toString().substring(0, 48);
	}

	private static String niceName(Class<?> clazz) {
		String name = clazz.getName();
		if (name.contains(".")) {
			String cName = name.substring(name.lastIndexOf('.') + 1);
			String pName = name.substring(0, name.lastIndexOf('.'));
			if (pName.contains(".")) {
				pName = pName.substring(pName.lastIndexOf('.') + 1);
			}
			return (cName.length() < 15 ? pName + '.' : "") + cName;
		}
		return name;
	}

	private final Map<Class<?>, AtomicInteger> invocationCount = new NonBlockingHashMap<Class<?>, AtomicInteger>();
	private final Map<Class<?>, AtomicLong> time = new NonBlockingHashMap<Class<?>, AtomicLong>();
	private final Map<Object, AtomicLong> singleTime = new NonBlockingHashMap<Object, AtomicLong>();
	private final Map<Object, AtomicLong> singleInvocationCount = new NonBlockingHashMap<Object, AtomicLong>();

	private AtomicLong getSingleInvocationCount(Object o) {
		AtomicLong t = singleInvocationCount.get(o);
		if (t == null) {
			synchronized (o) {
				t = singleInvocationCount.get(o);
				if (t == null) {
					t = new AtomicLong();
					singleInvocationCount.put(o, t);
				}
			}
		}
		return t;
	}

	// We synchronize on the class name as it is always the same object
	// We do not synchronize on the class object as that would also
	// prevent any synchronized static methods on it from running
	private AtomicInteger getInvocationCount(Class<?> clazz) {
		AtomicInteger i = invocationCount.get(clazz);
		if (i == null) {
			synchronized (clazz.getName()) {
				i = invocationCount.get(clazz);
				if (i == null) {
					i = new AtomicInteger();
					invocationCount.put(clazz, i);
				}
			}
		}
		return i;
	}

	private AtomicLong getSingleTime(Object o) {
		AtomicLong t = singleTime.get(o);
		if (t == null) {
			synchronized (o) {
				t = singleTime.get(o);
				if (t == null) {
					t = new AtomicLong();
					singleTime.put(o, t);
				}
			}
		}
		return t;
	}

	private AtomicLong getTime(Class<?> clazz) {
		AtomicLong t = time.get(clazz);
		if (t == null) {
			synchronized (clazz.getName()) {
				t = time.get(clazz);
				if (t == null) {
					t = new AtomicLong();
					time.put(clazz, t);
				}
			}
		}
		return t;
	}

	private class ComparableLongHolder implements Comparable<ComparableLongHolder> {
		public long value;

		ComparableLongHolder() {
		}

		@Override
		public int compareTo(final ComparableLongHolder comparableLongHolder) {
			long otherValue = comparableLongHolder.value;
			return (value < otherValue) ? -1 : ((value == otherValue) ? 0 : 1);
		}
	}

	private static final class ChunkCoords {
		public final int chunkXPos;
		public final int chunkZPos;
		public final int dimension;

		ChunkCoords(final int chunkXPos, final int chunkZPos, final int dimension) {
			this.chunkXPos = chunkXPos;
			this.chunkZPos = chunkZPos;
			this.dimension = dimension;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof ChunkCoords && ((ChunkCoords) o).chunkXPos == this.chunkXPos && ((ChunkCoords) o).chunkZPos == this.chunkZPos && ((ChunkCoords) o).dimension == this.dimension;
		}

		@Override
		public int hashCode() {
			return (chunkXPos << 16) ^ (chunkZPos << 4) ^ dimension;
		}
	}
}
