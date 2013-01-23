package me.nallar.tickthreading.minecraft;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

public class ChunkGarbageCollector {
	public static final boolean enabled = supportsGC();

	private static boolean supportsGC() {
		try {
			Class.forName("org.bukkit.craftbukkit.libs.jline.Terminal");
		} catch (ClassNotFoundException e) {
			return true;
		}
		return false;
	}

	public static Profiler profiler = MinecraftServer.getServer().theProfiler;

	public static void garbageCollect(WorldServer worldServer) {
		if (!enabled) {
			return;
		}
		profiler.startSection("chunkGC");
		int viewDistance = MinecraftServer.getServer().getConfigurationManager().getViewDistance();
		ChunkProviderServer chunkProvider = worldServer.theChunkProviderServer;
		Set<Long> chunksToUnload = new HashSet<Long>();
		for (Chunk chunk : chunkProvider.getLoadedChunks()) {
			chunksToUnload.add(ChunkCoordIntPair.chunkXZ2Int(chunk.xPosition, chunk.zPosition));
		}

		for (Object player_ : worldServer.playerEntities) {
			EntityPlayerMP player = (EntityPlayerMP) player_;
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

		chunkProvider.getChunksToUnloadSet().addAll(chunksToUnload);
		profiler.endSection();
	}
}
