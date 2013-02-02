package me.nallar.patched;

import com.google.common.collect.ImmutableSetMultimap;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;

public abstract class PatchForgeChunkManager extends ForgeChunkManager {
	public static ImmutableSetMultimap<ChunkCoordIntPair, Ticket> getPersistentChunksFor(World world) {
		ImmutableSetMultimap<ChunkCoordIntPair, Ticket> forcedChunks = ForgeChunkManager.forcedChunks.get(world);
		return forcedChunks == null ? ImmutableSetMultimap.<ChunkCoordIntPair, Ticket>of() : forcedChunks;
	}
}
