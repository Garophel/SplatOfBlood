package garophel.splatofblood;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import com.mojang.realmsclient.gui.ChatFormatting;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

public class TileBloodSplat extends TileEntity {
	
	private static final UUID uuidZero = new UUID(0l, 0l);
	
	@Deprecated
	private List<ItemStack> items = new LinkedList<ItemStack>();
	
	@Deprecated
	private UUID ownerUUID = uuidZero;
	@Deprecated
	private String ownerName = "";
	
	private UUID deathInventoryId = uuidZero;
	
	@Deprecated
	public void setItems(EntityPlayer plr, List<ItemStack> items) {
		ownerUUID = plr.getUniqueID();
		ownerName = plr.getName();
		
		items.clear();
		items.addAll(items);
	}
	
	public void createSplat(UUID deathInventoryId) {
		this.deathInventoryId = deathInventoryId;
	}
	
	/** Returns whether the splat was removed or not. */
	public boolean removeSplat() {
		if(!DeathInventoryManager.instance.isSplatValid(deathInventoryId)) {
			removeBlock();
			return true;
		}
		
		return false;
	}
	
	public void breakSplat() {
		DeathInventoryManager.instance.destroyBloodSplat(worldObj, pos, deathInventoryId);
		removeBlock();
	}
	
	public void recoverItems(EntityPlayer plr) {
		if(DeathInventoryManager.instance.tryRecoverDeathInventory(deathInventoryId, plr)) {
			removeBlock();
		}
	}
	
	private void removeBlock() {
		worldObj.removeTileEntity(pos);
		worldObj.setBlockToAir(pos);
	}
	
	public static void dropItem(World world, BlockPos pos, ItemStack stack) {
		float r = .5f;
		
		double x = world.rand.nextFloat() * r + (1f - r) * .5f;
		double y = world.rand.nextFloat() * r + (1f - r) * .5f;
		double z = world.rand.nextFloat() * r + (1f - r) * .5f;
		
		EntityItem ent = new EntityItem(world, (double) pos.getX() + x, (double) pos.getY() + y, (double) pos.getZ() + z, stack);
		world.spawnEntityInWorld(ent);
	}
	
	@Deprecated
	public void dropItems() {
		for(ItemStack is : items) {
			dropItem(worldObj, pos, is);
		}
	}
	
	@Deprecated
	public void pickupItems(EntityPlayer plr) {
		if(items.size() == 0) removeBlock();
		
		if(ownerUUID.equals(plr.getUniqueID()) || ownerName.equals(plr.getName())) {
			int count = 0;
			for(ItemStack is : items) {
				if(!plr.inventory.addItemStackToInventory(is) || is.stackSize != 0) {
					plr.addChatMessage(new TextComponentString(ChatFormatting.AQUA.toString() + count + " items recovered, " + items.size() + " remain." + ChatFormatting.RESET));
					return;
				}
				
				count++;
				items.remove(is);
			}
			
			plr.addChatMessage(new TextComponentString(ChatFormatting.AQUA + "All remaining " + count + " recovered." + ChatFormatting.RESET));
			
			removeBlock();
		} else {
			plr.addChatMessage(new TextComponentString(ChatFormatting.RED + "You can't steal another player's items!" + ChatFormatting.RESET));
		}
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		
		nbt.setUniqueId("deathInvId", deathInventoryId);
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		
		this.deathInventoryId = nbt.getUniqueId("deathInvId");
	}
}
