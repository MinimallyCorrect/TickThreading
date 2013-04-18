package me.nallar.patched.storage;

import me.nallar.tickthreading.patcher.Declare;
import me.nallar.unsafe.UnsafeUtil;
import net.minecraft.world.chunk.NibbleArray;

public abstract class PatchNibbleArray extends NibbleArray {
	public PatchNibbleArray(final int par1, final int par2) {
		super(par1, par2);
	}

	@Override
	@Declare
	public NibbleArray clone_() {
		NibbleArray cloned;
		try {
			cloned = (NibbleArray) super.clone();
		} catch (CloneNotSupportedException e) {
			throw UnsafeUtil.throwIgnoreChecked(e);
		}
		if (data != null) {
			cloned.data = data.clone();
		}
		return cloned;
	}
}
