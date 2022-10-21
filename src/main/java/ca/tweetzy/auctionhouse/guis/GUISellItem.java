package ca.tweetzy.auctionhouse.guis;

import ca.tweetzy.auctionhouse.AuctionHouse;
import ca.tweetzy.auctionhouse.api.AuctionAPI;
import ca.tweetzy.auctionhouse.auction.AuctionPlayer;
import ca.tweetzy.auctionhouse.auction.AuctionedItem;
import ca.tweetzy.auctionhouse.auction.enums.AuctionSaleType;
import ca.tweetzy.auctionhouse.guis.confirmation.GUIListingConfirm;
import ca.tweetzy.auctionhouse.helpers.ConfigurationItemHelper;
import ca.tweetzy.auctionhouse.helpers.MaterialCategorizer;
import ca.tweetzy.auctionhouse.settings.Settings;
import ca.tweetzy.core.compatibility.XMaterial;
import ca.tweetzy.core.gui.events.GuiClickEvent;
import ca.tweetzy.core.hooks.EconomyManager;
import ca.tweetzy.core.input.ChatPrompt;
import ca.tweetzy.core.input.PlayerChatInput;
import ca.tweetzy.core.utils.NumberUtils;
import ca.tweetzy.core.utils.PlayerUtils;
import ca.tweetzy.core.utils.TextUtils;
import lombok.Getter;
import lombok.NonNull;
import org.bukkit.ChatColor;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The current file has been created by Kiran Hart
 * Date Created: June 27 2021
 * Time Created: 10:28 a.m.
 * Usage of any code found within this class is prohibited unless given explicit permission otherwise
 */

public class GUISellItem extends AbstractPlaceholderGui {

	private final AuctionPlayer auctionPlayer;
	@Getter
	private ItemStack itemToBeListed;

	private Double buyNowPrice;
	private Double bidStartPrice;
	private Double bidIncrementPrice;
	private boolean isBiddingItem;
	private boolean isAllowingBuyNow;
	private int auctionTime;

	public GUISellItem(AuctionPlayer auctionPlayer, ItemStack itemToBeListed, double buyNowPrice, double bidStartPrice, double bidIncrementPrice, boolean isBiddingItem, boolean isAllowingBuyNow, int auctionTime) {
		super(auctionPlayer);
		this.auctionPlayer = auctionPlayer;
		this.itemToBeListed = itemToBeListed;
		this.buyNowPrice = buyNowPrice;
		this.bidStartPrice = bidStartPrice;
		this.bidIncrementPrice = bidIncrementPrice;
		this.isBiddingItem = isBiddingItem;
		this.isAllowingBuyNow = isAllowingBuyNow;
		this.auctionTime = auctionTime;
		setTitle(TextUtils.formatText(Settings.GUI_SELL_TITLE.getString()));
		setDefaultItem(Settings.GUI_SELL_BG_ITEM.getMaterial().parseItem());
		setUseLockedCells(true);
		setAllowDrops(false);
		setAllowClose(false);
		setRows(Settings.ALLOW_PLAYERS_TO_DEFINE_AUCTION_TIME.getBoolean() ? 6 : 5);

		setOnOpen(open -> {
			// Check if they are already using a sell gui
			if (ChatPrompt.isRegistered(open.player)) {
				AuctionHouse.getInstance().getLocale().getMessage("general.finishenteringprice").sendPrefixedMessage(open.player);
				open.gui.close();
			}

			if (AuctionHouse.getInstance().getAuctionBanManager().checkAndHandleBan(open.player)) {
				open.gui.close();
				return;
			}

			ItemStack held = AuctionHouse.getInstance().getAuctionPlayerManager().getSellHolding().get(open.player.getUniqueId());
			if (held == null) {
				setAcceptsItems(true);
			} else {
				setAcceptsItems(held.getType() == XMaterial.AIR.parseMaterial());
			}

			AuctionHouse.getInstance().getAuctionPlayerManager().addToUsingSellGUI(open.player.getUniqueId(), this);
		});

		setOnClose(close -> {
			if (!AuctionHouse.getInstance().getAuctionPlayerManager().getUsingSellGUI().containsKey(close.player.getUniqueId())) {
				ItemStack toGiveBack = AuctionHouse.getInstance().getAuctionPlayerManager().getSellHolding().get(close.player.getUniqueId());
				PlayerUtils.giveItem(close.player, toGiveBack); // this could give them air

				try {
					if (toGiveBack.getType() == XMaterial.AIR.parseMaterial()) {
						if (getItem(1, 4) != null && getItem(1, 4).getType() != XMaterial.AIR.parseMaterial()) {
							PlayerUtils.giveItem(close.player, getItem(1, 4));
						}
					}
				} catch (NullPointerException ignored) {
					// stfu
				}

				AuctionHouse.getInstance().getAuctionPlayerManager().removeItemFromSellHolding(close.player.getUniqueId());
				if (Settings.SELL_MENU_CLOSE_SENDS_TO_LISTING.getBoolean()) {
					close.manager.showGUI(close.player, new GUIAuctionHouse(this.auctionPlayer));
				}
			}
		});

		if (Settings.FORCE_AUCTION_USAGE.getBoolean()) {
			this.isBiddingItem = true;
		}

		if (!Settings.ALLOW_USAGE_OF_BUY_NOW_SYSTEM.getBoolean()) {
			this.isBiddingItem = true;
		}

		setUnlocked(1, 4);
		setUnlockedRange(54, 89);
		draw();
	}

	public GUISellItem(AuctionPlayer auctionPlayer, ItemStack itemToBeListed) {
		this(auctionPlayer, itemToBeListed, Settings.MIN_AUCTION_PRICE.getDouble(), Settings.MIN_AUCTION_START_PRICE.getDouble(), Settings.MIN_AUCTION_INCREMENT_PRICE.getDouble(), false, true, auctionPlayer.getAllowedSellTime(AuctionSaleType.WITHOUT_BIDDING_SYSTEM));
	}

	private void draw() {
		reset();

		// the draw item that is being listed
		setButton(1, 4, this.itemToBeListed, e -> {
			if (e.clickType == ClickType.RIGHT || e.clickType == ClickType.NUMBER_KEY) e.event.setCancelled(true);
			// Is the user selling with an item in hand?
			if (AuctionHouse.getInstance().getAuctionPlayerManager().getSellHolding().containsKey(e.player.getUniqueId())) {
				if (AuctionHouse.getInstance().getAuctionPlayerManager().getSellHolding().get(e.player.getUniqueId()).getType() != XMaterial.AIR.parseMaterial()) {
					e.event.setCancelled(true);
				}
			}
			this.itemToBeListed = e.clickedItem;
		});

		if (Settings.ALLOW_PLAYERS_TO_DEFINE_AUCTION_TIME.getBoolean()) {
			long[] times = AuctionAPI.getInstance().getRemainingTimeValues(this.auctionTime);

			setButton(4, 2, ConfigurationItemHelper.createConfigurationItem(Settings.GUI_SELL_ITEMS_LIST_TIME_ITEM.getString(), Settings.GUI_SELL_ITEMS_LIST_TIME_NAME.getString(), Settings.GUI_SELL_ITEMS_LIST_TIME_LORE.getStringList(), new HashMap<String, Object>() {{
				put("%remaining_days%", times[0]);
				put("%remaining_hours%", times[1]);
				put("%remaining_minutes%", times[2]);
				put("%remaining_seconds%", times[3]);
			}}), ClickType.LEFT, e -> {
				e.gui.close();

				// work-around to clicking closeable item with item on cursor
				if (handleClosableCursorItem(e)) {
					return;
				}

				PlayerChatInput.PlayerChatInputBuilder<Long> builder = new PlayerChatInput.PlayerChatInputBuilder<>(AuctionHouse.getInstance(), e.player);
				builder.isValidInput((p, str) -> {
					String[] parts = ChatColor.stripColor(str).split(" ");
					if (parts.length == 2) {
						if (NumberUtils.isInt(parts[0]) && Arrays.asList("second", "minute", "hour", "day", "week", "month", "year", "seconds", "minutes", "hours", "days", "weeks", "months", "years").contains(parts[1].toLowerCase())) {
							return AuctionAPI.toTicks(str) <= Settings.MAX_CUSTOM_DEFINED_TIME.getInt();
						}
					}
					return false;
				});
				builder.sendValueMessage(TextUtils.formatText(AuctionHouse.getInstance().getLocale().getMessage("prompts.enter listing time").getMessage()));
				builder.invalidInputMessage(TextUtils.formatText(AuctionHouse.getInstance().getLocale().getMessage("prompts.enter valid listing time").getMessage()));
				builder.toCancel("cancel");
				builder.onCancel(p -> reopen(e));
				builder.setValue((p, value) -> AuctionAPI.toTicks(ChatColor.stripColor(value)));
				builder.onFinish((p, value) -> {
					this.auctionTime = value.intValue();
					reopen(e);
				});

				builder.onPlayerDiconnect(() -> PlayerUtils.giveItem(e.player, getItemAndReset()));

				PlayerChatInput<Long> input = builder.build();
				input.start();
			});
		}

		if (Settings.ALLOW_USAGE_OF_BUY_NOW_SYSTEM.getBoolean() && this.isAllowingBuyNow) {
			setButton(3, 1, ConfigurationItemHelper.createConfigurationItem(Settings.GUI_SELL_ITEMS_BUY_NOW_ITEM.getString(), Settings.GUI_SELL_ITEMS_BUY_NOW_NAME.getString(), Settings.GUI_SELL_ITEMS_BUY_NOW_LORE.getStringList(), new HashMap<String, Object>() {{
				put("%buy_now_price%", AuctionAPI.getInstance().formatNumber(buyNowPrice));
			}}), ClickType.LEFT, e -> {
				setAllowClose(true);

				e.gui.close();

				// work-around to clicking closeable item with item on cursor
				if (handleClosableCursorItem(e)) {
					return;
				}

				PlayerChatInput.PlayerChatInputBuilder<Double> builder = new PlayerChatInput.PlayerChatInputBuilder<>(AuctionHouse.getInstance(), e.player);
				builder.isValidInput((p, str) -> {
					if (validateChatNumber(str, Settings.MIN_AUCTION_PRICE.getDouble(), false) && validateChatNumber(str, Settings.MAX_AUCTION_PRICE.getDouble(), true)) {
						return !this.isAllowingBuyNow || !this.isBiddingItem || !Settings.BASE_PRICE_MUST_BE_HIGHER_THAN_BID_START.getBoolean() || !(Double.parseDouble(ChatColor.stripColor(str)) < this.bidStartPrice);
					}
					return false;
				});

				builder.sendValueMessage(TextUtils.formatText(AuctionHouse.getInstance().getLocale().getMessage("prompts.enter new buy now price").getMessage()));
				builder.toCancel("cancel");
				builder.onCancel(p -> reopen(e));
				builder.setValue((p, value) -> Double.parseDouble(ChatColor.stripColor(value)));
				builder.onFinish((p, value) -> {
					this.buyNowPrice = value;
					reopen(e);
				});

				builder.onPlayerDiconnect(() -> PlayerUtils.giveItem(e.player, getItemAndReset()));

				PlayerChatInput<Double> input = builder.build();
				input.start();
			});
		}

		if (this.isBiddingItem) {
			setButton(3, Settings.FORCE_CUSTOM_BID_AMOUNT.getBoolean() ? 2 : Settings.ALLOW_USAGE_OF_BUY_NOW_SYSTEM.getBoolean() ? 2 : 1, ConfigurationItemHelper.createConfigurationItem(Settings.GUI_SELL_ITEMS_STARTING_BID_ITEM.getString(), Settings.GUI_SELL_ITEMS_STARTING_BID_NAME.getString(), Settings.GUI_SELL_ITEMS_STARTING_BID_LORE.getStringList(), new HashMap<String, Object>() {{
				put("%starting_bid_price%", AuctionAPI.getInstance().formatNumber(bidStartPrice));
			}}), ClickType.LEFT, e -> {
				setAllowClose(true);

				e.gui.close();

				// work-around to clicking closeable item with item on cursor
				if (handleClosableCursorItem(e)) {
					return;
				}

				PlayerChatInput.PlayerChatInputBuilder<Double> builder = new PlayerChatInput.PlayerChatInputBuilder<>(AuctionHouse.getInstance(), e.player);
				builder.isValidInput((p, str) -> {
					return validateChatNumber(str, Settings.MIN_AUCTION_START_PRICE.getDouble(), false) && validateChatNumber(str, Settings.MAX_AUCTION_START_PRICE.getDouble(), true);
				});

				builder.sendValueMessage(TextUtils.formatText(AuctionHouse.getInstance().getLocale().getMessage("prompts.enter new starting bid").getMessage()));
				builder.toCancel("cancel");
				builder.onCancel(p -> reopen(e));
				builder.setValue((p, value) -> Double.parseDouble(ChatColor.stripColor(value)));
				builder.onFinish((p, value) -> {
					this.bidStartPrice = value;
					reopen(e);
				});

				builder.onPlayerDiconnect(() -> PlayerUtils.giveItem(e.player, getItemAndReset()));

				PlayerChatInput<Double> input = builder.build();
				input.start();
			});

			if (!Settings.FORCE_CUSTOM_BID_AMOUNT.getBoolean()) {
				setButton(3, 3, ConfigurationItemHelper.createConfigurationItem(Settings.GUI_SELL_ITEMS_BID_INC_ITEM.getString(), Settings.GUI_SELL_ITEMS_BID_INC_NAME.getString(), Settings.GUI_SELL_ITEMS_BID_INC_LORE.getStringList(), new HashMap<String, Object>() {{
					put("%bid_increment_price%", AuctionAPI.getInstance().formatNumber(bidIncrementPrice));
				}}), ClickType.LEFT, e -> {
					setAllowClose(true);

					e.gui.close();

					// work-around to clicking closeable item with item on cursor
					if (handleClosableCursorItem(e)) {
						return;
					}

					PlayerChatInput.PlayerChatInputBuilder<Double> builder = new PlayerChatInput.PlayerChatInputBuilder<>(AuctionHouse.getInstance(), e.player);
					builder.isValidInput((p, str) -> {
						return validateChatNumber(str, Settings.MIN_AUCTION_INCREMENT_PRICE.getDouble(), false) && validateChatNumber(str, Settings.MAX_AUCTION_INCREMENT_PRICE.getDouble(), true);
					});

					builder.sendValueMessage(TextUtils.formatText(AuctionHouse.getInstance().getLocale().getMessage("prompts.enter new bid increment").getMessage()));
					builder.toCancel("cancel");
					builder.onCancel(p -> reopen(e));
					builder.setValue((p, value) -> Double.parseDouble(ChatColor.stripColor(value)));
					builder.onFinish((p, value) -> {
						this.bidIncrementPrice = value;
						reopen(e);
					});

					builder.onPlayerDiconnect(() -> PlayerUtils.giveItem(e.player, getItemAndReset()));

					PlayerChatInput<Double> input = builder.build();
					input.start();
				});
			}

			if (Settings.ALLOW_USAGE_OF_BUY_NOW_SYSTEM.getBoolean()) {
				setButton(3, 6, ConfigurationItemHelper.createConfigurationItem(this.isAllowingBuyNow ? Settings.GUI_SELL_ITEMS_BUY_NOW_ENABLED_ITEM.getString() : Settings.GUI_SELL_ITEMS_BUY_NOW_DISABLED_ITEM.getString(), this.isAllowingBuyNow ? Settings.GUI_SELL_ITEMS_BUY_NOW_ENABLED_NAME.getString() : Settings.GUI_SELL_ITEMS_BUY_NOW_DISABLED_NAME.getString(), this.isAllowingBuyNow ? Settings.GUI_SELL_ITEMS_BUY_NOW_ENABLED_LORE.getStringList() : Settings.GUI_SELL_ITEMS_BUY_NOW_DISABLED_LORE.getStringList(), null), ClickType.LEFT, e -> {
					this.isAllowingBuyNow = !this.isAllowingBuyNow;
					draw();
				});
			}
		}

		if (Settings.ALLOW_USAGE_OF_BID_SYSTEM.getBoolean() || !Settings.ALLOW_USAGE_OF_BUY_NOW_SYSTEM.getBoolean()) {
			setButton(3, 5, ConfigurationItemHelper.createConfigurationItem(this.isBiddingItem ? Settings.GUI_SELL_ITEMS_BIDDING_ENABLED_ITEM.getString() : Settings.GUI_SELL_ITEMS_BIDDING_DISABLED_ITEM.getString(), this.isBiddingItem ? Settings.GUI_SELL_ITEMS_BIDDING_ENABLED_NAME.getString() : Settings.GUI_SELL_ITEMS_BIDDING_DISABLED_NAME.getString(), this.isBiddingItem ? Settings.GUI_SELL_ITEMS_BIDDING_ENABLED_LORE.getStringList() : Settings.GUI_SELL_ITEMS_BIDDING_DISABLED_LORE.getStringList(), null), e -> {
				if (!Settings.FORCE_AUCTION_USAGE.getBoolean()) {
					if (Settings.ALLOW_USAGE_OF_BUY_NOW_SYSTEM.getBoolean()) {
						this.isBiddingItem = !this.isBiddingItem;
					}
				}
				draw();
			});
		}

		setButton(3, 7, ConfigurationItemHelper.createConfigurationItem(Settings.GUI_SELL_ITEMS_CONFIRM_LISTING_ITEM.getString(), Settings.GUI_SELL_ITEMS_CONFIRM_LISTING_NAME.getString(), Settings.GUI_SELL_ITEMS_CONFIRM_LISTING_LORE.getStringList(), null), e -> {
			// if the item in the sell slot is null then stop the listing
			if (itemToBeListed == null || itemToBeListed.getType() == XMaterial.AIR.parseMaterial()) return;

			if (Settings.MAKE_BLOCKED_ITEMS_A_WHITELIST.getBoolean()) {
				if (!Settings.BLOCKED_ITEMS.getStringList().contains(this.itemToBeListed.getType().name())) {
					AuctionHouse.getInstance().getLocale().getMessage("general.blockeditem").processPlaceholder("item", this.itemToBeListed.getType().name()).sendPrefixedMessage(e.player);
					return;
				}
			} else {
				if (Settings.BLOCKED_ITEMS.getStringList().contains(this.itemToBeListed.getType().name())) {
					AuctionHouse.getInstance().getLocale().getMessage("general.blockeditem").processPlaceholder("item", this.itemToBeListed.getType().name()).sendPrefixedMessage(e.player);
					return;
				}
			}

			boolean blocked = false;

			String itemName = ChatColor.stripColor(AuctionAPI.getInstance().getItemName(this.itemToBeListed).toLowerCase());
			List<String> itemLore = AuctionAPI.getInstance().getItemLore(this.itemToBeListed).stream().map(line -> ChatColor.stripColor(line.toLowerCase())).collect(Collectors.toList());

			// Check for blocked names and lore
			for (String s : Settings.BLOCKED_ITEM_NAMES.getStringList()) {
				if (AuctionAPI.getInstance().match(s, itemName)) {
					AuctionHouse.getInstance().getLocale().getMessage("general.blockedname").sendPrefixedMessage(e.player);
					blocked = true;
				}
			}

			if (!itemLore.isEmpty() && !blocked) {
				for (String s : Settings.BLOCKED_ITEM_LORES.getStringList()) {
					for (String line : itemLore) {
						if (AuctionAPI.getInstance().match(s, line)) {
							AuctionHouse.getInstance().getLocale().getMessage("general.blockedlore").sendPrefixedMessage(e.player);
							blocked = true;
						}
					}
				}
			}

			if (blocked) return;

			// are they even allowed to sell more items
			if (this.auctionPlayer.isAtSellLimit()) {
				AuctionHouse.getInstance().getLocale().getMessage("general.sellinglimit").sendPrefixedMessage(e.player);
				return;
			}

			if (Settings.ALLOW_USAGE_OF_BUY_NOW_SYSTEM.getBoolean() && this.isAllowingBuyNow && this.isBiddingItem && this.buyNowPrice <= this.bidStartPrice && Settings.BASE_PRICE_MUST_BE_HIGHER_THAN_BID_START.getBoolean()) {
				AuctionHouse.getInstance().getLocale().getMessage("pricing.basepricetoolow").sendPrefixedMessage(e.player);
				return;
			}

			if (Settings.TAX_ENABLED.getBoolean() && Settings.TAX_CHARGE_LISTING_FEE.getBoolean()) {
				final double listingFee = AuctionAPI.getInstance().calculateListingFee(this.isBiddingItem && !isAllowingBuyNow || !Settings.ALLOW_USAGE_OF_BUY_NOW_SYSTEM.getBoolean() ? -1 : buyNowPrice);

				if (!EconomyManager.hasBalance(e.player, listingFee)) {
					AuctionHouse.getInstance().getLocale().getMessage("auction.tax.cannotpaylistingfee").processPlaceholder("price", listingFee).sendPrefixedMessage(e.player);
					return;
				}
			}

			if (Settings.ASK_FOR_LISTING_CONFIRMATION.getBoolean()) {
				if (!this.auctionPlayer.canListItem()) {
					return;
				}

				setAllowClose(true);

				// TODO clean up is monstrosity
				AuctionedItem auctionedItem = new AuctionedItem();
				auctionedItem.setId(UUID.randomUUID());
				auctionedItem.setOwner(e.player.getUniqueId());
				auctionedItem.setHighestBidder(e.player.getUniqueId());
				auctionedItem.setOwnerName(e.player.getName());
				auctionedItem.setHighestBidderName(e.player.getName());
				auctionedItem.setItem(this.itemToBeListed.clone());
				auctionedItem.setCategory(MaterialCategorizer.getMaterialCategory(this.itemToBeListed.clone()));
				auctionedItem.setExpiresAt(System.currentTimeMillis() + 1000L * this.auctionTime);
				auctionedItem.setBidItem(isBiddingItem);
				auctionedItem.setExpired(false);

				auctionedItem.setBasePrice(Settings.ROUND_ALL_PRICES.getBoolean() ? Math.round(this.isAllowingBuyNow ? buyNowPrice : -1) : this.isAllowingBuyNow ? buyNowPrice : -1);
				auctionedItem.setBidStartingPrice(Settings.ROUND_ALL_PRICES.getBoolean() ? Math.round(this.isBiddingItem ? this.bidStartPrice : 0) : this.isBiddingItem ? this.bidStartPrice : 0);
				auctionedItem.setBidIncrementPrice(Settings.ROUND_ALL_PRICES.getBoolean() ? Math.round(this.isBiddingItem ? this.bidIncrementPrice != null ? this.bidIncrementPrice : Settings.MIN_AUCTION_INCREMENT_PRICE.getDouble() : 0) : this.isBiddingItem ? this.bidIncrementPrice != null ? this.bidIncrementPrice : Settings.MIN_AUCTION_INCREMENT_PRICE.getDouble() : 0);
				auctionedItem.setCurrentPrice(Settings.ROUND_ALL_PRICES.getBoolean() ? Math.round(this.isBiddingItem ? this.bidStartPrice : this.buyNowPrice <= -1 ? this.bidStartPrice : this.buyNowPrice) : this.isBiddingItem ? this.bidStartPrice : this.buyNowPrice <= -1 ? this.bidStartPrice : this.buyNowPrice);

				auctionedItem.setListedWorld(e.player.getWorld().getName());
				auctionedItem.setInfinite(false);
				auctionedItem.setAllowPartialBuy(false);

				AuctionHouse.getInstance().getGuiManager().showGUI(e.player, new GUIListingConfirm(e.player, auctionedItem, result -> {
					if (!result) {
						e.player.closeInventory();
						if (!this.acceptsItems || this.itemToBeListed != null && this.itemToBeListed.getType() != XMaterial.AIR.parseMaterial())
							PlayerUtils.giveItem(e.player, this.itemToBeListed);
						return;
					}

					AuctionAPI.getInstance().listAuction(
							e.player,
							this.itemToBeListed.clone(),
							this.itemToBeListed.clone(),
							this.auctionTime,
							/* buy now price */ this.isAllowingBuyNow ? this.buyNowPrice : -1,
							/* start bid price */ isBiddingItem ? this.bidStartPrice : !this.isAllowingBuyNow ? this.buyNowPrice : 0,
							/* bid inc price */ isBiddingItem ? this.bidIncrementPrice != null ? this.bidIncrementPrice : Settings.MIN_AUCTION_INCREMENT_PRICE.getDouble() : 0,
							/* current price */ isBiddingItem ? this.bidStartPrice : this.buyNowPrice <= -1 ? this.bidStartPrice : this.buyNowPrice,
							isBiddingItem || !this.isAllowingBuyNow,
							false,
							false,
							false,
							false
					);

					e.player.closeInventory();
					if (Settings.OPEN_MAIN_AUCTION_HOUSE_AFTER_MENU_LIST.getBoolean()) {
						e.manager.showGUI(e.player, new GUIAuctionHouse(this.auctionPlayer));
					}
				}));

			} else {
				if (!this.auctionPlayer.canListItem()) {
					return;
				}

				AuctionAPI.getInstance().listAuction(
						e.player,
						this.itemToBeListed.clone(),
						this.itemToBeListed.clone(),
						this.auctionTime,
						this.isBiddingItem && !isAllowingBuyNow || !Settings.ALLOW_USAGE_OF_BUY_NOW_SYSTEM.getBoolean() ? -1 : buyNowPrice,
						this.isBiddingItem ? bidStartPrice : 0,
						Settings.FORCE_CUSTOM_BID_AMOUNT.getBoolean() ? 1 : this.isBiddingItem ? bidIncrementPrice : 0,
						this.isBiddingItem ? bidStartPrice : buyNowPrice,
						this.isBiddingItem,
						false,
						false
				);

				AuctionHouse.getInstance().getAuctionPlayerManager().removeItemFromSellHolding(e.player.getUniqueId());
				AuctionHouse.getInstance().getAuctionPlayerManager().removeFromUsingSellGUI(e.player.getUniqueId());
				setAllowClose(true);
				e.gui.close();

				if (Settings.OPEN_MAIN_AUCTION_HOUSE_AFTER_MENU_LIST.getBoolean()) {
					e.manager.showGUI(e.player, new GUIAuctionHouse(this.auctionPlayer));
				}
			}
		});

		setButton(3, 4, ConfigurationItemHelper.createConfigurationItem(Settings.GUI_CLOSE_BTN_ITEM.getString(), Settings.GUI_CLOSE_BTN_NAME.getString(), Settings.GUI_CLOSE_BTN_LORE.getStringList(), null), e -> {
			AuctionHouse.getInstance().getAuctionPlayerManager().getUsingSellGUI().remove(e.player.getUniqueId());
			setAllowClose(true);
			e.gui.close();

			if (e.cursor != null)
				e.player.getInventory().addItem(e.cursor);
		});

	}

	private ItemStack getItemAndReset() {
		final ItemStack buf = itemToBeListed;
		this.itemToBeListed = null;
		return buf;
	}

	private boolean handleClosableCursorItem(@NonNull final GuiClickEvent e) {
		if (e.cursor != null && getItem(1, 4) == null) {
//			AuctionHouse.getInstance().getAuctionPlayerManager().addItemToSellHolding(player.getUniqueId(), e.cursor);
//			this.itemToBeListed = e.cursor.clone();
			PlayerUtils.giveItem(e.player, e.cursor.clone());
			return true;
		}

		return false;
	}

	private boolean validateChatNumber(String input, double requirement, boolean checkMax) {
		String val = ChatColor.stripColor(input);
		if (checkMax)
			return val != null && val.length() != 0 && NumberUtils.isDouble(val) && Double.parseDouble(val) <= requirement;
		return val != null && val.length() != 0 && NumberUtils.isDouble(val) && Double.parseDouble(val) >= requirement;
	}

	private void reopen(GuiClickEvent e) {
		e.manager.showGUI(e.player, new GUISellItem(this.auctionPlayer, this.itemToBeListed, this.buyNowPrice, this.bidStartPrice, this.bidIncrementPrice, this.isBiddingItem, this.isAllowingBuyNow, this.auctionTime));
	}
}
