package me.nallar.tickthreading.minecraft.patched;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.world.World;

public abstract class PatchEntityItem extends EntityItem {
	private static final double mergeRadius = 1D;

	public PatchEntityItem(World par1World, double par2, double par4, double par6) {
		super(par1World, par2, par4, par6);
	}

	@Override
	protected void func_85054_d() {
		if (this.ticksExisted % 25 == 0) {
			for (Object o : this.worldObj.getEntitiesWithinAABB(EntityItem.class, this.boundingBox.expand(mergeRadius, mergeRadius, mergeRadius))) {
				EntityItem var2 = (EntityItem) o;
				this.combineItems(var2);
			}
		}
	}
}
