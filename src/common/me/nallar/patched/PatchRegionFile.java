package me.nallar.patched;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.world.chunk.storage.RegionFile;
import net.minecraft.world.chunk.storage.RegionFileChunkBuffer;

public abstract class PatchRegionFile {
	private static final byte[] emptySector = new byte[4096];
	private final RandomAccessFile dataFile;
	private final int[] offsets = new int[1024];
	private final ArrayList<Boolean> sectorFree = new ArrayList<Boolean>(0);

	public PatchRegionFile(File fileName) {
		RandomAccessFile dataFile = null;
		boolean failed = false;
		try {
			dataFile = new RandomAccessFile(fileName, "rw");
		} catch (IOException e) {
			FMLLog.log(Level.SEVERE, e, "Failed to read region file for " + fileName);
			failed = true;
		}
		this.dataFile = dataFile;
		if (failed) {
			return;
		}
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
			sectorFree.ensureCapacity(length);
			int i;

			for (i = 0; i < length; ++i) {
				this.sectorFree.add(true);
			}

			this.sectorFree.set(0, false);
			this.sectorFree.set(1, false);
			dataFile.seek(0L);
			int var4;

			for (i = 0; i < 1024; ++i) {
				var4 = dataFile.readInt();
				this.offsets[i] = var4;

				int offsetSize = (var4 >> 8);
				int maxSize = offsetSize + (var4 & 255);
				if (var4 != 0 && offsetSize >= 0 && maxSize >= 0 && maxSize <= this.sectorFree.size()) {
					for (int var5 = 0; var5 < (var4 & 255); ++var5) {
						this.sectorFree.set(offsetSize + var5, false);
					}
				}
			}

			dataFile.skipBytes(4096);
		} catch (IOException e) {
			FMLLog.log(Level.SEVERE, e, "Failed to read region file for " + fileName);
		}
	}

	/**
	 * args: x, y - get uncompressed chunk stream from the region file
	 */
	public synchronized DataInputStream getChunkDataInputStream(int par1, int par2) {
		if (this.outOfBounds(par1, par2)) {
			return null;
		} else {
			try {
				int var3 = this.getOffset(par1, par2);

				if (var3 == 0) {
					return null;
				} else {
					int var4 = var3 >> 8;
					int var5 = var3 & 255;

					if (var4 + var5 > this.sectorFree.size()) {
						return null;
					} else {
						this.dataFile.seek((long) (var4 * 4096));
						int var6 = this.dataFile.readInt();

						if (var6 > 4096 * var5) {
							return null;
						} else if (var6 <= 0) {
							return null;
						} else {
							byte var7 = this.dataFile.readByte();
							byte[] var8;

							if (var7 == 1) {
								var8 = new byte[var6 - 1];
								this.dataFile.read(var8);
								return new DataInputStream(new BufferedInputStream(new GZIPInputStream(new ByteArrayInputStream(var8))));
							} else if (var7 == 2) {
								var8 = new byte[var6 - 1];
								this.dataFile.read(var8);
								return new DataInputStream(new BufferedInputStream(new InflaterInputStream(new ByteArrayInputStream(var8))));
							} else {
								return null;
							}
						}
					}
				}
			} catch (IOException var9) {
				return null;
			}
		}
	}

	/**
	 * args: x, z - get an output stream used to write chunk data, data is on disk when the returned stream is closed
	 */
	public DataOutputStream getChunkDataOutputStream(int par1, int par2) {
		return this.outOfBounds(par1, par2) ? null : new DataOutputStream(new DeflaterOutputStream(new RegionFileChunkBuffer((RegionFile) (Object) this, par1, par2)));
	}

	/**
	 * args: x, z, data, length - write chunk data at (x, z) to disk
	 */
	protected synchronized void write(int par1, int par2, byte[] par3ArrayOfByte, int par4) {
		try {
			int var5 = this.getOffset(par1, par2);
			int var6 = var5 >> 8;
			int var7 = var5 & 255;
			int var8 = (par4 + 5) / 4096 + 1;

			if (var8 >= 256) {
				return;
			}

			if (var6 != 0 && var7 == var8) {
				this.write(var6, par3ArrayOfByte, par4);
			} else {
				int var9;

				for (var9 = 0; var9 < var7; ++var9) {
					this.sectorFree.set(var6 + var9, true);
				}

				var9 = this.sectorFree.indexOf(true);
				int var10 = 0;
				int var11;

				if (var9 != -1) {
					for (var11 = var9; var11 < this.sectorFree.size(); ++var11) {
						if (var10 != 0) {
							if (this.sectorFree.get(var11)) {
								++var10;
							} else {
								var10 = 0;
							}
						} else if (this.sectorFree.get(var11)) {
							var9 = var11;
							var10 = 1;
						}

						if (var10 >= var8) {
							break;
						}
					}
				}

				if (var10 >= var8) {
					var6 = var9;
					this.setOffset(par1, par2, var9 << 8 | var8);

					for (var11 = 0; var11 < var8; ++var11) {
						this.sectorFree.set(var6 + var11, false);
					}

					this.write(var6, par3ArrayOfByte, par4);
				} else {
					this.dataFile.seek(this.dataFile.length());
					var6 = this.sectorFree.size();

					for (var11 = 0; var11 < var8; ++var11) {
						this.dataFile.write(emptySector);
						this.sectorFree.add(false);
					}

					this.write(var6, par3ArrayOfByte, par4);
					this.setOffset(par1, par2, var6 << 8 | var8);
				}
			}

			this.setChunkTimestamp(par1, par2, (int) (System.currentTimeMillis() / 1000L));
		} catch (IOException e) {
			FMLLog.log(Level.SEVERE, e, "Error saving region file");
		}
	}

	/**
	 * args: sectorNumber, data, length - write the chunk data to this RegionFile
	 */
	private void write(int par1, byte[] par2ArrayOfByte, int par3) throws IOException {
		this.dataFile.seek((long) (par1 * 4096));
		this.dataFile.writeInt(par3 + 1);
		this.dataFile.writeByte(2);
		this.dataFile.write(par2ArrayOfByte, 0, par3);
	}

	/**
	 * args: x, z - check region bounds
	 */
	private boolean outOfBounds(int par1, int par2) {
		return par1 < 0 || par1 >= 32 || par2 < 0 || par2 >= 32;
	}

	/**
	 * args: x, y - get chunk's offset in region file
	 */
	private int getOffset(int par1, int par2) {
		return this.offsets[par1 + par2 * 32];
	}

	/**
	 * args: x, z, - true if chunk has been saved / converted
	 */
	public boolean isChunkSaved(int par1, int par2) {
		return this.getOffset(par1, par2) != 0;
	}

	/**
	 * args: x, z, offset - sets the chunk's offset in the region file
	 */
	private void setOffset(int par1, int par2, int par3) throws IOException {
		this.offsets[par1 + par2 * 32] = par3;
		this.dataFile.seek((long) ((par1 + par2 * 32) * 4));
		this.dataFile.writeInt(par3);
	}

	/**
	 * args: x, z, timestamp - sets the chunk's write timestamp
	 */
	private void setChunkTimestamp(int x, int z, int ts) throws IOException {
		int i = x + z * 32;
		if (i >= 1024) {
			throw new ArrayIndexOutOfBoundsException();
		}
		this.dataFile.seek((long) (4096 + (i) * 4));
		this.dataFile.writeInt(ts);
	}

	/**
	 * close this RegionFile and prevent further writes
	 */
	public void close() throws IOException {
		if (this.dataFile != null) {
			this.dataFile.close();
		}
	}
}
