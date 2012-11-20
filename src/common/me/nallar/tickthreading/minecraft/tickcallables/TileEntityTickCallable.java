package me.nallar.tickthreading.minecraft.tickcallables;

import java.util.ArrayList;
import java.util.List;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.ThreadManager;
import net.minecraft.src.Chunk;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;

public class TileEntityTickCallable<T> extends TickCallable {
	private final List<TileEntity> tileEntityList = new ArrayList<TileEntity>();

	public TileEntityTickCallable(World world, String identifier, ThreadManager manager, int hashCode) {
		super(world, identifier, manager, hashCode);
	}

	@Override
	public T call() {
		try {
			IChunkProvider chunkProvider = world.getChunkProvider();
			for (TileEntity tileEntity : tileEntityList) {
				if (tileEntity.isInvalid()) {
					manager.remove(tileEntity);
					Log.warning("Removed invalid tile: " + tileEntity.xCoord + ", " + tileEntity.yCoord + ", " + tileEntity.zCoord + "\ttype:" + tileEntity.getClass().toString());
					if (chunkProvider.chunkExists(tileEntity.xCoord >> 4, tileEntity.zCoord >> 4)) {
						Chunk chunk = world.getChunkFromChunkCoords(tileEntity.xCoord >> 4, tileEntity.zCoord >> 4);
						if (chunk != null) {
							chunk.cleanChunkBlockTileEntity(tileEntity.xCoord & 0xf, tileEntity.yCoord, tileEntity.zCoord & 0xf);
						}
					}
				} else {
					tileEntity.updateEntity();
				}
				if (manager.getHashCode(tileEntity) != hashCode) {
					manager.remove(tileEntity);
					manager.add(tileEntity);
				}
			}
		} catch (Exception exception) {
			Log.severe("Exception during tile entity tick at " + identifier + ":", exception);
		}
		return null;
	}

	public void add(TileEntity tileEntity) {
		if (!tileEntityList.contains(tileEntity)) {
			tileEntityList.add(tileEntity);
		}
	}

	public boolean remove(TileEntity tileEntity) {
		return tileEntityList.remove(tileEntity);
	}

	@Override
	public boolean isEmpty() {
		return tileEntityList.isEmpty();
	}
}
