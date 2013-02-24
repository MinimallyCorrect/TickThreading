package me.nallar.patched;

import net.minecraft.world.chunk.storage.RegionFile;
import net.minecraft.world.chunk.storage.RegionFileChunkBuffer;

public abstract class PatchRegionFileChunkBuffer extends RegionFileChunkBuffer {
	public PatchRegionFileChunkBuffer(RegionFile par1RegionFile, int par2, int par3) {
		super(par1RegionFile, par2, par3);
	}
}
