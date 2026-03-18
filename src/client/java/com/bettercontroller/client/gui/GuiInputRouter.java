package com.bettercontroller.client.gui;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.polling.ControllerAxis;
import com.bettercontroller.client.polling.ControllerButton;
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
        InputTranslator translator,
        boolean allowConfirmRepeat
    ) {
        long initialDelayMs = clampLong(config.menuInitialRepeatDelayMs, 60L, 400L, 140L);
        long repeatIntervalMs = clampLong(config.menuRepeatIntervalMs, 20L, 200L, 55L);
        if (allowConfirmRepeat) {
            initialDelayMs = Math.min(initialDelayMs, 150L);
            repeatIntervalMs = Math.min(repeatIntervalMs, 40L);
        }

        boolean upPressed = actionPressedWithFallback(snapshot, config, layout, translator, GameplayAction.MENU_UP);
        boolean downPressed = actionPressedWithFallback(snapshot, config, layout, translator, GameplayAction.MENU_DOWN);
        boolean leftPressed = actionPressedWithFallback(snapshot, config, layout, translator, GameplayAction.MENU_LEFT);
        boolean rightPressed = actionPressedWithFallback(snapshot, config, layout, translator, GameplayAction.MENU_RIGHT);
        boolean confirmPressed = actionPressedWithFallback(snapshot, config, layout, translator, GameplayAction.MENU_CONFIRM);
        boolean backPressed = actionPressedWithFallback(snapshot, config, layout, translator, GameplayAction.MENU_BACK);
        boolean pageNextPressed = actionPressedWithFallback(snapshot, config, layout, translator, GameplayAction.MENU_PAGE_NEXT);
        boolean pagePrevPressed = actionPressedWithFallback(snapshot, config, layout, translator, GameplayAction.MENU_PAGE_PREV);
        boolean tabNextPressed = actionPressedWithFallback(snapshot, config, layout, translator, GameplayAction.MENU_TAB_NEXT);
        boolean tabPrevPressed = actionPressedWithFallback(snapshot, config, layout, translator, GameplayAction.MENU_TAB_PREV);

        return new GuiInputFrame(
            pulse(GameplayAction.MENU_UP, upPressed, true, initialDelayMs, repeatIntervalMs),
            pulse(GameplayAction.MENU_DOWN, downPressed, true, initialDelayMs, repeatIntervalMs),
            pulse(GameplayAction.MENU_LEFT, leftPressed, true, initialDelayMs, repeatIntervalMs),
            pulse(GameplayAction.MENU_RIGHT, rightPressed, true, initialDelayMs, repeatIntervalMs),
            pulse(GameplayAction.MENU_CONFIRM, confirmPressed, allowConfirmRepeat, initialDelayMs, repeatIntervalMs),
            pulse(GameplayAction.MENU_BACK, backPressed, false, initialDelayMs, repeatIntervalMs),
            pulse(GameplayAction.MENU_PAGE_NEXT, pageNextPressed, false, initialDelayMs, repeatIntervalMs),
            pulse(GameplayAction.MENU_PAGE_PREV, pagePrevPressed, false, initialDelayMs, repeatIntervalMs),
            pulse(GameplayAction.MENU_TAB_NEXT, tabNextPressed, false, initialDelayMs, repeatIntervalMs),
            pulse(GameplayAction.MENU_TAB_PREV, tabPrevPressed, false, initialDelayMs, repeatIntervalMs)
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

    private static boolean actionPressedWithFallback(
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout,
        InputTranslator translator,
        GameplayAction action
    ) {
        if (snapshot == null || config == null || layout == null || translator == null || action == null) {
            return false;
        }
        if (translator.isActionPressed(snapshot, config, layout, action)) {
            return true;
        }
        return defaultMenuFallback(snapshot, config, action);
    }

    private static boolean defaultMenuFallback(
        ControllerSnapshot snapshot,
        ControllerConfig config,
        GameplayAction action
    ) {
        if (snapshot == null || config == null || action == null) {
            return false;
        }

        float axisThreshold = clampFloat(config.menuAxisThreshold, 0.2F, 0.95F, 0.35F);
        float triggerThreshold = clampFloat(config.triggerThreshold, 0.01F, 1.0F, 0.45F);
        float leftX = snapshot.axis(ControllerAxis.LEFT_X);
        float leftY = snapshot.axis(ControllerAxis.LEFT_Y);
        float leftTrigger = normalizeTrigger(snapshot.axis(ControllerAxis.LEFT_TRIGGER));
        float rightTrigger = normalizeTrigger(snapshot.axis(ControllerAxis.RIGHT_TRIGGER));

        return switch (action) {
            case MENU_UP -> snapshot.isPressed(ControllerButton.DPAD_UP) || leftY <= -axisThreshold;
            case MENU_DOWN -> snapshot.isPressed(ControllerButton.DPAD_DOWN) || leftY >= axisThreshold;
            case MENU_LEFT -> snapshot.isPressed(ControllerButton.DPAD_LEFT) || leftX <= -axisThreshold;
            case MENU_RIGHT -> snapshot.isPressed(ControllerButton.DPAD_RIGHT) || leftX >= axisThreshold;
            case MENU_CONFIRM -> snapshot.isPressed(ControllerButton.SOUTH);
            case MENU_BACK -> snapshot.isPressed(ControllerButton.EAST);
            case MENU_PAGE_NEXT -> snapshot.isPressed(ControllerButton.RIGHT_BUMPER) || rightTrigger >= triggerThreshold;
            case MENU_PAGE_PREV -> snapshot.isPressed(ControllerButton.LEFT_BUMPER) || leftTrigger >= triggerThreshold;
            case MENU_TAB_NEXT -> snapshot.isPressed(ControllerButton.RIGHT_BUMPER);
            case MENU_TAB_PREV -> snapshot.isPressed(ControllerButton.LEFT_BUMPER);
            default -> false;
        };
    }

    private static float normalizeTrigger(float value) {
        float normalized = (value + 1.0F) * 0.5F;
        return Math.max(0.0F, Math.min(1.0F, normalized));
    }

    private static float clampFloat(float value, float min, float max, float fallback) {
        if (!Float.isFinite(value) || value < min || value > max) {
            return fallback;
        }
        return value;
    }
}
