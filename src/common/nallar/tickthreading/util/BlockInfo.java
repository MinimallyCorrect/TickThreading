package nallar.tickthreading.util;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

public class BlockInfo {
	public final int id;
	public final int meta;
	public final Block type;
	public final String name;

	public BlockInfo(final int id, final int meta) {
		this.id = id;
		this.meta = meta;
		Block type;
		this.type = type = Block.blocksList[id];
		Item item = type == null ? null : Item.itemsList[id];
		ItemStack itemType = item == null ? null : new ItemStack(id, 1, meta);
		String name = itemType == null ? (type == null ? "unknown" : type.getLocalizedName()) : item.getItemDisplayName(itemType);
		String preTranslateName = "item." + name;
		String localizedName = StatCollector.translateToLocal(preTranslateName);
		//noinspection StringEquality
		if (localizedName != null && localizedName != preTranslateName) {
			name = localizedName;
		}
		this.name = name;
	}

	@Override
	public String toString() {
		return id + ':' + meta + ", " + name;
	}
}
