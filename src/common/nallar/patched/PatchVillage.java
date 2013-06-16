package nallar.patched;

import net.minecraft.block.Block;
import net.minecraft.village.Village;

public abstract class PatchVillage extends Village {
	// SERIOUSLY, MOJANK? CHUNKLOADING BUGS BECAUSE OF VILLAGES? :@
	//                 G
	@Override
	protected boolean isBlockDoor(int par1, int par2, int par3) {
		int blockID = this.worldObj.getBlockIdWithoutLoad(par1, par2, par3);
		return blockID == -1 || blockID == Block.doorWood.blockID;
	}
}
