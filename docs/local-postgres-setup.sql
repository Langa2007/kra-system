-- Run this in pgAdmin while connected as the PostgreSQL admin user.
-- It creates the local database expected by apps/api/src/main/resources/application.yaml.

CREATE DATABASE "kra system"
  OWNER postgres
  ENCODING 'UTF8';
