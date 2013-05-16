package me.nallar.patched.forge;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.logging.Level;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

import cpw.mods.fml.common.FMLLog;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.collections.ForcedChunksRedirectMap;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;

public abstract class PatchForgeChunkManager extends ForgeChunkManager {
	private static Field requestedChunksField;

	public static void staticConstruct() throws NoSuchFieldException {
		try {
			forcedChunks = new ForcedChunksRedirectMap();
		} catch (NoSuchFieldError ignored) {
			// MCPC+
		}
		requestedChunksField = Ticket.class.getDeclaredField("requestedChunks");
		requestedChunksField.setAccessible(true);
	}

	public static ImmutableSetMultimap<ChunkCoordIntPair, Ticket> getPersistentChunksFor(World world) {
		return world.forcedChunks;
	}

	public static void unforceChunk(Ticket ticket, ChunkCoordIntPair chunk) {
		if (ticket == null || chunk == null) {
			return;
		}
		LinkedHashSet<ChunkCoordIntPair> requestedChunks;
		try {
			requestedChunks = (LinkedHashSet<ChunkCoordIntPair>) requestedChunksField.get(ticket);
		} catch (IllegalAccessException e) {
			Log.severe("Failed to get requestedChunks", e);
			return;
		}
		requestedChunks.remove(chunk);
		MinecraftForge.EVENT_BUS.post(new UnforceChunkEvent(ticket, chunk));

		synchronized (ForgeChunkManager.class) {
			LinkedHashMultimap<ChunkCoordIntPair, Ticket> copy = LinkedHashMultimap.create(ticket.world.forcedChunks);
			copy.remove(chunk, ticket);
			ticket.world.forcedChunks = ImmutableSetMultimap.copyOf(copy);
		}
	}

	public static void forceChunk(Ticket ticket, ChunkCoordIntPair chunk) {
		if (ticket == null || chunk == null) {
			return;
		}
		if (ticket.getType() == Type.ENTITY && ticket.getEntity() == null) {
			throw new RuntimeException("Attempted to use an entity ticket to force a chunk, without an entity");
		}
		if (ticket.isPlayerTicket() ? !playerTickets.containsValue(ticket) : !tickets.get(ticket.world).containsEntry(ticket.getModId(), ticket)) {
			FMLLog.severe("The mod %s attempted to force load a chunk with an invalid ticket. This is not permitted.", ticket.getModId());
			return;
		}
		LinkedHashSet<ChunkCoordIntPair> requestedChunks;
		try {
			requestedChunks = (LinkedHashSet<ChunkCoordIntPair>) requestedChunksField.get(ticket);
		} catch (IllegalAccessException e) {
			Log.severe("Failed to get requestedChunks", e);
			return;
		}
		requestedChunks.add(chunk);
		MinecraftForge.EVENT_BUS.post(new ForceChunkEvent(ticket, chunk));

		synchronized (ForgeChunkManager.class) {
			ticket.world.forcedChunks = ImmutableSetMultimap.<ChunkCoordIntPair, Ticket>builder().putAll(ticket.world.forcedChunks).put(chunk, ticket).build();
		}
		if (ticket.getChunkListDepth() > 0 && requestedChunks.size() > ticket.getChunkListDepth()) {
			ChunkCoordIntPair removed = requestedChunks.iterator().next();
			unforceChunk(ticket, removed);
		}
	}

	static void saveWorld(World world) {
		// only persist persistent worlds
		if (!(world instanceof WorldServer)) {
			return;
		}
		WorldServer worldServer = (WorldServer) world;
		File chunkDir = worldServer.getChunkSaveLocation();
		File chunkLoaderData = new File(chunkDir, "forcedchunks.dat");

		NBTTagCompound forcedChunkData = new NBTTagCompound();
		NBTTagList ticketList = new NBTTagList();
		forcedChunkData.setTag("TicketList", ticketList);

		Multimap<String, Ticket> ticketSet = tickets.get(worldServer);
		for (String modId : ticketSet.keySet()) {
			NBTTagCompound ticketHolder = new NBTTagCompound();
			ticketList.appendTag(ticketHolder);

			ticketHolder.setString("Owner", modId);
			NBTTagList tickets = new NBTTagList();
			ticketHolder.setTag("Tickets", tickets);

			for (Ticket tick : ticketSet.get(modId)) {
				if (tick == null) {
					continue;
				}
				NBTTagCompound ticket = new NBTTagCompound();
				ticket.setByte("Type", (byte) tick.getType().ordinal());
				ticket.setByte("ChunkListDepth", (byte) tick.getChunkListDepth());
				if (tick.isPlayerTicket()) {
					ticket.setString("ModId", tick.getModId());
					ticket.setString("Player", tick.getPlayerName());
				}
				if (tick.getModData() != null) {
					ticket.setCompoundTag("ModData", tick.getModData());
				}
				Entity e = tick.getType() == Type.ENTITY ? tick.getEntity() : null;
				if (e != null && e.addEntityID(new NBTTagCompound())) {
					ticket.setInteger("chunkX", MathHelper.floor_double(e.chunkCoordX));
					ticket.setInteger("chunkZ", MathHelper.floor_double(e.chunkCoordZ));
					ticket.setLong("PersistentIDMSB", e.getPersistentID().getMostSignificantBits());
					ticket.setLong("PersistentIDLSB", e.getPersistentID().getLeastSignificantBits());
					tickets.appendTag(ticket);
				} else if (tick.getType() != Type.ENTITY) {
					tickets.appendTag(ticket);
				}
			}
		}
		try {
			CompressedStreamTools.write(forcedChunkData, chunkLoaderData);
		} catch (IOException e) {
			FMLLog.log(Level.WARNING, e, "Unable to write forced chunk data to %s - chunkloading won't work", chunkLoaderData.getAbsolutePath());
		}
	}
}
