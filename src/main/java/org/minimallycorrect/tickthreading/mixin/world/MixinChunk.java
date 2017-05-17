package org.minimallycorrect.tickthreading.mixin.world;

import net.minecraft.world.chunk.Chunk;
import org.minimallycorrect.mixin.Mixin;

@Mixin
public abstract class MixinChunk extends Chunk {
	public MixinChunk() {
		super(null, 0, 0);
	}

	@Override
	public boolean needsSaving(boolean force) {
		if (force) {
			return isModified || (hasEntities && this.worldObj.getTotalWorldTime() != this.lastSaveTime);
		}

		return (isModified || hasEntities) && this.worldObj.getTotalWorldTime() >= this.lastSaveTime + 2111L;
	}
}
