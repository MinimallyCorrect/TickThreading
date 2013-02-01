package me.nallar.patched;

import net.minecraft.util.ChunkCoordinates;
import net.minecraft.village.VillageCollection;
import net.minecraft.village.VillageDoorInfo;

public abstract class PatchVillageCollection extends VillageCollection {
	public PatchVillageCollection(String par1Str) {
		super(par1Str);
	}

	@Override
	protected void addUnassignedWoodenDoorsAroundToNewDoorsList(ChunkCoordinates par1ChunkCoordinates) {
		byte var2 = 16;
		byte var3 = 4;
		byte var4 = 16;

		if (!worldObj.checkChunksExist(par1ChunkCoordinates.posX - var2,
				par1ChunkCoordinates.posY - var3,
				par1ChunkCoordinates.posZ - var4,
				par1ChunkCoordinates.posX + var2,
				par1ChunkCoordinates.posY + var3,
				par1ChunkCoordinates.posZ + var4)) {
			villagerPositionsList.add(par1ChunkCoordinates);
			return;
		}

		for (int var5 = par1ChunkCoordinates.posX - var2; var5 < par1ChunkCoordinates.posX + var2; ++var5) {
			for (int var6 = par1ChunkCoordinates.posY - var3; var6 < par1ChunkCoordinates.posY + var3; ++var6) {
				for (int var7 = par1ChunkCoordinates.posZ - var4; var7 < par1ChunkCoordinates.posZ + var4; ++var7) {
					if (this.isWoodenDoorAt(var5, var6, var7)) {
						VillageDoorInfo var8 = this.getVillageDoorAt(var5, var6, var7);

						if (var8 == null) {
							this.addDoorToNewListIfAppropriate(var5, var6, var7);
						} else {
							var8.lastActivityTimestamp = this.tickCounter;
						}
					}
				}
			}
		}
	}
}
