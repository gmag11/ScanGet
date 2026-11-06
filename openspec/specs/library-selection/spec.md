# library-selection Specification

## Purpose

Multi-selection of documents on the library home screen: long press to select, then share (one file per document) or delete several documents at once.

## Requirements

### Requirement: Selection mode
A long press on a document SHALL enter selection mode and select it. In selection mode a tap SHALL
toggle a document's selection instead of opening it. The number of selected documents SHALL be
shown. Selection mode SHALL end when no document remains selected, when the user closes it, or on
back.

#### Scenario: Enter selection mode
- **WHEN** the user long-presses a document
- **THEN** selection mode starts with that document selected and "1 selected" is shown

#### Scenario: Toggle
- **WHEN** selection mode is active and the user taps another document
- **THEN** it becomes selected and the count is 2

#### Scenario: Leave with back
- **WHEN** selection mode is active and the user presses back
- **THEN** selection mode ends and nothing is selected

#### Scenario: Select all
- **WHEN** the user uses "Select all"
- **THEN** every document shown is selected, respecting the active search

### Requirement: Share selected documents
In selection mode, a Share action SHALL generate one file per selected document, with all its pages,
in the export format and quality chosen in the settings, named after each document, and share them
in a single share request. The working document SHALL NOT change.

#### Scenario: Share three documents as PDF
- **WHEN** three documents are selected, the export format is PDF, and the user presses Share
- **THEN** the share request carries three PDFs named after the documents

#### Scenario: Progress
- **WHEN** the files are being generated
- **THEN** a progress indicator is shown and the action can be cancelled

### Requirement: Delete selected documents
In selection mode, a Delete action SHALL ask for confirmation stating the number of documents, and
then delete all of them as the single-document delete does.

#### Scenario: Delete two documents
- **WHEN** two documents are selected and the user confirms the deletion
- **THEN** both are removed from the library and selection mode ends
