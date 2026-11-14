# ==========================================
# Stage 1: Build (Maven + JDK)
# ==========================================
FROM maven:3-eclipse-temurin-24 AS builder
WORKDIR /app

COPY pom.xml .
COPY src ./src
RUN mvn clean package -B -ntp -DskipTests

# Baixa o agente OpenTelemetry oficial
RUN curl -fsSL -o /app/opentelemetry-javaagent.jar \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar

# ==========================================
# Stage 2: Runtime (JRE minimal)
# ==========================================
FROM eclipse-temurin:24-jre

RUN apt-get update && apt-get upgrade -y && rm -rf /var/lib/apt/lists/*

WORKDIR /app

RUN useradd -m -u 10001 springuser

COPY --from=builder --chown=springuser:springuser /app/target/payment-service-0.0.1-SNAPSHOT.jar /app/app.jar
COPY --from=builder --chown=springuser:springuser /app/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

RUN chmod 644 /app/app.jar /app/opentelemetry-javaagent.jar

USER springuser

EXPOSE 8080

ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "/app/app.jar"]
