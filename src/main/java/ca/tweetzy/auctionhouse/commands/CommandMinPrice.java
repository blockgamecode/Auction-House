package ca.tweetzy.auctionhouse.commands;

import ca.tweetzy.auctionhouse.AuctionHouse;
import ca.tweetzy.auctionhouse.api.AuctionAPI;
import ca.tweetzy.auctionhouse.auction.MinItemPrice;
import ca.tweetzy.auctionhouse.guis.GUIMinItemPrices;
import ca.tweetzy.auctionhouse.helpers.PlayerHelper;
import ca.tweetzy.core.commands.AbstractCommand;
import ca.tweetzy.core.compatibility.XMaterial;
import ca.tweetzy.core.utils.NumberUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;

/**
 * The current file has been created by Kiran Hart
 * Date Created: June 22 2021
 * Time Created: 3:18 p.m.
 * Usage of any code found within this class is prohibited unless given explicit permission otherwise
 */
public class CommandMinPrice extends AbstractCommand {

	public CommandMinPrice() {
		super(CommandType.PLAYER_ONLY, "minprices");
	}

	@Override
	protected ReturnType runCommand(CommandSender sender, String... args) {
		Player player = (Player) sender;
		if (CommandMiddleware.handle(player) == ReturnType.FAILURE) return ReturnType.FAILURE;

		if (args.length == 0) {
			AuctionHouse.getInstance().getGuiManager().showGUI(player, new GUIMinItemPrices(player));
			return ReturnType.SUCCESS;
		}

		if (args.length == 2 && args[0].equalsIgnoreCase("add")) {

			ItemStack held = PlayerHelper.getHeldItem(player);

			if (held.getType() == XMaterial.AIR.parseMaterial()) {
				AuctionHouse.getInstance().getLocale().getMessage("general.min item price air").sendPrefixedMessage(player);
				return ReturnType.FAILURE;
			}

			if (AuctionHouse.getInstance().getMinItemPriceManager().getMinPrice(held.clone()) != null) {
				AuctionHouse.getInstance().getLocale().getMessage("general.min price already added").sendPrefixedMessage(player);
				return ReturnType.FAILURE;
			}

			if (!NumberUtils.isNumeric(args[1])) {
				AuctionHouse.getInstance().getLocale().getMessage("general.notanumber").sendPrefixedMessage(player);
				return ReturnType.FAILURE;
			}

			final double price = Double.parseDouble(args[1]);

			AuctionHouse.getInstance().getDataManager().insertMinPriceAsync(new MinItemPrice(held.clone(), price), (error, inserted) -> {
				if (error == null) {
					AuctionHouse.getInstance().getMinItemPriceManager().addItem(inserted);
					AuctionHouse.getInstance().getLocale().getMessage("general.added min price")
							.processPlaceholder("item", AuctionAPI.getInstance().getItemName(inserted.getItemStack()))
							.processPlaceholder("price", AuctionAPI.getInstance().formatNumber(inserted.getPrice()))
							.sendPrefixedMessage(player);
				}
			});
		}

		return ReturnType.SUCCESS;
	}

	@Override
	public String getPermissionNode() {
		return "auctionhouse.cmd.minprice";
	}

	@Override
	public String getSyntax() {
		return AuctionHouse.getInstance().getLocale().getMessage("commands.syntax.min price").getMessage();
	}

	@Override
	public String getDescription() {
		return AuctionHouse.getInstance().getLocale().getMessage("commands.description.min price").getMessage();
	}

	@Override
	protected List<String> onTab(CommandSender sender, String... args) {
		if (args.length == 1) return Collections.singletonList("add");
		return null;
	}
}
