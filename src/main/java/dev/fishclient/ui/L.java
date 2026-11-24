package dev.fishclient.ui;

import net.minecraft.locale.Language;

/** Short-hand for the mod's translation keys, returning plain strings for immediate-mode drawing. */
public final class L {
	public static String t(String key) {
		return Language.getInstance().getOrDefault("fishclient." + key);
	}

	public static String f(String key, Object... args) {
		return String.format(t(key), args);
	}

	private L() {
	}
}
