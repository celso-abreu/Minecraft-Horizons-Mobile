# Minecraft Horizons Mobile

Android NativeActivity host for the Rust game engine in `android/upstream`. The first target is
`arm64-v8a`; the APK does not include Minecraft's proprietary client/server files, Java runtime,
or account data. The user imports resources prepared from their own Minecraft Java Edition
installation.

## Build a debug APK

Required on the build machine:

- JDK 17.
- Android SDK Platform 35, Build Tools, and NDK `27.2.12479018`.
- Gradle 8.9 or newer compatible with Android Gradle Plugin 8.7.3.
- Rust 1.97 or newer installed with `rustup`; the Gradle task installs the
  `aarch64-linux-android` Rust target.

From the repository root:

```sh
git submodule update --init --recursive
cd android
gradle :app:assembleDebug
```

Gradle idempotently applies the tracked Android adaptation patch to the pinned Horizon submodule,
then builds the Rust `cdylib` against the Android NDK, stages it as
`lib/arm64-v8a/libhorizon_android.so`, and packages the Java setup Activity and NativeActivity in:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Open the app, import the
resource ZIP described below, and then start the game. Only arm64 Android devices are packaged.

## GitHub Actions

`.github/workflows/android-apk.yml` builds the debug APK on pushes, pull requests to `main`, and
manual dispatch. A successful run publishes `app-debug.apk` as the downloadable artifact
`minecraft-horizons-android-debug-<commit>`; the artifact contains no Minecraft resource bundle.

## Prepare the user's resource ZIP

On a desktop with the user's Minecraft Java Edition installed and a compatible Java runtime, from
the Horizon submodule:

```sh
cd android/upstream
cargo run --release -p horizon-prepare -- \
  --prepare --complete \
  --minecraft-dir "$HOME/.minecraft" \
  --root "$HOME/horizon-mobile-resources"
```

Create a ZIP containing the generated `26.3` directory itself (not just its contents):

```sh
cd "$HOME/horizon-mobile-resources/.local/horizon-client"
zip -r "$HOME/horizon-resources-26.3.zip" 26.3
```

Transfer that ZIP to the phone and choose it with **Choose prepared resources (.zip)**. The app
checks the manifest version and schema, extracts into private app storage, and never asks for
broad shared-storage access. The ZIP contains copyrighted game data: do not commit or redistribute
it.

## Current validation status

The Rust touch controls are now connected to Winit touch events, gamepad actions, camera targeting,
HUD drawing, hotbar selection, and menu/inventory pointer input. Android currently leaves the
platform audio and physical gamepad backends disabled; they need dedicated mobile integration. The
Gradle/NDK build path is configured, but this repository state has **not** yet been compiled into an
APK or exercised on an Android device. The current development environment has no Rust/Cargo, JDK,
Gradle, Android SDK/NDK, or `adb`; a local build and device validation are still required before
calling the APK functional.
