package nallar.tickthreading.mixin;

import me.nallar.mixin.Add;
import me.nallar.mixin.Mixin;
import net.minecraft.world.World;

@Mixin
public abstract class MixinWorld extends World {
	/* TODO: per-world tasks
	@Add
	private final ArrayDeque<Runnable> tasks_ = null;
	*/
	@Add
	public boolean unloaded_;
	@Add
	private String cachedName_;

	protected MixinWorld() {
		super(null, null, null, null, false);
		/* TODO: per-world tasks
		tasks = new ArrayDeque<>();
		*/
	}

	/*
	@Override
	public void updateEntities() {
		// TODO: Copy from old TT, merge changes in MC, run from tasks list
	}
	*/

	@Add
	public int getDimension() {
		return provider.getDimensionId();
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
