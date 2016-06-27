package nallar.tickthreading.mixin.world;

import me.nallar.mixin.Add;
import me.nallar.mixin.Mixin;
import net.minecraft.world.World;

@Mixin
public abstract class MixinWorld extends World {
	@Add
	public boolean unloaded_;
	@Add
	private String cachedName_;

	protected MixinWorld() {
		super(null, null, null, null, false);
	}

	@Add
	public int getDimensionId() {
		return provider.getDimensionType().getId();
	}

	@Add
	public String getName() {
		String name = cachedName;
		if (name != null) {
			return name;
		}
		int dimensionId = getDimensionId();
		name = worldInfo.getWorldName();
		if (name.equals("DIM" + dimensionId) || "world".equals(name)) {
			name = provider.getDimensionType().getName();
		}
		if (name.startsWith("world_") && name.length() != 6) {
			name = name.substring(6);
		}
		name += '/' + dimensionId + (isRemote ? "-r" : "");
		cachedName = name;
		return name;
	}
}
