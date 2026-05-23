# BetterController

<p align="center">
  <a href="https://github.com/Nekuzaky/BetterController/actions/workflows/ci.yml">
    <img alt="CI" src="https://github.com/Nekuzaky/BetterController/actions/workflows/ci.yml/badge.svg">
  </a>
  <img alt="Minecraft" src="https://img.shields.io/badge/minecraft-1.21.11-62b132">
  <img alt="Fabric" src="https://img.shields.io/badge/fabric-0.18.4-blueviolet">
  <img alt="Java" src="https://img.shields.io/badge/java-21-orange">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-blue">
</p>

Client-side Fabric mod that lets you play Minecraft Java with a gamepad. Designed
to be small, predictable, and zero-allocation in the hot path.

## Highlights

- Auto detect / disconnect, plug-and-play with any GLFW-compatible controller
  (Xbox, PlayStation, Switch Pro, generic XInput).
- Analog look + movement with deadzones, anti-deadzone, response curve,
  adaptive smoothing.
- Controller-driven GUI navigation: pause menu, options, inventory, chests,
  creative search, world list, virtual keyboard for chat.
- Right stick scrolls scrollable screens (creative inventory, world list...).
- Creative double-tap A toggles flight reliably (no synthetic-pulse hacks).
- In-game settings screen with the five sliders that matter; everything else
  in `config/bettercontroller.json`.
- Debug overlay (`F8`) showing raw axes, triggers, pressed buttons.

## Stack

- Minecraft: `1.21.11`
- Java: `21`
- Fabric Loader: `0.18.4`
- Fabric API: `0.141.3+1.21.11`

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api) into `mods/`.
3. Drop `bettercontroller-<version>.jar` (from the [latest release](https://github.com/Nekuzaky/BetterController/releases)) into `mods/`.
4. Launch the game with a controller connected.

## Default bindings

| Action          | Button       |
|-----------------|--------------|
| Movement        | Left stick   |
| Look            | Right stick  |
| Jump            | A / Cross    |
| Sprint          | L3           |
| Sneak           | R3           |
| Attack / mine   | RT           |
| Use / place     | LT           |
| Inventory       | Y / Triangle |
| Drop item       | B / Circle   |
| Swap hands      | X / Square   |
| Pick block / Hotbar next | RB  |
| Hotbar previous | LB           |
| Open chat       | D-pad up     |
| Toggle perspective | D-pad down |
| Pause           | Start        |
| Player list     | Back / Select |

In menus: left stick / D-pad navigate, A confirms, B goes back, RB/LB switch
tabs, RT/LT page, right stick scrolls.

## Configuration

The runtime config lives at `config/bettercontroller.json` (auto-created on
first launch). Edits are picked up live within ~500 ms.

In-game settings screen (`Options -> Controls -> Controller Settings`, or the
button on the pause menu) exposes the five sliders most users want to tune:

- Movement deadzone
- Look sensitivity X
- Look sensitivity Y
- Look speed multiplier
- Trigger threshold

Everything else - look response curve, camera smoothing, key bindings, axes -
is edited in the JSON. See [`src/main/resources/bettercontroller.default.json`](src/main/resources/bettercontroller.default.json)
for the full schema and inline documentation.

### Rebinding

```jsonc
{
  "bindings": {
    "jump": ["A"],
    "attack": ["RT"],
    "menu_confirm": ["A"]
  },
  "axes": {
    "move_x": "LEFT_X",
    "move_y": "LEFT_Y",
    "look_x": "RIGHT_X",
    "look_y": "RIGHT_Y"
  }
}
```

- Bindings accept aliases (`A`/`CROSS`/`SWITCH_A`/`SOUTH`, `LB`/`L1`, ...).
- Prefix an axis token with `-` to invert it (e.g. `"-RIGHT_Y"`).
- A single action can have multiple bindings: `"hotbar_next": ["RB", "RT"]`.

### Quick tuning

| Goal                          | Try                                              |
|-------------------------------|--------------------------------------------------|
| Snappier camera               | `lookSpeedMultiplier` 2.5 – 3.0                  |
| Less stick drift              | `movementDeadzone` 0.14 – 0.18                   |
| Softer center, fast outer     | `lookResponseCurve` `exponential_light`          |
| Faster menu navigation        | `menuInitialRepeatDelayMs` 90, `menuRepeatIntervalMs` 30 |
| Slower triggers feel laggy    | Lower `triggerThreshold` to 0.35                 |

## Build from source

Requires JDK 21.

```bash
# Windows
.\gradlew.bat build
.\gradlew.bat runClient

# Linux / macOS
./gradlew build
./gradlew runClient
```

Artifacts land in `build/libs/bettercontroller-<version>.jar`.

## Architecture

```
ControllerPoller
   |  ControllerSnapshot
   v
InputTranslator ---->  GameplayInputFrame  (mutable, reused per tick)
   |
   +--> MinecraftInputApplier  -- in-world: keybindings + analog vector
   |
   +--> ControllerGuiNavigationHooks
            |
            v
        GuiNavigationController
            |  delegates to:
            +-- NavigationModeResolver   (widget / list / inventory / text)
            +-- SlotNavigator            (handled screens)
            +-- WidgetNavigator          (clickable widgets)
            +-- CreativeTabNavigator     (creative inventory tabs)
            +-- CursorCaptureManager     (hide / restore OS cursor)
```

### Design constraints (NASA Power of 10, adapted)

- All functions stay under 60 lines.
- The tick loop is zero-allocation in steady state: `GameplayInputFrame` is a
  single mutable instance reused every tick; `input.movementVector` is set in
  place; we no longer write `input.playerInput` (Minecraft's `KeyboardInput.tick`
  rebuilds it from keybindings, which preserves the rising-edge detection used
  by creative double-tap fly).
- No reflection: `setSelectedTab` on the creative inventory and the
  `selectedTab` static field are accessed via a Mixin Accessor / Invoker.
- All loops are bounded (fixed-size snapshots, finite widget/slot lists).
- Public entry points validate `null` at the boundary instead of asserting
  inside the hot path.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and the
[pull request template](.github/PULL_REQUEST_TEMPLATE.md). Bug reports and
feature requests go through the
[issue templates](.github/ISSUE_TEMPLATE).

## License

[MIT](LICENSE).
