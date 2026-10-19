# VR ADB Terminal — Wireless Debugging edition

A native Android app intended to open as a resizable **2D window** on Meta Quest 2 / Pro / 3 / 3S. It pairs with the headset's Wireless Debugging ADB service and runs shell commands through that connection. It does not require Shizuku.

## Features
- Automatic discovery / connection to the Wireless Debugging ADB service
- Manual pairing using the on-headset 6-digit pairing code and pairing port
- Manual connect using the main Wireless Debugging port
- Quick commands and a shell command field with output in the app window
- Remembers its ADB key/certificate across launches

## Setup
1. On Quest, enable Developer Mode if needed.
2. Open Quest **Settings → System → Developer → Wireless Debugging** and turn it on.
3. Tap **Auto Connect** in VR ADB Terminal. If it cannot find the service, choose **Pair Device** and keep the Quest's “Pair device with pairing code” panel open while entering the six-digit code and pairing port.
4. After pairing, tap Auto Connect if needed. Use the port on the main Wireless Debugging panel for Manual Connect (not the pairing port).
5. Enter commands such as `getprop ro.product.model`, `pm list packages`, `settings list system`, `dumpsys activity activities`, or `id`. `adb shell <command>` is also accepted as a convenience prefix.

## Scope and security
This app is an ADB client to the device's local wireless-debugging service. It is not the desktop ADB host utility; commands such as `adb install`, `adb push`, `adb pull`, or `adb devices` are not implemented. Shell capabilities and restrictions depend on the headset OS. Review commands before running them; shell commands may change device settings or app state. Disable Wireless Debugging when finished if you do not need it.

## Build
Requirements: JDK 17, Android SDK platform/build tools 35, Gradle 8.9, and internet access for Maven/JitPack dependencies.

```sh
gradle --no-daemon -p QuestShellAPK :app:assembleDebug
```

APK output: `QuestShellAPK/app/build/outputs/apk/debug/app-debug.apk`.

Dependencies include [libadb-android](https://github.com/MuntashirAkon/libadb-android) for ADB pairing and shell transport, [sun-security-android](https://github.com/MuntashirAkon/sun-security-android) for certificate creation, and HiddenApiBypass for Android's internal crypto classes.
