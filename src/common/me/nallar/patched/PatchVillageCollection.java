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
		byte xRange = 16;
		byte yRange = 4;
		byte zRange = 16;

		if (!worldObj.checkChunksExist(par1ChunkCoordinates.posX - xRange,
				par1ChunkCoordinates.posY - yRange,
				par1ChunkCoordinates.posZ - zRange,
				par1ChunkCoordinates.posX + xRange,
				par1ChunkCoordinates.posY + yRange,
				par1ChunkCoordinates.posZ + zRange)) {
			villagerPositionsList.add(par1ChunkCoordinates);
			return;
		}

		for (int var5 = par1ChunkCoordinates.posX - xRange; var5 < par1ChunkCoordinates.posX + xRange; ++var5) {
			for (int var6 = par1ChunkCoordinates.posY - yRange; var6 < par1ChunkCoordinates.posY + yRange; ++var6) {
				for (int var7 = par1ChunkCoordinates.posZ - zRange; var7 < par1ChunkCoordinates.posZ + zRange; ++var7) {
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
