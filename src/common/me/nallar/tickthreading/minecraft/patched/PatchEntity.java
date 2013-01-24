package me.nallar.tickthreading.minecraft.patched;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public abstract class PatchEntity extends Entity {
	public PatchEntity(World par1World) {
		super(par1World);
	}

	@Override
	public void moveEntity(double par1, double par3, double par5) {
		if (this.noClip) {
			this.boundingBox.offset(par1, par3, par5);
			this.posX = (this.boundingBox.minX + this.boundingBox.maxX) / 2.0D;
			this.posY = this.boundingBox.minY + (double) this.yOffset - (double) this.ySize;
			this.posZ = (this.boundingBox.minZ + this.boundingBox.maxZ) / 2.0D;
		} else {
			this.worldObj.theProfiler.startSection("move");
			this.ySize *= 0.4F;
			double var7 = this.posX;
			double var9 = this.posY;
			double var11 = this.posZ;

			if (this.isInWeb) {
				this.isInWeb = false;
				par1 *= 0.25D;
				par3 *= 0.05000000074505806D;
				par5 *= 0.25D;
				this.motionX = 0.0D;
				this.motionY = 0.0D;
				this.motionZ = 0.0D;
			}

			double var13 = par1;
			double var15 = par3;
			double var17 = par5;
			AxisAlignedBB var19 = this.boundingBox.copy();
			boolean var20 = this.onGround && this.isSneaking() && (Object) this instanceof EntityPlayer;

			if (var20) {
				double var21;

				for (var21 = 0.05D; par1 != 0.0D && !this.worldObj.hasCollidingBoundingBoxes(this, this.boundingBox.getOffsetBoundingBox(par1, -1.0D, 0.0D)); var13 = par1) {
					if (par1 < var21 && par1 >= -var21) {
						par1 = 0.0D;
					} else if (par1 > 0.0D) {
						par1 -= var21;
					} else {
						par1 += var21;
					}
				}

				for (; par5 != 0.0D && !this.worldObj.hasCollidingBoundingBoxes(this, this.boundingBox.getOffsetBoundingBox(0.0D, -1.0D, par5)); var17 = par5) {
					if (par5 < var21 && par5 >= -var21) {
						par5 = 0.0D;
					} else if (par5 > 0.0D) {
						par5 -= var21;
					} else {
						par5 += var21;
					}
				}

				while (par1 != 0.0D && par5 != 0.0D && !this.worldObj.hasCollidingBoundingBoxes(this, this.boundingBox.getOffsetBoundingBox(par1, -1.0D, par5))) {
					if (par1 < var21 && par1 >= -var21) {
						par1 = 0.0D;
					} else if (par1 > 0.0D) {
						par1 -= var21;
					} else {
						par1 += var21;
					}

					if (par5 < var21 && par5 >= -var21) {
						par5 = 0.0D;
					} else if (par5 > 0.0D) {
						par5 -= var21;
					} else {
						par5 += var21;
					}

					var13 = par1;
					var17 = par5;
				}
			}

			List var35 = this.worldObj.getCollidingBoundingBoxes(this, this.boundingBox.addCoord(par1, par3, par5));

			for (int var22 = 0; var22 < var35.size(); ++var22) {
				par3 = ((AxisAlignedBB) var35.get(var22)).calculateYOffset(this.boundingBox, par3);
			}

			this.boundingBox.offset(0.0D, par3, 0.0D);

			if (!this.field_70135_K && var15 != par3) {
				par5 = 0.0D;
				par3 = 0.0D;
				par1 = 0.0D;
			}

			boolean var34 = this.onGround || var15 != par3 && var15 < 0.0D;
			int var23;

			for (var23 = 0; var23 < var35.size(); ++var23) {
				par1 = ((AxisAlignedBB) var35.get(var23)).calculateXOffset(this.boundingBox, par1);
			}

			this.boundingBox.offset(par1, 0.0D, 0.0D);

			if (!this.field_70135_K && var13 != par1) {
				par5 = 0.0D;
				par3 = 0.0D;
				par1 = 0.0D;
			}

			for (var23 = 0; var23 < var35.size(); ++var23) {
				par5 = ((AxisAlignedBB) var35.get(var23)).calculateZOffset(this.boundingBox, par5);
			}

			this.boundingBox.offset(0.0D, 0.0D, par5);

			if (!this.field_70135_K && var17 != par5) {
				par5 = 0.0D;
				par3 = 0.0D;
				par1 = 0.0D;
			}

			double var25;
			double var27;
			int var30;
			double var36;

			if (this.stepHeight > 0.0F && var34 && (var20 || this.ySize < 0.05F) && (var13 != par1 || var17 != par5)) {
				var36 = par1;
				var25 = par3;
				var27 = par5;
				par1 = var13;
				par3 = (double) this.stepHeight;
				par5 = var17;
				AxisAlignedBB var29 = this.boundingBox.copy();
				this.boundingBox.setBB(var19);
				var35 = this.worldObj.getCollidingBoundingBoxes(this, this.boundingBox.addCoord(var13, par3, var17));

				for (var30 = 0; var30 < var35.size(); ++var30) {
					par3 = ((AxisAlignedBB) var35.get(var30)).calculateYOffset(this.boundingBox, par3);
				}

				this.boundingBox.offset(0.0D, par3, 0.0D);

				if (!this.field_70135_K && var15 != par3) {
					par5 = 0.0D;
					par3 = 0.0D;
					par1 = 0.0D;
				}

				for (var30 = 0; var30 < var35.size(); ++var30) {
					par1 = ((AxisAlignedBB) var35.get(var30)).calculateXOffset(this.boundingBox, par1);
				}

				this.boundingBox.offset(par1, 0.0D, 0.0D);

				if (!this.field_70135_K && var13 != par1) {
					par5 = 0.0D;
					par3 = 0.0D;
					par1 = 0.0D;
				}

				for (var30 = 0; var30 < var35.size(); ++var30) {
					par5 = ((AxisAlignedBB) var35.get(var30)).calculateZOffset(this.boundingBox, par5);
				}

				this.boundingBox.offset(0.0D, 0.0D, par5);

				if (!this.field_70135_K && var17 != par5) {
					par5 = 0.0D;
					par3 = 0.0D;
					par1 = 0.0D;
				}

				if (!this.field_70135_K && var15 != par3) {
					par5 = 0.0D;
					par3 = 0.0D;
					par1 = 0.0D;
				} else {
					par3 = (double) (-this.stepHeight);

					for (var30 = 0; var30 < var35.size(); ++var30) {
						par3 = ((AxisAlignedBB) var35.get(var30)).calculateYOffset(this.boundingBox, par3);
					}

					this.boundingBox.offset(0.0D, par3, 0.0D);
				}

				if (var36 * var36 + var27 * var27 >= par1 * par1 + par5 * par5) {
					par1 = var36;
					par3 = var25;
					par5 = var27;
					this.boundingBox.setBB(var29);
				}
				/* Fixes a vanilla bug where the player view would dip when stepping between certain blocks
                 * https://mojang.atlassian.net/browse/MC-1594
                else
                {
                    double var40 = this.boundingBox.minY - (double)((int)this.boundingBox.minY);

                    if (var40 > 0.0D)
                    {
                        this.ySize = (float)((double)this.ySize + var40 + 0.01D);
                    }
                }
                */
			}

			this.worldObj.theProfiler.endSection();
			this.worldObj.theProfiler.startSection("rest");
			this.posX = (this.boundingBox.minX + this.boundingBox.maxX) / 2.0D;
			this.posY = this.boundingBox.minY + (double) this.yOffset - (double) this.ySize;
			this.posZ = (this.boundingBox.minZ + this.boundingBox.maxZ) / 2.0D;
			this.isCollidedHorizontally = var13 != par1 || var17 != par5;
			this.isCollidedVertically = var15 != par3;
			this.onGround = var15 != par3 && var15 < 0.0D;
			this.isCollided = this.isCollidedHorizontally || this.isCollidedVertically;
			this.updateFallState(par3, this.onGround);

			if (var13 != par1) {
				this.motionX = 0.0D;
			}

			if (var15 != par3) {
				this.motionY = 0.0D;
			}

			if (var17 != par5) {
				this.motionZ = 0.0D;
			}

			var36 = this.posX - var7;
			var25 = this.posY - var9;
			var27 = this.posZ - var11;

			if (this.canTriggerWalking() && !var20 && this.ridingEntity == null) {
				int var37 = MathHelper.floor_double(this.posX);
				var30 = MathHelper.floor_double(this.posY - 0.20000000298023224D - (double) this.yOffset);
				int var31 = MathHelper.floor_double(this.posZ);
				int var32 = this.worldObj.getBlockId(var37, var30, var31);

				if (var32 == 0) {
					int var33 = this.worldObj.func_85175_e(var37, var30 - 1, var31);

					if (var33 == 11 || var33 == 32 || var33 == 21) {
						var32 = this.worldObj.getBlockId(var37, var30 - 1, var31);
					}
				}

				if (var32 != Block.ladder.blockID) {
					var25 = 0.0D;
				}

				this.distanceWalkedModified = (float) ((double) this.distanceWalkedModified + (double) MathHelper.sqrt_double(var36 * var36 + var27 * var27) * 0.6D);
				this.field_82151_R = (float) ((double) this.field_82151_R + (double) MathHelper.sqrt_double(var36 * var36 + var25 * var25 + var27 * var27) * 0.6D);

				if (this.field_82151_R > (float) this.nextStepDistance && var32 > 0) {
					this.nextStepDistance = (int) this.field_82151_R + 1;

					if (this.isInWater()) {
						float var39 = MathHelper.sqrt_double(this.motionX * this.motionX * 0.20000000298023224D + this.motionY * this.motionY + this.motionZ * this.motionZ * 0.20000000298023224D) * 0.35F;

						if (var39 > 1.0F) {
							var39 = 1.0F;
						}

						this.func_85030_a("liquid.swim", var39, 1.0F + (this.rand.nextFloat() - this.rand.nextFloat()) * 0.4F);
					}

					this.playStepSound(var37, var30, var31, var32);
					Block.blocksList[var32].onEntityWalking(this.worldObj, var37, var30, var31, this);
				}
			}

			this.doBlockCollisions();
			boolean var38 = this.isWet();

			if (this.worldObj.isBoundingBoxBurning(this.boundingBox.contract(0.001D, 0.001D, 0.001D))) {
				this.dealFireDamage(1);

				if (!var38) {
					++this.fire;

					if (this.fire == 0) {
						this.setFire(8);
					}
				}
			} else if (this.fire <= 0) {
				this.fire = -this.fireResistance;
			}

			if (var38 && this.fire > 0) {
				this.func_85030_a("random.fizz", 0.7F, 1.6F + (this.rand.nextFloat() - this.rand.nextFloat()) * 0.4F);
				this.fire = -this.fireResistance;
			}

			this.worldObj.theProfiler.endSection();
		}
	}
}
