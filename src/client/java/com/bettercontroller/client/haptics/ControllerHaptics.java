package com.bettercontroller.client.haptics;

import com.bettercontroller.BetterControllerMod;
import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.polling.ControllerSnapshot;

public final class ControllerHaptics {
    private boolean warnedUnsupported;

    public void trigger(HapticEvent event, ControllerConfig config, ControllerSnapshot snapshot) {
        if (event == null || config == null || snapshot == null) {
            return;
        }
        if (!config.vibrationEnabled) {
            return;
        }

        HapticProfile profile = HapticProfile.fromConfig(config.vibrationIntensity);
        if (profile.intensityMultiplier() <= 0.0F) {
            return;
        }

        // GLFW does not expose cross-platform rumble APIs for gamepads in this stack.
        if (!warnedUnsupported) {
            warnedUnsupported = true;
            BetterControllerMod.LOGGER.info("Controller haptics requested but not available in current GLFW backend. Running in graceful no-op mode.");
        }
    }

    public void resetWarning() {
        warnedUnsupported = false;
    }
}
