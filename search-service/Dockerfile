FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY common-security ./common-security
COPY api-gateway ./api-gateway
COPY identity-service ./identity-service
COPY workspace-service ./workspace-service
COPY content-service ./content-service
COPY notification-service ./notification-service
COPY search-service ./search-service

RUN chmod +x ./gradlew && ./gradlew :search-service:bootJar --no-daemon

FROM eclipse-temurin:25-jre

WORKDIR /app

RUN addgroup --system app && adduser --system --ingroup app app

COPY --from=build /app/search-service/build/libs/search-service.jar app.jar

USER app

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar"]
