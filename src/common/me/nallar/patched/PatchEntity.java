package me.nallar.patched;

import java.util.List;

import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public abstract class PatchEntity extends Entity {
	@Declare
	public Boolean isForced_;
	@Declare
	public me.nallar.tickthreading.minecraft.tickregion.EntityTickRegion tickRegion_;
	@Declare
	public int collidingEntityTickSkipCounter_;
	private int lavaCheckTicks;
	private boolean inLava;

	public PatchEntity(World par1World) {
		super(par1World);
	}

	@Override
	public boolean handleLavaMovement() {
		return (lavaCheckTicks++ % 15 == 0) ? inLava = this.worldObj.isMaterialInBB(this.boundingBox.expand(-0.10000000149011612D, -0.4000000059604645D, -0.10000000149011612D), Material.lava) : inLava;
	}

	@Override
	public void moveEntity(double vX, double vY, double vZ) {
		if (this.noClip) {
			this.boundingBox.offset(vX, vY, vZ);
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
				vX *= 0.25D;
				vY *= 0.05000000074505806D;
				vZ *= 0.25D;
				this.motionX = 0.0D;
				this.motionY = 0.0D;
				this.motionZ = 0.0D;
			}

			double vX1 = vX;
			double vY1 = vY;
			double vZ1 = vZ;
			AxisAlignedBB var19 = this.boundingBox.copy();
			boolean sneakingPlayer = this.onGround && this.isSneaking() && (Object) this instanceof EntityPlayer;

			if (sneakingPlayer) {
				double var21;

				for (var21 = 0.05D; vX != 0.0D && !this.worldObj.hasCollidingBoundingBoxes(this, this.boundingBox.getOffsetBoundingBox(vX, -1.0D, 0.0D)); vX1 = vX) {
					if (vX < var21 && vX >= -var21) {
						vX = 0.0D;
					} else if (vX > 0.0D) {
						vX -= var21;
					} else {
						vX += var21;
					}
				}

				for (; vZ != 0.0D && !this.worldObj.hasCollidingBoundingBoxes(this, this.boundingBox.getOffsetBoundingBox(0.0D, -1.0D, vZ)); vZ1 = vZ) {
					if (vZ < var21 && vZ >= -var21) {
						vZ = 0.0D;
					} else if (vZ > 0.0D) {
						vZ -= var21;
					} else {
						vZ += var21;
					}
				}

				while (vX != 0.0D && vZ != 0.0D && !this.worldObj.hasCollidingBoundingBoxes(this, this.boundingBox.getOffsetBoundingBox(vX, -1.0D, vZ))) {
					if (vX < var21 && vX >= -var21) {
						vX = 0.0D;
					} else if (vX > 0.0D) {
						vX -= var21;
					} else {
						vX += var21;
					}

					if (vZ < var21 && vZ >= -var21) {
						vZ = 0.0D;
					} else if (vZ > 0.0D) {
						vZ -= var21;
					} else {
						vZ += var21;
					}

					vX1 = vX;
					vZ1 = vZ;
				}
			}

			List<AxisAlignedBB> collidingBoundingBoxes = this.worldObj.getCollidingBoundingBoxes(this, this.boundingBox.addCoord(vX, vY, vZ), 12);
			int collidingBoundingBoxesSize = collidingBoundingBoxes.size();
			collidingEntityTickSkipCounter = collidingBoundingBoxesSize / 11;
			if (collidingEntityTickSkipCounter != 0 && tickRegion != null) {
				collidingEntityTickSkipCounter = tickRegion.size() / 50;
			}

			for (int i = 0; i < collidingBoundingBoxesSize; ++i) {
				vY = collidingBoundingBoxes.get(i).calculateYOffset(this.boundingBox, vY);
			}

			this.boundingBox.offset(0.0D, vY, 0.0D);

			if (!this.field_70135_K && vY1 != vY) {
				vZ = 0.0D;
				vY = 0.0D;
				vX = 0.0D;
			}

			boolean var34 = this.onGround || vY1 != vY && vY1 < 0.0D;
			int i;

			for (i = 0; i < collidingBoundingBoxesSize; ++i) {
				vX = collidingBoundingBoxes.get(i).calculateXOffset(this.boundingBox, vX);
			}

			this.boundingBox.offset(vX, 0.0D, 0.0D);

			if (!this.field_70135_K && vX1 != vX) {
				vZ = 0.0D;
				vY = 0.0D;
				vX = 0.0D;
			}

			for (i = 0; i < collidingBoundingBoxesSize; ++i) {
				vZ = collidingBoundingBoxes.get(i).calculateZOffset(this.boundingBox, vZ);
			}

			this.boundingBox.offset(0.0D, 0.0D, vZ);

			if (!this.field_70135_K && vZ1 != vZ) {
				vZ = 0.0D;
				vY = 0.0D;
				vX = 0.0D;
			}

			double var25;
			double var27;
			int var30;
			double var36;

			if (this.stepHeight > 0.0F && var34 && (sneakingPlayer || this.ySize < 0.05F) && (vX1 != vX || vZ1 != vZ)) {
				var36 = vX;
				var25 = vY;
				var27 = vZ;
				vX = vX1;
				vY = (double) this.stepHeight;
				vZ = vZ1;
				AxisAlignedBB var29 = this.boundingBox.copy();
				this.boundingBox.setBB(var19);
				collidingBoundingBoxes = this.worldObj.getCollidingBoundingBoxes(this, this.boundingBox.addCoord(vX1, vY, vZ1), 10);
				collidingBoundingBoxesSize = collidingBoundingBoxes.size();

				for (var30 = 0; var30 < collidingBoundingBoxesSize; ++var30) {
					vY = collidingBoundingBoxes.get(var30).calculateYOffset(this.boundingBox, vY);
				}

				this.boundingBox.offset(0.0D, vY, 0.0D);

				if (!this.field_70135_K && vY1 != vY) {
					vZ = 0.0D;
					vY = 0.0D;
					vX = 0.0D;
				}

				for (var30 = 0; var30 < collidingBoundingBoxesSize; ++var30) {
					vX = collidingBoundingBoxes.get(var30).calculateXOffset(this.boundingBox, vX);
				}

				this.boundingBox.offset(vX, 0.0D, 0.0D);

				if (!this.field_70135_K && vX1 != vX) {
					vZ = 0.0D;
					vY = 0.0D;
					vX = 0.0D;
				}

				for (var30 = 0; var30 < collidingBoundingBoxesSize; ++var30) {
					vZ = collidingBoundingBoxes.get(var30).calculateZOffset(this.boundingBox, vZ);
				}

				this.boundingBox.offset(0.0D, 0.0D, vZ);

				if (!this.field_70135_K && vZ1 != vZ) {
					vZ = 0.0D;
					vY = 0.0D;
					vX = 0.0D;
				}

				if (!this.field_70135_K && vY1 != vY) {
					vZ = 0.0D;
					vY = 0.0D;
					vX = 0.0D;
				} else {
					vY = (double) (-this.stepHeight);

					for (var30 = 0; var30 < collidingBoundingBoxesSize; ++var30) {
						vY = collidingBoundingBoxes.get(var30).calculateYOffset(this.boundingBox, vY);
					}

					this.boundingBox.offset(0.0D, vY, 0.0D);
				}

				if (var36 * var36 + var27 * var27 >= vX * vX + vZ * vZ) {
					vX = var36;
					vY = var25;
					vZ = var27;
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
			this.isCollidedHorizontally = vX1 != vX || vZ1 != vZ;
			this.isCollidedVertically = vY1 != vY;
			this.onGround = vY1 != vY && vY1 < 0.0D;
			this.isCollided = this.isCollidedHorizontally || this.isCollidedVertically;
			this.updateFallState(vY, this.onGround);

			if (vX1 != vX) {
				this.motionX = 0.0D;
			}

			if (vY1 != vY) {
				this.motionY = 0.0D;
			}

			if (vZ1 != vZ) {
				this.motionZ = 0.0D;
			}

			var36 = this.posX - var7;
			var25 = this.posY - var9;
			var27 = this.posZ - var11;

			if (this.canTriggerWalking() && !sneakingPlayer && this.ridingEntity == null) {
				int var37 = MathHelper.floor_double(this.posX);
				var30 = MathHelper.floor_double(this.posY - 0.20000000298023224D - (double) this.yOffset);
				int var31 = MathHelper.floor_double(this.posZ);
				int var32 = this.worldObj.getBlockId(var37, var30, var31);

				if (var32 == 0) {
					int var33 = this.worldObj.blockGetRenderType(var37, var30 - 1, var31);

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

						this.playSound("liquid.swim", var39, 1.0F + (this.rand.nextFloat() - this.rand.nextFloat()) * 0.4F);
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
				this.playSound("random.fizz", 0.7F, 1.6F + (this.rand.nextFloat() - this.rand.nextFloat()) * 0.4F);
				this.fire = -this.fireResistance;
			}

			this.worldObj.theProfiler.endSection();
		}
	}
}
