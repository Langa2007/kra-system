FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY apps/api/pom.xml apps/api/pom.xml
RUN mvn -f apps/api/pom.xml -DskipTests dependency:go-offline

COPY apps/api apps/api
RUN mvn -f apps/api/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/apps/api/target/revenue-intelligence-api-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
