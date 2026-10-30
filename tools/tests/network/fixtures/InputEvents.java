package me.eigenraven.lwjgl3ify.api;

/** Link-only fixture: cached MUI2's client API mentions lwjgl3ify, absent from the offline cache.
 * No keyboard/text callbacks are executed by these server-boundary tests. Not runtime/game validation. */
public final class InputEvents {
    public interface KeyboardListener {}
    public interface TextInputListener {}
    public static final class KeyEvent {}
    public static final class TextEvent {}
}
