package nallar.tickthreading.minecraft.profiling;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Functions;
import com.google.common.collect.Ordering;

import nallar.tickthreading.minecraft.commands.Command;
import nallar.tickthreading.util.TableFormatter;
import net.minecraft.command.ICommandSender;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet250CustomPayload;
import org.cliffc.high_scale_lib.NonBlockingHashMap;

public class PacketProfiler {
	private static boolean profiling = false;
	private static final Map<String, AtomicInteger> size = new NonBlockingHashMap<String, AtomicInteger>();
	private static final Map<String, AtomicInteger> count = new NonBlockingHashMap<String, AtomicInteger>();

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
		Map<String, Integer> count = new HashMap<String, Integer>();
		for (Map.Entry<String, AtomicInteger> entry : PacketProfiler.count.entrySet()) {
			count.put(entry.getKey(), entry.getValue().get());
		}
		Map<String, Integer> size = new HashMap<String, Integer>();
		for (Map.Entry<String, AtomicInteger> entry : PacketProfiler.size.entrySet()) {
			size.put(entry.getKey(), entry.getValue().get());
		}

		tf
				.heading("Packet")
				.heading("Count")
				.heading("Size");
		final List<String> sortedIdsByCount = sortedKeys(count, elements);
		for (String id : sortedIdsByCount) {
			tf
					.row(getName(id))
					.row(count.get(id))
					.row(humanReadableByteCount(size.get(id)));
		}
		tf.finishTable();
		tf.sb.append('\n');
		tf
				.heading("Packet")
				.heading("Count")
				.heading("Size");
		final List<String> sortedIdsBySize = sortedKeys(size, elements);
		for (String id : sortedIdsBySize) {
			tf
					.row(getName(id))
					.row(count.get(id))
					.row(humanReadableByteCount(size.get(id)));
		}
		tf.finishTable();
		return tf;
	}

	private static String getName(String name) {
		int id;
		try {
			id = Integer.parseInt(name);
		} catch (NumberFormatException ignored) {
			return name;
		}
		return ((Class) Packet.packetIdToClassMap.lookup(id)).getName().replace("net.minecraft.network.packet.Packet", "");
	}

	public static void record(final Packet packet) {
		if (!profiling) {
			return;
		}
		String id;
		if (packet instanceof Packet250CustomPayload) {
			id = ((Packet250CustomPayload) packet).channel;
		} else {
			id = String.valueOf(packet.getPacketId());
		}
		getCount(id).getAndIncrement();
		getSize(id).addAndGet(packet.getPacketSize());
	}

	private static AtomicInteger getCount(String id) {
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

	private static AtomicInteger getSize(String id) {
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

	// http://stackoverflow.com/a/3758880/250076
	public static String humanReadableByteCount(int bytes) {
		int unit = 1024;
		if (bytes < unit) {
			return bytes + " B";
		}
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		char pre = ("KMGTPE").charAt(exp - 1);
		return String.format("%.1f%cB", bytes / Math.pow(unit, exp), pre);
	}
}
