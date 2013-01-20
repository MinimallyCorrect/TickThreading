package me.nallar.tickthreading.minecraft.patched;

import net.minecraft.block.Block;
import net.minecraft.village.Village;

public abstract class PatchVillage extends Village {
	// SERIOUSLY, MOJANK? CHUNKLOADING BUGS BECAUSE OF VILLAGES? :@
	//                 G
	@Override
	protected boolean isBlockDoor(int par1, int par2, int par3) {
		if (!this.worldObj.getChunkProvider().chunkExists(par1 / 16, par3 / 16)) {
			return true;
		}
		int var4 = this.worldObj.getBlockId(par1, par2, par3);
		return var4 > 0 && var4 == Block.doorWood.blockID;
	}
}
