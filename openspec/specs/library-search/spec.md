# library-search Specification

## Purpose

Search of the library home screen: filters the saved documents by name while a query is typed, ignoring case and accents.

## Requirements

### Requirement: Search by name
The library SHALL offer a search action. While a query is entered, the list SHALL show only the
documents whose name contains the query or, when the document has OCR data, whose recognized text
contains it, ignoring case and accents, keeping the usual order. Documents without OCR data
SHALL be found by name only.

#### Scenario: Matching documents
- **WHEN** the library contains "Factura luz", "Recibo agua" and "factura gas" and the user types
  "fact"
- **THEN** only "Factura luz" and "factura gas" are shown

#### Scenario: Accents ignored
- **WHEN** the user types "camion"
- **THEN** a document named "Camión alquiler" is shown

#### Scenario: Match in the recognized text
- **WHEN** a document named "Scan 2026-10-11" has recognized text containing "hipoteca" and the user
  types "hipoteca"
- **THEN** that document is shown

#### Scenario: Document without OCR data
- **WHEN** a document has no OCR data and the user types a word that is only in its pages
- **THEN** it is not shown

#### Scenario: No results
- **WHEN** no document matches the query
- **THEN** a message says that no documents match

#### Scenario: Closing the search
- **WHEN** the user closes the search
- **THEN** the query is cleared and all documents are shown
