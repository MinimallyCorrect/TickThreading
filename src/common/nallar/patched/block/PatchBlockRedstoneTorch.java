package nallar.patched.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRedstoneTorch;
import net.minecraft.network.packet.Packet61DoorChange;
import net.minecraft.server.management.PlayerInstance;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

public abstract class PatchBlockRedstoneTorch extends BlockRedstoneTorch {
	protected PatchBlockRedstoneTorch(int par1, int par2, boolean par3) {
		super(par1, par2, par3);
	}

	private static long hash(int x, int y, int z) {
		return (x & 0xFFFFFFL) << 28 | z & 0xFFFFFFL | (long) y << 56;
	}

	private static boolean checkForBurnout(World world, long hash, boolean increment) {
		Integer startTicks = world.redstoneBurnoutMap.get(hash);
		int ticks = startTicks == null ? 0 : startTicks;
		if (increment) {
			++ticks;
			if (startTicks == null) {
				return world.redstoneBurnoutMap.putIfAbsent(hash, (Integer) ticks) == null && ticks > 16;
			} else {
				return world.redstoneBurnoutMap.replace(hash, startTicks, (Integer) ticks) && ticks > 16;
			}
		}
		return ticks > 16;
	}

	@Override
	protected boolean checkForBurnout(World world, int x, int y, int z, boolean increment) {
		return checkForBurnout(world, hash(x, y, z), increment);
	}

	private static boolean isIndirectlyPowered(World world, int x, int y, int z, int metadata) {
		return metadata == 5 && world.isBlockIndirectlyProvidingPowerTo(x, y - 1, z, 0) || (metadata == 3 && world.isBlockIndirectlyProvidingPowerTo(x, y, z - 1, 2) || (metadata == 4 && world.isBlockIndirectlyProvidingPowerTo(x, y, z + 1, 3) || (metadata == 1 && world.isBlockIndirectlyProvidingPowerTo(x - 1, y, z, 4) || metadata == 2 && world.isBlockIndirectlyProvidingPowerTo(x + 1, y, z, 5))));
	}

	@Override
	public void updateTick(World world, int x, int y, int z, Random rand) {
		int metadata = world.getBlockMetadata(x, y, z);
		boolean indirectlyPowered = isIndirectlyPowered(world, x, y, z, metadata);

		if (this.torchActive) {
			if (indirectlyPowered) {
				world.setBlockAndMetadataWithNotify(x, y, z, Block.torchRedstoneIdle.blockID, metadata);

				if (this.checkForBurnout(world, x, y, z, true)) {
					world.playSoundEffect((double) ((float) x + 0.5F), (double) ((float) y + 0.5F), (double) ((float) z + 0.5F), "random.fizz", 0.5F, 2.6F + (world.rand.nextFloat() - world.rand.nextFloat()) * 0.8F);

					if (world instanceof WorldServer) {
						PlayerInstance playerInstance = ((WorldServer) world).getPlayerManager().getOrCreateChunkWatcher(x >> 4, z >> 4, false);
						if (playerInstance != null) {
							playerInstance.sendToAllPlayersWatchingChunk(new Packet61DoorChange(2000, x, y, z, rand.nextInt(9), false));
							playerInstance.sendToAllPlayersWatchingChunk(new Packet61DoorChange(2000, x, y, z, rand.nextInt(9), false));
						}
					}
				}
			}
		} else if (!indirectlyPowered && !this.checkForBurnout(world, x, y, z, false)) {
			world.setBlockAndMetadataWithNotify(x, y, z, Block.torchRedstoneActive.blockID, metadata);
		}
	}
}
