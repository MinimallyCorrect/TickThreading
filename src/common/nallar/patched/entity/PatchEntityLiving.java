package nallar.patched.entity;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public abstract class PatchEntityLiving extends EntityLiving {
	public PatchEntityLiving(World par1World) {
		super(par1World);
	}

	@Override
	public void moveEntityWithHeading(float par1, float par2) {
		double var9;

		@SuppressWarnings ("RedundantCast")
		boolean isPlayer = (Object) this instanceof EntityPlayer;
		boolean canFly = isPlayer && ((EntityPlayer) (Object) this).capabilities.isFlying;

		if (!canFly && this.isInWater()) {
			var9 = this.posY;
			this.moveFlying(par1, par2, this.isAIEnabled() ? 0.04F : 0.02F);
			this.moveEntity(this.motionX, this.motionY, this.motionZ);
			this.motionX *= 0.800000011920929D;
			this.motionY *= 0.800000011920929D;
			this.motionZ *= 0.800000011920929D;
			this.motionY -= 0.02D;

			if (this.isCollidedHorizontally && this.isOffsetPositionInLiquid(this.motionX, this.motionY + 0.6000000238418579D - this.posY + var9, this.motionZ)) {
				this.motionY = 0.30000001192092896D;
			}
		} else if (!canFly && this.handleLavaMovement()) {
			var9 = this.posY;
			this.moveFlying(par1, par2, 0.02F);
			this.moveEntity(this.motionX, this.motionY, this.motionZ);
			this.motionX *= 0.5D;
			this.motionY *= 0.5D;
			this.motionZ *= 0.5D;
			this.motionY -= 0.02D;

			if (this.isCollidedHorizontally && this.isOffsetPositionInLiquid(this.motionX, this.motionY + 0.6000000238418579D - this.posY + var9, this.motionZ)) {
				this.motionY = 0.30000001192092896D;
			}
		} else {
			float var3 = 0.91F;

			if (this.onGround) {
				var3 = 0.54600006F;
				int var4 = this.worldObj.getBlockId(MathHelper.floor_double(this.posX), MathHelper.floor_double(this.boundingBox.minY) - 1, MathHelper.floor_double(this.posZ));

				if (var4 > 0) {
					var3 = Block.blocksList[var4].slipperiness * 0.91F;
				}
			}

			float var8 = 0.16277136F / (var3 * var3 * var3);
			float var5;

			if (this.onGround) {
				if (this.isAIEnabled()) {
					var5 = this.getAIMoveSpeed();
				} else {
					var5 = this.landMovementFactor;
				}

				var5 *= var8;
			} else {
				var5 = this.jumpMovementFactor;
			}

			this.moveFlying(par1, par2, var5);
			var3 = 0.91F;

			if (this.onGround) {
				var3 = 0.54600006F;
				int var6 = this.worldObj.getBlockId(MathHelper.floor_double(this.posX), MathHelper.floor_double(this.boundingBox.minY) - 1, MathHelper.floor_double(this.posZ));

				if (var6 > 0) {
					var3 = Block.blocksList[var6].slipperiness * 0.91F;
				}
			}

			boolean onLadder = this.isOnLadder();
			if (onLadder) {
				double maxLadderSpeed = 0.15F;

				if (this.motionX < -maxLadderSpeed) {
					this.motionX = -maxLadderSpeed;
				}

				if (this.motionX > maxLadderSpeed) {
					this.motionX = maxLadderSpeed;
				}

				if (this.motionZ < -maxLadderSpeed) {
					this.motionZ = -maxLadderSpeed;
				}

				if (this.motionZ > maxLadderSpeed) {
					this.motionZ = maxLadderSpeed;
				}

				this.fallDistance = 0.0F;

				if (this.motionY < -0.15D) {
					this.motionY = -0.15D;
				}

				if (isPlayer && this.motionY < 0.0D && this.isSneaking()) {
					this.motionY = 0.0D;
				}
			}

			this.moveEntity(this.motionX, this.motionY, this.motionZ);

			if (this.isCollidedHorizontally && onLadder) {
				this.motionY = 0.2D;
			}

			if (this.worldObj.isRemote && (!this.worldObj.blockExists((int) this.posX, 0, (int) this.posZ) || !this.worldObj.getChunkFromBlockCoords((int) this.posX, (int) this.posZ).isChunkLoaded)) {
				if (this.posY > 0.0D) {
					this.motionY = -0.1D;
				} else {
					this.motionY = 0.0D;
				}
			} else {
				this.motionY -= 0.08D;
			}

			this.motionY *= 0.9800000190734863D;
			this.motionX *= (double) var3;
			this.motionZ *= (double) var3;
		}

		this.prevLimbYaw = this.limbYaw;
		var9 = this.posX - this.prevPosX;
		double var12 = this.posZ - this.prevPosZ;
		float var11 = MathHelper.sqrt_double(var9 * var9 + var12 * var12) * 4.0F;

		if (var11 > 1.0F) {
			var11 = 1.0F;
		}

		this.limbYaw += (var11 - this.limbYaw) * 0.4F;
		this.limbSwing += this.limbYaw;
	}

	@Override
	public void onLivingUpdate() {
		final World worldObj = this.worldObj;
		final Profiler theProfiler = worldObj.theProfiler;
		final boolean isServer = !this.isClientWorld();
		if (this.jumpTicks > 0) {
			--this.jumpTicks;
		}

		if (this.newPosRotationIncrements > 0) {
			double var1 = this.posX + (this.newPosX - this.posX) / (double) this.newPosRotationIncrements;
			double var3 = this.posY + (this.newPosY - this.posY) / (double) this.newPosRotationIncrements;
			double var5 = this.posZ + (this.newPosZ - this.posZ) / (double) this.newPosRotationIncrements;
			double var7 = MathHelper.wrapAngleTo180_double(this.newRotationYaw - (double) this.rotationYaw);
			this.rotationYaw = (float) ((double) this.rotationYaw + var7 / (double) this.newPosRotationIncrements);
			this.rotationPitch = (float) ((double) this.rotationPitch + (this.newRotationPitch - (double) this.rotationPitch) / (double) this.newPosRotationIncrements);
			--this.newPosRotationIncrements;
			this.setPosition(var1, var3, var5);
			this.setRotation(this.rotationYaw, this.rotationPitch);
		} else if (isServer) {
			this.motionX *= 0.98D;
			this.motionY *= 0.98D;
			this.motionZ *= 0.98D;
		}

		if (Math.abs(this.motionX) < 0.005D) {
			this.motionX = 0.0D;
		}

		if (Math.abs(this.motionY) < 0.005D) {
			this.motionY = 0.0D;
		}

		if (Math.abs(this.motionZ) < 0.005D) {
			this.motionZ = 0.0D;
		}

		if (--collisionSkipCounter >= 0) {
			return;
		}

		theProfiler.startSection("ai");

		if (this.isMovementBlocked()) {
			this.isJumping = false;
			this.moveStrafing = 0.0F;
			this.moveForward = 0.0F;
			this.randomYawVelocity = 0.0F;
		} else if (this.isClientWorld()) {
			if (this.isAIEnabled()) {
				theProfiler.startSection("newAi");
				this.updateAITasks();
				theProfiler.endSection();
			} else {
				theProfiler.startSection("oldAi");
				this.updateEntityActionState();
				theProfiler.endSection();
				this.rotationYawHead = this.rotationYaw;
			}
		}

		theProfiler.endSection();
		theProfiler.startSection("jump");

		if (this.isJumping) {
			if (!this.isInWater() && !this.handleLavaMovement()) {
				if (this.onGround && this.jumpTicks == 0) {
					this.jump();
					this.jumpTicks = 10;
				}
			} else {
				this.motionY += 0.03999999910593033D;
			}
		} else {
			this.jumpTicks = 0;
		}

		theProfiler.endSection();
		theProfiler.startSection("travel");
		this.moveStrafing *= 0.98F;
		this.moveForward *= 0.98F;
		this.randomYawVelocity *= 0.9F;
		float oldLandMovementFactor = this.landMovementFactor;
		this.landMovementFactor *= this.getSpeedModifier();
		this.moveEntityWithHeading(this.moveStrafing, this.moveForward);
		this.landMovementFactor = oldLandMovementFactor;
		theProfiler.endSection();
		theProfiler.startSection("push");

		if (!worldObj.isRemote) {
			this.func_85033_bc();
		}

		theProfiler.endSection();
		theProfiler.startSection("looting");

		if (!worldObj.isRemote && this.canPickUpLoot && !this.dead && !this.isDead && this.getHealth() > 0 && worldObj.getGameRules().getGameRuleBooleanValue("mobGriefing")) {
			List<EntityItem> entityItemList = worldObj.getEntitiesWithinAABB(EntityItem.class, this.boundingBox.expand(1.0D, 0.0D, 1.0D));

			for (EntityItem entityItem : entityItemList) {

				if (!entityItem.isDead && entityItem.getEntityItem() != null) {
					ItemStack itemStack = entityItem.getEntityItem();

					// This isn't actually redundant, because patcher.
					//noinspection RedundantCast
					boolean isPlayer = (Object) this instanceof EntityPlayerMP;
					Item item = itemStack.getItem();
					if (item == null || (!(item instanceof ItemArmor) && (!isPlayer || entityItem.delayBeforeCanPickup > 8))) {
						continue;
					}

					int targetSlot = getArmorPosition(itemStack);

					if (targetSlot > -1) {
						boolean shouldEquip = true;
						ItemStack oldItem = this.getCurrentItemOrArmor(targetSlot);

						if (oldItem != null) {
							if (isPlayer) {
								continue;
							}
							if (targetSlot == 0) {
								if (item instanceof ItemSword && !(oldItem.getItem() instanceof ItemSword)) {
									shouldEquip = true;
								} else if (item instanceof ItemSword && oldItem.getItem() instanceof ItemSword) {
									ItemSword newSword = (ItemSword) item;
									ItemSword oldSword = (ItemSword) oldItem.getItem();

									if (newSword.func_82803_g() == oldSword.func_82803_g()) {
										shouldEquip = itemStack.getItemDamage() > oldItem.getItemDamage() || itemStack.hasTagCompound() && !oldItem.hasTagCompound();
									} else {
										shouldEquip = newSword.func_82803_g() > oldSword.func_82803_g();
									}
								} else {
									shouldEquip = false;
								}
							} else if (item instanceof ItemArmor && !(oldItem.getItem() instanceof ItemArmor)) {
								shouldEquip = true;
							} else if (item instanceof ItemArmor && oldItem.getItem() instanceof ItemArmor) {
								ItemArmor newArmor = (ItemArmor) item;
								ItemArmor oldArmor = (ItemArmor) oldItem.getItem();

								if (newArmor.damageReduceAmount == oldArmor.damageReduceAmount) {
									shouldEquip = itemStack.getItemDamage() > oldItem.getItemDamage() || itemStack.hasTagCompound() && !oldItem.hasTagCompound();
								} else {
									shouldEquip = newArmor.damageReduceAmount > oldArmor.damageReduceAmount;
								}
							} else {
								shouldEquip = false;
							}
						}

						if (shouldEquip) {
							if (oldItem != null && this.rand.nextFloat() - 0.1F < this.equipmentDropChances[targetSlot]) {
								this.entityDropItem(oldItem, 0.0F);
							}

							this.setCurrentItemOrArmor(targetSlot, itemStack);
							this.equipmentDropChances[targetSlot] = 2.0F;
							this.persistenceRequired = true;
							this.onItemPickup(entityItem, 1);
							entityItem.setDead();
						}
					}
				}
			}
		}

		theProfiler.endSection();
	}
}
