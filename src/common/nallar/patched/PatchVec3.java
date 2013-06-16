package nallar.patched;

import net.minecraft.util.Vec3;
import net.minecraft.util.Vec3Pool;

/**
 * Not actually patched, just so the prepatcher will make the constructor accessible.
 */
public abstract class PatchVec3 extends Vec3 {
	protected PatchVec3(final Vec3Pool par1Vec3Pool, final double par2, final double par4, final double par6) {
		super(par1Vec3Pool, par2, par4, par6);
	}
}
