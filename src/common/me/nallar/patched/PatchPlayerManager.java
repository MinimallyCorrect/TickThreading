package me.nallar.patched;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Queue;

import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.server.management.PlayerInstance;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.world.WorldServer;

public abstract class PatchPlayerManager extends PlayerManager {
	public Object chunkWatcherLock;
	private net.minecraft.util.LongHashMap loadingPlayerInstances;
	protected List chunkWatcherWithPlayersF;

	public void construct() {
		chunkWatcherLock = new Object();
		loadingPlayerInstances = new net.minecraft.util.LongHashMap();
		try {
			chunkWatcherWithPlayersF = chunkWatcherWithPlayers;
		} catch (NoSuchFieldError ignored) {
			for (Field f : this.getClass().getDeclaredFields()) {
				if (f.getType().equals(Queue.class)) {
					final Queue q;
					try {
						q = (Queue) f.get(this);
					} catch (IllegalAccessException e) {
						throw new RuntimeException(e);
					}
					chunkWatcherWithPlayersF = new QueueList(q);
					break;
				}
			}
		}
	}

	public PatchPlayerManager(WorldServer par1WorldServer, int par2) {
		super(par1WorldServer, par2);
	}

	@Override
	@Declare
	public net.minecraft.util.LongHashMap getChunkWatchers() {
		return this.playerInstances;
	}

	@Override
	@Declare
	public List getChunkWatcherWithPlayers() {
		return this.chunkWatcherWithPlayersF;
	}

	@Override
	public PlayerInstance getOrCreateChunkWatcher(int par1, int par2, boolean par3) {
		long var4 = (long) par1 + 2147483647L | (long) par2 + 2147483647L << 32;
		PlayerInstance var6 = (PlayerInstance) this.playerInstances.getValueByKey(var4);

		if (var6 == null && (par3 || loadingPlayerInstances.containsItem(var4))) {
			synchronized (chunkWatcherLock) {
				var6 = (PlayerInstance) this.playerInstances.getValueByKey(var4);
				if (var6 == null) {
					var6 = (PlayerInstance) loadingPlayerInstances.getValueByKey(var4);
				} else {
					return var6;
				}
				if (var6 == null) {
					var6 = new PlayerInstance(this, par1, par2);
					this.loadingPlayerInstances.add(var4, var6);
				}
			}
			getWorldServer().theChunkProviderServer.loadChunk(par1, par2);
			synchronized (chunkWatcherLock) {
				if (this.loadingPlayerInstances.remove(var4) != null) {
					this.playerInstances.add(var4, var6);
				}
			}
		}

		return var6;
	}

	public static class QueueList implements List {
		final Queue q;

		public QueueList(Queue q) {
			this.q = q;
		}

		@Override
		public int size() {
			return q.size();
		}

		@Override
		public boolean isEmpty() {
			return q.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			return false;
		}

		@Override
		public Iterator iterator() {
			return null;
		}

		@Override
		public Object[] toArray() {
			return q.toArray();
		}

		@Override
		public boolean add(Object o) {
			return q.add(o);
		}

		@Override
		public boolean remove(Object o) {
			return q.remove(o);
		}

		@Override
		public boolean containsAll(Collection c) {
			return false;
		}

		@Override
		public boolean addAll(Collection c) {
			return false;
		}

		@Override
		public boolean addAll(int index, Collection c) {
			return false;
		}

		@Override
		public boolean removeAll(Collection c) {
			return false;
		}

		@Override
		public boolean retainAll(Collection c) {
			return false;
		}

		@Override
		public void clear() {
		}

		@Override
		public Object get(int index) {
			return null;
		}

		@Override
		public Object set(int index, Object element) {
			return null;
		}

		@Override
		public void add(int index, Object element) {
		}

		@Override
		public Object remove(int index) {
			return null;
		}

		@Override
		public int indexOf(Object o) {
			return 0;
		}

		@Override
		public int lastIndexOf(Object o) {
			return 0;
		}

		@Override
		public ListIterator listIterator() {
			return null;
		}

		@Override
		public ListIterator listIterator(int index) {
			return null;
		}

		@Override
		public List subList(int fromIndex, int toIndex) {
			return null;
		}

		@Override
		public Object[] toArray(Object[] a) {
			return q.toArray(a);
		}
	}
}
