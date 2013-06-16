package nallar.patched.storage;

import nallar.tickthreading.patcher.Declare;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.storage.AnvilChunkLoaderPending;

public abstract class PatchAnvilChunkLoaderPending extends AnvilChunkLoaderPending {
	@Declare
	public boolean unloading_;

	public PatchAnvilChunkLoaderPending(ChunkCoordIntPair par1ChunkCoordIntPair, NBTTagCompound par2NBTTagCompound) {
		super(par1ChunkCoordIntPair, par2NBTTagCompound);
	}
}
