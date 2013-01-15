package me.nallar.tickthreading.minecraft.patched;

import me.nallar.tickthreading.minecraft.TickThreading;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.DimensionManager;

public abstract class PatchChunkProviderServer extends ChunkProviderServer {
	public PatchChunkProviderServer(WorldServer par1WorldServer, IChunkLoader par2IChunkLoader, IChunkProvider par3IChunkProvider) {
		super(par1WorldServer, par2IChunkLoader, par3IChunkProvider);
	}

	@Override
	public void unloadChunksIfNotNearSpawn(int par1, int par2) {
		if (TickThreading.instance.shouldLoadSpawn && this.currentServer.provider.canRespawnHere() && DimensionManager.shouldLoadSpawn(currentServer.provider.dimensionId)) {
			ChunkCoordinates var3 = this.currentServer.getSpawnPoint();
			int var4 = par1 * 16 + 8 - var3.posX;
			int var5 = par2 * 16 + 8 - var3.posZ;
			short var6 = 128;

			if (var4 < -var6 || var4 > var6 || var5 < -var6 || var5 > var6) {
				this.chunksToUnload.add(ChunkCoordIntPair.chunkXZ2Int(par1, par2));
			}
		} else {
			this.chunksToUnload.add(ChunkCoordIntPair.chunkXZ2Int(par1, par2));
		}
	}
}
