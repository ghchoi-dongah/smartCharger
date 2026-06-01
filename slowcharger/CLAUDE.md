# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android kiosk application for a slow EV charger (model `DEVW007P`, vendor `Dongah/Humax`). Runs on Android 7.0+ (minSdk 24, targetSdk 35), communicates with cloud via OCPP 1.6 over WebSocket, and controls hardware via serial ports. UI is single-activity, fragment-based, full-screen kiosk mode.

## Build Commands

```powershell
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires keystore at D:\AndroidDongah\PlatformKeyClear\platform.jks)
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

Release APK output: `app/release/DEVW007.apk`

## Architecture

### Single Activity + Fragment State Machine

- **`MainActivity`** is the sole Activity. Fragment transitions are driven by `FragmentChange.java`, which reads the current `UiSeq` enum value and replaces the fragment container.
- **`UiSeq`** defines all possible screens (INIT, AUTH_SELECT, SOC, CHARGING, CHARGING_FINISH, FAULT, etc.).
- **`GlobalVariables`** holds static global state and all `Message.what` constants used by the Handler bus.
- **`ChargingCurrentData`** is the data model for the active charging session (energy, cost, timestamps).
- **`SharedModel`** (LiveData-based ViewModel) is used for Fragment-to-Fragment reactive communication.

### Communication Layers

| Layer | Class | Transport | Purpose |
|---|---|---|---|
| Cloud | `Socket.java` | WebSocket (OkHttp3 + TLS) | OCPP 1.6 message exchange |
| Vehicle | `PlcModem.java` | Serial `/dev/ttyS3` @ 115200 | ISO-IEC 61851-3 DC charging protocol |
| Hardware relay | `ControlBoard.java` | Serial `/dev/ttyS0` @ 38400 | Relay/sensor control board (CRC16, 62-byte payload) |
| RF Card | `RfCardReader.java` | Serial `/dev/ttyS5` | Abstract RF card reader |
| Cellular modem | `ClientSocket.java` | TCP | AT command interface for LTE modem |

### OCPP Message Flow

```
WebSocket frame
  → SocketReceiveMessage (parse JSON)
  → JSONCommunicator / FeatureRepository (match request/response)
  → ProcessHandler extends Handler (dispatch via Message.what)
  → MainActivity / Fragment method calls (UI update)
```

OCPP features live under `websocket/ocpp/`: `core/`, `firmware/`, `security/`, `smartcharging/`, `remotetrigger/`, `localauthlist/`, `reservation/`, `datatransfer/`.  
Vendor-specific extensions (DongAh, Humax) are implemented as `DataTransfer` custom actions in `datatransfer/`.

### Pages (UI Fragments)

All fragments are in `pages/`. Key ones:

- `InitFragment` – idle screen, shows QR code
- `AuthSelectFragment` – payment method selection (member card, credit card, QR)
- `MemberCardFragment` / `CreditCardFragment` – card reading screens
- `SocFragment` – State of Charge target selection
- `ChargingFragment` – active charging display
- `ChargingFinishFragment` – post-charge summary
- `FaultFragment` – error/fault display
- `ConfigSettingFragment` / `AdminPasswordFragment` – admin config UI
- `ControlDebugFragment` / `WebSocketDebugFragment` – debug screens

### Configuration

`ChargerConfiguration.java` manages persistent settings (OCPP server URL, charger ID, pricing, etc.) stored via `SharedPreferences`. All string keys are constants in that class.

## Key Packages

```
com.dongah.smartcharger/
├── basefunction/   # MainActivity, GlobalVariables, FragmentChange, UiSeq, ChargingCurrentData
├── pages/          # All UI Fragments
├── websocket/
│   ├── ocpp/       # OCPP 1.6 protocol implementation
│   ├── socket/     # WebSocket wrapper (OkHttp3), Connector model
│   └── tcpsocket/  # TCP client for modem AT commands
├── controlboard/   # Serial control board protocol
├── plc/            # PLC modem (ISO-IEC 61851-3) + request/receive handlers
├── rfcard/         # RF card reader abstraction
├── handler/        # ProcessHandler (main OCPP message dispatcher)
├── utils/          # SharedModel, LogDataSave, SftpUtil, FileManagement
└── android_serialport_api/ # JNI serial port wrapper
```

## Native Code

JNI serial port library is in `app/src/main/jni/`. NDK version 26.1.10909125. The `SerialPort.java` wrapper opens `/dev/ttyS*` devices.

## Important Notes

- **Korean market**: UI strings and comments mix Korean and English. String resources are in `res/values/strings.xml`.
- **Kiosk mode**: `MainActivity` hides system navigation bars; the app is designed to run unattended on a touchscreen.
- **Handler bus**: All cross-thread communication goes through `ProcessHandler` (Android `Handler`). Message type constants are `MSG_*` in `GlobalVariables`. Adding new OCPP features requires registering a new constant there and handling it in `ProcessHandler`.
- **No CI/CD**: Builds are done manually via Gradle. There are placeholder unit/instrumented tests only.
- **Signing**: Debug builds use a platform keystore at `D:\AndroidDongah\PlatformKeyClear\platform.jks` (not committed). Release builds require the same keystore to be present locally.
