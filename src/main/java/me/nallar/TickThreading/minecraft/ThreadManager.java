package me.nallar.tickthreading.minecraft;

import java.io.File;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.minecraft.server.MinecraftServer;
import net.minecraft.src.Chunk;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
import net.minecraftforge.common.Configuration;

public class ThreadManager {
	private static class TileEntityTask implements Runnable {
		private final int threadID;
		private final int worldID = 0;

		public TileEntityTask(int threadID) {
			super();
			this.threadID = threadID;
		}

		@Override
		public void run() {
			try {
				logger.fine("Started tick thread " + threadID);
				//noinspection InfiniteLoopStatement
				while (true) {
					if (tickNotifyLatch.await() == 0) {
						tickNotifyLatch.reset();
					}
					for (TileEntity tile : tileEntityList[threadID]) {
						if (!tile.isInvalid() && tile.worldObj != null) {
							tile.updateEntity();
						}
						if (tile.isInvalid() && tile.worldObj != null) {
							World world = tile.worldObj;
							logger.fine("Invalid tile!");
							while (tileEntityList[threadID].remove(tile)) {
								;
							}
							logger.warning("Removed invalid tile: " + tile.xCoord + ", " + tile.yCoord + ", " + tile.zCoord + "\ttype:" + tile.getClass().toString());//yes, it's blank...
							if (world.getChunkProvider().chunkExists(tile.xCoord >> 4, tile.zCoord >> 4)) {
								Chunk chunk = world.getChunkFromChunkCoords(tile.xCoord >> 4, tile.zCoord >> 4);
								if (chunk != null) {
									chunk.cleanChunkBlockTileEntity(tile.xCoord & 0xf, tile.yCoord, tile.zCoord & 0xf);
								}
							}
						}
					}
					endTickLatch.await();
				}
			} catch (Exception exception) {
				logger.log(Level.FINE, "Exception in tile thread " + threadID + ":", exception);
			}
		}
	}

	private static class EntityTask implements Runnable {
		private final int threadID;

		public EntityTask(int threadID) {
			super();
			this.threadID = threadID;
		}

		@Override
		public void run() {
			/*try{
				tickNotify.wait();
			}
			catch(InterruptedException ignored){}
			Iterator entityIterator = entityList[threadID].iterator();
			while(entityIterator.hasNext()){
				Entity entity = (Entity)entityIterator.next();

			}*/
		}
	}

	private static final Configuration conf = new Configuration(new File("TickThreading.conf"));
	private static final Logger logger = Logger.getLogger("Minecraft");
	private static final Random random = new Random();
	public static final CyclicBarrier tickNotifyLatch;
	public static final CyclicBarrier endTickLatch;
	private static volatile CopyOnWriteArrayList<TileEntity>[] tileEntityList;
	private static volatile CopyOnWriteArrayList<TileEntity>[] entityList;
	private static volatile ThreadPoolExecutor threadPool = null;
	static int numTileThreads = 1;
	static int numEntityThreads = 1;

	static {
		/*Property TileThreads = conf.get("core.TileThreads", Configuration.CATEGORY_GENERAL, numTileThreads);
		numTileThreads = Integer.parseInt(TileThreads.value);*/

		endTickLatch = new CyclicBarrier(1 + numTileThreads);//+numEntityThreads);
		tickNotifyLatch = new CyclicBarrier(1 + numTileThreads);//+numEntityThreads);

		//noinspection unchecked
		tileEntityList = new CopyOnWriteArrayList[numTileThreads];
		//noinspection unchecked
		entityList = new CopyOnWriteArrayList[numEntityThreads];

		for (int i = 0; i < numTileThreads; i++) {
			tileEntityList[i] = new CopyOnWriteArrayList<TileEntity>();
		}
		for (int i = 0; i < numEntityThreads; i++) {
			entityList[i] = new CopyOnWriteArrayList<TileEntity>();
		}

		threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(numEntityThreads + numTileThreads);
		for (int i = 0; i < numTileThreads; i++) {
			threadPool.submit(new TileEntityTask(i)); //TODO Get world
		}
		for (int i = 0; i < numEntityThreads; i++) {
			threadPool.submit(new EntityTask(i));
		}
	}

	public static synchronized ThreadGroup getAllThreads() {
		ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
		ThreadGroup parentGroup;
		while ((parentGroup = rootGroup.getParent()) != null) {
			rootGroup = parentGroup;
		}
		return rootGroup;
	}

	public static synchronized void initialise(MinecraftServer mcserver) {
		logger.log(Level.FINE, "Initialised forgeMT thread API!");
	}

	public static synchronized void addTile(TileEntity TE) {
		//logger.fine("Added TE!");
		tileEntityList[random.nextInt(numTileThreads)].add(TE);
	}
}

