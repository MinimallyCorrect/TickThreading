package nallar.patched.server;

import java.io.File;

import nallar.tickthreading.minecraft.TickThreading;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldServer;

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
		logger.info("Preparing start region for level " + dimension);
		WorldServer worldServer = this.worldServers[dimension];
		ChunkCoordinates spawnPoint = worldServer.getSpawnPoint();

		for (int var11 = -192; var11 <= 192 && this.isServerRunning(); var11 += 16) {
			for (int var12 = -192; var12 <= 192 && this.isServerRunning(); var12 += 16) {
				long currentTime = System.currentTimeMillis();

				if (currentTime - startTime > 1000L) {
					this.outputPercentRemaining("Preparing spawn area", loadedChunks * 100 / 625);
					startTime = currentTime;
				}

				++loadedChunks;
				worldServer.theChunkProviderServer.loadChunk(spawnPoint.posX + var11 >> 4, spawnPoint.posZ + var12 >> 4);
			}
		}

		this.clearCurrentTask();
	}
}
