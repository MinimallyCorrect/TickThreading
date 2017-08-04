package org.minimallycorrect.tickthreading.mixin.forge;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.minimallycorrect.mixin.Mixin;

@Mixin
public abstract class MixinFMLCommonHandler extends FMLCommonHandler {
	@Override
	public Side getEffectiveSide() {
		return Side.SERVER;
	}

	@Override
	public Side getSide() {
		return Side.SERVER;
	}
}
