package dev.fishclient.hud;

import dev.fishclient.config.FishConfig;
import dev.fishclient.ui.Draw;
import dev.fishclient.ui.Smooth;
import dev.fishclient.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

public final class FpsPingHud extends HudElement {
	private final Smooth fps = new Smooth(0d, 140d);
	private final Smooth ping = new Smooth(0d, 140d);

	FpsPingHud() {
		super("fps_ping", true, 0.01f, 0.01f);
	}

	@Override
	public float width(Draw d) {
		return 92f;
	}

	@Override
	public float height(Draw d) {
		return 20f;
	}

	@Override
	public void draw(Draw d) {
		Minecraft mc = Minecraft.getInstance();
		FishConfig config = FishConfig.get();
		HudStyle.box(d, width(d), height(d));
		PlayerInfo info = mc.player != null && mc.getConnection() != null ? mc.getConnection().getPlayerInfo(mc.player.getUUID()) : null;
		int latency = info != null ? info.getLatency() : 0;
		this.fps.target(mc.getFps());
		this.ping.target(latency);
		int shownFps = (int) Math.round(this.fps.get());
		int shownPing = (int) Math.round(this.ping.get());
		int fpsColor = mc.getFps() >= config.fpsGood ? Theme.GOOD : mc.getFps() >= config.fpsOkay ? Theme.WARN : Theme.BAD;
		int pingColor = latency <= config.pingGood ? Theme.GOOD : latency <= config.pingOkay ? Theme.WARN : Theme.BAD;
		String fpsText = shownFps + " FPS";
		d.bold(fpsText, HudStyle.PAD, 6f, fpsColor);
		d.right(shownPing + " ms", width(d) - HudStyle.PAD, 6f, 1f, pingColor, true);
	}
}
