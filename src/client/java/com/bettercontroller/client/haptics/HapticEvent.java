package com.bettercontroller.client.haptics;

public enum HapticEvent {
    DAMAGE_TAKEN("damage_taken", 1.00F),
    EXPLOSION_NEARBY("explosion_nearby", 1.20F),
    BLOCK_BREAK("block_break", 0.45F),
    LANDING("landing", 0.70F);

    private final String configKey;
    private final float defaultIntensityMultiplier;

    HapticEvent(String configKey, float defaultIntensityMultiplier) {
        this.configKey = configKey;
        this.defaultIntensityMultiplier = defaultIntensityMultiplier;
    }

    public String configKey() {
        return configKey;
    }

    public float defaultIntensityMultiplier() {
        return defaultIntensityMultiplier;
    }
}
