# Phase 1 Runbook

This runbook covers the Phase 1 baseline from `BUILD_PHASES.md`: repository structure, local development, infrastructure dependencies, linting, tests, and API documentation.

## Repository Structure

```text
apps/
  api/        Spring Boot backend API
  web/        Next.js dashboard shell
  analytics/ FastAPI analytics service
```

## Local Dependencies

```powershell
Copy-Item .env.example .env
docker compose up -d postgres redis
```

PostgreSQL:

```text
Host: localhost
Port: 5432
Database: kra system
Username: postgres
Password: set in your local .env file
```

Redis:

```text
Host: localhost
Port: 6379
```

## Run Services

Backend API:

```powershell
.\mvnw.cmd -f apps/api/pom.xml spring-boot:run
```

Web app:

```powershell
npm install
npm run dev:web
```

Analytics API:

```powershell
pip install -r apps/analytics/requirements.txt
uvicorn app.main:app --app-dir apps/analytics --reload --port 8090
```

## OpenAPI

The backend exposes generated OpenAPI documents through SpringDoc:

```text
JSON: http://localhost:8080/api/openapi
UI:   http://localhost:8080/api/docs
```

## Testing Gate

```powershell
.\mvnw.cmd -f apps/api/pom.xml test
npm run lint:web
npm run test:web
python -m ruff check apps/analytics
python -m pytest apps/analytics
docker compose exec -T postgres pg_isready -U postgres -d "kra system"
docker compose exec -T redis redis-cli ping
```

## Exit Criteria Mapping

- Backend Spring Boot app: `apps/api`
- Frontend Next.js app: `apps/web`
- Analytics Python app: `apps/analytics`
- PostgreSQL and Redis containers: `docker-compose.yml`
- Environment template: `.env.example`
- Linting and formatting: ESLint, Ruff, Prettier, EditorConfig
- CI pipeline skeleton: `.github/workflows/ci.yml`
- OpenAPI generation path: `/api/openapi` and `/api/docs`
