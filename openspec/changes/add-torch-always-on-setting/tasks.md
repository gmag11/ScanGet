## 1. Setting

- [x] 1.1 Add the `torch_always_on` boolean preference to `SettingsRepository` (flow, default false, setter). Verify with a `SettingsRepositoryTest` case: default false, persists true after set.
- [x] 1.2 Add the field to `SettingsUiState`, combine it in `SettingsViewModel`, and add `setTorchAlwaysOn`. Verify by compiling and by a view-model state test if existing tests cover the pattern.
- [x] 1.3 Add the switch row to the Scan section of `SettingsScreen` with a new string resource. Verify manually: the switch toggles and survives an app restart; previews still compile.

## 2. Camera

- [x] 2.1 In `CameraViewModel`, set the torch state from the preference each time the capture screen is entered, keeping `setTorchEnabled` unchanged. Verify with a unit test: setting on → torch state true on entry; setting off → false; manual toggle does not alter the preference.
- [x] 2.2 Manual check on a device: option on → torch lit on opening the camera, button shows on, pressing it turns the torch off, reopening turns it on again; option off → behaves as before.

## 3. Verification

- [x] 3.1 Run `./gradlew :app:testDebugUnitTest` and confirm everything passes.
