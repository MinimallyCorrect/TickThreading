package me.nallar.tickthreading.minecraft.patched;

import net.minecraft.world.ChunkCoordIntPair;

public class PatchChunkCoordIntPair extends ChunkCoordIntPair {
	public PatchChunkCoordIntPair(int par1, int par2) {
		super(par1, par2);
	}

	@Override
	public int hashCode() {
		return (chunkXPos * 31) + chunkXPos;
	}
}
