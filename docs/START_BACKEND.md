# Start the Spring Boot Backend Locally

## 1. Confirm Java

Java is already installed on this machine. The project compiles with Java 21 bytecode and can run on the installed newer JDK.

```powershell
java -version
```

## 2. Create the PostgreSQL Database

Open pgAdmin and connect to your local PostgreSQL 16 server.

The current local defaults are taken from `.env.example`:

```text
Host: localhost
Port: 5432
Database: kra system
Username: postgres
Password: set in your local .env file
```

If the database does not exist yet, run this in pgAdmin while connected as `postgres`:

```sql
CREATE DATABASE "kra system"
  OWNER postgres
  ENCODING 'UTF8';
```

The backend reads the database password from `DB_PASSWORD`:

```text
Host: localhost
Port: 5432
Database: kra system
Username: postgres
Password: DB_PASSWORD
```

## 3. Run Tests

From the repository root:

```powershell
.\mvnw.cmd -f apps/api/pom.xml test
```

## 4. Start Spring Boot

From the repository root:

```powershell
.\mvnw.cmd -f apps/api/pom.xml spring-boot:run
```

## 5. Check the API

Open:

```text
http://localhost:8080/api/health
```

Expected response:

```json
{
  "service": "revenue-intelligence-api",
  "status": "UP",
  "timestamp": "..."
}
```

OpenAPI routes:

```text
http://localhost:8080/api/openapi
http://localhost:8080/api/docs
```

## Notes

- The first Maven command may take time because dependencies are downloaded.
- Flyway will run database migrations automatically when the backend starts.
- Docker Compose can start the local PostgreSQL and Redis dependencies:

```powershell
docker compose up -d postgres redis
```
