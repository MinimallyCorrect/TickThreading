package me.nallar.tickthreading.minecraft.patched;

import java.util.ArrayList;
import java.util.List;

import cpw.mods.fml.common.FMLLog;
import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.world.ChunkEvent;

public abstract class PatchChunk extends Chunk {
	private List<Entity> toAdd;
	@Declare
	public boolean allowEntityAdding_;

	public PatchChunk(World par1World, int par2, int par3) {
		super(par1World, par2, par3);
	}

	public void construct() {
		allowEntityAdding = true;
		toAdd = new ArrayList<Entity>();
	}

	@Override
	public void onChunkLoad() {
		this.worldObj.addTileEntity(this.chunkTileEntityMap.values());

		for (int var1 = 0; var1 < this.entityLists.length; ++var1) {
			this.worldObj.addLoadedEntities(this.entityLists[var1]);
		}
		MinecraftForge.EVENT_BUS.post(new ChunkEvent.Load(this));
		this.isChunkLoaded = true;
		this.allowEntityAdding = true;
		for (Entity entity : toAdd) {
			addEntity(entity);
		}
		toAdd.clear();
	}

	@Override
	public void addEntity(Entity par1Entity) {
		if (!this.allowEntityAdding) {
			toAdd.add(par1Entity);
			return;
		}
		int var2 = MathHelper.floor_double(par1Entity.posX / 16.0D);
		int var3 = MathHelper.floor_double(par1Entity.posZ / 16.0D);

		if (var2 != this.xPosition || var3 != this.zPosition) {
			FMLLog.warning("Entity %s added to the wrong chunk - expected x%d z%d, got x%d z%d", par1Entity.toString(), this.xPosition, this.zPosition, var2, var3);
			return;
		}

		this.hasEntities = true;

		int var4 = MathHelper.floor_double(par1Entity.posY / 16.0D);

		if (var4 < 0) {
			var4 = 0;
		}

		if (var4 >= this.entityLists.length) {
			var4 = this.entityLists.length - 1;
		}
		MinecraftForge.EVENT_BUS.post(new EntityEvent.EnteringChunk(par1Entity, this.xPosition, this.zPosition, par1Entity.chunkCoordX, par1Entity.chunkCoordZ));
		par1Entity.addedToChunk = true;
		par1Entity.chunkCoordX = this.xPosition;
		par1Entity.chunkCoordY = var4;
		par1Entity.chunkCoordZ = this.zPosition;
		this.entityLists[var4].add(par1Entity);
	}
}
