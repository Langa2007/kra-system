# Build Phases: Revenue Intelligence and Compliance Assurance Platform

## Purpose of This Document

This document converts the project README into a build sequence from concept to production.

The README defines the full product vision. This file defines how to build it without drifting, skipping modules, or losing the original point over time.

Every phase below must end with a testing gate. A phase is not complete until its tests, data checks, security checks, and acceptance criteria pass.

## Source of Truth

Primary source: `README.md`

This build plan follows the README sections:

- Project purpose
- Core problem
- Product vision
- System principles
- Main users
- Core product modules
- Detailed use cases
- Data model overview
- Suggested database tables
- Technology stack
- Recommended architecture
- MVP scope
- Build roadmap
- Go-to-market strategy
- Legal, privacy, AI, and security requirements
- Development standards
- Regional adaptation strategy

## What the System Seeks to Do

The platform seeks to become the intelligence layer between raw government revenue data and real-world revenue recovery.

It must do the following:

- Detect tax gaps across VAT, income tax, PAYE, withholding tax, customs, excise, rental income, turnover tax, and county revenue.
- Compare declared taxpayer values against observed economic activity from invoices, returns, customs declarations, payments, permits, and settlement records.
- Reconcile government collections against bank or treasury settlements.
- Create a single taxpayer and entity view across fragmented identifiers.
- Use deterministic rules to detect clear compliance mismatches.
- Use machine learning to detect patterns that static rules miss.
- Use graph intelligence to reveal linked companies, directors, suppliers, buyers, importers, revenue channels, and payment relationships.
- Prioritize audit and compliance cases by materiality, confidence, risk, and expected recoverable revenue.
- Generate explainable evidence packs for every serious case.
- Support voluntary compliance nudges before formal enforcement.
- Give officers a complete workflow for review, assignment, investigation, communication, escalation, closure, and revenue recovery tracking.
- Give executives dashboards for tax gaps, regional risk, sector risk, audit productivity, compliance improvement, and revenue recovery.
- Give counties a revenue assurance module for own-source revenue leakage.
- Give Treasury or oversight agencies a settlement reconciliation and public finance assurance view.
- Provide strong governance, audit logs, role-based access, model versioning, and privacy-by-design.
- Be deployable first as a local MVP, then as a pilot-ready government system, then as an enterprise national-scale platform.
- Be adaptable to other African revenue authorities through country-specific tax adapters.

## The Void the System Seeks to Fill

KRA and similar agencies already have systems such as eTIMS, iTax, customs platforms, payment systems, and internal audit processes. This system does not try to replace them.

The gap is the lack of a specialized, explainable, cross-source intelligence layer that:

- Connects siloed revenue data.
- Converts fragmented records into taxpayer-level insight.
- Detects mismatches automatically.
- Shows officers exactly why a taxpayer or payment channel is risky.
- Prioritizes the highest-value cases.
- Produces evidence packs instead of just charts.
- Tracks whether intelligence becomes recovered revenue.
- Supports counties and other public agencies, not only national tax administration.
- Can be customized for African revenue environments.

In simple terms:

```text
Existing systems collect and store revenue data.
This system turns that data into recoverable revenue intelligence.
```

## Product Pillars

All phases must map to at least one of these pillars:

1. Data ingestion and quality
2. Master taxpayer and entity resolution
3. Tax gap detection
4. Revenue assurance and settlement reconciliation
5. Risk scoring and prioritization
6. Graph intelligence
7. Case management
8. Evidence packs
9. Voluntary compliance nudging
10. Dashboards and reporting
11. Governance, security, audit, and privacy
12. AI and model management
13. Integration readiness
14. Regional adaptation

## Build Principles

- Build a usable MVP first, then scale.
- Use synthetic data before government data access.
- Prefer explainable rules before complex AI.
- Keep all risk scores traceable to source data.
- Keep the core modular but start as a modular monolith where practical.
- Every officer-facing alert must have evidence.
- Every sensitive action must be auditable.
- Every phase must have tests before the next phase starts.
- Data quality failures must be visible, not hidden.
- The platform must be useful to KRA, counties, and other African governments.

## Chosen Technology Stack

This section makes a practical technology choice for the first full build. The README lists alternatives; this plan chooses a primary path so development does not stall.

### Frontend

- Next.js
- React
- TypeScript
- Tailwind CSS
- shadcn/ui or a restrained custom government design system
- TanStack Query
- TanStack Table or AG Grid
- ECharts or Recharts
- Playwright for end-to-end tests

### Core Backend

- Java 21
- Spring Boot
- Spring Security
- Spring Data JPA
- Flyway database migrations
- OpenAPI/Swagger
- JUnit 5
- Testcontainers

Reason:

Spring Boot is a strong choice for government-grade enterprise systems, long-term maintainability, security reviews, and structured backend development.

### Analytics and AI Services

- Python
- FastAPI for analytics service APIs where needed
- Pandas and Polars
- scikit-learn
- XGBoost or LightGBM when supervised labels exist
- SHAP for explainability
- MLflow for model registry
- pytest

### Databases and Storage

MVP:

- PostgreSQL
- Redis
- Local object storage or filesystem-based evidence storage for development

Pilot and enterprise:

- PostgreSQL
- ClickHouse for high-volume analytical queries
- Neo4j for graph analytics
- Redis
- S3-compatible object storage such as MinIO

### Messaging and Jobs

MVP:

- Spring scheduled jobs
- Redis-backed queues if needed

Pilot and enterprise:

- RabbitMQ for workflow queues
- Kafka for high-volume ingestion/event streaming

### Identity and Access

MVP:

- Spring Security JWT auth
- Internal users and roles

Pilot and enterprise:

- Keycloak
- OpenID Connect
- MFA
- Government SSO integration where required

### Infrastructure

Local:

- Docker Compose

Pilot:

- Docker
- Nginx or API gateway
- Linux VM or private cloud deployment

Enterprise:

- Kubernetes
- Helm
- Terraform
- Prometheus
- Grafana
- Loki or ELK
- OpenTelemetry

### Reporting and Exports

- Server-side PDF generation
- Excel export
- CSV export
- Apache POI for Excel in Java
- Playwright or JasperReports for PDF generation
- Apache Superset or Power BI integration later

## Recommended Repository Structure

```text
kra-system/
  README.md
  BUILD_PHASES.md
  docs/
    architecture.md
    data-dictionary.md
    product-requirements.md
    security-model.md
    privacy-impact.md
    pilot-proposal.md
    api-contracts.md
    testing-strategy.md
  apps/
    web/
    api/
    analytics/
  packages/
    data-contracts/
    synthetic-data/
  data/
    samples/
    synthetic/
    schemas/
  infra/
    docker/
    kubernetes/
    terraform/
  scripts/
    generate-synthetic-data/
    import-samples/
  tests/
    e2e/
    load/
    security/
```

## Canonical Database Schema

This schema is the starting point. Each build phase below introduces the tables it needs, but this complete view keeps the destination clear.

The MVP should begin in PostgreSQL. Later, high-volume events can move to ClickHouse and graph relationships can move to Neo4j.

### Core System Tables

```sql
CREATE TABLE data_sources (
  id UUID PRIMARY KEY,
  code VARCHAR(80) NOT NULL UNIQUE,
  name VARCHAR(200) NOT NULL,
  source_type VARCHAR(80) NOT NULL,
  owner_agency VARCHAR(160),
  ingestion_method VARCHAR(80) NOT NULL,
  schema_version VARCHAR(40),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE ingestion_jobs (
  id UUID PRIMARY KEY,
  data_source_id UUID NOT NULL REFERENCES data_sources(id),
  file_name VARCHAR(300),
  status VARCHAR(40) NOT NULL,
  records_received BIGINT NOT NULL DEFAULT 0,
  records_valid BIGINT NOT NULL DEFAULT 0,
  records_invalid BIGINT NOT NULL DEFAULT 0,
  started_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ,
  error_summary TEXT,
  created_by UUID
);

CREATE TABLE data_quality_issues (
  id UUID PRIMARY KEY,
  ingestion_job_id UUID REFERENCES ingestion_jobs(id),
  data_source_id UUID REFERENCES data_sources(id),
  severity VARCHAR(40) NOT NULL,
  issue_type VARCHAR(120) NOT NULL,
  record_reference VARCHAR(200),
  field_name VARCHAR(120),
  issue_message TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);
```

### Taxpayer and Entity Tables

```sql
CREATE TABLE taxpayers (
  id UUID PRIMARY KEY,
  kra_pin VARCHAR(40) UNIQUE,
  taxpayer_type VARCHAR(40) NOT NULL,
  legal_name VARCHAR(240) NOT NULL,
  trading_name VARCHAR(240),
  registration_number VARCHAR(80),
  sector_code VARCHAR(80),
  sector_name VARCHAR(160),
  tax_office VARCHAR(160),
  county VARCHAR(120),
  status VARCHAR(60) NOT NULL,
  registered_at DATE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE taxpayer_identifiers (
  id UUID PRIMARY KEY,
  taxpayer_id UUID NOT NULL REFERENCES taxpayers(id),
  identifier_type VARCHAR(80) NOT NULL,
  identifier_value VARCHAR(200) NOT NULL,
  source VARCHAR(120),
  confidence_score NUMERIC(5,2) NOT NULL DEFAULT 100,
  created_at TIMESTAMPTZ NOT NULL,
  UNIQUE(identifier_type, identifier_value)
);

CREATE TABLE taxpayer_relationships (
  id UUID PRIMARY KEY,
  source_taxpayer_id UUID NOT NULL REFERENCES taxpayers(id),
  target_taxpayer_id UUID REFERENCES taxpayers(id),
  related_person_name VARCHAR(240),
  relationship_type VARCHAR(80) NOT NULL,
  source VARCHAR(120),
  confidence_score NUMERIC(5,2) NOT NULL,
  valid_from DATE,
  valid_to DATE,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE tax_obligations (
  id UUID PRIMARY KEY,
  taxpayer_id UUID NOT NULL REFERENCES taxpayers(id),
  tax_head VARCHAR(80) NOT NULL,
  obligation_status VARCHAR(60) NOT NULL,
  effective_from DATE,
  effective_to DATE,
  created_at TIMESTAMPTZ NOT NULL
);
```

### Tax, Invoice, Customs, and Payroll Tables

```sql
CREATE TABLE tax_returns (
  id UUID PRIMARY KEY,
  taxpayer_id UUID NOT NULL REFERENCES taxpayers(id),
  tax_head VARCHAR(80) NOT NULL,
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  return_reference VARCHAR(120),
  declared_sales NUMERIC(18,2),
  declared_income NUMERIC(18,2),
  declared_tax_due NUMERIC(18,2),
  declared_input_tax NUMERIC(18,2),
  declared_output_tax NUMERIC(18,2),
  filing_status VARCHAR(60) NOT NULL,
  filed_at TIMESTAMPTZ,
  source_job_id UUID REFERENCES ingestion_jobs(id),
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE invoices (
  id UUID PRIMARY KEY,
  invoice_number VARCHAR(120) NOT NULL,
  supplier_taxpayer_id UUID REFERENCES taxpayers(id),
  buyer_taxpayer_id UUID REFERENCES taxpayers(id),
  supplier_pin VARCHAR(40),
  buyer_pin VARCHAR(40),
  invoice_date DATE NOT NULL,
  invoice_type VARCHAR(60) NOT NULL,
  invoice_status VARCHAR(60) NOT NULL,
  taxable_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
  tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
  total_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
  currency VARCHAR(10) NOT NULL DEFAULT 'KES',
  etims_reference VARCHAR(160),
  source_job_id UUID REFERENCES ingestion_jobs(id),
  created_at TIMESTAMPTZ NOT NULL,
  UNIQUE(invoice_number, supplier_pin, invoice_date)
);

CREATE TABLE invoice_lines (
  id UUID PRIMARY KEY,
  invoice_id UUID NOT NULL REFERENCES invoices(id),
  line_number INTEGER NOT NULL,
  item_description TEXT,
  quantity NUMERIC(18,4),
  unit_price NUMERIC(18,4),
  taxable_amount NUMERIC(18,2),
  tax_amount NUMERIC(18,2),
  hs_code VARCHAR(40),
  product_code VARCHAR(80)
);

CREATE TABLE customs_declarations (
  id UUID PRIMARY KEY,
  taxpayer_id UUID REFERENCES taxpayers(id),
  importer_pin VARCHAR(40),
  declaration_number VARCHAR(120) NOT NULL UNIQUE,
  declaration_type VARCHAR(80) NOT NULL,
  declaration_date DATE NOT NULL,
  hs_code VARCHAR(40),
  goods_description TEXT,
  country_of_origin VARCHAR(120),
  customs_value NUMERIC(18,2) NOT NULL DEFAULT 0,
  duty_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
  vat_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
  total_landed_cost NUMERIC(18,2),
  source_job_id UUID REFERENCES ingestion_jobs(id),
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE withholding_certificates (
  id UUID PRIMARY KEY,
  certificate_number VARCHAR(120) NOT NULL UNIQUE,
  payer_taxpayer_id UUID REFERENCES taxpayers(id),
  payee_taxpayer_id UUID REFERENCES taxpayers(id),
  payer_pin VARCHAR(40),
  payee_pin VARCHAR(40),
  certificate_date DATE NOT NULL,
  payment_period_start DATE,
  payment_period_end DATE,
  gross_amount NUMERIC(18,2) NOT NULL,
  withheld_amount NUMERIC(18,2) NOT NULL,
  tax_rate NUMERIC(8,4),
  source_job_id UUID REFERENCES ingestion_jobs(id),
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE payroll_returns (
  id UUID PRIMARY KEY,
  taxpayer_id UUID NOT NULL REFERENCES taxpayers(id),
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  employee_count INTEGER,
  gross_pay NUMERIC(18,2),
  paye_due NUMERIC(18,2),
  paye_paid NUMERIC(18,2),
  filing_status VARCHAR(60) NOT NULL,
  source_job_id UUID REFERENCES ingestion_jobs(id),
  created_at TIMESTAMPTZ NOT NULL
);
```

### County, Property, Payment, and Settlement Tables

```sql
CREATE TABLE business_permits (
  id UUID PRIMARY KEY,
  taxpayer_id UUID REFERENCES taxpayers(id),
  permit_number VARCHAR(120) NOT NULL,
  county VARCHAR(120) NOT NULL,
  business_activity VARCHAR(200),
  premises_location VARCHAR(240),
  valid_from DATE,
  valid_to DATE,
  permit_fee NUMERIC(18,2),
  permit_status VARCHAR(60) NOT NULL,
  source_job_id UUID REFERENCES ingestion_jobs(id),
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE properties (
  id UUID PRIMARY KEY,
  property_reference VARCHAR(120) NOT NULL UNIQUE,
  owner_taxpayer_id UUID REFERENCES taxpayers(id),
  owner_pin VARCHAR(40),
  county VARCHAR(120),
  location_description VARCHAR(240),
  property_type VARCHAR(80),
  valuation_amount NUMERIC(18,2),
  estimated_monthly_rent NUMERIC(18,2),
  source_job_id UUID REFERENCES ingestion_jobs(id),
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE payment_transactions (
  id UUID PRIMARY KEY,
  transaction_reference VARCHAR(160) NOT NULL UNIQUE,
  payer_taxpayer_id UUID REFERENCES taxpayers(id),
  payer_pin VARCHAR(40),
  collecting_agency VARCHAR(160) NOT NULL,
  revenue_channel VARCHAR(120) NOT NULL,
  service_code VARCHAR(120),
  payment_date TIMESTAMPTZ NOT NULL,
  amount NUMERIC(18,2) NOT NULL,
  currency VARCHAR(10) NOT NULL DEFAULT 'KES',
  payment_status VARCHAR(60) NOT NULL,
  provider_reference VARCHAR(160),
  source_job_id UUID REFERENCES ingestion_jobs(id),
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE settlement_records (
  id UUID PRIMARY KEY,
  settlement_reference VARCHAR(160) NOT NULL UNIQUE,
  collecting_agency VARCHAR(160) NOT NULL,
  revenue_channel VARCHAR(120) NOT NULL,
  settlement_account VARCHAR(160),
  settlement_date DATE NOT NULL,
  settled_amount NUMERIC(18,2) NOT NULL,
  transaction_count INTEGER,
  settlement_status VARCHAR(60) NOT NULL,
  source_job_id UUID REFERENCES ingestion_jobs(id),
  created_at TIMESTAMPTZ NOT NULL
);
```

### Risk, Case, Evidence, AI, and Governance Tables

```sql
CREATE TABLE risk_rules (
  id UUID PRIMARY KEY,
  code VARCHAR(120) NOT NULL UNIQUE,
  name VARCHAR(200) NOT NULL,
  description TEXT,
  tax_head VARCHAR(80),
  rule_type VARCHAR(80) NOT NULL,
  severity VARCHAR(40) NOT NULL,
  threshold_config JSONB NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE risk_signals (
  id UUID PRIMARY KEY,
  taxpayer_id UUID REFERENCES taxpayers(id),
  risk_rule_id UUID REFERENCES risk_rules(id),
  signal_type VARCHAR(120) NOT NULL,
  tax_head VARCHAR(80),
  period_start DATE,
  period_end DATE,
  observed_amount NUMERIC(18,2),
  declared_amount NUMERIC(18,2),
  estimated_gap NUMERIC(18,2),
  confidence_score NUMERIC(5,2) NOT NULL,
  severity VARCHAR(40) NOT NULL,
  explanation TEXT NOT NULL,
  evidence JSONB NOT NULL,
  status VARCHAR(60) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE risk_scores (
  id UUID PRIMARY KEY,
  taxpayer_id UUID NOT NULL REFERENCES taxpayers(id),
  score NUMERIC(5,2) NOT NULL,
  confidence_score NUMERIC(5,2) NOT NULL,
  scoring_period_start DATE,
  scoring_period_end DATE,
  main_factors JSONB NOT NULL,
  model_version_id UUID,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE cases (
  id UUID PRIMARY KEY,
  case_number VARCHAR(120) NOT NULL UNIQUE,
  taxpayer_id UUID REFERENCES taxpayers(id),
  title VARCHAR(240) NOT NULL,
  case_type VARCHAR(80) NOT NULL,
  priority VARCHAR(40) NOT NULL,
  status VARCHAR(60) NOT NULL,
  estimated_recoverable_amount NUMERIC(18,2),
  assigned_to UUID,
  opened_at TIMESTAMPTZ NOT NULL,
  closed_at TIMESTAMPTZ,
  closure_reason VARCHAR(160)
);

CREATE TABLE case_events (
  id UUID PRIMARY KEY,
  case_id UUID NOT NULL REFERENCES cases(id),
  event_type VARCHAR(80) NOT NULL,
  event_note TEXT,
  created_by UUID,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE evidence_packs (
  id UUID PRIMARY KEY,
  case_id UUID NOT NULL REFERENCES cases(id),
  version INTEGER NOT NULL,
  summary TEXT NOT NULL,
  evidence_json JSONB NOT NULL,
  file_uri VARCHAR(500),
  generated_by UUID,
  generated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE notifications (
  id UUID PRIMARY KEY,
  taxpayer_id UUID REFERENCES taxpayers(id),
  case_id UUID REFERENCES cases(id),
  channel VARCHAR(40) NOT NULL,
  template_code VARCHAR(120) NOT NULL,
  recipient VARCHAR(240),
  subject VARCHAR(240),
  message_body TEXT NOT NULL,
  status VARCHAR(60) NOT NULL,
  sent_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE recovery_records (
  id UUID PRIMARY KEY,
  case_id UUID NOT NULL REFERENCES cases(id),
  assessed_amount NUMERIC(18,2),
  agreed_amount NUMERIC(18,2),
  collected_amount NUMERIC(18,2),
  collection_date DATE,
  recovery_status VARCHAR(60) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE model_versions (
  id UUID PRIMARY KEY,
  model_name VARCHAR(160) NOT NULL,
  version VARCHAR(80) NOT NULL,
  model_type VARCHAR(80) NOT NULL,
  training_data_summary TEXT,
  metrics JSONB,
  active BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL,
  UNIQUE(model_name, version)
);

CREATE TABLE model_predictions (
  id UUID PRIMARY KEY,
  model_version_id UUID NOT NULL REFERENCES model_versions(id),
  taxpayer_id UUID REFERENCES taxpayers(id),
  prediction_type VARCHAR(120) NOT NULL,
  score NUMERIC(8,4) NOT NULL,
  explanation JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE app_users (
  id UUID PRIMARY KEY,
  external_identity_id VARCHAR(160),
  email VARCHAR(240) NOT NULL UNIQUE,
  full_name VARCHAR(240) NOT NULL,
  department VARCHAR(160),
  status VARCHAR(60) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE roles (
  id UUID PRIMARY KEY,
  code VARCHAR(80) NOT NULL UNIQUE,
  name VARCHAR(160) NOT NULL,
  description TEXT
);

CREATE TABLE user_roles (
  user_id UUID NOT NULL REFERENCES app_users(id),
  role_id UUID NOT NULL REFERENCES roles(id),
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE audit_logs (
  id UUID PRIMARY KEY,
  actor_user_id UUID REFERENCES app_users(id),
  action VARCHAR(120) NOT NULL,
  entity_type VARCHAR(120),
  entity_id UUID,
  ip_address VARCHAR(80),
  user_agent TEXT,
  details JSONB,
  created_at TIMESTAMPTZ NOT NULL
);
```

## Phase Traceability Matrix

| Phase | Main README Coverage |
| --- | --- |
| 0 | Product purpose, market gap, users, principles |
| 1 | Development standards, repository structure, infrastructure |
| 2 | Data model overview, database tables, security baseline |
| 3 | MVP scope, synthetic data, detailed use cases |
| 4 | Data ingestion layer, data quality |
| 5 | Master taxpayer and entity index |
| 6 | Risk rules engine |
| 7 | Tax gap detection engine |
| 8 | Case management and evidence packs |
| 9 | Dashboards and reports |
| 10 | Revenue assurance and settlement monitor |
| 11 | Voluntary compliance and nudging |
| 12 | Machine learning risk scoring, AI design |
| 13 | Graph intelligence |
| 14 | Administration, governance, security, privacy |
| 15 | Reporting, BI, exports, executive dashboards |
| 16 | Pilot package, ROI, go-to-market |
| 17 | Government integration readiness |
| 18 | Enterprise scale, monitoring, operations |
| 19 | Regional adaptation |
| 20 | Production readiness and launch |

## Phase 0: Product Foundation and Control Documents

### Objective

Create the non-code foundation so the build has a clear target.

### Scope

- Finalize product name.
- Confirm primary buyer path: KRA, county government, public finance agency, or taxpayer-side compliance wedge.
- Create product requirements document.
- Create data dictionary.
- Create architecture notes.
- Create pilot proposal outline.
- Create ROI calculator outline.
- Create security and privacy design notes.

### Tech Stack

- Markdown documentation
- Draw.io, Mermaid, or Excalidraw for diagrams
- Spreadsheet for ROI model

### Database Work

No database implementation yet.

### Testing Gate

- README and this build plan reviewed.
- Every product module from the README appears in the phase plan.
- Every major user type has at least one planned workflow.
- Every major data source has a planned ingestion path.
- Every major risk area has a mitigation section.

### Exit Criteria

- `README.md` exists.
- `BUILD_PHASES.md` exists.
- Product scope is clear enough to begin scaffolding.

## Phase 1: Repository, Dev Environment, and Engineering Baseline

### Objective

Create the working repo structure and local development foundation.

### Scope

- Create backend Spring Boot app.
- Create frontend Next.js app.
- Create analytics Python app.
- Create Docker Compose setup.
- Add PostgreSQL and Redis containers.
- Add environment variable templates.
- Add linting and formatting.
- Add CI pipeline skeleton.
- Add OpenAPI generation path.

### Tech Stack

- Java 21
- Spring Boot
- Maven
- Node.js
- Next.js
- TypeScript
- Python
- FastAPI project skeleton
- PostgreSQL
- Redis
- Docker Compose

### Database Work

- Create empty database.
- Configure Flyway migrations.
- Add first migration table.

### Testing Gate

- Backend health endpoint test passes.
- Frontend home page renders.
- Analytics service health endpoint passes.
- PostgreSQL container starts and accepts connections.
- Redis container starts and accepts connections.
- CI runs lint and basic tests.
- Local setup works from clean checkout.

### Exit Criteria

- A developer can clone, configure, run, and test the system locally.

## Phase 2: Core Database Schema and Security Skeleton

### Objective

Implement the core PostgreSQL schema and minimal application security.

### Scope

- [x] Add Flyway migrations for core system tables.
- [x] Add taxpayer tables.
- [x] Add tax return, invoice, customs, WHT, payment, and settlement tables.
- [x] Add risk, case, evidence, user, role, and audit tables.
- [x] Implement seed roles.
- [x] Implement basic JWT authentication for MVP.
- [x] Implement audit logging foundation.

### Tech Stack

- [x] PostgreSQL
- [x] Flyway
- [x] Spring Data JPA
- [x] Spring Security
- [x] JUnit
- [x] Testcontainers

### Database Work

Implement the MVP subset of the canonical schema:

- [x] data_sources
- [x] ingestion_jobs
- [x] data_quality_issues
- [x] taxpayers
- [x] taxpayer_identifiers
- [x] taxpayer_relationships
- [x] tax_obligations
- [x] tax_returns
- [x] invoices
- [x] invoice_lines
- [x] customs_declarations
- [x] withholding_certificates
- [x] payroll_returns
- [x] payment_transactions
- [x] settlement_records
- [x] business_permits
- [x] properties
- [x] risk_rules
- [x] risk_signals
- [x] risk_scores
- [x] cases
- [x] case_events
- [x] evidence_packs
- [x] notifications
- [x] recovery_records
- [x] app_users
- [x] roles
- [x] user_roles
- [x] audit_logs

### Testing Gate

- [x] Migration test passes on clean database.
- [x] Migration rollback strategy is documented.
- [x] Entity mapping tests pass.
- [x] Required indexes are present for taxpayer, period, and reference lookups.
- [x] Authentication tests pass.
- [x] Authorization tests block unauthorized access.
- [x] Audit log is written for login and sensitive API access.

### Exit Criteria

- [x] The database can support MVP data, risk signals, cases, evidence packs, and audit logs.

## Phase 3: Synthetic Data Generator

### Objective

Create realistic synthetic data so the system can be demonstrated before government integration.

### Scope

- [x] Generate taxpayers.
- [x] Generate taxpayer identifiers.
- [x] Generate tax obligations.
- [x] Generate eTIMS-like invoices.
- [x] Generate VAT returns.
- [x] Generate customs declarations.
- [x] Generate WHT certificates.
- [x] Generate payroll returns.
- [x] Generate business permits.
- [x] Generate property records.
- [x] Generate payment transactions.
- [x] Generate settlement records.
- [x] Inject known risk scenarios.

### Tech Stack

- [x] Python
- [x] Faker
- [x] Polars
- [x] CSV and JSON output
- [x] pytest

### Database Work

- [x] No new tables unless synthetic generation logs are desired.

### Required Synthetic Scenarios

- [x] VAT output underdeclaration
- [x] VAT input overclaim
- [x] Import-to-sales mismatch
- [x] WHT certificate mismatch
- [x] PAYE underdeclaration
- [x] Nil filer issuing invoices
- [x] Rental income mismatch
- [x] County permit without matching tax activity
- [x] Payment collected but not settled
- [x] Delayed settlement
- [x] Duplicate payment transaction

### Testing Gate

- [x] Synthetic generator creates repeatable data with a seed.
- [x] Generated files match expected schemas.
- [x] Known risk scenarios are present and measurable.
- [x] At least 10,000 taxpayers can be generated.
- [x] At least 200,000 invoices can be generated.
- [x] Data generation completes within an acceptable local runtime.
- [x] pytest validates all generated data contracts.

### Exit Criteria

- [x] The team can demo the system using synthetic data without touching real taxpayer data.

## Phase 4: Data Ingestion and Data Quality

### Objective

Build the ingestion layer for batch data uploads and validation.

### Scope

- [x] Upload CSV and JSON files.
- [x] Register data sources.
- [x] Map incoming schemas.
- [x] Validate records.
- [x] Store valid records.
- [x] Quarantine invalid records.
- [x] Track ingestion jobs.
- [x] Show ingestion status.
- [x] Show data quality issues.

### Tech Stack

- [x] Spring Boot REST APIs
- [x] PostgreSQL
- [x] Spring Batch or custom ingestion services
- [x] Apache Commons CSV or OpenCSV
- [x] JSON Schema validation
- [x] MinIO later for raw file storage

### Database Work

Use:

- [x] data_sources
- [x] ingestion_jobs
- [x] data_quality_issues
- [x] all target domain tables

Add indexes:

- [x] ingestion_jobs(data_source_id, status)
- [x] data_quality_issues(ingestion_job_id)
- [x] invoices(source_job_id)
- [x] tax_returns(source_job_id)

### APIs

- [x] `POST /api/data-sources`
- [x] `GET /api/data-sources`
- [x] `POST /api/ingestion/jobs`
- [x] `GET /api/ingestion/jobs`
- [x] `GET /api/ingestion/jobs/{id}`
- [x] `GET /api/ingestion/jobs/{id}/issues`

### Testing Gate

- [x] Unit tests for validators.
- [x] Integration tests for each file type.
- [x] Invalid rows are rejected with clear reasons.
- [x] Valid rows are persisted correctly.
- [x] Duplicate file upload behavior is defined and tested.
- [x] Large file ingestion test passes with synthetic data.
- [x] Audit logs are written for uploads and imports.

### Exit Criteria

- [x] The platform can ingest all MVP datasets safely and show data quality results.

## Phase 5: Master Taxpayer and Entity Resolution

### Objective

Build the single taxpayer view and link records across identifiers.

### Scope

- [x] Match records by KRA PIN.
- [x] Match records by registration number.
- [x] Match records by invoice supplier/buyer PIN.
- [x] Link permits, properties, payments, and customs records to taxpayers.
- [x] Add fuzzy matching support for business names.
- [x] Add match confidence.
- [x] Show linked identifiers in taxpayer profile.

### Tech Stack

- [x] Spring Boot
- [x] PostgreSQL
- [x] pg_trgm extension for fuzzy matching
- [x] Optional Python helper for advanced matching (not required for this phase)

### Database Work

Use:

- [x] taxpayers
- [x] taxpayer_identifiers
- [x] taxpayer_relationships
- [x] invoices
- [x] tax_returns
- [x] customs_declarations
- [x] withholding_certificates
- [x] business_permits
- [x] properties
- [x] payment_transactions

Add indexes:

- [x] taxpayers(kra_pin)
- [x] taxpayers(registration_number)
- [x] taxpayer_identifiers(identifier_type, identifier_value)
- [x] taxpayers using trigram index on legal_name and trading_name

### Testing Gate

- [x] Exact match tests pass.
- [x] Fuzzy match tests pass with confidence scores.
- [x] Low-confidence matches are not auto-linked without review.
- [x] Duplicate taxpayer detection test passes.
- [x] Taxpayer profile aggregates records correctly.
- [x] Entity resolution job is idempotent.

### Exit Criteria

- [x] The system can produce a reliable taxpayer profile from fragmented source records.

## Phase 6: Rule Engine

### Objective

Build deterministic compliance checks that produce explainable risk signals.

### Scope

- [x] Create configurable rule definitions.
- [x] Implement core tax risk rules.
- [x] Implement severity and threshold configuration.
- [x] Store risk signals with evidence.
- [x] Add rule execution job.
- [x] Add rule result viewer.

### Tech Stack

- [x] Spring Boot
- [x] PostgreSQL JSONB
- [x] JUnit
- [x] Testcontainers

### Database Work

Use:

- [x] risk_rules
- [x] risk_signals
- [x] audit_logs

### First Rules

- [x] VAT output mismatch
- [x] VAT input mismatch
- [x] Import-to-sales mismatch
- [x] WHT certificate mismatch
- [x] Nil filer issuing invoices
- [x] PAYE ratio anomaly using simple rule
- [x] Permit-active but tax-inactive
- [x] Payment collected but not settled

### Testing Gate

- [x] Unit tests for every rule.
- [x] Integration tests using known synthetic scenarios.
- [x] Rule thresholds are configurable.
- [x] Rule outputs include explanation and evidence JSON.
- [x] Rule execution is idempotent.
- [x] False positives caused by obvious timing differences are handled where configured.
- [x] Performance test runs rules over MVP synthetic dataset.

### Exit Criteria

- [x] The system produces explainable risk signals from ingested data.

## Phase 7: Tax Gap Detection Engine

### Objective

Convert raw risk signals into quantified estimated tax gaps.

### Scope

- [x] Calculate estimated gap by tax head.
- [x] Estimate recoverable revenue.
- [x] Estimate penalty and interest where configuration exists.
- [x] Group signals by taxpayer and period.
- [x] Assign confidence levels.
- [x] Prioritize by materiality.

### Tech Stack

- [x] Spring Boot
- [x] PostgreSQL
- [x] Configurable calculation module
- [x] JUnit

### Database Work

Use:

- [x] risk_signals
- [x] risk_scores
- [x] tax_returns
- [x] invoices
- [x] customs_declarations
- [x] withholding_certificates
- [x] payroll_returns

Add optional table later:

```sql
CREATE TABLE tax_gap_estimates (
  id UUID PRIMARY KEY,
  taxpayer_id UUID NOT NULL REFERENCES taxpayers(id),
  tax_head VARCHAR(80) NOT NULL,
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  declared_amount NUMERIC(18,2),
  observed_amount NUMERIC(18,2),
  estimated_gap NUMERIC(18,2) NOT NULL,
  estimated_recoverable_tax NUMERIC(18,2),
  confidence_score NUMERIC(5,2) NOT NULL,
  evidence JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);
```

- [x] `tax_gap_estimates` added for durable Phase 7 rankings.

### Testing Gate

- [x] Calculations are tested for every tax gap category.
- [x] Edge cases are tested: nil returns, credit notes, cancelled invoices, missing records, negative values.
- [x] Tax gap summaries match underlying risk signals.
- [x] Ranking by estimated recoverable amount works.
- [x] Evidence links back to source records.

### Exit Criteria

- [x] The platform can rank taxpayers by estimated gap, tax head, period, confidence, and recoverable value.

## Phase 8: Case Management and Evidence Packs

### Objective

Turn risk intelligence into officer workflows.

### Scope

- [x] Create cases from risk signals.
- [x] Assign officers.
- [x] Add priority and status workflow.
- [x] Add case notes and events.
- [x] Add evidence pack generation.
- [x] Export evidence pack to PDF and JSON.
- [x] Track closure reason.
- [x] Track recovered revenue.

### Tech Stack

- [x] Spring Boot
- [x] PostgreSQL
- [x] PDF generation library
- [x] Apache POI for Excel later
- [x] Object storage later

### Database Work

Use:

- [x] cases
- [x] case_events
- [x] evidence_packs
- [x] recovery_records
- [x] audit_logs

### APIs

- [x] `POST /api/cases`
- [x] `GET /api/cases`
- [x] `GET /api/cases/{id}`
- [x] `PATCH /api/cases/{id}`
- [x] `POST /api/cases/{id}/events`
- [x] `POST /api/cases/{id}/evidence-packs`
- [x] `GET /api/cases/{id}/evidence-packs/{packId}`

### Testing Gate

- [x] Case creation from risk signal works.
- [x] Officer assignment is permission-controlled.
- [x] Status transitions are validated.
- [x] Evidence pack includes taxpayer, period, source records, rule, gap, confidence, and recommendation.
- [x] PDF export renders correctly.
- [x] Audit logs capture every case action.
- [x] Recovery amount is tracked and included in reports.

### Exit Criteria

- [x] Officers can move from signal to case to evidence to closure.

## Phase 9: Officer and Executive Dashboard Frontend

### Objective

Create the first usable web interface.

### Scope

- [x] Login.
- [x] Overview dashboard.
- [x] Risk queue.
- [x] Taxpayer search.
- [x] Taxpayer profile.
- [x] Case list.
- [x] Case detail.
- [x] Evidence pack viewer.
- [x] Ingestion status.
- [x] Rule configuration view.

### Tech Stack

- [x] Next.js
- [x] React
- [x] TypeScript
- [x] Tailwind CSS
- [x] TanStack Query
- [x] TanStack Table or AG Grid
- [x] ECharts or Recharts
- [x] Playwright

### Database Work

- [x] No new core tables unless user preferences are needed.

Optional:

```sql
CREATE TABLE user_preferences (
  user_id UUID PRIMARY KEY REFERENCES app_users(id),
  preferences JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
```

### Testing Gate

- [x] Component tests for core UI components.
- [x] Playwright tests for login, dashboard, taxpayer profile, risk queue, and case workflow.
- [x] Tables handle large synthetic datasets.
- [x] Filters and sorting work.
- [x] Evidence pack is readable.
- [x] UI access respects user role.
- [x] No obvious responsive layout breaks.

### Exit Criteria

- [ ] A non-developer can use the MVP to find a risk, open a case, and view evidence.

## Phase 10: Revenue Assurance and Settlement Monitor

### Objective

Build the module for government collection-to-settlement reconciliation.

### Scope

- Reconcile payment transactions against settlement records.
- Detect missing settlements.
- Detect delayed settlements.
- Detect duplicate transactions.
- Detect wrong settlement account.
- Detect convenience fee mismatch if fee data exists.
- Create exception reports.
- Create settlement risk cases.

### Tech Stack

- Spring Boot
- PostgreSQL
- Optional ClickHouse later for very large transaction volumes
- Next.js dashboards

### Database Work

Use:

- payment_transactions
- settlement_records
- risk_rules
- risk_signals
- cases

Add optional reconciliation table:

```sql
CREATE TABLE reconciliation_results (
  id UUID PRIMARY KEY,
  reconciliation_date DATE NOT NULL,
  collecting_agency VARCHAR(160) NOT NULL,
  revenue_channel VARCHAR(120) NOT NULL,
  expected_amount NUMERIC(18,2) NOT NULL,
  settled_amount NUMERIC(18,2) NOT NULL,
  variance_amount NUMERIC(18,2) NOT NULL,
  transaction_count INTEGER,
  settlement_status VARCHAR(60) NOT NULL,
  evidence JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);
```

### Testing Gate

- Reconciliation tests cover exact match, missing settlement, partial settlement, delayed settlement, duplicate payment, and wrong account.
- Daily exception report is generated.
- Settlement cases can be opened.
- Dashboard shows collections, settlements, variance, and aging.
- Performance test runs with at least 50,000 synthetic transactions.

### Exit Criteria

- The product has a strong county/public finance pilot module.

## Phase 11: Voluntary Compliance and Nudging

### Objective

Support taxpayer correction before formal enforcement.

### Scope

- Notification templates.
- Nudge generation from cases or risk signals.
- Email/SMS adapter interfaces.
- Notification status tracking.
- Taxpayer response placeholder.
- Officer review of communication history.

### Tech Stack

- Spring Boot
- PostgreSQL
- Email provider adapter
- SMS provider adapter
- Next.js notification UI

### Database Work

Use:

- notifications
- cases
- risk_signals
- audit_logs

Add optional templates table:

```sql
CREATE TABLE notification_templates (
  id UUID PRIMARY KEY,
  code VARCHAR(120) NOT NULL UNIQUE,
  channel VARCHAR(40) NOT NULL,
  subject_template VARCHAR(240),
  body_template TEXT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
```

### Testing Gate

- Template rendering tests pass.
- Notifications are linked to risk signals or cases.
- Failed notifications are retried or marked clearly.
- Sensitive data is not exposed unnecessarily.
- Officer permissions are enforced.
- Audit logs capture every sent message.

### Exit Criteria

- The system can support voluntary correction workflows.

## Phase 12: Machine Learning Risk Scoring

### Objective

Add AI-assisted anomaly detection and risk ranking.

### Scope

- Build analytics service.
- Train first unsupervised anomaly model on synthetic data.
- Generate model predictions.
- Store model versions.
- Store prediction explanations.
- Combine rule scores and model scores.
- Add model performance dashboard.

### Tech Stack

- Python
- FastAPI
- scikit-learn
- Pandas or Polars
- MLflow
- SHAP where applicable
- pytest
- Spring Boot integration client

### Database Work

Use:

- model_versions
- model_predictions
- risk_scores
- taxpayers
- tax_returns
- invoices
- customs_declarations
- withholding_certificates

### Initial Models

- Isolation Forest for anomaly detection
- Peer-group percentile scoring
- Sector-based outlier detection

### Testing Gate

- Model training pipeline is reproducible.
- Model version is stored.
- Prediction output schema is validated.
- Explanations include main contributing features.
- Model never creates enforcement action without officer review.
- Known synthetic anomalies receive higher scores than normal peers.
- False-positive review process is documented.

### Exit Criteria

- AI improves prioritization while remaining explainable and governed.

## Phase 13: Graph Intelligence

### Objective

Reveal relationships between taxpayers, directors, suppliers, buyers, permits, payment channels, importers, and agencies.

### Scope

- Build relationship extraction jobs.
- Create graph tables in PostgreSQL for MVP.
- Add Neo4j option for pilot/enterprise.
- Show relationship graph in taxpayer profile.
- Detect invoice rings, shared identifiers, related entities, and high-risk clusters.

### Tech Stack

MVP:

- PostgreSQL
- Spring Boot
- Next.js graph visualization

Enterprise:

- Neo4j
- Cypher queries
- Graph data science library if needed

### Database Work

Use:

- taxpayer_relationships

Add graph table:

```sql
CREATE TABLE graph_edges (
  id UUID PRIMARY KEY,
  source_type VARCHAR(80) NOT NULL,
  source_id UUID NOT NULL,
  target_type VARCHAR(80) NOT NULL,
  target_id UUID NOT NULL,
  edge_type VARCHAR(80) NOT NULL,
  weight NUMERIC(8,4) NOT NULL DEFAULT 1,
  source VARCHAR(120),
  evidence JSONB,
  created_at TIMESTAMPTZ NOT NULL
);
```

### Testing Gate

- Graph extraction creates expected edges from synthetic data.
- Duplicate edges are controlled.
- Relationship confidence is stored.
- Graph view loads for taxpayer profile.
- High-risk cluster detection works on seeded scenarios.
- Access controls protect sensitive relationship data.

### Exit Criteria

- Officers can see how taxpayers and risk signals are connected.

## Phase 14: Administration, Governance, Security, and Privacy

### Objective

Make the platform safe for serious pilot use.

### Scope

- Role management.
- Permission management.
- Data-source administration.
- Rule administration.
- Model version administration.
- Audit log viewer.
- Data retention configuration.
- Sensitive export controls.
- MFA path through Keycloak.
- Privacy impact checklist.

### Tech Stack

- Spring Security
- Keycloak for pilot/enterprise
- PostgreSQL
- Next.js admin UI
- OWASP ZAP
- Trivy or dependency scanner

### Database Work

Use:

- app_users
- roles
- user_roles
- audit_logs
- data_sources
- risk_rules
- model_versions

Add optional permissions:

```sql
CREATE TABLE permissions (
  id UUID PRIMARY KEY,
  code VARCHAR(120) NOT NULL UNIQUE,
  description TEXT
);

CREATE TABLE role_permissions (
  role_id UUID NOT NULL REFERENCES roles(id),
  permission_id UUID NOT NULL REFERENCES permissions(id),
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE data_retention_policies (
  id UUID PRIMARY KEY,
  data_category VARCHAR(120) NOT NULL UNIQUE,
  retention_days INTEGER NOT NULL,
  policy_reason TEXT,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL
);
```

### Testing Gate

- Unauthorized users cannot access restricted APIs.
- Role tests cover executive, officer, admin, analyst, and auditor personas.
- Audit logs cannot be edited through normal app APIs.
- Bulk export is permission-controlled.
- Security scan has no critical unresolved findings.
- Privacy checklist is completed for every data category.

### Exit Criteria

- The system is ready for controlled pilot conversations.

## Phase 15: Reporting, BI, and Executive Analytics

### Objective

Create strong reporting for leadership, audit teams, and pilot buyers.

### Scope

- Executive revenue gap dashboard.
- Sector risk dashboard.
- Regional risk dashboard.
- Tax head dashboard.
- Audit pipeline dashboard.
- Officer productivity dashboard.
- Revenue recovery dashboard.
- Settlement variance dashboard.
- Exportable reports.

### Tech Stack

- Next.js dashboards
- ECharts or Recharts
- Apache POI
- PDF generation
- Optional Apache Superset or Power BI later

### Database Work

Use existing tables.

Add materialized views:

```sql
CREATE MATERIALIZED VIEW mv_tax_gap_by_sector AS
SELECT
  t.sector_code,
  t.sector_name,
  rs.tax_head,
  COUNT(*) AS signal_count,
  SUM(rs.estimated_gap) AS estimated_gap,
  AVG(rs.confidence_score) AS avg_confidence
FROM risk_signals rs
JOIN taxpayers t ON t.id = rs.taxpayer_id
GROUP BY t.sector_code, t.sector_name, rs.tax_head;
```

### Testing Gate

- Report numbers reconcile to source tables.
- Export tests pass for PDF, Excel, and CSV.
- Dashboard filters work by tax head, sector, region, period, severity, and officer.
- Large dashboard queries meet performance target.
- Executive summaries are clear and non-technical.

### Exit Criteria

- The system can support demos to senior government and county leaders.

## Phase 16: Pilot Package, ROI, and Commercial Readiness

### Objective

Prepare the product for real buyer conversations.

### Scope

- Pilot proposal.
- Demo script.
- ROI calculator.
- Security overview.
- Data processing overview.
- Deployment overview.
- Sample evidence packs.
- Sample dashboards.
- Pricing model.
- Procurement route notes.

### Tech Stack

- Markdown docs
- PDF exports
- Spreadsheet model
- Demo environment

### Database Work

No new core tables.

### Testing Gate

- End-to-end demo works from clean synthetic dataset.
- ROI calculator matches dashboard outputs.
- Pilot script covers KRA and county angles.
- Sample evidence pack is generated live.
- All demo users and permissions work.
- No fake "real data" is used in demos.

### Exit Criteria

- The product can be shown credibly to counties, KRA stakeholders, integrators, or public finance reform partners.

## Phase 17: Government Integration Readiness

### Objective

Prepare secure adapters for real source systems.

### Scope

- API integration templates.
- SFTP ingestion adapters.
- Database read-only connector pattern.
- Schema mapping UI or config files.
- Data processing agreements template.
- Source freshness monitoring.
- Integration error dashboards.

### Tech Stack

- Spring Boot
- Apache Camel optional
- SFTP libraries
- Kafka or RabbitMQ for high-volume event flow
- MinIO for raw file archive

### Database Work

Extend:

- data_sources
- ingestion_jobs

Add:

```sql
CREATE TABLE source_schema_mappings (
  id UUID PRIMARY KEY,
  data_source_id UUID NOT NULL REFERENCES data_sources(id),
  source_schema JSONB NOT NULL,
  target_entity VARCHAR(120) NOT NULL,
  mapping_config JSONB NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
```

### Testing Gate

- Mock API adapter test passes.
- Mock SFTP adapter test passes.
- Schema mapping tests pass.
- Failed integration retries are controlled.
- Source freshness alert fires when data is late.
- Security review confirms no secrets in logs.

### Exit Criteria

- The system can connect to real government data sources through approved channels.

## Phase 18: Enterprise Scale, Observability, and Reliability

### Objective

Harden the platform for national-scale or multi-county usage.

### Scope

- Add observability.
- Add structured logs.
- Add metrics.
- Add tracing.
- Add backup and restore.
- Add disaster recovery plan.
- Add load testing.
- Add queue-based ingestion.
- Add ClickHouse for analytics if PostgreSQL becomes slow.
- Add object storage for evidence packs.

### Tech Stack

- Kubernetes
- Helm
- Prometheus
- Grafana
- Loki or ELK
- OpenTelemetry
- Kafka or RabbitMQ
- ClickHouse
- MinIO or S3
- k6 or Gatling

### Database Work

- Add read replicas where needed.
- Add partitioning for high-volume tables.
- Add ClickHouse analytical tables for invoices, payments, and risk signals if needed.

Candidate partitioned tables:

- invoices by invoice_date
- payment_transactions by payment_date
- audit_logs by created_at
- risk_signals by created_at

### Testing Gate

- Load test passes target volume.
- Backup restore test passes.
- Ingestion queue handles retry without duplication.
- Dashboard performance target passes.
- Observability shows API latency, ingestion lag, failed jobs, and database health.
- Disaster recovery runbook is tested.

### Exit Criteria

- The system is technically credible for enterprise deployment.

## Phase 19: Regional Adaptation Layer

### Objective

Make the platform reusable for other African revenue authorities.

### Scope

- Country configuration.
- Tax head configuration.
- Currency configuration.
- Filing period configuration.
- Taxpayer ID format configuration.
- Invoice schema adapter.
- Customs schema adapter.
- Penalty and interest rule configuration.
- Administrative region configuration.
- Language support path.

### Tech Stack

- Spring Boot configuration modules
- PostgreSQL config tables
- Next.js localization support

### Database Work

Add:

```sql
CREATE TABLE country_configs (
  id UUID PRIMARY KEY,
  country_code VARCHAR(10) NOT NULL UNIQUE,
  country_name VARCHAR(120) NOT NULL,
  currency_code VARCHAR(10) NOT NULL,
  taxpayer_id_label VARCHAR(80) NOT NULL,
  config_json JSONB NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE tax_head_configs (
  id UUID PRIMARY KEY,
  country_config_id UUID NOT NULL REFERENCES country_configs(id),
  tax_head_code VARCHAR(80) NOT NULL,
  tax_head_name VARCHAR(160) NOT NULL,
  filing_frequency VARCHAR(80),
  rules_json JSONB,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE(country_config_id, tax_head_code)
);
```

### Testing Gate

- Kenya config works.
- At least one sample non-Kenya config works with synthetic data.
- Taxpayer ID validation changes by country.
- Tax head labels and periods change by country.
- Existing Kenya workflows do not break.

### Exit Criteria

- The product is no longer hardcoded to Kenya only, while still serving Kenya deeply.

## Phase 20: Production Readiness and Launch Control

### Objective

Prepare the platform for controlled production use.

### Scope

- Final security review.
- Penetration testing.
- Data protection impact assessment.
- Production deployment runbook.
- Incident response plan.
- Support process.
- User training materials.
- Admin training.
- Monitoring dashboards.
- Go-live checklist.

### Tech Stack

- Final chosen deployment stack
- SIEM integration
- Backup tooling
- Monitoring stack
- Documentation portal

### Database Work

- Production migration dry run.
- Backup and restore dry run.
- Retention policy job.
- Audit log protection.

### Testing Gate

- All automated tests pass.
- End-to-end tests pass.
- Security tests pass.
- Load tests pass.
- Backup restore passes.
- Role and permission tests pass.
- Pilot user acceptance testing passes.
- Go-live checklist is signed off.

### Exit Criteria

- The platform can be launched into a controlled production or pilot production environment.

## Master Test Strategy

Every phase must add tests, not postpone them.

### Backend Tests

- Unit tests for services and rules
- Integration tests with PostgreSQL using Testcontainers
- API contract tests
- Authorization tests
- Audit logging tests

### Frontend Tests

- Component tests
- Playwright end-to-end tests
- Accessibility checks
- Large-table rendering tests
- Role-based UI tests

### Data Tests

- Schema validation
- Duplicate detection
- Referential integrity
- Data quality rules
- Synthetic scenario validation
- Reconciliation checks

### AI Tests

- Training reproducibility
- Prediction schema validation
- Explainability output validation
- Known anomaly detection
- Model versioning checks
- False-positive review workflow

### Security Tests

- Authentication tests
- Authorization tests
- Dependency scans
- Secret scanning
- OWASP ZAP scans
- Export permission tests
- Audit log integrity tests

### Performance Tests

- Ingestion throughput
- Rule execution time
- Dashboard query time
- Case search speed
- Evidence pack generation time
- Concurrent user simulation

## Completion Definition

The platform is considered complete for the first major version when it can:

- Ingest synthetic and approved pilot data.
- Build taxpayer profiles.
- Detect tax gaps.
- Reconcile government payments and settlements.
- Score risk using rules and AI.
- Show relationship intelligence.
- Create officer cases.
- Generate evidence packs.
- Send voluntary compliance nudges.
- Show executive and officer dashboards.
- Track recovery outcomes.
- Log all sensitive actions.
- Enforce roles and permissions.
- Export reports.
- Support a pilot buyer.
- Adapt to at least one additional country configuration.

## Development Order Summary

Do not build everything at once. Build in this order:

1. Foundation
2. Local environment
3. Database schema
4. Synthetic data
5. Ingestion
6. Entity resolution
7. Rules
8. Tax gaps
9. Cases and evidence
10. Dashboards
11. Settlement reconciliation
12. Nudges
13. AI scoring
14. Graph intelligence
15. Governance and security
16. Reporting
17. Pilot package
18. Integrations
19. Enterprise scale
20. Regional adaptation
21. Production launch

This sequence keeps the build grounded: data first, intelligence second, workflow third, AI and scale after the product already works.
