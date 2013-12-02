package nallar.tickthreading.minecraft;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

import java.util.HashSet;
import java.util.Set;

public class ChunkGarbageCollector {
	private static final Profiler profiler = MinecraftServer.getServer().theProfiler;

	public static void garbageCollect(WorldServer worldServer) {
		profiler.startSection("chunkGC");
		int viewDistance = MinecraftServer.getServer().getConfigurationManager().getViewDistance() + 1;
		ChunkProviderServer chunkProvider = worldServer.theChunkProviderServer;
		Set<Long> chunksToUnload = new HashSet<Long>();
		Iterable<Chunk> loadedChunks = chunkProvider.getLoadedChunks();
		synchronized (loadedChunks) {
			for (Chunk chunk : loadedChunks) {
				chunksToUnload.add(ChunkCoordIntPair.chunkXZ2Int(chunk.xPosition, chunk.zPosition));
			}
		}

		for (EntityPlayerMP player : (Iterable<EntityPlayerMP>) worldServer.playerEntities) {
			int cX = (int) player.managedPosX >> 4;
			int cZ = (int) player.managedPosZ >> 4;
			int minX = cX - viewDistance;
			int maxX = cX + viewDistance;
			int minZ = cZ - viewDistance;
			int maxZ = cZ + viewDistance;
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					chunksToUnload.remove(ChunkCoordIntPair.chunkXZ2Int(x, z));
				}
			}
		}

		for (ChunkCoordIntPair chunkCoordIntPair : worldServer.getPersistentChunks().keySet()) {
			chunksToUnload.remove(ChunkCoordIntPair.chunkXZ2Int(chunkCoordIntPair.chunkXPos, chunkCoordIntPair.chunkZPos));
		}

		for (long chunk : chunksToUnload) {
			chunkProvider.unloadChunkForce(chunk);
		}

		profiler.endSection();
	}
}
