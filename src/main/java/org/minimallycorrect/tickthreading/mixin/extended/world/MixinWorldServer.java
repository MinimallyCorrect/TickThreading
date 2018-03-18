package org.minimallycorrect.tickthreading.mixin.extended.world;

import lombok.val;
import net.minecraft.crash.CrashReport;
import net.minecraft.network.play.server.SPacketTimeUpdate;
import net.minecraft.util.ReportedException;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.minimallycorrect.mixin.Add;
import org.minimallycorrect.mixin.Mixin;

@Mixin
public abstract class MixinWorldServer extends WorldServer {
	@Add
	public void tickWithEvents() {
		if (this.getTotalWorldTime() % 20 == 0) {
			mcServer.getPlayerList().sendPacketToAllPlayersInDimension(new SPacketTimeUpdate(getTotalWorldTime(), getWorldTime(), getGameRules().getBoolean("doDaylightCycle")), provider.getDimension());
		}
		FMLCommonHandler.instance().onPreWorldTick(this);
		try {
			tick();
		} catch (Throwable throwable) {
			val crashreport = CrashReport.makeCrashReport(throwable, "Exception ticking world");
			addWorldInfoToCrashReport(crashreport);
			throw new ReportedException(crashreport);
		}
		try {
			updateEntities();
		} catch (Throwable throwable) {
			val crashreport = CrashReport.makeCrashReport(throwable, "Exception ticking world entities");
			addWorldInfoToCrashReport(crashreport);
			throw new ReportedException(crashreport);
		}
		FMLCommonHandler.instance().onPostWorldTick(this);
		getEntityTracker().tick();
	}
}
