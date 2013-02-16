package me.nallar.patched;

import net.minecraft.world.ChunkCoordIntPair;

public abstract class PatchChunkCoordIntPair extends ChunkCoordIntPair {
	public PatchChunkCoordIntPair(int par1, int par2) {
		super(par1, par2);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof ChunkCoordIntPair && ((ChunkCoordIntPair) o).chunkXPos == this.chunkXPos && ((ChunkCoordIntPair) o).chunkZPos == this.chunkZPos;
	}

	@Override
	public int hashCode() {
		return (chunkXPos * 7907) + chunkXPos;
	}
}
