FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY nook-api-application nook-api-application
COPY nook-api-domain nook-api-domain
COPY nook-api-infrastructure nook-api-infrastructure
COPY nook-api-presentation nook-api-presentation

RUN chmod +x gradlew && ./gradlew :nook-api-presentation:bootJar --no-daemon

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S nook && adduser -S nook -G nook
COPY --from=builder --chown=nook:nook /workspace/nook-api-presentation/build/libs/*.jar app.jar

USER nook
EXPOSE 8080
ENV JVM_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JVM_OPTS -jar app.jar"]
