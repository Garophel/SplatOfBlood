package garophel.splatofblood;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TimeZone;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.Capability.IStorage;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import org.apache.logging.log4j.Level;

import com.google.common.io.Files;
import com.sun.corba.se.spi.extension.ZeroPortPolicy;

public class DeathInventoryManager {
	
	@CapabilityInject(IPlayerDeathInventory.class)
	public static Capability<IPlayerDeathInventory> DEATH_INVENTORY_HANDLER;
	
	public static ResourceLocation deathInventoryCapKey = new ResourceLocation(SplatOfBlood.MODID, "di");
	
	public static void registerCapability() {
		CapabilityManager.INSTANCE.register(IPlayerDeathInventory.class, new IStorage<IPlayerDeathInventory>() {
			
			@Override
			public NBTBase writeNBT(Capability<IPlayerDeathInventory> capability, IPlayerDeathInventory instance, EnumFacing side) {
				return null;
			}
			
			@Override
			public void readNBT(Capability<IPlayerDeathInventory> capability, IPlayerDeathInventory instance, EnumFacing side, NBTBase nbt) {}
			
		}, PlayerDeathInventory.class);
	}
	
	private static UUID uuidZero = new UUID(0l, 0l);
	public static DeathInventoryManager instance = null;
	
	private Random rng = new Random();
	private DeathInventories data;
	
	private static Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
	private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
	
	public DeathInventoryManager(File worldDir) {
		data = new DeathInventories(worldDir, "DeathInventories");
	}
	
	/** Called when a player right-clicks a blood splat.
	 *  As many items as possible will be added to the player's
	 *  inventory and removed from the storage if the player
	 *  owns the splat.
	 *  @return true if the splat can be removed. */
	public boolean tryRecoverDeathInventory(UUID deathInventoryId, EntityPlayer plr) {
		DeathInventory deathInventory = data.getDeathInventoryForPlayer(deathInventoryId, plr);
		if(deathInventory != null) {
			boolean changed = false;
			boolean all = true;
			Iterator<ItemStack> iter = deathInventory.items.iterator();
			while(iter.hasNext()) {
				ItemStack is = iter.next();
				
				int stacksize = is.stackSize;
				if(plr.inventory.addItemStackToInventory(is)) {
					iter.remove();
					changed = true;
				} else {
					if(stacksize != is.stackSize) changed = true;
					all = false;
				}
			}
			
//			for(ItemStack is : deathInventory.items) {
//				
//			}
			
			if(deathInventory.experience > 0) {
				plr.addExperience(deathInventory.experience);
				deathInventory.experience = 0;
				changed = true;
			}
			
			if(all) {
				data.deleteDeathInventory(deathInventoryId, true);
				return true;
			} else if(changed) {
				deathInventory.setCacheInvalid();
				data.save();
			}
		}
		
		return false;
	}
	
	/** Used to determine whether it's ok to allow any player to
	 *  remove the splat. */
	public boolean isSplatValid(UUID deathInventoryId) {
		return data.isDeathInventoryValid(deathInventoryId);
	}
	
	/** Called when a blood splat is destroyed. Actual breaking of
	 *  the block needs to be handled elsewhere. */
	public void destroyBloodSplat(World world, BlockPos pos, UUID deathInventoryId) {
		DeathInventory di = data.deleteDeathInventory(deathInventoryId, true);
		if(di != null) {
			for(ItemStack is : di.items) {
				TileBloodSplat.dropItem(world, pos, is);
			}
		}
	}
	
	public UUID storeDeathInventory(EntityPlayer plr, List<EntityItem> deathItems) {
		UUID oldDeathInventoryId = null;
		IPlayerDeathInventory pdi = plr.getCapability(DEATH_INVENTORY_HANDLER, null);
		if(pdi != null) {
			UUID deathInventoryId = createDeathInventoryId();
			oldDeathInventoryId = pdi.getDeathInventoryId();

			if(oldDeathInventoryId != null) {
				data.deleteDeathInventory(oldDeathInventoryId, false);
			}
			
			data.storeDeathInventory(deathInventoryId, plr, deathItems);
			pdi.setDeathInventoryId(deathInventoryId);
			
			return deathInventoryId;
		}
		
		SplatOfBlood.log.error("Death inventory capability not found on player: " + plr.getDisplayName() + "(" + plr.getName() + " / " + plr.getUniqueID() + ")");
		return null;
	}
	
	public UUID createDeathInventoryId() {
		long time = utc.getTimeInMillis();
		long rand = (long) rng.nextInt() << 32 | (long) rng.nextInt();
		
		return new UUID(time, rand);
	}
	
	public long getDeathTimestamp(UUID deathInventoryId) {
		return deathInventoryId.getMostSignificantBits();
	}
	
	public String getDeathTimeFormatted(UUID deathInventoryId) {
		Date date = new Date(getDeathTimestamp(deathInventoryId));
		return dateFormat.format(date);
	}
	
	public DeathInventoryManager init() {
		
		return this;
	}
	
	public static class DeathInventories {
		private Map<UUID, DeathInventory> validDeathInventories = new HashMap<UUID, DeathInventory>();
		private final File mainFile, backupFile, mainFileOld, backupFileOld;
		
		public DeathInventories(File dir, String filename) {
			mainFile = new File(dir, filename + ".dat");
			backupFile = new File(dir, filename + ".backup.dat");
			
			mainFileOld = new File(dir, filename + ".dat.old");
			backupFileOld = new File(dir, filename + ".backup.dat.old");
		}
		
		public void init() {
			try {
				if(mainFile.exists()) {
					if(mainFile.isFile()) {
						
					} else {
						SplatOfBlood.log.error("Directory at " + mainFile.getAbsolutePath() + " is blocking a required data file!");
					}
				}
			} catch(Exception e) {
				SplatOfBlood.log.catching(e);
			}
		}
		
		public boolean isDeathInventoryValid(UUID deathInventoryId) {
			return validDeathInventories.containsKey(deathInventoryId);
		}
		
		public DeathInventory deleteDeathInventory(UUID deathInventoryId, boolean forceSave) {
			DeathInventory ret = validDeathInventories.remove(deathInventoryId);
			if(forceSave) save();
			return ret;
		}
		
		public DeathInventory getDeathInventoryForPlayer(UUID deathInventoryId, EntityPlayer plr) {
			DeathInventory deathInventory = validDeathInventories.get(deathInventoryId);
			return deathInventory == null ? null : deathInventory.isOwner(plr) ? deathInventory : null;
		}
		
		public void storeDeathInventory(UUID deathInventoryId, EntityPlayer plr, List<EntityItem> drops) {
			DeathInventory deathInventory = DeathInventory.fromDrops(plr, drops);
			
			validDeathInventories.put(deathInventoryId, deathInventory);
			save();
		}
		
		private static boolean tryWriteFile(File file, NBTTagCompound data) {
			try {
				FileOutputStream fos = new FileOutputStream(file);
				CompressedStreamTools.writeCompressed(data, fos);
				return true;
			} catch(Exception e) {
				SplatOfBlood.log.catching(e);
			}
			
			return false;
		}
		
		private static NBTTagCompound tryLoadFile(File file) {
			try {
				FileInputStream fis = new FileInputStream(file);
				return CompressedStreamTools.readCompressed(fis);
			} catch(Exception e) {
				SplatOfBlood.log.catching(e);
			}
			
			return null;
		}
		
		public boolean load() {
			NBTTagCompound nbt = tryLoadFile(mainFile);
			if(nbt == null) {
				SplatOfBlood.log.warn("Main death inventories file inaccessible, attempting to load backup...");
				nbt = tryLoadFile(backupFile);
				if(nbt == null) {
					SplatOfBlood.log.error("Main and backup death inventory files inaccessible! Regenerating...");
					return false;
				}
			}
			
			NBTTagList deathInventories = nbt.getTagList("list", 10);
			final int len = deathInventories.tagCount();
			for(int i = 0; i < len; i++) {
				NBTTagCompound pair = deathInventories.getCompoundTagAt(i);
				
				UUID deathInventoryId = pair.getUniqueId("id");
				NBTTagCompound deathInventory = pair.getCompoundTag("data");
				
				if(!deathInventoryId.equals(uuidZero)) {
					DeathInventory di = DeathInventory.fromNBT(deathInventory);
					if(di != null) {
						validDeathInventories.put(deathInventoryId, di);
					} else {
						SplatOfBlood.log.error("Found an invalid death inventory (deserialization failed)!");
					}
				} else {
					SplatOfBlood.log.error("Found an invalid death inventory (zero id)!");
				}
			}
			
			return true;
		}
		
		/** Returns true if save succeeded fine. */
		public boolean save() {
			boolean fine = true;
			
			NBTTagCompound nbt = new NBTTagCompound();
			NBTTagList deathInventories = new NBTTagList();
			nbt.setTag("list", deathInventories);
			
			for(Entry<UUID, DeathInventory> ent : validDeathInventories.entrySet()) {
				NBTTagCompound pair = new NBTTagCompound();
				pair.setUniqueId("id", ent.getKey());
				pair.setTag("data", ent.getValue().toNBT());
			}
			
			try {
				Files.move(mainFile, mainFileOld);
			} catch(Exception e) {
				SplatOfBlood.log.error("Error while moving main data file into an old file. Trying File.renameTo..");
				SplatOfBlood.log.catching(e);
				if(!mainFile.renameTo(mainFileOld)) SplatOfBlood.log.error("File.renameTo for main file failed aswell..");
			}
			
			if(!tryWriteFile(mainFile, nbt)) {
				fine = false;
				SplatOfBlood.log.error("Error while writing main file.");
			}
			
			try {
				Files.move(backupFile, backupFileOld);
			} catch(Exception e) {
				SplatOfBlood.log.error("Error while moving backup file into an old file. Trying File.renameTo..");
				SplatOfBlood.log.catching(e);
				if(!backupFile.renameTo(backupFileOld)) SplatOfBlood.log.error("File.renameTo for backup file failed aswell..");
			}
			
			if(!tryWriteFile(backupFile, nbt)) {
				fine = false;
				SplatOfBlood.log.error("Error while writing backup file.");
			}
			
			return fine;
		}
	}
	
	public static class DeathInventory {
		private final UUID playerUUID;
		private final String playerName;
		
		private List<ItemStack> items;
		private int experience = 0;
		
		private boolean cacheValid = true;
		private NBTTagCompound cached = null;
		
		public DeathInventory(EntityPlayer plr, List<ItemStack> items) {
			playerUUID = plr.getUniqueID();
			playerName = plr.getName();
			
			this.experience = plr.experienceTotal;
			this.items = items;
		}
		
		private DeathInventory(UUID playerUUID, String playerName, List<ItemStack> items, int experience) {
			this.playerUUID = playerUUID;
			this.playerName = playerName;
			
			this.items = items;
			this.experience = experience;
		}
		
		private DeathInventory setCached(NBTTagCompound nbt) {
			this.cached = nbt;
			return this;
		}
		
		public DeathInventory setCacheInvalid() {
			this.cacheValid = false;
			this.cached = null;
			return this;
		}
		
		public boolean isOwner(EntityPlayer plr) {
			return plr.getUniqueID().equals(playerUUID) || plr.getName().equals(playerName);
		}
		
		public NBTTagCompound toNBT() {
			if(cacheValid) return cached;
			NBTTagCompound nbt = new NBTTagCompound();
			
			nbt.setUniqueId("playerUUID", playerUUID);
			nbt.setString("playerName", playerName);
			
			NBTTagList items = new NBTTagList();
			nbt.setTag("items", items);
			for(ItemStack is : this.items) {
				items.appendTag(is.serializeNBT());
			}
			
			nbt.setInteger("experience", experience);
			
			return nbt;
		}
		
		public static DeathInventory fromNBT(NBTTagCompound nbt) {
			UUID playerUUID = nbt.getUniqueId("playerUUID");
			if(playerUUID.equals(uuidZero)) return null;
			
			String playerName = nbt.getString("playerName");
			if(playerName.equals("")) return null;
			
			List<ItemStack> itemList = new LinkedList<ItemStack>();
			NBTTagList items = nbt.getTagList("items", 10);
			
			final int len = items.tagCount();
			for(int i = 0; i < len; i++) {
				ItemStack is = ItemStack.loadItemStackFromNBT(items.getCompoundTagAt(i));
				if(is != null) itemList.add(is);
			}
			
			int experience = nbt.getInteger("experience");
			
			return new DeathInventory(playerUUID, playerName, itemList, experience).setCached(nbt);
		}
		
		public static DeathInventory fromDrops(EntityPlayer plr, List<EntityItem> drops) {
			List<ItemStack> items = new LinkedList<ItemStack>();
			for(EntityItem ent : drops) {
				items.add(ent.getEntityItem());
			}
			
			return new DeathInventory(plr, items);
		}
	}
	
	public static interface IPlayerDeathInventory {
		public UUID getDeathInventoryId();
		
		public void setDeathInventoryId(UUID deathInventoryId);
	}
	
	public static class PlayerDeathInventory implements IPlayerDeathInventory, ICapabilityProvider, INBTSerializable<NBTTagCompound> {
		
		private UUID deathInventoryId = uuidZero;
		
		@Override
		public UUID getDeathInventoryId() {
			return deathInventoryId;
		}
		
		@Override
		public void setDeathInventoryId(UUID deathInventoryId) {
			this.deathInventoryId = deathInventoryId;
		}

		@Override
		public NBTTagCompound serializeNBT() {
			NBTTagCompound nbt = new NBTTagCompound();
			nbt.setUniqueId("deathInvId", deathInventoryId);
			return nbt;
		}

		@Override
		public void deserializeNBT(NBTTagCompound nbt) {
			deathInventoryId = nbt.getUniqueId("deathInvId");
		}

		@Override
		public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
			return capability == DEATH_INVENTORY_HANDLER;
		}

		@Override
		public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
			if(capability == DEATH_INVENTORY_HANDLER) {
				return DEATH_INVENTORY_HANDLER.cast(this);
			}
			
			return null;
		}
	}
	
	public static class DeathInventoryAttacher {
		
		@SubscribeEvent
		public void attach(AttachCapabilitiesEvent.Entity e) {
			Entity ent = e.getEntity();
			if(!ent.worldObj.isRemote && ent instanceof EntityPlayer) {
				e.addCapability(deathInventoryCapKey, new PlayerDeathInventory());
			}
		}
	}
}
