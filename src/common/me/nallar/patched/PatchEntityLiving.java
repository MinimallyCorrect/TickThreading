package me.nallar.patched;

import java.util.Iterator;
import java.util.List;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
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
		float var11 = this.landMovementFactor;
		this.landMovementFactor *= this.getSpeedModifier();
		this.moveEntityWithHeading(this.moveStrafing, this.moveForward);
		this.landMovementFactor = var11;
		theProfiler.endSection();
		theProfiler.startSection("push");

		if (!worldObj.isRemote) {
			this.func_85033_bc();
		}

		theProfiler.endSection();
		theProfiler.startSection("looting");

		if (!worldObj.isRemote && this.canPickUpLoot && !this.dead && worldObj.getGameRules().getGameRuleBooleanValue("mobGriefing")) {
			List var2 = worldObj.getEntitiesWithinAABB(EntityItem.class, this.boundingBox.expand(1.0D, 0.0D, 1.0D));
			Iterator var12 = var2.iterator();

			while (var12.hasNext()) {
				EntityItem var4 = (EntityItem) var12.next();

				if (!var4.isDead && var4.getEntityItem() != null) {
					ItemStack var13 = var4.getEntityItem();
					int var6 = func_82159_b(var13);

					if (var6 > -1) {
						boolean var14 = true;
						ItemStack var8 = this.getCurrentItemOrArmor(var6);

						if (var8 != null) {
							if (var6 == 0) {
								if (var13.getItem() instanceof ItemSword && !(var8.getItem() instanceof ItemSword)) {
									var14 = true;
								} else if (var13.getItem() instanceof ItemSword && var8.getItem() instanceof ItemSword) {
									ItemSword var9 = (ItemSword) var13.getItem();
									ItemSword var10 = (ItemSword) var8.getItem();

									if (var9.func_82803_g() == var10.func_82803_g()) {
										var14 = var13.getItemDamage() > var8.getItemDamage() || var13.hasTagCompound() && !var8.hasTagCompound();
									} else {
										var14 = var9.func_82803_g() > var10.func_82803_g();
									}
								} else {
									var14 = false;
								}
							} else if (var13.getItem() instanceof ItemArmor && !(var8.getItem() instanceof ItemArmor)) {
								var14 = true;
							} else if (var13.getItem() instanceof ItemArmor && var8.getItem() instanceof ItemArmor) {
								ItemArmor var15 = (ItemArmor) var13.getItem();
								ItemArmor var16 = (ItemArmor) var8.getItem();

								if (var15.damageReduceAmount == var16.damageReduceAmount) {
									var14 = var13.getItemDamage() > var8.getItemDamage() || var13.hasTagCompound() && !var8.hasTagCompound();
								} else {
									var14 = var15.damageReduceAmount > var16.damageReduceAmount;
								}
							} else {
								var14 = false;
							}
						}

						if (var14) {
							if (var8 != null && this.rand.nextFloat() - 0.1F < this.equipmentDropChances[var6]) {
								this.entityDropItem(var8, 0.0F);
							}

							this.setCurrentItemOrArmor(var6, var13);
							this.equipmentDropChances[var6] = 2.0F;
							this.persistenceRequired = true;
							this.onItemPickup(var4, 1);
							var4.setDead();
						}
					}
				}
			}
		}

		theProfiler.endSection();
	}
}
