package me.nallar.patched;

import java.util.HashMap;

import com.google.common.collect.ImmutableSetMultimap;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;

public abstract class PatchForgeChunkManager extends ForgeChunkManager {
	private static ImmutableSetMultimap<ChunkCoordIntPair, Ticket> emptyMap;

	public static void staticConstruct() {
		forcedChunks = new HashMap<World, ImmutableSetMultimap<ChunkCoordIntPair, Ticket>>();
		emptyMap = ImmutableSetMultimap.of();
	}

	public static ImmutableSetMultimap<ChunkCoordIntPair, Ticket> getPersistentChunksFor(World world) {
		ImmutableSetMultimap<ChunkCoordIntPair, Ticket> forcedChunks = ForgeChunkManager.forcedChunks.get(world);
		return forcedChunks == null ? emptyMap : forcedChunks;
	}
}
