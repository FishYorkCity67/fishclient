package dev.fishclient.feature;

import dev.fishclient.config.FishConfig;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.jspecify.annotations.Nullable;

/**
 * Chat recognition per server. The defaults are starting points, not verified formats: the exact lines of
 * DonutSMP, HugoSMP and FishSMP have to be checked in-game and can be edited in the menu.
 */
public final class ServerRules {
	private static final String AMOUNT = "\\$?(?<amount>[0-9][0-9.,]*\\s?[kKmMbBtT]?)";
	private static final String PLAYER = "(?<player>[A-Za-z0-9_.]{2,17})";

	public static void addDefaults(FishConfig config) {
		if (config.servers.isEmpty()) {
			config.servers.add(defaults("DonutSMP", "donut"));
			config.servers.add(defaults("HugoSMP", "hugo"));
			config.servers.add(defaults("FishSMP", "fish"));
		}
		// Profiles from before the live auction have no pay command yet.
		for (FishConfig.ServerPatterns p : config.servers) {
			if (p.payCommand == null || p.payCommand.isBlank()) {
				p.payCommand = "/pay {player} {amount}";
			}
		}
	}

	private static FishConfig.ServerPatterns defaults(String name, String address) {
		FishConfig.ServerPatterns p = new FishConfig.ServerPatterns();
		p.name = name;
		p.addressContains = address;
		p.received = new java.util.ArrayList<>(List.of(
			"^" + PLAYER + " paid you " + AMOUNT + "[.!]?$",
			"^You received " + AMOUNT + " from " + PLAYER + "[.!]?$"
		));
		p.sent = new java.util.ArrayList<>(List.of(
			"^You paid " + PLAYER + " " + AMOUNT + "[.!]?$",
			"^You sent " + AMOUNT + " to " + PLAYER + "[.!]?$"
		));
		p.sold = new java.util.ArrayList<>(List.of(
			"^" + PLAYER + " bought your (?<item>.+?) for " + AMOUNT + "[.!]?$",
			"^You sold (?<item>.+?) for " + AMOUNT + "[.!]?$"
		));
		p.bought = new java.util.ArrayList<>(List.of(
			"^You bought (?<item>.+?) from " + PLAYER + " for " + AMOUNT + "[.!]?$",
			"^You bought (?<item>.+?) for " + AMOUNT + "[.!]?$"
		));
		return p;
	}

	/** The rules for the current server, matched by address. Unknown servers and singleplayer use the first entry. */
	public static FishConfig.@Nullable ServerPatterns current() {
		List<FishConfig.ServerPatterns> all = FishConfig.get().servers;
		if (all.isEmpty()) {
			return null;
		}
		ServerData data = Minecraft.getInstance().getCurrentServer();
		if (data != null && data.ip != null) {
			String ip = data.ip.toLowerCase(java.util.Locale.ROOT);
			for (FishConfig.ServerPatterns p : all) {
				if (!p.addressContains.isEmpty() && ip.contains(p.addressContains.toLowerCase(java.util.Locale.ROOT))) {
					return p;
				}
			}
		}
		return all.getFirst();
	}

	private ServerRules() {
	}
}
