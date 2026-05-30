# Synthetic Data Generator

Phase 3 creates repeatable, realistic CSV and JSON data for demos before any real taxpayer data is available.

## Install

```powershell
py -3.13 -m pip install -r packages/synthetic-data/requirements.txt
```

CI uses Python 3.12. On local machines where compiled data libraries are slow to install, the generator still runs with Faker and the standard library; Polars is included in the dependency set for downstream validation work.

## Generate

```powershell
python scripts/generate-synthetic-data/generate.py --output data/synthetic/latest --taxpayers 10000 --invoices 200000 --seed 202603
```

Outputs:

- CSV files matching the Phase 2 tables
- `risk_scenarios.csv`
- `risk_scenarios.json`
- `manifest.json`

Generated datasets are ignored by Git under `data/synthetic/`.

## Scenarios

The generator injects all required Phase 3 risk scenarios:

- VAT output underdeclaration
- VAT input overclaim
- Import-to-sales mismatch
- WHT certificate mismatch
- PAYE underdeclaration
- Nil filer issuing invoices
- Rental income mismatch
- County permit without matching tax activity
- Payment collected but not settled
- Delayed settlement
- Duplicate payment transaction
