package nallar.patched.server;

import nallar.tickthreading.minecraft.TickThreading;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldServer;

import java.io.*;

public abstract class PatchMinecraftServerForge extends MinecraftServer {
	public PatchMinecraftServerForge(File par1File) {
		super(par1File);
	}

	@Override
	public String getServerModName() {
		return "tickthreading,forge,fml";
	}

	@Override
	protected void initialWorldChunkLoad() {
		if (!TickThreading.instance.shouldLoadSpawn) {
			return;
		}
		int loadedChunks = 0;
		this.setUserMessage("menu.generatingTerrain");
		byte dimension = 0;
		long startTime = System.currentTimeMillis();
		getLogAgent().logInfo("Loading spawn area for dimension " + dimension);
		WorldServer worldServer = this.worldServers[dimension];
		ChunkCoordinates spawnPoint = worldServer.getSpawnPoint();

		for (int var11 = -192; var11 <= 192 && this.isServerRunning(); var11 += 16) {
			for (int var12 = -192; var12 <= 192 && this.isServerRunning(); var12 += 16) {
				long currentTime = System.currentTimeMillis();

				if (currentTime - startTime > 1000L) {
					this.outputPercentRemaining("Loading spawn chunks", loadedChunks * 100 / 625);
					startTime = currentTime;
				}

				++loadedChunks;
				worldServer.theChunkProviderServer.loadChunk(spawnPoint.posX + var11 >> 4, spawnPoint.posZ + var12 >> 4);
			}
		}

		this.clearCurrentTask();
	}
}
