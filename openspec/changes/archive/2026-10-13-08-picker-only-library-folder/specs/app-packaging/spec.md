## MODIFIED Requirements

### Requirement: App name
The app SHALL call itself ScanGet: in the launcher, in the system lists of apps and in its own
texts that speak of the app by its name. The public intent action SHALL NOT change.

#### Scenario: Launcher
- **WHEN** the app is installed
- **THEN** its icon is labeled "ScanGet"

#### Scenario: Texts of the app
- **WHEN** the library setup screen asks where the app should keep the documents
- **THEN** it speaks of ScanGet, in English and in Spanish


#### Scenario: Library folder
- **WHEN** the library setup screen is shown for the first time
- **THEN** it does not propose any folder name: the user chooses or creates the folder
