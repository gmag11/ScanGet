# crop-quad-editing Specification

## Purpose

Lets the user adjust the page outline (a four-sided quad) in the crop screen by dragging its corners and its sides.

## Requirements

### Requirement: Draggable sides
The user SHALL be able to drag each of the four sides of the outline. A dragged side SHALL translate perpendicular to its own direction, so that its orientation does not change. The component of the finger movement along the side SHALL have no effect.

#### Scenario: Dragging a side across
- **WHEN** the user touches the middle of a side and drags the finger
- **THEN** the side moves by the finger displacement measured perpendicular to the side, and stays parallel to its previous position

#### Scenario: Diagonal finger movement
- **WHEN** the finger moves both along and across the side
- **THEN** only the across component moves the side

#### Scenario: Side handle is visible
- **WHEN** the crop screen shows the outline
- **THEN** each side displays a handle at its midpoint, distinct from the corner handles

### Requirement: Corners take priority
When a touch is within reach of both a corner and a side, the corner SHALL be selected. Corner dragging SHALL behave as before.

#### Scenario: Touch near a corner
- **WHEN** the user touches a point close to both a corner and the adjacent side
- **THEN** the corner is dragged, not the side

### Requirement: Valid outline is preserved
Dragging a side SHALL keep the outline inside the image and convex, as for corners. A drag step that would move any end of the side outside the image or make the outline non-convex SHALL be ignored.

#### Scenario: Side reaches the image border
- **WHEN** the user drags a side beyond the image border
- **THEN** the outline does not leave the image

#### Scenario: Side would make outline concave
- **WHEN** a drag step would make the outline non-convex
- **THEN** the outline stays as it was before that step

### Requirement: Loupe while dragging a side
While the user touches or drags a side, the loupe SHALL be shown, centered on the midpoint of that side.

#### Scenario: Loupe on side
- **WHEN** the user drags a side
- **THEN** the loupe is visible and follows the side's midpoint
