FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY api-gateway ./api-gateway
COPY identity-service ./identity-service
COPY workspace-service ./workspace-service
COPY content-service ./content-service

RUN chmod +x gradlew
RUN ./gradlew :identity-service:bootJar -x test --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --home-dir /app app
COPY --from=build /app/identity-service/build/libs/identity-service.jar /app/app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8081
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]

