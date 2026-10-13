# library-search Specification

## Purpose

Search of the library home screen: filters the saved documents by name while a query is typed, ignoring case and accents.

## Requirements

### Requirement: Search by name
The library SHALL offer a search action. While a query is entered, the list SHALL show only the
documents whose name contains the query, ignoring case and accents, keeping the usual order.

#### Scenario: Matching documents
- **WHEN** the library contains "Factura luz", "Recibo agua" and "factura gas" and the user types
  "fact"
- **THEN** only "Factura luz" and "factura gas" are shown

#### Scenario: Accents ignored
- **WHEN** the user types "camion"
- **THEN** a document named "Camión alquiler" is shown

#### Scenario: No results
- **WHEN** no document matches the query
- **THEN** a message says that no documents match

#### Scenario: Closing the search
- **WHEN** the user closes the search
- **THEN** the query is cleared and all documents are shown
