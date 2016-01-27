package nallar.mixin;

import me.nallar.mixin.Add;
import me.nallar.mixin.Mixin;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

@Mixin(makePublic = true)
public abstract class MixinWorld extends World {
	protected MixinWorld(ISaveHandler saveHandlerIn, WorldInfo info, WorldProvider providerIn, Profiler profilerIn, boolean client) {
		super(saveHandlerIn, info, providerIn, profilerIn, client);
	}

	@Add
	public boolean unloaded;

	@Add
	public String getName() {
		throw new UnsupportedOperationException("TODO");// TODO
	}
}
