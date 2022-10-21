package ca.tweetzy.auctionhouse.listeners;

import ca.tweetzy.core.utils.nms.NBTEditor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;

/**
 * This event handler (PrepareAnvilEvent) was in {@link PlayerListeners} but needed to be moved out
 * because it breaks compatibility with 1.8
 * (bukkit doesn't register any handler of the class because this event is unknown).
 */
public class AnvilListener implements Listener {
	@EventHandler
	public void onInventoryClick(PrepareAnvilEvent event) {
		ItemStack stack = event.getResult();
		if (stack == null) return;

		stack = NBTEditor.set(stack, "AUCTION_REPAIRED", "AuctionHouseRepaired");
		event.setResult(stack);
	}
}
