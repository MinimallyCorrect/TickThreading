package org.minimallycorrect.tickthreading.mixin.world;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import org.minimallycorrect.mixin.Mixin;

@Mixin
public abstract class MixinChunkProviderServer extends ChunkProviderServer {
	@Override
	public boolean saveChunks(boolean force) {
		int i = 0;

		// id2ChunkMap should not be modified while we're doing this - don't duplicate it to a list like original MC code
		for (Chunk chunk : this.id2ChunkMap.values()) {
			if (force) {
				this.saveChunkExtraData(chunk);
			}

			if (chunk.needsSaving(force)) {
				this.saveChunkData(chunk);
				chunk.setModified(false);
				if (!force && ++i == 24) {
					return false;
				}
			}
		}

		return true;
	}
}
