# Security Overview

## Identity And Access

- Spring Security protects API routes.
- JWT authentication is used for local MVP and demo flows.
- Keycloak-compatible MFA path is documented for pilot and enterprise deployments.
- Role-aware UI and API checks support administrator, executive, officer, analyst, and auditor personas.

## Permissions

- Administrative APIs are restricted to administrators.
- Sensitive exports require authenticated access.
- Bulk export controls and role-permission mappings are visible in governance dashboards.

## Auditability

- Login and sensitive API access events are audited.
- Case actions, evidence generation, and governance reads are recorded.
- Audit logs are not editable through normal application APIs.

## Privacy

- Synthetic data is the default demo dataset.
- Approved pilot data requires a named purpose, authorized users, retention period, and masking review.
- Model outputs require officer review before enforcement action.
