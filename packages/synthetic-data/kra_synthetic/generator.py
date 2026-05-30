import argparse
import csv
import json
import random
import uuid
from contextlib import ExitStack
from dataclasses import dataclass
from datetime import UTC, date, datetime, timedelta
from pathlib import Path
from typing import Any

try:
    from faker import Faker
except ImportError:  # pragma: no cover - CI installs Faker; local fallback keeps scripts usable.
    Faker = None

from kra_synthetic.contracts import REQUIRED_SCENARIOS, SCHEMAS

VAT_RATE = 0.16
NAMESPACE = uuid.UUID("2c9c3a91-d39b-45e6-a93e-39bdafc9b66b")
PERIOD_START = date(2025, 1, 1)
PERIOD_END = date(2025, 3, 31)
CREATED_AT = datetime(2025, 4, 5, 9, 0, tzinfo=UTC).isoformat()

COUNTIES = [
    "Nairobi",
    "Mombasa",
    "Kisumu",
    "Nakuru",
    "Kiambu",
    "Machakos",
    "Uasin Gishu",
    "Kajiado",
]
SECTORS = [
    ("G46", "Wholesale trade"),
    ("G47", "Retail trade"),
    ("F41", "Construction"),
    ("I56", "Food service"),
    ("H49", "Transport"),
    ("L68", "Real estate"),
    ("C10", "Food manufacturing"),
    ("J62", "Software services"),
]
TAX_OFFICES = ["Nairobi East", "Nairobi West", "Mombasa", "Kisumu", "Nakuru", "Eldoret"]
PRODUCTS = [
    ("POS terminals", "8471"),
    ("Construction steel", "7214"),
    ("Consulting services", "9983"),
    ("Packaged foods", "1905"),
    ("Transport services", "9965"),
    ("Office supplies", "4820"),
]


@dataclass(frozen=True)
class GenerationConfig:
    output_dir: Path
    taxpayer_count: int = 1_000
    invoice_count: int = 5_000
    seed: int = 202603


@dataclass(frozen=True)
class TaxpayerRef:
    id: str
    pin: str
    name: str
    sector_code: str
    sector_name: str
    county: str


class WriterSet:
    def __init__(self, output_dir: Path, stack: ExitStack) -> None:
        self.output_dir = output_dir
        self.stack = stack
        self.writers: dict[str, csv.DictWriter[str]] = {}

    def open(self, name: str) -> csv.DictWriter[str]:
        file = self.stack.enter_context((self.output_dir / f"{name}.csv").open("w", newline=""))
        writer = csv.DictWriter(file, fieldnames=SCHEMAS[name], extrasaction="ignore")
        writer.writeheader()
        self.writers[name] = writer
        return writer


def generate_dataset(config: GenerationConfig) -> dict[str, Any]:
    output_dir = Path(config.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    rng = random.Random(config.seed)
    fake = _fake(config.seed)
    taxpayers = _build_taxpayers(config.taxpayer_count, config.seed, rng, fake)
    scenarios: dict[str, dict[str, Any]] = {}

    with ExitStack() as stack:
        writers = WriterSet(output_dir, stack)
        opened = {name: writers.open(name) for name in SCHEMAS if name != "risk_scenarios"}

        _write_taxpayer_dimensions(opened, taxpayers, config.seed)
        invoice_totals = _write_invoices(
            opened, taxpayers, config.invoice_count, config.seed, rng, scenarios
        )
        _write_returns(opened, taxpayers, invoice_totals, rng, scenarios)
        _write_customs(opened, taxpayers, rng, scenarios)
        _write_wht(opened, taxpayers, rng, scenarios)
        _write_payroll(opened, taxpayers, rng, scenarios)
        _write_permits_and_properties(opened, taxpayers, rng, scenarios)
        _write_payments_and_settlements(opened, taxpayers, rng, scenarios)

    scenario_rows = [scenarios[code] for code in REQUIRED_SCENARIOS]
    _write_scenarios(output_dir, scenario_rows)
    _write_manifest(output_dir, config, scenario_rows)

    return {
        "output_dir": str(output_dir),
        "taxpayer_count": config.taxpayer_count,
        "invoice_count": config.invoice_count,
        "scenario_count": len(scenario_rows),
        "seed": config.seed,
    }


def _fake(seed: int) -> Any:
    if Faker is None:
        return None
    Faker.seed(seed)
    fake = Faker("en_US")
    fake.seed_instance(seed)
    return fake


def _stable_uuid(seed: int, label: str) -> str:
    return str(uuid.uuid5(NAMESPACE, f"{seed}:{label}"))


def _money(value: float) -> str:
    return f"{value:.2f}"


def _pin(index: int) -> str:
    return f"P{index + 100000000:09d}{chr(65 + (index % 26))}"


def _date(value: date) -> str:
    return value.isoformat()


def _datetime(value: datetime) -> str:
    return value.replace(tzinfo=UTC).isoformat()


def _company_name(fake: Any, index: int) -> str:
    if fake is not None:
        return fake.company()
    return f"Synthetic Company {index:05d} Ltd"


def _build_taxpayers(count: int, seed: int, rng: random.Random, fake: Any) -> list[TaxpayerRef]:
    if count < len(REQUIRED_SCENARIOS) + 5:
        raise ValueError(f"taxpayer_count must be at least {len(REQUIRED_SCENARIOS) + 5}")

    taxpayers: list[TaxpayerRef] = []
    for index in range(count):
        sector_code, sector_name = SECTORS[index % len(SECTORS)]
        county = COUNTIES[(index + rng.randrange(len(COUNTIES))) % len(COUNTIES)]
        taxpayers.append(
            TaxpayerRef(
                id=_stable_uuid(seed, f"taxpayer:{index}"),
                pin=_pin(index),
                name=_company_name(fake, index),
                sector_code=sector_code,
                sector_name=sector_name,
                county=county,
            )
        )
    return taxpayers


def _write_taxpayer_dimensions(
    writers: dict[str, csv.DictWriter[str]], taxpayers: list[TaxpayerRef], seed: int
) -> None:
    for index, taxpayer in enumerate(taxpayers):
        office = TAX_OFFICES[index % len(TAX_OFFICES)]
        registered_at = PERIOD_START - timedelta(days=365 + index % 2_500)
        writers["taxpayers"].writerow(
            {
                "id": taxpayer.id,
                "kra_pin": taxpayer.pin,
                "taxpayer_type": "COMPANY" if index % 5 else "SOLE_PROPRIETOR",
                "legal_name": taxpayer.name,
                "trading_name": taxpayer.name.replace("Ltd", "Trading"),
                "registration_number": f"BN-{index + 10000}",
                "sector_code": taxpayer.sector_code,
                "sector_name": taxpayer.sector_name,
                "tax_office": office,
                "county": taxpayer.county,
                "status": "ACTIVE",
                "registered_at": _date(registered_at),
                "created_at": CREATED_AT,
                "updated_at": CREATED_AT,
            }
        )
        writers["taxpayer_identifiers"].writerow(
            {
                "id": _stable_uuid(seed, f"pin:{taxpayer.id}"),
                "taxpayer_id": taxpayer.id,
                "identifier_type": "KRA_PIN",
                "identifier_value": taxpayer.pin,
                "source": "SYNTHETIC_KRA",
                "confidence_score": "100.00",
                "created_at": CREATED_AT,
            }
        )
        for tax_head in ("VAT", "INCOME_TAX", "PAYE" if index % 3 == 0 else "WHT"):
            writers["tax_obligations"].writerow(
                {
                    "id": _stable_uuid(seed, f"obligation:{taxpayer.id}:{tax_head}"),
                    "taxpayer_id": taxpayer.id,
                    "tax_head": tax_head,
                    "obligation_status": "ACTIVE",
                    "effective_from": _date(registered_at),
                    "effective_to": "",
                    "created_at": CREATED_AT,
                }
            )


def _write_invoice(
    writers: dict[str, csv.DictWriter[str]],
    seed: int,
    index: int,
    supplier: TaxpayerRef,
    buyer: TaxpayerRef,
    invoice_date: date,
    taxable: float,
    status: str = "VALID",
) -> tuple[str, float]:
    product_name, hs_code = PRODUCTS[index % len(PRODUCTS)]
    tax = taxable * VAT_RATE
    invoice_id = _stable_uuid(seed, f"invoice:{index}")
    writers["invoices"].writerow(
        {
            "id": invoice_id,
            "invoice_number": f"ETIMS-{index + 1:010d}",
            "supplier_taxpayer_id": supplier.id,
            "buyer_taxpayer_id": buyer.id,
            "supplier_pin": supplier.pin,
            "buyer_pin": buyer.pin,
            "invoice_date": _date(invoice_date),
            "invoice_type": "SALE",
            "invoice_status": status,
            "taxable_amount": _money(taxable),
            "tax_amount": _money(tax),
            "total_amount": _money(taxable + tax),
            "currency": "KES",
            "etims_reference": f"ETR-{index + 500000}",
            "source_job_id": "",
            "created_at": CREATED_AT,
        }
    )
    writers["invoice_lines"].writerow(
        {
            "id": _stable_uuid(seed, f"invoice-line:{index}"),
            "invoice_id": invoice_id,
            "line_number": 1,
            "item_description": product_name,
            "quantity": "1.0000",
            "unit_price": _money(taxable),
            "taxable_amount": _money(taxable),
            "tax_amount": _money(tax),
            "hs_code": hs_code,
            "product_code": f"SKU-{index % 1000:04d}",
        }
    )
    return invoice_id, tax


def _write_invoices(
    writers: dict[str, csv.DictWriter[str]],
    taxpayers: list[TaxpayerRef],
    invoice_count: int,
    seed: int,
    rng: random.Random,
    scenarios: dict[str, dict[str, Any]],
) -> dict[str, float]:
    invoice_totals = {taxpayer.id: 0.0 for taxpayer in taxpayers}
    blocked_supplier_ids = {taxpayers[7].id}
    normal_suppliers = [
        taxpayer for taxpayer in taxpayers[12:] if taxpayer.id not in blocked_supplier_ids
    ]

    for index in range(invoice_count):
        supplier = normal_suppliers[index % len(normal_suppliers)]
        buyer = taxpayers[(index * 7 + 19) % len(taxpayers)]
        if buyer.id == supplier.id:
            buyer = taxpayers[(index * 7 + 20) % len(taxpayers)]
        taxable = float(2_000 + rng.randrange(850_000))
        invoice_date = PERIOD_START + timedelta(days=index % 90)
        _, tax = _write_invoice(writers, seed, index, supplier, buyer, invoice_date, taxable)
        invoice_totals[supplier.id] += tax

    scenario_start = invoice_count
    scenario_specs = [
        ("VAT_OUTPUT_UNDERDECLARATION", 0, 3_500_000.0, 400_000.0),
        ("NIL_FILER_ISSUING_INVOICES", 5, 1_200_000.0, 0.0),
    ]
    for offset, (code, taxpayer_index, taxable, declared) in enumerate(scenario_specs):
        supplier = taxpayers[taxpayer_index]
        buyer = taxpayers[20 + offset]
        invoice_id, tax = _write_invoice(
            writers,
            seed,
            scenario_start + offset,
            supplier,
            buyer,
            PERIOD_START + timedelta(days=14 + offset),
            taxable,
        )
        invoice_totals[supplier.id] += tax
        scenarios[code] = _scenario(
            code,
            supplier,
            invoice_id,
            observed=tax,
            declared=declared,
            evidence={"source": "invoices", "rule": code.lower()},
        )

    return invoice_totals


def _write_returns(
    writers: dict[str, csv.DictWriter[str]],
    taxpayers: list[TaxpayerRef],
    invoice_totals: dict[str, float],
    rng: random.Random,
    scenarios: dict[str, dict[str, Any]],
) -> None:
    overrides = {
        taxpayers[0].id: {"declared_output_tax": 400_000.0},
        taxpayers[1].id: {"declared_input_tax": 900_000.0, "observed_input_tax": 100_000.0},
        taxpayers[3].id: {"declared_sales": 80_000.0},
        taxpayers[5].id: {
            "filing_status": "NIL",
            "declared_sales": 0.0,
            "declared_output_tax": 0.0,
        },
        taxpayers[6].id: {"declared_income": 120_000.0, "declared_sales": 120_000.0},
    }

    for index, taxpayer in enumerate(taxpayers):
        observed_output = invoice_totals.get(taxpayer.id, 0.0)
        declared_output = observed_output * rng.uniform(0.92, 1.04)
        declared_sales = declared_output / VAT_RATE if declared_output else 0.0
        declared_input = declared_output * rng.uniform(0.15, 0.42)
        override = overrides.get(taxpayer.id, {})
        declared_output = override.get("declared_output_tax", declared_output)
        declared_input = override.get("declared_input_tax", declared_input)
        declared_sales = override.get("declared_sales", declared_sales)
        declared_income = override.get("declared_income", declared_sales)
        filing_status = override.get("filing_status", "FILED")
        return_id = _stable_uuid(index, f"vat-return:{taxpayer.id}")
        writers["tax_returns"].writerow(
            {
                "id": return_id,
                "taxpayer_id": taxpayer.id,
                "tax_head": "VAT",
                "period_start": _date(PERIOD_START),
                "period_end": _date(PERIOD_END),
                "return_reference": f"VAT-{index + 1:09d}",
                "declared_sales": _money(declared_sales),
                "declared_income": _money(declared_income),
                "declared_tax_due": _money(max(declared_output - declared_input, 0.0)),
                "declared_input_tax": _money(declared_input),
                "declared_output_tax": _money(declared_output),
                "filing_status": filing_status,
                "filed_at": _datetime(datetime(2025, 4, 20, 10, 30) + timedelta(minutes=index)),
                "source_job_id": "",
                "created_at": CREATED_AT,
            }
        )

    scenarios["VAT_INPUT_OVERCLAIM"] = _scenario(
        "VAT_INPUT_OVERCLAIM",
        taxpayers[1],
        _stable_uuid(1, f"vat-return:{taxpayers[1].id}"),
        observed=100_000.0,
        declared=900_000.0,
        evidence={"source": "tax_returns", "field": "declared_input_tax"},
    )


def _write_customs(
    writers: dict[str, csv.DictWriter[str]],
    taxpayers: list[TaxpayerRef],
    rng: random.Random,
    scenarios: dict[str, dict[str, Any]],
) -> None:
    for index, taxpayer in enumerate(taxpayers[: max(20, len(taxpayers) // 8)]):
        customs_value = float(50_000 + rng.randrange(2_500_000))
        if index == 2:
            customs_value = 8_000_000.0
        declaration_id = _stable_uuid(index, f"customs:{taxpayer.id}")
        writers["customs_declarations"].writerow(
            {
                "id": declaration_id,
                "taxpayer_id": taxpayer.id,
                "importer_pin": taxpayer.pin,
                "declaration_number": f"CUS-{index + 1:010d}",
                "declaration_type": "IMPORT",
                "declaration_date": _date(PERIOD_START + timedelta(days=index % 60)),
                "hs_code": PRODUCTS[index % len(PRODUCTS)][1],
                "goods_description": PRODUCTS[index % len(PRODUCTS)][0],
                "country_of_origin": "CN" if index % 2 else "AE",
                "customs_value": _money(customs_value),
                "duty_amount": _money(customs_value * 0.25),
                "vat_amount": _money(customs_value * VAT_RATE),
                "total_landed_cost": _money(customs_value * 1.41),
                "source_job_id": "",
                "created_at": CREATED_AT,
            }
        )
        if index == 2:
            scenarios["IMPORT_TO_SALES_MISMATCH"] = _scenario(
                "IMPORT_TO_SALES_MISMATCH",
                taxpayer,
                declaration_id,
                observed=customs_value,
                declared=250_000.0,
                evidence={
                    "source": "customs_declarations",
                    "compare_to": "tax_returns.declared_sales",
                },
            )


def _write_wht(
    writers: dict[str, csv.DictWriter[str]],
    taxpayers: list[TaxpayerRef],
    rng: random.Random,
    scenarios: dict[str, dict[str, Any]],
) -> None:
    for index, payee in enumerate(taxpayers[: max(20, len(taxpayers) // 10)]):
        payer = taxpayers[(index + 31) % len(taxpayers)]
        gross = 60_000.0 + rng.randrange(900_000)
        if index == 3:
            gross = 1_800_000.0
        certificate_id = _stable_uuid(index, f"wht:{payee.id}")
        writers["withholding_certificates"].writerow(
            {
                "id": certificate_id,
                "certificate_number": f"WHT-{index + 1:010d}",
                "payer_taxpayer_id": payer.id,
                "payee_taxpayer_id": payee.id,
                "payer_pin": payer.pin,
                "payee_pin": payee.pin,
                "certificate_date": _date(PERIOD_START + timedelta(days=index % 90)),
                "payment_period_start": _date(PERIOD_START),
                "payment_period_end": _date(PERIOD_END),
                "gross_amount": _money(gross),
                "withheld_amount": _money(gross * 0.05),
                "tax_rate": "0.0500",
                "source_job_id": "",
                "created_at": CREATED_AT,
            }
        )
        if index == 3:
            scenarios["WHT_CERTIFICATE_MISMATCH"] = _scenario(
                "WHT_CERTIFICATE_MISMATCH",
                payee,
                certificate_id,
                observed=gross,
                declared=80_000.0,
                evidence={
                    "source": "withholding_certificates",
                    "compare_to": "tax_returns.declared_sales",
                },
            )


def _write_payroll(
    writers: dict[str, csv.DictWriter[str]],
    taxpayers: list[TaxpayerRef],
    rng: random.Random,
    scenarios: dict[str, dict[str, Any]],
) -> None:
    for index, taxpayer in enumerate(taxpayers[: max(20, len(taxpayers) // 9)]):
        employees = 2 + rng.randrange(80)
        gross_pay = float(employees * (35_000 + rng.randrange(90_000)))
        paye_due = gross_pay * 0.18
        paye_paid = paye_due * rng.uniform(0.96, 1.0)
        if index == 4:
            employees = 125
            gross_pay = 9_000_000.0
            paye_due = 1_620_000.0
            paye_paid = 180_000.0
        payroll_id = _stable_uuid(index, f"payroll:{taxpayer.id}")
        writers["payroll_returns"].writerow(
            {
                "id": payroll_id,
                "taxpayer_id": taxpayer.id,
                "period_start": _date(PERIOD_START),
                "period_end": _date(PERIOD_END),
                "employee_count": employees,
                "gross_pay": _money(gross_pay),
                "paye_due": _money(paye_due),
                "paye_paid": _money(paye_paid),
                "filing_status": "FILED",
                "source_job_id": "",
                "created_at": CREATED_AT,
            }
        )
        if index == 4:
            scenarios["PAYE_UNDERDECLARATION"] = _scenario(
                "PAYE_UNDERDECLARATION",
                taxpayer,
                payroll_id,
                observed=paye_due,
                declared=paye_paid,
                evidence={"source": "payroll_returns", "field": "paye_paid"},
            )


def _write_permits_and_properties(
    writers: dict[str, csv.DictWriter[str]],
    taxpayers: list[TaxpayerRef],
    rng: random.Random,
    scenarios: dict[str, dict[str, Any]],
) -> None:
    for index, taxpayer in enumerate(taxpayers[: max(20, len(taxpayers) // 7)]):
        permit_fee = 12_000.0 + rng.randrange(180_000)
        permit_id = _stable_uuid(index, f"permit:{taxpayer.id}")
        writers["business_permits"].writerow(
            {
                "id": permit_id,
                "taxpayer_id": taxpayer.id,
                "permit_number": f"BP-{index + 1:010d}",
                "county": taxpayer.county,
                "business_activity": taxpayer.sector_name,
                "premises_location": f"{taxpayer.county} CBD Block {index % 40}",
                "valid_from": _date(date(2025, 1, 1)),
                "valid_to": _date(date(2025, 12, 31)),
                "permit_fee": _money(permit_fee),
                "permit_status": "ACTIVE",
                "source_job_id": "",
                "created_at": CREATED_AT,
            }
        )
        if index == 7:
            scenarios["COUNTY_PERMIT_WITHOUT_TAX_ACTIVITY"] = _scenario(
                "COUNTY_PERMIT_WITHOUT_TAX_ACTIVITY",
                taxpayer,
                permit_id,
                observed=permit_fee,
                declared=0.0,
                evidence={"source": "business_permits", "compare_to": "tax_returns/invoices"},
            )

        property_id = _stable_uuid(index, f"property:{taxpayer.id}")
        monthly_rent = float(25_000 + rng.randrange(500_000))
        if index == 6:
            monthly_rent = 450_000.0
        writers["properties"].writerow(
            {
                "id": property_id,
                "property_reference": f"PROP-{index + 1:010d}",
                "owner_taxpayer_id": taxpayer.id,
                "owner_pin": taxpayer.pin,
                "county": taxpayer.county,
                "location_description": f"{taxpayer.county} LR {index + 9000}",
                "property_type": "COMMERCIAL" if index % 3 else "RESIDENTIAL_RENTAL",
                "valuation_amount": _money(monthly_rent * 160),
                "estimated_monthly_rent": _money(monthly_rent),
                "source_job_id": "",
                "created_at": CREATED_AT,
            }
        )
        if index == 6:
            scenarios["RENTAL_INCOME_MISMATCH"] = _scenario(
                "RENTAL_INCOME_MISMATCH",
                taxpayer,
                property_id,
                observed=monthly_rent * 3,
                declared=120_000.0,
                evidence={"source": "properties", "compare_to": "tax_returns.declared_income"},
            )


def _write_payments_and_settlements(
    writers: dict[str, csv.DictWriter[str]],
    taxpayers: list[TaxpayerRef],
    rng: random.Random,
    scenarios: dict[str, dict[str, Any]],
) -> None:
    settled_amounts: dict[tuple[str, str, date], list[float]] = {}
    payment_total = max(50, len(taxpayers) // 4)
    for index in range(payment_total):
        taxpayer = taxpayers[(index * 5) % len(taxpayers)]
        amount = float(1_000 + rng.randrange(600_000))
        payment_date = datetime(2025, 2, 1, 9, 0, tzinfo=UTC) + timedelta(days=index % 40)
        transaction_id = _stable_uuid(index, f"payment:{taxpayer.id}")
        provider_reference = f"MPESA-{index + 1:010d}"
        writers["payment_transactions"].writerow(
            _payment_row(index, transaction_id, taxpayer, amount, payment_date, provider_reference)
        )
        settlement_date = payment_date.date() + timedelta(days=1)
        settled_amounts.setdefault(("KRA", "MPESA", settlement_date), []).append(amount)

    special_payment_base = payment_total + 10
    missing_taxpayer = taxpayers[8]
    missing_amount = 875_000.0
    missing_id = _stable_uuid(8, "payment-missing-settlement")
    writers["payment_transactions"].writerow(
        _payment_row(
            special_payment_base,
            missing_id,
            missing_taxpayer,
            missing_amount,
            datetime(2025, 3, 5, 12, 0, tzinfo=UTC),
            "MPESA-MISSING-0001",
        )
    )
    scenarios["PAYMENT_COLLECTED_NOT_SETTLED"] = _scenario(
        "PAYMENT_COLLECTED_NOT_SETTLED",
        missing_taxpayer,
        missing_id,
        observed=missing_amount,
        declared=0.0,
        evidence={"source": "payment_transactions", "compare_to": "settlement_records"},
    )

    delayed_taxpayer = taxpayers[9]
    delayed_amount = 650_000.0
    delayed_id = _stable_uuid(9, "payment-delayed-settlement")
    delayed_date = datetime(2025, 3, 8, 12, 0, tzinfo=UTC)
    writers["payment_transactions"].writerow(
        _payment_row(
            special_payment_base + 1,
            delayed_id,
            delayed_taxpayer,
            delayed_amount,
            delayed_date,
            "MPESA-DELAYED-0001",
        )
    )
    delayed_settlement_key = ("KRA", "MPESA", delayed_date.date() + timedelta(days=14))
    settled_amounts.setdefault(delayed_settlement_key, []).append(delayed_amount)
    scenarios["DELAYED_SETTLEMENT"] = _scenario(
        "DELAYED_SETTLEMENT",
        delayed_taxpayer,
        delayed_id,
        observed=14.0,
        declared=1.0,
        evidence={"source": "settlement_records", "unit": "days_to_settle"},
    )

    duplicate_taxpayer = taxpayers[10]
    duplicate_amount = 420_000.0
    duplicate_ref = "MPESA-DUPLICATE-0001"
    first_duplicate_id = _stable_uuid(10, "payment-duplicate-a")
    for offset in range(2):
        transaction_id = (
            first_duplicate_id if offset == 0 else _stable_uuid(10, "payment-duplicate-b")
        )
        writers["payment_transactions"].writerow(
            _payment_row(
                special_payment_base + 2 + offset,
                transaction_id,
                duplicate_taxpayer,
                duplicate_amount,
                datetime(2025, 3, 10, 15, 0, tzinfo=UTC),
                duplicate_ref,
            )
        )
    settled_amounts.setdefault(("KRA", "MPESA", date(2025, 3, 11)), []).append(duplicate_amount)
    scenarios["DUPLICATE_PAYMENT_TRANSACTION"] = _scenario(
        "DUPLICATE_PAYMENT_TRANSACTION",
        duplicate_taxpayer,
        first_duplicate_id,
        observed=duplicate_amount * 2,
        declared=duplicate_amount,
        evidence={"source": "payment_transactions", "provider_reference": duplicate_ref},
    )

    settlement_items = sorted(settled_amounts.items())
    for index, ((agency, channel, settlement_date), amounts) in enumerate(settlement_items):
        amount = sum(amounts)
        writers["settlement_records"].writerow(
            {
                "id": _stable_uuid(index, f"settlement:{agency}:{channel}:{settlement_date}"),
                "settlement_reference": f"SET-{index + 1:010d}",
                "collecting_agency": agency,
                "revenue_channel": channel,
                "settlement_account": "CBK-KRA-MAIN",
                "settlement_date": _date(settlement_date),
                "settled_amount": _money(amount),
                "transaction_count": len(amounts),
                "settlement_status": "SETTLED",
                "source_job_id": "",
                "created_at": CREATED_AT,
            }
        )


def _payment_row(
    index: int,
    transaction_id: str,
    taxpayer: TaxpayerRef,
    amount: float,
    payment_date: datetime,
    provider_reference: str,
) -> dict[str, Any]:
    return {
        "id": transaction_id,
        "transaction_reference": f"PAY-{index + 1:010d}",
        "payer_taxpayer_id": taxpayer.id,
        "payer_pin": taxpayer.pin,
        "collecting_agency": "KRA",
        "revenue_channel": "MPESA",
        "service_code": "VAT_PAYMENT",
        "payment_date": _datetime(payment_date),
        "amount": _money(amount),
        "currency": "KES",
        "payment_status": "PAID",
        "provider_reference": provider_reference,
        "source_job_id": "",
        "created_at": CREATED_AT,
    }


def _scenario(
    code: str,
    taxpayer: TaxpayerRef,
    primary_record_id: str,
    observed: float,
    declared: float,
    evidence: dict[str, Any],
) -> dict[str, Any]:
    return {
        "scenario_code": code,
        "taxpayer_id": taxpayer.id,
        "kra_pin": taxpayer.pin,
        "primary_record_id": primary_record_id,
        "observed_amount": _money(observed),
        "declared_amount": _money(declared),
        "estimated_gap": _money(abs(observed - declared)),
        "evidence": json.dumps(evidence, sort_keys=True),
    }


def _write_scenarios(output_dir: Path, scenarios: list[dict[str, Any]]) -> None:
    with (output_dir / "risk_scenarios.csv").open("w", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=SCHEMAS["risk_scenarios"])
        writer.writeheader()
        writer.writerows(scenarios)
    with (output_dir / "risk_scenarios.json").open("w") as file:
        json.dump(scenarios, file, indent=2)


def _write_manifest(
    output_dir: Path,
    config: GenerationConfig,
    scenarios: list[dict[str, Any]],
) -> None:
    manifest = {
        "seed": config.seed,
        "taxpayer_count": config.taxpayer_count,
        "invoice_count_requested": config.invoice_count,
        "scenario_count": len(scenarios),
        "required_scenarios": REQUIRED_SCENARIOS,
        "generated_at": CREATED_AT,
        "files": sorted(path.name for path in output_dir.glob("*.csv"))
        + ["risk_scenarios.json"],
    }
    with (output_dir / "manifest.json").open("w") as file:
        json.dump(manifest, file, indent=2)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate synthetic KRA revenue intelligence data."
    )
    parser.add_argument("--output", type=Path, default=Path("data/synthetic/latest"))
    parser.add_argument("--taxpayers", type=int, default=1_000)
    parser.add_argument("--invoices", type=int, default=5_000)
    parser.add_argument("--seed", type=int, default=202603)
    parser.add_argument("--quiet", action="store_true")
    args = parser.parse_args()

    result = generate_dataset(
        GenerationConfig(
            output_dir=args.output,
            taxpayer_count=args.taxpayers,
            invoice_count=args.invoices,
            seed=args.seed,
        )
    )
    if not args.quiet:
        print(json.dumps(result, indent=2))
