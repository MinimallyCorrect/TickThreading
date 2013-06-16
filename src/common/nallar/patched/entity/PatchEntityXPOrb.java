package nallar.patched.entity;

import nallar.tickthreading.patcher.Declare;
import nallar.tickthreading.util.concurrent.SimpleMutex;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.world.World;

public abstract class PatchEntityXPOrb extends EntityXPOrb {
	private static final SimpleMutex lock = new SimpleMutex();

	public PatchEntityXPOrb(final World par1World, final double par2, final double par4, final double par6, final int par8) {
		super(par1World, par2, par4, par6, par8);
	}

	@Override
	@Declare
	public void addXPFrom(EntityXPOrb other) {
		lock.lock();
		synchronized (this) {
			synchronized (other) {
				lock.unlock();
				xpValue += other.getXpValue();
				other.setDead();
			}
		}
	}
}
