package dev.fishclient.feature;

import java.util.Locale;

/** Parses and formats in-game money such as {@code 1.5M} or {@code 250,000}. */
public final class Money {
	public static double parse(String raw) {
		String s = raw.trim().replace(" ", "").replace("$", "");
		if (s.isEmpty()) {
			return 0d;
		}
		double multiplier = 1d;
		char last = Character.toLowerCase(s.charAt(s.length() - 1));
		switch (last) {
			case 'k' -> multiplier = 1e3;
			case 'm' -> multiplier = 1e6;
			case 'b' -> multiplier = 1e9;
			case 't' -> multiplier = 1e12;
			default -> {
			}
		}
		if (multiplier != 1d) {
			s = s.substring(0, s.length() - 1);
		}
		// A comma followed by exactly three digits groups thousands; anything else is a decimal comma.
		if (s.matches(".*,\\d{3}(,\\d{3})*(\\.\\d+)?$")) {
			s = s.replace(",", "");
		} else {
			s = s.replace(",", ".");
		}
		// Likewise "1.500.000" groups thousands with dots.
		if (s.matches("\\d{1,3}(\\.\\d{3}){2,}")) {
			s = s.replace(".", "");
		}
		try {
			return Double.parseDouble(s) * multiplier;
		} catch (NumberFormatException e) {
			return 0d;
		}
	}

	public static String format(double value) {
		double abs = Math.abs(value);
		String sign = value < 0d ? "-" : "";
		if (abs >= 1e12) {
			return sign + "$" + trim(abs / 1e12) + "T";
		}
		if (abs >= 1e9) {
			return sign + "$" + trim(abs / 1e9) + "B";
		}
		if (abs >= 1e6) {
			return sign + "$" + trim(abs / 1e6) + "M";
		}
		if (abs >= 1e3) {
			return sign + "$" + trim(abs / 1e3) + "K";
		}
		return sign + "$" + Math.round(abs);
	}

	public static String signed(double value) {
		return (value > 0d ? "+" : "") + format(value);
	}

	private static String trim(double v) {
		String s = String.format(Locale.ROOT, "%.2f", v);
		if (s.endsWith("0")) {
			s = s.substring(0, s.length() - 1);
		}
		if (s.endsWith(".0")) {
			s = s.substring(0, s.length() - 2);
		}
		return s;
	}

	private Money() {
	}
}
