package nallar.tickthreading.mixin.world;

import me.nallar.mixin.Mixin;
import net.minecraft.world.chunk.Chunk;

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
