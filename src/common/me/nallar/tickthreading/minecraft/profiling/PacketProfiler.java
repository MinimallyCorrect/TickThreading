package me.nallar.tickthreading.minecraft.profiling;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.base.Functions;
import com.google.common.collect.Ordering;

import me.nallar.tickthreading.minecraft.commands.Command;
import me.nallar.tickthreading.util.MappingUtil;
import me.nallar.tickthreading.util.TableFormatter;
import net.minecraft.command.ICommandSender;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet250CustomPayload;
import org.cliffc.high_scale_lib.NonBlockingHashMap;

public class PacketProfiler {
	private static boolean profiling = false;
	private static final Map<Integer, AtomicInteger> size = new NonBlockingHashMap<Integer, AtomicInteger>();
	private static final Map<Integer, AtomicInteger> count = new NonBlockingHashMap<Integer, AtomicInteger>();

	public static synchronized boolean startProfiling(final ICommandSender commandSender, final int time) {
		if (profiling) {
			Command.sendChat(commandSender, "Someone else is already profiling packets.");
			return false;
		}
		profiling = true;
		Command.sendChat(commandSender, "Profiling packets for " + time + " seconds.");
		new Thread() {
			@Override
			public void run() {
				try {
					Thread.sleep(time * 1000);
				} catch (InterruptedException ignored) {
				}
				Command.sendChat(commandSender, writeStats(new TableFormatter(commandSender)).toString());
				synchronized (PacketProfiler.class) {
					size.clear();
					count.clear();
					profiling = false;
				}
			}
		}.start();
		return true;
	}

	private static <T> List<T> sortedKeys(Map<T, ? extends Comparable<?>> map, int elements) {
		List<T> list = Ordering.natural().reverse().onResultOf(Functions.forMap(map)).immutableSortedCopy(map.keySet());
		return list.size() > elements ? list.subList(0, elements) : list;
	}

	private static TableFormatter writeStats(final TableFormatter tf) {
		return writeStats(tf, 8);
	}

	private static TableFormatter writeStats(final TableFormatter tf, int elements) {
		Map<Integer, Integer> count = new HashMap<Integer, Integer>();
		for (Map.Entry<Integer, AtomicInteger> entry : PacketProfiler.count.entrySet()) {
			count.put(entry.getKey(), entry.getValue().get());
		}
		Map<Integer, Integer> size = new HashMap<Integer, Integer>();
		for (Map.Entry<Integer, AtomicInteger> entry : PacketProfiler.size.entrySet()) {
			size.put(entry.getKey(), entry.getValue().get());
		}

		tf
				.heading("Packet")
				.heading("Count")
				.heading("Size");
		final List<Integer> sortedIdsByCount = sortedKeys(count, elements);
		for (Integer id : sortedIdsByCount) {
			tf
					.row(getName(id))
					.row(count.get(id))
					.row(size.get(id));
		}
		tf.finishTable();
		tf.sb.append('\n');
		tf
				.heading("Packet")
				.heading("Count")
				.heading("Size");
		final List<Integer> sortedIdsBySize = sortedKeys(size, elements);
		for (Integer id : sortedIdsBySize) {
			tf
					.row(getName(id))
					.row(count.get(id))
					.row(size.get(id));
		}
		tf.finishTable();
		return tf;
	}

	private static String getName(int id) {
		return MappingUtil.debobfuscate(((Class) Packet.packetIdToClassMap.lookup(id)).getName()).replace("net.minecraft.network.packet.Packet", "");
	}

	public static void record(final Packet packet) {
		if (!profiling) {
			return;
		}
		Integer id;
		if (false && packet instanceof Packet250CustomPayload) {
			// TODO: Separate packet250s into their own IDs.
		} else {
			id = packet.getPacketId();
		}
		getCount(id).getAndIncrement();
		getSize(id).addAndGet(packet.getPacketSize());
	}

	private static AtomicInteger getCount(Integer id) {
		AtomicInteger t = count.get(id);
		if (t == null) {
			synchronized (count) {
				t = count.get(id);
				if (t == null) {
					t = new AtomicInteger();
					count.put(id, t);
				}
			}
		}
		return t;
	}

	private static AtomicInteger getSize(Integer id) {
		AtomicInteger t = size.get(id);
		if (t == null) {
			synchronized (size) {
				t = size.get(id);
				if (t == null) {
					t = new AtomicInteger();
					size.put(id, t);
				}
			}
		}
		return t;
	}
}
