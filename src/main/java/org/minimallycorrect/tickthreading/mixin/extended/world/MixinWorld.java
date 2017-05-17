package org.minimallycorrect.tickthreading.mixin.extended.world;

import me.nallar.mixin.Mixin;
import net.minecraft.world.World;

@Mixin
public abstract class MixinWorld extends World {	/* TODO: per-world tasks
	@Add
	private final ArrayDeque<Runnable> tasks_ = null;
	*/

	@SuppressWarnings("ConstantConditions")
	protected MixinWorld() {
		super(null, null, null, null, false);
		/* TODO: per-world tasks
		tasks = new ArrayDeque<>();
		*/
	}

	@Override
	public void updateEntities() {
		// TODO: Copy from old TT, merge changes in MC, run from tasks list
	}
}
