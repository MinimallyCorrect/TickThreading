package nallar.patched.forge;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
import nallar.patched.annotation.Public;
import nallar.tickthreading.patcher.Declare;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;

import java.io.*;
import java.util.*;
import java.util.logging.*;

@SuppressWarnings("UnusedDeclaration")
@Public
public abstract class PatchForgeChunkManager extends ForgeChunkManager {
	private static final Map<World, ArrayListMultimap<String, Ticket>> worldLoadedTickets = new HashMap<World, ArrayListMultimap<String, Ticket>>();
	private static final Map<World, Map<String, ListMultimap<String, Ticket>>> worldPlayerLoadedTickets = new HashMap<World, Map<String, ListMultimap<String, Ticket>>>();

	public static ImmutableSetMultimap<ChunkCoordIntPair, Ticket> getPersistentChunksFor(World world) {
		return world.forcedChunks;
	}

	static void forceChunkInternal(Ticket ticket, ChunkCoordIntPair chunk) {
		World world = ticket.world;
		synchronized (ForgeChunkManager.class) {
			world.forcedChunks = ImmutableSetMultimap.<ChunkCoordIntPair, Ticket>builder().putAll(world.forcedChunks).put(chunk, ticket).build();
		}
	}

	static void unforceChunkInternal(Ticket ticket, ChunkCoordIntPair chunk) {
		World world = ticket.world;
		synchronized (ForgeChunkManager.class) {
			LinkedHashMultimap<ChunkCoordIntPair, Ticket> copy = LinkedHashMultimap.create(world.forcedChunks);
			copy.remove(chunk, ticket);
			world.forcedChunks = ImmutableSetMultimap.copyOf(copy);
		}
	}

	public static void unforceChunk(Ticket ticket, ChunkCoordIntPair chunk) {
		if (ticket == null || chunk == null) {
			return;
		}
		ticket.requestedChunks.remove(chunk);
		MinecraftForge.EVENT_BUS.post(new UnforceChunkEvent(ticket, chunk));
		unforceChunkInternal(ticket, chunk);
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
		LinkedHashSet<ChunkCoordIntPair> requestedChunks = ticket.requestedChunks;
		requestedChunks.add(chunk);
		MinecraftForge.EVENT_BUS.post(new ForceChunkEvent(ticket, chunk));

		forceChunkInternal(ticket, chunk);
		if (ticket.getChunkListDepth() > 0 && requestedChunks.size() > ticket.getChunkListDepth()) {
			ChunkCoordIntPair removed = requestedChunks.iterator().next();
			unforceChunk(ticket, removed);
		}
		ticket.world.getChunkProvider().loadChunk(chunk.chunkXPos, chunk.chunkZPos);
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
				if (e != null && e.writeToNBTOptional(new NBTTagCompound())) {
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

	static void loadWorld(World world) {
		ArrayListMultimap<String, Ticket> newTickets = ArrayListMultimap.create();
		tickets.put(world, newTickets);

		world.forcedChunks = ImmutableSetMultimap.of();

		if (!(world instanceof WorldServer)) {
			return;
		}

		WorldServer worldServer = (WorldServer) world;
		File chunkDir = worldServer.getChunkSaveLocation();
		File chunkLoaderData = new File(chunkDir, "forcedchunks.dat");

		if (chunkLoaderData.exists() && chunkLoaderData.isFile()) {
			// MCPC+ force chunks later to help guarantee Forge event ordering
			ChunkProviderServer chunkProviderServer = worldServer.theChunkProviderServer;
			ArrayListMultimap<String, Ticket> loadedTickets = ArrayListMultimap.create();
			Map<String, ListMultimap<String, Ticket>> playerLoadedTickets = Maps.newHashMap();
			NBTTagCompound forcedChunkData;
			try {
				forcedChunkData = CompressedStreamTools.read(chunkLoaderData);
			} catch (IOException e) {
				FMLLog.log(Level.WARNING, e, "Unable to read forced chunk data at %s - it will be ignored", chunkLoaderData.getAbsolutePath());
				return;
			}
			NBTTagList ticketList = forcedChunkData.getTagList("TicketList");
			for (int i = 0; i < ticketList.tagCount(); i++) {
				NBTTagCompound ticketHolder = (NBTTagCompound) ticketList.tagAt(i);
				String modId = ticketHolder.getString("Owner");
				boolean isPlayer = "Forge".equals(modId);

				if (!isPlayer && !Loader.isModLoaded(modId)) {
					FMLLog.warning("Found chunkloading data for mod %s which is currently not available or active - it will be removed from the world save", modId);
					continue;
				}

				if (!isPlayer && !callbacks.containsKey(modId)) {
					FMLLog.warning("The mod %s has registered persistent chunkloading data but doesn't seem to want to be called back with it - it will be removed from the world save", modId);
					continue;
				}

				NBTTagList tickets = ticketHolder.getTagList("Tickets");
				for (int j = 0; j < tickets.tagCount(); j++) {
					NBTTagCompound ticket = (NBTTagCompound) tickets.tagAt(j);
					modId = ticket.hasKey("ModId") ? ticket.getString("ModId") : modId;
					Type type = Type.values()[ticket.getByte("Type")];
					byte ticketChunkDepth = ticket.getByte("ChunkListDepth");
					Ticket tick = new Ticket(modId, type, world);
					if (ticket.hasKey("ModData")) {
						tick.modData = ticket.getCompoundTag("ModData");
					}
					if (ticket.hasKey("Player")) {
						tick.player = ticket.getString("Player");
						if (!playerLoadedTickets.containsKey(tick.modId)) {
							playerLoadedTickets.put(modId, ArrayListMultimap.<String, Ticket>create());
						}
						playerLoadedTickets.get(tick.modId).put(tick.player, tick);
					} else {
						loadedTickets.put(modId, tick);
					}
					if (type == Type.ENTITY) {
						tick.entityChunkX = ticket.getInteger("chunkX");
						tick.entityChunkZ = ticket.getInteger("chunkZ");
						UUID uuid = new UUID(ticket.getLong("PersistentIDMSB"), ticket.getLong("PersistentIDLSB"));
						pendingEntities.put(uuid, tick);
						// add the ticket to the "pending entity" list
					}

					// MCPC+ start - save the chunks forced by this ticket (fix for chunkloaders)
					NBTTagList ticketChunks = ticket.getTagList("Chunks");
					for (int k = 0; k < ticketChunks.tagCount(); k++) {
						NBTTagCompound nbtChunk = (NBTTagCompound) ticketChunks.tagAt(k);
						int chunkX = nbtChunk.getInteger("chunkX");
						int chunkZ = nbtChunk.getInteger("chunkZ");
						ChunkCoordIntPair chunkCoordIntPair = new ChunkCoordIntPair(chunkX, chunkZ);
						forceChunkInternal(tick, chunkCoordIntPair);
						chunkProviderServer.cacheChunk(chunkX, chunkZ);
					}
					// MCPC+ end
				}
			}

			// MCPC+ hold on to the callback tickets temporarily to fire after WorldEvent.Load has been called on the plugins
			worldLoadedTickets.put(world, loadedTickets);
			worldPlayerLoadedTickets.put(world, playerLoadedTickets);
		}
	}

	public static void callbacksForWorld(World world) {
		// MCPC+ - do nothing. Postpone longer to allow ThreadedChunkProvider to asynchronously load the forced chunks.
	}

	@Declare
	public static void loadForcedChunks(WorldServer world) {
		synchronized (WorldServer.class) {
			if (world.forcedChunksInited) {
				return;
			}
			world.forcedChunksInited = true;
		}
		ChunkProviderServer chunkProviderServer = world.theChunkProviderServer;
		ArrayListMultimap<String, Ticket> loadedTickets = worldLoadedTickets.remove(world);
		if (loadedTickets != null) {
			for (String modId : loadedTickets.keySet()) {
				LoadingCallback loadingCallback = callbacks.get(modId);
				int maxTicketLength = getMaxTicketLengthFor(modId);
				List<Ticket> tickets = loadedTickets.get(modId);
				if (loadingCallback instanceof OrderedLoadingCallback) {
					OrderedLoadingCallback orderedLoadingCallback = (OrderedLoadingCallback) loadingCallback;
					tickets = orderedLoadingCallback.ticketsLoaded(ImmutableList.copyOf(tickets), world, maxTicketLength);
				}
				if (tickets.size() > maxTicketLength) {
					FMLLog.warning("The mod %s has too many open chunkloading tickets %d. Excess will be dropped", modId, tickets.size());
					tickets.subList(maxTicketLength, tickets.size()).clear();
				}
				ForgeChunkManager.tickets.get(world).putAll(modId, tickets);
				loadingCallback.ticketsLoaded(ImmutableList.copyOf(tickets), world);
			}
		}
		Map<String, ListMultimap<String, Ticket>> playerLoadedTickets = worldPlayerLoadedTickets.remove(world);
		if (playerLoadedTickets != null) {
			for (final Map.Entry<String, ListMultimap<String, Ticket>> stringListMultimapEntry : playerLoadedTickets.entrySet()) {
				LoadingCallback loadingCallback = callbacks.get(stringListMultimapEntry.getKey());
				ListMultimap<String, Ticket> tickets = stringListMultimapEntry.getValue();
				if (loadingCallback instanceof PlayerOrderedLoadingCallback) {
					PlayerOrderedLoadingCallback orderedLoadingCallback = (PlayerOrderedLoadingCallback) loadingCallback;
					tickets = orderedLoadingCallback.playerTicketsLoaded(ImmutableListMultimap.copyOf(tickets), world);
					playerTickets.putAll(tickets);
				}
				ForgeChunkManager.tickets.get(world).putAll("Forge", tickets.values());
				loadingCallback.ticketsLoaded(ImmutableList.copyOf(tickets.values()), world);
			}
		}
	}

	static void unloadWorld(World world) {
		// World save fires before this event so the chunk loading info will be done
		if (!(world instanceof WorldServer)) {
			return;
		}

		worldLoadedTickets.remove(world);
		worldPlayerLoadedTickets.remove(world);
		dormantChunkCache.remove(world);
		// integrated server is shutting down
		if (!MinecraftServer.getServer().isServerRunning()) {
			playerTickets.clear();
			tickets.clear();
		}
	}
}
