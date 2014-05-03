package nallar.patched.entity;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.World;

import java.util.*;

public class PatchEntityLiving extends EntityLiving {
	public PatchEntityLiving(World par1World) {
		super(par1World);
	}

	@Override
	public void onLivingUpdate() {
		final World worldObj = this.worldObj;
		final Profiler theProfiler = worldObj.theProfiler;
		theProfiler.startSection("looting");

		if (!worldObj.isRemote && this.canPickUpLoot() && !this.dead && !this.isDead && this.getHealth() > 0 && worldObj.getGameRules().getGameRuleBooleanValue("mobGriefing")) {
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
