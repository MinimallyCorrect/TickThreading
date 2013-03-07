package me.nallar.patched.storage;

import me.nallar.patched.annotation.FakeExtend;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;

/**
 * This is a replacement for ChunkProviderServer
 * Instead of attempting to patch a class with many different implementations,
 * this replaces it with an implementation which is intended to be compatible
 * with both Forge and MCPC+.
 */
@FakeExtend
public abstract class ThreadedChunkProvider extends ChunkProviderServer {
	public ThreadedChunkProvider(WorldServer par1WorldServer, IChunkLoader par2IChunkLoader, IChunkProvider par3IChunkProvider) {
		super(par1WorldServer, par2IChunkLoader, par3IChunkProvider);
	}
}
