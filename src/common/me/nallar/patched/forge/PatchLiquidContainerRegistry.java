package me.nallar.patched.forge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.liquids.LiquidContainerData;
import net.minecraftforge.liquids.LiquidContainerRegistry;
import net.minecraftforge.liquids.LiquidStack;

public abstract class PatchLiquidContainerRegistry {
	public static final int BUCKET_VOLUME = 1000;
	public static final ItemStack EMPTY_BUCKET = new ItemStack(Item.bucketEmpty);
	private static final Map<FilledItemFromLiquidKey, LiquidContainerData> mapFilledItemFromLiquid = new HashMap<FilledItemFromLiquidKey, LiquidContainerData>();
	private static final Map<LiquidFromFilledItemKey, LiquidContainerData> mapLiquidFromFilledItem = new HashMap<LiquidFromFilledItemKey, LiquidContainerData>();
	private static final Set<List> setContainerValidation = new HashSet<List>();
	private static final Set<List> setLiquidValidation = new HashSet<List>();
	private static final ArrayList<LiquidContainerData> liquids = new ArrayList<LiquidContainerData>();

	/**
	 * Default registrations
	 */
	static {
		registerLiquid(new LiquidContainerData(new LiquidStack(Block.waterStill, LiquidContainerRegistry.BUCKET_VOLUME), new ItemStack(Item.bucketWater), new ItemStack(Item.bucketEmpty)));
		registerLiquid(new LiquidContainerData(new LiquidStack(Block.lavaStill, LiquidContainerRegistry.BUCKET_VOLUME), new ItemStack(Item.bucketLava), new ItemStack(Item.bucketEmpty)));
		registerLiquid(new LiquidContainerData(new LiquidStack(Block.waterStill, LiquidContainerRegistry.BUCKET_VOLUME), new ItemStack(Item.potion), new ItemStack(Item.glassBottle)));
		// registerLiquid(new LiquidContainerData(new LiquidStack(Item.bucketMilk, LiquidContainerRegistry.BUCKET_VOLUME), new ItemStack(Item.bucketMilk), new ItemStack(Item.bucketEmpty)));
	}

	public static class LiquidFromFilledItemKey {
		private final int itemID;
		private final int itemDamage;

		public LiquidFromFilledItemKey(final int itemID, final int itemDamage) {
			this.itemID = itemID;
			this.itemDamage = itemID;
		}

		@Override
		public boolean equals(final Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof LiquidFromFilledItemKey)) {
				return false;
			}

			final LiquidFromFilledItemKey liquidFromFilledItemKey = (LiquidFromFilledItemKey) o;

			return itemDamage == liquidFromFilledItemKey.itemDamage && itemID == liquidFromFilledItemKey.itemID;
		}

		@Override
		public int hashCode() {
			int result = itemID;
			result = 31 * result + itemDamage;
			return result;
		}
	}

	public static class FilledItemFromLiquidKey {
		private final int containerItemID;
		private final int containerDamage;
		private final int liquidItemID;
		private final int liquidMeta;

		public FilledItemFromLiquidKey(final int containerItemID, final int containerDamage, final int liquidItemID, final int liquidMeta) {
			this.containerItemID = containerItemID;
			this.containerDamage = containerDamage;
			this.liquidItemID = liquidItemID;
			this.liquidMeta = liquidMeta;
		}

		@Override
		public boolean equals(final Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof FilledItemFromLiquidKey)) {
				return false;
			}

			final FilledItemFromLiquidKey that = (FilledItemFromLiquidKey) o;

			return containerDamage == that.containerDamage && containerItemID == that.containerItemID && liquidItemID == that.liquidItemID && liquidMeta == that.liquidMeta;
		}

		@Override
		public int hashCode() {
			int result = containerItemID;
			result = 31 * result + containerDamage;
			result = 31 * result + liquidItemID;
			result = 31 * result + liquidMeta;
			return result;
		}
	}

	/**
	 * To register a container with a non-bucket size, the LiquidContainerData entry simply needs to use a size other than LiquidManager.BUCKET_VOLUME
	 */
	public static synchronized void registerLiquid(LiquidContainerData data) {
		ItemStack container = data.container;
		LiquidStack liquid = data.stillLiquid;
		mapFilledItemFromLiquid.put(new FilledItemFromLiquidKey(container.itemID, container.getItemDamage(), liquid.itemID, liquid.itemMeta), data);
		mapLiquidFromFilledItem.put(new LiquidFromFilledItemKey(data.filled.itemID, data.filled.getItemDamage()), data);
		setContainerValidation.add(Arrays.asList(data.container.itemID, data.container.getItemDamage()));
		setLiquidValidation.add(Arrays.asList(data.stillLiquid.itemID, data.stillLiquid.itemMeta));

		liquids.add(data);
	}

	public static LiquidStack getLiquidForFilledItem(ItemStack filledContainer) {
		if (filledContainer == null) {
			return null;
		}

		LiquidContainerData ret = mapLiquidFromFilledItem.get(new LiquidFromFilledItemKey(filledContainer.itemID, filledContainer.getItemDamage()));
		return ret == null ? null : ret.stillLiquid.copy();
	}

	public static ItemStack fillLiquidContainer(LiquidStack liquid, ItemStack emptyContainer) {
		if (emptyContainer == null || liquid == null) {
			return null;
		}

		LiquidContainerData ret = mapFilledItemFromLiquid.get(new FilledItemFromLiquidKey(emptyContainer.itemID, emptyContainer.getItemDamage(), liquid.itemID, liquid.itemMeta));

		if (ret != null && liquid.amount >= ret.stillLiquid.amount) {
			return ret.filled.copy();
		}

		return null;
	}

	public static boolean containsLiquid(ItemStack filledContainer, LiquidStack liquid) {
		if (filledContainer == null || liquid == null) {
			return false;
		}

		LiquidContainerData ret = mapLiquidFromFilledItem.get(new LiquidFromFilledItemKey(filledContainer.itemID, filledContainer.getItemDamage()));

		return ret != null && ret.stillLiquid.isLiquidEqual(liquid);
	}

	public static boolean isBucket(ItemStack container) {
		if (container == null) {
			return false;
		}

		if (container.isItemEqual(EMPTY_BUCKET)) {
			return true;
		}

		LiquidContainerData ret = mapLiquidFromFilledItem.get(new LiquidFromFilledItemKey(container.itemID, container.getItemDamage()));
		return ret != null && ret.container.isItemEqual(EMPTY_BUCKET);
	}

	public static boolean isContainer(ItemStack container) {
		return isEmptyContainer(container) || isFilledContainer(container);
	}

	public static boolean isEmptyContainer(ItemStack emptyContainer) {
		return emptyContainer != null && setContainerValidation.contains(Arrays.asList(emptyContainer.itemID, emptyContainer.getItemDamage()));
	}

	public static boolean isFilledContainer(ItemStack filledContainer) {
		return filledContainer != null && getLiquidForFilledItem(filledContainer) != null;
	}

	public static boolean isLiquid(ItemStack item) {
		return item != null && setLiquidValidation.contains(Arrays.asList(item.itemID, item.getItemDamage()));
	}

	public static LiquidContainerData[] getRegisteredLiquidContainerData() {
		return liquids.toArray(new LiquidContainerData[liquids.size()]);
	}
}

