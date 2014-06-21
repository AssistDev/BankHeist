package me.assist.bankheist;

import java.io.IOException;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class BankHeist extends JavaPlugin {

	private Economy economy;

	private HashMap<UUID, Double> participants;
	private Heist state;

	public void onEnable() {
		if (getServer().getPluginManager().getPlugin("Vault") != null) {
			getLogger().info("Found Vault, hooking...");

			if (setupEconomy()) {
				getLogger().info("Succesfully hooked to Vault!");
			} else {
				getLogger().severe("Unable to hook to Vault, disabling plugin...");
				getServer().getPluginManager().disablePlugin(this);
				return;
			}

		} else {
			getLogger().severe("Vault not found, disabling plugin...");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		try {
			Metrics metrics = new Metrics(this);
			metrics.start();
		} catch (IOException e) {
			getLogger().severe("Failed to sumbit stats.");
		}

		participants = new HashMap<UUID, Double>();
		state = Heist.AVAILABLE;
	}

	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equals("bankheist")) {
			if (!(sender instanceof Player))
				return true;

			Player p = (Player) sender;

			if (args.length == 1) {
				if (state != Heist.RUNNING) {
					if (state != Heist.IDLE) {
						if (!participants.containsKey(p.getUniqueId())) {
							double bid = toDouble(args[0]);

							if (bid > 0) {
								double bal = economy.getBalance(p);

								if (bal >= bid) {
									economy.withdrawPlayer(p, bid);

									if (state == Heist.AVAILABLE) {
										getServer().broadcastMessage(ChatColor.DARK_GREEN + p.getDisplayName() + ChatColor.GREEN + " has started planning a bankheist!");
										getServer().broadcastMessage(ChatColor.GREEN + "Type " + ChatColor.DARK_GREEN + "/bankheist x" + ChatColor.GREEN + " to enter.");

										state = Heist.WAITING;

										getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
											@Override
											public void run() {
												startHeist();
											}
										}, 2400);
									}

									participants.put(p.getUniqueId(), bid);
									p.sendMessage(ChatColor.GREEN + "You have joined the heist with a $" + bid + " bid.");
								} else {
									p.sendMessage(ChatColor.RED + "You don't have enough money!");
								}

							} else {
								p.sendMessage(ChatColor.RED + "Bid must be $1 or more!");
							}
							
						} else {
							p.sendMessage(ChatColor.RED + "You are already planning a bankheist!");
						}

					} else {
						p.sendMessage(ChatColor.RED + "The cops are on high alert from the last job - check again later.");
					}

				} else {
					p.sendMessage(ChatColor.RED + "A bankheist is already being executed - check again later.");
				}
			}
		}

		return false;
	}

	String[] bankNames = { "Municipal Bank", "City Bank", "State Bank", "National Bank", "Federal Reserve" };

	private void startHeist() {
		int k = participants.size();

		if (k == 0) {
			state = Heist.AVAILABLE;
			return;
		}

		state = Heist.RUNNING;

		String bankName = "Bank";

		final int j = k < 10 ? 0 : k > 40 ? 4 : k % 10;
		bankName = bankNames[j];

		getServer().broadcastMessage(ChatColor.GREEN + "Grab your weapons - we're rushing in the CraftBukkit " + bankName + "!");

		getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
			@Override
			public void run() {
				finishHeist(j);
			}
		}, 1200L);
	}

	private void finishHeist(int difficulty) {
		Random r = new Random();
		double multiplier = 1;

		int base = 60;
		base -= difficulty * 7;

		if (r.nextInt(100) + 1 <= base) {
			if (difficulty == 0) {
				multiplier = 1.5;
			} else if (difficulty == 1) {
				multiplier = 1.7;
			} else if (difficulty == 2) {
				multiplier = 2.0;
			} else {
				multiplier = 2.5;
			}

		} else {
			getServer().broadcastMessage(ChatColor.GREEN + "The plan was a bust, but the crew managed to bail before the cops got there.");

			participants.clear();
			startIdle();

			return;
		}

		StringBuilder builder = new StringBuilder(ChatColor.GREEN + "The crew got away with the cash! The payouts are: ");

		for (UUID id : participants.keySet()) {
			OfflinePlayer p = getServer().getOfflinePlayer(id);

			double bid = participants.get(id);
			double win = bid * multiplier;

			economy.depositPlayer(p, win);

			builder.append(ChatColor.DARK_GREEN + p.getName()).append(ChatColor.GREEN + " (").append(ChatColor.DARK_GREEN + "" + win).append(ChatColor.GREEN + ")").append(", ");
		}

		getServer().broadcastMessage(builder.toString());

		participants.clear();
		startIdle();
	}

	private void startIdle() {
		state = Heist.IDLE;

		getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
			@Override
			public void run() {
				state = Heist.AVAILABLE;
			}
		}, 12000);
	}

	private double toDouble(String in) {
		try {
			return Double.parseDouble(in);
		} catch (NumberFormatException ex) {
			ex.printStackTrace();
		}

		return 0D;
	}

	private boolean setupEconomy() {
		RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(Economy.class);

		if (economyProvider != null) {
			economy = economyProvider.getProvider();
		}

		return economy != null;
	}
}

enum Heist {

	AVAILABLE, WAITING, RUNNING, IDLE;
}
