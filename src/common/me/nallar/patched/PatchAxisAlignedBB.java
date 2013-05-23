package me.nallar.patched;

import net.minecraft.util.AABBPool;
import net.minecraft.util.AxisAlignedBB;

public abstract class PatchAxisAlignedBB extends AxisAlignedBB {
	public static final AABBPool aabbPool = new AABBPool(-1, -1);

	public PatchAxisAlignedBB(double par1, double par3, double par5, double par7, double par9, double par11) {
		super(par1, par3, par5, par7, par9, par11);
	}

	@Override
	public boolean intersectsWith(AxisAlignedBB other) {
		return other.maxX > this.minX && other.minX < this.maxX && (other.maxY > this.minY && other.minY < this.maxY && other.maxZ > this.minZ && other.minZ < this.maxZ);
	}

	public static AABBPool a() {
		return aabbPool;
	}
}
