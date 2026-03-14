package com.bettercontroller.client.input;

import com.bettercontroller.client.chat.VirtualKeyboardLauncher;
import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.config.ControllerConfigManager;
import com.bettercontroller.client.gui.ControllerGuiNavigationHooks;
import com.bettercontroller.client.haptics.ControllerHaptics;
import com.bettercontroller.client.haptics.HapticEvent;
import com.bettercontroller.client.polling.ControllerPoller;
import com.bettercontroller.client.polling.ControllerSnapshot;
import com.bettercontroller.client.polling.ControllerType;
import com.bettercontroller.client.radial.RadialMenu;
import com.bettercontroller.client.radial.RadialMenuController;
import com.bettercontroller.client.translation.GameplayInputFrame;
import com.bettercontroller.client.translation.InputTranslator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;

import java.nio.file.Path;
import java.util.function.Consumer;

public final class ControllerRuntime {
    private final ControllerPoller controllerPoller = new ControllerPoller();
    private final ControllerConfigManager configManager = new ControllerConfigManager();
    private final InputTranslator inputTranslator = new InputTranslator();
    private final MinecraftInputApplier inputApplier = new MinecraftInputApplier();
    private final ControllerGuiNavigationHooks guiNavigationHooks = new ControllerGuiNavigationHooks(inputTranslator);
    private final RadialMenuController radialMenuController = new RadialMenuController();
    private final ControllerHaptics controllerHaptics = new ControllerHaptics();
    private final VirtualKeyboardLauncher virtualKeyboardLauncher = new VirtualKeyboardLauncher();

    private ControllerSnapshot latestSnapshot;
    private ControllerConfig latestConfig;
    private ControllerConfig.ResolvedLayout activeLayout;
    private ControllerType activeControllerType = ControllerType.NONE;
    private GameplayInputFrame latestFrame = GameplayInputFrame.empty();
    private float previousHealth = -1.0F;
    private boolean previousOnGround;
    private long lastBlockBreakHapticMs;
    private long lastRenderLookUpdateMs;

    public ControllerRuntime() {
        this.configManager.load();
        this.latestConfig = configManager.getConfig();
    }

    public Path configPath() {
        return configManager.configPath();
    }

    public ControllerSnapshot latestSnapshot() {
        return latestSnapshot;
    }

    public ControllerConfig latestConfig() {
        return latestConfig;
    }

    public ControllerConfig.ResolvedLayout activeLayout() {
        return activeLayout;
    }

    public ControllerType activeControllerType() {
        return activeControllerType;
    }

    public GameplayInputFrame latestFrame() {
        return latestFrame;
    }

    public RadialMenu radialMenu() {
        return radialMenuController.menu();
    }

    public boolean isHudHintsEnabled() {
        return latestConfig != null && latestConfig.hudHintsEnabled;
    }

    public void setHudHintsEnabled(boolean enabled) {
        updateConfig(config -> config.hudHintsEnabled = enabled);
    }

    public void applyUltraFluidPreset() {
        updateConfig(config -> {
            config.movementDeadzone = 0.08F;
            config.lookDeadzone = 0.03F;
            config.lookSensitivityX = 18.0F;
            config.lookSensitivityY = 16.5F;
            config.lookSpeedMultiplier = 2.6F;
            config.lookResponseCurve = "linear";
            config.cameraSmoothing = false;
            config.cameraSmoothingStrength = 0.12F;
            config.triggerThreshold = 0.30F;
            config.menuAxisThreshold = 0.30F;
            config.menuInitialRepeatDelayMs = 100;
            config.menuRepeatIntervalMs = 38;
        });
    }

    public void updateConfig(Consumer<ControllerConfig> updater) {
        if (updater == null) {
            return;
        }
        ControllerConfig config = mutableConfig();
        updater.accept(config);
        latestConfig = configManager.save(config);
    }

    public void tick(MinecraftClient client) {
        if (client == null) {
            return;
        }

        latestConfig = configManager.getConfig();
        latestSnapshot = controllerPoller.pollSnapshot();

        if (latestSnapshot == null || !latestSnapshot.isConnected()) {
            activeControllerType = ControllerType.NONE;
            activeLayout = null;
            latestFrame = GameplayInputFrame.empty();
            inputApplier.releaseGameplayHolds(client);
            inputTranslator.resetState();
            guiNavigationHooks.reset();
            radialMenuController.reset();
            resetRenderLookClock();
            resetHapticBaseline();
            return;
        }

        activeControllerType = latestSnapshot.controllerType();
        activeLayout = latestConfig.resolveLayout(activeControllerType);

        if (!latestConfig.autoActivateOnController) {
            resetRenderLookClock();
            return;
        }

        int radialSelectedSlot = radialMenuController.tick(latestSnapshot, latestConfig, activeLayout, inputTranslator);

        if (client.currentScreen != null) {
            latestFrame = GameplayInputFrame.empty();
            inputApplier.releaseGameplayHolds(client);
            inputTranslator.resetState();
            guiNavigationHooks.onScreenTick(client, latestSnapshot, latestConfig, activeLayout);
            resetRenderLookClock();
            return;
        }

        if (client.player == null || client.world == null) {
            latestFrame = GameplayInputFrame.empty();
            inputApplier.releaseGameplayHolds(client);
            inputTranslator.resetState();
            guiNavigationHooks.reset();
            resetRenderLookClock();
            return;
        }

        GameplayInputFrame frame = inputTranslator.translate(latestSnapshot, latestConfig, activeLayout);
        if (radialSelectedSlot >= 0) {
            frame = replaceHotbarSelection(frame, radialSelectedSlot);
        }

        if (radialMenuController.menu().isActive()) {
            latestFrame = frame;
            inputApplier.releaseGameplayHolds(client);
            resetRenderLookClock();
            return;
        }

        if (frame.chatTap()) {
            virtualKeyboardLauncher.tryOpen(latestConfig);
        }

        inputApplier.applyGameplayFrame(client, frame);
        latestFrame = frame;
        processHaptics(client, frame);
    }

    public void onRenderFrame(MinecraftClient client) {
        if (client == null) {
            return;
        }
        if (latestConfig == null || !latestConfig.autoActivateOnController) {
            resetRenderLookClock();
            return;
        }
        if (latestSnapshot == null || !latestSnapshot.isConnected() || latestFrame == null) {
            resetRenderLookClock();
            return;
        }
        if (client.currentScreen != null || client.player == null || client.world == null) {
            resetRenderLookClock();
            return;
        }
        if (radialMenuController.menu().isActive()) {
            resetRenderLookClock();
            return;
        }

        long now = System.currentTimeMillis();
        long deltaMs = lastRenderLookUpdateMs == 0L ? 16L : now - lastRenderLookUpdateMs;
        deltaMs = Math.max(4L, Math.min(deltaMs, 75L));
        lastRenderLookUpdateMs = now;

        double baseScale = deltaMs / 33.333D;
        double speedMultiplier = latestConfig.lookSpeedMultiplier;
        double stickMagnitude = Math.max(
            Math.abs(latestFrame.processedLookX()),
            Math.abs(latestFrame.processedLookY())
        );
        double turnBoost = 1.0D + Math.max(0.0D, stickMagnitude - 0.60D) * 1.3D;
        double frameScale = clamp(baseScale * speedMultiplier * turnBoost, 0.20D, 8.50D);
        inputApplier.applyLookDelta(
            client,
            latestFrame.lookDeltaX() * frameScale,
            latestFrame.lookDeltaY() * frameScale
        );
    }

    private void processHaptics(MinecraftClient client, GameplayInputFrame frame) {
        if (client.player == null || latestSnapshot == null || latestConfig == null) {
            return;
        }

        float currentHealth = client.player.getHealth();
        boolean onGround = client.player.isOnGround();

        if (previousHealth >= 0.0F && currentHealth < previousHealth) {
            controllerHaptics.trigger(HapticEvent.DAMAGE_TAKEN, latestConfig, latestSnapshot);
            if ((previousHealth - currentHealth) >= 4.0F) {
                controllerHaptics.trigger(HapticEvent.EXPLOSION_NEARBY, latestConfig, latestSnapshot);
            }
        }

        if (!previousOnGround && onGround && client.player.fallDistance > 2.0F) {
            controllerHaptics.trigger(HapticEvent.LANDING, latestConfig, latestSnapshot);
        }

        if (frame.attackHeld() && client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            long now = System.currentTimeMillis();
            if (now - lastBlockBreakHapticMs > 250L) {
                lastBlockBreakHapticMs = now;
                controllerHaptics.trigger(HapticEvent.BLOCK_BREAK, latestConfig, latestSnapshot);
            }
        }

        previousHealth = currentHealth;
        previousOnGround = onGround;
    }

    private void resetHapticBaseline() {
        previousHealth = -1.0F;
        previousOnGround = false;
        lastBlockBreakHapticMs = 0L;
    }

    private void resetRenderLookClock() {
        lastRenderLookUpdateMs = 0L;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private ControllerConfig mutableConfig() {
        ControllerConfig config = latestConfig != null ? latestConfig : configManager.getConfig();
        if (config == null) {
            config = ControllerConfig.createDefault();
        }
        return config;
    }

    private static GameplayInputFrame replaceHotbarSelection(GameplayInputFrame frame, int slot) {
        return new GameplayInputFrame(
            frame.moveLeft(),
            frame.moveRight(),
            frame.moveForward(),
            frame.moveBackward(),
            frame.jumpHeld(),
            frame.sprintHeld(),
            frame.sneakHeld(),
            frame.attackHeld(),
            frame.useHeld(),
            frame.playerListHeld(),
            frame.inventoryTap(),
            frame.swapHandsTap(),
            frame.dropTap(),
            frame.chatTap(),
            frame.perspectiveTap(),
            frame.pauseTap(),
            frame.pickBlockTap(),
            frame.hotbarStep(),
            slot,
            frame.lookDeltaX(),
            frame.lookDeltaY(),
            frame.processedMoveX(),
            frame.processedMoveY(),
            frame.processedLookX(),
            frame.processedLookY()
        );
    }
}
