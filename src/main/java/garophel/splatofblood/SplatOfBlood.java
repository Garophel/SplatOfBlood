package garophel.splatofblood;

import java.io.File;

import org.apache.logging.log4j.Logger;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.chunk.storage.AnvilSaveConverter;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod(modid=SplatOfBlood.MODID, name=SplatOfBlood.MOD_NAME, version=SplatOfBlood.VERSION)
public class SplatOfBlood {
	public static final String MODID = "splatofblood";
	public static final String MOD_NAME = "Splatofblood";
	public static final String VERSION = "1.0";
	
	public static Logger log = null;
	
	public static Block bloodSplat;
	
	@EventHandler
	public void preInit(FMLPreInitializationEvent e) {
		log = e.getModLog();
		
		bloodSplat = new BlockBloodSplat();
		GameRegistry.register(bloodSplat.setRegistryName(new ResourceLocation(MODID, "bloodSplat")));
		
		ItemBlock itemBloodSplat = new BlockBloodSplat.ItemBlockBloodSplat(bloodSplat);
		GameRegistry.register(itemBloodSplat.setRegistryName(new ResourceLocation(MODID, "bloodSplat")));
	}
	
	@EventHandler
	public void init(FMLInitializationEvent e) {
		DeathInventoryManager.registerCapability();

		MinecraftForge.EVENT_BUS.register(new DeathInventoryManager.DeathInventoryAttacher());
		MinecraftForge.EVENT_BUS.register(new PlayerDropsHandler());
		
		TileEntity.addMapping(TileBloodSplat.class, MODID + "_bloodSplat");
	}
	
	@EventHandler
	public void serverStarting(FMLServerStartingEvent e) {
		try {
			System.out.println("FML Common saves: " + FMLCommonHandler.instance().getSavesDirectory());
			File fl = new File(((AnvilSaveConverter)e.getServer().getActiveAnvilConverter()).savesDirectory, e.getServer().getFolderName());
			DeathInventoryManager.instance = new DeathInventoryManager(fl);
			System.out.println("Directory: " + fl.getAbsolutePath());
		} catch(Exception er) {
			System.err.println("Error during serverstarting in splatofblood");
			er.printStackTrace();
		}
	}
	
	@EventHandler
	public void serverStopping(FMLServerStoppingEvent e) {
		DeathInventoryManager.instance = null; // is a save needed?
	}
}
