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
        float eventMultiplier = resolveEventIntensityMultiplier(config, event);
        float effectiveIntensity = profile.intensityMultiplier() * eventMultiplier;
        if (effectiveIntensity <= 0.0F) {
            return;
        }

        // GLFW does not expose cross-platform rumble APIs for gamepads in this stack.
        if (!warnedUnsupported) {
            warnedUnsupported = true;
            BetterControllerMod.LOGGER.info(
                "Controller haptics requested (effective intensity {:.2f}) but not available in current GLFW backend. Running in graceful no-op mode.",
                effectiveIntensity
            );
        }
    }

    public void resetWarning() {
        warnedUnsupported = false;
    }

    static float resolveEventIntensityMultiplier(ControllerConfig config, HapticEvent event) {
        if (config == null || event == null) {
            return 0.0F;
        }
        if (config.vibrationEventIntensity == null || config.vibrationEventIntensity.isEmpty()) {
            return event.defaultIntensityMultiplier();
        }
        Float configured = config.vibrationEventIntensity.get(event.configKey());
        if (configured == null || !Float.isFinite(configured)) {
            return event.defaultIntensityMultiplier();
        }
        return Math.max(0.0F, Math.min(2.0F, configured));
    }
}
