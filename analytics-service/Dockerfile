FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY miniwsa-contracts miniwsa-contracts
COPY analytics-service analytics-service
COPY ingest-api/pom.xml ingest-api/pom.xml
COPY generator/pom.xml generator/pom.xml
RUN mvn -pl analytics-service -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/analytics-service/target/analytics-service-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
