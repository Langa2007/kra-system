# Deployment Overview

## Demo Environment

- Docker Compose services for API, PostgreSQL, Redis, Neo4j, and MinIO.
- Next.js frontend served locally.
- Spring Boot API with Flyway migrations.
- Synthetic data and demo fallbacks for buyer walkthroughs.

## Controlled Pilot Stack

- Spring Boot API.
- PostgreSQL primary database.
- Next.js officer and executive web app.
- MinIO-compatible object storage for files and evidence packs.
- Neo4j optional for graph intelligence.
- Keycloak-compatible identity and MFA path.
- Dependency scanning and audit review before pilot use.

## Operational Checks

- API health endpoint is available.
- Database migrations run cleanly from a fresh schema.
- Demo users can authenticate and access their intended views.
- Dashboard outputs reconcile to source records.
- Evidence pack export works during the demo.
