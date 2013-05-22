package me.nallar.tickthreading.util;

import net.minecraft.world.chunk.Chunk;

public abstract class ChunkLoadRunnable implements Runnable {
	public abstract void onLoad(Chunk chunk);

	@Override
	public final void run() {
		throw new UnsupportedOperationException("Should use onLoad.");
	}
}
