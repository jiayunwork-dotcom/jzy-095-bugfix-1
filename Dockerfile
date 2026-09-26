# syntax=docker/dockerfile:1

# ---- Build stage: Maven runs the full test suite inside the image build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B clean package

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /build/target/notch-fatigue-service-1.0.0.jar app.jar

# Fixed HTTP port.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
