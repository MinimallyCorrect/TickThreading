package me.nallar.collections;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableSetMultimap;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;

// Only implements put/get/remove properly, nothing else needed for what forge does.
public class ForcedChunksRedirectMap implements Map<World, ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket>> {
	public static final ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> emptyMap = ImmutableSetMultimap.of();

	@Override
	public int size() {
		return 0;
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public boolean containsKey(Object key) {
		return false;
	}

	@Override
	public boolean containsValue(Object value) {
		return false;
	}

	@Override
	public ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> get(Object key) {
		return ((World) key).forcedChunks;
	}

	@Override
	public ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> put(World key, ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> value) {
		key.forcedChunks = value;
		return null;
	}

	@Override
	public ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> remove(Object key) {
		((World) key).forcedChunks = emptyMap;
		return null;
	}

	@Override
	public void putAll(Map<? extends World, ? extends ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket>> m) {
	}

	@Override
	public void clear() {
	}

	@Override
	public Set<World> keySet() {
		return null;
	}

	@Override
	public Collection<ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket>> values() {
		return null;
	}

	@Override
	public Set<Entry<World, ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket>>> entrySet() {
		return null;
	}
}
