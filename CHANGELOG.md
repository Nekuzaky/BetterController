# Changelog

All notable changes to BetterController are documented in this file. The format
loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and
the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - Initial public release

First clean release. The mod's scope is intentionally limited to *playing
Minecraft Java with a gamepad*; everything else has been removed.

### Added
- Plug-and-play GLFW controller detection (Xbox, PlayStation, Switch Pro,
  generic) with live connect / disconnect.
- Analog look + movement pipeline with per-axis deadzone, anti-deadzone,
  configurable response curve, and adaptive smoothing.
- Controller-driven GUI navigation across vanilla screens, including:
  - widget navigation (buttons, sliders, list widgets)
  - inventory slot navigation with row/lane aware scoring
  - creative inventory tab cycling (RB / LB)
  - right stick analog scroll for any scrollable screen
  - virtual keyboard for chat / text fields
- In-game settings screen with five sliders (movement deadzone, look X / Y
  sensitivity, look speed multiplier, trigger threshold). Other options live
  in `config/bettercontroller.json`.
- Debug overlay (toggle with `F8`): raw axes, trigger values, glyph set,
  pressed buttons, processed move / look vectors.
- HUD button-prompt hints (jump / attack / use / inventory) when looking at
  something, with the glyph set adapted to the detected controller type.
- Mixin Accessor for `CreativeInventoryScreen.selectedTab` / `setSelectedTab`
  replaces the previous reflection path.

### Fixed
- Creative double-tap A now toggles flight correctly. The previous build
  overwrote `input.playerInput.jump`, which masked the rising-edge detection
  Minecraft uses in `ClientPlayerEntity.tickMovement`.
- RB / LB now cycle creative inventory tabs reliably (Mixin Accessor instead
  of reflection plus chained `||` fallbacks).
- The OS mouse cursor is now centralised in `CursorCaptureManager`: hidden
  while the controller is driving any non-text-input mode, restored when the
  player switches to mouse, leaves the screen, or unplugs the controller.
- `TextFieldWidget` no longer traps the controller on screens without
  inventory slots (e.g. Create World). Any directional input releases focus
  and resumes widget navigation.
- Virtual keyboard navigation now uses lane-based scoring so each row
  (Lowercase / Backspace / ..., Cursor < / Cursor > / Cancel / Enter) is
  reachable consistently via left / right inside the row.

### Changed
- Single generic input layout. Glyphs still adapt to the detected controller
  type for display, but bindings are no longer duplicated per-type.
- `GameplayInputFrame` is mutable and reused every tick. The tick loop no
  longer allocates a frame per tick.
- `GuiNavigationController` split into focused helpers (`NavigationMode`
  resolver, `SlotNavigator`, `WidgetNavigator`, `CursorCaptureManager`,
  `CreativeTabNavigator`); all methods stay under 60 lines.
- `BetterControllerSettingsScreen` rewritten around a slider factory; the rest
  of the configuration moved to the JSON file.

### Removed
- Haptics / rumble architecture (`ControllerHaptics`, `HapticEvent`,
  `HapticProfile`) — GLFW provides no cross-platform rumble in this stack and
  the runtime was a permanent no-op.
- Radial menu (`RadialMenu`, `RadialMenuController`, `RadialMenuRenderer`,
  `RadialMenuSlot`).
- Animated inventory help panel.
- `ControllerPreset` and multi-layout overrides
  (xbox / playstation / switch / generic).

[0.1.0]: https://github.com/Nekuzaky/BetterController/releases/tag/v0.1.0
