package garophel.splatofblood;

import garophel.splatofblood.DeathInventoryManager.DeathInventoryEntry;

import java.util.LinkedList;
import java.util.List;

import com.google.common.base.Function;
import com.mojang.realmsclient.gui.ChatFormatting;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

public class CmdDeathInventory extends CommandBase {
	
	private Function<DeathInventoryEntry, String> dumpEntry = new Function<DeathInventoryManager.DeathInventoryEntry, String>() {

		@Override
		public String apply(DeathInventoryEntry entry) {
			return entry.getPlayerName() + ": " + ChatFormatting.GRAY + entry.getDeathInventoryId() + " (" + entry.getDeathInventory() + ")" + ChatFormatting.RESET;
		}
	};
	
	private Function<DeathInventoryEntry, String> nameList = new Function<DeathInventoryManager.DeathInventoryEntry, String>() {

		@Override
		public String apply(DeathInventoryEntry entry) {
			return entry.getPlayerName();
		}
	};
	
	private Function<DeathInventoryEntry, String> invidList = new Function<DeathInventoryManager.DeathInventoryEntry, String>() {

		@Override
		public String apply(DeathInventoryEntry entry) {
			return entry.getDeathInventoryId().toString();
		}
	};
	
	private Function<DeathInventoryEntry, String> playeridList = new Function<DeathInventoryManager.DeathInventoryEntry, String>() {

		@Override
		public String apply(DeathInventoryEntry entry) {
			return entry.getPlayerUUID().toString();
		}
	};

	@Override
	public String getCommandName() {
		return "deathinventory";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/deathinventory [subcommand]";
	}

	@Override // 0 = inv id, 1 = player id, 2 = player name
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if(sender instanceof EntityPlayer) {
			if(DeathInventoryManager.instance != null) {
				if(args.length == 0) {
					for(String s : DeathInventoryManager.instance.getDeathInventoryStrings(null, 2, dumpEntry, true)) {
						sender.addChatMessage(new TextComponentString(s));
					}
				} else {
					if(!args[0].startsWith("--")) {
						for(String s : DeathInventoryManager.instance.getDeathInventoryStrings(args.length == 2 ? args[1] : null, 2, dumpEntry, true)) {
							sender.addChatMessage(new TextComponentString(s));
						}
					}
					
					if(args[0].equalsIgnoreCase("--name")) {
						for(String s : DeathInventoryManager.instance.getDeathInventoryStrings(args.length == 2 ? args[1] : null, 2, dumpEntry, true)) {
							sender.addChatMessage(new TextComponentString(s));
						}
					} else if(args[0].equalsIgnoreCase("--invid")) {
						for(String s : DeathInventoryManager.instance.getDeathInventoryStrings(args.length == 2 ? args[1] : null, 0, dumpEntry, true)) {
							sender.addChatMessage(new TextComponentString(s));
						}
					} else if(args[0].equalsIgnoreCase("--playerid")) {
						for(String s : DeathInventoryManager.instance.getDeathInventoryStrings(args.length == 2 ? args[1] : null, 1, dumpEntry, true)) {
							sender.addChatMessage(new TextComponentString(s));
						}
					}
					
					if("--clearall".equalsIgnoreCase(args[0])) {
						if(args.length == 2 && "YES".equals(args[1])) {
							DeathInventoryManager.instance.deleteAllDeathInventories();
							sender.addChatMessage(new TextComponentString("All death inventories were deleted..."));
						} else {
							sender.addChatMessage(new TextComponentString(ChatFormatting.RED + "[Mistake Protection] Add \"YES\" to perform the operation." + ChatFormatting.RESET));
						}
					}
				}
			}
		}
	}
	
	@Override
	public List<String> getTabCompletionOptions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
		List<String> ret = new LinkedList<String>();
		if(DeathInventoryManager.instance != null) {
			if(args[0].startsWith("--name") || !args[0].startsWith("--")) {
				ret.addAll(DeathInventoryManager.instance.getDeathInventoryStrings(args.length == 2 ? args[1] : args.length == 1 ? args[0] : null, 2, nameList, false));
			} else if(args.length == 1 || args.length == 2) {
				if(args[0].startsWith("--invid")) {
					ret.addAll(DeathInventoryManager.instance.getDeathInventoryStrings(args.length == 2 ? args[1] : null, 2, invidList, false));
				} else if(args[0].startsWith("--playerid")) {
					ret.addAll(DeathInventoryManager.instance.getDeathInventoryStrings(args.length == 2 ? args[1] : null, 2, playeridList, false));
				}
			}
//			sender.addChatMessage(new TextComponentString("\"" + args[0] + "\""));
			
		}
		
		return ret;
	}
	
}
