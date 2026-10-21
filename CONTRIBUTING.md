# Contributing

Thanks for helping. This guide covers the repo layout, the dev loop, and the few rules that keep the library
consistent.

## Principles

- **The API mirrors `expo-glass-effect`.** New props only if iOS has them too.
- **Motion is fixed by the library**, as on iOS. No props for springs, scale, bulge or refraction. If a value
  needs changing, it changes in `GlazeTuning.kt` for everyone.
- **Idle costs nothing on Android.** Never call `invalidate()` from an `OnPreDrawListener`: `ViewRootImpl`
  clears `mWillDrawSoon` before dispatching pre-draw listeners, so it schedules another frame and loops
  forever. Re-record `RenderNode`s there instead (see `GlazeView.recordBackdrop`).
- **Glass never draws into a backdrop** (`GlazeRegistry`). This prevents glass refracting glass and
  `RenderNode` reference cycles.
- **Never take touches from JS.** The Android host observes touches in `dispatchTouchEvent` and never calls
  `performClick` or `requestDisallowInterceptTouchEvent`.

## Layout

```text
src/                     TypeScript API; .ios / .android files are platform-specific
  __tests__/             Jest (runs as iOS and Android projects)
android/src/main/java/dev/glaze/
  GlazeModule.kt         Expo module: views, props, helpers
  GlazeView.kt           Host view: touch, springs, repaint policy
  GlazeContainerView.kt  Two-shape merge
  GlazeRegistry.kt       Backdrop capture without glass
  GlassPainter.kt        Per-tier rendering
  GlazeTier.kt           Quality tier selection
  GlazeTuning.kt         Every motion and material constant
  engine/                Vendored from QWEA0/Liquid-Glass-Android (MIT)
example/                 Expo app with the test screens
docs/TESTING.md          Device checklist
```

iOS has no native code: it's TypeScript over `expo-glass-effect` and `expo-blur`.

## Dev loop

Requirements: Node 24 (see `.nvmrc`), JDK 17 and the Android SDK for Android, Xcode 26 for iOS.

```sh
npm install
npm run typecheck
npm run lint
npm test
npm run build              # build/ is committed so installs from GitHub work; CI fails if it's stale
```

Example app (uses the repo's source through `example/metro.config.js`):

```sh
cd example
npm install
npx expo run:android       # or run:ios
```

Native changes are picked up by rebuilding the app. JS changes reload with Metro.

Fast Android compile check without building the whole app:

```sh
cd example && npx expo prebuild --platform android --no-install
cd android && ./gradlew :react-native-glaze:compileDebugKotlin :react-native-glaze:lintDebug \
  -x :react-native-worklets:lintAnalyzeDebug -x :react-native-reanimated:lintAnalyzeDebug \
  -x :expo-modules-core:lintAnalyzeDebug
```

On a low-memory machine, add `org.gradle.workers.max=2` and `kotlin.compiler.execution.strategy=in-process`
to `example/android/gradle.properties`.

## Pull requests

- Keep them focused. CI must pass: JS checks, the Android module and app build, and the iOS simulator build.
- Run the relevant parts of `docs/TESTING.md` on a device and say which ones in the PR.
- Changes to `GlazeTuning.kt` need a side-by-side video against iOS 26.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, …).
- Update `CHANGELOG.md` for user-facing changes.

## Releasing

1. Bump `version` in `package.json`, move the `CHANGELOG.md` entries under the new version, run
   `npm run build`, commit.
2. Create a GitHub release with tag `v<version>`. The Release workflow checks the tag, runs the checks
   and publishes to npm with provenance. It needs an `NPM_TOKEN` repository secret.

## Upstream engine

`android/.../engine/` is vendored from QWEA0/Liquid-Glass-Android `v2.0.11`. Each file header lists our
changes. When pulling upstream fixes, keep the shader identical unless there's a reason, and update
`THIRD_PARTY_NOTICES.md` with the new version.
