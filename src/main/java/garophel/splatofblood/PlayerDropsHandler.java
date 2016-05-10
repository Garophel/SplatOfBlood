package garophel.splatofblood;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class PlayerDropsHandler {
	
	@SubscribeEvent
	public void handle(PlayerDropsEvent e) {
		World world = e.getEntityPlayer().worldObj;
		BlockPos origin = e.getEntityPlayer().getPosition();
		
		if(origin.getY() < 0) {
			origin = new BlockPos(origin.getX(), 0, origin.getZ());
			for(int y = 0; y < 5; y++) {
				origin = origin.up();
				if(world.getBlockState(origin).getBlock() != Blocks.bedrock) {
					break;
				}
			}
		} else if(origin.getY() > world.getHeight()) {
			origin = new BlockPos(origin.getX(), world.getHeight() - 1, origin.getZ());
			
			for(int y = 0; y < 5; y++) {
				origin = origin.down();
				if(world.getBlockState(origin).getBlock() != Blocks.bedrock) {
					break;
				}
			}
		}
		
		if(world.isAirBlock(origin)) {
			BlockPos newOrigin = origin.down();
			while(!world.getBlockState(newOrigin).getMaterial().blocksMovement()) {
				origin = newOrigin;
			}
		} else {
			if(world.isAirBlock(origin.up())) {
				origin = origin.up();
			} else {
				root:
				for(int y = -1; y < 2; y++) {
					for(int x = -1; x < 2; x++) {
						for(int z = -1; z < 2; z++) {
							if(world.isAirBlock(origin.add(x, y, z))) {
								break root;
							}
						}
					}
				}
			}
		}
		
		if(world.setBlockState(origin, SplatOfBlood.bloodSplat.getDefaultState())) {
			TileEntity te = world.getTileEntity(origin);
			if(te instanceof TileBloodSplat) {
				UUID deathInventoryId = DeathInventoryManager.instance.storeDeathInventory(e.getEntityPlayer(), e.getDrops());
				((TileBloodSplat) te).createSplat(deathInventoryId);
				
				e.setCanceled(true);
			}
		} else {
			SplatOfBlood.log.error("Couldn't create blood splat at " + origin.toString());
			return;
		}
		
			
		
	}
	
	// store items in separate location, file named with an uuid?
	// save uuid into player
	// save uuid into splat tile
	
	// tile broken -> drop items
	// player dies again -> old uuid invalidated -> new uuid generated, old drops no longer accessible
}
