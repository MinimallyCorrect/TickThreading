package nallar.patched.network;

import net.minecraft.network.TcpConnection;
import net.minecraft.network.TcpReaderThread;

public abstract class PatchTcpReaderThread extends TcpReaderThread {
	PatchTcpReaderThread(final TcpConnection par1TcpConnection, final String par2Str) {
		super(par1TcpConnection, par2Str);
	}
}
