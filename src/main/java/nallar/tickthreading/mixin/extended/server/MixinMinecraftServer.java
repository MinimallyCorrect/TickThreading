package nallar.tickthreading.mixin.extended.server;

import me.nallar.mixin.Mixin;
import net.minecraft.server.MinecraftServer;

@Mixin
public abstract class MixinMinecraftServer extends MinecraftServer {
	public MixinMinecraftServer() {
		super(null, null);
	}


	/* TODO: per-world tasks
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
	*/
}
