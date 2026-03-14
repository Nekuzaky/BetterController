package com.bettercontroller.client.input;

import com.bettercontroller.client.translation.GameplayInputFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

import java.lang.reflect.Field;

public final class MinecraftInputApplier {
    private static final Field BOUND_KEY_FIELD = resolveBoundKeyField();
    private static final Field MOVEMENT_VECTOR_FIELD = resolveMovementVectorField();
    private boolean previousAttackHeld;
    private boolean previousUseHeld;

    public void applyGameplayFrame(MinecraftClient client, GameplayInputFrame frame) {
        if (client == null || client.options == null || client.player == null) {
            return;
        }

        applyAnalogMovement(client, frame);
        setHold(client.options.forwardKey, frame.moveForward());
        setHold(client.options.backKey, frame.moveBackward());
        setHold(client.options.leftKey, frame.moveLeft());
        setHold(client.options.rightKey, frame.moveRight());
        setHold(client.options.jumpKey, frame.jumpHeld());
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
        previousAttackHeld = false;
        previousUseHeld = false;
    }

    private static void applyAnalogMovement(MinecraftClient client, GameplayInputFrame frame) {
        if (client.player == null || client.player.input == null) {
            return;
        }

        Input input = client.player.input;
        input.playerInput = new PlayerInput(
            frame.moveForward(),
            frame.moveBackward(),
            frame.moveLeft(),
            frame.moveRight(),
            frame.jumpHeld(),
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
        if (BOUND_KEY_FIELD == null) {
            return null;
        }
        try {
            return (InputUtil.Key) BOUND_KEY_FIELD.get(keyBinding);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static Field resolveBoundKeyField() {
        try {
            Field field = KeyBinding.class.getDeclaredField("boundKey");
            field.setAccessible(true);
            return field;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void setMovementVector(Input input, float moveX, float moveY) {
        if (input == null || MOVEMENT_VECTOR_FIELD == null) {
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

        try {
            MOVEMENT_VECTOR_FIELD.set(input, new Vec2f(clampedX, clampedY));
        } catch (IllegalAccessException ignored) {
        }
    }

    private static Field resolveMovementVectorField() {
        try {
            Field field = Input.class.getDeclaredField("movementVector");
            field.setAccessible(true);
            return field;
        } catch (Exception ignored) {
            return null;
        }
    }
}
