package dev.fishclient.hud;

import dev.fishclient.feature.LiveAuction;
import dev.fishclient.feature.Money;
import dev.fishclient.ui.Anim;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.Easing;
import dev.fishclient.ui.L;
import dev.fishclient.ui.Smooth;
import dev.fishclient.ui.Theme;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * The auction card for the stream: lot, countdown, the current leader and every bid. After the end it turns into
 * the winner card for a few seconds. Rows glide to their new place when the order changes.
 */
public final class LiveAuctionHud extends HudElement {
	private static final float W = 190f;
	private static final float ROW = 12f;
	private static final int MAX_ROWS = 8;
	private final Smooth leaderAmount = new Smooth(0d, 120d);
	private final Map<String, Smooth> rowY = new HashMap<>();
	private final Map<String, Anim> rowIn = new HashMap<>();
	private final Anim appear = new Anim(0f, 320, Easing.OUT_BACK);
	private final Anim winner = new Anim(0f, 450, Easing.OUT_BACK);
	private long seenStart = -1L;

	LiveAuctionHud() {
		super("live_auction", true, 0.5f, 0.1f);
	}

	@Override
	public boolean visible() {
		return LiveAuction.running() || LiveAuction.showingWinner();
	}

	private static int rows() {
		return Math.min(MAX_ROWS, Math.max(1, LiveAuction.ranking().size()));
	}

	@Override
	public float width(Draw d) {
		return W;
	}

	@Override
	public float height(Draw d) {
		if (!LiveAuction.running()) {
			return 96f;
		}
		LiveAuction.Settings s = LiveAuction.current();
		float noteH = s != null && !s.note.isEmpty() ? 10f : 0f;
		int more = LiveAuction.ranking().size() > MAX_ROWS ? 1 : 0;
		// Header, lot, bar and leader take 90 units; the bid rows follow, then a little room at the bottom.
		return 92f + noteH + (rows() + more) * ROW;
	}

	@Override
	public void draw(Draw d) {
		if (LiveAuction.startedAt() != this.seenStart) {
			this.seenStart = LiveAuction.startedAt();
			reset();
		}
		boolean live = LiveAuction.running();
		this.appear.to(1f);
		this.winner.to(live ? 0f : 1f);
		float a = this.appear.get();
		float previous = d.alpha;
		d.alpha = previous * Math.clamp(a, 0f, 1f);
		if (live) {
			drawLive(d);
		} else {
			drawWinner(d);
		}
		d.alpha = previous;
	}

	private void drawLive(Draw d) {
		LiveAuction.Settings s = LiveAuction.current();
		if (s == null) {
			return;
		}
		float h = height(d);
		long now = Util.getMillis();
		long remaining = LiveAuction.remainingMs();
		boolean urgent = remaining <= 10_000L;
		float pulse = urgent ? 0.5f + 0.5f * (float) Math.sin(now / 120.0) : 0f;

		// A brief glow whenever a new bid comes in.
		float flash = Math.max(0f, 1f - (now - LiveAuction.lastBidAt()) / 900f);
		d.glow(0f, 0f, W, h, 8f, 6f, Draw.withAlpha(Theme.accent(), Math.round(0x30 + 0x80 * flash)));
		d.round(0f, 0f, W, h, 8f, Draw.withAlpha(Theme.panel(), 0xE8));
		d.roundOutline(0f, 0f, W, h, 8f, 1f, Draw.lerpColor(Theme.panelEdge(), Theme.accent(), flash));

		float x = 8f;
		float y = 7f;
		float dot = 0.55f + 0.45f * (float) Math.sin(now / 260.0);
		d.round(x, y + 1.5f, 6f, 6f, 3f, Draw.mulAlpha(Theme.BAD, 0.4f + 0.6f * dot));
		d.text(L.t("live.title"), x + 10f, y, 0.85f, Theme.text(), true);
		String clock = clock(remaining);
		int clockColor = urgent ? Draw.lerpColor(Theme.WARN, Theme.BAD, pulse) : Theme.text();
		d.right(clock, W - 8f, y - 1f, 1.25f, clockColor, true);
		y += 15f;

		// Lot: icons of up to three stacks, then the name.
		float iconX = x;
		for (int i = 0; i < Math.min(3, s.lots.size()); i++) {
			ItemStack stack = s.lots.get(i).stack();
			d.round(iconX, y, 20f, 20f, 4f, Theme.cardHover());
			d.g.item(stack, Math.round(iconX + 2f), Math.round(y + 2f));
			iconX += 23f;
		}
		float textX = s.lots.isEmpty() ? x : iconX + 2f;
		d.bold(d.fit(LiveAuction.label(s), W - textX - 8f), textX, y + 2f, Theme.text());
		String detail = s.worth > 0d ? L.f("live.worth", Money.format(s.worth)) : L.f("live.min", Money.format(s.minBid));
		d.text(d.fit(detail, (W - textX - 8f) / 0.8f), textX, y + 12f, 0.8f, Theme.muted(), false);
		y += 25f;
		if (!s.note.isEmpty()) {
			d.text(d.fit(s.note, (W - 16f) / 0.85f), x, y, 0.85f, Theme.accent2(), false);
			y += 10f;
		}

		// Countdown bar.
		float fraction = Math.clamp(remaining / (float) LiveAuction.totalMs(), 0f, 1f);
		d.round(x, y, W - 16f, 3f, 1.5f, Theme.line());
		d.roundH(x, y, Math.max(3f, (W - 16f) * fraction), 3f, 1.5f, urgent ? Theme.BAD : Theme.accent(), urgent ? Theme.WARN : Theme.accent2());
		y += 8f;

		// The leader, large.
		LiveAuction.Bid leader = LiveAuction.leader();
		d.text(L.t("live.highest").toUpperCase(Locale.ROOT), x, y, 0.75f, Theme.muted(), true);
		y += 10f;
		if (leader == null) {
			d.text(L.t("live.no_bids"), x, y + 3f, Theme.faint());
			this.leaderAmount.snap(0d);
		} else {
			this.leaderAmount.target(leader.value(LiveAuction.summed()));
			d.bold(d.fit(leader.player, W - 100f), x, y + 2f, Theme.text());
			d.right(Money.format(this.leaderAmount.get()), W - 8f, y, 1.35f, Theme.GOOD, true);
		}
		y += 17f;
		d.rect(x, y, W - 16f, 0.5f, Theme.line());
		y += 4f;

		// Every bid; rows ease to their new place when someone overtakes.
		List<LiveAuction.Bid> ranking = LiveAuction.ranking();
		if (ranking.isEmpty()) {
			d.text(L.t("live.waiting"), x, y + 1f, 0.85f, Theme.faint(), false);
		}
		for (int i = 0; i < Math.min(MAX_ROWS, ranking.size()); i++) {
			LiveAuction.Bid bid = ranking.get(i);
			float target = y + i * ROW;
			Smooth sy = this.rowY.computeIfAbsent(bid.player, k -> new Smooth(target, 70d));
			sy.target(target);
			Anim in = this.rowIn.computeIfAbsent(bid.player, k -> {
				Anim anim = new Anim(0f, 260, Easing.OUT_CUBIC);
				anim.to(1f);
				return anim;
			});
			float ry = (float) sy.get();
			float rowAlpha = in.get();
			float slide = (1f - rowAlpha) * 12f;
			boolean eligible = LiveAuction.eligible(bid);
			boolean fresh = bid.player.equals(LiveAuction.lastBidder()) && flash > 0f;
			if (fresh) {
				d.round(x - 3f, ry - 1.5f, W - 10f, ROW - 1f, 3f, Draw.mulAlpha(Draw.withAlpha(Theme.accent(), 0x50), flash));
			}
			float prev = d.alpha;
			d.alpha = prev * rowAlpha;
			int nameColor = !eligible ? Theme.faint() : i == 0 ? Theme.text() : Theme.muted();
			d.text((i + 1) + ".", x + slide, ry, 0.85f, Theme.faint(), false);
			String amount = Money.format(bid.value(LiveAuction.summed()));
			String suffix = bid.payments > 1 ? "  " + bid.payments + "x" : "";
			d.text(d.fit(bid.player + suffix, (W - 90f) / 0.9f), x + 13f + slide, ry, 0.9f, nameColor, false);
			d.right(amount, W - 8f + slide, ry, 0.9f, !eligible ? Theme.faint() : i == 0 ? Theme.GOOD : Theme.text(), i == 0);
			d.alpha = prev;
		}
		if (ranking.size() > MAX_ROWS) {
			d.text(L.f("live.more", ranking.size() - MAX_ROWS), x, y + MAX_ROWS * ROW, 0.8f, Theme.faint(), false);
		}
	}

	private void drawWinner(Draw d) {
		LiveAuction.Result result = LiveAuction.lastResult();
		if (result == null) {
			return;
		}
		float h = height(d);
		float pop = this.winner.get();
		long now = Util.getMillis();
		float shimmer = 0.5f + 0.5f * (float) Math.sin(now / 300.0);
		boolean won = result.winner != null;
		d.glow(0f, 0f, W, h, 10f, 10f, Draw.withAlpha(won ? Theme.accent() : Theme.faint(), Math.round(0x40 + 0x50 * shimmer)));
		d.round(0f, 0f, W, h, 10f, Draw.withAlpha(Theme.panel(), 0xF0));
		d.roundOutline(0f, 0f, W, h, 10f, 1.2f, won ? Draw.lerpColor(Theme.accent(), Theme.accent2(), shimmer) : Theme.panelEdge());

		float cx = W / 2f;
		d.g.pose().pushMatrix();
		d.g.pose().translate(cx, h / 2f);
		float scale = 0.85f + 0.15f * pop;
		d.g.pose().scale(scale, scale);
		d.g.pose().translate(-cx, -h / 2f);
		if (result.cancelled) {
			d.centered(L.t("live.cancelled"), cx, 30f, 1.2f, Theme.text(), true);
			d.centered(L.t("live.refund_hint"), cx, 50f, 0.85f, Theme.muted(), false);
		} else if (!won) {
			d.centered(L.t("live.no_winner"), cx, 30f, 1.2f, Theme.text(), true);
			d.centered(L.f("live.min", Money.format(result.settings.minBid)), cx, 50f, 0.85f, Theme.muted(), false);
		} else {
			d.icon("star", cx - 7f, 8f, 14f, Theme.accent());
			d.centered(L.t("live.winner").toUpperCase(Locale.ROOT), cx, 25f, 0.85f, Theme.accent(), true);
			d.centered(d.fit(result.winner, W - 20f), cx, 37f, 1.5f, Theme.text(), true);
			d.centered(Money.format(result.price), cx, 55f, 1.35f, Theme.GOOD, true);
			d.centered(d.fit(LiveAuction.label(result.settings), (W - 20f) / 0.9f), cx, 73f, 0.9f, Theme.muted(), false);
			d.centered(L.f("live.bidders", result.bids.size()), cx, 83f, 0.8f, Theme.faint(), false);
		}
		d.g.pose().popMatrix();
	}

	private static String clock(long ms) {
		long seconds = (ms + 999L) / 1000L;
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
	}

	/** Drops the row animations, so the next auction starts with fresh ones. */
	private void reset() {
		this.rowY.clear();
		this.rowIn.clear();
		this.appear.snap(0f);
		this.leaderAmount.snap(0d);
	}
}
