# ============================================================
# PhoenixKey Database — Multi-stage Dockerfile
# Stage 1: Build JAR với Maven
# Stage 2: Run với JRE slim
# ============================================================

# ── Stage 1: Build ──────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Cache dependencies trước (layer riêng — rebuild nhanh hơn khi chỉ đổi source)
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

# Copy source và build
COPY src ./src
RUN mvn clean package -DskipTests -B -q

# ── Stage 2: Runtime ────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Tạo user non-root để chạy app (security best practice)
RUN addgroup -S phoenixkey && adduser -S phoenixkey -G phoenixkey

COPY --from=builder /app/target/*.jar app.jar

RUN chown phoenixkey:phoenixkey app.jar

USER phoenixkey

EXPOSE 8080 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
