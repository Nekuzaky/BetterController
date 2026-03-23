package com.bettercontroller.client.config;

import com.bettercontroller.client.haptics.HapticEvent;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerConfigDefaultsTest {
    @Test
    void ensureDefaultsBackfillsEventIntensityMap() {
        ControllerConfig config = ControllerConfig.createDefault();
        config.vibrationEventIntensity = new LinkedHashMap<>();
        config.vibrationEventIntensity.put(HapticEvent.DAMAGE_TAKEN.configKey(), 0.8F);

        config.ensureDefaults();

        assertNotNull(config.vibrationEventIntensity);
        for (HapticEvent event : HapticEvent.values()) {
            assertTrue(config.vibrationEventIntensity.containsKey(event.configKey()));
        }
    }
}
