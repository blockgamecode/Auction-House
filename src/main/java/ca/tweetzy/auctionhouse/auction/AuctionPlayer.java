package ca.tweetzy.auctionhouse.auction;

import ca.tweetzy.auctionhouse.AuctionHouse;
import ca.tweetzy.auctionhouse.auction.enums.AuctionItemCategory;
import ca.tweetzy.auctionhouse.auction.enums.AuctionSaleType;
import ca.tweetzy.auctionhouse.auction.enums.AuctionSortType;
import ca.tweetzy.auctionhouse.settings.Settings;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * The current file has been created by Kiran Hart
 * Date Created: February 02 2021
 * Time Created: 6:26 p.m.
 * Usage of any code found within this class is prohibited unless given explicit permission otherwise
 */

@Getter
@Setter
@AllArgsConstructor
public class AuctionPlayer {

	private final UUID uuid;
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private WeakReference<Player> player;

	private AuctionSaleType selectedSaleType;
	private AuctionItemCategory selectedFilter;
	private AuctionSortType auctionSortType;
	private String currentSearchPhrase;
	private boolean showListingInfo;
	private long lastListedItem;

	private ItemStack itemBeingListed;
	private int assignedTaskId;

	public AuctionPlayer(UUID uuid) {
		this(uuid, null, AuctionSaleType.BOTH, AuctionItemCategory.ALL, AuctionSortType.RECENT, "", true, -1, null, -1);
	}

	public AuctionPlayer(Player player) {
		this(player.getUniqueId());
		this.player = new WeakReference<>(player);
	}

	public Player getPlayer() {
		return player == null ? null : player.get();
	}

	public void setPlayer(Player player) {
		this.player = new WeakReference<>(player);
	}

	public boolean isOnline() {
		final Player player = getPlayer();
		return player != null && player.isOnline();
	}

	public boolean canListItem() {
		if (Settings.LIST_ITEM_DELAY.getInt() == -1) {
			return true;
		}

		if (this.lastListedItem == -1 || System.currentTimeMillis() >= this.lastListedItem) {
			this.lastListedItem = System.currentTimeMillis() + 1000L * Settings.LIST_ITEM_DELAY.getInt();
			AuctionHouse.getInstance().getDataManager().updateAuctionPlayer(this, (error, success) -> {
				if (error == null && success)
					if (!Settings.DISABLE_PROFILE_UPDATE_MSG.getBoolean())
						AuctionHouse.getInstance().getLogger().info("Updating profile for player: " + Optional.ofNullable(player).map(WeakReference::get).map(Player::getName).orElse(uuid.toString()));

			});
			return true;
		}

		final Player onlinePlayer = getPlayer();
		if (onlinePlayer != null && onlinePlayer.isOnline()) {
			AuctionHouse.getInstance().getLocale().getMessage("general.wait_to_list").processPlaceholder("time", (this.lastListedItem - System.currentTimeMillis()) / 1000).sendPrefixedMessage(onlinePlayer);
		}

		return false;
	}

	public List<AuctionedItem> getItems(boolean getExpired) {
		List<AuctionedItem> items = new ArrayList<>();

		for (Map.Entry<UUID, AuctionedItem> entry : AuctionHouse.getInstance().getAuctionItemManager().getItems().entrySet()) {
			AuctionedItem auctionItem = entry.getValue();
			if (auctionItem.getOwner().equals(uuid) && auctionItem.isExpired() == getExpired && !AuctionHouse.getInstance().getAuctionItemManager().getGarbageBin().containsKey(auctionItem.getId())) {
				items.add(auctionItem);
			}
		}
		return items;
	}

	public void resetFilter() {
		this.selectedFilter = AuctionItemCategory.ALL;
		this.auctionSortType = AuctionSortType.RECENT;
		this.selectedSaleType = AuctionSaleType.BOTH;
		this.currentSearchPhrase = "";
	}


	public int getSellLimit() {
		final Player onlinePlayer = getPlayer();
		// fall back
		if (onlinePlayer == null)
			return Integer.MAX_VALUE;

		if (onlinePlayer.hasPermission("auctionhouse.maxsell.*")) return Integer.MAX_VALUE;
		for (int i = 1001; i > 0; i--) {
			if (onlinePlayer.hasPermission("auctionhouse.maxsell." + i)) return i;
		}
		return 0;
	}

	public boolean isAtSellLimit() {
		return getSellLimit() - 1 < getItems(false).size();
	}

	public int getAllowedSellTime(AuctionSaleType auctionSaleType) {
		int defaultTime = auctionSaleType == AuctionSaleType.USED_BIDDING_SYSTEM ? Settings.DEFAULT_AUCTION_LISTING_TIME.getInt() : Settings.DEFAULT_BIN_LISTING_TIME.getInt();

		final Player onlinePlayer = getPlayer();
		if (onlinePlayer == null) {
			return defaultTime;
		}

		List<Integer> possibleTimes = new ArrayList<>();
		Settings.AUCTION_TIME.getStringList().forEach(line -> {
			String[] split = line.split(":");
			if (onlinePlayer.hasPermission("auctionhouse.time." + split[0])) {
				possibleTimes.add(Integer.parseInt(split[1]));
			}
		});

		return possibleTimes.size() <= 0 ? defaultTime : Math.max(defaultTime, Collections.max(possibleTimes));
	}

}
