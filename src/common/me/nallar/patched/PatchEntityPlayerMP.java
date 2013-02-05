package me.nallar.patched;

import java.util.ArrayList;
import java.util.Iterator;

import javassist.is.faulty.Timings;
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

public abstract class PatchEntityPlayerMP extends EntityPlayerMP {
	public PatchEntityPlayerMP(MinecraftServer par1MinecraftServer, World par2World, String par3Str, ItemInWorldManager par4ItemInWorldManager) {
		super(par1MinecraftServer, par2World, par3Str, par4ItemInWorldManager);
	}

	@Override
	public void onUpdate() {
		this.theItemInWorldManager.updateBlockRemoving();
		--this.initialInvulnerability;
		this.openContainer.detectAndSendChanges();

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
	}
}
