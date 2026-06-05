# Data Processing Overview

## Demo Data Policy

The demo environment uses synthetic data by default. Synthetic data must be clearly labelled in dashboards, scripts, and exports.

## Approved Pilot Data Path

1. Confirm data owner and lawful pilot purpose.
2. Register the source system or file type.
3. Map schema fields to platform tables.
4. Validate records and quarantine invalid rows.
5. Ingest accepted records with audit logs.
6. Run deterministic rules, tax gap estimation, reconciliation, and optional model scoring.
7. Generate evidence packs only for authorized pilot users.

## Data Categories

- Taxpayer registry and identifiers.
- Returns, invoices, customs, WHT, PAYE, payments, and settlement records.
- Risk signals, cases, evidence packs, notifications, and audit logs.
- Model predictions and explanations where enabled.

## Retention

Pilot retention should be time-bounded and approved before live data is loaded. Synthetic data can be reset between demos.
