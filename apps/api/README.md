# Revenue Intelligence API

Spring Boot backend for the Revenue Intelligence and Compliance Assurance Platform.

## Requirements

- Java 21 or newer
- Maven or Maven Wrapper
- PostgreSQL

The local default database settings are:

```text
Database: kra system
Username: postgres
Password: 5638
Port: 5432
```

## Start Locally

From this folder:

```powershell
mvn spring-boot:run
```

Or from the repository root if using the Maven wrapper:

```powershell
.\mvnw.cmd -f apps/api/pom.xml spring-boot:run
```

Health endpoint:

```text
http://localhost:8080/api/health
```

OpenAPI endpoints:

```text
http://localhost:8080/api/openapi
http://localhost:8080/api/docs
```
