import csv
import hashlib
import json
from pathlib import Path

import polars as pl

from kra_synthetic.contracts import REQUIRED_SCENARIOS, SCHEMAS
from kra_synthetic.generator import GenerationConfig, generate_dataset


def _generate(tmp_path: Path, seed: int = 99, taxpayers: int = 80, invoices: int = 250) -> Path:
    output = tmp_path / f"seed-{seed}"
    generate_dataset(
        GenerationConfig(
            output_dir=output,
            taxpayer_count=taxpayers,
            invoice_count=invoices,
            seed=seed,
        )
    )
    return output


def _file_hash(path: Path) -> str:
    digest = hashlib.sha256()
    digest.update(path.read_bytes())
    return digest.hexdigest()


def _rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="") as file:
        return list(csv.DictReader(file))


def test_generation_is_repeatable_for_same_seed(tmp_path: Path) -> None:
    first = _generate(tmp_path / "first", seed=123)
    second = _generate(tmp_path / "second", seed=123)

    for table in SCHEMAS:
        assert _file_hash(first / f"{table}.csv") == _file_hash(second / f"{table}.csv")
    assert _file_hash(first / "risk_scenarios.json") == _file_hash(
        second / "risk_scenarios.json"
    )
    assert _file_hash(first / "manifest.json") == _file_hash(second / "manifest.json")


def test_generated_csv_files_match_contract_headers(tmp_path: Path) -> None:
    output = _generate(tmp_path)

    for table, columns in SCHEMAS.items():
        with (output / f"{table}.csv").open(newline="") as file:
            reader = csv.reader(file)
            assert next(reader) == columns


def test_polars_reads_generated_csv_contracts(tmp_path: Path) -> None:
    output = _generate(tmp_path)

    taxpayers = pl.read_csv(output / "taxpayers.csv")
    invoices = pl.read_csv(output / "invoices.csv")
    scenarios = pl.read_csv(output / "risk_scenarios.csv")

    assert taxpayers.height == 80
    assert invoices.height >= 250
    assert scenarios["scenario_code"].n_unique() == len(REQUIRED_SCENARIOS)


def test_required_risk_scenarios_are_present_and_measurable(tmp_path: Path) -> None:
    output = _generate(tmp_path)
    scenarios = json.loads((output / "risk_scenarios.json").read_text())
    by_code = {scenario["scenario_code"]: scenario for scenario in scenarios}

    assert set(by_code) == set(REQUIRED_SCENARIOS)
    for code in REQUIRED_SCENARIOS:
        scenario = by_code[code]
        assert float(scenario["estimated_gap"]) > 0
        assert scenario["taxpayer_id"]
        assert scenario["primary_record_id"]


def test_core_record_counts_cover_phase_three_scope(tmp_path: Path) -> None:
    output = _generate(tmp_path, taxpayers=120, invoices=400)

    assert len(_rows(output / "taxpayers.csv")) == 120
    assert len(_rows(output / "invoices.csv")) >= 400
    assert len(_rows(output / "invoice_lines.csv")) >= 400
    assert len(_rows(output / "taxpayer_identifiers.csv")) == 120
    assert len(_rows(output / "tax_obligations.csv")) >= 120
    assert _rows(output / "tax_returns.csv")
    assert _rows(output / "customs_declarations.csv")
    assert _rows(output / "withholding_certificates.csv")
    assert _rows(output / "payroll_returns.csv")
    assert _rows(output / "business_permits.csv")
    assert _rows(output / "properties.csv")
    assert _rows(output / "payment_transactions.csv")
    assert _rows(output / "settlement_records.csv")


def test_manifest_records_seed_volume_and_scenarios(tmp_path: Path) -> None:
    output = _generate(tmp_path, seed=456, taxpayers=90, invoices=300)
    manifest = json.loads((output / "manifest.json").read_text())

    assert manifest["seed"] == 456
    assert manifest["taxpayer_count"] == 90
    assert manifest["invoice_count_requested"] == 300
    assert manifest["scenario_count"] == len(REQUIRED_SCENARIOS)
