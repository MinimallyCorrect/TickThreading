package me.nallar.tickthreading.minecraft.patched;

import java.util.ArrayList;
import java.util.Iterator;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemInWorldManager;
import net.minecraft.network.packet.Packet29DestroyEntity;
import net.minecraft.network.packet.Packet56MapChunks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkWatchEvent;

public class PatchEntityPlayerMP extends EntityPlayerMP {
	public PatchEntityPlayerMP(MinecraftServer par1MinecraftServer, World par2World, String par3Str, ItemInWorldManager par4ItemInWorldManager) {
		super(par1MinecraftServer, par2World, par3Str, par4ItemInWorldManager);
	}

	@Override
	public void onUpdate() {
		this.theItemInWorldManager.updateBlockRemoving();
		--this.initialInvulnerability;
		this.openContainer.updateCraftingResults();

		while (!this.destroyedItemsNetCache.isEmpty()) {
			int var1 = Math.min(this.destroyedItemsNetCache.size(), 127);
			int[] var2 = new int[var1];
			Iterator var3 = this.destroyedItemsNetCache.iterator();

			for (int var4 = 0; var3.hasNext() && var4 < var1; ) {
				var2[var4++] = (Integer) var3.next();
				var3.remove();
			}

			this.playerNetServerHandler.sendPacketToPlayer(new Packet29DestroyEntity(var2));
		}

		synchronized (loadedChunks) {
			if (!this.loadedChunks.isEmpty()) {
				ArrayList var6 = new ArrayList();
				Iterator var7 = this.loadedChunks.iterator();
				ArrayList var8 = new ArrayList();

				while (var7.hasNext() && var6.size() < 5) {
					ChunkCoordIntPair var9 = (ChunkCoordIntPair) var7.next();
					int x = var9.chunkXPos;
					int z = var9.chunkZPos;
					var7.remove();

					if (this.worldObj.blockExists(var9.chunkXPos << 4, 0, var9.chunkZPos << 4)) {
						var6.add(this.worldObj.getChunkFromChunkCoords(var9.chunkXPos, var9.chunkZPos));
						//BugFix: 16 makes it load an extra chunk, which isn't associated with a player, which makes it not unload unless a player walks near it.
						//ToDo: Find a way to efficiently clean abandoned chunks.
						//var8.addAll(((WorldServer) this.worldObj).getAllTileEntityInBox(var9.chunkXPos * 16, 0, var9.chunkZPos * 16, var9.chunkXPos * 16 + 16, 256, var9.chunkZPos * 16 + 16));
						var8.addAll(((WorldServer) this.worldObj).getAllTileEntityInBox(var9.chunkXPos * 16, 0, var9.chunkZPos * 16, var9.chunkXPos * 16 + 15, 256, var9.chunkZPos * 16 + 15));
					}
				}

				if (!var6.isEmpty()) {
					this.playerNetServerHandler.sendPacketToPlayer(new Packet56MapChunks(var6));
					Iterator var11 = var8.iterator();

					while (var11.hasNext()) {
						TileEntity var5 = (TileEntity) var11.next();
						this.sendTileEntityToPlayer(var5);
					}

					var11 = var6.iterator();

					while (var11.hasNext()) {
						Chunk var10 = (Chunk) var11.next();
						this.getServerForPlayer().getEntityTracker().func_85172_a(this, var10);
						MinecraftForge.EVENT_BUS.post(new ChunkWatchEvent.Watch(var10.getChunkCoordIntPair(), this));
					}
				}
			}
		}
	}
}
