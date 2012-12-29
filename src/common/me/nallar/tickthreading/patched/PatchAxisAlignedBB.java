package me.nallar.tickthreading.patched;

import net.minecraft.util.AxisAlignedBB;

public class PatchAxisAlignedBB extends AxisAlignedBB {
	private PatchAxisAlignedBB(double par1, double par3, double par5, double par7, double par9, double par11) {
		super(par1, par3, par5, par7, par9, par11);
	}

	@Override
	public boolean intersectsWith(AxisAlignedBB par1AxisAlignedBB) {
		return par1AxisAlignedBB.maxX > this.minX && par1AxisAlignedBB.minX < this.maxX && (par1AxisAlignedBB.maxY > this.minY && par1AxisAlignedBB.minY < this.maxY && par1AxisAlignedBB.maxZ > this.minZ && par1AxisAlignedBB.minZ < this.maxZ);
	}
}
