package dev.fishclient.feature;

import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** Native open and save dialogs. They block, so they run off the render thread and report back onto it. */
public final class FileDialogs {
	public static void open(String title, String pattern, String description, Consumer<Path> onPicked) {
		Util.ioPool().execute(() -> {
			String result;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer filters = stack.mallocPointer(1);
				filters.put(stack.UTF8(pattern)).flip();
				result = TinyFileDialogs.tinyfd_openFileDialog(title, "", filters, description, false);
			}
			finish(result, onPicked);
		});
	}

	public static void save(String title, String defaultName, String pattern, String description, Consumer<Path> onPicked) {
		Util.ioPool().execute(() -> {
			String result;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer filters = stack.mallocPointer(1);
				filters.put(stack.UTF8(pattern)).flip();
				result = TinyFileDialogs.tinyfd_saveFileDialog(title, defaultName, filters, description);
			}
			finish(result, onPicked);
		});
	}

	private static void finish(String result, Consumer<Path> onPicked) {
		if (result != null) {
			Path path = Path.of(result);
			Minecraft.getInstance().execute(() -> onPicked.accept(path));
		}
	}

	private FileDialogs() {
	}
}
