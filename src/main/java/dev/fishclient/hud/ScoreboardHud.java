package dev.fishclient.hud;

import dev.fishclient.FishClient;
import dev.fishclient.config.FishConfig;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.Theme;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;

/** The sidebar in the mod's style. Colours and any custom server fonts inside the lines stay as they are. */
public final class ScoreboardHud extends HudElement {
	private static final Style BASE = Style.EMPTY.withFont(new FontDescription.Resource(FishClient.id("inter")));
	private static final Comparator<PlayerScoreEntry> ORDER = Comparator.comparingInt(PlayerScoreEntry::value).reversed().thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
	private static final float ROW = 11f;

	private record Line(FormattedCharSequence name, FormattedCharSequence score, float nameWidth, float scoreWidth) {
	}

	private final List<Line> lines = new ArrayList<>();
	@Nullable
	private FormattedCharSequence title;
	private float titleWidth;
	private float contentWidth;

	ScoreboardHud() {
		super("scoreboard", true, 0.995f, 0.4f);
	}

	@Nullable
	private static Objective objective() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return null;
		}
		Scoreboard scoreboard = mc.level.getScoreboard();
		PlayerTeam team = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
		Objective objective = null;
		if (team != null) {
			DisplaySlot slot = DisplaySlot.teamColorToSlot(team.getColor());
			if (slot != null) {
				objective = scoreboard.getDisplayObjective(slot);
			}
		}
		return objective != null ? objective : scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
	}

	@Override
	public boolean visible() {
		return FishConfig.get().scoreboard && objective() != null;
	}

	private void collect(Draw d) {
		this.lines.clear();
		this.title = null;
		this.contentWidth = 60f;
		Objective objective = objective();
		if (objective == null) {
			return;
		}
		boolean hide = FishConfig.get().scoreboardHideNumbers;
		Scoreboard scoreboard = objective.getScoreboard();
		NumberFormat format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
		this.title = styled(objective.getDisplayName());
		this.titleWidth = d.font.width(this.title);
		this.contentWidth = Math.max(this.contentWidth, this.titleWidth);
		scoreboard.listPlayerScores(objective).stream()
			.filter(entry -> !entry.isHidden())
			.sorted(ORDER)
			.limit(15L)
			.forEach(entry -> {
				PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
				FormattedCharSequence name = styled(PlayerTeam.formatNameForTeam(team, entry.ownerName()));
				FormattedCharSequence score = hide ? FormattedCharSequence.EMPTY : styled(entry.formatValue(format));
				float nameWidth = d.font.width(name);
				float scoreWidth = hide ? 0f : d.font.width(score);
				this.lines.add(new Line(name, score, nameWidth, scoreWidth));
				this.contentWidth = Math.max(this.contentWidth, nameWidth + (scoreWidth > 0f ? scoreWidth + 8f : 0f));
			});
	}

	private static FormattedCharSequence styled(Component component) {
		MutableComponent wrapped = Component.empty().withStyle(BASE);
		wrapped.append(component);
		return wrapped.getVisualOrderText();
	}

	@Override
	public float width(Draw d) {
		collect(d);
		return this.contentWidth + HudStyle.PAD * 2f;
	}

	@Override
	public float height(Draw d) {
		collect(d);
		return 20f + this.lines.size() * ROW + 3f;
	}

	@Override
	public void draw(Draw d) {
		collect(d);
		float w = this.contentWidth + HudStyle.PAD * 2f;
		HudStyle.box(d, w, 20f + this.lines.size() * ROW + 3f);
		if (this.title == null) {
			return;
		}
		line(d, this.title, (w - this.titleWidth) / 2f, 5.5f);
		d.rect(HudStyle.PAD, 17f, w - HudStyle.PAD * 2f, 0.5f, Draw.withAlpha(Theme.accent(), 0x90));
		float y = 21f;
		for (Line l : this.lines) {
			line(d, l.name(), HudStyle.PAD, y);
			if (l.scoreWidth() > 0f) {
				line(d, l.score(), w - HudStyle.PAD - l.scoreWidth(), y);
			}
			y += ROW;
		}
	}

	private static void line(Draw d, FormattedCharSequence text, float x, float y) {
		d.g.pose().pushMatrix();
		d.g.pose().translate(x, y);
		d.g.text(d.font, text, 0, 0, 0xFFFFFFFF, false);
		d.g.pose().popMatrix();
	}
}
