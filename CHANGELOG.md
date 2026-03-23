# Changelog

All notable changes to this project are documented in this file.

## [Unreleased] - 2026-03-23

### Fixed
- GUI navigation tick flow now runs every screen tick in `ControllerGuiNavigationHooks` (no more effective pulse-only gating).
- Added targeted hotfix instrumentation for GUI navigation with `[GUI-HOTFIX]` logs:
  - active screen class
  - resolved mode
  - selected widget/slot info
  - directional input and consumption paths (slider/list/inventory)
- Added left-stick menu hysteresis diagnostics in `GuiInputRouter` to validate clean press/release directional engagement.
- Creative inventory controller navigation now switches tabs reliably on horizontal boundary intent and tab/page inputs.
- Flight mode toggle reliability improved in `MinecraftInputApplier`:
  - jump assist uses a timed window instead of a single-tick pending flag
  - synthetic jump pulse (`release` then `rise`) is applied through `PlayerInput` to generate a reliable second jump edge for creative flight toggling
  - added `[FLY-HOTFIX]` event logs for arm/trigger visibility

### Notes
- Current hotfix logs are intentionally verbose for in-game diagnosis and may be reduced/removed once behavior is validated.
