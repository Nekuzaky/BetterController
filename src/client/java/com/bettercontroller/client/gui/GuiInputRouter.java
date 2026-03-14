package com.bettercontroller.client.gui;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.polling.ControllerSnapshot;
import com.bettercontroller.client.translation.GameplayAction;
import com.bettercontroller.client.translation.InputTranslator;

import java.util.EnumMap;

public final class GuiInputRouter {
    private final EnumMap<GameplayAction, Boolean> previousStates = new EnumMap<>(GameplayAction.class);
    private final EnumMap<GameplayAction, Long> holdStartMs = new EnumMap<>(GameplayAction.class);
    private final EnumMap<GameplayAction, Long> lastRepeatMs = new EnumMap<>(GameplayAction.class);

    public GuiInputFrame route(
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout,
        InputTranslator translator
    ) {
        long initialDelayMs = clampLong(config.menuInitialRepeatDelayMs, 60L, 400L, 140L);
        long repeatIntervalMs = clampLong(config.menuRepeatIntervalMs, 20L, 200L, 55L);

        return new GuiInputFrame(
            pulse(GameplayAction.MENU_UP, translator.isActionPressed(snapshot, config, layout, GameplayAction.MENU_UP), true, initialDelayMs, repeatIntervalMs),
            pulse(GameplayAction.MENU_DOWN, translator.isActionPressed(snapshot, config, layout, GameplayAction.MENU_DOWN), true, initialDelayMs, repeatIntervalMs),
            pulse(GameplayAction.MENU_LEFT, translator.isActionPressed(snapshot, config, layout, GameplayAction.MENU_LEFT), true, initialDelayMs, repeatIntervalMs),
            pulse(GameplayAction.MENU_RIGHT, translator.isActionPressed(snapshot, config, layout, GameplayAction.MENU_RIGHT), true, initialDelayMs, repeatIntervalMs),
            pulse(GameplayAction.MENU_CONFIRM, translator.isActionPressed(snapshot, config, layout, GameplayAction.MENU_CONFIRM), false, initialDelayMs, repeatIntervalMs),
            pulse(GameplayAction.MENU_BACK, translator.isActionPressed(snapshot, config, layout, GameplayAction.MENU_BACK), false, initialDelayMs, repeatIntervalMs),
            pulse(GameplayAction.MENU_PAGE_NEXT, translator.isActionPressed(snapshot, config, layout, GameplayAction.MENU_PAGE_NEXT), false, initialDelayMs, repeatIntervalMs),
            pulse(GameplayAction.MENU_PAGE_PREV, translator.isActionPressed(snapshot, config, layout, GameplayAction.MENU_PAGE_PREV), false, initialDelayMs, repeatIntervalMs),
            pulse(GameplayAction.MENU_TAB_NEXT, translator.isActionPressed(snapshot, config, layout, GameplayAction.MENU_TAB_NEXT), false, initialDelayMs, repeatIntervalMs),
            pulse(GameplayAction.MENU_TAB_PREV, translator.isActionPressed(snapshot, config, layout, GameplayAction.MENU_TAB_PREV), false, initialDelayMs, repeatIntervalMs)
        );
    }

    public void reset() {
        previousStates.clear();
        holdStartMs.clear();
        lastRepeatMs.clear();
    }

    private boolean pulse(
        GameplayAction action,
        boolean pressed,
        boolean repeatable,
        long initialDelayMs,
        long repeatIntervalMs
    ) {
        long now = System.currentTimeMillis();
        boolean previous = Boolean.TRUE.equals(previousStates.get(action));

        if (!pressed) {
            previousStates.put(action, false);
            holdStartMs.remove(action);
            lastRepeatMs.remove(action);
            return false;
        }

        if (!previous) {
            previousStates.put(action, true);
            holdStartMs.put(action, now);
            lastRepeatMs.put(action, now);
            return true;
        }

        previousStates.put(action, true);
        if (!repeatable) {
            return false;
        }

        long heldSince = holdStartMs.getOrDefault(action, now);
        long lastRepeat = lastRepeatMs.getOrDefault(action, now);
        long acceleratedInterval = Math.max(16L, repeatIntervalMs - 12L);
        long effectiveInterval = (now - heldSince) >= 900L ? acceleratedInterval : repeatIntervalMs;
        if ((now - heldSince) >= initialDelayMs && (now - lastRepeat) >= effectiveInterval) {
            lastRepeatMs.put(action, now);
            return true;
        }
        return false;
    }

    private static long clampLong(long value, long min, long max, long fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }
}
