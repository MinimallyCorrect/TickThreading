package me.nallar.patched.nbt;

import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.nbt.NBTBase;

public abstract class PatchNBTBase extends NBTBase {
	public PatchNBTBase(final String par1Str) {
		super(par1Str);
	}

	@Override
	@Declare
	public String getRawName() {
		return name;
	}
}
