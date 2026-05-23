package com.bettercontroller.client.translation;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.polling.ControllerAxis;
import com.bettercontroller.client.polling.ControllerButton;
import com.bettercontroller.client.polling.ControllerSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputTranslatorTest {
    private InputTranslator translator;
    private ControllerSnapshot snapshot;
    private ControllerConfig config;
    private ControllerConfig.ResolvedLayout layout;

    @BeforeEach
    void setUp() {
        translator = new InputTranslator();
        snapshot = ControllerSnapshot.forTest();
        config = ControllerConfig.createDefault();
        layout = config.resolveLayout();
    }

    @Test
    void defaultSnapshotProducesNoMovementOrLook() {
        GameplayInputFrame frame = translator.translate(snapshot, config, layout);

        assertFalse(frame.moveForward());
        assertFalse(frame.moveBackward());
        assertFalse(frame.moveLeft());
        assertFalse(frame.moveRight());
        assertEquals(0.0f, frame.lookDeltaX(), 0.001f);
        assertEquals(0.0f, frame.lookDeltaY(), 0.001f);
    }

    @Test
    void movementBelowDeadzoneProducesNoMovement() {
        config.movementDeadzone = 0.14f;
        snapshot.simulateAxis(ControllerAxis.LEFT_X, 0.10f);

        GameplayInputFrame frame = translator.translate(snapshot, config, layout);

        assertFalse(frame.moveRight());
    }

    @Test
    void movementAboveDeadzoneProducesMovement() {
        config.movementDeadzone = 0.14f;
        snapshot.simulateAxis(ControllerAxis.LEFT_X, 0.50f);

        GameplayInputFrame frame = translator.translate(snapshot, config, layout);

        assertTrue(frame.moveRight());
    }

    @Test
    void negativeAxisProducesOppositeMovement() {
        config.movementDeadzone = 0.14f;
        snapshot.simulateAxis(ControllerAxis.LEFT_X, -0.50f);

        GameplayInputFrame frame = translator.translate(snapshot, config, layout);

        assertTrue(frame.moveLeft());
        assertFalse(frame.moveRight());
    }

    @Test
    void lookBelowDeadzoneProducesNoLook() {
        config.lookDeadzone = 0.07f;
        config.cameraSmoothing = false;
        snapshot.simulateAxis(ControllerAxis.RIGHT_X, 0.04f);

        GameplayInputFrame frame = translator.translate(snapshot, config, layout);

        assertEquals(0.0f, frame.lookDeltaX(), 0.001f);
    }

    @Test
    void lookAboveDeadzoneProducesLookDelta() {
        config.lookDeadzone = 0.07f;
        config.lookAntiDeadzone = 0.0f;
        config.cameraSmoothing = false;
        config.lookSensitivityX = 1.0f;
        snapshot.simulateAxis(ControllerAxis.RIGHT_X, 0.50f);

        GameplayInputFrame frame = translator.translate(snapshot, config, layout);

        assertTrue(frame.lookDeltaX() > 0.0f);
    }

    @Test
    void invertLookYNegatesVerticalDelta() {
        config.lookDeadzone = 0.0f;
        config.lookAntiDeadzone = 0.0f;
        config.cameraSmoothing = false;
        config.lookSensitivityY = 1.0f;
        snapshot.simulateAxis(ControllerAxis.RIGHT_Y, 0.5f);

        GameplayInputFrame normal = translator.translate(snapshot, config, layout);
        translator.resetState();

        config.invertLookY = true;
        InputTranslator invertedTranslator = new InputTranslator();
        GameplayInputFrame inverted = invertedTranslator.translate(snapshot, config, layout);

        assertEquals(-normal.lookDeltaY(), inverted.lookDeltaY(), 0.001f);
    }

    @Test
    void inventoryTapFiresOnlyOnRisingEdge() {
        snapshot.simulateButton(ControllerButton.NORTH, true);

        assertTrue(translator.translate(snapshot, config, layout).inventoryTap(), "first press should fire");
        assertFalse(translator.translate(snapshot, config, layout).inventoryTap(), "held press should not re-fire");
    }

    @Test
    void inventoryTapRefireAfterRelease() {
        snapshot.simulateButton(ControllerButton.NORTH, true);
        translator.translate(snapshot, config, layout);

        snapshot.simulateButton(ControllerButton.NORTH, false);
        translator.translate(snapshot, config, layout);

        snapshot.simulateButton(ControllerButton.NORTH, true);
        assertTrue(translator.translate(snapshot, config, layout).inventoryTap(), "re-press after release should fire");
    }

    @Test
    void resetStateClearsPreviousEdgeMemory() {
        snapshot.simulateButton(ControllerButton.NORTH, true);
        translator.translate(snapshot, config, layout);

        translator.resetState();

        assertTrue(translator.translate(snapshot, config, layout).inventoryTap(), "should fire again after reset");
    }

    @Test
    void hotbarNextProducesPositiveStep() {
        snapshot.simulateButton(ControllerButton.RIGHT_BUMPER, true);

        GameplayInputFrame frame = translator.translate(snapshot, config, layout);

        assertEquals(1, frame.hotbarStep());
    }

    @Test
    void hotbarPreviousProducesNegativeStep() {
        snapshot.simulateButton(ControllerButton.LEFT_BUMPER, true);

        GameplayInputFrame frame = translator.translate(snapshot, config, layout);

        assertEquals(-1, frame.hotbarStep());
    }

    @Test
    void hotbarNextAndPreviousSimultaneousCancelOut() {
        snapshot.simulateButton(ControllerButton.RIGHT_BUMPER, true);
        snapshot.simulateButton(ControllerButton.LEFT_BUMPER, true);

        GameplayInputFrame frame = translator.translate(snapshot, config, layout);

        assertEquals(0, frame.hotbarStep());
    }

    @Test
    void isActionPressedReturnsFalseWhenUnbound() {
        assertFalse(translator.isActionPressed(snapshot, config, layout, GameplayAction.JUMP));
    }

    @Test
    void isActionPressedReturnsTrueWhenButtonHeld() {
        snapshot.simulateButton(ControllerButton.SOUTH, true);

        assertTrue(translator.isActionPressed(snapshot, config, layout, GameplayAction.JUMP));
    }

    @Test
    void jumpIsHeldEveryFrame() {
        snapshot.simulateButton(ControllerButton.SOUTH, true);

        assertTrue(translator.translate(snapshot, config, layout).jumpHeld());
        assertTrue(translator.translate(snapshot, config, layout).jumpHeld(), "held keys stay true every frame");
    }

    @Test
    void sneakIsHeldWhenButtonPressed() {
        snapshot.simulateButton(ControllerButton.RIGHT_STICK, true);

        assertTrue(translator.translate(snapshot, config, layout).sneakHeld());
    }

    @Test
    void exponentialResponseCurveProducesLowerOutputThanLinear() {
        config.lookDeadzone = 0.0f;
        config.lookAntiDeadzone = 0.0f;
        config.cameraSmoothing = false;
        config.lookSensitivityX = 1.0f;
        snapshot.simulateAxis(ControllerAxis.RIGHT_X, 0.5f);

        config.lookResponseCurve = "linear";
        GameplayInputFrame linear = translator.translate(snapshot, config, layout);
        translator.resetState();

        config.lookResponseCurve = "exponential_strong";
        InputTranslator expTranslator = new InputTranslator();
        GameplayInputFrame exponential = expTranslator.translate(snapshot, config, layout);

        assertTrue(exponential.lookDeltaX() < linear.lookDeltaX(),
            "exponential curve should produce smaller output at mid-range stick");
    }
}
