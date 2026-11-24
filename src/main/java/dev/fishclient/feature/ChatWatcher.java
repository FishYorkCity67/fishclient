package dev.fishclient.feature;

import com.mojang.logging.LogUtils;
import dev.fishclient.config.FishConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.ChatFormatting;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/** Matches incoming server messages against the current server's patterns. */
public final class ChatWatcher {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Map<String, Pattern> CACHE = new HashMap<>();

	/** A parsed message, or null fields where the pattern has no such group. */
	public record Match(String kind, @Nullable String player, double amount, @Nullable String item) {
	}

	public static void onMessage(String raw) {
		FishConfig.ServerPatterns rules = ServerRules.current();
		if (rules == null) {
			return;
		}
		Match m = parse(raw, rules);
		if (m == null) {
			return;
		}
		String player = m.player() != null ? m.player() : "?";
		switch (m.kind()) {
			case "received" -> {
				Transactions.add(player, m.amount(), true);
				LiveAuction.onPayment(player, m.amount());
			}
			case "sent" -> Transactions.add(player, m.amount(), false);
			case "sold" -> Auctions.addSale(m.item() != null ? m.item() : "?", player, m.amount(), true);
			case "bought" -> Auctions.addSale(m.item() != null ? m.item() : "?", player, m.amount(), false);
			default -> {
			}
		}
	}

	/** Exposed separately so the pattern tester in the menu and the tests use the same code. */
	@Nullable
	public static Match parse(String raw, FishConfig.ServerPatterns rules) {
		String line = ChatFormatting.stripFormatting(raw);
		if (line == null) {
			return null;
		}
		line = line.trim();
		Match m = first("received", rules.received, line);
		if (m == null) {
			m = first("sent", rules.sent, line);
		}
		if (m == null) {
			m = first("sold", rules.sold, line);
		}
		if (m == null) {
			m = first("bought", rules.bought, line);
		}
		return m;
	}

	@Nullable
	private static Match first(String kind, List<String> patterns, String line) {
		for (String regex : patterns) {
			Pattern pattern = compile(regex);
			if (pattern == null) {
				continue;
			}
			Matcher matcher = pattern.matcher(line);
			if (matcher.find()) {
				double amount = Money.parse(group(matcher, "amount", "0"));
				if (amount > 0d) {
					return new Match(kind, group(matcher, "player", null), amount, group(matcher, "item", null));
				}
			}
		}
		return null;
	}

	@Nullable
	private static String group(Matcher matcher, String name, @Nullable String fallback) {
		try {
			String value = matcher.group(name);
			return value != null ? value : fallback;
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}

	@Nullable
	private static Pattern compile(String regex) {
		if (CACHE.containsKey(regex)) {
			return CACHE.get(regex);
		}
		Pattern pattern = null;
		try {
			pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		} catch (PatternSyntaxException e) {
			LOGGER.warn("FishClient skipped an invalid chat pattern: {}", regex);
		}
		CACHE.put(regex, pattern);
		return pattern;
	}

	public static boolean validRegex(String regex) {
		try {
			Pattern.compile(regex);
			return true;
		} catch (PatternSyntaxException e) {
			return false;
		}
	}

	private ChatWatcher() {
	}
}
