package com.bettercontroller.client.chat;

import com.bettercontroller.BetterControllerMod;
import com.bettercontroller.client.config.ControllerConfig;

import java.io.IOException;
import java.util.Locale;

public final class VirtualKeyboardLauncher {
    private long lastLaunchTimestamp;

    public void tryOpen(ControllerConfig config) {
        if (config == null || !config.virtualKeyboardEnabled) {
            return;
        }

        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!osName.contains("win")) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastLaunchTimestamp < 2000L) {
            return;
        }
        lastLaunchTimestamp = now;

        try {
            new ProcessBuilder("osk").start();
        } catch (IOException exception) {
            BetterControllerMod.LOGGER.warn("Could not launch Windows on-screen keyboard: {}", exception.getMessage());
        }
    }
}
