package com.bettercontroller.client.haptics;

import com.bettercontroller.client.config.ControllerConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControllerHapticsTest {
    @Test
    void usesDefaultEventIntensityWhenMapMissing() {
        ControllerConfig config = ControllerConfig.createDefault();
        config.vibrationEventIntensity = null;

        float value = ControllerHaptics.resolveEventIntensityMultiplier(config, HapticEvent.DAMAGE_TAKEN);
        assertEquals(HapticEvent.DAMAGE_TAKEN.defaultIntensityMultiplier(), value);
    }

    @Test
    void clampsConfiguredEventIntensity() {
        ControllerConfig config = ControllerConfig.createDefault();
        config.vibrationEventIntensity = new LinkedHashMap<>();
        config.vibrationEventIntensity.put(HapticEvent.BLOCK_BREAK.configKey(), 5.0F);
        config.vibrationEventIntensity.put(HapticEvent.LANDING.configKey(), -2.0F);

        float blockBreak = ControllerHaptics.resolveEventIntensityMultiplier(config, HapticEvent.BLOCK_BREAK);
        float landing = ControllerHaptics.resolveEventIntensityMultiplier(config, HapticEvent.LANDING);

        assertEquals(2.0F, blockBreak);
        assertEquals(0.0F, landing);
    }
}
