package me.nallar.patched.storage;

import me.nallar.tickthreading.patcher.Declare;
import me.nallar.unsafe.UnsafeUtil;
import net.minecraft.world.chunk.NibbleArray;

public abstract class PatchNibbleArrayForge extends NibbleArray {
	public PatchNibbleArrayForge(final int par1, final int par2) {
		super(par1, par2);
	}

	@Override
	@Declare
	public byte[] getValueArray() {
		return this.data;
	}
}
