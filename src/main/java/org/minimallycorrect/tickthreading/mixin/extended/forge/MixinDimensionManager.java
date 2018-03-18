package org.minimallycorrect.tickthreading.mixin.extended.forge;

import java.util.Hashtable;

import org.minimallycorrect.javatransformer.api.code.RETURN;
import org.minimallycorrect.mixin.*;
import org.minimallycorrect.tickthreading.config.Config;
import org.minimallycorrect.tickthreading.reporting.LeakDetector;

import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

@Mixin
public abstract class MixinDimensionManager extends DimensionManager {
	@Injectable
	public static void scheduleLeakCheck(WorldServer w) {
		LeakDetector.scheduleLeakCheck(w, w.getName());
	}

	@Injectable
	public static void abortWorldUnloading() {
		if (unloadQueue.isEmpty()) {
			throw RETURN.VOID();
		}
		if (!Config.$.worldUnloading) {
			unloadQueue.clear();
			throw RETURN.VOID();
		}
	}

	@Inject(injectable = "abortWorldUnloading", type = Type.BODY)
	@Inject(injectable = "scheduleLeakCheck", type = Type.METHOD_CALL, value = "flush")
	public static void unloadWorlds(@SuppressWarnings({"UseOfObsoleteCollectionType", "unused"}) Hashtable<Integer, long[]> worldTickTimes) {}

	@Overwrite
	public static void initDimension(int dim) {
		// TODO: re-implement this safely
		throw new UnsupportedOperationException();
	}
}
