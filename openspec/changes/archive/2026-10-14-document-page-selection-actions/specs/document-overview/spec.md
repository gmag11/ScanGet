# Spec Delta

## MODIFIED Requirements

### Requirement: Opening a page
Tapping a thumbnail SHALL open the page editor on that page, unless page selection is active, in
which case a tap toggles the selection (see `document-page-selection`). Going back from the page
editor SHALL return to the overview.

#### Scenario: Edit a page
- **WHEN** the user taps page 4
- **THEN** the page editor opens showing page 4

#### Scenario: Back to the grid
- **WHEN** the user presses back in the page editor
- **THEN** the overview is shown

#### Scenario: Tap while selecting
- **WHEN** page selection is active and the user taps page 4
- **THEN** page 4 is toggled and the page editor does not open

### Requirement: Reordering in the grid
A long press followed by a drag on a thumbnail SHALL move the page to the drop position. The new
order SHALL be saved. A long press released without dragging SHALL instead start page selection (see
`document-page-selection`). While pages are selected, dragging SHALL NOT reorder.

#### Scenario: Move a page
- **WHEN** the user drags page 5 before page 2
- **THEN** the former page 5 becomes page 2 and the numbers update

#### Scenario: Long press without drag
- **WHEN** the user long-presses page 5 and releases without moving
- **THEN** page selection starts with page 5 selected and the order is unchanged

#### Scenario: No reordering while selecting
- **WHEN** page selection is active and the user drags a page
- **THEN** the order does not change
