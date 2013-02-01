package me.nallar.tickthreading.minecraft.patched;

import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.entity.EntityTracker;
import net.minecraft.world.WorldServer;

public abstract class PatchEntityTracker extends EntityTracker {
	public PatchEntityTracker(WorldServer par1WorldServer) {
		super(par1WorldServer);
	}

	@Declare
	public boolean isTracking(int id) {
		return this.trackedEntityIDs.containsItem(id);
	}
}
