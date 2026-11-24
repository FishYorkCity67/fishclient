package dev.fishclient.test;

/** Implemented on MouseHandler by a mixin. */
public interface MouseAccess {
	void test$pointer(double x, double y);

	void test$delta(double dx, double dy);

	void test$turn();

	void test$scroll(long handle, double amount);
}
