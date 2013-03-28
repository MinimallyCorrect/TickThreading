package me.nallar.patched;

import net.minecraft.util.Vec3;
import net.minecraft.util.Vec3Pool;

public abstract class PatchVec3Pool extends Vec3Pool {
	public PatchVec3Pool(final int par1, final int par2) {
		super(par1, par2);
	}

	@Override
	public Vec3 getVecFromPool(double par1, double par3, double par5) {
		return new Vec3(this, par1, par3, par5);
	}

	@Override
	public void clear() {
	}

	@Override
	public int getPoolSize() {
		return 0;
	}

	@Override
	public int func_82590_d() {
		return 0;
	}
}
