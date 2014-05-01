package nallar.patched;

import nallar.tickthreading.patcher.Declare;
import net.minecraft.util.Vec3;
import net.minecraft.util.Vec3Pool;

public abstract class PatchVec3 extends Vec3 {
	protected PatchVec3(final Vec3Pool par1Vec3Pool, final double par2, final double par4, final double par6) {
		super(par1Vec3Pool, par2, par4, par6);
	}

	@Override
	@Declare
	public Vec3 setThisComponents(double v, double v1, double v2) {
		// Guaranteed to set in this vec3 - not just making setComponents public as it may change to returning a different
		// vec3 in future MC versions
		xCoord = v;
		yCoord = v1;
		zCoord = v2;
		return this;
	}
}
