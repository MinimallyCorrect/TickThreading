package me.nallar.patched.forge;

import com.google.common.collect.ImmutableSetMultimap;

import me.nallar.tickthreading.collections.ForcedChunksRedirectMap;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;

public abstract class PatchForgeChunkManager extends ForgeChunkManager {
	public static void staticConstruct() {
		forcedChunks = new ForcedChunksRedirectMap();
	}

	public static ImmutableSetMultimap<ChunkCoordIntPair, Ticket> getPersistentChunksFor(World world) {
		return world.forcedChunks;
	}
}
