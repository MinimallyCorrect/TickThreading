package me.nallar.patched.storage;

import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.storage.AnvilChunkLoaderPending;

/**
 * This patch class doesn't change anything, it's just here so the prepatcher will
 * make AnvilChunkLoaderPending public
 */
public abstract class PatchAnvilChunkLoaderPending extends AnvilChunkLoaderPending {
	@Declare
	public boolean unloading_;

	public PatchAnvilChunkLoaderPending(ChunkCoordIntPair par1ChunkCoordIntPair, NBTTagCompound par2NBTTagCompound) {
		super(par1ChunkCoordIntPair, par2NBTTagCompound);
	}
}
