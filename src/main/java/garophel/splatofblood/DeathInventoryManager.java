package garophel.splatofblood;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TimeZone;
import java.util.UUID;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.google.common.base.Function;
import com.google.common.io.Files;

public class DeathInventoryManager {
	
	private static UUID uuidZero = new UUID(0l, 0l);
	public static DeathInventoryManager instance = null;
	
	private Random rng = new Random();
	private DeathInventories data;
	
	private static Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
	private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
	
	private static final String deathInventoriesDirectory = "DeathInventories";
	
	public DeathInventoryManager(File worldDir) {
		data = new DeathInventories(new File(worldDir, deathInventoriesDirectory), "DeathInventories");
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
		} else {
			SplatOfBlood.log.warn("No such blood splat: " + deathInventoryId + ", " + getDeathTimeFormatted(deathInventoryId));
		}
		
		return false;
	}
	
	public void deleteAllDeathInventories() {
		data.clearAll();
	}
	
	public List<String> getDeathInventoryStrings(String filter, int type, Function<DeathInventoryEntry, String> func, boolean endMarker) {
		return data.getDeathInventoryStrings(filter, type, func, endMarker);
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
		UUID deathInventoryId = createDeathInventoryId();
		data.storeDeathInventory(deathInventoryId, plr, deathItems);
		return deathInventoryId;
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
		data.init();
		return this;
	}
	
	public static class DeathInventories {
		private Map<UUID, DeathInventoryEntry> invIdToEntryMap = new HashMap<UUID, DeathInventoryEntry>();
		private Map<UUID, DeathInventoryEntry> playerIdToEntryMap = new HashMap<UUID, DeathInventoryEntry>();
		private Map<String, DeathInventoryEntry> playerNameToEntryMap = new HashMap<String, DeathInventoryEntry>();
		
		private final File dir, mainFile, backupFile, mainFileOld, backupFileOld;
		
		public DeathInventories(File dir, String filename) {
			this.dir = dir;
			
			mainFile = new File(dir, filename + ".dat");
			backupFile = new File(dir, filename + ".backup.dat");
			
			mainFileOld = new File(dir, filename + ".dat.old");
			backupFileOld = new File(dir, filename + ".backup.dat.old");
		}
		
		public void init() {
			if(!dir.isDirectory()) {
				dir.mkdir();
			}
			
			if(mainFile.isFile() || backupFile.isFile()) {
				load();
			}
		}
		
		public void clearAll() {
			invIdToEntryMap.clear();
			save();
		}
		
		private DeathInventory removeDeathInventory(DeathInventoryEntry entry, boolean forceSave) {
			if(entry == null) return null;
			
			invIdToEntryMap.remove(entry.getDeathInventoryId());
			playerIdToEntryMap.remove(entry.getPlayerUUID());
			playerNameToEntryMap.remove(entry.getPlayerName());
			
			if(forceSave) save();
			
			return entry.getDeathInventory();
		}
		
		private void addDeathInventory(DeathInventoryEntry entry, boolean forceSave) {
			if(entry == null) return;
			
			invIdToEntryMap.put(entry.getDeathInventoryId(), entry);
			playerIdToEntryMap.put(entry.getPlayerUUID(), entry);
			playerNameToEntryMap.put(entry.getPlayerName(), entry);
			
			if(forceSave) save();
		}
		
		/** @param type: 0 = inv id, 1 = player id, 2 = player name */
		public List<String> getDeathInventoryStrings(String filter, int type, Function<DeathInventoryEntry, String> func, boolean endMarker) {
			boolean doFilter = filter != null && !filter.isEmpty();
			List<String> ret = new LinkedList<String>();
			switch(type) {
			case 0:
				for(Entry<UUID, DeathInventoryEntry> e : invIdToEntryMap.entrySet()) {
					if(doFilter) {
						if(e.getKey().toString().startsWith(filter)) ret.add(func.apply(e.getValue()));
					} else {
						ret.add(func.apply(e.getValue()));
					}
				}
				
				break;
			case 1:
				for(Entry<UUID, DeathInventoryEntry> e : playerIdToEntryMap.entrySet()) {
					if(doFilter) {
						if(e.getKey().toString().startsWith(filter)) ret.add(func.apply(e.getValue()));
					} else {
						ret.add(func.apply(e.getValue()));
					}
				}
				
				break;
			case 2:
				for(Entry<String, DeathInventoryEntry> e : playerNameToEntryMap.entrySet()) {
					if(doFilter) {
						if(e.getKey().startsWith(filter)) ret.add(func.apply(e.getValue()));
					} else {
						ret.add(func.apply(e.getValue()));
					}
				}
				
				break;
			}
			
			if(endMarker && ret.isEmpty()) {
				ret.add("[No death inventories]");
			}
			
			return ret;
		}
		
		public boolean isDeathInventoryValid(UUID deathInventoryId) {
			return invIdToEntryMap.containsKey(deathInventoryId);
		}
		
		public DeathInventory deleteDeathInventory(UUID deathInventoryId, boolean forceSave) {
			DeathInventory ret = removeDeathInventory(invIdToEntryMap.get(deathInventoryId), false);
//			SplatOfBlood.log.info("deleteDeathInv.ret: " + ret);
			if(forceSave) save();
			return ret;
		}
		
		public DeathInventory getDeathInventoryForPlayer(UUID deathInventoryId, EntityPlayer plr) {
			DeathInventoryEntry deathInventory = invIdToEntryMap.get(deathInventoryId);
//			SplatOfBlood.log.info("DI: " + deathInventory);
			return deathInventory == null ? null : deathInventory.isOwner(plr) ? deathInventory.getDeathInventory() : null;
		}
		
		private DeathInventoryEntry getOldDeathInventory(EntityPlayer plr) {
			DeathInventoryEntry old = playerIdToEntryMap.get(plr.getUniqueID());
			if(old != null) return old;
			
			old = playerNameToEntryMap.get(plr.getName());
			return old;
		}
		
		public void storeDeathInventory(UUID deathInventoryId, EntityPlayer plr, List<EntityItem> drops) {
			DeathInventory deathInventory = DeathInventory.fromDrops(plr, drops);
			SplatOfBlood.log.info("CREATE death inventory: " + deathInventoryId + " = " + deathInventory);
			
			DeathInventoryEntry old = getOldDeathInventory(plr);
			removeDeathInventory(old, false);
			
			addDeathInventory(new DeathInventoryEntry(plr.getUniqueID(), plr.getName(), deathInventoryId, deathInventory), true);
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
				NBTTagCompound entry = deathInventories.getCompoundTagAt(i);
				
				DeathInventoryEntry dientry = DeathInventoryEntry.fromNBT(entry);
				if(dientry == null) {
					SplatOfBlood.log.error("Found an invalid death inventory.");
				} else {
					addDeathInventory(dientry, false);
				}
				
//				UUID deathInventoryId = pair.getUniqueId("id");
//				NBTTagCompound deathInventory = pair.getCompoundTag("data");
//				
//				if(!deathInventoryId.equals(uuidZero)) {
//					DeathInventory di = DeathInventory.fromNBT(deathInventory);
//					if(di != null) {
//						SplatOfBlood.log.info("LOAD death inventory: " + deathInventoryId + " = " + di);
//						invIdToEntryMap.put(deathInventoryId, di);
//					} else {
//						SplatOfBlood.log.error("Found an invalid death inventory (deserialization failed)!");
//					}
//				} else {
//					SplatOfBlood.log.error("Found an invalid death inventory (zero id)!");
//				}
			}
			
			return true;
		}
		
		/** Returns true if save succeeded fine. */
		public boolean save() {
			boolean fine = true;
			
			NBTTagCompound nbt = new NBTTagCompound();
			NBTTagList deathInventories = new NBTTagList();
			nbt.setTag("list", deathInventories);
			
			for(Entry<UUID, DeathInventoryEntry> ent : invIdToEntryMap.entrySet()) {
//				NBTTagCompound pair = new NBTTagCompound();
//				pair.setUniqueId("id", ent.getKey());
//				pair.setTag("data", ent.getValue().toNBT());
//				
				deathInventories.appendTag(ent.getValue().toNBT());
				SplatOfBlood.log.info("SAVE death inventory: " + ent.getKey() + " = " + ent.getValue());
			}
			
			if(mainFile.isFile()) {
				try {
					Files.move(mainFile, mainFileOld);
				} catch(Exception e) {
					SplatOfBlood.log.error("Error while moving main data file into an old file. Trying File.renameTo..");
					SplatOfBlood.log.catching(e);
					if(!mainFile.renameTo(mainFileOld)) SplatOfBlood.log.error("File.renameTo for main file failed aswell..");
				}
			}
			
			if(!tryWriteFile(mainFile, nbt)) {
				fine = false;
				SplatOfBlood.log.error("Error while writing main file.");
			}
			
			if(backupFile.isFile()) {
				try {
					Files.move(backupFile, backupFileOld);
				} catch(Exception e) {
					SplatOfBlood.log.error("Error while moving backup file into an old file. Trying File.renameTo..");
					SplatOfBlood.log.catching(e);
					if(!backupFile.renameTo(backupFileOld)) SplatOfBlood.log.error("File.renameTo for backup file failed aswell..");
				}
			}
			
			if(!tryWriteFile(backupFile, nbt)) {
				fine = false;
				SplatOfBlood.log.error("Error while writing backup file.");
			}
			
			return fine;
		}
	}
	
	public static class DeathInventoryEntry {
		private UUID playerUUID;
		private String playerName;
		private UUID deathInventoryId;
		private DeathInventory deathInventory;
		
		private NBTTagCompound cached = null;
		
		public DeathInventoryEntry(UUID playerUUID, String playerName, UUID deathInventoryId, DeathInventory deathInventory) {
			this.playerUUID = playerUUID;
			this.playerName = playerName;
			this.deathInventoryId = deathInventoryId;
			this.deathInventory = deathInventory;
		}
		
		public boolean isOwner(EntityPlayer plr) {
			return plr.getUniqueID().equals(playerUUID) || plr.getName().equals(playerName);
		}
		
		private DeathInventoryEntry setCached(NBTTagCompound nbt) {
			cached = nbt;
			return this;
		}
		
		public UUID getPlayerUUID() {
			return playerUUID;
		}
		
		public String getPlayerName() {
			return playerName;
		}
		
		public UUID getDeathInventoryId() {
			return deathInventoryId;
		}
		
		public DeathInventory getDeathInventory() {
			return deathInventory;
		}
		
		public void invalidateCache() {
			cached = null;
		}
		
		public NBTTagCompound toNBT() {
			if(cached != null) return cached;
			
			NBTTagCompound nbt = new NBTTagCompound();
			nbt.setUniqueId("playerUUID", playerUUID);
			nbt.setString("playerName", playerName);
			nbt.setUniqueId("id", deathInventoryId);
			nbt.setTag("di", deathInventory.toNBT());
			
			return cached = nbt;
		}
		
		public static DeathInventoryEntry fromNBT(NBTTagCompound nbt) {
			UUID playerUUID = nbt.getUniqueId("playerUUID");
			if(uuidZero.equals(playerUUID)) return null;
			
			String playerName = nbt.getString("playerName");
			if(playerName.isEmpty()) return null;
			
			UUID deathInventoryId = nbt.getUniqueId("id");
			if(uuidZero.equals(playerUUID)) return null;
			
			DeathInventory deathInventory = DeathInventory.fromNBT(nbt.getCompoundTag("di"));
			if(deathInventory == null) return null;
			
			return new DeathInventoryEntry(playerUUID, playerName, deathInventoryId, deathInventory).setCached(nbt);
		}
		
		@Override
		public String toString() {
			return deathInventory.toString();
		}
	}
	
	public static class DeathInventory {
		private final UUID playerUUID;
		private final String playerName;
		
		private List<ItemStack> items;
		private int experience = 0;
		
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
			this.cached = null;
			return this;
		}
		
		public boolean isOwner(EntityPlayer plr) {
			return plr.getUniqueID().equals(playerUUID) || plr.getName().equals(playerName);
		}
		
		public NBTTagCompound toNBT() {
			if(cached != null) return cached;
			NBTTagCompound nbt = new NBTTagCompound();
			
			nbt.setUniqueId("playerUUID", playerUUID);
			nbt.setString("playerName", playerName);
			
			NBTTagList items = new NBTTagList();
			nbt.setTag("items", items);
			for(ItemStack is : this.items) {
				NBTTagCompound itemNBT = is.serializeNBT();
				if(itemNBT == null) {
					SplatOfBlood.log.warn("Null item nbt: " + itemNBT);
				} else {
					items.appendTag(itemNBT);
				}
			}
			
			nbt.setInteger("experience", experience);
			
			return cached = nbt;
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
		
		@Override
		public String toString() {
			return "Owner name: " + playerName + ", owner UUID: " + playerUUID + ", stacks: " + (items == null ? "NULL" : items.size()) + ", exp: " + experience; 
		}
	}
}
