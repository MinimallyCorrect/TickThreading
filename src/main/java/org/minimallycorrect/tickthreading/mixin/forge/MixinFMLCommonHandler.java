package org.minimallycorrect.tickthreading.mixin.forge;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.minimallycorrect.mixin.Mixin;
import org.minimallycorrect.mixin.Overwrite;

@Mixin
public abstract class MixinFMLCommonHandler extends FMLCommonHandler {
	@Overwrite
	public Side getEffectiveSide() {
		return Side.SERVER;
	}

	@Overwrite
	public Side getSide() {
		return Side.SERVER;
	}
}
