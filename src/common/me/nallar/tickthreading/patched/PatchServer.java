package me.nallar.tickthreading.patched;

import java.io.File;

import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.crash.CrashReport;
import net.minecraft.network.packet.Packet4UpdateTime;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.gui.IUpdatePlayerListBox;
import net.minecraft.util.ReportedException;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

public abstract class PatchServer extends MinecraftServer {
	public PatchServer(File par1File) {
		super(par1File);
	}

	@Override
	public void updateTimeLightAndEntities() {
		this.theProfiler.startSection("levels");
		int var1;

		Integer[] ids = DimensionManager.getIDs();
		for (int x = 0; x < ids.length; x++) {
			int id = ids[x];
			long var2 = System.nanoTime();

			if (id == 0 || this.getAllowNether()) {
				WorldServer var4 = DimensionManager.getWorld(id);
				this.theProfiler.startSection(var4.getWorldInfo().getWorldName());
				this.theProfiler.startSection("pools");
				var4.getWorldVec3Pool().clear();
				this.theProfiler.endSection();

				if (this.tickCounter % 60 == 0) {
					this.theProfiler.startSection("timeSync");
					this.serverConfigManager.sendPacketToAllPlayersInDimension(new Packet4UpdateTime(var4.getTotalWorldTime(), var4.getWorldTime()), var4.provider.dimensionId);
					this.theProfiler.endSection();
				}

				this.theProfiler.startSection("forgeTick");
				FMLCommonHandler.instance().onPreWorldTick(var4);

				CrashReport var6;

				this.theProfiler.endStartSection("worldTick");
				try {
					var4.tick();
				} catch (Throwable var8) {
					var6 = CrashReport.makeCrashReport(var8, "Exception ticking world");
					var4.addWorldInfoToCrashReport(var6);
					throw new ReportedException(var6);
				}

				this.theProfiler.endStartSection("entityTick");
				try {
					var4.updateEntities();
				} catch (Throwable var7) {
					var6 = CrashReport.makeCrashReport(var7, "Exception ticking world entities");
					var4.addWorldInfoToCrashReport(var6);
					throw new ReportedException(var6);
				}

				this.theProfiler.endStartSection("postForgeTick");
				FMLCommonHandler.instance().onPostWorldTick(var4);
				this.theProfiler.endSection();
				this.theProfiler.startSection("tracker");
				var4.getEntityTracker().updateTrackedEntities();
				this.theProfiler.endSection();
				this.theProfiler.endSection();
			}

			worldTickTimes.get(id)[this.tickCounter % 100] = System.nanoTime() - var2;
		}

		this.theProfiler.endStartSection("dim_unloading");
		DimensionManager.unloadWorlds(worldTickTimes);
		this.theProfiler.endStartSection("connection");
		this.getNetworkThread().networkTick();
		this.theProfiler.endStartSection("players");
		this.serverConfigManager.sendPlayerInfoToAllPlayers();
		this.theProfiler.endStartSection("tickables");

		for (var1 = 0; var1 < this.tickables.size(); ++var1) {
			((IUpdatePlayerListBox) this.tickables.get(var1)).update();
		}

		this.theProfiler.endSection();
	}
}
