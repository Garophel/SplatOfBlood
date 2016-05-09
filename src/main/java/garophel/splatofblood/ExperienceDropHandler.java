package garophel.splatofblood;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ExperienceDropHandler {
	
	@SubscribeEvent
	public void expDrop(LivingExperienceDropEvent e) {
		if(e.getEntityLiving() instanceof EntityPlayer && e.getEntityLiving().hasCapability(DeathInventoryManager.DEATH_INVENTORY_HANDLER, null)) {
			e.setCanceled(true);
		}
	}
	
}
