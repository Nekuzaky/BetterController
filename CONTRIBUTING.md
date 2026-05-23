# Contributing

Thanks for thinking about contributing to BetterController.

## Scope

The mod has exactly one job: **let you play Minecraft Java with a gamepad**.
Features that don't directly serve that purpose (radial menus, vibration,
custom HUD overlays, gameplay tweaks, etc.) are out of scope and will be
declined.

When in doubt, open an issue first.

## Development setup

- JDK 21 (Temurin, JBR, GraalVM... anything Java 21 works).
- The Gradle wrapper handles everything else.

```bash
# Build + test
./gradlew build

# Launch the dev client
./gradlew runClient
```

Artifacts land in `build/libs/`.

## Code style

The project follows a few hard rules borrowed from
[NASA's "Power of Ten"](https://web.eecs.umich.edu/~imarkov/10rules.pdf),
adapted to Java:

1. **Functions stay under 60 lines.** Split before hitting the limit, even if
   it means extracting a helper used in one place.
2. **No allocation in the tick loop.** Reuse `GameplayInputFrame`; mutate
   `input.movementVector` in place; cache pre-computed values.
3. **Bound every loop.** Iterate over fixed-size snapshots or finite widget
   lists. No `while (true)` without an explicit exit condition.
4. **Restrict scope.** Prefer `private` and `final`; keep state local.
5. **Validate at the boundary, not inside hot paths.** Public methods reject
   `null` early; internal helpers may assume valid input.

CI runs `./gradlew build` with `-Xlint:all` on every push to `main`,
`develop`, and `feature/**` / `fix/**` branches. New warnings are noise; fix
them.

## Branching

- `main` is the stable branch. Tagged releases are cut from here.
- `develop` is the integration branch. PRs land here first.
- Feature work: `feature/<short-name>`.
- Bug fixes: `fix/<short-name>`.

## Pull requests

- Target `develop`.
- Fill in the PR template (summary, type, test plan).
- Tests are required for new translation / config logic. Manual gamepad
  testing is required for any change to the input or GUI navigation layer;
  describe what you tested in the PR description.
- Keep commits focused. Squash before merging if the history is noisy.

## Bug reports / feature requests

Use the issue templates. Include the controller model, OS, mod version, and
exact reproduction steps — those three things resolve 80% of debugging.

## Security

See [SECURITY.md](SECURITY.md).
