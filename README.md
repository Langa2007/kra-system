# KRA Revenue Intelligence and Tax Gap Detection Platform

## Project Purpose

This repository is the reference home for building a government-grade revenue intelligence system for the Kenya Revenue Authority (KRA), county governments, and other African revenue authorities.

The system is designed to help revenue agencies detect tax gaps, identify high-risk compliance cases, reconcile declared revenue against digital evidence, prioritize audit work, and increase voluntary compliance without overwhelming taxpayers or officers.

The long-term product name can be changed later, but the working name is:

**Revenue Intelligence and Compliance Assurance Platform**

The idea is not just to build another tax filing system. KRA already has iTax, eTIMS, customs systems, payment channels, and internal compliance processes. The opportunity is to build the intelligence layer that sits across those systems, connects data that currently lives in silos, and converts it into explainable audit leads, voluntary correction prompts, revenue leakage reports, and government-ready dashboards.

## Executive Summary

Kenya is actively pushing for stronger domestic revenue mobilization, better tax compliance, digital tax administration, and broader tax base expansion. KRA already operates major digital platforms such as eTIMS and iTax, and the National Treasury's Medium-Term Revenue Strategy for FY 2024/25 to FY 2026/27 emphasizes revenue growth, tax-base expansion, improved compliance, and plugging loopholes.

This platform will help KRA and similar agencies answer questions such as:

- Which taxpayers show the largest mismatch between electronic invoices and filed tax returns?
- Which importers bring in goods but declare unusually low domestic sales?
- Which suppliers receive withholding tax certificates but do not declare matching income?
- Which VAT claims are inconsistent with supplier invoices, customs records, or sector patterns?
- Which businesses are active in digital transactions but missing from the tax base?
- Which audit cases have the highest probability of recovering material revenue?
- Which taxpayers should receive soft voluntary compliance nudges before enforcement?
- Which government revenue channels show collection, settlement, or reconciliation gaps?

The product should become a decision-support platform for revenue officers, not an uncontrolled black-box punishment system. Every alert must be explainable, traceable, auditable, and tied to lawful data sources.

## Current Market and Existence Check

Research date: **2026-05-29**

The market check found several adjacent systems and initiatives, but not a publicly visible Kenya-specific, independent, end-to-end platform with the exact proposed combination of:

- Multi-source tax-gap detection for KRA
- eTIMS, iTax, customs, withholding tax, PAYE, rental income, and government payment reconciliation
- Explainable AI risk scoring
- Entity resolution and graph intelligence
- Audit case management
- Voluntary compliance workflows
- Deployment model adaptable to other African revenue authorities
- Revenue assurance and settlement monitoring for national and county governments

Important adjacent systems and signals:

- KRA already operates eTIMS, which requires persons engaged in business to onboard and issue electronic tax invoices.
- KRA has iTax, customs systems, and other internal revenue systems.
- KRA has publicly stated that it is strengthening digital infrastructure, data-driven decision-making, automation, taxpayer segmentation, and advanced analytics.
- The National Treasury's Medium-Term Revenue Strategy targets higher tax-to-GDP collection, broader tax base expansion, better compliance, and administrative efficiency.
- CIAT has an open-source electronic invoicing anomaly detector, e-IAD, which uses electronic invoices, tax returns, and taxpayer registry data. It has been installed in countries such as Costa Rica, Guatemala, and Rio Grande do Sul.
- Private taxpayer-side Kenyan eTIMS tools exist, such as eTIMS integrators, compliance tools, and invoicing helpers.
- International fiscalization and e-invoicing vendors exist, but they generally focus on invoice infrastructure, fiscal devices, VAT reporting, or generic government tax platforms.

Conclusion:

The opportunity is viable if positioned correctly. The winning angle is not "KRA does not have systems." KRA does have systems. The opportunity is to build a **specialized revenue intelligence layer** that integrates existing data, generates explainable compliance insights, improves officer productivity, and can also be sold to counties and other African governments.

The product must be built as a complementary platform, not as a replacement for iTax or eTIMS.

## Strategic Positioning

### Primary Positioning

**A revenue intelligence and compliance assurance platform that helps tax authorities detect tax gaps, prioritize audit cases, and increase voluntary compliance using explainable analytics across e-invoicing, tax returns, customs, payments, and taxpayer registry data.**

### Buyer Language

For KRA:

> "A data-driven compliance intelligence platform that converts eTIMS, iTax, customs, WHT, PAYE, and taxpayer registry data into explainable risk scores, audit leads, voluntary compliance nudges, and revenue recovery dashboards."

For county governments:

> "A revenue assurance platform that reconciles county permits, rates, licenses, cess, market fees, parking, payment channels, and bank settlements to detect leakage and improve own-source revenue collection."

For other African revenue authorities:

> "A modular tax-gap detection and revenue assurance system for e-invoicing, VAT, customs, taxpayer registration, withholding tax, payroll taxes, and audit prioritization."

For Treasury, Auditor-General, Controller of Budget, or public finance agencies:

> "A public revenue assurance platform that tracks collection, allocation, settlement, and reconciliation across government revenue channels."

## Core Problem

African governments need more domestic revenue but often face:

- Underdeclared income
- VAT underreporting
- Fake or inflated input VAT claims
- Mismatches between imports and domestic sales
- Informal businesses that remain outside the tax base
- Weak reconciliation between payments collected and money settled
- Siloed systems that do not speak to each other
- Manual audit selection that misses high-value cases
- Officers spending too much time on low-yield compliance work
- Lack of clear evidence packs for audit and enforcement
- Limited taxpayer nudging before formal enforcement
- Difficulty monitoring county and agency revenue leakages

The platform solves these problems by turning fragmented data into usable intelligence.

## Product Vision

The complete system should become the operating intelligence layer for revenue compliance.

It should:

- Ingest structured data from tax, invoice, customs, payment, registry, and licensing systems.
- Build a single taxpayer and entity view.
- Detect discrepancies between declared and observed economic activity.
- Use rules, statistics, machine learning, and graph analytics to score risk.
- Generate explainable case files for officers.
- Recommend the best next action: ignore, monitor, nudge, request documents, audit, investigate, or refer.
- Track outcomes and revenue recovered.
- Continuously learn which signals produce successful recoveries.
- Produce dashboards for executives, regional offices, audit teams, and policy teams.
- Support both national tax authority use and county revenue assurance use.

## System Principles

The system must be:

- **Explainable**: Every flag must show the reason, source data, formula, confidence level, and recommended action.
- **Auditable**: Every data access, model decision, officer action, and case update must be logged.
- **Modular**: KRA, counties, and other countries should be able to adopt modules independently.
- **Privacy-aware**: Use lawful, necessary, proportionate data. Avoid unnecessary personal profiling.
- **Officer-friendly**: The platform should reduce manual burden and make officers faster.
- **Taxpayer-fair**: Risk scores should guide review, not automatically punish taxpayers.
- **Integration-first**: It should connect to existing government systems instead of trying to replace them.
- **Evidence-driven**: Cases should be based on measurable mismatches, not rumors or weak signals.
- **Scalable**: The architecture should support national-scale invoice, payment, and customs data.
- **Adaptable**: The same core should work for Kenya, counties, and other African administrations.

## Main Users

### KRA Executive Leadership

Needs:

- Revenue gap dashboards
- Sector risk analysis
- Regional performance
- Revenue recovery tracking
- Policy impact analysis
- Tax base expansion insights

### Compliance Officers

Needs:

- Prioritized audit leads
- Explainable evidence packs
- Taxpayer history
- Case workflow
- Document request tools
- Outcome recording

### Audit Teams

Needs:

- High-value cases
- Cross-source discrepancy analysis
- Supporting data
- Case notes
- Recovery estimates
- Escalation workflow

### Domestic Taxes Department

Needs:

- VAT mismatch detection
- Income tax underdeclaration analysis
- PAYE anomalies
- WHT mismatches
- Rental income risk
- Turnover tax compliance patterns

### Customs and Border Teams

Needs:

- Importer risk scoring
- Import-to-sales mismatch analysis
- Undervaluation indicators
- HS code anomaly detection
- Related-party trade signals

### County Revenue Departments

Needs:

- Own-source revenue reconciliation
- Business permit compliance
- Property rates monitoring
- Market fee collections
- Parking revenue assurance
- Single business permit risk mapping

### Treasury and Public Finance Oversight Teams

Needs:

- Government collection and settlement reconciliation
- MDA revenue monitoring
- Missing settlement detection
- Payment channel performance
- Public finance leakage reporting

### Taxpayers and Tax Agents

Needs, where a taxpayer-facing module is provided:

- Compliance health score
- Correction recommendations
- Filing mismatch explanations
- Voluntary disclosure or correction workflows
- Downloadable reconciliation reports

## Core Product Modules

### 1. Data Ingestion Layer

This layer pulls data from internal and external systems.

Potential sources:

- eTIMS invoices
- iTax returns
- VAT returns
- Income tax returns
- PAYE returns
- Withholding tax certificates
- Customs import declarations
- Customs export declarations
- Excise records
- Taxpayer registration data
- Company registry data
- Beneficial ownership records, where lawfully available
- Business permits
- County licensing systems
- Property rates data
- Government payment gateway transactions
- Bank settlement statements
- MDA revenue collection files
- Procurement supplier records
- Public business listings
- Approved third-party datasets

Supported ingestion methods:

- REST APIs
- SOAP APIs, where legacy government systems require it
- Secure file transfer via SFTP
- Batch CSV, Excel, XML, JSON, or Parquet uploads
- Database replication or read-only connectors
- Message queues and event streams
- Manual officer uploads for pilot environments

Minimum ingestion features:

- Source registration
- Schema mapping
- Field validation
- Duplicate detection
- Error handling
- Data lineage tracking
- Ingestion logs
- Retry and quarantine queues
- Source freshness monitoring

### 2. Master Taxpayer and Entity Index

This module creates a single view of each taxpayer or organization.

It should resolve:

- KRA PIN
- Business registration number
- Company name
- Trading names
- Directors
- Beneficial owners
- Physical locations
- Tax obligations
- Branches
- Importer/exporter codes
- eTIMS devices or integrations
- Paybill/till numbers, where lawfully provided
- Bank accounts, where lawfully provided
- County permits
- Related companies
- Supplier and buyer relationships

Matching methods:

- Exact matching
- Fuzzy name matching
- Identifier matching
- Address normalization
- Phone/email matching, where lawful
- Director and ownership graph matching
- Taxpayer registry joins
- Confidence scoring

Output:

- Unified taxpayer profile
- Linked identifiers
- Data-source provenance
- Relationship graph
- Confidence score for each match
- Duplicate taxpayer alerts

### 3. Risk Rules Engine

The rules engine handles deterministic compliance checks.

Example rules:

- eTIMS sales exceed declared VAT sales by more than a configured threshold.
- Supplier issued invoices but did not declare corresponding income.
- Buyer claimed input VAT from a supplier whose invoice is missing, cancelled, invalid, or inconsistent.
- Imports exceed declared domestic sales over a defined period.
- WHT certificate income exceeds declared income.
- PAYE headcount drops sharply while business sales rise.
- A taxpayer files nil returns while issuing eTIMS invoices.
- A business has active permits but no matching tax activity.
- A taxpayer claims expenses from non-compliant suppliers.
- A sector-normal margin differs materially from declared margins.
- Customs value, freight, and insurance do not align with expected landed costs.
- Government payment channel collection does not equal bank settlement.

The rules engine must allow non-developers to configure:

- Thresholds
- Tax head
- Period
- Region
- Sector
- Taxpayer segment
- Severity
- Required supporting evidence
- Recommended action

### 4. Machine Learning Risk Scoring

The ML layer should help detect patterns that static rules miss.

Recommended techniques:

- Unsupervised anomaly detection
- Semi-supervised learning where confirmed audit outcomes exist
- Gradient boosting for risk scoring when labels become available
- Clustering taxpayers by sector and size
- Time-series anomaly detection
- Graph-based risk propagation
- Natural language processing for document classification

Possible models:

- Isolation Forest
- Local Outlier Factor
- One-Class SVM for small experiments
- XGBoost or LightGBM for supervised risk models
- CatBoost for mixed categorical and numeric tax data
- Autoencoders for high-dimensional anomaly detection
- Prophet, ARIMA, or modern time-series models for collection trends
- Node embeddings for relationship graph risk

ML outputs:

- Risk score from 0 to 100
- Confidence level
- Main contributing factors
- Similar taxpayer peer group
- Expected vs observed values
- Suggested next action
- Model version
- Explanation payload

The ML layer must never be the only source of action. It should support officer decision-making.

### 5. Tax Gap Detection Engine

This is the core intelligence module.

Tax-gap categories:

- VAT output underdeclaration
- VAT input overclaim
- Income tax underdeclaration
- PAYE underdeclaration
- WHT mismatch
- Customs undervaluation
- Import-to-sales mismatch
- Excise leakage
- Rental income underdeclaration
- Turnover tax non-compliance
- Digital business compliance gaps
- County permit-to-tax mismatch
- Government collection-to-settlement leakage

For each tax gap, the system should calculate:

- Taxpayer
- Tax head
- Period
- Declared amount
- Observed or expected amount
- Gap amount
- Potential tax exposure
- Penalty and interest estimate, where rules are configured
- Confidence level
- Evidence sources
- Recommended action

### 6. Graph Intelligence Module

Tax evasion and leakage often happen through relationships. The graph module should reveal connected entities.

Graph nodes:

- Taxpayers
- Companies
- Directors
- Beneficial owners
- Branches
- Suppliers
- Buyers
- Importers
- Customs agents
- eTIMS devices
- Permits
- Properties
- Payment accounts
- Government agencies
- Bank settlement accounts

Graph edges:

- Owns
- Directs
- Supplies
- Buys from
- Shares address with
- Shares phone with
- Imports for
- Issues invoice to
- Receives payment from
- Settles to
- Uses same device
- Uses same customs agent

Use cases:

- Detect invoice rings
- Detect related-party supplier chains
- Detect shell suppliers
- Detect repeated suspicious customs agents
- Identify linked taxpayers spreading activity across multiple entities
- Identify government revenue settlement anomalies
- Discover dormant companies connected to active high-risk directors

### 7. Case Management and Workflow

The platform must include a serious case workflow, because intelligence without action is just a dashboard.

Case lifecycle:

1. Signal generated
2. Risk scored
3. Officer review
4. Evidence pack created
5. Taxpayer nudge or document request
6. Taxpayer response
7. Officer assessment
8. Audit or compliance action
9. Recovery, closure, escalation, or dismissal
10. Outcome feedback to the model

Case features:

- Case queue
- Officer assignment
- Priority ranking
- SLA tracking
- Evidence attachments
- Comments and notes
- Document request templates
- Taxpayer communication history
- Approval workflow
- Escalation workflow
- Closure reasons
- Recovery amount
- Model feedback
- Exportable case report

### 8. Evidence Pack Generator

Every alert should produce an evidence pack.

Evidence pack contents:

- Taxpayer profile
- Tax head
- Period under review
- Summary of discrepancy
- Source datasets used
- Declared values
- Observed values
- Calculated gap
- Rule or model that triggered the case
- Top risk factors
- Linked entities
- Relevant invoices, returns, declarations, certificates, or payments
- Officer checklist
- Recommended next action
- Legal and policy notes configured by the agency
- Audit trail

Export formats:

- PDF
- Excel
- CSV
- JSON
- Internal case file

### 9. Voluntary Compliance and Nudging Module

The platform should support soft compliance before formal enforcement.

Use cases:

- Notify taxpayers of mismatches.
- Invite taxpayers to review and amend returns.
- Remind businesses to onboard to eTIMS.
- Alert taxpayers about missing supplier invoices.
- Warn taxpayers when claimed expenses may be rejected.
- Encourage landlords, small businesses, and digital sellers to regularize.

Channels:

- Email
- SMS
- iTax message center integration
- eTIMS portal integration
- Taxpayer dashboard
- Agent dashboard

Nudge design:

- Clear explanation
- Period affected
- Amount in question
- Recommended correction
- Deadline
- Link to official filing or correction process
- Contact/support path

### 10. Revenue Assurance and Settlement Monitor

This module expands the platform beyond KRA tax enforcement into broader government revenue assurance.

It tracks whether money collected by government channels is fully settled to the correct accounts.

Use cases:

- eCitizen payment reconciliation
- MDA revenue monitoring
- County payment channel reconciliation
- Parking revenue reconciliation
- Market fee collections
- Business permit revenue
- Land rates and rent payments
- Court fees
- Hospital fees
- School or institutional fees
- Agency service charges

Core checks:

- Transaction collected but not settled
- Settlement delayed beyond SLA
- Settled to wrong account
- Duplicate transaction
- Cancelled transaction after service delivery
- Convenience fee mismatch
- Missing agency allocation
- Manual override without approval
- Unexpected drop in collections
- Channel-level underperformance

This module may be the best first pilot because it is less dependent on sensitive taxpayer profiling and has a clear public finance value proposition.

### 11. Dashboards and Reports

Dashboard types:

- Executive revenue gap dashboard
- Tax head dashboard
- Sector risk dashboard
- Regional risk dashboard
- Audit pipeline dashboard
- Officer productivity dashboard
- Revenue recovery dashboard
- Compliance nudge dashboard
- Customs risk dashboard
- VAT risk dashboard
- PAYE risk dashboard
- WHT risk dashboard
- County own-source revenue dashboard
- Government settlement dashboard
- Model performance dashboard

Common metrics:

- Estimated tax gap
- Potential recoverable revenue
- Confirmed recovered revenue
- Number of high-risk taxpayers
- Cases opened
- Cases closed
- Average case age
- Officer workload
- False positive rate
- Revenue recovered per officer
- Sector concentration
- Regional concentration
- Compliance improvement after nudges
- Payment settlement delays

### 12. Administration and Governance Module

Required admin features:

- User management
- Role-based access control
- Multi-factor authentication
- Agency and department management
- Region and office configuration
- Data-source configuration
- Rule configuration
- Threshold management
- Model version management
- Case workflow configuration
- Notification templates
- Audit logs
- Data retention rules
- Approval workflows

## Detailed Use Cases

### VAT Output Underdeclaration

Scenario:

A business issues many eTIMS invoices during a month, but its VAT return declares lower taxable sales.

System flow:

1. Ingest eTIMS invoice totals.
2. Ingest VAT return.
3. Match taxpayer by PIN.
4. Compare taxable sales by month.
5. Adjust for credit notes, exemptions, cancelled invoices, and timing differences.
6. Calculate mismatch.
7. Score severity based on amount, frequency, and taxpayer history.
8. Generate evidence pack.
9. Recommend voluntary correction or officer review.

### VAT Input Overclaim

Scenario:

A taxpayer claims input VAT using invoices that are invalid, missing, cancelled, or inconsistent with supplier declarations.

System flow:

1. Ingest buyer input claims.
2. Match claimed invoices against eTIMS invoice records.
3. Check supplier validity and VAT registration status.
4. Check invoice status.
5. Compare claimed amount with invoice amount.
6. Flag invalid or unsupported claims.
7. Estimate recoverable VAT.

### Import-to-Sales Mismatch

Scenario:

An importer brings in goods worth a large amount but declares very low domestic sales.

System flow:

1. Ingest customs import declarations.
2. Map HS codes to product categories.
3. Calculate landed cost.
4. Estimate expected sales using sector margins.
5. Compare against VAT and income tax declarations.
6. Adjust for inventory cycles and seasonality.
7. Score risk and generate case.

### WHT Certificate Mismatch

Scenario:

A supplier receives withholding tax certificates from buyers, but the supplier's income tax return does not reflect the same income.

System flow:

1. Ingest WHT certificates.
2. Ingest income tax declarations.
3. Match supplier PIN.
4. Compare certificate gross amounts with declared revenue.
5. Identify unexplained gaps.
6. Create compliance nudge or audit case.

### PAYE Underdeclaration

Scenario:

A business with rising revenue reports unusually low payroll tax compared to sector peers.

System flow:

1. Ingest PAYE returns.
2. Ingest sales and VAT data.
3. Compare payroll ratio to sector benchmarks.
4. Look for sudden staff count changes.
5. Check related statutory contribution signals if lawfully integrated.
6. Flag for review where risk is material.

### Rental Income Compliance

Scenario:

A property owner has multiple rentable properties but declares little or no rental income.

System flow:

1. Ingest property records and county rates data.
2. Link owner to KRA PIN.
3. Estimate rental potential based on location, property type, and market rates.
4. Compare with declared rental income.
5. Prioritize cases where evidence is strong and revenue exposure is material.

### County Revenue Leakage

Scenario:

A county collects parking or market fees through multiple channels, but bank settlement does not match daily collections.

System flow:

1. Ingest payment transactions.
2. Ingest bank settlement files.
3. Ingest service delivery records.
4. Reconcile collection, allocation, and settlement.
5. Flag missing, delayed, duplicated, reversed, or misallocated payments.
6. Generate daily exception report.

## Data Model Overview

Core entities:

- Taxpayer
- Organization
- Person
- TaxObligation
- FilingReturn
- Invoice
- InvoiceLine
- CustomsDeclaration
- WithholdingCertificate
- PayrollReturn
- PaymentTransaction
- SettlementRecord
- Property
- BusinessPermit
- RevenueChannel
- RiskSignal
- RiskScore
- Case
- EvidencePack
- User
- Officer
- Department
- AuditLog
- Notification
- ModelVersion
- RuleDefinition

Important relationships:

- Taxpayer has many returns.
- Taxpayer has many invoices issued.
- Taxpayer has many invoices received.
- Taxpayer has many customs declarations.
- Taxpayer has many permits.
- Taxpayer has many risk signals.
- Risk signal belongs to a rule or model.
- Case belongs to taxpayer.
- Case contains evidence pack.
- Case has assigned officer.
- Officer actions produce audit logs.
- Payment transaction belongs to revenue channel.
- Settlement record reconciles payment transaction.

## Suggested Database Tables

Minimum MVP tables:

- taxpayers
- taxpayer_identifiers
- tax_returns
- invoices
- invoice_lines
- customs_declarations
- withholding_certificates
- payment_transactions
- settlement_records
- business_permits
- properties
- risk_rules
- risk_signals
- risk_scores
- cases
- case_events
- evidence_packs
- users
- roles
- permissions
- audit_logs
- data_sources
- ingestion_jobs

Later tables:

- directors
- beneficial_owners
- entity_relationships
- graph_edges
- sector_benchmarks
- model_versions
- model_predictions
- notification_templates
- taxpayer_notifications
- documents
- officer_workloads
- recovery_records
- appeals_or_disputes

## Technology Stack

The full build should use technologies that can survive government scale, security review, procurement review, and long-term maintenance.

### Frontend

Recommended:

- Next.js
- React
- TypeScript
- Tailwind CSS
- shadcn/ui or a custom government-grade design system
- TanStack Query for server state
- TanStack Table or AG Grid for large tables
- Recharts, ECharts, or Apache Superset embeds for charts
- Mapbox, Leaflet, or OpenLayers for geographic risk maps

Frontend requirements:

- Responsive web app
- Dark and light mode optional
- Dense officer dashboards
- Accessible forms and tables
- Advanced filters
- Exportable reports
- Case workflow UI
- Role-based navigation

### Backend API

Recommended primary options:

- Java Spring Boot for enterprise government environments
- NestJS for TypeScript-first teams
- FastAPI for Python-heavy analytics teams

Strong recommendation:

- Use Spring Boot or NestJS for the core transactional platform.
- Use Python services for analytics and machine learning.

Backend requirements:

- REST APIs
- Internal service APIs
- OpenAPI documentation
- Strong validation
- Rate limiting
- Idempotent ingestion endpoints
- Background jobs
- Multi-tenant or multi-agency support
- Role-based authorization
- Audit logging

### Analytics and Data Engineering

Recommended:

- Python
- Pandas
- Polars
- PySpark for national scale
- dbt for transformations
- Apache Airflow or Dagster for orchestration
- Great Expectations or Soda for data quality
- DuckDB for local prototyping
- Apache Parquet for analytical storage

### Machine Learning

Recommended:

- Python
- scikit-learn
- XGBoost
- LightGBM
- CatBoost
- PyTorch only where deep learning is justified
- MLflow for model tracking
- SHAP for explainability
- Evidently AI or custom model monitoring

### Databases and Storage

Recommended:

- PostgreSQL for application data
- TimescaleDB extension for time-series revenue monitoring
- ClickHouse for high-volume analytical queries
- Neo4j for graph analytics, or PostgreSQL graph tables for MVP
- Redis for caching and job coordination
- Object storage compatible with S3 for files and evidence packs
- MinIO for local/private deployments

### Search

Recommended:

- OpenSearch or Elasticsearch

Use cases:

- Taxpayer search
- Invoice search
- Case search
- Document search
- Audit trail search

### Messaging and Background Processing

Recommended:

- Apache Kafka for high-volume event streaming
- RabbitMQ for simpler queues
- Redis Queue, BullMQ, or Celery for MVP background jobs

### Identity and Access Management

Recommended:

- Keycloak
- OpenID Connect
- OAuth2
- SAML support if government SSO requires it
- Multi-factor authentication
- Fine-grained RBAC
- Attribute-based access control for sensitive data

### Security

Required:

- TLS everywhere
- Encryption at rest
- Secrets manager
- Database encryption
- Field-level encryption for sensitive fields
- Strong password policy
- MFA for officers
- IP allowlisting where required
- Full audit logs
- Tamper-evident logs
- Session timeout
- Device/session management
- Security event monitoring
- Vulnerability scanning
- Dependency scanning
- Penetration testing before pilots

Useful tools:

- HashiCorp Vault or cloud secrets manager
- Trivy
- OWASP ZAP
- Snyk or Dependabot
- Wazuh, ELK, Splunk, or similar SIEM integration

### Infrastructure

Deployment options:

- Government private cloud
- On-premise data center
- Sovereign cloud
- Approved public cloud region if permitted
- Hybrid deployment

Recommended infrastructure:

- Docker
- Kubernetes
- Helm
- Terraform
- GitHub Actions, GitLab CI, or Jenkins
- Prometheus
- Grafana
- Loki or ELK for logs
- OpenTelemetry for tracing

### Reporting and Business Intelligence

Recommended:

- Apache Superset
- Metabase for MVP
- Power BI integration if government users already rely on Microsoft tools
- Custom executive dashboards inside the web app

### Document Generation

Recommended:

- Server-side PDF generation
- Excel export
- CSV export
- Signed evidence pack exports

Possible libraries:

- JasperReports for Java
- WeasyPrint for Python
- Playwright PDF rendering
- ExcelJS for Node.js
- Apache POI for Java

## Recommended Architecture

High-level architecture:

1. Source systems produce data.
2. Ingestion services validate and load data.
3. Raw data is stored in a lakehouse.
4. Transformation jobs clean and normalize data.
5. Master entity resolution builds taxpayer profiles.
6. Rules engine generates deterministic signals.
7. ML services generate anomaly scores and risk predictions.
8. Graph engine enriches signals with relationship context.
9. Case engine converts high-value signals into workflows.
10. Dashboard API serves officer and executive views.
11. Notification service sends nudges and requests.
12. Audit service records every data and user action.

Suggested service boundaries:

- auth-service
- ingestion-service
- taxpayer-service
- invoice-service
- customs-service
- payment-reconciliation-service
- risk-engine-service
- ml-scoring-service
- graph-service
- case-management-service
- notification-service
- reporting-service
- audit-service
- admin-service

For MVP, these can be implemented as a modular monolith to move faster. As the product matures, split into services where scale or ownership requires it.

## MVP Scope

The MVP should prove value without needing full KRA integration.

MVP goal:

Build a working demo that shows how the platform detects tax gaps and creates explainable audit cases using synthetic or approved sample data.

MVP modules:

- Data upload/import
- Taxpayer profile
- eTIMS-like invoice ingestion
- VAT return ingestion
- Customs-like import data ingestion
- WHT certificate ingestion
- Rule-based risk engine
- Basic anomaly scoring
- Risk dashboard
- Case management
- Evidence pack export
- Admin user roles
- Audit logs

MVP datasets:

- Synthetic taxpayer registry
- Synthetic invoice records
- Synthetic VAT returns
- Synthetic customs declarations
- Synthetic WHT certificates
- Synthetic payment transactions

MVP demo cases:

- Invoice sales higher than declared VAT sales
- Import value higher than declared sales
- WHT certificate mismatch
- Nil filer issuing invoices
- Invalid input VAT claim
- Government payment settlement mismatch

MVP success criteria:

- User can upload data.
- System validates data.
- System links records to taxpayers.
- System calculates risk signals.
- Dashboard ranks taxpayers by potential revenue gap.
- Officer can open a case.
- Officer can view evidence.
- Officer can export an evidence pack.
- All actions are logged.

## Build Roadmap

### Phase 0: Research and Product Foundation

Deliverables:

- This README
- Product requirements document
- Data dictionary
- Synthetic data generator
- Architecture diagram
- Security and privacy design
- Pitch deck
- Pilot proposal

### Phase 1: Local MVP

Deliverables:

- Web dashboard
- Backend API
- PostgreSQL database
- Synthetic data import
- Rule engine
- Basic risk scoring
- Case management
- Evidence pack export

Suggested stack:

- Next.js frontend
- NestJS or Spring Boot backend
- PostgreSQL
- Python analytics worker
- Docker Compose

### Phase 2: Pilot-Ready Platform

Deliverables:

- Strong authentication
- Role-based access
- Audit logs
- Configurable rules
- Data quality dashboard
- Secure ingestion
- Automated reports
- Officer workflow
- Model explainability
- Deployment scripts

### Phase 3: Government Pilot

Possible pilot buyers:

- County revenue department
- Public agency with many payments
- Tax consulting firm serving businesses
- Large enterprise supplier compliance team
- KRA innovation or compliance department, if access is possible

Pilot goal:

- Prove that the platform finds real discrepancies and improves revenue recovery or compliance efficiency.

### Phase 4: Enterprise Government Build

Deliverables:

- High availability
- Scalable ingestion
- Advanced ML
- Graph analytics
- Case SLA engine
- Integration adapters
- SIEM integration
- Data retention controls
- Disaster recovery
- Multi-agency tenancy

### Phase 5: Regional Expansion

Target countries:

- Kenya
- Uganda
- Tanzania
- Rwanda
- Zambia
- Ghana
- Nigeria
- Ethiopia
- Malawi
- Botswana

Expansion approach:

- Keep the core platform generic.
- Create country-specific tax adapters.
- Localize tax heads, IDs, filing periods, and rules.
- Support different e-invoicing and customs systems.

## Go-To-Market Strategy

### Best First Wedge

The easiest first wedge may be the **Revenue Assurance and Settlement Monitor** for counties or government agencies.

Reasons:

- Clear value
- Faster pilot
- Less dependency on national taxpayer data
- Less political sensitivity
- Strong public finance story
- Reusable technology for the KRA product

### Second Wedge

Taxpayer-side compliance intelligence for businesses and tax agents.

This can help build:

- eTIMS understanding
- Tax data models
- Market credibility
- Case studies
- Revenue before government procurement closes

### Main Government Wedge

KRA or other revenue authority pilot focused on:

- VAT mismatch detection
- WHT mismatch detection
- Import-to-sales mismatch
- Audit case prioritization

### Procurement Routes

Possible routes:

- Public tender
- Framework agreement with an existing ICT vendor
- Public-private partnership
- Privately initiated proposal where permitted
- Donor-funded public finance reform project
- County pilot followed by intergovernmental expansion
- Innovation sandbox or proof of concept

### Pricing Models

Possible pricing:

- Setup and integration fee
- Annual software license
- Support and maintenance fee
- Per-agency subscription
- Per-module subscription
- Usage-based fee by transaction volume
- Capped success fee based on independently verified recovered revenue

Recommended:

Use annual license plus implementation fee for government buyers. Add performance-based components carefully and only where procurement rules allow.

## Competitive Advantage

The platform should win by being:

- Kenya-first and Africa-adaptable
- Built around eTIMS, iTax-like systems, customs, and county revenue realities
- Explainable rather than black-box
- Modular enough for counties and national authorities
- Strong on revenue assurance, not just tax enforcement
- Designed for officer workflow, not just dashboards
- Built with audit logs and legal defensibility from the beginning
- Capable of generating evidence packs, not just charts
- Able to start with synthetic or partial data and mature into full integration

## Legal, Privacy, and Governance Design

The system must be designed to operate under applicable law, including Kenya's Data Protection Act and any public-sector ICT, procurement, tax administration, and cybersecurity requirements.

Key governance requirements:

- Lawful basis for every data source
- Data minimization
- Purpose limitation
- Role-based access control
- Access logs
- Data retention schedules
- Privacy impact assessment
- Human review for adverse decisions
- Explainability of risk scores
- Model monitoring
- Secure integration agreements
- Clear data processing agreements

The product should not depend on informal access to sensitive financial or personal data. It should be able to work with official data provided under a lawful government integration.

## AI Design

AI should be used in a practical and defensible way.

AI use cases:

- Anomaly detection
- Risk scoring
- Document classification
- Entity resolution support
- Similar-case search
- Officer assistant for summarizing evidence packs
- Forecasting revenue collection
- Detecting unusual sector patterns

AI should not:

- Automatically punish taxpayers
- Replace officer judgment
- Hide the reason behind a risk score
- Use unexplained personal profiling
- Produce case conclusions without evidence

AI governance:

- Model registry
- Model versioning
- Training dataset documentation
- Bias and false-positive testing
- Human approval gates
- Explanation logs
- Periodic model review
- Outcome-based model improvement

## Security Requirements

Minimum security requirements:

- MFA for all privileged users
- RBAC and least privilege
- Encryption in transit
- Encryption at rest
- Secure key management
- Audit trails for every sensitive action
- Immutable or tamper-evident logging
- Secure file upload scanning
- Rate limiting
- API authentication
- API authorization
- Environment separation
- Secrets never committed to source control
- Backups
- Disaster recovery plan
- Vulnerability scans
- Penetration test before production

High-risk actions requiring audit:

- Viewing taxpayer profile
- Exporting evidence pack
- Changing risk rule
- Changing model version
- Assigning case
- Closing case
- Sending taxpayer notification
- Downloading bulk data
- Granting access
- Deleting or archiving data

## Development Standards

Codebase expectations:

- Clear module boundaries
- Strong typing where possible
- API-first design
- Tests for business rules
- Seed data for demos
- Repeatable local setup
- Dockerized dependencies
- Database migrations
- CI pipeline
- Linting and formatting
- Security checks
- API documentation
- Architecture decision records

Testing strategy:

- Unit tests for rules
- Integration tests for ingestion
- API tests
- End-to-end dashboard tests
- Data quality tests
- Security tests
- Model validation tests
- Performance tests for large datasets

## Suggested Repository Structure

```text
kra-system/
  README.md
  docs/
    architecture.md
    data-dictionary.md
    product-requirements.md
    security-model.md
    privacy-impact.md
    pilot-proposal.md
    pitch-deck-notes.md
  apps/
    web/
    api/
    worker/
  packages/
    shared/
    rules-engine/
    data-contracts/
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
```

## Initial Development Plan

### Step 1: Create Synthetic Data

Generate:

- 10,000 taxpayers
- 200,000 invoices
- 20,000 VAT returns
- 5,000 customs declarations
- 10,000 WHT certificates
- 50,000 payment transactions
- 2,000 settlement records

Include known risk patterns so the demo can prove detection.

### Step 2: Build Database and API

Build:

- Taxpayer CRUD
- Ingestion endpoints
- Risk rules endpoint
- Case endpoint
- Dashboard endpoint
- Evidence pack endpoint

### Step 3: Build Rule Engine

Implement:

- VAT output mismatch
- VAT input mismatch
- Import-to-sales mismatch
- WHT mismatch
- Nil filer with invoices
- Payment settlement mismatch

### Step 4: Build Dashboard

Screens:

- Overview dashboard
- Taxpayer search
- Taxpayer profile
- Risk queue
- Case detail
- Evidence pack viewer
- Rule configuration
- Ingestion status
- Reports

### Step 5: Add ML Scoring

Start with:

- Isolation Forest anomaly detection
- Peer-group comparison
- SHAP explanations if supervised models are introduced

### Step 6: Prepare Pilot Package

Create:

- Demo environment
- Pitch deck
- Pilot proposal
- Security overview
- Data processing overview
- ROI calculator
- Sample evidence packs

## ROI Model

Revenue authorities care about measurable impact.

Potential ROI metrics:

- Additional revenue identified
- Additional revenue recovered
- Audit hit rate improvement
- Average recovery per case
- Reduction in low-value audits
- Time saved per officer
- Voluntary correction rate
- Reduction in settlement leakage
- Faster reconciliation
- Increase in compliant taxpayers

Simple ROI calculation:

```text
Estimated Recoverable Revenue =
  Sum of High Confidence Tax Gaps
  x Expected Confirmation Rate
  x Expected Collection Rate
```

Example:

```text
KES 2 billion identified tax gaps
x 40% confirmed after review
x 60% collected
= KES 480 million recovered
```

## Product Risks and How the System Handles Them

### Data Quality Risk

Mitigation:

- Data validation
- Source freshness monitoring
- Confidence scoring
- Officer review
- Clear evidence provenance

### False Positive Risk

Mitigation:

- Explainable scoring
- Human review
- Taxpayer nudges before enforcement
- Outcome feedback loops
- Threshold tuning

### Procurement Risk

Mitigation:

- Start with pilots
- Partner with established ICT integrators
- Build modular product
- Offer county and agency use cases
- Prepare strong documentation

### Integration Risk

Mitigation:

- Support batch uploads first
- Build adapters gradually
- Use open data contracts
- Keep ingestion loosely coupled

### Trust Risk

Mitigation:

- Strong audit logs
- Legal compliance
- Explainability
- Privacy-by-design
- Human decision gates

## Adaptation for Other African Governments

The product should be built with country adapters.

Configurable items:

- Taxpayer ID format
- Tax heads
- Filing periods
- Currency
- Sector codes
- Invoice schema
- Customs schema
- Payment channels
- Penalty rules
- Administrative regions
- Government departments
- User roles
- Language

Reusable core:

- Ingestion engine
- Entity resolution
- Risk rules
- ML scoring
- Graph analytics
- Case management
- Evidence packs
- Audit logging
- Dashboards

## Reference Sources

These sources informed the project direction and viability check:

- KRA eTIMS official page: https://www.kra.go.ke/online-services/etims
- KRA organizational reforms and data-driven administration: https://www.kra.go.ke/news-center/press-release/2165-kra-commences-organizational-reforms-to-enhance-efficiency-and-personalized-customer-centric-service
- Kenya Medium-Term Revenue Strategy FY 2024/25 - 2026/27: https://www.treasury.go.ke/wp-content/uploads/2023/12/Medium-Term-Revenue-Strategy-2023.pdf
- Kenya Data Protection Act: https://new.kenyalaw.org/akn/ke/act/2019/24/eng@2019-11-15
- Kenya Public Private Partnerships Act: https://new.kenyalaw.org/akn/ke/act/2021/14
- CIAT e-IAD Electronic Invoicing Anomaly Detector: https://www.ciat.org/e-iad-electronic-invoicing-anomaly-detector/?lang=en
- KRA eTIMS reverse invoicing: https://www.kra.go.ke/business/etims-electronic-tax-invoice-management-system/learn-about-etims/reverse-invoicing

## Final Product Definition

The system we are building is a full revenue intelligence platform for government.

It will:

- Detect tax gaps.
- Reconcile revenue.
- Prioritize audit cases.
- Generate explainable evidence.
- Support voluntary compliance.
- Help officers work faster.
- Help executives see where revenue is leaking.
- Help counties improve own-source revenue.
- Help African governments modernize tax administration.

The long-term goal is to become the trusted intelligence layer between raw government revenue data and real-world revenue recovery.

