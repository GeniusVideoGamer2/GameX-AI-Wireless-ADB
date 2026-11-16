# GameX AI Wireless ADB — prototype 0.1

Android app prototype for connecting a phone to Sunny Prime TV Stick via **Termux ADB**.

## Requirements
- Termux (F-Droid), with `pkg update && pkg install android-tools`
- In Termux: `mkdir -p ~/.termux && printf 'allow-external-apps=true\n' >> ~/.termux/termux.properties && termux-reload-settings`
- Grant the app Termux RUN_COMMAND permission
- Phone and TV on the same Wi-Fi
- TV Wireless debugging enabled

Open TV's **Pair device with pairing code**, enter its pairing port and 6-digit code in the app. Then use the separate connection port to connect.

## Build
GitHub Actions → **Build Android APK** → **Run workflow**, then download the debug APK artifact. Debug APKs are for testing.

## Limitations
This is **not** an independent ADB client and **does not contain GPT-6**. It delegates to Termux and does not display the ADB command output inside the app. Pairing and Android 17 compatibility require device testing. It cannot flash ROMs or overwrite protected system apps. Do not disable critical system packages.

## Added controls
- Installed packages list (read-only)
- Treble, verified boot, dynamic partition and ABI checks (read-only)
- TV Home, Back, Volume Up/Down
- Offline Turkish keyword-to-command helper (not GPT-6)

## Planned, not implemented
- Standalone ADB pairing without Termux
- GPT-6 integration through a secure backend (never embed an API key in APK)
- APK installation/disable-enable package manager with confirmation and recovery
- In-app command output and connection status

**Security:** ROM flashing, bootloader unlocking and system partition changes are not implemented. Confirm target TV and back up important data before changing apps.
