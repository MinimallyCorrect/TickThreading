package nallar.patched.entity;

import nallar.tickthreading.patcher.Declare;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.world.WorldServer;

public abstract class PatchEntityTracker extends EntityTracker {
	public PatchEntityTracker(WorldServer par1WorldServer) {
		super(par1WorldServer);
	}

	@Override
	@Declare
	public boolean isTracking(int id) {
		return this.trackedEntityIDs.containsItem(id);
	}

	@Override
	@Declare
	public Entity getEntity(int id) {
		EntityTrackerEntry entityTrackerEntry = (EntityTrackerEntry) this.trackedEntityIDs.lookup(id);
		if (entityTrackerEntry == null) {
			return null;
		}
		return entityTrackerEntry.myEntity;
	}
}
