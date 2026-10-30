# Stage 1: Build stage with Eclipse Temurin Java 21 JDK
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

# Copy Maven wrapper files and pom.xml first to optimize layer caching
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn

# Make wrapper script executable
RUN chmod +x mvnw

# Download dependencies offline
RUN ./mvnw dependency:go-offline -B || true

# Copy application source code
COPY src src

# Build the executable Spring Boot JAR
RUN ./mvnw clean package -DskipTests -B

# Standardize output JAR name
RUN cp target/*.jar app.jar

# Stage 2: Lightweight runtime stage with Eclipse Temurin Java 21 JRE
FROM eclipse-temurin:21-jre-jammy

# Create non-root user and group for security
RUN groupadd -r spring && useradd -r -g spring spring

WORKDIR /app

# Copy executable jar from builder stage
COPY --from=builder /build/app.jar /app/app.jar

# Prepare local file upload directory with appropriate ownership
RUN mkdir -p /app/uploads && chown -R spring:spring /app

USER spring:spring

# Default environment configuration (Render overrides PORT automatically)
ENV PORT=8080
ENV FILE_UPLOAD_DIR=/app/uploads

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
