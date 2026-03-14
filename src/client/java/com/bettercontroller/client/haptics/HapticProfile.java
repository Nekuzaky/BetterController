package com.bettercontroller.client.haptics;

import java.util.Locale;

public record HapticProfile(float intensityMultiplier) {
    public static HapticProfile fromConfig(String intensitySetting) {
        if (intensitySetting == null) {
            return new HapticProfile(0.65F);
        }

        return switch (intensitySetting.toLowerCase(Locale.ROOT)) {
            case "off" -> new HapticProfile(0.0F);
            case "low" -> new HapticProfile(0.35F);
            case "strong" -> new HapticProfile(1.0F);
            default -> new HapticProfile(0.65F);
        };
    }
}
