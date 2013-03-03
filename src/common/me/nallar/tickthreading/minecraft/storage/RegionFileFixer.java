package me.nallar.tickthreading.minecraft.storage;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.logging.Level;

import com.google.common.io.Files;

import cpw.mods.fml.common.FMLLog;
import me.nallar.unsafe.UnsafeUtil;
import net.minecraft.world.chunk.storage.RegionFile;

public class RegionFileFixer {
	public static RegionFile newRegionFile(File regionFileFile) {
		try {
			return new RegionFile(regionFileFile);
		} catch (ArrayIndexOutOfBoundsException e) {
			return fixNegativeOffset(regionFileFile);
		} catch (Throwable t) {
			FMLLog.log(Level.SEVERE, t, "Error opening region file: " + regionFileFile);
			throw UnsafeUtil.throwIgnoreChecked(t);
		}
	}

	private static RegionFile fixNegativeOffset(File regionFileFile) {
		FMLLog.log(Level.WARNING, "Region file " + regionFileFile + " is corrupted: negative offset. Attempting to fix.");
		try {
			Files.copy(regionFileFile, new File(regionFileFile.getParentFile(), regionFileFile.getName() + ".bak"));
		} catch (IOException e) {
			FMLLog.log(Level.SEVERE, e, "Failed to back up corrupt region file.");
		}
		try {
			RandomAccessFile dataFile = new RandomAccessFile(regionFileFile, "rw");
			try {
				int length;

				if (dataFile.length() < 4096L) {
					for (length = 0; length < 1024; ++length) {
						dataFile.writeInt(0);
					}

					for (length = 0; length < 1024; ++length) {
						dataFile.writeInt(0);
					}
				}

				if ((dataFile.length() & 4095L) != 0L) {
					for (length = 0; (long) length < (dataFile.length() & 4095L); ++length) {
						dataFile.write(0);
					}
				}

				length = (int) dataFile.length() / 4096;

				dataFile.seek(0L);

				for (int i = 0; i < 1024; ++i) {
					int offset = dataFile.readInt();

					if (offset != 0 && (offset >> 8) + (offset & 255) <= length) {
						for (int var5 = 0; var5 < (offset & 255); ++var5) {
							if ((offset >> 8) + var5 < 0) {
								dataFile.seek(dataFile.getFilePointer() - 4);
								dataFile.writeInt(0);
								break;
							}
						}
					}
				}
			} finally {
				dataFile.close();
			}
		} catch (Throwable t) {
			FMLLog.log(Level.SEVERE, t, "Failed to fix negative offset index in " + regionFileFile);
			throw UnsafeUtil.throwIgnoreChecked(t);
		}
		return new RegionFile(regionFileFile);
	}
}
