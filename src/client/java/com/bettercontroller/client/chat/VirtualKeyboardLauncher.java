package com.bettercontroller.client.chat;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.gui.ControllerVirtualKeyboardScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;

/**
 * Opens BetterController's in-game virtual keyboard for the currently focused text field.
 */
public final class VirtualKeyboardLauncher {
    private static final long OPEN_COOLDOWN_MS = 180L;
    private static final long PENDING_TIMEOUT_MS = 3500L;

    private long lastLaunchTimestamp;
    private boolean pendingOpen;
    private long pendingSince;

    public boolean tryOpen(ControllerConfig config) {
        if (!isEnabled(config)) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return false;
        }
        if (client.currentScreen == null || client.currentScreen instanceof ControllerVirtualKeyboardScreen) {
            return false;
        }

        long now = System.currentTimeMillis();
        if ((now - lastLaunchTimestamp) < OPEN_COOLDOWN_MS) {
            return false;
        }

        Screen screen = client.currentScreen;
        TextFieldWidget targetField = resolveTargetTextField(screen);
        if (targetField == null) {
            return false;
        }

        lastLaunchTimestamp = now;
        client.setScreen(new ControllerVirtualKeyboardScreen(screen, targetField));
        pendingOpen = false;
        pendingSince = 0L;
        return true;
    }

    public void requestOpenOnNextTextScreen(ControllerConfig config) {
        if (!isEnabled(config)) {
            return;
        }
        pendingOpen = true;
        pendingSince = System.currentTimeMillis();
    }

    public void processPending(ControllerConfig config) {
        if (!pendingOpen || !isEnabled(config)) {
            return;
        }
        if (tryOpen(config)) {
            return;
        }
        if ((System.currentTimeMillis() - pendingSince) > PENDING_TIMEOUT_MS) {
            pendingOpen = false;
            pendingSince = 0L;
        }
    }

    private static TextFieldWidget resolveTargetTextField(Screen screen) {
        if (screen == null) {
            return null;
        }
        if (screen.getFocused() instanceof TextFieldWidget focusedTextField) {
            return focusedTextField;
        }
        for (Element element : screen.children()) {
            if (element instanceof TextFieldWidget textField) {
                return textField;
            }
        }
        return null;
    }

    private static boolean isEnabled(ControllerConfig config) {
        return config != null && config.virtualKeyboardEnabled;
    }
}
