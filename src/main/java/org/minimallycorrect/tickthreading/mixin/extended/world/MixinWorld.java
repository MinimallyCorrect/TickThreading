package org.minimallycorrect.tickthreading.mixin.extended.world;

import java.util.*;
import java.util.concurrent.*;

import lombok.val;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;

import org.minimallycorrect.mixin.Add;
import org.minimallycorrect.mixin.Mixin;

import net.minecraft.world.World;

@Mixin
public abstract class MixinWorld extends World {
	@Add
	private final ArrayDeque<Runnable> tasks_ = new ArrayDeque<>();
	@Add
	private Thread worldThread_;

	@Add
	public <V> ListenableFuture<V> callFromMainThread(Callable<V> callable) {
		if (!unloaded && Thread.currentThread() == worldThread) {
			val task = ListenableFutureTask.create(callable);
			tasks.add(task);
			return task;
		}
		try {
			return Futures.immediateFuture(callable.call());
		} catch (Exception exception) {
			return Futures.immediateFailedCheckedFuture(exception);
		}
	}

	@Add
	public ListenableFuture<Object> addScheduledTask(Runnable runnableToSchedule) {
		return this.<Object> callFromMainThread(Executors.callable(runnableToSchedule));
	}

	@Add
	public void runTasks() {
		val tasks = this.tasks;
		Runnable task;
		while ((task = tasks.poll()) != null)
			task.run();
	}
}
