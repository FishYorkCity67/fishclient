package dev.fishclient;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class FishKeys {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(FishClient.id("main"));

	public static final KeyMapping MENU = key("menu", GLFW.GLFW_KEY_RIGHT_SHIFT);
	public static final KeyMapping FREELOOK = key("freelook", GLFW.GLFW_KEY_LEFT_ALT);
	public static final KeyMapping ZOOM = key("zoom", GLFW.GLFW_KEY_C);
	public static final KeyMapping WAYPOINT = key("waypoint", InputConstants.UNKNOWN.getValue());

	public static final List<KeyMapping> ALL = List.of(MENU, FREELOOK, ZOOM, WAYPOINT);

	private static KeyMapping key(String name, int code) {
		return new KeyMapping("key.fishclient." + name, code, CATEGORY);
	}

	public static void register() {
		ALL.forEach(KeyMappingHelper::registerKeyMapping);
	}

	private FishKeys() {
	}
}
