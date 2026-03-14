# BetterController

BetterController is a client-side Fabric mod for premium controller support in Minecraft.

## Stack (as of 2026-03-14)
- Minecraft: `1.21.11`
- Java: `21`
- Fabric Loader: `0.18.4`
- Fabric API: `0.141.3+1.21.11`

## Current V2 Features
- Automatic controller connect/disconnect detection.
- Controller type detection (`xbox`, `playstation`, `switch`, `generic`) with auto-layout switching.
- Low-latency input pipeline:
  - `ControllerPoller -> ControllerSnapshot -> InputTranslator -> GameplayInputFrame -> MinecraftInputApplier`
- Full JSON action rebinding for gameplay + hotbar + menu navigation.
- Configurable deadzones, trigger threshold, look sensitivity, response curve, and camera smoothing.
- GUI controller navigation modules:
  - `GuiNavigationController`
  - `GuiFocusElement`
  - `GuiInputRouter`
- Radial quick menu modules:
  - `RadialMenu`
  - `RadialMenuSlot`
  - `RadialMenuController`
  - `RadialMenuRenderer`
- Bedrock-inspired compact HUD hints near the hotbar (less intrusive).
- In-game settings shortcut (`Controller Settings`) in:
  - `Options -> Controls`
  - pause menu
  - options menu
- Settings screen includes:
  - HUD on/off toggle
  - deadzone/sensitivity/trigger sliders
  - look curve + smoothing controls
  - one-click ultra-fluid tuning preset
- Debug overlay (toggle with `F8`).
- Haptics architecture (`ControllerHaptics`, `HapticEvent`, `HapticProfile`) with graceful no-op fallback on unsupported backends.
- Chat binding support with optional Windows OSK launch.

## Config
- Runtime config path: `config/bettercontroller.json` (auto-created on first launch)
- Bundled template: `src/main/resources/bettercontroller.default.json`
- Example config: `examples/bettercontroller.example.json`
- Config reload is automatic when the file is saved.

### Important config keys
- `activeLayout`
- `controllerTypeLayouts`
- `movementDeadzone`
- `lookDeadzone`
- `lookSensitivityX`
- `lookSensitivityY`
- `lookSpeedMultiplier`
- `lookResponseCurve` (`linear`, `exponential_light`, `exponential_strong`)
- `cameraSmoothing`
- `cameraSmoothingStrength`
- `triggerThreshold`
- `menuAxisThreshold`
- `menuInitialRepeatDelayMs`
- `menuRepeatIntervalMs`
- `hudHintsEnabled`
- `debugOverlayEnabled`
- `radialMenuEnabled`
- `radialMenuSlots`
- `vibrationEnabled`
- `vibrationIntensity` (`off`, `low`, `medium`, `strong`)
- `virtualKeyboardEnabled`

### Rebinding format
- Global bindings:
  - `bindings.<action>` supports one or many inputs (`["A"]`, `["RT", "RB"]`, etc.)
- Per-layout override:
  - `layouts.<layout>.bindings.<action>`
- Axes:
  - Global: `axes.move_x`, `axes.move_y`, `axes.look_x`, `axes.look_y`
  - Per-layout: `layouts.<layout>.axes.<axis>`
- Axis inversion:
  - prefix token with `-` (example: `"-RIGHT_Y"`).

### Quick tuning for smoother feel
- `movementDeadzone`: start around `0.12` to `0.16`
- `lookDeadzone`: start around `0.04` to `0.08`
- `lookSensitivityX` / `lookSensitivityY`: increase together for faster camera
- `lookSpeedMultiplier`: increase for faster turning with stick
- `lookResponseCurve`: `linear` for direct aim, `exponential_light` for softer center control
- `triggerThreshold`: lower to `0.35` to `0.45` if triggers feel slow to react
- `menuInitialRepeatDelayMs` / `menuRepeatIntervalMs`: lower values for faster menu navigation
- Or use the in-game `Apply Ultra Fluid Preset` button from BetterController settings.

## Build and Run
Requires JDK `21` (`java -version`).

Windows:
- if needed, set Java 21 for the current terminal:
  - ``$env:JAVA_HOME="C:\Path\To\jdk-21"``
  - ``$env:PATH="$env:JAVA_HOME\bin;$env:PATH"``
- `.\gradlew.bat build`
- `.\gradlew.bat runClient`

Linux/macOS:
- `./gradlew build`
- `./gradlew runClient`

Build output:
- `build/libs/`

## Creator Setup (Before Build)
If you are a creator/testing release builds, check these values first:
- `gradle.properties -> mod_version`: release/version tag shown in the mod metadata
- `gradle.properties -> archives_base_name`: output jar name
- `gradle.properties -> org.gradle.jvmargs`: increase memory for heavier local builds (example: `-Xmx2G`)

Recommended in-game config profile for recording/review sessions (`run/config/bettercontroller.json`):
- `hudHintsEnabled: false` (clean HUD footage)
- `debugOverlayEnabled: false`
- `lookSpeedMultiplier: 2.4` to `3.0` (faster camera feel)
- `menuInitialRepeatDelayMs: 90` to `120`
- `menuRepeatIntervalMs: 30` to `45`

Quick release check:
1. Update `mod_version` in `gradle.properties`.
2. Run `.\gradlew.bat build`.
3. Verify jar under `build/libs/`.
