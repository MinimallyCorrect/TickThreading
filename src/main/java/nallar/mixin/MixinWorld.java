package nallar.mixin;

import me.nallar.mixin.Add;
import me.nallar.mixin.Mixin;
import nallar.log.Log;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;

@Mixin
public abstract class MixinWorld extends World {
	@Add
	public int dimensionId;
	@Add
	public boolean unloaded;
	@Add
	public int originalDimension;
	@Add
	private String cachedName;

	protected MixinWorld(ISaveHandler saveHandlerIn, WorldInfo info, WorldProvider providerIn, Profiler profilerIn, boolean client) {
		super(saveHandlerIn, info, providerIn, profilerIn, client);
	}

	@Add
	public int getDimension() {
		return dimensionId;
	}

	@Add
	public void setDimension(int dimensionId) {
		WorldProvider provider = this.provider;
		this.dimensionId = dimensionId;
		if (provider.getDimensionId() != dimensionId) {
			try {
				DimensionManager.registerDimension(dimensionId, provider.getDimensionId());
			} catch (Throwable t) {
				Log.warning("Failed to register corrected dimension ID with DimensionManager", t);
			}
			originalDimension = provider.getDimensionId();
			provider.setDimension(dimensionId);
			Log.warning("World " + getName() + "'s provider dimensionId is not the same as its real dimensionId. Provider ID: " + originalDimension + ", real ID: " + dimensionId);
		}
		cachedName = null;
		Log.fine("Set dimension ID for " + this.getName());
	}

	@Add
	public String getName() {
		String name = cachedName;
		if (name != null) {
			return name;
		}
		int dimensionId = getDimension();
		name = worldInfo.getWorldName();
		if (name.equals("DIM" + dimensionId) || "world".equals(name)) {
			name = provider.getDimensionName();
		}
		if (name.startsWith("world_") && name.length() != 6) {
			name = name.substring(6);
		}
		cachedName = name = (name + '/' + dimensionId + (isRemote ? "-r" : ""));
		return name;
	}
}
