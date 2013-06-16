package nallar.patched.entity;

import java.util.Iterator;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemInWorldManager;
import net.minecraft.network.packet.Packet29DestroyEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

public abstract class PatchEntityPlayerMP extends EntityPlayerMP {
	public PatchEntityPlayerMP(MinecraftServer par1MinecraftServer, World par2World, String par3Str, ItemInWorldManager par4ItemInWorldManager) {
		super(par1MinecraftServer, par2World, par3Str, par4ItemInWorldManager);
	}

	@Override
	public void onUpdate() {
		xpCooldown = 0;
		this.theItemInWorldManager.updateBlockRemoving();
		--this.initialInvulnerability;
		this.openContainer.detectAndSendChanges();

		List<Integer> destroyedItemsNetCache = this.destroyedItemsNetCache;
		while (!destroyedItemsNetCache.isEmpty()) {
			int items = Math.min(destroyedItemsNetCache.size(), 127);
			int[] removedItems = new int[items];
			Iterator<Integer> iterator = destroyedItemsNetCache.iterator();

			for (int var4 = 0; iterator.hasNext() && var4 < items; var4++) {
				removedItems[var4] = iterator.next();
				iterator.remove();
			}

			this.playerNetServerHandler.sendPacketToPlayer(new Packet29DestroyEntity(removedItems));
		}

		if (playerNetServerHandler.connectionClosed) {
			worldObj.removePlayerEntityDangerously(this);
		}
	}
}
