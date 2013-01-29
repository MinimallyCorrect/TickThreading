package me.nallar.tickthreading.minecraft.patched;

import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.network.packet.Packet51MapChunk;
import net.minecraft.network.packet.Packet51MapChunkData;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

public abstract class PatchPacket51MapChunk extends Packet51MapChunk {
	static ThreadLocal<byte[]> temporaryData;

	public static void staticConstruct() {
		temporaryData = new ThreadLocalByteArray();
	}

	@Override
	@Declare
	public void setData(byte[] data) {
		this.chunkData = data;
		this.tempLength = data.length;
	}

	public static Packet51MapChunkData a(Chunk par0Chunk, boolean par1, int par2) {
		int var3 = 0;
		ExtendedBlockStorage[] var4 = par0Chunk.getBlockStorageArray();
		int var5 = 0;
		Packet51MapChunkData var6 = new Packet51MapChunkData();
		byte[] var7 = temporaryData.get();

		if (par1) {
			par0Chunk.deferRender = true;
		}

		int var8;

		for (var8 = 0; var8 < var4.length; ++var8) {
			if (var4[var8] != null && (!par1 || !var4[var8].isEmpty()) && (par2 & 1 << var8) != 0) {
				var6.chunkExistFlag |= 1 << var8;

				if (var4[var8].getBlockMSBArray() != null) {
					var6.chunkHasAddSectionFlag |= 1 << var8;
					++var5;
				}
			}
		}

		for (var8 = 0; var8 < var4.length; ++var8) {
			if (var4[var8] != null && (!par1 || !var4[var8].isEmpty()) && (par2 & 1 << var8) != 0) {
				byte[] var9 = var4[var8].getBlockLSBArray();
				System.arraycopy(var9, 0, var7, var3, var9.length);
				var3 += var9.length;
			}
		}

		NibbleArray var10;

		for (var8 = 0; var8 < var4.length; ++var8) {
			if (var4[var8] != null && (!par1 || !var4[var8].isEmpty()) && (par2 & 1 << var8) != 0) {
				var10 = var4[var8].getMetadataArray();
				System.arraycopy(var10.data, 0, var7, var3, var10.data.length);
				var3 += var10.data.length;
			}
		}

		for (var8 = 0; var8 < var4.length; ++var8) {
			if (var4[var8] != null && (!par1 || !var4[var8].isEmpty()) && (par2 & 1 << var8) != 0) {
				var10 = var4[var8].getBlocklightArray();
				System.arraycopy(var10.data, 0, var7, var3, var10.data.length);
				var3 += var10.data.length;
			}
		}

		if (!par0Chunk.worldObj.provider.hasNoSky) {
			for (var8 = 0; var8 < var4.length; ++var8) {
				if (var4[var8] != null && (!par1 || !var4[var8].isEmpty()) && (par2 & 1 << var8) != 0) {
					var10 = var4[var8].getSkylightArray();
					System.arraycopy(var10.data, 0, var7, var3, var10.data.length);
					var3 += var10.data.length;
				}
			}
		}

		if (var5 > 0) {
			for (var8 = 0; var8 < var4.length; ++var8) {
				if (var4[var8] != null && (!par1 || !var4[var8].isEmpty()) && var4[var8].getBlockMSBArray() != null && (par2 & 1 << var8) != 0) {
					var10 = var4[var8].getBlockMSBArray();
					System.arraycopy(var10.data, 0, var7, var3, var10.data.length);
					var3 += var10.data.length;
				}
			}
		}

		if (par1) {
			byte[] var11 = par0Chunk.getBiomeArray();
			System.arraycopy(var11, 0, var7, var3, var11.length);
			var3 += var11.length;
		}

		var6.compressedData = new byte[var3];
		System.arraycopy(var7, 0, var6.compressedData, 0, var3);
		return var6;
	}

	public static class ThreadLocalByteArray extends ThreadLocal<byte[]> {
		@Override
		public byte[] initialValue() {
			return new byte[196864];
		}
	}
}
