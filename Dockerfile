# Root Dockerfile: allows building directly from repository root
# Stage 1: Build stage with Java 21 JDK
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

# Copy Maven wrapper and pom.xml from feculty_backend subdirectory
COPY feculty_backend/mvnw feculty_backend/mvnw.cmd feculty_backend/pom.xml ./
COPY feculty_backend/.mvn .mvn

RUN chmod +x mvnw

RUN ./mvnw dependency:go-offline -B || true

COPY feculty_backend/src src

RUN ./mvnw clean package -DskipTests -B

RUN cp target/*.jar app.jar

# Stage 2: Runtime stage with Java 21 JRE
FROM eclipse-temurin:21-jre-jammy

RUN groupadd -r spring && useradd -r -g spring spring

WORKDIR /app

COPY --from=builder /build/app.jar /app/app.jar

RUN mkdir -p /app/uploads && chown -R spring:spring /app

USER spring:spring

ENV PORT=8080
ENV FILE_UPLOAD_DIR=/app/uploads

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
