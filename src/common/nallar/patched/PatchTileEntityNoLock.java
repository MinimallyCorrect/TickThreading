package nallar.patched;

import net.minecraft.tileentity.TileEntity;

public abstract class PatchTileEntityNoLock extends TileEntity {
	@Override
	public boolean noLock() {
		return true;
	}
}
