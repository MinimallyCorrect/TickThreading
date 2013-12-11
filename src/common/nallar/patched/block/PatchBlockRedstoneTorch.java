package nallar.patched.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRedstoneTorch;
import net.minecraft.network.packet.Packet61DoorChange;
import net.minecraft.server.management.PlayerInstance;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.*;

public abstract class PatchBlockRedstoneTorch extends BlockRedstoneTorch {
	public PatchBlockRedstoneTorch(final int par1, final boolean par2) {
		super(par1, par2);
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

	@Override
	protected boolean isIndirectlyPowered(World par1World, int par2, int par3, int par4) {
		int l = par1World.getBlockMetadata(par2, par3, par4);
		return l == 5 && par1World.getIndirectPowerOutput(par2, par3 - 1, par4, 0) || (l == 3 && par1World.getIndirectPowerOutput(par2, par3, par4 - 1, 2) || (l == 4 && par1World.getIndirectPowerOutput(par2, par3, par4 + 1, 3) || (l == 1 && par1World.getIndirectPowerOutput(par2 - 1, par3, par4, 4) || l == 2 && par1World.getIndirectPowerOutput(par2 + 1, par3, par4, 5))));
	}

	@Override
	public void updateTick(World world, int x, int y, int z, Random rand) {
		int metadata = world.getBlockMetadata(x, y, z);
		boolean indirectlyPowered = isIndirectlyPowered(world, x, y, z);

		if (this.torchActive) {
			if (indirectlyPowered) {
				world.setBlock(x, y, z, Block.torchRedstoneIdle.blockID, metadata, 3);

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
			world.setBlock(x, y, z, Block.torchRedstoneActive.blockID, metadata, 3);
		}
	}
}
