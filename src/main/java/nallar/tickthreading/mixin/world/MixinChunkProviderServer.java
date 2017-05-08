package nallar.tickthreading.mixin.world;

import me.nallar.mixin.Mixin;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

@Mixin
public abstract class MixinChunkProviderServer extends ChunkProviderServer {
	public MixinChunkProviderServer() {
		super(null, null, null);
	}

	@Override
	public boolean saveChunks(boolean force) {
		int i = 0;

		for (Chunk chunk : this.id2ChunkMap.values()) {
			if (force) {
				this.saveChunkExtraData(chunk);
			}

			if (chunk.needsSaving(force)) {
				this.saveChunkData(chunk);
				chunk.setModified(false);
				if (++i == 12 && !force) {
					return false;
				}
			}
		}

		return true;
	}
}
