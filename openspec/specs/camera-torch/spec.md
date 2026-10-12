# camera-torch Specification

## Purpose

Defines how the torch is switched on and off while the user captures pages with the camera.

## Requirements

### Requirement: Always-on torch setting
Settings SHALL offer an option, off by default, to turn the torch on whenever the camera is used. The choice SHALL persist across app restarts.

#### Scenario: Default
- **WHEN** the user has never changed the option
- **THEN** the option is off and the torch is off when the capture screen opens

#### Scenario: Option enabled
- **WHEN** the option is on and the user opens the capture screen
- **THEN** the torch is on, and the torch button shows its on state

#### Scenario: Option disabled
- **WHEN** the option is off and the user opens the capture screen
- **THEN** the torch is off until the user presses the torch button

### Requirement: Torch button remains a toggle
The torch button on the capture screen SHALL toggle the torch on and off regardless of the setting. A manual toggle SHALL affect only the current visit to the capture screen and SHALL NOT change the setting.

#### Scenario: Turning the torch off manually
- **WHEN** the option is on, the torch is on, and the user presses the torch button
- **THEN** the torch turns off and the setting stays on

#### Scenario: Next visit
- **WHEN** the user turned the torch off manually and later reopens the capture screen with the option on
- **THEN** the torch is on again

#### Scenario: Torch not available
- **WHEN** the device has no torch
- **THEN** enabling the option has no effect and the capture screen works as before
