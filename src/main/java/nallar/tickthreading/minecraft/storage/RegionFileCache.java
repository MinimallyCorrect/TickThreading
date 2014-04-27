package nallar.tickthreading.minecraft.storage;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.world.chunk.storage.RegionFile;

import java.io.*;
import java.util.*;
import java.util.logging.*;

public class RegionFileCache {
	private final Map<Long, RegionFile> regionFileMap = new HashMap<Long, RegionFile>();
	private final File regionDir;
	private boolean closed = false;
	private volatile boolean closing = false;

	public RegionFileCache(File worldDir) {
		regionDir = new File(worldDir, "region").getAbsoluteFile();
		if (!regionDir.isDirectory() && !regionDir.mkdirs()) {
			throw new Error("Failed to create region directory: " + regionDir);
		}
	}

	private static long hash(int x, int z) {
		return (((long) x) << 32) | (z & 0xffffffffL);
	}

	public RegionFile get(int x, int z) {
		if (closed) {
			throw new IllegalStateException("RegionFileCache already closed");
		}

		long hash = hash(x, z);
		RegionFile regionFile = regionFileMap.get(hash);
		if (!closing && regionFile != null) {
			return regionFile;
		}
		synchronized (this) {
			regionFile = regionFileMap.get(hash);
			if (regionFile != null) {
				return regionFile;
			}
			if (regionFileMap.size() >= 256) {
				closeRegionFiles();
			}
			String location = "r." + x + '.' + z + ".mca";
			File regionFileFile = new File(regionDir, location);
			regionFile = RegionFileFixer.newRegionFile(regionFileFile);
			if (regionFileMap.put(hash, regionFile) != null) {
				throw new Error("Region file recreated concurrently. This should never happen.");
			}
			return regionFile;
		}
	}

	public synchronized void close() {
		closed = true;
		closeRegionFiles();
	}

	private synchronized void closeRegionFiles() {
		closing = true;
		for (RegionFile regionFile : regionFileMap.values()) {
			try {
				regionFile.close();
			} catch (IOException e) {
				FMLLog.log(Level.WARNING, e, "Failed to close regionFile");
			}
		}
		regionFileMap.clear();
		closing = false;
	}

	public DataOutputStream getChunkOutputStream(int x, int z) {
		return new DataOutputStream(new BufferedOutputStream(get(x >> 5, z >> 5).getChunkDataOutputStream(x & 31, z & 31)));
	}

	public DataInputStream getChunkInputStream(int x, int z) {
		return get(x >> 5, z >> 5).getChunkDataInputStream(x & 31, z & 31);
	}
}
