package nallar.tickthreading.mixin.extended.server;

import com.google.common.util.concurrent.ListenableFuture;
import me.nallar.mixin.Mixin;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.*;

@Mixin
public abstract class MixinMinecraftServer extends MinecraftServer {
	@SuppressWarnings("ConstantConditions")
	public MixinMinecraftServer() {
		super(null, null, null, null, null, null, null);
	}

	@Override
	public <V> ListenableFuture<V> callFromMainThread(Callable<V> callable) {
		// TODO: Replace this with error, move all scheduled tasks to be per world?
		throw new UnsupportedOperationException();
	}

	@Override
	public ListenableFuture<Object> addScheduledTask(Runnable runnableToSchedule) {
		// TODO: Replace this with error, move all scheduled tasks to be per world?
		throw new UnsupportedOperationException();
	}
}
