package org.minimallycorrect.tickthreading.mixin.forge;

import me.nallar.mixin.Mixin;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

@Mixin
public abstract class MixinFMLCommonHandler extends FMLCommonHandler {
	private MixinFMLCommonHandler() {
	}

	@Override
	public Side getEffectiveSide() {
		return Side.SERVER;
	}

	@Override
	public Side getSide() {
		return Side.SERVER;
	}
}
