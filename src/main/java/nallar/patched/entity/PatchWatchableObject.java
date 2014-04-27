package nallar.patched.entity;

import nallar.patched.annotation.Public;
import net.minecraft.entity.WatchableObject;

@Public
public abstract class PatchWatchableObject extends WatchableObject {
	public PatchWatchableObject(final int par1, final int par2, final Object par3Obj) {
		super(par1, par2, par3Obj);
	}
}
