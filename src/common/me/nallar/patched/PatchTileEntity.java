package me.nallar.patched;

import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.tileentity.TileEntity;

public abstract class PatchTileEntity extends TileEntity {
	// Two locks - x, z. No need for plus/minus, never both needed unless region size of 1 = not allowed + stupid
	@Declare
	public int lastTTX_;
	@Declare
	public int lastTTY_;
	@Declare
	public int lastTTZ_;
	@Declare
	public java.util.concurrent.locks.Lock xMinusLock_;
	@Declare
	public java.util.concurrent.locks.Lock zMinusLock_;
	@Declare
	public java.util.concurrent.locks.Lock xPlusLock_;
	@Declare
	public java.util.concurrent.locks.Lock zPlusLock_;
}
