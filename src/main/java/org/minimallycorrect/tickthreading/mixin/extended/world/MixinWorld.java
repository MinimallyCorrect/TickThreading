package org.minimallycorrect.tickthreading.mixin.extended.world;

import net.minecraft.world.World;
import org.minimallycorrect.mixin.Mixin;

@Mixin
public abstract class MixinWorld extends World {	/* TODO: per-world tasks
	@Add
	private final ArrayDeque<Runnable> tasks_ = new ArrayDeque<>();
	*/
	@Override
	public void updateEntities() {
		// TODO: Copy from old TT, merge changes in MC, run from tasks list
	}
}
