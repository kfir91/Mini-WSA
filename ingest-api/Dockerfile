FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY contracts contracts
COPY ingest-api ingest-api
COPY analytics-service/pom.xml analytics-service/pom.xml
COPY generator/pom.xml generator/pom.xml
COPY coverage/pom.xml coverage/pom.xml
COPY e2e/pom.xml e2e/pom.xml
RUN mvn -pl ingest-api -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/ingest-api/target/ingest-api-0.1.0-SNAPSHOT-exec.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app.jar"]
