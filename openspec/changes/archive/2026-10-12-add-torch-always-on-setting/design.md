## Context

Torch state lives in `CameraViewModel._isTorchEnabled` (initially `false`); `CameraScreen` applies it to `cameraControl.enableTorch` and the button calls `setTorchEnabled(!enabled)`. The view model already holds `settingsRepository`. Settings are DataStore preferences exposed as flows in `SettingsRepository`, mapped into `SettingsUiState` by `SettingsViewModel`. The Scan section of `SettingsScreen` currently has only the default color mode.

## Goals / Non-Goals

**Goals:**
- One boolean preference, wired like the existing ones.
- Initial torch state follows the preference; button logic untouched.

**Non-Goals:**
- Remembering the manual toggle across visits.
- Changes to torch hardware handling or flash-on-capture.

## Decisions

- **Preference:** `booleanPreferencesKey("torch_always_on")`, default `false`, exposed as `Flow<Boolean>`; setter `setTorchAlwaysOn`.
- **Initial state:** each time the capture screen is entered, set the torch state from the preference (read once with `first()`), instead of a one-time init in the view model constructor, so a setting changed in Settings applies on return even if the view model survived. Manual toggles afterwards only mutate `_isTorchEnabled`. Alternative: combine the flow continuously with the manual state — rejected, it would fight the user's toggle when the setting emits.
- **Existing torch re-apply logic** in `CameraScreen` (re-apply on first analysis) already handles applying a `true` initial value once the camera session is active; no change needed there.
- **UI:** a `ListItem` with a `Switch` as trailing content in the Scan section, below the default color mode. A default-off value in `SettingsUiState` keeps previews and tests valid.
- **Strings:** add the English string; other locales fall back to English, matching how the project adds new strings (verify during apply).

## Risks / Trade-offs

- [Brief flash of "off" before the preference is read] → read is fast from DataStore; the camera session starts later than the read in practice, and the existing re-apply covers the rest.
- [Battery/heat with torch always on] → opt-in, and the user can toggle off per session.
