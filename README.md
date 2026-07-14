# MyTV Launcher

MyTV Launcher is an open-source Android TV launcher inspired by ATK and Emotn-style home screens. It focuses on a clean TV-first home experience with local customization and no commercial activation flow.

## Features

- Android TV / Google TV launcher entry points
- Leanback and standard app discovery
- Weather card and weather detail screen
- App folders
- App hiding and manual sorting
- Search dialog for installed apps
- Wallpaper modes: built-in light/dark, local image, remote image URL, local video, and custom gradients
- Appearance controls such as 24-hour clock, minimal status bar, and section visibility
- Optional accessibility fallback for devices that do not allow third-party default Home apps
- Boot/update/wake return options

## Open-Source Scope

This repository is the open-source edition. The following commercial or device-control features have been removed:

- Paid unlock, trials, redemption codes, device codes, and code generator tooling
- Local ADB pairing and authorization
- Phone pairing web bridge
- Floating ADB setup overlay
- ADB, Conscrypt, sun-security, and QR-code dependencies used only by those removed features

## Requirements

- Android Studio Koala or newer, or a local Gradle/Android SDK setup
- JDK 17
- Android SDK 35
- Minimum Android version: Android 7.0 / API 24

## Build

From the project root:

```powershell
.\gradlew assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Release Signing

Release signing is optional and configured through Gradle properties:

```properties
MYTV_STORE_FILE=/path/to/keystore.jks
MYTV_STORE_PASSWORD=...
MYTV_KEY_ALIAS=...
MYTV_KEY_PASSWORD=...
```

Then build:

```powershell
.\gradlew assembleRelease
```

## Privacy

The launcher stores preferences locally on the device. It does not include paid activation, local ADB control, pairing web pages, analytics, billing, or account login code in this open-source edition.

The optional accessibility service is used only as a Home fallback on devices that block third-party launchers. Its description is included in `app/src/main/res/values/strings.xml`.

## License

Apache License 2.0. See `LICENSE`.
