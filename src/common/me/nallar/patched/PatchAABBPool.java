package me.nallar.patched;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.util.AABBPool;
import net.minecraft.util.AxisAlignedBB;

public abstract class PatchAABBPool extends AABBPool {
	public PatchAABBPool(final int par1, final int par2) {
		super(par1, par2);
	}

	@Override
	public AxisAlignedBB addOrModifyAABBInPool(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		return new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	@Override
	public void cleanPool() {
	}

	@Override
	@SideOnly (Side.CLIENT)
	public void clearPool() {
	}

	@Override
	public int getlistAABBsize() {
		return 0;
	}

	@Override
	public int getnextPoolIndex() {
		return 0;
	}
}
