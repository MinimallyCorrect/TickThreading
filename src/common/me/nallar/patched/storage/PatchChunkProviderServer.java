package me.nallar.patched.storage;

import java.util.List;

import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;

public abstract class PatchChunkProviderServer extends ChunkProviderServer {
	public PatchChunkProviderServer(WorldServer par1WorldServer, IChunkLoader par2IChunkLoader, IChunkProvider par3IChunkProvider) {
		super(par1WorldServer, par2IChunkLoader, par3IChunkProvider);
	}

	@Override
	@Declare
	public List<Chunk> getLoadedChunks() {
		return loadedChunks;
	}

	@Override
	@Declare
	public Chunk getChunkIfExists(int x, int z) {
		return provideChunk(x, z);
	}
}
