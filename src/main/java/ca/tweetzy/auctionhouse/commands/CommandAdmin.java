package ca.tweetzy.auctionhouse.commands;

import ca.tweetzy.auctionhouse.AuctionHouse;
import ca.tweetzy.auctionhouse.api.AuctionAPI;
import ca.tweetzy.auctionhouse.auction.AuctionPlayer;
import ca.tweetzy.auctionhouse.guis.GUIAuctionHouse;
import ca.tweetzy.auctionhouse.guis.GUISellItem;
import ca.tweetzy.auctionhouse.guis.admin.GUIAdminExpired;
import ca.tweetzy.auctionhouse.guis.admin.GUIAdminLogs;
import ca.tweetzy.auctionhouse.helpers.PlayerHelper;
import ca.tweetzy.auctionhouse.settings.Settings;
import ca.tweetzy.core.commands.AbstractCommand;
import ca.tweetzy.core.compatibility.CompatibleHand;
import ca.tweetzy.core.compatibility.XMaterial;
import ca.tweetzy.core.utils.PlayerUtils;
import ca.tweetzy.core.utils.TextUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The current file has been created by Kiran Hart
 * Date Created: May 23 2021
 * Time Created: 12:14 p.m.
 * Usage of any code found within this class is prohibited unless given explicit permission otherwise
 */
public class CommandAdmin extends AbstractCommand {

	public CommandAdmin() {
		super(CommandType.CONSOLE_OK, "admin");
	}

	@Override
	protected ReturnType runCommand(CommandSender sender, String... args) {
		if (args.length < 1) return ReturnType.FAILURE;
		if (AuctionAPI.tellMigrationStatus(sender)) return ReturnType.FAILURE;

		switch (args[0].toLowerCase()) {
			case "logs":
				if (!(sender instanceof Player)) break;
				Player player = (Player) sender;
				if (!player.hasPermission("auctionhouse.cmd.admin.logs")) return ReturnType.FAILURE;

				AuctionHouse.getInstance().getDataManager().getAdminLogs((error, logs) -> {
					if (error == null)
						AuctionHouse.newChain().sync(() -> AuctionHouse.getInstance().getGuiManager().showGUI(player, new GUIAdminLogs(player, logs))).execute();
					else
						error.printStackTrace();
				});
				break;
			case "viewexpired":
				if (!(sender instanceof Player)) break;
				player = (Player) sender;
				if (!player.hasPermission("auctionhouse.cmd.admin.viewexpired")) return ReturnType.FAILURE;


				if (args.length < 2) return ReturnType.FAILURE;
				OfflinePlayer target = Bukkit.getPlayerExact(args[1]);

				if (target == null) {
					for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
						if (offlinePlayer.getName() != null && offlinePlayer.getName().equalsIgnoreCase(args[1])) {
							target = offlinePlayer;
						}
					}
				}

				if (target == null) {
					AuctionHouse.getInstance().getLocale().getMessage("general.playernotfound").processPlaceholder("player", args[1]).sendPrefixedMessage(sender);
					return ReturnType.FAILURE;
				}

				AuctionHouse.getInstance().getGuiManager().showGUI(player, new GUIAdminExpired(player, target));

				break;
			case "endall":
				if (!sender.hasPermission("auctionhouse.cmd.admin.endall")) return ReturnType.FAILURE;
				for (UUID id : AuctionHouse.getInstance().getAuctionItemManager().getItems().keySet()) {
					AuctionHouse.getInstance().getAuctionItemManager().getItems().get(id).setExpired(true);
				}
				AuctionHouse.getInstance().getLocale().getMessage("general.endedallauctions").sendPrefixedMessage(sender);
				break;
			case "relistall":
				if (!sender.hasPermission("auctionhouse.cmd.admin.relistall")) return ReturnType.FAILURE;
				for (UUID id : AuctionHouse.getInstance().getAuctionItemManager().getItems().keySet()) {
					if (AuctionHouse.getInstance().getAuctionItemManager().getItems().get(id).isExpired()) {
						int relistTime = args.length == 1 ? AuctionHouse.getInstance().getAuctionItemManager().getItems().get(id).isBidItem() ? Settings.DEFAULT_AUCTION_LISTING_TIME.getInt() : Settings.DEFAULT_BIN_LISTING_TIME.getInt() : Integer.parseInt(args[1]);

						AuctionHouse.getInstance().getAuctionItemManager().getItems().get(id).setExpiresAt(System.currentTimeMillis() + 1000L * relistTime);
						AuctionHouse.getInstance().getAuctionItemManager().getItems().get(id).setExpired(false);
					}
				}
				AuctionHouse.getInstance().getLocale().getMessage("general.relisteditems").sendPrefixedMessage(sender);
				break;
			case "clearall":
				if (!sender.hasPermission("auctionhouse.cmd.admin.clearall")) return ReturnType.FAILURE;
				// Don't tell ppl that this exists
				AuctionHouse.getInstance().getAuctionItemManager().getItems().clear();
			case "opensell":
				if (args.length < 2) return ReturnType.FAILURE;
				if (!sender.hasPermission("auctionhouse.cmd.admin.opensell")) return ReturnType.FAILURE;

				player = PlayerUtils.findPlayer(args[1]);
				if (player == null) return ReturnType.FAILURE;

				ItemStack itemToSell = PlayerHelper.getHeldItem(player).clone();

				if (itemToSell.getType() == XMaterial.AIR.parseMaterial() && Settings.SELL_MENU_REQUIRES_USER_TO_HOLD_ITEM.getBoolean()) {
					AuctionHouse.getInstance().getLocale().getMessage("general.air").sendPrefixedMessage(player);
					return ReturnType.FAILURE;
				} else {
					AuctionHouse.getInstance().getGuiManager().showGUI(player, new GUISellItem(AuctionHouse.getInstance().getAuctionPlayerManager().getPlayer(player.getUniqueId()), itemToSell));
					AuctionHouse.getInstance().getAuctionPlayerManager().addItemToSellHolding(player.getUniqueId(), itemToSell);
					PlayerUtils.takeActiveItem(player, CompatibleHand.MAIN_HAND, itemToSell.getAmount());
				}
				break;
			case "open":
				if (args.length < 2) return ReturnType.FAILURE;
				if (!sender.hasPermission("auctionhouse.cmd.admin.open")) return ReturnType.FAILURE;

				player = PlayerUtils.findPlayer(args[1]);
				if (player == null) return ReturnType.FAILURE;

				if (CommandMiddleware.handle(player) == ReturnType.FAILURE) return ReturnType.FAILURE;

				if (AuctionHouse.getInstance().getAuctionPlayerManager().getPlayer(player.getUniqueId()) == null) {
					AuctionHouse.getInstance().getLocale().newMessage(TextUtils.formatText("&cCould not find auction player instance for&f: &e" + player.getName() + "&c creating one now.")).sendPrefixedMessage(Bukkit.getConsoleSender());
					AuctionHouse.getInstance().getAuctionPlayerManager().addPlayer(new AuctionPlayer(player));
				}

				AuctionHouse.getInstance().getGuiManager().showGUI(player, new GUIAuctionHouse(AuctionHouse.getInstance().getAuctionPlayerManager().getPlayer(player.getUniqueId())));
				break;
		}

		return ReturnType.SUCCESS;
	}

	@Override
	protected List<String> onTab(CommandSender sender, String... args) {
		if (args.length == 1) return Arrays.asList("endall", "relistall", "logs", "viewexpired", "open");
		if (args.length == 2 && args[0].equalsIgnoreCase("relistAll")) return Arrays.asList("1", "2", "3", "4", "5");
		if (args.length == 2 && (args[0].equalsIgnoreCase("viewexpired") || args[0].equalsIgnoreCase("open"))) return Bukkit.getOnlinePlayers().stream().map(OfflinePlayer::getName).collect(Collectors.toList());
		return null;
	}

	@Override
	public String getPermissionNode() {
		return "auctionhouse.cmd.admin";
	}

	@Override
	public String getSyntax() {
		return "admin <endall|relistAll> [value]";
	}

	@Override
	public String getDescription() {
		return "Admin options for auction house.";
	}
}
