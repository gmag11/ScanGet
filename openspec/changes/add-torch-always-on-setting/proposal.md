## Why

The torch (flashlight) is off every time the camera opens and the user must turn it on by hand for each scanning session. Users who scan in poor light, or simply prefer the extra light, want it on automatically.

## What Changes

- New setting in the Scan section of Settings: "Always turn on the torch when using the camera" (off by default).
- When enabled, the torch is on whenever the capture screen is entered.
- The torch button on the capture screen keeps working as a toggle for the current session; turning it off there does not change the setting.

## Capabilities

### New Capabilities
- `camera-torch`: how the torch is controlled on the capture screen (initial state from the setting, manual toggle).

### Modified Capabilities

## Impact

- `SettingsRepository.kt`: new boolean preference and flow.
- `SettingsViewModel.kt`, `SettingsScreen.kt`: UI state and a switch row; new string resource (+ translations as the project handles them).
- `CameraViewModel.kt`: initial torch state taken from the setting.
- Tests: `SettingsRepositoryTest`, camera view-model test. No new dependencies.
