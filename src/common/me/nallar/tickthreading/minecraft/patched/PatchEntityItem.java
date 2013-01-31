package me.nallar.tickthreading.minecraft.patched;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.item.ItemExpireEvent;

public abstract class PatchEntityItem extends EntityItem {
	private static final double mergeRadius = 2D;

	public PatchEntityItem(World par1World, double par2, double par4, double par6) {
		super(par1World, par2, par4, par6);
	}

	@Override
	public void onUpdate() {
		if (this.delayBeforeCanPickup > 0) {
			--this.delayBeforeCanPickup;
		}

		boolean forceUpdate = this.ticksExisted % 75 == 0;
		this.prevPosX = this.posX;
		this.prevPosY = this.posY;
		this.prevPosZ = this.posZ;
		this.motionY -= 0.03999999910593033D;
		if (forceUpdate) {
			this.noClip = this.pushOutOfBlocks(this.posX, (this.boundingBox.minY + this.boundingBox.maxY) / 2.0D, this.posZ);
		}
		this.moveEntity(this.motionX, this.motionY, this.motionZ);
		boolean var1 = (int) this.prevPosX != (int) this.posX || (int) this.prevPosY != (int) this.posY || (int) this.prevPosZ != (int) this.posZ;

		if ((var1 && this.ticksExisted % 5 == 0) || forceUpdate) {
			if (this.worldObj.getBlockMaterial(MathHelper.floor_double(this.posX), MathHelper.floor_double(this.posY), MathHelper.floor_double(this.posZ)) == Material.lava) {
				this.motionY = 0.20000000298023224D;
				this.motionX = (double) ((this.rand.nextFloat() - this.rand.nextFloat()) * 0.2F);
				this.motionZ = (double) ((this.rand.nextFloat() - this.rand.nextFloat()) * 0.2F);
				this.playSound("random.fizz", 0.4F, 2.0F + this.rand.nextFloat() * 0.4F);
			}

			if (forceUpdate && !this.worldObj.isRemote) {
				this.func_85054_d();
			}
		}

		float var2 = 0.98F;

		if (this.onGround && this.motionX > 0.01 || this.motionY > 0.01) {
			var2 = 0.58800006F;
			int var3 = this.worldObj.getBlockId(MathHelper.floor_double(this.posX), MathHelper.floor_double(this.boundingBox.minY) - 1, MathHelper.floor_double(this.posZ));

			if (var3 > 0) {
				var2 = Block.blocksList[var3].slipperiness * 0.98F;
			}
		}

		this.motionX *= (double) var2;
		this.motionY *= 0.9800000190734863D;
		this.motionZ *= (double) var2;

		if (this.onGround) {
			this.motionY *= -0.5D;
		}

		++this.age;

		ItemStack item = getDataWatcher().getWatchableObjectItemStack(10);

		if (!this.worldObj.isRemote && this.age >= lifespan) {
			if (item != null) {
				ItemExpireEvent event = new ItemExpireEvent(this, (item.getItem() == null ? 6000 : item.getItem().getEntityLifespan(item, worldObj)));
				if (MinecraftForge.EVENT_BUS.post(event)) {
					lifespan += event.extraLife;
				} else {
					this.setDead();
				}
			} else {
				this.setDead();
			}
		}

		if (item != null && item.stackSize <= 0) {
			this.setDead();
		}
	}

	@Override
	protected void func_85054_d() {
		if (this.ticksExisted % 75 == 0) {
			for (Object o : this.worldObj.getEntitiesWithinAABB(EntityItem.class, this.boundingBox.expand(mergeRadius, mergeRadius, mergeRadius))) {
				EntityItem var2 = (EntityItem) o;
				this.combineItems(var2);
			}
		}
	}
}
