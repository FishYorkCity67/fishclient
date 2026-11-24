package dev.fishclient.menu;

/** One tab of the menu. It draws from the top down and returns the height of what it drew. */
@FunctionalInterface
public interface Page {
	float draw(Ui ui, float x, float y, float w);
}
