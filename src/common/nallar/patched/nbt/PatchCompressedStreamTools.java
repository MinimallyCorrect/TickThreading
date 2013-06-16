package nallar.patched.nbt;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

public abstract class PatchCompressedStreamTools extends CompressedStreamTools {
	public static void a(NBTTagCompound nbtTagCompound, OutputStream outputStream) throws IOException {
		DataOutputStream dataOutputStream = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new BufferedOutputStream(outputStream, 32768)), 32768));

		try {
			write(nbtTagCompound, dataOutputStream);
		} finally {
			dataOutputStream.close();
		}
	}
}
