FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
# Full Testcontainers verification runs on the host/CI with Docker access.
RUN mvn --batch-mode --no-transfer-progress clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/Distriubuted_AI_Gateway-1.0-SNAPSHOT.jar app.jar
USER 10001:10001
ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
