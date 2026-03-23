package com.bettercontroller.client.input;

import com.bettercontroller.BetterControllerMod;
import com.bettercontroller.client.mixin.InputAccessor;
import com.bettercontroller.client.mixin.KeyBindingAccessor;
import com.bettercontroller.client.translation.GameplayInputFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

public final class MinecraftInputApplier {
    private static final long FLIGHT_ASSIST_WINDOW_MS = 280L;

    private static boolean warnedMissingBoundKeyAccessor;
    private static boolean warnedMissingMovementVectorAccessor;
    private boolean previousJumpHeld;
    private long pendingFlightAssistUntilMs;
    private int syntheticJumpPulseTicks;
    private boolean syntheticJumpRisingEdgeTick;
    private boolean previousAttackHeld;
    private boolean previousUseHeld;

    public void applyGameplayFrame(MinecraftClient client, GameplayInputFrame frame) {
        if (client == null || client.options == null || client.player == null) {
            return;
        }

        boolean effectiveJumpHeld = resolveEffectiveJumpHeld(frame.jumpHeld());

        applyAnalogMovement(client, frame, effectiveJumpHeld);
        setHold(client.options.forwardKey, frame.moveForward());
        setHold(client.options.backKey, frame.moveBackward());
        setHold(client.options.leftKey, frame.moveLeft());
        setHold(client.options.rightKey, frame.moveRight());
        applyJumpKey(client, client.options.jumpKey, effectiveJumpHeld);
        setHold(client.options.sprintKey, frame.sprintHeld());
        setHold(client.options.sneakKey, frame.sneakHeld());
        applyActionKey(client.options.attackKey, frame.attackHeld(), true);
        applyActionKey(client.options.useKey, frame.useHeld(), false);
        setHold(client.options.playerListKey, frame.playerListHeld());

        if (frame.inventoryTap()) {
            tap(client.options.inventoryKey);
        }
        if (frame.swapHandsTap()) {
            tap(client.options.swapHandsKey);
        }
        if (frame.dropTap()) {
            tap(client.options.dropKey);
        }
        if (frame.chatTap()) {
            tap(client.options.chatKey);
        }
        if (frame.perspectiveTap()) {
            tap(client.options.togglePerspectiveKey);
        }
        if (frame.pickBlockTap()) {
            tap(client.options.pickItemKey);
        }
        if (frame.pauseTap()) {
            openPauseMenu(client);
        }
        if (frame.hotbarStep() != 0) {
            cycleSlot(client, frame.hotbarStep());
        }
        if (frame.hotbarSelect() >= 0) {
            selectSlot(client, frame.hotbarSelect());
        }
    }

    public void applyLookDelta(MinecraftClient client, double deltaX, double deltaY) {
        if (client == null || client.player == null) {
            return;
        }
        if (deltaX == 0.0D && deltaY == 0.0D) {
            return;
        }
        client.player.changeLookDirection(deltaX, deltaY);
    }

    public void releaseGameplayHolds(MinecraftClient client) {
        if (client == null || client.options == null) {
            return;
        }

        clearAnalogMovement(client);
        setHold(client.options.forwardKey, false);
        setHold(client.options.backKey, false);
        setHold(client.options.leftKey, false);
        setHold(client.options.rightKey, false);
        setHold(client.options.jumpKey, false);
        setHold(client.options.sprintKey, false);
        setHold(client.options.sneakKey, false);
        setHold(client.options.attackKey, false);
        setHold(client.options.useKey, false);
        setHold(client.options.playerListKey, false);
        previousJumpHeld = false;
        pendingFlightAssistUntilMs = 0L;
        syntheticJumpPulseTicks = 0;
        syntheticJumpRisingEdgeTick = false;
        previousAttackHeld = false;
        previousUseHeld = false;
    }

    private boolean resolveEffectiveJumpHeld(boolean rawJumpHeld) {
        syntheticJumpRisingEdgeTick = false;
        if (syntheticJumpPulseTicks <= 0) {
            return rawJumpHeld;
        }
        if (syntheticJumpPulseTicks == 2) {
            syntheticJumpPulseTicks = 1;
            return false;
        }
        syntheticJumpPulseTicks = 0;
        syntheticJumpRisingEdgeTick = true;
        return true;
    }

    private static void applyAnalogMovement(MinecraftClient client, GameplayInputFrame frame, boolean jumpHeld) {
        if (client.player == null || client.player.input == null) {
            return;
        }

        Input input = client.player.input;
        input.playerInput = new PlayerInput(
            frame.moveForward(),
            frame.moveBackward(),
            frame.moveLeft(),
            frame.moveRight(),
            jumpHeld,
            frame.sneakHeld(),
            frame.sprintHeld()
        );

        setMovementVector(input, frame.processedMoveX(), -frame.processedMoveY());
    }

    private static void clearAnalogMovement(MinecraftClient client) {
        if (client == null || client.player == null || client.player.input == null) {
            return;
        }

        Input input = client.player.input;
        input.playerInput = PlayerInput.DEFAULT;
        setMovementVector(input, 0.0F, 0.0F);
    }

    private static void cycleSlot(MinecraftClient client, int offset) {
        if (client.player == null) {
            return;
        }

        int currentSlot = client.player.getInventory().getSelectedSlot();
        int nextSlot = Math.floorMod(currentSlot + offset, 9);
        selectSlot(client, nextSlot);
    }

    private static void selectSlot(MinecraftClient client, int slot) {
        if (client.player == null) {
            return;
        }
        int nextSlot = Math.floorMod(slot, 9);
        client.player.getInventory().setSelectedSlot(nextSlot);
        if (client.player.networkHandler != null) {
            client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(nextSlot));
        }
    }

    private static void openPauseMenu(MinecraftClient client) {
        if (client.currentScreen == null) {
            client.setScreen(new GameMenuScreen(true));
        }
    }

    private static void tap(KeyBinding keyBinding) {
        InputUtil.Key boundKey = readBoundKey(keyBinding);
        if (boundKey != null) {
            KeyBinding.onKeyPressed(boundKey);
            return;
        }

        keyBinding.setPressed(true);
        keyBinding.setPressed(false);
    }

    private static void setHold(KeyBinding keyBinding, boolean pressed) {
        keyBinding.setPressed(pressed);
    }

    private void applyJumpKey(MinecraftClient client, KeyBinding keyBinding, boolean held) {
        maybeApplyPendingFlightAssistTap(client, keyBinding, held);

        // Creative flight toggles rely on discrete jump presses; pulse on rising edge,
        // then keep the key held for normal jump/fly-up behavior.
        if (held && !previousJumpHeld) {
            tap(keyBinding);
            if (!syntheticJumpRisingEdgeTick && shouldArmFlightAssist(client)) {
                pendingFlightAssistUntilMs = System.currentTimeMillis() + FLIGHT_ASSIST_WINDOW_MS;
                BetterControllerMod.LOGGER.info(
                    "[FLY-HOTFIX] armed assist flying={} onGround={} held={}",
                    client.player != null && client.player.getAbilities().flying,
                    client.player != null && client.player.isOnGround(),
                    held
                );
            }
        }
        setHold(keyBinding, held);
        previousJumpHeld = held;
    }

    private void maybeApplyPendingFlightAssistTap(MinecraftClient client, KeyBinding keyBinding, boolean held) {
        if (pendingFlightAssistUntilMs <= 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now > pendingFlightAssistUntilMs) {
            pendingFlightAssistUntilMs = 0L;
            return;
        }
        if (!held) {
            return;
        }
        if (shouldFireFlightAssist(client)) {
            queueSyntheticJumpPulse();
            pendingFlightAssistUntilMs = 0L;
            BetterControllerMod.LOGGER.info(
                "[FLY-HOTFIX] queued synthetic pulse flying={} onGround={} held={}",
                client.player != null && client.player.getAbilities().flying,
                client.player != null && client.player.isOnGround(),
                held
            );
        }
    }

    private void queueSyntheticJumpPulse() {
        if (syntheticJumpPulseTicks <= 0) {
            syntheticJumpPulseTicks = 2;
            BetterControllerMod.LOGGER.info("[FLY-HOTFIX] synthetic pulse stage=release_then_rise");
        }
    }

    private static boolean shouldArmFlightAssist(MinecraftClient client) {
        if (client == null || client.player == null) {
            return false;
        }
        return client.player.getAbilities().allowFlying && !client.player.getAbilities().flying;
    }

    private static boolean shouldFireFlightAssist(MinecraftClient client) {
        if (client == null || client.player == null) {
            return false;
        }
        if (!client.player.getAbilities().allowFlying || client.player.getAbilities().flying) {
            return false;
        }
        // Assist second tap while airborne to make creative/fly toggles more reliable on controllers.
        return !client.player.isOnGround();
    }

    private void applyActionKey(KeyBinding keyBinding, boolean held, boolean isAttack) {
        boolean wasHeld = isAttack ? previousAttackHeld : previousUseHeld;
        // Pulse a tap on rising edge so actions also trigger when not targeting blocks/entities.
        if (held && !wasHeld) {
            tap(keyBinding);
        }
        setHold(keyBinding, held);
        if (isAttack) {
            previousAttackHeld = held;
        } else {
            previousUseHeld = held;
        }
    }

    private static InputUtil.Key readBoundKey(KeyBinding keyBinding) {
        if (keyBinding instanceof KeyBindingAccessor accessor) {
            return accessor.bettercontroller$getBoundKey();
        }
        if (!warnedMissingBoundKeyAccessor) {
            warnedMissingBoundKeyAccessor = true;
            BetterControllerMod.LOGGER.warn("KeyBinding accessor unavailable; controller tap fallback path is active.");
        }
        return null;
    }

    private static void setMovementVector(Input input, float moveX, float moveY) {
        if (input == null) {
            return;
        }

        float clampedX = Math.max(-1.0F, Math.min(1.0F, moveX));
        float clampedY = Math.max(-1.0F, Math.min(1.0F, moveY));
        float lengthSquared = (clampedX * clampedX) + (clampedY * clampedY);
        if (lengthSquared > 1.0F) {
            float scale = (float) (1.0D / Math.sqrt(lengthSquared));
            clampedX *= scale;
            clampedY *= scale;
        }

        if (input instanceof InputAccessor accessor) {
            accessor.bettercontroller$setMovementVector(new Vec2f(clampedX, clampedY));
            return;
        }
        if (!warnedMissingMovementVectorAccessor) {
            warnedMissingMovementVectorAccessor = true;
            BetterControllerMod.LOGGER.warn("Input accessor unavailable; analog movement vector could not be applied.");
        }
    }
}
