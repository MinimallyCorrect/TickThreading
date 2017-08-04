package org.minimallycorrect.tickthreading.mixin.world;

import net.minecraft.world.chunk.Chunk;
import org.minimallycorrect.mixin.Mixin;

@Mixin
public abstract class MixinChunk extends Chunk {
	@Override
	public boolean needsSaving(boolean force) {
		if (force) {
			return dirty || (hasEntities && this.world.getTotalWorldTime() != this.lastSaveTime);
		}

		return (dirty || hasEntities) && this.world.getTotalWorldTime() >= this.lastSaveTime + 2111L;
	}
}
