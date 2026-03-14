package com.bettercontroller.client.translation;

public record GameplayInputFrame(
    boolean moveLeft,
    boolean moveRight,
    boolean moveForward,
    boolean moveBackward,
    boolean jumpHeld,
    boolean sprintHeld,
    boolean sneakHeld,
    boolean attackHeld,
    boolean useHeld,
    boolean playerListHeld,
    boolean inventoryTap,
    boolean swapHandsTap,
    boolean dropTap,
    boolean chatTap,
    boolean perspectiveTap,
    boolean pauseTap,
    boolean pickBlockTap,
    int hotbarStep,
    int hotbarSelect,
    double lookDeltaX,
    double lookDeltaY,
    float processedMoveX,
    float processedMoveY,
    float processedLookX,
    float processedLookY
) {
    public static GameplayInputFrame empty() {
        return new GameplayInputFrame(
            false, false, false, false,
            false, false, false, false, false, false,
            false, false, false, false, false, false, false,
            0, -1, 0.0D, 0.0D,
            0.0F, 0.0F, 0.0F, 0.0F
        );
    }
}
